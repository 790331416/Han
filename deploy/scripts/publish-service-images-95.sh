#!/usr/bin/env bash
set -euo pipefail

REGISTRY="${HAN_IMAGE_REGISTRY:-registry.cn-hangzhou.aliyuncs.com/xzy0112}"
TAG="${HAN_IMAGE_TAG:-latest}"
VERIFY_PULL=0
CONFIRM=0
DRY_RUN=0
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
  --verify-pull         Run docker pull after push to prove registry pull path.
  --yes                 Confirm latest-tag push.
  --dry-run             Print selected images without pushing.
  -h, --help            Show this help.

Safety:
  This script does not build images, restart containers, delete images,
  delete volumes, or read secrets. It requires local images to already exist.
  Pushing latest tags is a release action, so --yes or
  HAN_IMAGE_PUBLISH_CONFIRM=1 is required unless --dry-run is used.
USAGE
}

add_image_token() {
  local token="$1"
  local item image

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

  TARGET_IMAGES+=("${image}")
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

if [[ "${#TARGET_IMAGES[@]}" -eq 0 ]]; then
  add_image_token "all"
fi
dedupe_images

echo "[publish-images] registry: ${REGISTRY}"
echo "[publish-images] tag: ${TAG}"
echo "[publish-images] verify pull: ${VERIFY_PULL}"
echo "[publish-images] target images:"
printf '  - %s\n' "${TARGET_IMAGES[@]}"

if [[ "${DRY_RUN}" -eq 1 ]]; then
  echo "[publish-images] dry run only"
  exit 0
fi

if [[ "${CONFIRM}" -ne 1 && "${HAN_IMAGE_PUBLISH_CONFIRM:-0}" != "1" ]]; then
  echo "[publish-images] refusing to push latest tags without --yes or HAN_IMAGE_PUBLISH_CONFIRM=1" >&2
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

  if [[ "${VERIFY_PULL}" -eq 1 ]]; then
    echo "[publish-images] pulling for verification: ${image}"
    docker pull "${image}"
  fi
done

echo "[publish-images] publish and registry verification passed"
