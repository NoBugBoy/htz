import os, json, subprocess, requests
from openai import OpenAI

def run_cmd(cmd):
    result = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if result.returncode != 0:
        print(f"[CMD WARN] {cmd}\n{result.stderr.strip()}")
    return result.stdout.strip()

def main():
    report_file = "semgrep_report.json"
    if not os.path.exists(report_file):
        print("未发现扫描结果文件")
        return

    with open(report_file, "r", encoding="utf-8") as f:
        report = json.load(f)

    findings = report.get("results", [])
    if not findings:
        print("✅ 未发现任何代码缺陷！")
        return

    print(f"📊 Semgrep 共扫描出 {len(findings)} 个问题，正在交给 AI 进行智能研判...")

    issues_summary = []
    for f in findings[:5]: # 取前 5 个进行治理分析
        issues_summary.append({
            "check_id": f.get("check_id"),
            "path": f.get("path"),
            "line": f.get("start", {}).get("line"),
            "code": f.get("extra", {}).get("lines"),
            "message": f.get("extra", {}).get("message")
        })

    client = OpenAI(
        api_key=os.environ["AI_API_KEY"],
        base_url=os.environ.get("AI_BASE_URL", "https://api.openai.com/v1")
    )

    system_prompt = """
你是一名极其严格的高级代码架构师。针对 Semgrep 提供的缺陷列表，请进行分级处理。

【语言要求】：
所有输出的内容（包括 title, analysis, recommended_plan, 忽略原因等）必须【100% 严格使用通俗、专业、地道的简体中文】输出！严禁输出任何英文分析！

严格按以下 JSON 格式输出，不要包含任何 markdown 标记：
{
  "ignore_list": ["用中文说明为什么判定为误报或不需要处理的原因..."],
  "auto_fix_list": [
    {
      "file_path": "文件相对路径",
      "issue_desc": "简短中文问题描述",
      "original_snippet": "目标文件中真实存在的、待替换的旧代码精确片段",
      "fixed_snippet": "替换后的新代码"
    }
  ],
  "need_review_list": [
    {
      "title": "简短的中文 Issue 标题（例如：代码中存在潜在的空指针或硬编码风险）",
      "analysis": "用简体中文详细说明问题危害及发生原因",
      "recommended_plan": "用简体中文说明推荐的修复方案及示例代码",
      "target_file": "涉及文件相对路径"
    }
  ]
}

判断标准：
- ignore_list：测试类文件、配置假阳性、有框架兜底不会发生的问题。
- auto_fix_list：纯语法级、无业务副作用、影响范围极小（如 NPE 防御、简单日志脱敏、漏加 @Transactional、简单的判空反转）。
- need_review_list：改动影响业务流程、涉及重构、需要产品/研发确认业务语义的问题。
"""

    resp = client.chat.completions.create(
        model="deepseek-chat" if "deepseek" in os.environ.get("AI_BASE_URL", "") else "gpt-4o",
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": f"扫描结果如下：\n{json.dumps(issues_summary, ensure_ascii=False)}"}
        ],
        response_format={"type": "json_object"}
    )

    decision = json.loads(resp.choices[0].message.content)
    repo = os.environ["GITHUB_REPOSITORY"]
    token = os.environ["GITHUB_TOKEN"]
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github.v3+json"}

    # ---------------- 1. 处理低风险自动修复 (Auto Fix -> 提 PR) ----------------
    auto_fixes = decision.get("auto_fix_list", [])
    if auto_fixes:
        branch_name = f"ai-autofix-{os.environ.get('GITHUB_RUN_ID', 'dev')}"
        run_cmd(f"git checkout -b {branch_name}")

        modified = False
        fix_descriptions = []
        for fix in auto_fixes:
            file_path = fix["file_path"]
            if os.path.exists(file_path):
                with open(file_path, "r", encoding="utf-8") as rf:
                    content = rf.read()
                if fix["original_snippet"] in content:
                    content = content.replace(fix["original_snippet"], fix["fixed_snippet"], 1)
                    with open(file_path, "w", encoding="utf-8") as wf:
                        wf.write(content)
                    modified = True
                    fix_descriptions.append(f"- [{fix['file_path']}]: {fix['issue_desc']}")
                    print(f"已自动修改文件: {file_path}")

        if modified:
            print("正在执行 Spotless 格式化...")
            run_cmd("mvn spotless:apply || ./gradlew spotlessApply || true")

            run_cmd("git config user.name 'github-actions[bot]'")
            run_cmd("git config user.email 'github-actions[bot]@users.noreply.github.com'")
            run_cmd("git add .")
            run_cmd("git commit -m 'chore: AI 自动修复已知微小代码异味与隐患'")
            run_cmd(f"git push origin {branch_name} --force")

            # 调用 GitHub API 发起 PR，base 设定为 htz
            pr_payload = {
                "title": "🤖 [AI Auto-Fix] 修复低风险代码异味及安全缺陷",
                "head": branch_name,
                "base": "htz",
                "body": "### AI 自动修复报告\n本 PR 由 AI 自动扫描并完成修复，已通过 Spotless 格式化。\n\n" + "\n".join(fix_descriptions)
            }
            pr_res = requests.post(f"https://api.github.com/repos/{repo}/pulls", json=pr_payload, headers=headers)
            if pr_res.status_code == 201:
                print(f"✅ 成功创建自动修复 PR: {pr_res.json().get('html_url')}")
            else:
                print(f"❌ 创建 PR 失败! 状态码: {pr_res.status_code}, 返回: {pr_res.text}")

    # ---------------- 2. 处理复杂业务问题 (Need Review -> 提 Issue) ----------------
    reviews = decision.get("need_review_list", [])
    for item in reviews:
        issue_body = f"""### ⚠️ 问题深度分析
**涉及文件**：`{item.get('target_file')}`
**根因与危害**：
{item.get('analysis')}

---
### 💡 AI 建议的修复方案
{item.get('recommended_plan')}

---
> 💬 **人机协作提示**：
> 如果您认可上述方案，请在下方评论 **【同意】** 或 **【执行】**，AI 将自动创建修复分支、完成格式化并为您发起 Pull Request。
"""
        issue_payload = {
            "title": f"⚠️ [待决策] {item.get('title')}",
            "body": issue_body,
            "labels": ["ai-needs-decision"]
        }
        iss_res = requests.post(f"https://api.github.com/repos/{repo}/issues", json=issue_payload, headers=headers)
        if iss_res.status_code == 201:
            print(f"✅ 已创建待审核 Issue: {item.get('title')}")
        else:
            print(f"❌ 创建 Issue 失败! 状态码: {iss_res.status_code}, 返回: {iss_res.text}")

if __name__ == "__main__":
    main()