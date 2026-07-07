#!/usr/bin/env python3
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[1]
CONFIG_FILE = ROOT_DIR / "src/main/resources/application-test.yml"


def read_openai_config():
    text = CONFIG_FILE.read_text(encoding="utf-8")
    in_openai_chat = False
    config = {}
    for raw_line in text.splitlines():
        line = raw_line.rstrip()
        if line.startswith("openai:"):
            in_openai_chat = False
            continue
        if line.startswith("  chat:"):
            in_openai_chat = True
            continue
        if in_openai_chat:
            if line and not line.startswith("    "):
                break
            match = re.match(r"\s{4}([a-zA-Z0-9_-]+):\s*(.*)$", line)
            if match:
                config[match.group(1).replace("-", "_")] = match.group(2).strip().strip('"').strip("'")
    required = ["base_url", "api_key", "model"]
    missing = [key for key in required if not config.get(key)]
    if missing:
        raise RuntimeError(f"application-test.yml 缺少配置: {', '.join(missing)}")
    return config


def build_report_prompt():
    system_prompt = (
        "你是一名严谨的 HR AI 面试评估官。只输出严格 JSON，不要 Markdown，不要解释。"
        "评分必须主要依据候选人在本次面试中的实际回答，回答质量、具体证据、表达完整度合计权重不得低于70%。"
        "岗位 JD、能力要求和简历只能作为辅助上下文，不能因为简历或岗位表面匹配就给高分。"
        "如果候选人回答很短、泛泛而谈、没有案例、没有技术细节或答非所问，必须明显降分。"
    )
    user_prompt = """请根据下面的岗位、候选人简历和完整面试对话，生成面试评估报告。
必须输出 JSON 结构：
{
  "totalScore": 0,
  "dimensionScores": [{"name":"回答有效性","score":0,"comment":""}],
  "strengths": "",
  "risks": "",
  "recommendation": "RECOMMEND|HOLD|REJECT",
  "followUpQuestions": [""],
  "summary": ""
}

评分维度建议包含：回答有效性、表达完整度、岗位匹配度、经验支撑度、沟通配合度。
推荐规则：75分及以上 RECOMMEND，60-74.99 HOLD，60分以下 REJECT；如果有效回答少于2轮，最高不超过60分。

岗位信息：
岗位名称：高级全栈开发工程师
岗位JD：负责企业内部 HR 面试系统的前后端开发，包含 Vue 管理后台、Spring Boot API、Redis 缓存、WebSocket 实时语音链路、AI 面试报告生成和 Docker 部署。
能力要求：熟悉 Java、Spring Boot、MySQL、Redis、Vue，理解接口设计、并发控制、异步任务、日志排查和线上故障处理。

候选人信息：
姓名：张三
简历：候选人有 5 年 Java 开发经验，做过 Spring Boot 管理系统、Vue 后台、Redis 缓存优化和 WebSocket 消息推送。参与过 Docker 部署和线上故障处理。

面试对话：
AI面试官：请介绍一个你做过的高并发接口优化案例。
候选人：我之前负责一个报名系统，活动开始后接口 QPS 大概 1500。最初所有请求都直接查 MySQL，数据库 CPU 很快打满。我先把活动配置、库存计数和用户重复提交校验迁到 Redis，用 Lua 保证扣减原子性，再把订单创建改成 MQ 异步落库。最后接口平均响应从 800ms 降到 90ms，数据库 CPU 从 90% 降到 35% 左右。
AI面试官：如果 Redis 出现短暂抖动，你怎么保护主链路？
候选人：我会先区分数据类型。像权限、岗位详情这种可以短期容忍旧值的，用短 TTL 和降级兜底；像库存扣减这种强一致场景不能直接降级到本地内存，会做限流、排队或直接提示稍后重试。同时给 Redis 操作加超时和熔断，避免线程池被拖死。
AI面试官：你在前端性能方面做过什么？
候选人：后台列表页我处理过首屏接口过多的问题。原来登录后一次拉岗位、候选人、面试、权限等全部数据，后来改成按当前 tab 懒加载，并缓存基础字典。大表格改成分页查询，减少一次性加载。"""
    return system_prompt, user_prompt


def extract_json(content):
    value = (content or "").strip()
    start = value.find("{")
    end = value.rfind("}")
    if start >= 0 and end > start:
        value = value[start:end + 1]
    return json.loads(value)


def request_chat(config):
    base_url = config["base_url"].rstrip("/")
    url = f"{base_url}/chat/completions"
    timeout = int(config.get("timeout_seconds") or 60)
    system_prompt, user_prompt = build_report_prompt()
    body = {
        "model": config["model"],
        "temperature": 0.2,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {config['api_key']}",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        response_body = response.read().decode("utf-8")
    return json.loads(response_body)


def main():
    try:
        config = read_openai_config()
        print(f"请求地址: {config['base_url'].rstrip('/')}/chat/completions")
        print(f"模型: {config['model']}")
        response = request_chat(config)
        content = response["choices"][0]["message"]["content"]
        report = extract_json(content)
        required_fields = ["totalScore", "dimensionScores", "strengths", "risks", "recommendation", "followUpQuestions", "summary"]
        missing = [field for field in required_fields if field not in report]
        if missing:
            raise RuntimeError(f"AI 返回 JSON 缺少字段: {', '.join(missing)}")
        print("\nAI 报告解析成功")
        print(f"总分: {report.get('totalScore')}")
        print(f"建议: {report.get('recommendation')}")
        print(f"优势: {report.get('strengths')}")
        print(f"风险: {report.get('risks')}")
        print("维度:")
        for item in report.get("dimensionScores") or []:
            print(f"  - {item.get('name')}: {item.get('score')} / {item.get('comment')}")
        print("追问:")
        for question in report.get("followUpQuestions") or []:
            print(f"  - {question}")
        usage = response.get("usage") or {}
        if usage:
            print(f"\nToken: prompt={usage.get('prompt_tokens')} completion={usage.get('completion_tokens')} total={usage.get('total_tokens')}")
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        print(f"请求失败: HTTP {error.code}")
        print(body)
        sys.exit(1)
    except Exception as error:
        print(f"测试失败: {error}")
        sys.exit(1)


if __name__ == "__main__":
    main()
