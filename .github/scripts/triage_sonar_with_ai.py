import os, requests, json, subprocess
from openai import OpenAI

def run_cmd(cmd):
    return subprocess.run(cmd, shell=True, text=True, capture_output=True).stdout.strip()

def main():
    sonar_token = os.environ["SONAR_TOKEN"]
    project_key = os.environ["SONAR_PROJECT_KEY"]
    repo = os.environ["GITHUB_REPOSITORY"]
    gh_token = os.environ["GITHUB_TOKEN"]

    # 1. 调用 SonarCloud REST API 拉取未解决的 Issues
    sonar_api = f"https://sonarcloud.io/api/issues/search?componentKeys={project_key}&resolved=false&ps=10"
    res = requests.get(sonar_api, auth=(sonar_token, ""))

    if res.status_code != 200:
        print(f"❌ 获取 SonarCloud 结果失败: {res.text}")
        return

    data = res.json()
    issues = data.get("issues", [])
    if not issues:
        print("✅ SonarCloud 显示项目没有任何代码异味与缺陷！")
        return

    print(f"📊 SonarCloud 捕获到 {len(issues)} 个专业代码问题，正在交由 AI 分析...")

    # 格式化给 AI 的数据
    sonar_summary = []
    for iss in issues[:5]:
        # Sonar 里的文件路径是形如 "project_key:src/main/java/..."
        raw_path = iss.get("component", "")
        file_path = raw_path.split(":")[-1] if ":" in raw_path else raw_path

        sonar_summary.append({
            "rule": iss.get("rule"),
            "severity": iss.get("severity"),
            "file": file_path,
            "line": iss.get("line"),
            "message": iss.get("message") # Sonar 的专业中文/英文错误描述（如 "Null pointer dereference"）
        })

    # 2. 交给 AI 分级与生成修复
    client = OpenAI(
        api_key=os.environ["AI_API_KEY"],
        base_url=os.environ.get("AI_BASE_URL", "https://api.openai.com/v1")
    )

    system_prompt = """
你是一名极其严格的高级 Java 架构师。针对 SonarCloud 发现的代码异味与 Bug 进行分级处理。
必须全量使用简体中文输出！
严格输出以下 JSON：
{
  "auto_fix_list": [
    {
      "file_path": "相对路径",
      "issue_desc": "中文描述",
      "original_snippet": "待替换的旧代码精确片段",
      "fixed_snippet": "修复后的新代码"
    }
  ],
  "need_review_list": [
    {
      "title": "中文 Issue 标题",
      "analysis": "危害分析",
      "recommended_plan": "推荐方案",
      "target_file": "涉及文件"
    }
  ]
}
"""
    resp = client.chat.completions.create(
        model="deepseek-chat" if "deepseek" in os.environ.get("AI_BASE_URL", "") else "gpt-4o",
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": f"Sonar 检查结果：\n{json.dumps(sonar_summary, ensure_ascii=False)}"}
        ],
        response_format={"type": "json_object"}
    )

    decision = json.loads(resp.choices[0].message.content)
    headers = {"Authorization": f"Bearer {gh_token}", "Accept": "application/vnd.github.v3+json"}

    # 3. 自动修代码并提 PR
    auto_fixes = decision.get("auto_fix_list", [])
    if auto_fixes:
        branch_name = f"sonar-autofix-{os.environ.get('GITHUB_RUN_ID', 'dev')}"
        run_cmd(f"git checkout -b {branch_name}")
        modified = False
        fix_descs = []
        for fix in auto_fixes:
            fp = fix["file_path"]
            if os.path.exists(fp):
                with open(fp, "r", encoding="utf-8") as f:
                    content = f.read()
                if fix["original_snippet"] in content:
                    content = content.replace(fix["original_snippet"], fix["fixed_snippet"], 1)
                    with open(fp, "w", encoding="utf-8") as f:
                        f.write(content)
                    modified = True
                    fix_descs.append(f"- [{fp}]: {fix['issue_desc']}")

        if modified:
            print("执行 Spotless 格式化...")
            run_cmd("mvn spotless:apply || ./gradlew spotlessApply || true")

            run_cmd("git config user.name 'github-actions[bot]'")
            run_cmd("git config user.email 'github-actions[bot]@users.noreply.github.com'")
            run_cmd("git add .")
            run_cmd("git commit -m 'chore: AI 自动修复 SonarCloud 告警缺陷'")
            run_cmd(f"git push origin {branch_name} --force")

            pr_payload = {
                "title": "🤖 [Sonar+AI Auto-Fix] 自动修复 Java 代码异味与缺陷",
                "head": branch_name,
                "base": "htz",
                "body": "### SonarCloud 缺陷修复报告\n已由 AI 完成代码重构并执行 Spotless 格式化：\n\n" + "\n".join(fix_descs)
            }
            pr_res = requests.post(f"https://api.github.com/repos/{repo}/pulls", json=pr_payload, headers=headers)
            if pr_res.status_code == 201:
                print(f"✅ 成功创建 PR: {pr_res.json().get('html_url')}")
            else:
                print(f"❌ 创建 PR 失败: {pr_res.status_code}, {pr_res.text}")

if __name__ == "__main__":
    main()