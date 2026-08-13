#!/usr/bin/env bash
set -euo pipefail

REGISTRY="${HAN_IMAGE_REGISTRY:-registry.cn-hangzhou.aliyuncs.com/xzy0112}"
TAG=""
SERVICES="gateway,auth,system,job,tenant,workflow,open,file,ai,gen,ui"
OUTPUT=""
SOURCE_COMMIT=""

usage() {
  cat <<'USAGE'
Usage:
  generate-image-release-manifest-95.sh --tag <tag> [--services <list>] [--output <path>] [--source-commit <commit>]

Resolves remote image digests for a release tag and writes a deterministic
env-style manifest that can be consumed by rehearse-image-digest-deploy-95.sh.

Safety:
  This script reads remote registry manifests only. It does not build images,
  push images, pull images, restart services, edit .env, delete volumes, or
  read secrets.
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
    echo "[release-manifest] failed to resolve digest for ${image}" >&2
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

normalized_services() {
  local item
  local old_ifs="${IFS}"
  declare -a requested=()

  IFS=","
  read -r -a requested <<<"${SERVICES}"
  IFS="${old_ifs}"

  for item in "${requested[@]}"; do
    item="${item//[[:space:]]/}"
    item="${item#han-}"
    [[ -n "${item}" ]] && printf '%s\n' "${item}"
  done
}

emit_manifest() {
  local service image digest env_var

  echo "# Han image release manifest"
  echo "# generated_by=deploy/scripts/generate-image-release-manifest-95.sh"
  echo "# release_tag=${TAG}"
  echo "# source_commit=${SOURCE_COMMIT:-unknown}"
  echo "# registry=${REGISTRY}"
  echo "# services=${SERVICES}"
  echo "HAN_RELEASE_TAG=${TAG}"
  echo "HAN_RELEASE_SOURCE_COMMIT=${SOURCE_COMMIT:-unknown}"
  echo "HAN_RELEASE_REGISTRY=${REGISTRY}"
  echo "HAN_RELEASE_SERVICES=${SERVICES}"

  while IFS= read -r service; do
    image="${REGISTRY}/han-${service}:${TAG}"
    digest="$(remote_digest "${image}")"
    env_var="$(env_var_for_service "${service}")"
    echo "${env_var}=${REGISTRY}/han-${service}@${digest}"
  done < <(normalized_services)
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      [[ $# -ge 2 ]] || { echo "--tag requires a value" >&2; exit 2; }
      TAG="$2"
      shift 2
      ;;
    --services)
      [[ $# -ge 2 ]] || { echo "--services requires a value" >&2; exit 2; }
      SERVICES="$2"
      shift 2
      ;;
    --output)
      [[ $# -ge 2 ]] || { echo "--output requires a value" >&2; exit 2; }
      OUTPUT="$2"
      shift 2
      ;;
    --source-commit)
      [[ $# -ge 2 ]] || { echo "--source-commit requires a value" >&2; exit 2; }
      SOURCE_COMMIT="$2"
      shift 2
      ;;
    --registry)
      [[ $# -ge 2 ]] || { echo "--registry requires a value" >&2; exit 2; }
      REGISTRY="$2"
      shift 2
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

if [[ -z "$(normalized_services)" ]]; then
  echo "--services resolved to an empty list" >&2
  exit 2
fi

if [[ -n "${OUTPUT}" ]]; then
  mkdir -p "$(dirname "${OUTPUT}")"
  tmp="$(mktemp)"
  emit_manifest >"${tmp}"
  mv "${tmp}" "${OUTPUT}"
  echo "[release-manifest] wrote ${OUTPUT}"
else
  emit_manifest
fi
