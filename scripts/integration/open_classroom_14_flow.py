#!/usr/bin/env python3
"""开放平台视频课堂 14 接口串行联调脚本（仅使用 Python 标准库）。

凭据和真实设备信息只从环境变量读取，不会写入报告：
  OPEN_PLATFORM_BASE_URL
  OPEN_PLATFORM_CLIENT_ID
  OPEN_PLATFORM_CLIENT_SECRET
  OPEN_PLATFORM_DEVICE_CODE
  OPEN_PLATFORM_ATTEND_MEMBER_ID（可选，缺省时生成独立听讲身份）

执行生产写流程：
  python scripts/integration/open_classroom_14_flow.py --execute-writes

仅检查脚本自身：
  python scripts/integration/open_classroom_14_flow.py --self-test
"""

from __future__ import annotations

import argparse
import json
import os
import socket
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Callable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit, urlunsplit
from urllib.request import Request, urlopen


SCOPES = (
    "classroom.live.read",
    "classroom.app.read",
    "classroom.course.read",
    "classroom.course.write",
    "classroom.live.control",
    "classroom.record.control",
    "classroom.member.control",
    "classroom.device.read",
    "classroom.event.read",
)

ENDPOINTS = (
    "查询主讲设备",
    "查询应用升级信息",
    "创建设备手动课程",
    "查询并确认新课程",
    "进入课程并创建房间",
    "订阅课堂事件",
    "开始课堂",
    "加入课堂",
    "查询直播状态",
    "开始录制",
    "静音成员",
    "踢出成员",
    "停止录制",
    "发送下课事件",
)

SENSITIVE_KEY_PARTS = ("token", "secret", "password", "authorization", "app_key", "client_key")
VIDEO_DEPENDENCIES = {"video-capability"}


def required_env(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise ValueError(f"缺少环境变量 {name}")
    return value


def positive_int_env(name: str, default: int) -> int:
    value = int(os.getenv(name, str(default)))
    if value <= 0:
        raise ValueError(f"{name} 必须大于 0")
    return value


def safe_url(value: str) -> str:
    try:
        parts = urlsplit(value)
        return urlunsplit((parts.scheme, parts.netloc, parts.path, "", "")) if parts.scheme else value[:200]
    except ValueError:
        return value[:200]


def sanitized(value: Any) -> Any:
    if isinstance(value, dict):
        result: dict[str, Any] = {}
        for key, item in value.items():
            lowered = str(key).lower()
            if any(part in lowered for part in SENSITIVE_KEY_PARTS):
                result[str(key)] = "***"
            elif lowered.endswith("url") and isinstance(item, str):
                result[str(key)] = safe_url(item)
            else:
                result[str(key)] = sanitized(item)
        return result
    if isinstance(value, list):
        return [sanitized(item) for item in value]
    return value


def request_json(
    base_url: str,
    method: str,
    path: str,
    timeout: int,
    token: str | None = None,
    query: dict[str, Any] | None = None,
    json_body: dict[str, Any] | None = None,
    form: dict[str, Any] | None = None,
) -> dict[str, Any]:
    url = base_url.rstrip("/") + path
    if query:
        encoded = urlencode({key: value for key, value in query.items() if value is not None}, doseq=True)
        if encoded:
            url += "?" + encoded
    headers = {"Accept": "application/json", "User-Agent": "Han-OpenClassroom-14Flow/1.0"}
    data: bytes | None = None
    if json_body is not None:
        data = json.dumps(json_body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    elif form is not None:
        data = urlencode(form).encode("utf-8")
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif method.upper() == "POST":
        data = b""
    if token:
        headers["Authorization"] = "Bearer " + token

    started = time.perf_counter()
    status = 0
    raw = b""
    transport_error = ""
    try:
        with urlopen(Request(url, data=data, headers=headers, method=method.upper()), timeout=timeout) as response:
            status = response.status
            raw = response.read()
    except HTTPError as error:
        status = error.code
        raw = error.read()
    except (URLError, TimeoutError, socket.timeout, OSError) as error:
        transport_error = str(getattr(error, "reason", error))

    elapsed_ms = round((time.perf_counter() - started) * 1000)
    payload: Any = None
    decode_error = ""
    if raw:
        try:
            payload = json.loads(raw.decode("utf-8-sig"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            decode_error = raw.decode("utf-8", errors="replace")[:200]
    return {
        "http_status": status,
        "elapsed_ms": elapsed_ms,
        "payload": payload,
        "transport_error": transport_error,
        "decode_error": decode_error,
    }


def business_success(response: dict[str, Any]) -> bool:
    status = response["http_status"]
    payload = response["payload"]
    if status < 200 or status >= 300 or not isinstance(payload, dict):
        return False
    if "success" in payload:
        return payload.get("success") is True and str(payload.get("code")) == "200"
    if "code" in payload:
        return str(payload.get("code")) == "200"
    return True


def response_message(response: dict[str, Any]) -> str:
    if response["transport_error"]:
        return "网络错误: " + response["transport_error"]
    payload = response["payload"]
    if isinstance(payload, dict):
        return str(payload.get("message") or payload.get("msg") or "")
    if response["decode_error"]:
        return "非 JSON 响应: " + response["decode_error"]
    return "空响应"


def classify(success: bool, response: dict[str, Any], dependency: str) -> str:
    if success:
        return "PASS"
    message = response_message(response).lower()
    if response["http_status"] == 0:
        return "NETWORK_BLOCKED"
    if response["http_status"] in (401, 403) or any(
        item in message for item in ("token", "scope", "授权", "凭证", "未获")
    ):
        return "AUTH_BLOCKED"
    if dependency in VIDEO_DEPENDENCIES:
        return "VIDEO_BLOCKED"
    return "PLATFORM_FAILED"


def step(
    results: list[dict[str, Any]],
    base_url: str,
    token: str,
    timeout: int,
    name: str,
    method: str,
    path: str,
    dependency: str,
    query: dict[str, Any] | None = None,
    json_body: dict[str, Any] | None = None,
    validator: Callable[[dict[str, Any]], str | None] | None = None,
) -> tuple[dict[str, Any], dict[str, Any]]:
    response = request_json(base_url, method, path, timeout, token, query, json_body)
    success = business_success(response)
    validation_error = validator(response) if success and validator else None
    if validation_error:
        success = False
    message = validation_error or response_message(response) or ("操作成功" if success else "业务失败")
    payload = response["payload"]
    contract_status = classify(success, response, dependency)
    record = {
        "index": len(results) + 1,
        "name": name,
        "method": method,
        "path": path,
        "dependency": dependency,
        "contract_status": contract_status,
        "status": contract_status,
        "http_status": response["http_status"],
        "business_code": payload.get("code") if isinstance(payload, dict) else None,
        "message": message,
        "elapsed_ms": response["elapsed_ms"],
        "request": sanitized({"query": query or {}, "json": json_body}),
        "response": sanitized(payload),
        "finished_at": datetime.now().astimezone().isoformat(),
    }
    results.append(record)
    print(
        f"[{record['index']:02d}/14] {record['status']:<15} {name} "
        f"(HTTP {record['http_status']}, code {record['business_code']}, {record['elapsed_ms']}ms) {message}"
    )
    return record, response


def skipped(results: list[dict[str, Any]], name: str, method: str, path: str, dependency: str, reason: str) -> None:
    record = {
        "index": len(results) + 1,
        "name": name,
        "method": method,
        "path": path,
        "dependency": dependency,
        "contract_status": "SKIPPED",
        "status": "SKIPPED",
        "http_status": None,
        "business_code": None,
        "message": reason,
        "elapsed_ms": 0,
        "request": None,
        "response": None,
        "finished_at": datetime.now().astimezone().isoformat(),
    }
    results.append(record)
    print(f"[{record['index']:02d}/14] SKIPPED         {name} {reason}")


def payload_result(response: dict[str, Any]) -> Any:
    payload = response.get("payload")
    return payload.get("result") if isinstance(payload, dict) else None


def device_from(response: dict[str, Any]) -> dict[str, Any] | None:
    result = payload_result(response)
    if not isinstance(result, dict) or not result.get("device_code") or not result.get("org_id"):
        return None
    return result


def find_course(response: dict[str, Any], course_name: str) -> dict[str, Any] | None:
    result = payload_result(response)
    records = result.get("records") if isinstance(result, dict) else None
    if not isinstance(records, list):
        return None
    return next((item for item in records if isinstance(item, dict) and item.get("courseName") == course_name), None)


def room_from(response: dict[str, Any]) -> str | None:
    result = payload_result(response)
    if not isinstance(result, dict):
        return None
    value = result.get("roomId") or result.get("room_id")
    return str(value) if value else None


def write_report(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


def run_flow(args: argparse.Namespace) -> int:
    base_url = required_env("OPEN_PLATFORM_BASE_URL").rstrip("/")
    client_id = required_env("OPEN_PLATFORM_CLIENT_ID")
    client_secret = required_env("OPEN_PLATFORM_CLIENT_SECRET")
    device_code = required_env("OPEN_PLATFORM_DEVICE_CODE")
    timeout = positive_int_env("OPEN_PLATFORM_TIMEOUT_SECONDS", 30)
    scope = os.getenv("OPEN_PLATFORM_SCOPE", " ".join(SCOPES)).strip()
    app_id = os.getenv("OPEN_PLATFORM_UPGRADE_APP_ID", "com.example.video").strip()
    version_code = os.getenv("OPEN_PLATFORM_UPGRADE_VERSION_CODE", "1").strip()
    started_at = datetime.now().astimezone()
    run_id = started_at.strftime("%Y%m%d-%H%M%S")
    attend_member_id = os.getenv("OPEN_PLATFORM_ATTEND_MEMBER_ID", "").strip() or f"{device_code}-ATTEND-{run_id}"
    attend_member_name = os.getenv("OPEN_PLATFORM_ATTEND_MEMBER_NAME", "自动联调听讲端").strip()
    report_path = Path(args.report or f".release/open-classroom-14-flow/log-{run_id}.json").resolve()
    results: list[dict[str, Any]] = []
    context: dict[str, Any] = {}

    token_response = request_json(
        base_url,
        "POST",
        "/open/oauth2/token",
        timeout,
        form={
            "grant_type": "client_credentials",
            "client_id": client_id,
            "client_secret": client_secret,
            "scope": scope,
        },
    )
    token_payload = token_response["payload"]
    access_token = token_payload.get("access_token") if isinstance(token_payload, dict) else None
    if not access_token:
        report = {
            "run_id": run_id,
            "started_at": started_at.isoformat(),
            "finished_at": datetime.now().astimezone().isoformat(),
            "base_url": base_url,
            "setup": {
                "status": "FAILED",
                "http_status": token_response["http_status"],
                "message": response_message(token_response),
                "request": sanitized(
                    {
                        "method": "POST",
                        "path": "/open/oauth2/token",
                        "form": {
                            "grant_type": "client_credentials",
                            "client_id": client_id,
                            "client_secret": client_secret,
                            "scope": scope,
                        },
                    }
                ),
                "response": sanitized(token_payload),
            },
            "results": results,
        }
        write_report(report_path, report)
        print(f"[Token] FAILED {report['setup']['message']}")
        print(f"报告: {report_path}")
        return 3
    print(f"[Token] PASS scope={token_payload.get('scope', scope)} expires_in={token_payload.get('expires_in')}")

    def validate_main_device(response: dict[str, Any]) -> str | None:
        device = device_from(response)
        if not device:
            return "接口返回成功，但缺少 device_code 或 org_id"
        context["main_device"] = device
        return None

    _, main_device_response = step(
        results,
        base_url,
        access_token,
        timeout,
        "查询主讲设备",
        "GET",
        "/open/api/v1/classroom/common/getDeviceInfoByDeviceCode",
        "platform-directory",
        query={"deviceCode": device_code},
        validator=validate_main_device,
    )

    main_device = context.get("main_device")
    attend_device = main_device

    step(
        results,
        base_url,
        access_token,
        timeout,
        "查询应用升级信息",
        "GET",
        "/open/api/v1/classroom/user/tAppUpgrade/getAppUpgradeInfo",
        "classroom-core",
        query={"appId": app_id, "versionCode": version_code},
    )

    course_name = "开放平台14接口联调-" + run_id
    begin = datetime.now().replace(microsecond=0)
    end = begin + timedelta(minutes=positive_int_env("OPEN_PLATFORM_COURSE_MINUTES", 60))
    if not main_device or not attend_device:
        skipped(results, "创建设备手动课程", "POST", "/open/api/v1/classroom/tb-course-info/saveCourseInfo", "classroom-core", "设备查询失败")
    else:
        course_body = {
            "courseName": course_name,
            "courseType": "6",
            "courseDesc": "开放平台 14 接口自动联调记录",
            "organId": str(main_device["org_id"]),
            "organName": main_device.get("org_name") or "",
            "memberId": str(main_device["device_code"]),
            "memberName": main_device.get("device_name") or str(main_device["device_code"]),
            "teacherName": str(main_device["device_code"]),
            "timeBegin": begin.strftime("%Y-%m-%d %H:%M:%S"),
            "timeEnd": end.strftime("%Y-%m-%d %H:%M:%S"),
            "isLive": "1",
            "isRecord": "1",
            "viewAuth": "1",
            "reviewAuth": "1",
            "ruleId": "",
            "tbCourseAttendList": [
                {
                    "organId": str(attend_device["org_id"]),
                    "organName": attend_device.get("org_name") or "",
                    "placeId": str(attend_device.get("place_id") or ""),
                    "placeName": attend_device.get("place_name") or "",
                    "memberId": attend_member_id,
                    "memberName": attend_member_name,
                }
            ],
        }
        step(
            results,
            base_url,
            access_token,
            timeout,
            "创建设备手动课程",
            "POST",
            "/open/api/v1/classroom/tb-course-info/saveCourseInfo",
            "classroom-core",
            json_body=course_body,
        )

    def validate_course(response: dict[str, Any]) -> str | None:
        course = find_course(response, course_name)
        if not course or not course.get("courseId"):
            return "接口返回成功，但没有查到刚创建的课程"
        context["course"] = course
        context["course_id"] = str(course["courseId"])
        return None

    if main_device and results[-1]["status"] == "PASS":
        step(
            results,
            base_url,
            access_token,
            timeout,
            "查询并确认新课程",
            "POST",
            "/open/api/v1/classroom/tb-course-info/getCourseInfoList",
            "classroom-core",
            query={
                "courseName": course_name,
                "courseType": "6",
                "organId": str(main_device["org_id"]),
                "pageNum": 1,
                "pageSize": 10,
            },
            validator=validate_course,
        )
    else:
        skipped(results, "查询并确认新课程", "POST", "/open/api/v1/classroom/tb-course-info/getCourseInfoList", "classroom-core", "课程创建失败")

    def validate_room(response: dict[str, Any]) -> str | None:
        room_id = room_from(response)
        if not room_id:
            return "接口返回成功，但没有返回 roomId"
        context["room_id"] = room_id
        return None

    course_id = context.get("course_id")
    if course_id:
        step(
            results,
            base_url,
            access_token,
            timeout,
            "进入课程并创建房间",
            "POST",
            "/open/api/v1/classroom/live/enterCourse",
            "video-capability",
            query={"courseId": course_id},
            validator=validate_room,
        )
    else:
        skipped(results, "进入课程并创建房间", "POST", "/open/api/v1/classroom/live/enterCourse", "video-capability", "缺少 courseId")

    attend_member = attend_member_id if attend_device else ""
    if attend_member:
        step(
            results,
            base_url,
            access_token,
            timeout,
            "订阅课堂事件",
            "GET",
            "/open/api/v1/classroom/event/eventSubscriptions",
            "classroom-event",
            query={"memberId": attend_member},
        )
    else:
        skipped(results, "订阅课堂事件", "GET", "/open/api/v1/classroom/event/eventSubscriptions", "classroom-event", "缺少听讲成员")

    room_id = context.get("room_id")
    main_member = str(main_device["device_code"]) if main_device else ""
    main_name = (main_device or {}).get("device_name") or main_member
    attend_name = attend_member_name

    controls = (
        ("开始课堂", "/open/api/v1/classroom/live/startClassroom", {"memberId": main_member, "roomId": room_id, "name": main_name, "liveType": "1"}),
        ("加入课堂", "/open/api/v1/classroom/live/joinClassroom", {"memberId": attend_member, "roomId": room_id, "name": attend_name, "liveType": "1"}),
        ("查询直播状态", "/open/api/v1/classroom/course/deliveryClassroom/getLiveStatusByUUID", {"roomId": room_id}),
        ("开始录制", "/open/api/v1/classroom/live/StartRecord", {"roomId": room_id}),
        ("静音成员", "/open/api/v1/classroom/live/muteMember", {"muted": "true", "roomId": room_id, "memberId": attend_member}),
        ("踢出成员", "/open/api/v1/classroom/live/kickPeople", {"roomId": room_id, "memberId": attend_member, "allowed": "0"}),
        ("停止录制", "/open/api/v1/classroom/live/StopRecordByUUID", {"roomId": room_id}),
        ("发送下课事件", "/open/api/v1/classroom/event/addClassOverEvent", {"memberId": main_member, "code": 8, "roomId": room_id}),
    )
    for name, path, query in controls:
        method = "GET" if name == "查询直播状态" else "POST"
        dependency = "classroom-event" if name == "发送下课事件" else "video-capability"
        if room_id and main_member and (attend_member or name not in {"加入课堂", "静音成员", "踢出成员"}):
            step(results, base_url, access_token, timeout, name, method, path, dependency, query=query)
        else:
            skipped(results, name, method, path, dependency, "缺少 roomId 或成员信息")

    if tuple(record["name"] for record in results) != ENDPOINTS:
        raise AssertionError("14 接口执行顺序与清单不一致")
    diagnostics: list[dict[str, str]] = []
    start_record = next(record for record in results if record["name"] == "开始录制")
    stop_record = next(record for record in results if record["name"] == "停止录制")
    if start_record["status"] == "PASS" and stop_record["status"] != "PASS" and "录制" in stop_record["message"]:
        start_record["status"] = "PLATFORM_FALSE_SUCCESS"
        start_record["message"] += "；但同一流程停止录制时确认未生成课件录制信息"
        diagnostics.append(
            {
                "type": "响应一致性缺陷",
                "message": "开始录制返回成功，但停止录制立即报告未生成录制信息；需结合视频能力日志定位原始失败。",
            }
        )
        print("[诊断] PLATFORM_FALSE_SUCCESS 开始录制响应与后续状态不一致")
    live_status = next(record for record in results if record["name"] == "查询直播状态")
    live_result = (live_status.get("response") or {}).get("result")
    if isinstance(live_result, dict) and live_result.get("isLive") is False:
        diagnostics.append(
            {
                "type": "RTC 会话未建立",
                "message": "开课和加入接口已签发 RTC 凭证，但测试脚本未建立 WebRTC 连接；录制、静音、踢人只能完成 HTTP 契约验证。",
            }
        )
        print("[诊断] RTC_NOT_CONNECTED 直播状态为 false，视频控制缺少真实在线成员")
    for record in results:
        record["e2e_status"] = record["status"]
        if (
            isinstance(live_result, dict)
            and live_result.get("isLive") is False
            and record["status"] == "PASS"
            and record["name"] in {"静音成员", "踢出成员"}
        ):
            record["e2e_status"] = "RTC_NOT_CONNECTED"
    summary: dict[str, int] = {}
    for record in results:
        summary[record["status"]] = summary.get(record["status"], 0) + 1
    report = {
        "run_id": run_id,
        "started_at": started_at.isoformat(),
        "finished_at": datetime.now().astimezone().isoformat(),
        "base_url": base_url,
        "setup": {
            "status": "PASS",
            "scope": token_payload.get("scope", scope),
            "expires_in": token_payload.get("expires_in"),
            "request": sanitized(
                {
                    "method": "POST",
                    "path": "/open/oauth2/token",
                    "form": {
                        "grant_type": "client_credentials",
                        "client_id": client_id,
                        "client_secret": client_secret,
                        "scope": scope,
                    },
                }
            ),
            "response": sanitized(token_payload),
        },
        "flow": {
            "courseName": course_name,
            "courseId": context.get("course_id"),
            "roomId": context.get("room_id"),
            "organId": str(main_device.get("org_id")) if main_device else None,
            "mainDeviceCode": device_code,
            "attendMemberId": attend_member_id,
            "note": "课程记录用于联调追踪；14 接口不包含删除课程。",
        },
        "summary": summary,
        "diagnostics": diagnostics,
        "results": results,
    }
    write_report(report_path, report)
    print("汇总: " + ", ".join(f"{key}={value}" for key, value in sorted(summary.items())))
    print(f"报告: {report_path}")
    return 0 if summary == {"PASS": 14} else 2


def self_test() -> None:
    assert len(ENDPOINTS) == 14 and len(set(ENDPOINTS)) == 14
    redacted = sanitized({"access_token": "abc", "client_secret": "def", "url": "https://x.test/a?t=1"})
    assert redacted == {"access_token": "***", "client_secret": "***", "url": "https://x.test/a"}
    assert classify(True, {"http_status": 200, "payload": {}, "transport_error": "", "decode_error": ""}, "video-capability") == "PASS"
    assert classify(False, {"http_status": 403, "payload": {}, "transport_error": "", "decode_error": ""}, "video-capability") == "AUTH_BLOCKED"
    assert classify(False, {"http_status": 200, "payload": {"message": "失败"}, "transport_error": "", "decode_error": ""}, "video-capability") == "VIDEO_BLOCKED"
    sample = {"payload": {"result": {"records": [{"courseName": "联调", "courseId": "1"}]}}}
    assert find_course(sample, "联调") == {"courseName": "联调", "courseId": "1"}
    print("SELF-TEST PASS: 14 接口清单、脱敏、分层和课程回查逻辑正常")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="开放平台视频课堂 14 接口完整流程联调")
    parser.add_argument("--execute-writes", action="store_true", help="允许创建课程并调用开课/录制/成员控制/下课接口")
    parser.add_argument("--report", help="JSON 报告路径；默认写入 .release/open-classroom-14-flow/")
    parser.add_argument("--self-test", action="store_true", help="只检查脚本，不访问网络")
    return parser.parse_args()


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    args = parse_args()
    if args.self_test:
        self_test()
        return 0
    if not args.execute_writes:
        print("拒绝执行：完整流程会创建课程并调用课堂控制接口，请显式传 --execute-writes。", file=sys.stderr)
        return 4
    try:
        return run_flow(args)
    except (ValueError, AssertionError) as error:
        print("配置或脚本错误: " + str(error), file=sys.stderr)
        return 4


if __name__ == "__main__":
    raise SystemExit(main())
