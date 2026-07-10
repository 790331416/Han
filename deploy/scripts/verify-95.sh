#!/usr/bin/env bash
set -euo pipefail

TARGET_TIER="${1:-all}"

check_url() {
  local label="$1"
  local url="$2"
  echo "[verify-95] checking ${label}: ${url}"
  curl -fsS "${url}" >/dev/null
}

check_tier() {
  local tier="$1"
  local runtime_url="$2"
  local ui_url="$3"

  check_url "${tier} runtime" "${runtime_url}"
  check_url "${tier} ui" "${ui_url}"
}

check_full_tier() {
  check_tier "full" "http://127.0.0.1:9090/system/runtime/capabilities" "http://127.0.0.1:3000/"
  check_url "full aivideo actuator" "http://127.0.0.1:9209/actuator/health"
  check_url "full aivideo studio" "http://127.0.0.1:3000/studio/projects"
  check_url "full aivideo task console" "http://127.0.0.1:3000/ai/aivideo/tasks"
}

case "${TARGET_TIER}" in
  small)
    check_tier "small" "http://127.0.0.1:19090/system/runtime/capabilities" "http://127.0.0.1:3100/"
    ;;
  medium)
    check_tier "medium" "http://127.0.0.1:29090/system/runtime/capabilities" "http://127.0.0.1:3200/"
    ;;
  full)
    check_full_tier
    ;;
  all)
    check_tier "small" "http://127.0.0.1:19090/system/runtime/capabilities" "http://127.0.0.1:3100/"
    check_tier "medium" "http://127.0.0.1:29090/system/runtime/capabilities" "http://127.0.0.1:3200/"
    check_full_tier
    ;;
  *)
    echo "Usage: $0 [small|medium|full|all]" >&2
    exit 2
    ;;
esac

echo "[verify-95] ${TARGET_TIER} runtime and ui checks passed"
