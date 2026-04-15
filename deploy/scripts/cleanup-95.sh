#!/usr/bin/env bash
set -euo pipefail

TARGET_ROOT="${HAN_95_ROOT:-/opt/han}"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="${TARGET_ROOT}/backups/pre-rebuild/${STAMP}"

mkdir -p "${BACKUP_DIR}"

if [[ -f "${TARGET_ROOT}/docker/.env" ]]; then
  cp "${TARGET_ROOT}/docker/.env" "${BACKUP_DIR}/docker.env.backup"
fi

docker ps -a --format '{{.Names}}' | grep '^han' | xargs -r docker rm -f
docker network ls --format '{{.Name}}' | grep '^han' | xargs -r docker network rm || true
docker volume ls --format '{{.Name}}' | grep '^han' | xargs -r docker volume rm || true

rm -rf "${TARGET_ROOT}/source"
mkdir -p "${TARGET_ROOT}/archive"

echo "[cleanup-95] backup: ${BACKUP_DIR}"
