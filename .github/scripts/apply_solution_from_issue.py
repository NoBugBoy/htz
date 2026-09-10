import os, re, subprocess, requests, json, traceback
from openai import OpenAI

# ── 工具函数 ──────────────────────────────────────────────────────────────────

def run_cmd(cmd, check=False):
    """执行 shell 命令，打印 warn；check=True 时失败抛出异常"""
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    if res.returncode != 0:
        msg = f"[CMD WARN] {cmd}\n{res.stderr.strip()}"
        print(msg)
        if check:
            raise RuntimeError(msg)
    return res.stdout.strip()

def send_feishu_card(webhook_url, title, summary_markdown, button_text=None, button_url=None, color="green"):
    """向飞书群机器人发送富文本互动卡片（未配置静默跳过，异常不阻塞主流程）"""
    if not webhook_url or not webhook_url.strip():
        return
    elements = [
        {"tag": "div", "text": {"tag": "lark_md", "content": summary_markdown}}
    ]
    if button_text and button_url:
        elements.append({
            "tag": "action",
            "actions": [
                {"tag": "button", "text": {"tag": "plain_text", "content": button_text}, "type": "primary", "url": button_url}
            ]
        })
    payload = {
        "msg_type": "interactive",
        "card": {
            "config": {"wide_screen_mode": True},
            "header": {"title": {"tag": "plain_text", "content": title}, "template": color},
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

def get_code_context(file_path, target_line, radius=20):
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
                    c_lines.pop(idx)
                return "\n".join(c_lines), True

    return content, False

def get_blame_author(file_path, line, repo, headers, sha_cache):
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
            f"https://api.github.com/repos/{repo}/commits/{sha}", headers=headers
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

def post_issue_comment(repo, headers, issue_number, body):
    """向 Issue 发送评论，确保结果可见"""
    res = requests.post(
        f"https://api.github.com/repos/{repo}/issues/{issue_number}/comments",
        json={"body": body}, headers=headers
    )
    if res.status_code != 201:
        print(f"⚠️ 回复 Issue 失败: {res.status_code} {res.text}")

def parse_approved_indices(comment_body, total):
    comment = comment_body.strip()
    if re.search(r'执行全部|approve\s+all', comment, re.IGNORECASE):
        return set(range(1, total + 1))
    skip_match = re.search(r'跳过\s*([\d,，\s]+)', comment)
    if skip_match:
        return set(range(1, total + 1)) - {int(n) for n in re.findall(r'\d+', skip_match.group(1))}
    nums_match = re.search(r'(?:同意修复|执行|approve)\s*([\d,，\s]+)', comment, re.IGNORECASE)
    if nums_match:
        return {int(n) for n in re.findall(r'\d+', nums_match.group(1))}
    if re.search(r'同意|执行|approve', comment, re.IGNORECASE):
        return set(range(1, total + 1))
    return set()

# ── 主逻辑 ────────────────────────────────────────────────────────────────────

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

    try:
        # ── Step 1：从 Issue body 提取 SonarCloud 原始数据 ────────────────────
        print(f"📦 Issue #{issue_number} | 评论：{comment_body[:80]}")
        data_match = re.search(r'<!-- SONAR_ISSUE_DATA\s*([\s\S]+?)\s*-->', issue_body)
        if not data_match:
            raise ValueError("未在 Issue 中找到 SONAR_ISSUE_DATA 数据块，请确认此 Issue 由 SonarCloud 流水线生成。")

        issue_data    = json.loads(data_match.group(1).strip())
        all_issues    = issue_data.get("issues", [])
        batch_index   = issue_data.get("batch_index", "?")
        total_batches = issue_data.get("total_batches", "?")
        total         = len(all_issues)
        print(f"✅ 解析数据块成功：第 {batch_index}/{total_batches} 批，共 {total} 条")

        # ── Step 2：解析评论，确定要修复哪些条目 ─────────────────────────────
        approved_indices = parse_approved_indices(comment_body, total)
        if not approved_indices:
            raise ValueError(
                f"未能从评论中解析出有效的修复指令。\n"
                f"支持格式：`同意修复 1,3,5` / `执行全部` / `跳过 2,4`"
            )
        valid_indices  = {i for i in approved_indices if 1 <= i <= total}
        approved_items = [all_issues[i - 1] for i in sorted(valid_indices)]
        print(f"✅ 已解析指令：将修复第 {sorted(valid_indices)} 项（共 {len(approved_items)} 条）")

        # ── Step 3：读取代码上下文 + blame 作者 ──────────────────────────────
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
        print(f"📄 已读取 {len(fix_requests)} 个文件的代码上下文")

        # ── Step 4：AI 生成修复方案 ───────────────────────────────────────────
        client = OpenAI(
            api_key=os.environ["AI_API_KEY"],
            base_url=os.environ.get("AI_BASE_URL", "https://api.deepseek.com/v1")
        )
        system_prompt = """
你是一名极其资深的 Java 架构师。针对 SonarCloud 发现的代码缺陷及其真实代码片段进行精准修复。
必须全部使用简体中文输出！

严格输出以下格式的 JSON，不要包含任何 markdown 标记：
{
  "auto_fix_list": [
    {
      "file_path": "文件路径",
      "severity": "严重级别",
      "issue_desc": "缺陷简要说明",
      "fix_summary": "本次修复内容的一句话总结（给 PR/Issue 展示用）",
      "original_snippet": "待替换/删除的目标代码片段（只包含目标行，严禁包含无关周边行）",
      "fixed_snippet": "修复后的代码（若为删除语句/死代码，直接填空字符串 \"\"）"
    }
  ],
  "skip_list": [
    {
      "file_path": "文件路径",
      "reason": "跳过原因（如：涉及重大业务逻辑必须人工决策等）"
    }
  ]
}

【精准修复标准与核心规则】：
1. 【空指针与明显致错代码根因修复】：
   - 当告警提示可能存在 NullPointerException、解引用风险、或参数被重写覆盖时，请仔细观察上下文！
   - 若发现方法形参或关键变量一进方法就被错误或恶意置为 null（如 `request = null;`、`user = null;`），这是导致后续 100% 空指针的根因致错代码，【必须直接放入 auto_fix_list 将该赋值语句整行删除】（fixed_snippet 设为 ""），恢复正常参数传递！
2. 【无用变量与硬编码密钥】：明显的硬编码密钥占位（如 hardcodedJwtSecret）、未使用局部变量，直接整行删除。
3. 【至关重要的替换规则】：
   - original_snippet 必须是【最小化目标行】（通常仅 1 行），严禁包含未修改的方法声明、括号或周边行！
   - 删除代码时：fixed_snippet 直接填空字符串 ""。
   - .github/ 目录下的文件一律放入 skip_list！
"""
        print(f"🤖 正在调用 AI 生成修复方案（{len(fix_requests)} 条）...")
        resp = client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user",   "content": f"待修复缺陷：\n{json.dumps(fix_requests, ensure_ascii=False, indent=2)}"}
            ],
            response_format={"type": "json_object"}
        )
        decision = json.loads(resp.choices[0].message.content)
        print(f"🤖 AI 决策完成")

        for skip in decision.get("skip_list", []):
            print(f"⏭️ 跳过 {skip.get('file_path')}: {skip.get('reason')}")

        auto_fixes = decision.get("auto_fix_list", [])
        if not auto_fixes:
            raise ValueError("AI 判断批准的问题均无法安全自动修复，已跳过。详见 skip_list。")

        # ── Step 5：应用代码修改 ──────────────────────────────────────────────
        branch_name = f"ai-fix-issue-{issue_number}-{os.environ.get('GITHUB_RUN_ID', 'dev')}"
        run_cmd(f"git checkout -b {branch_name}")
        modified   = False
        fix_descs  = []   # 用于 PR body 和 Issue 回复的修复摘要
        pr_authors = set()

        for fix in auto_fixes:
            fp    = fix["file_path"]
            orig  = fix["original_snippet"]
            fixed = fix.get("fixed_snippet", "")
            sev   = fix.get("severity", "")
            summary = fix.get("fix_summary", fix.get("issue_desc", ""))

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
                author     = next((r.get("blame_author") for r in fix_requests if r["file"] == fp), None)
                author_str = f"（提交人：{author}）" if author else ""
                fix_descs.append(f"- [{sev}] `{fp}`{author_str}\n  > {summary}")
                if author:
                    pr_authors.add(author)
                print(f"✅ 成功替换: {fp}  {author_str}")
            else:
                print(f"⚠️ 未能精确匹配旧代码，跳过: {fp} -> {repr(orig)}")

        if not modified:
            raise ValueError("所有修复项均未能在文件中精确匹配旧代码，无法自动修复。请手动处理或重新确认问题。")

        # ── Step 6：Spotless 格式化 ───────────────────────────────────────────
        print("🎨 正在执行 Spotless 格式化...")
        run_cmd("mvn spotless:apply -q || ./gradlew spotlessApply || true")
        print("✅ Spotless 格式化完成")

        # ── Step 7：git 提交 + 推送 ───────────────────────────────────────────
        print(f"📤 正在提交并推送分支 {branch_name}...")
        run_cmd("git config user.name 'github-actions[bot]'")
        run_cmd("git config user.email 'github-actions[bot]@users.noreply.github.com'")
        run_cmd("git add .")
        commit_out = run_cmd(
            f"git commit -m 'fix: AI 根据 Issue #{issue_number} 修复 Sonar 缺陷（第 {batch_index} 批） [skip ci]'"
        )
        print(f"  commit: {commit_out[:80]}")

        push_out = run_cmd(f"git push origin {branch_name} --force", check=True)
        print(f"✅ 分支已推送: {push_out[:80] if push_out else '(无输出)'}")

        # ── Step 8：创建 PR ────────────────────────────────────────────────────
        print("🚀 正在创建 Pull Request...")
        indices_str   = ",".join(str(i) for i in sorted(valid_indices))
        author_notice = (
            f"\n\n> 📢 **涉及代码提交人**：{' '.join(sorted(pr_authors))}  请关注此 PR。"
            if pr_authors else ""
        )
        fix_detail = "\n".join(fix_descs)
        target_branch = os.environ.get("TARGET_BRANCH") or "hook"
        pr_payload = {
            "title": f"🤖 [AI Fix] Issue #{issue_number} Sonar 缺陷修复（第 {batch_index}/{total_batches} 批，项 {indices_str}）",
            "head":  branch_name,
            "base":  target_branch,
            "body":  (
                f"关联并关闭 Issue: #{issue_number}（第 {batch_index}/{total_batches} 批）\n"
                f"开发者指令：`{comment_body.strip()}`\n\n"
                f"### 📋 修复内容摘要\n\n{fix_detail}"
                f"{author_notice}\n\n"
                f"---\n本 PR 由 AI 自动生成，已执行 Spotless 格式化，请 Code Review 后合并。\n\n"
                f"Fixes #{issue_number}"
            )
        }
        pr_res = requests.post(
            f"https://api.github.com/repos/{repo}/pulls", json=pr_payload, headers=headers
        )
        if pr_res.status_code != 201:
            raise RuntimeError(
                f"创建 PR 失败，状态码 {pr_res.status_code}，错误：{pr_res.text}"
            )

        pr_url = pr_res.json().get("html_url", "(URL 获取失败)")
        print(f"🎉 PR 创建成功: {pr_url}")

        # ── Step 9：回复 Issue（含完整修复摘要）────────────────────────────────
        author_cc     = (
            f"\n\n> 📢 **涉及代码提交人**：{' '.join(sorted(pr_authors))}  请 Review 此 PR。"
            if pr_authors else ""
        )
        reply = (
            f"✅ **AI 已完成修复并发起 Pull Request：{pr_url}**\n\n"
            f"根据您的指令（`{comment_body.strip()}`），本次共修复 **{len(fix_descs)}** 项：\n\n"
            f"{fix_detail}"
            f"{author_cc}\n\n"
            f"---\n"
            f"> PR 合并后此 Issue 将自动关闭。请 Code Review 后合并。"
        )
        post_issue_comment(repo, headers, issue_number, reply)
        print("✅ 已回复 Issue")

        # 飞书群通知
        feishu_url = os.environ.get("FEISHU_WEBHOOK_URL")
        feishu_md = (
            f"**📦 仓库：** `{repo}`\n"
            f"**📌 关联 Issue：** #{issue_number}\n"
            f"**💬 开发者指令：** `{comment_body.strip()}`\n"
            f"**🛠️ 修复结果：** 本次成功修复 **{len(fix_descs)}** 项缺陷并提 PR\n\n"
            f"---\n"
            f"**📋 修复清单：**\n{fix_detail}"
        )
        send_feishu_card(
            webhook_url=feishu_url,
            title=f"✅ [AI Fix] Issue #{issue_number} 决策修复 PR 已创建",
            summary_markdown=feishu_md,
            button_text="👉 点击前往 GitHub Review 并合并 PR",
            button_url=pr_url,
            color="green"
        )

    except Exception as e:
        # 任何步骤失败，统一回写 Issue，方便开发者排查
        err_detail = traceback.format_exc()
        print(f"❌ 执行失败：{e}\n{err_detail}")
        error_reply = (
            f"❌ **AI 自动修复失败**\n\n"
            f"**失败原因：**\n```\n{e}\n```\n\n"
            f"<details><summary>详细堆栈</summary>\n\n```\n{err_detail}\n```\n</details>\n\n"
            f"请检查 [GitHub Actions 日志]"
            f"(https://github.com/{os.environ.get('GITHUB_REPOSITORY', '')}/actions) 获取更多信息。"
        )
        post_issue_comment(repo, headers, issue_number, error_reply)
        raise  # 让 workflow 以失败状态结束，Actions tab 中显示红色

if __name__ == "__main__":
    main()