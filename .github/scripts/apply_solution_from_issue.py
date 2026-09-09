import os, subprocess, requests, json
from openai import OpenAI

def run_cmd(cmd):
    return subprocess.run(cmd, shell=True, text=True, capture_output=True).stdout.strip()

def main():
    issue_number = os.environ["ISSUE_NUMBER"]
    issue_body = os.environ["ISSUE_BODY"]
    repo = os.environ["GITHUB_REPOSITORY"]
    token = os.environ["GITHUB_TOKEN"]
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github.v3+json"}

    # 让 AI 提取 Issue 中的建议并输出最终的代码修改结果
    client = OpenAI(
        api_key=os.environ["AI_API_KEY"],
        base_url=os.environ.get("AI_BASE_URL", "https://api.openai.com/v1")
    )

    prompt = f"""
根据以下 Issue 描述中确定的方案，给出对目标代码的修改：
Issue 内容:
{issue_body}

请以 JSON 格式输出，不要包含 markdown 标记：
{{
  "file_path": "目标文件路径",
  "original_snippet": "要替换的代码原貌",
  "fixed_snippet": "替换后的完整新代码"
}}
"""
    res = client.chat.completions.create(
        model="deepseek-chat" if "deepseek" in os.environ.get("AI_BASE_URL", "") else "gpt-4o",
        messages=[{"role": "user", "content": prompt}],
        response_format={"type": "json_object"}
    )
    fix_data = json.loads(res.choices[0].message.content)
    target_file = fix_data["file_path"]

    if not os.path.exists(target_file):
        print("未找到目标文件，跳过")
        return

    with open(target_file, "r", encoding="utf-8") as f:
        source = f.read()

    if fix_data["original_snippet"] not in source:
        print("未完全匹配到待替换的代码片段")
        return

    source = source.replace(fix_data["original_snippet"], fix_data["fixed_snippet"])
    with open(target_file, "w", encoding="utf-8") as f:
        f.write(source)

    # 1. 运行 Spotless 格式化，确保符合规范
    print("正在执行 Spotless 格式化...")
    run_cmd("mvn spotless:apply || ./gradlew spotlessApply || true")

    # 2. 切分支并推代码
    branch_name = f"fix-issue-{issue_number}"
    run_cmd(f"git checkout -b {branch_name}")
    run_cmd("git config user.name 'github-actions[bot]'")
    run_cmd("git config user.email 'github-actions[bot]@users.noreply.github.com'")
    run_cmd("git add .")
    run_cmd(f"git commit -m 'fix: 自动修复 Issue #{issue_number}'")
    run_cmd(f"git push origin {branch_name} --force")

    # 3. 创建 PR，并在描述中加 `Closes #xxx` 关联 Issue
    pr_payload = {
        "title": f"fix: 解决 Issue #{issue_number} 提出的缺陷",
        "head": branch_name,
        "base": "main",
        "body": f"本 PR 由开发者在 Issue #{issue_number} 中确认授权后自动生成。\n\nCloses #{issue_number}"
    }
    pr_resp = requests.post(f"https://api.github.com/repos/{repo}/pulls", json=pr_payload, headers=headers)
    pr_url = pr_resp.json().get("html_url")

    # 4. 在原 Issue 留言通知开发者
    comment_payload = {
        "body": f"✅ 已根据您的确认自动完成代码修复与 Spotless 格式化！\n已为您发起 Pull Request：{pr_url}，该 PR 合并后将自动关闭本 Issue。"
    }
    requests.post(f"https://api.github.com/repos/{repo}/issues/{issue_number}/comments", json=comment_payload, headers=headers)

if __name__ == "__main__":
    main()