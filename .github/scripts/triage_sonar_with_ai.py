import os, re, requests, json, subprocess
from openai import OpenAI

SEVERITY_ORDER = {"BLOCKER": 0, "CRITICAL": 1, "MAJOR": 2, "MINOR": 3, "INFO": 4}
SEVERITY_EMOJI = {"BLOCKER": "🔴", "CRITICAL": "🟠", "MAJOR": "🟡", "MINOR": "🔵", "INFO": "⚪"}

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
    通过 git blame 找到指定行的提交 SHA，再通过 GitHub API 获取提交者 GitHub 账号。
    sha_cache: dict，避免对同一 SHA 重复发起 API 请求。
    返回 "@login" 或 "作者名"（API 失败时兜底）。
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
        # 全零 SHA 表示未提交的改动
        if not sha or sha == "0" * 40:
            return None

        # 先查缓存
        if sha in sha_cache:
            return sha_cache[sha]

        # 解析本地 blame 输出中的作者名（兜底用）
        author_name = next(
            (l[7:] for l in blame_lines if l.startswith("author ")), ""
        ).strip()

        # 通过 GitHub API 获取 @login
        res = requests.get(
            f"https://api.github.com/repos/{repo}/commits/{sha}",
            headers=headers
        )
        if res.status_code == 200:
            gh_user = res.json().get("author")
            login = gh_user.get("login") if gh_user else None
            mention = f"@{login}" if login else author_name
        else:
            mention = author_name

        sha_cache[sha] = mention
        return mention
    except Exception as e:
        print(f"[WARN] git blame 失败 {file_path}:{line} - {e}")
        return None

def ensure_label_exists(repo, headers, label_name, color="e11d48", description="AI 待决策"):
    """确保 GitHub label 存在，不存在则自动创建"""
    res = requests.post(
        f"https://api.github.com/repos/{repo}/labels",
        json={"name": label_name, "color": color, "description": description},
        headers=headers
    )
    if res.status_code == 201:
        print(f"✅ 已自动创建 label: {label_name}")
    elif res.status_code != 422:
        print(f"⚠️ 创建 label 失败: {res.status_code} {res.text}")

def fetch_all_sonar_issues(sonar_token, project_key):
    """分页拉取全量 SonarCloud 问题，最多500条，按严重性排序"""
    all_issues = []
    page, page_size = 1, 100
    while True:
        url = (
            f"https://sonarcloud.io/api/issues/search"
            f"?componentKeys={project_key}&resolved=false&ps={page_size}&p={page}"
        )
        res = requests.get(url, auth=(sonar_token, ""))
        if res.status_code != 200:
            print(f"❌ SonarCloud API 请求失败 (第{page}页): {res.text}")
            break
        data   = res.json()
        issues = data.get("issues", [])
        all_issues.extend(issues)
        total  = data.get("total", 0)
        print(f"  已获取 {len(all_issues)}/{total} 条...")
        if len(all_issues) >= total or not issues or len(all_issues) >= 500:
            break
        page += 1

    all_issues.sort(key=lambda x: SEVERITY_ORDER.get(x.get("severity", "INFO"), 99))
    return all_issues

def create_batch_issue(repo, headers, batch_issues, batch_index, total_batches, sha_cache):
    """将一批问题创建为 GitHub Issue（直接展示原始数据，不调 AI，附带 @提交人）"""
    rows = []
    for i, iss in enumerate(batch_issues, 1):
        sev        = iss.get("severity", "INFO")
        emoji      = SEVERITY_EMOJI.get(sev, "⚪")
        raw_path   = iss.get("component", "")
        file_path  = raw_path.split(":")[-1] if ":" in raw_path else raw_path
        line       = iss.get("line", 1)
        msg        = iss.get("message", "")[:100]
        issue_type = iss.get("type", "")
        rule       = iss.get("rule", "")
        # 查 blame 作者（批量 Issue 只用本地 git blame，不调 GitHub API 避免限速）
        author = get_blame_author(file_path, line, repo, headers, sha_cache) or "-"
        rows.append(
            f"| {i} | {emoji} {sev} | {issue_type} | `{file_path}` | {line} | {msg} | {rule} | {author} |"
        )

    hidden_json = json.dumps(
        {"batch_index": batch_index, "total_batches": total_batches, "issues": batch_issues},
        ensure_ascii=False
    )

    body = f"""## 🔍 SonarCloud 代码缺陷待处理（第 {batch_index} 批 / 共 {total_batches} 批）

| # | 严重性 | 类型 | 文件 | 行号 | 问题描述 | 规则 | 提交人 |
|---|--------|------|------|------|----------|------|--------|
{chr(10).join(rows)}

---
### 💬 操作说明
请在此 Issue 下回复您希望修复的项目，AI 将自动生成修复代码并发起 Pull Request：

- `同意修复 1,3,5` → 仅修复第 1、3、5 项
- `执行全部` → 修复本批全部问题
- `跳过 2,4` → 修复除第 2、4 项外的全部问题

<!-- SONAR_ISSUE_DATA
{hidden_json}
-->
"""
    issue_payload = {
        "title": f"⚠️ [Sonar 待决策] 第 {batch_index}/{total_batches} 批代码缺陷（{len(batch_issues)} 项）",
        "body":  body,
        "labels": ["ai-needs-decision"]
    }
    res = requests.post(f"https://api.github.com/repos/{repo}/issues", json=issue_payload, headers=headers)
    if res.status_code == 201:
        print(f"📌 已创建第 {batch_index} 批 Issue: {res.json().get('html_url')}")
    else:
        print(f"❌ 创建 Issue 失败: {res.status_code} {res.text}")

def main():
    sonar_token = os.environ.get("SONAR_TOKEN")
    project_key = os.environ.get("SONAR_PROJECT_KEY")
    repo        = os.environ["GITHUB_REPOSITORY"]
    gh_token    = os.environ["GITHUB_TOKEN"]
    headers     = {"Authorization": f"Bearer {gh_token}", "Accept": "application/vnd.github.v3+json"}
    model       = os.environ.get("AI_MODEL", "deepseek-v4-flash")
    sha_cache   = {}  # SHA -> @mention 缓存，避免重复 API 调用

    ensure_label_exists(repo, headers, "ai-needs-decision")

    # ── Step 1：拉取全量问题并按严重性排序 ──────────────────────────────────
    print("📡 正在从 SonarCloud 拉取全量问题...")
    all_issues = fetch_all_sonar_issues(sonar_token, project_key)

    if not all_issues:
        print("✅ SonarCloud 显示项目无任何未解决问题！")
        return

    total     = len(all_issues)
    top10     = all_issues[:10]
    remaining = all_issues[10:]
    print(f"📊 共 {total} 个问题：前 {len(top10)} 条自动分析，剩余 {len(remaining)} 条创建 Issue")

    # ── Step 2：对前10条高危问题 AI 三级分流 ─────────────────────────────────
    print(f"\n🔍 读取前 {len(top10)} 条代码上下文并查询提交人...")
    top10_summary = []
    for iss in top10:
        raw_path  = iss.get("component", "")
        file_path = raw_path.split(":")[-1] if ":" in raw_path else raw_path
        line      = iss.get("line", 1)
        author    = get_blame_author(file_path, line, repo, headers, sha_cache)
        top10_summary.append({
            "rule":         iss.get("rule"),
            "severity":     iss.get("severity"),
            "type":         iss.get("type"),
            "file":         file_path,
            "line":         line,
            "message":      iss.get("message"),
            "blame_author": author,
            "code_context": get_code_context(file_path, line)
        })

    print(f"🤖 AI 正在对前 {len(top10)} 条进行三级分流分析...")
    client = OpenAI(
        api_key=os.environ["AI_API_KEY"],
        base_url=os.environ.get("AI_BASE_URL", "https://api.deepseek.com/v1")
    )

    system_prompt = """
你是一名极其资深的 Java 架构师。对 SonarCloud 发现的代码缺陷进行三级分流，必须全部使用简体中文输出！

严格输出以下格式的 JSON，不要包含任何 markdown 标记：
{
  "auto_fix_list": [
    {
      "file_path": "文件路径",
      "severity": "严重级别",
      "issue_desc": "缺陷简要说明",
      "original_snippet": "在 code_context 中100%一字不差存在的原代码片段",
      "fixed_snippet": "修复后的新代码"
    }
  ],
  "need_review_list": [
    {
      "file_path": "文件路径",
      "severity": "严重级别",
      "issue_desc": "缺陷简要说明",
      "reason": "需要人工确认的原因（涉及业务逻辑/难以判断/改动影响范围大等）"
    }
  ],
  "ignore_list": [
    {
      "file_path": "文件路径",
      "issue_desc": "缺陷简要说明",
      "reason": "忽略原因（误报/低风险/框架已兜底/测试类等）"
    }
  ]
}

【三级分流标准】：
- auto_fix_list（直接修复）：纯语法异味、NPE 防御、漏加 @Transactional、简单判空反转等，改动无业务副作用，且 original_snippet 能在 code_context 中精确匹配。
- need_review_list（提 Issue 人工确认）：涉及业务逻辑改动、重构、安全漏洞需上下文确认、改动影响面广、难以判断是否安全、无法精确匹配代码的。
- ignore_list（直接忽略）：测试类文件、配置文件误报、框架已兜底绝不会发生的问题、风险极低无修复必要的代码异味。

【强制规则】：
- original_snippet 必须在提供的真实 code_context 中完全精确匹配！不得虚构代码！
- .github/ 目录下的文件一律放入 need_review_list，禁止自动修复！
- 宁可放入 need_review_list 也不要凭猜测放入 auto_fix_list！
"""

    resp = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user",   "content": f"待分析缺陷如下：\n{json.dumps(top10_summary, ensure_ascii=False, indent=2)}"}
        ],
        response_format={"type": "json_object"}
    )

    decision = json.loads(resp.choices[0].message.content)
    print(f"🤖 AI 决策：\n{json.dumps(decision, ensure_ascii=False, indent=2)}")

    # ── 忽略项：只打日志 ────────────────────────────────────────────────────
    ignore_list = decision.get("ignore_list", [])
    if ignore_list:
        print(f"\n🔕 AI 判定为忽略的问题（共 {len(ignore_list)} 条）：")
        for item in ignore_list:
            print(f"  - {item.get('file_path')}: {item.get('reason')}")

    # ── 自动修复项：应用代码替换 → 提 PR（PR 中 @提交人） ──────────────────
    auto_fixes = decision.get("auto_fix_list", [])
    if auto_fixes:
        branch_name = f"sonar-autofix-{os.environ.get('GITHUB_RUN_ID', 'dev')}"
        run_cmd(f"git checkout -b {branch_name}")
        modified   = False
        fix_descs  = []
        # 收集涉及文件的提交人，去重后在 PR 中统一 @mention
        pr_authors = set()

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
                # 从 top10_summary 中找对应的 blame_author
                author = next(
                    (s.get("blame_author") for s in top10_summary if s["file"] == fp),
                    None
                )
                author_str = f"（提交人：{author}）" if author else ""
                fix_descs.append(f"- [{sev}] `{fp}`{author_str}: {fix['issue_desc']}")
                if author:
                    pr_authors.add(author)
                print(f"✅ 成功修复: {fp}  提交人: {author or '未知'}")
            else:
                print(f"⚠️ 未能精确匹配旧代码，跳过: {fp}")

        if modified:
            print("🎨 执行 Spotless 格式化...")
            run_cmd("mvn spotless:apply -q || ./gradlew spotlessApply || true")
            run_cmd("git config user.name 'github-actions[bot]'")
            run_cmd("git config user.email 'github-actions[bot]@users.noreply.github.com'")
            run_cmd("git add .")
            run_cmd("git commit -m 'fix: AI 自动修复 Sonar TOP10 高危缺陷'")
            run_cmd(f"git push origin {branch_name} --force")

            author_notice = (
                f"\n\n> 📢 **涉及代码提交人**：{' '.join(sorted(pr_authors))}  请关注此 PR。"
                if pr_authors else ""
            )
            pr_payload = {
                "title": "🤖 [Sonar+AI] 自动修复高危代码缺陷",
                "head":  branch_name,
                "base":  "htz",
                "body":  (
                    "### SonarCloud 高危缺陷自动修复报告\n"
                    "本 PR 由 AI 对按严重性排序的前10条问题进行三级分流后自动修复，已执行 Spotless 格式化。\n\n"
                    "**修复项：**\n" + "\n".join(fix_descs) + author_notice
                )
            }
            pr_res = requests.post(f"https://api.github.com/repos/{repo}/pulls", json=pr_payload, headers=headers)
            if pr_res.status_code == 201:
                print(f"🎉 成功创建修复 PR: {pr_res.json().get('html_url')}")
            else:
                print(f"❌ 创建 PR 失败! 状态码: {pr_res.status_code}, 错误: {pr_res.text}")
        else:
            print("ℹ️ 所有自动修复项均未能精确匹配，未产生代码变更")
    else:
        print("ℹ️ AI 判断前10条中无可安全自动修复的问题")

    # ── 需人工确认项：创建 Issue，附 @提交人，复用隐藏 JSON 支持评论触发修复 ──
    need_review = decision.get("need_review_list", [])
    if need_review:
        print(f"\n⚠️ 需人工确认的问题（共 {len(need_review)} 条），正在创建 Issue...")

        # 从 top10_summary 找到对应的 blame_author 和原始 issue 数据
        def find_raw_and_author(file_path, issue_desc):
            for s, iss in zip(top10_summary, top10):
                if s["file"] == file_path:
                    return iss, s.get("blame_author")
            return None, None

        rows = []
        matched_raw_issues = []
        issue_authors = set()
        for i, item in enumerate(need_review, 1):
            sev    = item.get("severity", "")
            emoji  = SEVERITY_EMOJI.get(sev, "⚪")
            fp     = item.get("file_path", "")
            desc   = item.get("issue_desc", "")
            reason = item.get("reason", "")
            raw, author = find_raw_and_author(fp, desc)
            author_str  = author or "-"
            if author:
                issue_authors.add(author)
            rows.append(f"| {i} | {emoji} {sev} | `{fp}` | {desc} | {reason} | {author_str} |")
            matched_raw_issues.append(raw if raw else {
                "component": fp, "severity": sev, "message": desc, "type": "REVIEW_REQUIRED"
            })

        hidden_json = json.dumps(
            {"batch_index": "TOP10-REVIEW", "total_batches": "TOP10-REVIEW",
             "issues": matched_raw_issues},
            ensure_ascii=False
        )
        author_notice = (
            f"\n> 📢 **涉及代码提交人**：{' '.join(sorted(issue_authors))}  请关注此 Issue。\n"
            if issue_authors else ""
        )

        body = f"""## ⚠️ SonarCloud TOP10 中需人工确认的问题（共 {len(need_review)} 项）

> AI 已对前10条高危问题完成三级分流：
> - ✅ 可安全修复的已自动提 PR
> - 🔕 误报/低风险的已忽略
> - ⚠️ 以下问题涉及业务逻辑或难以判断，需您决策
{author_notice}
| # | 严重性 | 文件 | 问题描述 | AI 判断原因 | 提交人 |
|---|--------|------|----------|------------|--------|
{chr(10).join(rows)}

---
### 💬 操作说明
请在此 Issue 下回复您希望修复的项目：
- `同意修复 1,3` → 仅修复第 1、3 项
- `执行全部` → 修复本 Issue 全部问题
- `跳过 2` → 修复除第 2 项外的全部

<!-- SONAR_ISSUE_DATA
{hidden_json}
-->
"""
        issue_payload = {
            "title": f"⚠️ [Sonar TOP10 待决策] {len(need_review)} 个问题涉及业务逻辑，需人工确认",
            "body":  body,
            "labels": ["ai-needs-decision"]
        }
        iss_res = requests.post(f"https://api.github.com/repos/{repo}/issues", json=issue_payload, headers=headers)
        if iss_res.status_code == 201:
            print(f"📌 已创建 TOP10 待决策 Issue: {iss_res.json().get('html_url')}")
        else:
            print(f"❌ 创建 Issue 失败: {iss_res.status_code} {iss_res.text}")

    # ── Step 3：剩余问题每10条一个 Issue，不调 AI，附 @提交人 ─────────────────
    if remaining:
        batch_size    = 10
        batches       = [remaining[i:i+batch_size] for i in range(0, len(remaining), batch_size)]
        total_batches = len(batches)
        print(f"\n📋 剩余 {len(remaining)} 条问题，分为 {total_batches} 批创建 Issue（附提交人，无需 AI）...")
        for idx, batch in enumerate(batches, 1):
            create_batch_issue(repo, headers, batch, idx, total_batches, sha_cache)

    print(f"\n✅ 全部处理完毕：前10条已分流，剩余 {len(remaining)} 条已按批创建 Issue 等待决策")

if __name__ == "__main__":
    main()