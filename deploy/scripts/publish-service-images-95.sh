#!/usr/bin/env bash
set -euo pipefail

REGISTRY="${HAN_IMAGE_REGISTRY:-registry.cn-hangzhou.aliyuncs.com/xzy0112}"
TAG="${HAN_IMAGE_TAG:-latest}"
SOURCE_TAG="${HAN_IMAGE_SOURCE_TAG:-}"
VERIFY_PULL=0
CONFIRM=0
DRY_RUN=0
RETAG_FROM_SOURCE=0
declare -a IMAGE_TOKENS=()
declare -a TARGET_IMAGES=()
declare -a DEFAULT_SERVICES=("han-open" "han-file" "han-ai")

usage() {
  cat <<'USAGE'
Usage:
  publish-service-images-95.sh [--images open,file,ai|all|<image>] [--verify-pull] [--yes] [--dry-run]

Publishes already-built service images from the current Docker host to the
configured registry, then verifies that the remote manifest exists.

Defaults:
  registry: registry.cn-hangzhou.aliyuncs.com/xzy0112
  tag:      latest
  images:   han-open, han-file, han-ai

Options:
  --images <list>       Comma-separated services or full image refs.
                        Service names may be open,file,ai or han-open style.
  --image <image>       Add one image or service token. Can be repeated.
  --registry <registry> Override registry prefix.
  --tag <tag>           Override tag for service tokens.
  --source-tag <tag>    Tag from the same image repository with this source tag
                        when publishing an immutable target tag.
  --retag-from-source   Always retag the target image from --source-tag before push.
  --verify-pull         Run docker pull after push to prove registry pull path.
  --yes                 Confirm latest-tag push.
  --dry-run             Print selected images without pushing.
  -h, --help            Show this help.

Safety:
  This script does not build images, restart containers, delete images,
  delete volumes, or read secrets. It requires local images to already exist,
  or to be copied from an existing --source-tag on the same repository.
  Any registry push is a release action, so --yes or
  HAN_IMAGE_PUBLISH_CONFIRM=1 is required unless --dry-run is used.
USAGE
}

add_image_token() {
  local token="$1"
  local item

  token="${token//[[:space:]]/}"
  [[ -z "${token}" ]] && return 0

  if [[ "${token}" == *","* ]]; then
    local old_ifs="${IFS}"
    IFS=","
    for item in ${token}; do
      add_image_token "${item}"
    done
    IFS="${old_ifs}"
    return 0
  fi

  if [[ "${token}" == "all" ]]; then
    for item in "${DEFAULT_SERVICES[@]}"; do
      add_image_token "${item}"
    done
    return 0
  fi

  IMAGE_TOKENS+=("${token}")
}

resolve_image_token() {
  local token="$1"
  local image

  if [[ "${token}" == */* ]]; then
    image="${token}"
    if [[ "${image}" != *":"* ]]; then
      image="${image}:${TAG}"
    fi
  elif [[ "${token}" == han-* ]]; then
    image="${REGISTRY}/${token}:${TAG}"
  else
    image="${REGISTRY}/han-${token}:${TAG}"
  fi

  echo "${image}"
}

build_target_images() {
  local token

  if [[ "${#IMAGE_TOKENS[@]}" -eq 0 ]]; then
    add_image_token "all"
  fi

  TARGET_IMAGES=()
  for token in "${IMAGE_TOKENS[@]}"; do
    TARGET_IMAGES+=("$(resolve_image_token "${token}")")
  done
}

dedupe_images() {
  local image seen
  declare -A seen_images=()
  declare -a unique_images=()
  for image in "${TARGET_IMAGES[@]}"; do
    seen="${seen_images[${image}]:-}"
    if [[ -z "${seen}" ]]; then
      unique_images+=("${image}")
      seen_images["${image}"]=1
    fi
  done
  TARGET_IMAGES=("${unique_images[@]}")
}

image_repo() {
  local image="$1"
  local tail="${image##*/}"

  if [[ "${tail}" == *":"* ]]; then
    echo "${image%:*}"
  else
    echo "${image}"
  fi
}

source_image_for() {
  local target_image="$1"
  local repo

  repo="$(image_repo "${target_image}")"
  echo "${repo}:${SOURCE_TAG}"
}

env_var_for_image() {
  local image="$1"
  local repo name

  repo="$(image_repo "${image}")"
  name="${repo##*/}"
  name="${name#han-}"
  name="${name//-/_}"
  name="$(printf '%s' "${name}" | tr '[:lower:]' '[:upper:]')"
  echo "HAN_${name}_IMAGE"
}

remote_digest() {
  local image="$1"
  local manifest_file="$2"
  local err_file="${manifest_file}.err"
  local digest

  if docker manifest inspect --verbose "${image}" >"${manifest_file}" 2>"${err_file}"; then
    digest="$(sed -n 's/.*"digest": "\(sha256:[^"]*\)".*/\1/p' "${manifest_file}" | head -n 1 || true)"
    if [[ -n "${digest}" ]]; then
      echo "${digest}"
    else
      echo "manifest-present-digest-unavailable"
    fi
    return 0
  fi

  if [[ -s "${err_file}" ]]; then
    sed 's/^/[publish-images] remote manifest error: /' "${err_file}" >&2
  fi
  return 1
}

require_local_image() {
  local image="$1"
  docker image inspect "${image}" --format '{{.Id}}'
}

ensure_target_image() {
  local image="$1"
  local source_image source_id

  if [[ -n "${SOURCE_TAG}" && "${RETAG_FROM_SOURCE}" -eq 1 ]]; then
    source_image="$(source_image_for "${image}")"
    echo "[publish-images] retagging source image: ${source_image} -> ${image}"
    source_id="$(require_local_image "${source_image}")"
    docker tag "${source_image}" "${image}"
    echo "[publish-images] source image id: ${source_id}"
    return 0
  fi

  if require_local_image "${image}" >/dev/null 2>&1; then
    return 0
  fi

  if [[ -n "${SOURCE_TAG}" ]]; then
    source_image="$(source_image_for "${image}")"
    echo "[publish-images] target image missing, tagging source image: ${source_image} -> ${image}"
    source_id="$(require_local_image "${source_image}")"
    docker tag "${source_image}" "${image}"
    echo "[publish-images] source image id: ${source_id}"
    return 0
  fi

  require_local_image "${image}" >/dev/null
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --images)
      [[ $# -ge 2 ]] || { echo "--images requires a value" >&2; exit 2; }
      add_image_token "$2"
      shift 2
      ;;
    --image)
      [[ $# -ge 2 ]] || { echo "--image requires a value" >&2; exit 2; }
      add_image_token "$2"
      shift 2
      ;;
    --registry)
      [[ $# -ge 2 ]] || { echo "--registry requires a value" >&2; exit 2; }
      REGISTRY="$2"
      shift 2
      ;;
    --tag)
      [[ $# -ge 2 ]] || { echo "--tag requires a value" >&2; exit 2; }
      TAG="$2"
      shift 2
      ;;
    --source-tag)
      [[ $# -ge 2 ]] || { echo "--source-tag requires a value" >&2; exit 2; }
      SOURCE_TAG="$2"
      shift 2
      ;;
    --retag-from-source)
      RETAG_FROM_SOURCE=1
      shift
      ;;
    --verify-pull)
      VERIFY_PULL=1
      shift
      ;;
    --yes)
      CONFIRM=1
      shift
      ;;
    --dry-run)
      DRY_RUN=1
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

if [[ "${RETAG_FROM_SOURCE}" -eq 1 && -z "${SOURCE_TAG}" ]]; then
  echo "--retag-from-source requires --source-tag" >&2
  exit 2
fi

build_target_images
dedupe_images

echo "[publish-images] registry: ${REGISTRY}"
echo "[publish-images] tag: ${TAG}"
echo "[publish-images] source tag: ${SOURCE_TAG:-<none>}"
echo "[publish-images] retag from source: ${RETAG_FROM_SOURCE}"
echo "[publish-images] verify pull: ${VERIFY_PULL}"
echo "[publish-images] target images:"
printf '  - %s\n' "${TARGET_IMAGES[@]}"

if [[ "${DRY_RUN}" -eq 1 ]]; then
  echo "[publish-images] dry run only"
  exit 0
fi

if [[ "${CONFIRM}" -ne 1 && "${HAN_IMAGE_PUBLISH_CONFIRM:-0}" != "1" ]]; then
  echo "[publish-images] refusing to push registry tags without --yes or HAN_IMAGE_PUBLISH_CONFIRM=1" >&2
  exit 3
fi

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/han-image-publish.XXXXXX")"
cleanup() {
  rm -rf "${tmp_dir}"
}
trap cleanup EXIT

for image in "${TARGET_IMAGES[@]}"; do
  safe_name="${image//[^A-Za-z0-9_.-]/_}"
  before_manifest="${tmp_dir}/${safe_name}.before.json"
  after_manifest="${tmp_dir}/${safe_name}.after.json"

  ensure_target_image "${image}"

  echo "[publish-images] inspecting local image: ${image}"
  local_id="$(require_local_image "${image}")"
  echo "[publish-images] local image id: ${local_id}"

  if before_digest="$(remote_digest "${image}" "${before_manifest}")"; then
    echo "[publish-images] remote before: ${before_digest}"
  else
    echo "[publish-images] remote before: missing"
  fi

  echo "[publish-images] pushing: ${image}"
  docker push "${image}"

  after_digest="$(remote_digest "${image}" "${after_manifest}")"
  echo "[publish-images] remote after: ${after_digest}"
  if [[ "${after_digest}" == sha256:* ]]; then
    repo="$(image_repo "${image}")"
    env_var="$(env_var_for_image "${image}")"
    echo "[publish-images] digest pin: ${env_var}=${repo}@${after_digest}"
  fi

  if [[ "${VERIFY_PULL}" -eq 1 ]]; then
    echo "[publish-images] pulling for verification: ${image}"
    docker pull "${image}"
  fi
done

echo "[publish-images] publish and registry verification passed"
