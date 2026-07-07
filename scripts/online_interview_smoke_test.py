#!/usr/bin/env python3
"""
线上面试冒烟/并发测试脚本。

依赖：
    python3 -m pip install requests websocket-client

单场测试：
    HR_TEST_EMAIL=你的邮箱 HR_TEST_PASSWORD=你的密码 python3 scripts/online_interview_smoke_test.py

10 场并发测试，5 个旅游定制师 + 5 个培训讲师：
    HR_TEST_EMAIL=你的邮箱 HR_TEST_PASSWORD=你的密码 python3 scripts/online_interview_smoke_test.py --mode mixed10
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any
from urllib.parse import urljoin, urlparse

try:
    import requests
except ImportError:
    print("缺少 requests，请先执行：python3 -m pip install requests websocket-client")
    sys.exit(1)

try:
    import websocket
except ImportError:
    print("缺少 websocket-client，请先执行：python3 -m pip install requests websocket-client")
    sys.exit(1)


DEFAULT_BASE_URL = "https://zook.kaixinzou.cn"


@dataclass
class Scenario:
    code: str
    title: str
    jd: str
    requirements: str
    dimensions: list[dict[str, Any]]
    resume: str
    answers: list[str]


@dataclass
class InterviewRuntime:
    index: int
    scenario: Scenario
    job_id: int
    candidate_id: int
    interview_id: int
    public_token: str
    access_code: str
    invite_url: str
    closed_by_ai: bool = False
    ai_messages: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)
    message_count: int = 0
    report_score: Any = None
    report_recommendation: str = ""
    started_at: float = 0
    ended_at: float = 0


TRAVEL_SCENARIO = Scenario(
    code="travel",
    title="高端旅游定制师",
    jd="负责高端客户定制旅游咨询、需求挖掘、行程方案设计、资源协调、报价转化、出行中服务跟进和突发问题处理。",
    requirements="具备客户沟通、销售转化、目的地产品理解、预算控制、应急处理、高净值客户服务意识。",
    dimensions=[
        {"name": "客户需求挖掘", "description": "能否问清客户真实需求", "weight": 20},
        {"name": "行程方案设计", "description": "能否设计合理、有高端感的方案", "weight": 20},
        {"name": "资源协调与应急", "description": "能否处理改期、换酒店、资源紧张", "weight": 20},
        {"name": "销售转化", "description": "能否建立信任并推进成交", "weight": 20},
        {"name": "服务意识", "description": "是否关注细节、隐私、省心体验", "weight": 20},
    ],
    resume="""
姓名：测试旅游候选人
求职意向：高端旅游定制师
经验：5 年销售与客户服务经验，熟悉家庭出游、亲子出游和高净值客户服务。
优势：能通过提问了解客户预算、同行人员、时间安排、目的地偏好和服务期望；熟悉专车接送、酒店升级、VIP 通道、私密导览、应急改签等高端服务场景。
风险：对部分海外目的地资源不够熟，需要依赖供应商和工具做二次确认。
""",
    answers=[
        "您好，我有多年销售和客户服务经验，也很喜欢旅行。高端旅游定制我理解不是简单订机票酒店，而是先了解客户同行人员、预算、时间、偏好和禁忌，再把交通、住宿、餐饮、景点、导览、应急方案都安排好。",
        "如果客户只说想玩得舒服一点，我会先问出行人数、有没有老人孩子、预算范围、想休闲还是打卡、能接受的车程、餐饮偏好和酒店要求。先把需求问清楚，再给两到三个方案让客户选择。",
        "比如亲子客户去日本迪士尼，我会优先安排靠近园区的酒店、专车接送、快速通道、儿童餐、午休时间和备用雨天方案。行程不会排太满，要给孩子和家长留出缓冲。",
        "如果客户临时想换热门项目，我会先确认客户真实优先级，再联系供应商看有没有 VIP 名额或替代资源。如果费用变高，我会先说明差价和价值，再给客户选择。",
        "如果酒店服务没达到预期，我会先安抚客户，然后马上联系酒店升级房型、赠送早餐或延迟退房。如果确实无法解决，再协调换酒店，同时保证后面的用车和景点预约不受影响。",
        "预算有限但又要高端感，我会保留客户最能感知的服务，比如专车、核心景点 VIP 体验和好酒店，把不重要的打卡点减少。",
        "我觉得高净值客户最看重的是省心、私密和确定性。服务人员要提前把风险想好，不要让客户自己做很多选择题。",
        "如果遇到我不了解的目的地，我不会乱承诺。我会先告诉客户需要确认，再通过供应商、官方信息和工具核实，给客户一个准确答复时间。",
        "我的不足是部分海外资源积累还不够，但我的客户沟通、需求拆解和服务意识比较强。入职后我会优先补目的地产品库和供应商资源。",
        "我讲完了，如果本轮了解得差不多，可以结束面试。",
    ],
)


TRAINER_SCENARIO = Scenario(
    code="trainer",
    title="培训讲师",
    jd="负责新人培训、销售话术培训、产品知识培训、课程设计、培训组织、效果评估和一线团队赋能。",
    requirements="具备课程开发、现场授课、销售培训、复盘评估、学员互动、跨部门沟通和业务结果导向能力。",
    dimensions=[
        {"name": "课程设计", "description": "能否围绕业务目标设计课程", "weight": 20},
        {"name": "授课表达", "description": "表达是否清晰、有感染力", "weight": 20},
        {"name": "销售赋能", "description": "能否沉淀话术和方法", "weight": 20},
        {"name": "培训落地", "description": "能否跟踪效果并复盘优化", "weight": 20},
        {"name": "组织协同", "description": "能否推动业务部门配合", "weight": 20},
    ],
    resume="""
姓名：测试培训候选人
求职意向：培训讲师
经验：6 年销售和培训经验，做过新人岗前培训、产品卖点培训、电话营销话术培训和区域商家培训。
优势：能搭建标准化培训流程，输出课件、话术和演练机制，能根据一线反馈持续优化。
风险：对线上课程数据化运营经验一般，需要进一步提升数据分析能力。
""",
    answers=[
        "您好，我做过销售和培训，比较擅长把一线销售经验整理成新人能听懂、能练习、能复制的话术和流程。培训不是讲完课就结束，而是要看新人有没有真正会用。",
        "如果做新人培训，我会先拆岗位流程，比如客户开场、需求挖掘、产品介绍、异议处理和成交跟进。每一块都配案例、话术和演练。",
        "课程设计上，我会先和业务负责人确认目标，比如提升邀约率还是成交率，再倒推课程内容。不是为了讲知识点，而是解决业务问题。",
        "授课时我会多用真实案例和角色扮演，让学员现场练。如果只听不练，回到岗位上很容易忘。",
        "培训效果我会看几个指标，比如出勤、考试、演练评分、上岗后一周的通话质量和转化数据。效果不好就复盘是内容问题还是练习不够。",
        "如果老员工不愿意参加培训，我会先让主管一起明确培训目标，也会把课程做得更实战，减少纯理论内容。",
        "产品培训里，我会把卖点转成客户利益点，比如这个功能解决客户什么痛点，而不是只背产品参数。",
        "如果业务部门临时改需求，我会先确认优先级，再调整课程结构，保留必须掌握的内容，把次要内容做成课后材料。",
        "我的优势是有销售实战经验，能站在一线角度设计课程。不足是线上学习平台的数据化运营还可以继续提升。",
        "我讲完了，如果本轮了解得差不多，可以结束面试。",
    ],
)


class OnlineInterviewClient:
    def __init__(self, base_url: str, token: str | None, timeout: int):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update({"Content-Type": "application/json"})
        if token:
            self.session.headers.update({"Authorization": f"Bearer {token}"})

    def post(self, path: str, payload: dict[str, Any]) -> Any:
        return self._post(path, payload, auth=True)

    def public_post(self, path: str, payload: dict[str, Any]) -> Any:
        return self._post(path, payload, auth=False)

    def _post(self, path: str, payload: dict[str, Any], auth: bool) -> Any:
        headers = {}
        if not auth:
            headers["Authorization"] = ""
        response = self.session.post(
            self.absolute_url(path),
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers=headers,
            timeout=self.timeout,
        )
        try:
            body = response.json()
        except ValueError as exc:
            raise RuntimeError(f"接口返回非 JSON：HTTP {response.status_code} {response.text[:200]}") from exc
        if response.status_code >= 400:
            raise RuntimeError(f"HTTP {response.status_code}：{body}")
        if body.get("code") != 0:
            raise RuntimeError(f"{body.get('message')}（code={body.get('code')}）")
        return body.get("data")

    def absolute_url(self, path: str) -> str:
        if path.startswith("http://") or path.startswith("https://"):
            return path
        return urljoin(self.base_url + "/", path.lstrip("/"))

    def build_ws_url(self, websocket_url: str, ticket: str) -> str:
        base = urlparse(self.base_url)
        scheme = "wss" if base.scheme == "https" else "ws"
        host = base.netloc
        path = websocket_url if websocket_url.startswith("/") else "/" + websocket_url
        return f"{scheme}://{host}{path}?ticket={ticket}"


class OnlineInterviewSmokeTest:
    def __init__(self, base_url: str, email: str, password: str, timeout: int, answer_delay: float):
        self.base_url = base_url.rstrip("/")
        self.email = email
        self.password = password
        self.timeout = timeout
        self.answer_delay = answer_delay
        self.token = ""
        self.print_lock = threading.Lock()

    def run(self, mode: str, concurrency: int) -> None:
        self.log(f"线上地址：{self.base_url}")
        self.token = self.login()
        if mode == "mixed10":
            self.run_mixed10(concurrency)
        else:
            runtime = self.prepare_runtime(1, TRAVEL_SCENARIO)
            self.run_one(runtime)
            self.print_summary([runtime])

    def login(self) -> str:
        client = OnlineInterviewClient(self.base_url, None, self.timeout)
        data = client.post("/api/auth/login", {"email": self.email, "password": self.password})
        token = data.get("token")
        if not token:
            raise RuntimeError("登录成功但响应里没有 token")
        self.log("后台登录成功。")
        return token

    def run_mixed10(self, concurrency: int) -> None:
        scenarios = [TRAVEL_SCENARIO] * 5 + [TRAINER_SCENARIO] * 5
        runtimes = [self.prepare_runtime(index + 1, scenario) for index, scenario in enumerate(scenarios)]
        self.log(f"已创建 {len(runtimes)} 场测试面试，开始并发执行，线程数={concurrency}。")
        with ThreadPoolExecutor(max_workers=concurrency) as executor:
            futures = [executor.submit(self.run_one, runtime) for runtime in runtimes]
            for future in as_completed(futures):
                future.result()
        self.print_summary(runtimes)

    def prepare_runtime(self, index: int, scenario: Scenario) -> InterviewRuntime:
        client = OnlineInterviewClient(self.base_url, self.token, self.timeout)
        suffix = datetime.now().strftime("%m%d%H%M%S") + f"{index:02d}"
        job = self.create_job(client, scenario, suffix)
        candidate = self.create_candidate(client, scenario, job["id"], suffix)
        interview = client.post("/api/interviews/create", {"jobId": job["id"], "candidateId": candidate["id"]})
        invite_url = client.absolute_url(interview["inviteUrl"])
        runtime = InterviewRuntime(
            index=index,
            scenario=scenario,
            job_id=job["id"],
            candidate_id=candidate["id"],
            interview_id=interview["id"],
            public_token=interview["inviteToken"],
            access_code=interview["accessCode"],
            invite_url=invite_url,
        )
        self.log(
            f"[{runtime.label}] 创建成功：面试ID={runtime.interview_id}，"
            f"口令={runtime.access_code}，链接={runtime.invite_url}"
        )
        return runtime

    def create_job(self, client: OnlineInterviewClient, scenario: Scenario, suffix: str) -> dict[str, Any]:
        return client.post(
            "/api/jobs/create",
            {
                "title": f"测试-{scenario.title}-{suffix}",
                "jd": scenario.jd,
                "requirements": scenario.requirements,
                "dimensions": scenario.dimensions,
            },
        )

    def create_candidate(self, client: OnlineInterviewClient, scenario: Scenario, job_id: int, suffix: str) -> dict[str, Any]:
        return client.post(
            "/api/candidates/create",
            {
                "jobId": job_id,
                "name": f"测试{scenario.title}{suffix}",
                "gender": "UNKNOWN",
                "age": 29,
                "phone": f"139{suffix[-8:]}",
                "email": f"{scenario.code}-{suffix}@example.com",
                "resumeText": scenario.resume.strip(),
            },
        )

    def run_one(self, runtime: InterviewRuntime) -> None:
        runtime.started_at = time.time()
        client = OnlineInterviewClient(self.base_url, self.token, self.timeout)
        try:
            client.public_post("/api/public/interviews/detail", {"token": runtime.public_token})
            client.public_post(
                "/api/public/interviews/enter",
                {"token": runtime.public_token, "accessCode": runtime.access_code},
            )
            self.run_realtime(client, runtime)
            messages = client.public_post(
                "/api/public/interviews/messages/list",
                {"token": runtime.public_token, "accessCode": runtime.access_code},
            )
            runtime.message_count = len(messages)
            finished = client.public_post(
                "/api/public/interviews/finish",
                {"token": runtime.public_token, "accessCode": runtime.access_code},
            )
            self.log(f"[{runtime.label}] 已结束，状态={finished.get('status')}，消息数={runtime.message_count}")
            self.poll_report(client, runtime)
        except Exception as exc:
            runtime.errors.append(str(exc))
            self.log(f"[{runtime.label}] 失败：{exc}")
        finally:
            runtime.ended_at = time.time()

    def run_realtime(self, client: OnlineInterviewClient, runtime: InterviewRuntime) -> None:
        connect = client.public_post(
            "/api/public/interviews/realtime/connect",
            {"token": runtime.public_token, "accessCode": runtime.access_code},
        )
        ws_url = client.build_ws_url(connect["websocketUrl"], connect["ticket"])
        ws = websocket.create_connection(ws_url, timeout=self.timeout)
        try:
            self.receive_until_ai_turn(ws, runtime, max_seconds=20)
            for answer_index, answer in enumerate(runtime.scenario.answers, start=1):
                if runtime.closed_by_ai:
                    self.log(f"[{runtime.label}] AI 已发出结束语，停止发送模拟回答。")
                    break
                self.log(f"[{runtime.label}] 回答 {answer_index}/{len(runtime.scenario.answers)}")
                ws.send(answer)
                self.receive_until_ai_turn(ws, runtime, max_seconds=max(12, self.answer_delay))
                if runtime.closed_by_ai:
                    self.log(f"[{runtime.label}] AI 已发出结束语，停止发送模拟回答。")
                    break
            ws.send("finish")
            self.receive_for(ws, runtime, 2)
        finally:
            ws.close()

    def receive_until_ai_turn(self, ws: websocket.WebSocket, runtime: InterviewRuntime, max_seconds: float) -> bool:
        deadline = time.time() + max_seconds
        while time.time() < deadline:
            try:
                ws.settimeout(max(0.2, min(1.0, deadline - time.time())))
                message = ws.recv()
            except Exception:
                continue
            if self.handle_ws_message(runtime, message):
                return True
        runtime.errors.append(f"{max_seconds}s 内未收到 AI 完整回复")
        self.log(f"[{runtime.label}] 等待 AI 回复超时。")
        return False

    def receive_for(self, ws: websocket.WebSocket, runtime: InterviewRuntime, seconds: float) -> None:
        deadline = time.time() + seconds
        while time.time() < deadline:
            try:
                ws.settimeout(max(0.2, min(1.0, deadline - time.time())))
                message = ws.recv()
            except Exception:
                continue
            self.handle_ws_message(runtime, message)

    def handle_ws_message(self, runtime: InterviewRuntime, message: Any) -> bool:
        if isinstance(message, bytes) or not isinstance(message, str) or message.startswith("audio:"):
            return False
        try:
            body = json.loads(message)
        except json.JSONDecodeError:
            return False
        event = body.get("event")
        content = self.extract_content(body)
        if event in {"error", "dialog_error"}:
            runtime.errors.append(content or str(body))
            self.log(f"[{runtime.label}] WS错误：{content or body}")
        if content and event in {"chat_response", "tts_sentence_end", "interview_question_limit_reached"}:
            if event == "tts_sentence_end" or len(content) > 8:
                runtime.ai_messages.append(content)
            self.log(f"[{runtime.label}] AI：{content[:120]}")
            if self.is_interview_closing_text(content):
                runtime.closed_by_ai = True
            return event in {"tts_sentence_end", "interview_question_limit_reached"}
        return False

    def extract_content(self, body: dict[str, Any]) -> str:
        payload = body.get("payload")
        if isinstance(payload, str):
            try:
                payload = json.loads(payload)
            except json.JSONDecodeError:
                return payload
        if isinstance(payload, dict):
            if payload.get("content"):
                return str(payload["content"])
            if payload.get("text"):
                return str(payload["text"])
            results = payload.get("results")
            if isinstance(results, list):
                return "".join(str(item.get("text", "")) for item in results if isinstance(item, dict))
        return str(body.get("message") or "")

    def poll_report(self, client: OnlineInterviewClient, runtime: InterviewRuntime) -> None:
        for _ in range(18):
            time.sleep(5)
            try:
                report = client.post("/api/interviews/reports/detail", {"id": runtime.interview_id})
                runtime.report_score = report.get("totalScore")
                runtime.report_recommendation = report.get("recommendation") or ""
                self.log(
                    f"[{runtime.label}] 报告已生成："
                    f"总分={runtime.report_score}，建议={runtime.report_recommendation}"
                )
                return
            except RuntimeError:
                continue
        runtime.errors.append("报告 90 秒内未生成")
        self.log(f"[{runtime.label}] 报告 90 秒内未生成。")

    def print_summary(self, runtimes: list[InterviewRuntime]) -> None:
        self.log("\n========== 并发测试汇总 ==========")
        for runtime in runtimes:
            duration = round(runtime.ended_at - runtime.started_at, 1) if runtime.ended_at else 0
            first_question = self.first_real_question(runtime.ai_messages)
            self.log(
                f"[{runtime.label}] "
                f"面试ID={runtime.interview_id} "
                f"消息={runtime.message_count} "
                f"AI收尾={runtime.closed_by_ai} "
                f"分数={runtime.report_score} "
                f"建议={runtime.report_recommendation or '-'} "
                f"耗时={duration}s "
                f"错误={len(runtime.errors)}"
            )
            if first_question:
                self.log(f"  首个问题：{first_question[:120]}")
            if runtime.errors:
                self.log(f"  错误：{' | '.join(runtime.errors[:3])}")

    def first_real_question(self, messages: list[str]) -> str:
        for message in messages:
            if "？" in message or "?" in message:
                return message
        return messages[0] if messages else ""

    def is_interview_closing_text(self, content: str) -> bool:
        closing_keywords = [
            "面试就到这里",
            "面试到此结束",
            "本轮面试到此结束",
            "本轮面试问题就到这里",
            "本轮问题就到这里",
            "本轮问题已经了解得差不多",
            "问题已经全部覆盖",
            "感谢你的时间和配合",
            "感谢你的详细分享",
            "回答完成后请点击结束面试",
        ]
        return any(keyword in content for keyword in closing_keywords)

    def log(self, message: str) -> None:
        with self.print_lock:
            print(message, flush=True)


@property
def runtime_label(self: InterviewRuntime) -> str:
    return f"{self.index:02d}-{self.scenario.code}"


InterviewRuntime.label = runtime_label


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="线上 HR 面试冒烟/并发测试")
    parser.add_argument("--base-url", default=os.getenv("HR_TEST_BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--email", default=os.getenv("HR_TEST_EMAIL"))
    parser.add_argument("--password", default=os.getenv("HR_TEST_PASSWORD"))
    parser.add_argument("--timeout", type=int, default=int(os.getenv("HR_TEST_TIMEOUT", "30")))
    parser.add_argument("--answer-delay", type=float, default=float(os.getenv("HR_TEST_ANSWER_DELAY", "8")))
    parser.add_argument("--mode", choices=["single", "mixed10"], default=os.getenv("HR_TEST_MODE", "single"))
    parser.add_argument("--concurrency", type=int, default=int(os.getenv("HR_TEST_CONCURRENCY", "10")))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not args.email or not args.password:
        print("请通过 --email/--password 或 HR_TEST_EMAIL/HR_TEST_PASSWORD 提供后台账号密码。")
        sys.exit(1)
    test = OnlineInterviewSmokeTest(
        base_url=args.base_url,
        email=args.email,
        password=args.password,
        timeout=args.timeout,
        answer_delay=args.answer_delay,
    )
    test.run(args.mode, args.concurrency)


if __name__ == "__main__":
    main()
