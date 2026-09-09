import os, re, requests, json, subprocess
from openai import OpenAI

SEVERITY_ORDER = {"BLOCKER": 0, "CRITICAL": 1, "MAJOR": 2, "MINOR": 3, "INFO": 4}
SEVERITY_EMOJI = {"BLOCKER": "🔴", "CRITICAL": "🟠", "MAJOR": "🟡", "MINOR": "🔵", "INFO": "⚪"}

def run_cmd(cmd):
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if res.returncode != 0:
        print(f"[CMD WARN] {cmd}\n{res.stderr.strip()}")
    return res.stdout.strip()

def get_code_context(file_path, target_line, radius=20):
    """读取指定行前后20行的真实代码"""
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

def apply_single_fix(content, orig, fixed):
    """智能代码替换，包含精确匹配、标准化换行/首尾空格容错匹配"""
    if not orig:
        return content, False

    # 1. 尝试直接精确匹配
    if orig in content:
        return content.replace(orig, fixed, 1), True

    # 2. 统一转换为 \n 换行后匹配
    orig_norm = orig.replace("\r\n", "\n")
    content_norm = content.replace("\r\n", "\n")
    fixed_norm = fixed.replace("\r\n", "\n")
    if orig_norm in content_norm:
        return content_norm.replace(orig_norm, fixed_norm, 1), True

    # 3. 去除首尾空白行后尝试匹配
    orig_strip = orig_norm.strip()
    if orig_strip and orig_strip in content_norm:
        return content_norm.replace(orig_strip, fixed_norm.strip(), 1), True

    # 4. 如果 orig 只有单行，尝试按行两端 strip 匹配
    orig_lines = [l.strip() for l in orig_norm.split("\n") if l.strip()]
    if len(orig_lines) == 1:
        single_target = orig_lines[0]
        c_lines = content_norm.split("\n")
        for idx, line in enumerate(c_lines):
            if line.strip() == single_target:
                if fixed_norm.strip():
                    indent = line[:len(line) - len(line.lstrip())]
                    replacement_lines = [indent + l.lstrip() if l.strip() else "" for l in fixed_norm.split("\n")]
                    c_lines[idx:idx+1] = replacement_lines
                else:
                    # 删除该行
                    c_lines.pop(idx)
                return "\n".join(c_lines), True

    return content, False

def get_blame_author(file_path, line, repo, headers, sha_cache):
    """通过 git blame 找到指定行的提交 SHA 并通过 GitHub API 映射 @login"""
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
    res = requests.post(
        f"https://api.github.com/repos/{repo}/labels",
        json={"name": label_name, "color": color, "description": description},
        headers=headers
    )
    if res.status_code not in (201, 422):
        print(f"⚠️ 创建 label 失败: {res.status_code} {res.text}")

def fetch_all_sonar_issues(sonar_token, project_key):
    """拉取全量未解决缺陷（忽略最低级别 MINOR 和 INFO，仅拉取 BLOCKER, CRITICAL, MAJOR）"""
    all_issues = []
    page, page_size = 1, 100
    while True:
        url = (
            f"https://sonarcloud.io/api/issues/search"
            f"?componentKeys={project_key}&resolved=false&ps={page_size}&p={page}"
            f"&severities=BLOCKER,CRITICAL,MAJOR"
        )
        res = requests.get(url, auth=(sonar_token, ""))
        if res.status_code != 200:
            print(f"❌ SonarCloud API 请求失败 (第{page}页): {res.text}")
            break
        data   = res.json()
        issues = data.get("issues", [])
        all_issues.extend(issues)
        total  = data.get("total", 0)
        print(f"  已获取 {len(all_issues)}/{total} 条重点缺陷...")
        if len(all_issues) >= total or not issues or len(all_issues) >= 200:
            break
        page += 1

    # 过滤掉 .github/ 目录下的配置告警，仅保留真实业务与代码工程文件
    valid_issues = []
    for iss in all_issues:
        raw_path = iss.get("component", "")
        file_path = raw_path.split(":")[-1] if ":" in raw_path else raw_path
        if file_path.startswith(".github/"):
            continue
        valid_issues.append(iss)

    valid_issues.sort(key=lambda x: SEVERITY_ORDER.get(x.get("severity", "MAJOR"), 99))
    return valid_issues

def create_review_issue(repo, headers, batch_issues, batch_title, batch_idx, total_batches):
    """为真正涉及业务缺陷或需人工决策的问题创建 Issue"""
    rows = []
    issue_authors = set()
    for i, iss in enumerate(batch_issues, 1):
        sev        = iss.get("severity", "MAJOR")
        emoji      = SEVERITY_EMOJI.get(sev, "⚪")
        file_path  = iss.get("file_path", "")
        desc       = iss.get("issue_desc", "")
        reason     = iss.get("reason", "")
        author     = iss.get("blame_author") or "-"
        if author and author != "-":
            issue_authors.add(author)
        rows.append(f"| {i} | {emoji} {sev} | `{file_path}` | {desc} | {reason} | {author} |")

    hidden_json = json.dumps(
        {"batch_index": batch_idx, "total_batches": total_batches, "issues": batch_issues},
        ensure_ascii=False
    )
    author_notice = (
        f"\n> 📢 **涉及代码提交人**：{' '.join(sorted(issue_authors))} 请关注此 Issue。\n"
        if issue_authors else ""
    )

    body = f"""## ⚠️ SonarCloud 业务代码缺陷待决策

> 以下缺陷涉及业务逻辑变更、算法重构或意图确认，AI 未自动执行修改，请人工审阅：
{author_notice}
| # | 严重性 | 文件 | 问题描述 | AI 判断原因 | 提交人 |
|---|--------|------|----------|------------|--------|
{chr(10).join(rows)}

---
### 💬 操作说明
若认可方案，请在此 Issue 下回复指令，AI 将自动完成修复并提 PR：
- `同意修复 1,3` → 仅修复指定项
- `执行全部` → 修复本 Issue 中的全部问题
- `跳过 2` → 排除指定项，修复其余全部

<!-- SONAR_ISSUE_DATA
{hidden_json}
-->
"""
    issue_payload = {
        "title": batch_title,
        "body":  body,
        "labels": ["ai-needs-decision"]
    }
    res = requests.post(f"https://api.github.com/repos/{repo}/issues", json=issue_payload, headers=headers)
    if res.status_code == 201:
        print(f"📌 已创建待审核 Issue: {res.json().get('html_url')}")
    else:
        print(f"❌ 创建 Issue 失败: {res.status_code} {res.text}")

def main():
    sonar_token = os.environ.get("SONAR_TOKEN")
    project_key = os.environ.get("SONAR_PROJECT_KEY")
    repo        = os.environ["GITHUB_REPOSITORY"]
    gh_token    = os.environ["GITHUB_TOKEN"]
    headers     = {"Authorization": f"Bearer {gh_token}", "Accept": "application/vnd.github.v3+json"}
    model       = os.environ.get("AI_MODEL", "deepseek-v4-flash")
    sha_cache   = {}

    ensure_label_exists(repo, headers, "ai-needs-decision")

    # ── Step 1：拉取所有非最低级别（BLOCKER, CRITICAL, MAJOR）的缺陷 ────────
    print("📡 正在从 SonarCloud 拉取所有核心缺陷（已忽略 MINOR/INFO 级别）...")
    issues = fetch_all_sonar_issues(sonar_token, project_key)

    if not issues:
        print("✅ SonarCloud 显示项目无任何高/中危缺陷！")
        return

    print(f"📊 共筛选出 {len(issues)} 个核心缺陷，正在读取代码上下文并查询提交人...")

    issues_summary = []
    for iss in issues:
        raw_path  = iss.get("component", "")
        file_path = raw_path.split(":")[-1] if ":" in raw_path else raw_path
        line      = iss.get("line", 1)
        author    = get_blame_author(file_path, line, repo, headers, sha_cache)
        issues_summary.append({
            "key":          iss.get("key"),
            "rule":         iss.get("rule"),
            "severity":     iss.get("severity"),
            "type":         iss.get("type"),
            "file":         file_path,
            "line":         line,
            "message":      iss.get("message"),
            "blame_author": author,
            "code_context": get_code_context(file_path, line)
        })

    # ── Step 2：统一交给 AI 分析，简单非业务问题全部自动修，只有业务缺陷提 Issue ──
    print("🤖 AI 正在进行全局审查：非业务代码缺陷直接修复，仅业务疑难缺陷提取 Issue...")
    client = OpenAI(
        api_key=os.environ["AI_API_KEY"],
        base_url=os.environ.get("AI_BASE_URL", "https://api.deepseek.com/v1")
    )

    system_prompt = """
你是一名极其资深的 Java 架构师。针对 SonarCloud 发现的代码缺陷进行分流与自动修复。
必须全部使用简体中文输出！

【极简原则】：
1. 绝大部分非业务缺陷（单行或几行即可解决的代码异味、安全编码规范、死代码、空指针等），全部放入 auto_fix_list 自动修复！
2. 只有真正涉及核心业务流程变化、可能改变功能行为、需要产品研发决策的疑难缺陷，才放入 need_review_list 提 Issue 人工审核！
3. 测试类误报或完全无需修改的放入 ignore_list。

严格输出以下格式的 JSON，不要包含任何 markdown 标记：
{
  "auto_fix_list": [
    {
      "file_path": "文件路径",
      "severity": "严重级别",
      "issue_desc": "缺陷简要说明",
      "original_snippet": "待替换/删除的目标代码片段（只包含目标行，严禁包含无关周边行）",
      "fixed_snippet": "修复后的新代码（如果是删除语句或死代码，直接填空字符串 \"\"）"
    }
  ],
  "need_review_list": [
    {
      "file_path": "文件路径",
      "severity": "严重级别",
      "issue_desc": "缺陷简要说明",
      "reason": "需要人工审核的理由（具体改变了什么业务逻辑或决策）"
    }
  ],
  "ignore_list": [
    {
      "file_path": "文件路径",
      "reason": "忽略原因"
    }
  ]
}

【常见自动修复标准（一律放入 auto_fix_list）】：
- 明显的致错空指针（如方法形参被置空 `request = null;`、`user = null;`），直接将该置空语句整行删除（fixed_snippet 传 ""）！
- 无用局部变量与死代码（如 `String rawSql = ...;`、`String hardcodedJwtSecret = ...;` 未使用），直接整行删除！
- 注释掉的代码块（S125），直接删除！
- 工具类缺少私有构造函数（S1118），为工具类添加 private 构造方法！
- Spring 注解规范（如 @Component 改为 @Service）（S5673），直接替换注解！
- 未使用方法返回值（如 `orElseGet` 未使用），加上合理赋值或防御检查！
- 常量判空反转（`"ABC".equals(val)` 代替 `val.equals("ABC")`）。

【至关重要的精准替换规则】：
- original_snippet 必须是【最小化目标行】（通常仅 1 行），严禁包含未修改的方法声明、括号或周边行！
- 删除代码时：fixed_snippet 直接填空字符串 ""。
"""

    resp = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user",   "content": f"全部核心缺陷列表如下：\n{json.dumps(issues_summary, ensure_ascii=False, indent=2)}"}
        ],
        response_format={"type": "json_object"}
    )

    decision = json.loads(resp.choices[0].message.content)
    print(f"🤖 AI 决策输出：\n{json.dumps(decision, ensure_ascii=False, indent=2)}")

    auto_fixes  = decision.get("auto_fix_list", [])
    need_review = decision.get("need_review_list", [])
    ignore_list = decision.get("ignore_list", [])

    if ignore_list:
        print(f"🔕 忽略项（共 {len(ignore_list)} 条）")

    # ── Step 3：将所有简单非业务缺陷【全部一次性自动修复并提 PR】 ─────────
    if auto_fixes:
        print(f"\n🚀 发现 {len(auto_fixes)} 个可自动解决的代码缺陷，正在统一修复提 PR...")
        branch_name = f"sonar-autofix-{os.environ.get('GITHUB_RUN_ID', 'dev')}"
        run_cmd(f"git checkout -b {branch_name}")
        modified   = False
        fix_descs  = []
        pr_authors = set()

        for fix in auto_fixes:
            fp    = fix["file_path"]
            orig  = fix["original_snippet"]
            fixed = fix.get("fixed_snippet", "")
            sev   = fix.get("severity", "MAJOR")

            if not os.path.exists(fp):
                print(f"⚠️ 文件不存在，跳过: {fp}")
                continue

            with open(fp, "r", encoding="utf-8") as f:
                content = f.read()

            new_content, success = apply_single_fix(content, orig, fixed)
            if success:
                with open(fp, "w", encoding="utf-8") as f:
                    f.write(new_content)
                modified = True
                author = next((s.get("blame_author") for s in issues_summary if s["file"] == fp), None)
                author_str = f"（提交人：{author}）" if author else ""
                fix_descs.append(f"- [{sev}] `{fp}`{author_str}: {fix['issue_desc']}")
                if author and author != "-":
                    pr_authors.add(author)
                print(f"✅ 成功修复: {fp}  {author_str}")
            else:
                print(f"⚠️ 未能精确匹配旧代码，跳过: {fp} -> {repr(orig)}")

        if modified:
            print("🎨 执行 Spotless 格式化...")
            run_cmd("mvn spotless:apply -q || ./gradlew spotlessApply || true")
            run_cmd("git config user.name 'github-actions[bot]'")
            run_cmd("git config user.email 'github-actions[bot]@users.noreply.github.com'")
            run_cmd("git add .")
            run_cmd(f"git commit -m 'fix: AI 自动批量修复 {len(fix_descs)} 项非业务代码缺陷'")
            run_cmd(f"git push origin {branch_name} --force")

            author_notice = (
                f"\n\n> 📢 **涉及代码提交人**：{' '.join(sorted(pr_authors))} 请关注此 PR。"
                if pr_authors else ""
            )
            pr_payload = {
                "title": f"🤖 [Sonar+AI Auto-Fix] 自动批量修复 {len(fix_descs)} 项代码缺陷",
                "head":  branch_name,
                "base":  "htz",
                "body":  (
                    f"### SonarCloud 代码缺陷自动批量修复报告\n"
                    f"本次流水线共自动识别并消除了 **{len(fix_descs)}** 项非业务代码缺陷/安全隐患，已执行 Spotless 格式化。\n\n"
                    f"**修复清单：**\n" + "\n".join(fix_descs) + author_notice
                )
            }
            pr_res = requests.post(f"https://api.github.com/repos/{repo}/pulls", json=pr_payload, headers=headers)
            if pr_res.status_code == 201:
                print(f"🎉 成功创建自动修复 PR: {pr_res.json().get('html_url')}")
            else:
                print(f"❌ 创建 PR 失败: {pr_res.status_code} {pr_res.text}")
        else:
            print("ℹ️ 所有自动修复项未产生实际变更")
    else:
        print("ℹ️ 未发现可直接自动修复的代码缺陷")

    # ── Step 4：仅对涉及业务缺陷的问题提 Issue（>10条才分批，<=10条提1个Issue） ─
    if need_review:
        # 补全 blame 作者
        for item in need_review:
            fp = item.get("file_path", "")
            item["blame_author"] = next((s.get("blame_author") for s in issues_summary if s["file"] == fp), None)

        total_review = len(need_review)
        print(f"\n⚠️ 发现 {total_review} 个真正涉及业务逻辑的缺陷，正在生成 Issue...")

        if total_review <= 10:
            title = f"⚠️ [Sonar 待决策] 涉及业务逻辑的代码缺陷（共 {total_review} 项）"
            create_review_issue(repo, headers, need_review, title, 1, 1)
        else:
            batch_size = 10
            batches = [need_review[i:i+batch_size] for i in range(0, total_review, batch_size)]
            for idx, batch in enumerate(batches, 1):
                title = f"⚠️ [Sonar 待决策] 涉及业务代码缺陷（第 {idx}/{len(batches)} 批，共 {len(batch)} 项）"
                create_review_issue(repo, headers, batch, title, idx, len(batches))
    else:
        print("🎉 没有需要人工审核的复杂业务缺陷！不需要打扰开发者！")

    print("\n✅ 流水线治理分析完成！")

if __name__ == "__main__":
    main()