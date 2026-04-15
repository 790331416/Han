#!/usr/bin/env bash
set -euo pipefail

check_url() {
  local label="$1"
  local url="$2"
  echo "[verify-95] checking ${label}: ${url}"
  curl -fsS "${url}" >/dev/null
}

check_url "small runtime" "http://127.0.0.1:19090/system/runtime/capabilities"
check_url "medium runtime" "http://127.0.0.1:29090/system/runtime/capabilities"
check_url "full runtime" "http://127.0.0.1:9090/system/runtime/capabilities"

check_url "small ui" "http://127.0.0.1:3100/"
check_url "medium ui" "http://127.0.0.1:3200/"
check_url "full ui" "http://127.0.0.1:3000/"

echo "[verify-95] runtime and ui checks passed"
