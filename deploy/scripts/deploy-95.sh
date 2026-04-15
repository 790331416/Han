#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET_ROOT="${HAN_95_ROOT:-/opt/han}"
REPO_DIR="${TARGET_ROOT}/repo/Han"

echo "[deploy-95] target root: ${TARGET_ROOT}"
echo "[deploy-95] repo dir: ${REPO_DIR}"

mkdir -p "${TARGET_ROOT}/repo" "${TARGET_ROOT}/deploy/small" "${TARGET_ROOT}/deploy/medium" "${TARGET_ROOT}/deploy/full" "${TARGET_ROOT}/logs"

if [[ ! -d "${REPO_DIR}/.git" ]]; then
  git clone . "${REPO_DIR}"
fi

git -C "${REPO_DIR}" fetch origin
git -C "${REPO_DIR}" checkout master
git -C "${REPO_DIR}" pull --ff-only origin master

rsync -a --delete "${ROOT_DIR}/deploy/small/" "${TARGET_ROOT}/deploy/small/"
rsync -a --delete "${ROOT_DIR}/deploy/medium/" "${TARGET_ROOT}/deploy/medium/"
rsync -a --delete "${ROOT_DIR}/deploy/full/" "${TARGET_ROOT}/deploy/full/"

echo "[deploy-95] deploy skeleton synced"
