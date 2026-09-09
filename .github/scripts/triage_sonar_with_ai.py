import os, requests, json, subprocess
from openai import OpenAI

def run_cmd(cmd):
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if res.returncode != 0:
        print(f"[CMD WARN] {cmd}\n{res.stderr.strip()}")
    return res.stdout.strip()

def get_code_context(file_path, target_line, radius=10):
    """根据行号读取前后10行的真实代码"""
    if not os.path.exists(file_path):
        return ""
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            lines = f.readlines()
        start = max(0, target_line - radius - 1)
        end = min(len(lines), target_line + radius)
        return "".join(lines[start:end])
    except Exception:
        return ""

def ensure_label_exists(repo, headers, label_name, color="e11d48", description="AI 待决策"):
    """确保 GitHub label 存在，不存在则自动创建"""
    res = requests.post(
        f"https://api.github.com/repos/{repo}/labels",
        json={"name": label_name, "color": color, "description": description},
        headers=headers
    )
    if res.status_code == 201:
        print(f"✅ 已自动创建 label: {label_name}")
    elif res.status_code == 422:
        pass  # label 已存在，正常
    else:
        print(f"⚠️ 创建 label 失败: {res.status_code} {res.text}")

def main():
    sonar_token = os.environ.get("SONAR_TOKEN")
    project_key = os.environ.get("SONAR_PROJECT_KEY")
    repo        = os.environ["GITHUB_REPOSITORY"]
    gh_token    = os.environ["GITHUB_TOKEN"]
    headers     = {"Authorization": f"Bearer {gh_token}", "Accept": "application/vnd.github.v3+json"}
    model       = os.environ.get("AI_MODEL", "deepseek-v4-flash")

    # 优先保证 label 存在
    ensure_label_exists(repo, headers, "ai-needs-decision")

    # 1. 调用 SonarCloud API 获取缺陷（最多取 20 条）
    sonar_api = (
        f"https://sonarcloud.io/api/issues/search"
        f"?componentKeys={project_key}&resolved=false&ps=20"
        f"&types=BUG,VULNERABILITY,CODE_SMELL"
        f"&severities=BLOCKER,CRITICAL,MAJOR"
    )
    res = requests.get(sonar_api, auth=(sonar_token, ""))

    if res.status_code != 200:
        print(f"❌ 获取 SonarCloud 结果失败: {res.text}")
        return

    issues = res.json().get("issues", [])
    if not issues:
        print("✅ SonarCloud 显示项目没有任何 BUG / 漏洞 / 代码异味！")
        return

    total = len(issues)
    process_count = min(10, total)  # 最多处理10条，避免 token 超限
    print(f"📊 SonarCloud 共捕获到 {total} 个问题，本次处理前 {process_count} 条（按严重程度优先）...")

    sonar_summary = []
    for iss in issues[:process_count]:
        raw_path = iss.get("component", "")
        file_path = raw_path.split(":")[-1] if ":" in raw_path else raw_path
        line = iss.get("line", 1)

        # 从本地提取出真实的 Java 代码片段
        real_code_snippet = get_code_context(file_path, line)

        sonar_summary.append({
            "rule":         iss.get("rule"),
            "severity":     iss.get("severity"),
            "type":         iss.get("type"),
            "file":         file_path,
            "line":         line,
            "message":      iss.get("message"),
            "code_context": real_code_snippet
        })

    if total > process_count:
        print(f"⚠️ 还有 {total - process_count} 条问题因批量限制被跳过，可手动触发再次处理")

    # 2. 交由 AI 分析
    client = OpenAI(
        api_key=os.environ["AI_API_KEY"],
        base_url=os.environ.get("AI_BASE_URL", "https://api.deepseek.com/v1")
    )

    system_prompt = """
你是一名极其资深的 Java 架构师。针对 SonarCloud 发现的代码缺陷及其真实代码片段进行修复。
必须全部使用简体中文输出！

严格输出以下格式的 JSON，不要包含任何 markdown 标记：
{
  "auto_fix_list": [
    {
      "file_path": "文件路径",
      "issue_desc": "缺陷简要说明",
      "original_snippet": "在 code_context 中真实存在、100%一字不差的原代码片段",
      "fixed_snippet": "修复后的新代码"
    }
  ],
  "need_review_list": [
    {
      "title": "待审查的问题标题",
      "analysis": "原因与危害分析",
      "recommended_plan": "建议修复方案（含示例代码）",
      "target_file": "涉及文件"
    }
  ]
}

判断规则：
- auto_fix_list：NPE（空指针）防御、死代码、简单语法异味、漏加 @Transactional、简单判空反转等无业务副作用的修复。
- need_review_list：涉及业务逻辑、重构、安全漏洞需人工确认等复杂问题。
- original_snippet 必须能在提供的 code_context 中完全精确匹配！不得虚构代码！
- .github/ 目录下的文件一律放入 need_review_list，禁止自动修复！
"""

    resp = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": f"缺陷及对应源码如下：\n{json.dumps(sonar_summary, ensure_ascii=False, indent=2)}"}
        ],
        response_format={"type": "json_object"}
    )

    decision = json.loads(resp.choices[0].message.content)

    # 打印 AI 的完整决策
    print(f"🤖 AI 决策输出：\n{json.dumps(decision, ensure_ascii=False, indent=2)}")

    # 3. 处理自动修复提 PR
    auto_fixes = decision.get("auto_fix_list", [])
    if auto_fixes:
        branch_name = f"sonar-autofix-{os.environ.get('GITHUB_RUN_ID', 'dev')}"
        run_cmd(f"git checkout -b {branch_name}")
        modified   = False
        fix_descs  = []

        for fix in auto_fixes:
            fp    = fix["file_path"]
            orig  = fix["original_snippet"]
            fixed = fix["fixed_snippet"]

            if not os.path.exists(fp):
                print(f"⚠️ 文件不存在，跳过: {fp}")
                continue

            with open(fp, "r", encoding="utf-8") as f:
                content = f.read()

            if orig in content:
                content = content.replace(orig, fixed, 1)
                with open(fp, "w", encoding="utf-8") as f:
                    f.write(content)
                modified = True
                fix_descs.append(f"- [`{fp}`]: {fix['issue_desc']}")
                print(f"✅ 成功替换文件: {fp}")
            else:
                print(f"⚠️ 未能在 {fp} 中精确匹配到旧代码（AI 可能生成了不精确的片段）:\n{orig}")

        if modified:
            print("🎨 执行 Spotless 格式化...")
            run_cmd("mvn spotless:apply -q || ./gradlew spotlessApply || true")

            run_cmd("git config user.name 'github-actions[bot]'")
            run_cmd("git config user.email 'github-actions[bot]@users.noreply.github.com'")
            run_cmd("git add .")
            run_cmd("git commit -m 'fix: AI 自动修复 Sonar 告警代码缺陷'")
            run_cmd(f"git push origin {branch_name} --force")

            pr_payload = {
                "title": "🤖 [Sonar+AI Auto-Fix] 自动修复代码缺陷",
                "head": branch_name,
                "base": "htz",
                "body": (
                    "### SonarCloud 缺陷修复报告\n"
                    "本 PR 由 AI 自动定位并修复，已执行 Spotless 格式化，请 Code Review 后合并。\n\n"
                    "**本次修复项：**\n" + "\n".join(fix_descs)
                )
            }
            pr_res = requests.post(f"https://api.github.com/repos/{repo}/pulls", json=pr_payload, headers=headers)
            if pr_res.status_code == 201:
                print(f"🎉 成功创建修复 PR: {pr_res.json().get('html_url')}")
            else:
                print(f"❌ 创建 PR 失败! 状态码: {pr_res.status_code}, 错误: {pr_res.text}")
        else:
            print("ℹ️ 所有自动修复项均未能精确匹配，未产生代码变更")

    # 4. 处理复杂问题提 Issue
    for review in decision.get("need_review_list", []):
        issue_body = f"""### ⚠️ 问题深度分析
**涉及文件**：`{review.get('target_file')}`

**根因与危害**：
{review.get('analysis')}

---
### 💡 AI 建议的修复方案
{review.get('recommended_plan')}

---
> 💬 **人机协作提示**：
> 如果您认可上述方案，请在下方评论 **【同意】** 或 **【执行】**，AI 将自动创建修复分支并发起 Pull Request。
"""
        issue_payload = {
            "title": f"⚠️ [Sonar待决策] {review.get('title')}",
            "body": issue_body,
            "labels": ["ai-needs-decision"]
        }
        iss_res = requests.post(f"https://api.github.com/repos/{repo}/issues", json=issue_payload, headers=headers)
        if iss_res.status_code == 201:
            print(f"📌 成功创建待审核 Issue: {review.get('title')}")
        else:
            print(f"❌ 创建 Issue 失败! 状态码: {iss_res.status_code}, 错误: {iss_res.text}")

if __name__ == "__main__":
    main()