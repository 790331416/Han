#!/usr/bin/env bash
set -euo pipefail

TARGET_TIER="${1:-all}"

case "${TARGET_TIER}" in
  medium|full|all)
    ;;
  *)
    echo "Usage: $0 [medium|full|all]" >&2
    exit 2
    ;;
esac

if ! command -v python3 >/dev/null 2>&1; then
  echo "[verify-file-service-95] python3 is required" >&2
  exit 1
fi

python3 - "${TARGET_TIER}" <<'PY'
import datetime
import hashlib
import hmac
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ACCESS_KEY_ENV = "RUSTFS_ACCESS_KEY"
SECRET_KEY_ENV = "RUSTFS_SECRET_KEY"
REGION_DEFAULT = "us-east-1"
SERVICE = "s3"
# 探针账号一律由环境变量注入，不再内置超管默认登录口令。
USERNAME = os.environ.get("HAN_FILE_PROBE_USERNAME", "")
PASSWORD = os.environ.get("HAN_FILE_PROBE_PASSWORD", "")
if not USERNAME or not PASSWORD:
    print(
        "[verify-file-service-95] HAN_FILE_PROBE_USERNAME and HAN_FILE_PROBE_PASSWORD are required",
        file=sys.stderr,
    )
    sys.exit(2)

TIERS = {
    "medium": {
        "gateway": "http://127.0.0.1:29090",
        "rustfs": "http://127.0.0.1:29000",
        "deploy_dir": "/opt/han/deploy/medium",
    },
    "full": {
        "gateway": "http://127.0.0.1:9090",
        "rustfs": "http://127.0.0.1:9000",
        "deploy_dir": "/opt/han/deploy/full",
    },
}


class ProbeError(RuntimeError):
    pass


def selected_tiers(target):
    if target == "all":
        return ["medium", "full"]
    return [target]


def http_request(method, url, body=None, headers=None, timeout=30):
    data = body.encode("utf-8") if isinstance(body, str) else body
    req = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, dict(resp.headers), resp.read()
    except urllib.error.HTTPError as exc:
        return exc.code, dict(exc.headers), exc.read()


def parse_json(data):
    try:
        return json.loads(data.decode("utf-8")) if data else None
    except Exception:
        return {"raw": data.decode("utf-8", errors="replace")}


def json_request(method, url, payload=None, headers=None):
    merged = {"Content-Type": "application/json;charset=UTF-8"}
    if headers:
        merged.update(headers)
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    status, resp_headers, data = http_request(method, url, body=body, headers=merged)
    return status, resp_headers, parse_json(data)


def assert_r_ok(tier, label, status, payload):
    if status != 200 or not isinstance(payload, dict) or payload.get("code") != 200:
        raise ProbeError(f"{tier} {label} failed: http={status}, payload={payload}")


def login(tier, gateway):
    status, _, payload = json_request("POST", gateway + "/auth/app/login", {
        "username": USERNAME,
        "password": PASSWORD,
        "deviceId": "codex-file-probe-" + tier,
    })
    assert_r_ok(tier, "login", status, payload)
    token = (payload.get("data") or {}).get("accessToken")
    if not token:
        raise ProbeError(f"{tier} login returned no accessToken")
    return token


def multipart_body(field, filename, content, content_type="text/plain; charset=utf-8"):
    boundary = "----CodexFileProbe" + uuid.uuid4().hex
    lines = [("--" + boundary).encode()]
    disposition = f'Content-Disposition: form-data; name="{field}"; filename="{filename}"'
    lines.append(disposition.encode("utf-8"))
    lines.append(("Content-Type: " + content_type).encode("utf-8"))
    lines.append(b"")
    lines.append(content)
    lines.append(("--" + boundary + "--").encode())
    lines.append(b"")
    return boundary, b"\r\n".join(lines)


def upload(tier, gateway, token, marker):
    filename = f"codex-file-probe-{tier}-{int(time.time())}.txt"
    content = (marker + "\n").encode("utf-8")
    boundary, body = multipart_body("file", filename, content)
    headers = {
        "Authorization": "Bearer " + token,
        "Content-Type": "multipart/form-data; boundary=" + boundary,
        "Content-Length": str(len(body)),
    }
    status, _, data = http_request("POST", gateway + "/file/upload", body=body, headers=headers, timeout=60)
    payload = parse_json(data)
    assert_r_ok(tier, "upload", status, payload)
    info = payload.get("data") or {}
    object_name = info.get("name")
    public_url = info.get("url")
    if not object_name or not public_url:
        raise ProbeError(f"{tier} upload returned incomplete data: {payload}")
    return object_name, public_url, content


def download_public(tier, url, expected=None, label="download"):
    status, _, data = http_request("GET", url, timeout=60)
    if status != 200:
        raise ProbeError(f"{tier} {label} unexpected http={status}, body={data[:200]!r}")
    if expected is not None and data != expected:
        raise ProbeError(f"{tier} {label} content mismatch: expected={expected!r}, actual={data!r}")
    return status


def expect_business_404(tier, url, label):
    status, _, data = http_request("GET", url, timeout=30)
    payload = parse_json(data)
    if not (status == 200 and isinstance(payload, dict) and payload.get("code") == 404):
        raise ProbeError(f"{tier} {label} expected http=200/code=404, got http={status}, payload={payload}")
    return status, payload.get("code"), payload.get("msg")


def compose_env(deploy_dir, key):
    script = f'printf "%s" "${{{key}:-}}"'
    proc = subprocess.run(
        ["docker", "compose", "exec", "-T", "rustfs", "sh", "-lc", script],
        cwd=deploy_dir,
        check=False,
        universal_newlines=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if proc.returncode != 0:
        raise ProbeError(f"failed to read {key} from rustfs container in {deploy_dir}: {proc.stderr.strip()}")
    return proc.stdout.strip()


def psql_query(deploy_dir, sql):
    proc = subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "han", "-d", "han", "-Atc", sql],
        cwd=deploy_dir,
        check=False,
        universal_newlines=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if proc.returncode != 0:
        raise ProbeError(f"psql query failed in {deploy_dir}: {proc.stderr.strip()}")
    return proc.stdout.strip()


def normalize_endpoint(endpoint, fallback_host_endpoint):
    endpoint = (endpoint or "").strip()
    if not endpoint:
        return fallback_host_endpoint
    if "://rustfs:" in endpoint or endpoint.endswith("//rustfs:9000") or endpoint.endswith("://rustfs:9000"):
        return fallback_host_endpoint
    return endpoint.rstrip("/")


def parse_public_path(public_url):
    parts = urllib.parse.urlparse(public_url).path.strip("/").split("/")
    if len(parts) < 4 or parts[0] != "file" or parts[1] != "public":
        raise ProbeError(f"unexpected public file url path: {public_url}")
    locator = parts[2]
    object_name = urllib.parse.unquote("/".join(parts[3:]))
    return locator, object_name


def storage_config(tier, cfg, locator, object_name):
    deploy_dir = cfg["deploy_dir"]
    if locator.startswith("db-"):
        oss_config_id = locator[3:]
        if not oss_config_id.isdigit():
            raise ProbeError(f"{tier} invalid db locator: {locator}")
        sql = (
            "select coalesce(endpoint,''), coalesce(access_key,''), coalesce(secret_key,''), "
            "coalesce(bucket_name,''), coalesce(prefix,''), coalesce(region,''), coalesce(is_https,'') "
            f"from sys_oss_config where oss_config_id = {oss_config_id} limit 1"
        )
        row = psql_query(deploy_dir, sql)
        if not row:
            raise ProbeError(f"{tier} no sys_oss_config row for locator {locator}")
        endpoint, access_key, secret_key, bucket, prefix, region, is_https = (row.split("|") + [""] * 7)[:7]
    else:
        endpoint = cfg["rustfs"]
        access_key = os.environ.get(ACCESS_KEY_ENV) or compose_env(deploy_dir, ACCESS_KEY_ENV)
        secret_key = os.environ.get(SECRET_KEY_ENV) or compose_env(deploy_dir, SECRET_KEY_ENV)
        bucket = os.environ.get("HAN_FILE_PROBE_BUCKET", "han")
        prefix = os.environ.get("HAN_FILE_PROBE_PREFIX", "")
        region = os.environ.get("HAN_FILE_PROBE_REGION", REGION_DEFAULT)
        is_https = "1"
    if not access_key or not secret_key or not bucket:
        raise ProbeError(f"{tier} storage credentials or bucket missing for locator {locator}")
    endpoint = normalize_endpoint(endpoint, cfg["rustfs"])
    region = region or REGION_DEFAULT
    normalized_prefix = prefix.strip().strip("/")
    object_key = f"{normalized_prefix}/{object_name}" if normalized_prefix else object_name
    return {
        "endpoint": endpoint,
        "access_key": access_key,
        "secret_key": secret_key,
        "bucket": bucket,
        "region": region,
        "object_key": object_key,
        "is_https": is_https,
    }


def sign_key(secret_key, date_stamp, region_name, service_name):
    k_date = hmac.new(("AWS4" + secret_key).encode("utf-8"), date_stamp.encode("utf-8"), hashlib.sha256).digest()
    k_region = hmac.new(k_date, region_name.encode("utf-8"), hashlib.sha256).digest()
    k_service = hmac.new(k_region, service_name.encode("utf-8"), hashlib.sha256).digest()
    return hmac.new(k_service, b"aws4_request", hashlib.sha256).digest()


def s3_signed_request(method, storage):
    endpoint = storage["endpoint"].rstrip("/")
    parsed = urllib.parse.urlparse(endpoint)
    host = parsed.netloc
    path = "/" + urllib.parse.quote(storage["bucket"], safe="") + "/" + urllib.parse.quote(storage["object_key"], safe="/~")
    url = endpoint + path
    # datetime.utcnow() 在 Python 3.12 已废弃，未来版本会移除；这里生成的是
    # SigV4 的 x-amz-date，行为一旦变化会导致签名时间戳错误、探针对象删不掉。
    now = datetime.datetime.now(datetime.timezone.utc)
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    date_stamp = now.strftime("%Y%m%d")
    payload_hash = hashlib.sha256(b"").hexdigest()
    canonical_headers = (
        "host:" + host + "\n"
        "x-amz-content-sha256:" + payload_hash + "\n"
        "x-amz-date:" + amz_date + "\n"
    )
    signed_headers = "host;x-amz-content-sha256;x-amz-date"
    canonical_request = "\n".join([method, path, "", canonical_headers, signed_headers, payload_hash])
    credential_scope = f"{date_stamp}/{storage['region']}/{SERVICE}/aws4_request"
    string_to_sign = "\n".join([
        "AWS4-HMAC-SHA256",
        amz_date,
        credential_scope,
        hashlib.sha256(canonical_request.encode("utf-8")).hexdigest(),
    ])
    signature = hmac.new(
        sign_key(storage["secret_key"], date_stamp, storage["region"], SERVICE),
        string_to_sign.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    headers = {
        "Host": host,
        "x-amz-content-sha256": payload_hash,
        "x-amz-date": amz_date,
        "Authorization": (
            f"AWS4-HMAC-SHA256 Credential={storage['access_key']}/{credential_scope}, "
            f"SignedHeaders={signed_headers}, Signature={signature}"
        ),
    }
    return http_request(method, url, body=None, headers=headers, timeout=30)


def delete_object(tier, storage):
    status, _, data = s3_signed_request("DELETE", storage)
    if status not in (200, 202, 204):
        raise ProbeError(f"{tier} s3 delete failed: http={status}, body={data[:200]!r}")
    status, _, _ = s3_signed_request("HEAD", storage)
    if status != 404:
        raise ProbeError(f"{tier} object still exists or unexpected HEAD status after delete: http={status}")
    return status


def probe_tier(tier):
    cfg = TIERS[tier]
    gateway = cfg["gateway"]
    marker = f"han-file-probe tier={tier} marker={uuid.uuid4().hex}"
    print(f"[verify-file-service-95] checking {tier} file service through {gateway}")
    token = login(tier, gateway)
    invalid_http, invalid_code, invalid_msg = expect_business_404(
        tier,
        gateway + "/file/public/not-a-valid-locator/no-file.txt",
        "invalid locator",
    )
    object_name = None
    public_url = None
    storage = None
    try:
        object_name, public_url, expected = upload(tier, gateway, token, marker)
        locator, url_object_name = parse_public_path(public_url)
        if url_object_name != object_name:
            raise ProbeError(f"{tier} upload name and public url object mismatch: {object_name} vs {url_object_name}")
        storage = storage_config(tier, cfg, locator, object_name)
        download_http = download_public(tier, public_url, expected=expected, label="public download")
        delete_head_http = delete_object(tier, storage)
        after_http, after_code, after_msg = expect_business_404(tier, public_url, "public download after delete")
        result = {
            "tier": tier,
            "login_http": 200,
            "upload_http": 200,
            "download_http": download_http,
            "invalid_locator_http": invalid_http,
            "invalid_locator_code": invalid_code,
            "invalid_locator_msg": invalid_msg,
            "delete_head_http": delete_head_http,
            "after_delete_http": after_http,
            "after_delete_code": after_code,
            "after_delete_msg": after_msg,
            "locator": locator,
            "object_name": object_name,
        }
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    except Exception:
        if storage is not None:
            try:
                delete_object(tier, storage)
                print(f"[verify-file-service-95] cleaned object after failure: {tier} {object_name}")
            except Exception as cleanup_error:
                print(f"[verify-file-service-95] cleanup failed for {tier} {object_name}: {cleanup_error}", file=sys.stderr)
        raise


def main():
    target = sys.argv[1] if len(sys.argv) > 1 else "all"
    for tier in selected_tiers(target):
        probe_tier(tier)
    print(f"[verify-file-service-95] {target} file service checks passed")


if __name__ == "__main__":
    main()
PY
