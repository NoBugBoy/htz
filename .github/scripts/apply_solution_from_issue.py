import os, re, subprocess, requests, json
from openai import OpenAI

def run_cmd(cmd):
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if res.returncode != 0:
        print(f"[CMD WARN] {cmd}\n{res.stderr.strip()}")
    return res.stdout.strip()

def get_code_context(file_path, target_line, radius=10):
    """读取指定行前后10行的真实代码"""
    if not os.path.exists(file_path):
        return ""
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            lines = f.readlines()
        start = max(0, target_line - radius - 1)
        end   = min(len(lines), target_line + radius)
        return "".join(lines[start:end])
    except Exception:
        return ""

def get_blame_author(file_path, line, repo, headers, sha_cache):
    """
    通过 git blame 找到指定行的提交 SHA，再通过 GitHub API 获取 @login。
    sha_cache: dict，避免重复 API 请求。
    """
    if not os.path.exists(file_path):
        return None
    try:
        result = subprocess.run(
            f"git blame --porcelain -L {line},{line} -- {file_path}",
            shell=True, text=True, capture_output=True
        )
        if result.returncode != 0 or not result.stdout.strip():
            return None

        blame_lines = result.stdout.split("\n")
        sha = blame_lines[0].split(" ")[0]
        if not sha or sha == "0" * 40:
            return None

        if sha in sha_cache:
            return sha_cache[sha]

        author_name = next(
            (l[7:] for l in blame_lines if l.startswith("author ")), ""
        ).strip()

        res = requests.get(
            f"https://api.github.com/repos/{repo}/commits/{sha}",
            headers=headers
        )
        if res.status_code == 200:
            gh_user = res.json().get("author")
            login   = gh_user.get("login") if gh_user else None
            mention = f"@{login}" if login else author_name
        else:
            mention = author_name

        sha_cache[sha] = mention
        return mention
    except Exception as e:
        print(f"[WARN] git blame 失败 {file_path}:{line} - {e}")
        return None

def ensure_label_exists(repo, headers, label_name, color="e11d48", description="AI 待决策"):
    res = requests.post(
        f"https://api.github.com/repos/{repo}/labels",
        json={"name": label_name, "color": color, "description": description},
        headers=headers
    )
    if res.status_code not in (201, 422):
        print(f"⚠️ 创建 label 失败: {res.status_code} {res.text}")

def parse_approved_indices(comment_body, total):
    """
    从开发者评论中解析需要修复的问题编号（1-based），返回 set。
    支持格式：
      - 执行全部 / approve all → 全部
      - 跳过 2,4 → 全部 except 2,4
      - 同意修复 1,3,5 / 执行 1 3 5 / approve 1,2,3 → 指定项
      - 同意 / 执行 / approve（无数字）→ 全部
    """
    comment = comment_body.strip()

    if re.search(r'执行全部|approve\s+all', comment, re.IGNORECASE):
        return set(range(1, total + 1))

    skip_match = re.search(r'跳过\s*([\d,，\s]+)', comment)
    if skip_match:
        skip_nums = {int(n) for n in re.findall(r'\d+', skip_match.group(1))}
        return set(range(1, total + 1)) - skip_nums

    nums_match = re.search(r'(?:同意修复|执行|approve)\s*([\d,，\s]+)', comment, re.IGNORECASE)
    if nums_match:
        return {int(n) for n in re.findall(r'\d+', nums_match.group(1))}

    if re.search(r'同意|执行|approve', comment, re.IGNORECASE):
        return set(range(1, total + 1))

    return set()

def main():
    issue_number = os.environ["ISSUE_NUMBER"]
    issue_body   = os.environ.get("ISSUE_BODY", "")
    issue_title  = os.environ.get("ISSUE_TITLE", "")
    comment_body = os.environ.get("COMMENT_BODY", "")
    repo         = os.environ["GITHUB_REPOSITORY"]
    token        = os.environ["GITHUB_TOKEN"]
    headers      = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github.v3+json"}
    model        = os.environ.get("AI_MODEL", "deepseek-v4-flash")
    sha_cache    = {}

    ensure_label_exists(repo, headers, "ai-needs-decision")

    # ── Step 1：从 Issue body 的隐藏注释中提取原始 SonarCloud 数据 ──────────
    data_match = re.search(r'<!-- SONAR_ISSUE_DATA\s*([\s\S]+?)\s*-->', issue_body)
    if not data_match:
        print("❌ 未在 Issue 中找到 SONAR_ISSUE_DATA 数据块，终止执行")
        return

    issue_data    = json.loads(data_match.group(1).strip())
    all_issues    = issue_data.get("issues", [])
    batch_index   = issue_data.get("batch_index", "?")
    total_batches = issue_data.get("total_batches", "?")
    total         = len(all_issues)

    print(f"📦 Issue #{issue_number}  第 {batch_index}/{total_batches} 批，共 {total} 条问题")
    print(f"💬 开发者评论：{comment_body}")

    # ── Step 2：解析评论，确定要修复哪些条目 ──────────────────────────────────
    approved_indices = parse_approved_indices(comment_body, total)
    if not approved_indices:
        msg = "⚠️ 未能从评论中解析出有效的修复指令，请使用格式如：`同意修复 1,3,5` 或 `执行全部`"
        print(msg)
        requests.post(
            f"https://api.github.com/repos/{repo}/issues/{issue_number}/comments",
            json={"body": msg}, headers=headers
        )
        return

    valid_indices  = {i for i in approved_indices if 1 <= i <= total}
    approved_items = [all_issues[i - 1] for i in sorted(valid_indices)]
    print(f"✅ 将修复第 {sorted(valid_indices)} 项，共 {len(approved_items)} 条")

    # ── Step 3：读取代码上下文，查 blame 作者，调 AI 生成修复方案 ────────────
    fix_requests = []
    for iss in approved_items:
        raw_path  = iss.get("component", "")
        file_path = raw_path.split(":")[-1] if ":" in raw_path else raw_path
        line      = iss.get("line", 1)
        author    = get_blame_author(file_path, line, repo, headers, sha_cache)
        fix_requests.append({
            "rule":         iss.get("rule"),
            "severity":     iss.get("severity"),
            "type":         iss.get("type"),
            "file":         file_path,
            "line":         line,
            "message":      iss.get("message"),
            "blame_author": author,
            "code_context": get_code_context(file_path, line)
        })

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
      "severity": "严重级别",
      "issue_desc": "缺陷简要说明",
      "original_snippet": "在 code_context 中真实存在、100%一字不差的原代码片段",
      "fixed_snippet": "修复后的新代码"
    }
  ],
  "skip_list": [
    {
      "file_path": "文件路径",
      "reason": "跳过原因（如：无法精确匹配代码、有业务风险需人工确认等）"
    }
  ]
}

规则：
- original_snippet 必须在提供的真实 code_context 中完全精确匹配！不得虚构代码！
- 无法精确匹配或有业务风险的问题放入 skip_list 并说明原因
- .github/ 目录下的文件一律放入 skip_list！
"""

    print(f"🤖 调用 AI 为 {len(fix_requests)} 条问题生成修复方案...")
    resp = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user",   "content": f"待修复缺陷如下：\n{json.dumps(fix_requests, ensure_ascii=False, indent=2)}"}
        ],
        response_format={"type": "json_object"}
    )

    decision = json.loads(resp.choices[0].message.content)
    print(f"🤖 AI 决策：\n{json.dumps(decision, ensure_ascii=False, indent=2)}")

    for skip in decision.get("skip_list", []):
        print(f"⏭️ 跳过 {skip.get('file_path')}: {skip.get('reason')}")

    # ── Step 4：应用代码修改 ───────────────────────────────────────────────
    auto_fixes = decision.get("auto_fix_list", [])
    if not auto_fixes:
        msg = "⚠️ AI 判断批准的问题均无法安全自动修复，请手动处理。详见日志。"
        print(msg)
        requests.post(
            f"https://api.github.com/repos/{repo}/issues/{issue_number}/comments",
            json={"body": msg}, headers=headers
        )
        return

    branch_name = f"ai-fix-issue-{issue_number}-{os.environ.get('GITHUB_RUN_ID', 'dev')}"
    run_cmd(f"git checkout -b {branch_name}")
    modified   = False
    fix_descs  = []
    pr_authors = set()  # 收集涉及文件的提交人

    for fix in auto_fixes:
        fp    = fix["file_path"]
        orig  = fix["original_snippet"]
        fixed = fix["fixed_snippet"]
        sev   = fix.get("severity", "")

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
            # 从 fix_requests 中找 blame_author
            author = next(
                (r.get("blame_author") for r in fix_requests if r["file"] == fp),
                None
            )
            author_str = f"（提交人：{author}）" if author else ""
            fix_descs.append(f"- [{sev}] `{fp}`{author_str}: {fix['issue_desc']}")
            if author:
                pr_authors.add(author)
            print(f"✅ 成功修复: {fp}  提交人: {author or '未知'}")
        else:
            print(f"⚠️ 未能精确匹配旧代码，跳过: {fp}")

    if not modified:
        msg = "⚠️ 所有修复项均未能精确匹配文件内容，请手动处理。"
        print(msg)
        requests.post(
            f"https://api.github.com/repos/{repo}/issues/{issue_number}/comments",
            json={"body": msg}, headers=headers
        )
        return

    # ── Step 5：Spotless 格式化、提交、推送、建 PR ────────────────────────────
    print("🎨 执行 Spotless 格式化...")
    run_cmd("mvn spotless:apply -q || ./gradlew spotlessApply || true")
    run_cmd("git config user.name 'github-actions[bot]'")
    run_cmd("git config user.email 'github-actions[bot]@users.noreply.github.com'")
    run_cmd("git add .")
    run_cmd(f"git commit -m 'fix: AI 根据 Issue #{issue_number} 修复 Sonar 缺陷（第 {batch_index} 批）'")
    run_cmd(f"git push origin {branch_name} --force")

    indices_str    = ",".join(str(i) for i in sorted(valid_indices))
    author_notice  = (
        f"\n\n> 📢 **涉及代码提交人**：{' '.join(sorted(pr_authors))}  请关注此 PR。"
        if pr_authors else ""
    )
    pr_payload = {
        "title": f"🤖 [AI Fix] Issue #{issue_number} 第 {batch_index}/{total_batches} 批 Sonar 缺陷修复（项 {indices_str}）",
        "head":  branch_name,
        "base":  "htz",
        "body":  (
            f"关联 Issue: #{issue_number}（第 {batch_index}/{total_batches} 批）\n"
            f"开发者指令：`{comment_body.strip()}`\n\n"
            f"**本次修复项：**\n" + "\n".join(fix_descs) + author_notice
            + "\n\n本 PR 由 AI 自动生成，已执行 Spotless 格式化，请 Code Review 后合并。"
        )
    }
    pr_res = requests.post(f"https://api.github.com/repos/{repo}/pulls", json=pr_payload, headers=headers)
    if pr_res.status_code == 201:
        pr_url = pr_res.json().get("html_url")
        print(f"🎉 成功创建修复 PR: {pr_url}")

        # 在原 Issue 下回复 PR 链接，并 @提交人
        author_cc = f"\n\n> 📢 **涉及代码提交人**：{' '.join(sorted(pr_authors))}  请关注此 PR。" if pr_authors else ""
        reply_body = (
            f"✅ AI 已根据您的指令（`{comment_body.strip()}`）完成修复并创建 PR：{pr_url}\n\n"
            f"**修复项：**\n" + "\n".join(fix_descs) + author_cc
        )
        requests.post(
            f"https://api.github.com/repos/{repo}/issues/{issue_number}/comments",
            json={"body": reply_body}, headers=headers
        )
    else:
        print(f"❌ 创建 PR 失败! 状态码: {pr_res.status_code}, 错误: {pr_res.text}")

if __name__ == "__main__":
    main()