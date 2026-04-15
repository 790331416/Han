#!/usr/bin/env bash
set -euo pipefail

TARGET_ROOT="${HAN_95_ROOT:-/opt/han}"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="${TARGET_ROOT}/backups/pre-rebuild/${STAMP}"
ARCHIVE_DIR="${TARGET_ROOT}/archive"

archive_path() {
  local path="$1"
  local name="$2"
  if [[ -e "${path}" ]]; then
    mv "${path}" "${ARCHIVE_DIR}/${name}-${STAMP}"
  fi
}

mkdir -p "${BACKUP_DIR}" "${ARCHIVE_DIR}"

for env_file in \
  "${TARGET_ROOT}/docker/.env" \
  "${TARGET_ROOT}/deploy/small/.env" \
  "${TARGET_ROOT}/deploy/medium/.env" \
  "${TARGET_ROOT}/deploy/full/.env"; do
  if [[ -f "${env_file}" ]]; then
    cp "${env_file}" "${BACKUP_DIR}/$(basename "$(dirname "${env_file}")")-$(basename "${env_file}").backup"
  fi
done

docker ps -a --format '{{.Names}}' \
  | awk '/^(han|hansmall|hanmedium|hanfull)/ { print }' \
  | xargs -r docker rm -f

docker network ls --format '{{.Name}}' \
  | awk '/^(han|hansmall|hanmedium|hanfull)/ { print }' \
  | xargs -r docker network rm || true

docker volume ls --format '{{.Name}}' \
  | awk '/^(han|hansmall|hanmedium|hanfull)/ { print }' \
  | xargs -r docker volume rm || true

docker images --format '{{.Repository}}:{{.Tag}} {{.ID}}' \
  | awk '$1 ~ /(^han-|\\/han-)/ { print $2 }' \
  | sort -u \
  | xargs -r docker rmi -f || true

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

echo "[cleanup-95] backup: ${BACKUP_DIR}"
echo "[cleanup-95] archive: ${ARCHIVE_DIR}"
