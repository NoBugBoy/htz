import os, subprocess, requests, json, re
from openai import OpenAI

def run_cmd(cmd):
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if res.returncode != 0:
        print(f"[CMD WARN] {cmd}\n{res.stderr.strip()}")
    return res.stdout.strip()

def ensure_label_exists(repo, headers, label_name, color="e11d48", description="AI 待决策"):
    """确保 label 存在，不存在则自动创建"""
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
    issue_number = os.environ["ISSUE_NUMBER"]
    issue_body   = os.environ.get("ISSUE_BODY", "")
    issue_title  = os.environ.get("ISSUE_TITLE", "")
    repo         = os.environ["GITHUB_REPOSITORY"]
    token        = os.environ["GITHUB_TOKEN"]
    headers      = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github.v3+json"}

    # 1. 从 Issue 内容中用正则提取涉及的文件路径（格式：涉及文件：`xxx`）
    file_match = re.search(r'涉及文件[：:]\s*`?([^\n`]+)`?', issue_body)
    if not file_match:
        print("❌ 未能在 Issue 描述中解析出文件路径，终止执行")
        return

    target_file = file_match.group(1).strip()
    if not os.path.exists(target_file):
        print(f"❌ 目标文件不存在: {target_file}")
        return

    # 读取真实文件源码
    with open(target_file, "r", encoding="utf-8") as f:
        actual_source = f.read()

    print(f"📄 目标文件: {target_file}，共 {len(actual_source.splitlines())} 行")

    # 2. 把真实文件内容 + Issue 方案一起喂给 AI
    client = OpenAI(
        api_key=os.environ["AI_API_KEY"],
        base_url=os.environ.get("AI_BASE_URL", "https://api.deepseek.com/v1")
    )
    model = os.environ.get("AI_MODEL", "deepseek-v4-flash")

    prompt = f"""
你是一名资深代码重构专家。请根据以下 Issue 的修复建议，对目标文件代码进行精准替换。

【目标文件路径】: {target_file}

【目标文件当前的真实内容】:
```
{actual_source}
```

【Issue 标题】: {issue_title}

【Issue 修复方案】:
{issue_body}

严格输出以下 JSON 格式，不要包含任何 markdown 标记：
{{
  "original_snippet": "在目标文件中100%一字不差存在的旧代码片段",
  "fixed_snippet": "修复后的新代码片段",
  "change_summary": "简短说明本次修改内容（中文）"
}}

规则：
- original_snippet 必须是文件中真实存在的精确字符串，不能有任何差异
- 如果 Issue 方案不明确或无法安全自动修复，返回 original_snippet 和 fixed_snippet 均为空字符串，并在 change_summary 说明原因
"""

    print("🤖 正在调用 AI 生成修复方案...")
    resp = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": "你是一名极其资深的 Java 代码重构专家，只输出 JSON，不输出任何 markdown。"},
            {"role": "user", "content": prompt}
        ],
        response_format={"type": "json_object"}
    )

    decision = json.loads(resp.choices[0].message.content)
    print(f"🤖 AI 决策：\n{json.dumps(decision, ensure_ascii=False, indent=2)}")

    orig    = decision.get("original_snippet", "").strip()
    fixed   = decision.get("fixed_snippet", "").strip()
    summary = decision.get("change_summary", "")

    if not orig or not fixed:
        print(f"⚠️ AI 判断无法安全自动修复：{summary}")
        return

    # 3. 应用代码替换
    if orig not in actual_source:
        print(f"⚠️ 未能在 {target_file} 中精确匹配到旧代码，终止替换")
        print(f"  期望匹配：\n{orig}")
        return

    new_source = actual_source.replace(orig, fixed, 1)
    with open(target_file, "w", encoding="utf-8") as f:
        f.write(new_source)
    print(f"✅ 成功替换文件: {target_file}")

    # 4. Spotless 格式化
    print("🎨 执行 Spotless 格式化...")
    run_cmd("mvn spotless:apply -q || ./gradlew spotlessApply || true")

    # 5. Git 提交并推送新分支
    branch_name = f"ai-fix-issue-{issue_number}"
    run_cmd(f"git checkout -b {branch_name}")
    run_cmd("git config user.name 'github-actions[bot]'")
    run_cmd("git config user.email 'github-actions[bot]@users.noreply.github.com'")
    run_cmd("git add .")
    run_cmd(f"git commit -m 'fix: AI 根据 Issue #{issue_number} 自动修复代码 - {summary}'")
    run_cmd(f"git push origin {branch_name} --force")

    # 6. 创建 PR
    pr_payload = {
        "title": f"🤖 [AI Fix] Issue #{issue_number}: {issue_title}",
        "head": branch_name,
        "base": "htz",
        "body": (
            f"关联 Issue: #{issue_number}\n\n"
            f"### 📋 修复内容\n{summary}\n\n"
            f"### 📄 涉及文件\n`{target_file}`\n\n"
            f"本 PR 由 AI 根据 Issue 方案自动生成，已执行 Spotless 格式化，请 Code Review 后合并。"
        )
    }
    pr_res = requests.post(f"https://api.github.com/repos/{repo}/pulls", json=pr_payload, headers=headers)
    if pr_res.status_code == 201:
        pr_url = pr_res.json().get("html_url")
        print(f"🎉 成功创建修复 PR: {pr_url}")
        # 在原 Issue 下回复 PR 链接
        requests.post(
            f"https://api.github.com/repos/{repo}/issues/{issue_number}/comments",
            json={"body": f"✅ AI 已自动完成修复并创建 PR：{pr_url}\n\n请 Review 后合并。"},
            headers=headers
        )
    else:
        print(f"❌ 创建 PR 失败! 状态码: {pr_res.status_code}, 错误: {pr_res.text}")

if __name__ == "__main__":
    main()