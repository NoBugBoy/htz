import os, subprocess, requests, json, re
from openai import OpenAI

def run_cmd(cmd):
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    return res.stdout.strip()

def main():
    issue_number = os.environ["ISSUE_NUMBER"]
    issue_body = os.environ["ISSUE_BODY"]
    repo = os.environ["GITHUB_REPOSITORY"]
    token = os.environ["GITHUB_TOKEN"]
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github.v3+json"}

    # 1. 从 Issue 内容中用正则提取涉及的文件路径（例如 `涉及文件：.github/workflows/...`）
    file_match = re.search(r'涉及文件[：:]\s*`?([^\n`]+)`?', issue_body)
    if not file_match:
        print("未能在 Issue 描述中解析出文件路径")
        return

    target_file = file_match.group(1).strip()
    if not os.path.exists(target_file):
        print(f"目标文件不存在: {target_file}")
        return

    # 读取真实文件源码
    with open(target_file, "r", encoding="utf-8") as f:
        actual_source = f.read()

    # 2. 把真实文件内容 + Issue 方案一起喂给 AI
    client = OpenAI(
        api_key=os.environ["AI_API_KEY"],
        base_url=os.environ.get("AI_BASE_URL", "https://api.openai.com/v1")
    )

    prompt = f"""
你是一名资深代码重构专家。请根据以下 Issue 的修复建议，对目标文件代码进行精准替换。

【目标文件路径】: {target_file}

【目标文件当前的真实内容】: