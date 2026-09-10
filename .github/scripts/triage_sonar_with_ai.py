import os, re, requests, json, subprocess
from openai import OpenAI

SEVERITY_ORDER = {"BLOCKER": 0, "CRITICAL": 1, "MAJOR": 2, "MINOR": 3, "INFO": 4}
SEVERITY_EMOJI = {"BLOCKER": "🔴", "CRITICAL": "🟠", "MAJOR": "🟡", "MINOR": "🔵", "INFO": "⚪"}

def run_cmd(cmd):
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if res.returncode != 0:
        print(f"[CMD WARN] {cmd}\n{res.stderr.strip()}")
    return res.stdout.strip()

def send_feishu_card(webhook_url, title, summary_markdown, button_text=None, button_url=None, color="green"):
    """
    向飞书群机器人发送富文本互动卡片（Interactive Card）。
    若未配置 webhook_url 则静默跳过；发生网络异常仅打印提示，绝不阻塞主构建流程。
    """
    if not webhook_url or not webhook_url.strip():
        return

    elements = [
        {
            "tag": "div",
            "text": {
                "tag": "lark_md",
                "content": summary_markdown
            }
        }
    ]

    if button_text and button_url:
        elements.append({
            "tag": "action",
            "actions": [
                {
                    "tag": "button",
                    "text": {
                        "tag": "plain_text",
                        "content": button_text
                    },
                    "type": "primary",
                    "url": button_url
                }
            ]
        })

    payload = {
        "msg_type": "interactive",
        "card": {
            "config": {
                "wide_screen_mode": True
            },
            "header": {
                "title": {
                    "tag": "plain_text",
                    "content": title
                },
                "template": color
            },
            "elements": elements
        }
    }

    try:
        res = requests.post(webhook_url.strip(), json=payload, timeout=8)
        if res.status_code == 200:
            print("📲 飞书群通知卡片发送成功！")
        else:
            print(f"⚠️ 飞书通知发送返回非200: {res.status_code} {res.text}")
    except Exception as e:
        print(f"⚠️ 发送飞书通知网络异常（不影响主流程）: {e}")

def get_file_range_context(file_path, min_line, max_line, radius=15):
    """根据文件中所有缺陷的行号范围，读取连贯的代码上下文（文件<=150行时直接提供全文）"""
    if not os.path.exists(file_path):
        return ""
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            lines = f.readlines()
        if len(lines) <= 150:
            return "".join(lines)
        start = max(0, min_line - radius - 1)
        end   = min(len(lines), max_line + radius)
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

def is_low_or_info(iss):
    """
    判断缺陷是否属于 LOW 或 INFO 级别（不处理）：
    1. 优先根据 SonarCloud 新版 Clean Code impacts（HIGH, MEDIUM, LOW）判断；
    2. 若无 impacts，按传统 severity（MINOR 对应 LOW，INFO 对应 INFO）判断。
    """
    impacts = iss.get("impacts", [])
    if impacts:
        severities = {imp.get("severity", "").upper() for imp in impacts}
        if any(s in ("HIGH", "MEDIUM", "BLOCKER", "CRITICAL", "MAJOR") for s in severities):
            return False
        return True

    sev = iss.get("severity", "").upper()
    return sev in ("MINOR", "INFO", "LOW")

def fetch_all_sonar_issues(sonar_token, project_key, branch=None):
    """拉取未解决缺陷（排除 LOW/INFO 级别，仅保留 HIGH 和 MEDIUM 重点缺陷）"""
    all_issues = []
    page, page_size = 1, 100
    use_impact_param = True

    while True:
        impact_param = "&impactSeverities=HIGH,MEDIUM" if use_impact_param else ""
        url = (
            f"https://sonarcloud.io/api/issues/search"
            f"?componentKeys={project_key}&issueStatuses=OPEN,CONFIRMED{impact_param}&ps={page_size}&p={page}"
        )
        if branch:
            url += f"&branch={branch}"

        res = requests.get(url, auth=(sonar_token, ""))
        if res.status_code != 200:
            # 若带 impactSeverities 报错（例如旧版接口），尝试关闭该参数降级拉取并在内存过滤
            if use_impact_param:
                print(f"⚠️ impactSeverities 参数不受支持，降级为全量拉取后本地过滤...")
                use_impact_param = False
                continue

            # 若指定 branch 失败，自动降级为默认/主分支检索
            if branch:
                print(f"⚠️ 携带 branch={branch} 请求失败 (状态码 {res.status_code})，尝试不带 branch 请求...")
                branch = None
                continue

            print(f"❌ SonarCloud API 请求失败 (第{page}页): {res.text}")
            break

        data   = res.json()
        issues = data.get("issues", [])
        all_issues.extend(issues)
        total  = data.get("total", 0)
        print(f"  已获取 {len(all_issues)}/{total} 条缺陷...")
        if len(all_issues) >= total or not issues:
            break
        page += 1

    # 过滤掉 .github/ 目录以及 LOW / INFO 级别的缺陷
    valid_issues = []
    for iss in all_issues:
        raw_path = iss.get("component", "")
        file_path = raw_path.split(":")[-1] if ":" in raw_path else raw_path
        if file_path.startswith(".github/"):
            continue
        if is_low_or_info(iss):
            continue
        valid_issues.append(iss)

    valid_issues.sort(key=lambda x: SEVERITY_ORDER.get(x.get("severity", "MAJOR"), 99))
    return valid_issues

def build_file_grouped_batches(issues, repo, headers, sha_cache, batch_size=20):
    """
    将 issues 按文件归类聚合，并装箱为每批约 20 个缺陷。
    核心优势：
    1. 同一个文件的所有缺陷 100% 聚在同一个批次内，绝不割裂。
    2. 同一个文件的代码上下文只读取/发送 1 次，极度节约输入阅读 Token。
    """
    from collections import defaultdict
    files_map = defaultdict(list)
    for iss in issues:
        raw_path  = iss.get("component", "")
        file_path = raw_path.split(":")[-1] if ":" in raw_path else raw_path
        files_map[file_path].append(iss)

    batches = []
    current_batch = []
    current_count = 0

    for file_path, file_issues in files_map.items():
        # 若当前批次加上本文件 issues 超过 batch_size，且当前已有内容，则开启新批次
        if current_count + len(file_issues) > batch_size and current_count > 0:
            batches.append(current_batch)
            current_batch = []
            current_count = 0

        lines = [iss.get("line", 1) for iss in file_issues if iss.get("line") is not None]
        min_line = min(lines) if lines else 1
        max_line = max(lines) if lines else 1
        author   = get_blame_author(file_path, min_line, repo, headers, sha_cache)
        code_ctx = get_file_range_context(file_path, min_line, max_line, radius=15)

        file_obj = {
            "file": file_path,
            "blame_author": author,
            "code_context": code_ctx,
            "issues": [
                {
                    "key":      iss.get("key"),
                    "line":     iss.get("line"),
                    "rule":     iss.get("rule"),
                    "severity": iss.get("severity"),
                    "type":     iss.get("type"),
                    "message":  iss.get("message")
                }
                for iss in file_issues
            ]
        }
        current_batch.append(file_obj)
        current_count += len(file_issues)

    if current_batch:
        batches.append(current_batch)

    return batches

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
        issue_url = res.json().get("html_url")
        print(f"📌 已创建待审核 Issue: {issue_url}")

        feishu_url = os.environ.get("FEISHU_WEBHOOK_URL")
        authors_str = " ".join(sorted(issue_authors)) if issue_authors else "暂无"
        display_issues = [f"• [{iss.get('severity')}] `{iss.get('file_path')}`: {iss.get('issue_desc')}" for iss in batch_issues[:6]]
        more_note = f"\n*...等共 {len(batch_issues)} 项待决策*" if len(batch_issues) > 6 else ""
        issue_list_str = "\n".join(display_issues) + more_note

        summary_md = (
            f"**📦 仓库：** `{repo}`\n"
            f"**⚠️ 待审核项：** 本批共 **{len(batch_issues)}** 项代码缺陷涉及业务逻辑，需人工确认\n"
            f"**📢 涉及提交人：** {authors_str}\n\n"
            f"---\n"
            f"**🔍 待审清单节选：**\n{issue_list_str}\n\n"
            f"> 💡 *前往 Issue 评论「同意修复」或「执行全部」即可自动触发二次修复*"
        )
        send_feishu_card(
            webhook_url=feishu_url,
            title=f"⚠️ [Sonar 待决策] {len(batch_issues)} 项业务代码缺陷需人工审核",
            summary_markdown=summary_md,
            button_text="👉 点击前往 Issue 查阅并决策",
            button_url=issue_url,
            color="orange"
        )
    else:
        print(f"❌ 创建 Issue 失败: {res.status_code} {res.text}")

def main():
    sonar_token = os.environ.get("SONAR_TOKEN")
    project_key = os.environ.get("SONAR_PROJECT_KEY", "NoBugBoy_htz")
    branch      = os.environ.get("GITHUB_REF_NAME") or os.environ.get("BRANCH_NAME")
    repo        = os.environ["GITHUB_REPOSITORY"]
    gh_token    = os.environ["GITHUB_TOKEN"]
    headers     = {"Authorization": f"Bearer {gh_token}", "Accept": "application/vnd.github.v3+json"}
    model       = os.environ.get("AI_MODEL", "deepseek-v4-flash")
    sha_cache   = {}

    ensure_label_exists(repo, headers, "ai-needs-decision")

    # ── Step 1：拉取全量未解决缺陷（对齐 SonarCloud 网页端 OPEN / CONFIRMED） ────────
    print(f"📡 正在从 SonarCloud 拉取未解决缺陷 (projectKey={project_key}, branch={branch or '默认'})...")
    raw_issues = fetch_all_sonar_issues(sonar_token, project_key, branch)

    if not raw_issues:
        print("✅ SonarCloud 显示项目无任何未解决缺陷！")
        return

    # ── Step 2：按文件聚合装箱（每批约20个，相同文件强绑定同组，极省Token） ──
    batches = build_file_grouped_batches(raw_issues, repo, headers, sha_cache, batch_size=20)
    total_issues = sum(sum(len(f["issues"]) for f in b) for b in batches)
    print(f"📊 共筛选出 {total_issues} 个缺陷，已按文件聚合成 {len(batches)} 个微批次（每批约20项，同文件在同组）")

    client = OpenAI(
        api_key=os.environ["AI_API_KEY"],
        base_url=os.environ.get("AI_BASE_URL", "https://api.deepseek.com/v1")
    )

    system_prompt = """
你是一名极其资深的 Java 架构师。针对 SonarCloud 发现的代码缺陷进行分流与自动修复。
必须全部使用简体中文输出！

【输入格式】：
每个条目是一个文件对象，包含 `file`、`code_context` 以及该文件下的 `issues` 缺陷列表。
请结合该文件的上下文，对其中的各个缺陷进行分析与修复。

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

    all_auto_fixes  = []
    all_need_review = []
    all_ignore_list = []

    # ── Step 3：分批调用 AI（同文件聚合同组，注意力集中，绝不截断） ───────────
    for idx, batch in enumerate(batches, 1):
        batch_issue_cnt = sum(len(f["issues"]) for f in batch)
        print(f"🤖 正在处理第 {idx}/{len(batches)} 批（包含 {len(batch)} 个文件，共 {batch_issue_cnt} 个缺陷）...")
        try:
            resp = client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user",   "content": f"当前批次文件及缺陷列表如下：\n{json.dumps(batch, ensure_ascii=False, indent=2)}"}
                ],
                response_format={"type": "json_object"}
            )
            decision = json.loads(resp.choices[0].message.content)
            all_auto_fixes.extend(decision.get("auto_fix_list", []))
            all_need_review.extend(decision.get("need_review_list", []))
            all_ignore_list.extend(decision.get("ignore_list", []))
            print(f"  ✅ 第 {idx} 批决策完成：自动修复 {len(decision.get('auto_fix_list', []))} 项，待人工审核 {len(decision.get('need_review_list', []))} 项")
        except Exception as e:
            print(f"  ❌ 第 {idx} 批 AI 分析异常: {e}")

    # 建立 file_path 到 blame_author 映射
    file_author_map = {f["file"]: f.get("blame_author") for b in batches for f in b}

    # ── Step 4：将所有批次的非业务缺陷【汇总后一次性自动修复并提 PR】 ────────
    if all_auto_fixes:
        print(f"\n🚀 汇总得到 {len(all_auto_fixes)} 个可自动解决的代码缺陷，正在统一修复并提 PR...")
        branch_name = f"sonar-autofix-{os.environ.get('GITHUB_RUN_ID', 'dev')}"
        run_cmd(f"git checkout -b {branch_name}")
        modified   = False
        fix_descs  = []
        pr_authors = set()

        for fix in all_auto_fixes:
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
                author = file_author_map.get(fp)
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
            run_cmd(f"git commit -m 'fix: AI 自动批量修复 {len(fix_descs)} 项非业务代码缺陷 [skip ci]'")
            run_cmd(f"git push origin {branch_name} --force")

            author_notice = (
                f"\n\n> 📢 **涉及代码提交人**：{' '.join(sorted(pr_authors))} 请关注此 PR。"
                if pr_authors else ""
            )
            pr_payload = {
                "title": f"🤖 [Sonar+AI Auto-Fix] 自动批量修复 {len(fix_descs)} 项代码缺陷 [skip ci]",
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
                pr_url = pr_res.json().get("html_url")
                print(f"🎉 成功创建自动修复 PR: {pr_url}")

                feishu_url = os.environ.get("FEISHU_WEBHOOK_URL")
                authors_str = " ".join(sorted(pr_authors)) if pr_authors else "暂无"
                display_fixes = fix_descs[:8]
                more_note = f"\n*...等共 {len(fix_descs)} 项修复*" if len(fix_descs) > 8 else ""
                fix_list_str = "\n".join(display_fixes) + more_note

                summary_md = (
                    f"**📦 仓库：** `{repo}`\n"
                    f"**🌿 目标分支：** `htz`\n"
                    f"**🛠️ 消除缺陷：** 本次自动批量消除 **{len(fix_descs)}** 项核心代码缺陷\n"
                    f"**📢 涉及提交人：** {authors_str}\n\n"
                    f"---\n"
                    f"**📋 修复清单节选：**\n{fix_list_str}"
                )
                send_feishu_card(
                    webhook_url=feishu_url,
                    title=f"🤖 [Sonar+AI] 自动批量修复 PR 已发起（{len(fix_descs)} 项）",
                    summary_markdown=summary_md,
                    button_text="👉 点击前往 GitHub Review 并合并 PR",
                    button_url=pr_url,
                    color="green"
                )
            else:
                print(f"❌ 创建 PR 失败: {pr_res.status_code} {pr_res.text}")
        else:
            print("ℹ️ 所有自动修复项未产生实际变更")
    else:
        print("ℹ️ 未发现可直接自动修复的代码缺陷")

    # ── Step 5：仅对涉及业务缺陷的问题提 Issue（>10条才分批，<=10条提1个Issue） ─
    if all_need_review:
        for item in all_need_review:
            fp = item.get("file_path", "")
            item["blame_author"] = file_author_map.get(fp)

        total_review = len(all_need_review)
        print(f"\n⚠️ 发现 {total_review} 个真正涉及业务逻辑的缺陷，正在生成 Issue...")

        if total_review <= 10:
            title = f"⚠️ [Sonar 待决策] 涉及业务逻辑的代码缺陷（共 {total_review} 项）"
            create_review_issue(repo, headers, all_need_review, title, 1, 1)
        else:
            batch_size = 10
            review_batches = [all_need_review[i:i+batch_size] for i in range(0, total_review, batch_size)]
            for idx, r_batch in enumerate(review_batches, 1):
                title = f"⚠️ [Sonar 待决策] 涉及业务代码缺陷（第 {idx}/{len(review_batches)} 批，共 {len(r_batch)} 项）"
                create_review_issue(repo, headers, r_batch, title, idx, len(review_batches))
    else:
        print("🎉 没有需要人工审核的复杂业务缺陷！")

    print("\n✅ 全部批次流水线治理完成！")

if __name__ == "__main__":
    main()