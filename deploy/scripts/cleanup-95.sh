#!/usr/bin/env bash
set -euo pipefail

TARGET_ROOT="${HAN_95_ROOT:-/opt/han}"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="${TARGET_ROOT}/backups/pre-rebuild/${STAMP}"
ARCHIVE_DIR="${TARGET_ROOT}/archive"
ENV_NAME="${HAN_95_ENV_NAME:-95}"
BACKUP_HELPER_IMAGE="${HAN_95_BACKUP_HELPER_IMAGE:-registry.cn-hangzhou.aliyuncs.com/xzy0112/postgres:18.1}"
CONFIRM=0
DRY_RUN=0
PURGE_VOLUMES=0
PURGE_ACK=0

declare -a TIERS=("small" "medium" "full")
declare -a TIER_VOLUME_SUFFIXES=("postgres_data" "redis_data" "nacos_data" "rustfs_data" "rabbitmq_data")
declare -a EXTRA_VOLUMES=()
declare -a PRESERVE_IMAGE_REPOS=("preserved-han-gen")

usage() {
  cat <<'USAGE'
Usage:
  cleanup-95.sh [--yes] [--dry-run] [--purge-volumes --i-know-what-i-am-doing] [--volume <name>]

Rebuilds the 95 host layout: removes Han containers and networks, prunes Han
images, and archives the previous source/deploy/repo directories.

Data volumes are NOT touched unless --purge-volumes is passed together with
--i-know-what-i-am-doing. When volumes are purged the script first writes a
full backup (per-volume tar plus a pg_dumpall of every running tier database)
into backups/pre-rebuild/<stamp>; if any backup step fails the run aborts and
nothing is deleted.

Options:
  --yes                     Confirm the destructive run (or HAN_95_CLEANUP_CONFIRM=1).
  --dry-run                 Print the plan without changing anything.
  --purge-volumes           Also delete the tier data volumes (needs the ack flag).
  --i-know-what-i-am-doing  Acknowledge that purging volumes destroys tier data.
  --volume <name>           Add one extra volume to the purge whitelist. Repeatable.
  -h, --help                Show this help.

Safety:
  Volume selection uses an explicit whitelist (<tier>_<volume> for small/medium/full
  plus any --volume entries). Any other han* volume found on the host is reported
  and left untouched. Image pruning matches Han repositories only and never removes
  the preserved-han-gen rescue tag. Interactive runs must additionally type the
  environment name to proceed.
USAGE
}

log() {
  echo "[cleanup-95] $*"
}

run() {
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    log "dry-run: $*"
    return 0
  fi
  "$@"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --yes)
      CONFIRM=1
      shift
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --purge-volumes)
      PURGE_VOLUMES=1
      shift
      ;;
    --i-know-what-i-am-doing)
      PURGE_ACK=1
      shift
      ;;
    --volume)
      [[ $# -ge 2 ]] || { echo "--volume requires a value" >&2; exit 2; }
      EXTRA_VOLUMES+=("$2")
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

if [[ "${PURGE_VOLUMES}" -eq 1 && "${PURGE_ACK}" -ne 1 ]]; then
  echo "[cleanup-95] --purge-volumes requires --i-know-what-i-am-doing" >&2
  exit 2
fi

whitelisted_volumes() {
  local tier suffix volume
  for tier in "${TIERS[@]}"; do
    for suffix in "${TIER_VOLUME_SUFFIXES[@]}"; do
      echo "${tier}_${suffix}"
    done
  done
  for volume in "${EXTRA_VOLUMES[@]}"; do
    echo "${volume}"
  done
}

existing_volumes() {
  local candidate
  while read -r candidate; do
    [[ -z "${candidate}" ]] && continue
    if docker volume inspect "${candidate}" >/dev/null 2>&1; then
      echo "${candidate}"
    fi
  done < <(whitelisted_volumes | sort -u)
}

unlisted_han_volumes() {
  local listed
  listed="$(whitelisted_volumes | sort -u)"
  docker volume ls --format '{{.Name}}' \
    | grep -E '^(han|hansmall|hanmedium|hanfull)' \
    | grep -vxF "${listed}" || true
}

han_image_ids() {
  # Match bare han-xxx repositories and namespaced <registry>/<ns>/han-xxx ones.
  # Image ids that also carry a preserved rescue tag are skipped, because
  # `docker rmi -f <id>` would drop every tag pointing at that same id.
  local preserve_pattern
  preserve_pattern="$(printf '%s|' "${PRESERVE_IMAGE_REPOS[@]}")"
  preserve_pattern="^(${preserve_pattern%|})$"

  docker images --format '{{.Repository}} {{.ID}}' \
    | awk -v preserve="${preserve_pattern}" '
        { repo[NR] = $1; id[NR] = $2 }
        $1 ~ preserve { preserved[$2] = 1 }
        END {
          for (i = 1; i <= NR; i++) {
            if (repo[i] !~ /^han-/ && index(repo[i], "/han-") == 0) continue
            if (id[i] in preserved) continue
            if (id[i] in seen) continue
            seen[id[i]] = 1
            print id[i]
          }
        }
      '
}

backup_env_files() {
  local env_file
  for env_file in \
    "${TARGET_ROOT}/docker/.env" \
    "${TARGET_ROOT}/deploy/small/.env" \
    "${TARGET_ROOT}/deploy/medium/.env" \
    "${TARGET_ROOT}/deploy/full/.env"; do
    if [[ -f "${env_file}" ]]; then
      run cp "${env_file}" \
        "${BACKUP_DIR}/$(basename "$(dirname "${env_file}")")-$(basename "${env_file}").backup"
    fi
  done
}

backup_tier_databases() {
  local tier deploy_dir dump_file
  for tier in "${TIERS[@]}"; do
    deploy_dir="${TARGET_ROOT}/deploy/${tier}"
    [[ -d "${deploy_dir}" ]] || continue
    if ! (cd "${deploy_dir}" && docker compose ps -q postgres 2>/dev/null | grep -q .); then
      log "no running postgres for ${tier}; skipping logical dump"
      continue
    fi

    dump_file="${BACKUP_DIR}/${tier}-pg_dumpall.sql"
    log "dumping ${tier} database to ${dump_file}"
    if [[ "${DRY_RUN}" -eq 1 ]]; then
      continue
    fi
    if ! (cd "${deploy_dir}" && docker compose exec -T postgres pg_dumpall -U han) >"${dump_file}"; then
      echo "[cleanup-95] pg_dumpall failed for tier ${tier}; aborting before any deletion" >&2
      exit 4
    fi
    if [[ ! -s "${dump_file}" ]]; then
      echo "[cleanup-95] pg_dumpall produced an empty file for tier ${tier}; aborting" >&2
      exit 4
    fi
  done
}

backup_volumes() {
  local volume archive
  for volume in "$@"; do
    archive="${BACKUP_DIR}/volume-${volume}.tar.gz"
    log "archiving volume ${volume} to ${archive}"
    if [[ "${DRY_RUN}" -eq 1 ]]; then
      continue
    fi
    if ! docker run --rm \
      -v "${volume}:/han-src:ro" \
      -v "${BACKUP_DIR}:/han-dst" \
      "${BACKUP_HELPER_IMAGE}" \
      tar czf "/han-dst/volume-${volume}.tar.gz" -C /han-src .; then
      echo "[cleanup-95] volume backup failed for ${volume}; aborting before any deletion" >&2
      exit 4
    fi
    if [[ ! -s "${archive}" ]]; then
      echo "[cleanup-95] volume backup is empty for ${volume}; aborting" >&2
      exit 4
    fi
  done
}

archive_path() {
  local path="$1"
  local name="$2"
  if [[ -e "${path}" ]]; then
    run mv "${path}" "${ARCHIVE_DIR}/${name}-${STAMP}"
  fi
}

declare -a PURGE_TARGETS=()
declare -a SKIPPED_VOLUMES=()
if [[ "${PURGE_VOLUMES}" -eq 1 ]]; then
  while read -r line; do
    [[ -n "${line}" ]] && PURGE_TARGETS+=("${line}")
  done < <(existing_volumes)
  while read -r line; do
    [[ -n "${line}" ]] && SKIPPED_VOLUMES+=("${line}")
  done < <(unlisted_han_volumes)
fi

log "target root: ${TARGET_ROOT}"
log "backup dir: ${BACKUP_DIR}"
log "archive dir: ${ARCHIVE_DIR}"
log "purge volumes: ${PURGE_VOLUMES}"
if [[ "${#PURGE_TARGETS[@]}" -gt 0 ]]; then
  log "volumes queued for deletion:"
  printf '  - %s\n' "${PURGE_TARGETS[@]}"
fi
if [[ "${#SKIPPED_VOLUMES[@]}" -gt 0 ]]; then
  log "han* volumes NOT in the whitelist (left untouched, remove manually if needed):"
  printf '  - %s\n' "${SKIPPED_VOLUMES[@]}"
fi

if [[ "${DRY_RUN}" -eq 1 ]]; then
  log "dry run only; no container, network, image, volume or directory is changed"
  exit 0
fi

if [[ "${CONFIRM}" -ne 1 && "${HAN_95_CLEANUP_CONFIRM:-0}" != "1" ]]; then
  echo "[cleanup-95] refusing to run without --yes or HAN_95_CLEANUP_CONFIRM=1 (use --dry-run to preview)" >&2
  exit 3
fi

if [[ -t 0 ]]; then
  read -r -p "[cleanup-95] type the environment name to continue (${ENV_NAME}): " typed_env
  if [[ "${typed_env}" != "${ENV_NAME}" ]]; then
    echo "[cleanup-95] environment name mismatch; aborting" >&2
    exit 3
  fi
fi

mkdir -p "${BACKUP_DIR}" "${ARCHIVE_DIR}"

backup_env_files

if [[ "${PURGE_VOLUMES}" -eq 1 ]]; then
  backup_tier_databases
  if [[ "${#PURGE_TARGETS[@]}" -gt 0 ]]; then
    backup_volumes "${PURGE_TARGETS[@]}"
  fi
fi

if docker image inspect han-gen:latest >/dev/null 2>&1; then
  docker tag han-gen:latest preserved-han-gen:latest
  docker save -o "${BACKUP_DIR}/preserved-han-gen.tar" preserved-han-gen:latest
  log "preserved han-gen image tagged and exported to ${BACKUP_DIR}/preserved-han-gen.tar"
fi

docker ps -a --format '{{.Names}}' \
  | awk '/^(han|hansmall|hanmedium|hanfull)/ { print }' \
  | xargs -r docker rm -f

docker network ls --format '{{.Name}}' \
  | awk '/^(han|hansmall|hanmedium|hanfull)/ { print }' \
  | xargs -r docker network rm || true

if [[ "${PURGE_VOLUMES}" -eq 1 && "${#PURGE_TARGETS[@]}" -gt 0 ]]; then
  printf '%s\n' "${PURGE_TARGETS[@]}" | xargs -r docker volume rm
fi

han_image_ids | xargs -r docker rmi -f || true

archive_path "${TARGET_ROOT}/source" "source"
archive_path "${TARGET_ROOT}/docker" "docker"
archive_path "${TARGET_ROOT}/repo" "repo"
archive_path "${TARGET_ROOT}/deploy" "deploy"

if [[ -f "${TARGET_ROOT}/phase6_notice_center.sql.bak" ]]; then
  mv "${TARGET_ROOT}/phase6_notice_center.sql.bak" "${ARCHIVE_DIR}/phase6_notice_center.sql.bak-${STAMP}"
fi

mkdir -p \
  "${TARGET_ROOT}/repo" \
  "${TARGET_ROOT}/deploy/small" \
  "${TARGET_ROOT}/deploy/medium" \
  "${TARGET_ROOT}/deploy/full" \
  "${TARGET_ROOT}/logs"

log "backup: ${BACKUP_DIR}"
log "archive: ${ARCHIVE_DIR}"
