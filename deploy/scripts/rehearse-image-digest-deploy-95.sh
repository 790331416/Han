#!/usr/bin/env bash
set -euo pipefail

REGISTRY="${HAN_IMAGE_REGISTRY:-registry.cn-hangzhou.aliyuncs.com/xzy0112}"
TARGET_ROOT="${HAN_95_ROOT:-/opt/han}"
TARGET_TIER="all"
TAG=""
APPLY=0

usage() {
  cat <<'USAGE'
Usage:
  rehearse-image-digest-deploy-95.sh --tag <tag> [--target medium|full|all] [--apply]

Resolves service image digests from a registry tag and, when --apply is set,
recreates the matching 95 services with digest-pinned images.

Targets:
  medium: open,file
  full:   open,file,ai
  all:    medium and full

Safety:
  This script does not build images, edit .env, delete volumes, clear data,
  or read secrets. Without --apply it only prints the resolved digest pins.
  With --apply it only runs docker compose pull/up for the target services
  using temporary environment variables.
USAGE
}

remote_digest() {
  local image="$1"
  local digest

  digest="$(
    docker manifest inspect --verbose "${image}" \
      | sed -n 's/.*"digest": "\(sha256:[^"]*\)".*/\1/p' \
      | head -n 1
  )"

  if [[ -z "${digest}" ]]; then
    echo "[digest-rehearsal] failed to resolve digest for ${image}" >&2
    return 1
  fi
  echo "${digest}"
}

env_var_for_service() {
  local service="$1"
  local name

  name="${service//-/_}"
  name="$(printf '%s' "${name}" | tr '[:lower:]' '[:upper:]')"
  echo "HAN_${name}_IMAGE"
}

services_for_tier() {
  local tier="$1"
  case "${tier}" in
    medium)
      echo "open file"
      ;;
    full)
      echo "open file ai"
      ;;
    *)
      echo "[digest-rehearsal] unsupported tier: ${tier}" >&2
      return 2
      ;;
  esac
}

wait_service() {
  local deploy_dir="$1"
  local service="$2"
  local cid state

  if (
    cd "${deploy_dir}"
    for _ in $(seq 1 60); do
      cid="$(docker compose ps -q "${service}" || true)"
      if [[ -n "${cid}" ]]; then
        state="$(
          docker inspect \
            --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
            "${cid}" 2>/dev/null || true
        )"
        if [[ "${state}" == "healthy" || "${state}" == "running" ]]; then
          echo "[digest-rehearsal] ${deploy_dir##*/}/${service} ${state}"
          return 0
        fi
        echo "[digest-rehearsal] waiting ${deploy_dir##*/}/${service}: ${state:-unknown}"
      else
        echo "[digest-rehearsal] waiting ${deploy_dir##*/}/${service}: no container"
      fi
      sleep 5
    done
    docker compose ps "${service}" || true
    exit 1
  ); then
    return 0
  fi

  echo "[digest-rehearsal] service not healthy: ${deploy_dir}/${service}" >&2
  return 1
}

run_tier() {
  local tier="$1"
  local deploy_dir="${TARGET_ROOT}/deploy/${tier}"
  local service image digest env_var
  declare -a services=()
  declare -a env_args=()

  if [[ ! -d "${deploy_dir}" ]]; then
    echo "[digest-rehearsal] missing deploy dir: ${deploy_dir}" >&2
    return 1
  fi

  read -r -a services <<<"$(services_for_tier "${tier}")"
  echo "[digest-rehearsal] tier: ${tier}"
  echo "[digest-rehearsal] deploy dir: ${deploy_dir}"

  for service in "${services[@]}"; do
    image="${REGISTRY}/han-${service}:${TAG}"
    digest="$(remote_digest "${image}")"
    env_var="$(env_var_for_service "${service}")"
    env_args+=("${env_var}=${REGISTRY}/han-${service}@${digest}")
    echo "[digest-rehearsal] ${env_var}=${REGISTRY}/han-${service}@${digest}"
  done

  if [[ "${APPLY}" -ne 1 ]]; then
    echo "[digest-rehearsal] dry run for ${tier}; pass --apply to recreate services"
    return 0
  fi

  (
    cd "${deploy_dir}"
    env "${env_args[@]}" docker compose pull "${services[@]}"
    env "${env_args[@]}" docker compose up -d --no-deps --force-recreate "${services[@]}"
  )

  for service in "${services[@]}"; do
    wait_service "${deploy_dir}" "${service}"
  done
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      [[ $# -ge 2 ]] || { echo "--tag requires a value" >&2; exit 2; }
      TAG="$2"
      shift 2
      ;;
    --target)
      [[ $# -ge 2 ]] || { echo "--target requires a value" >&2; exit 2; }
      TARGET_TIER="$2"
      shift 2
      ;;
    --registry)
      [[ $# -ge 2 ]] || { echo "--registry requires a value" >&2; exit 2; }
      REGISTRY="$2"
      shift 2
      ;;
    --apply)
      APPLY=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "${TAG}" ]]; then
  echo "--tag is required" >&2
  exit 2
fi

echo "[digest-rehearsal] registry: ${REGISTRY}"
echo "[digest-rehearsal] tag: ${TAG}"
echo "[digest-rehearsal] target: ${TARGET_TIER}"
echo "[digest-rehearsal] apply: ${APPLY}"

case "${TARGET_TIER}" in
  medium)
    run_tier medium
    ;;
  full)
    run_tier full
    ;;
  all)
    run_tier medium
    run_tier full
    ;;
  *)
    echo "Usage: $0 --tag <tag> [--target medium|full|all] [--apply]" >&2
    exit 2
    ;;
esac

echo "[digest-rehearsal] image digest deployment rehearsal passed"
