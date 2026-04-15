#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET_ROOT="${HAN_95_ROOT:-/opt/han}"
REPO_DIR="${TARGET_ROOT}/repo/Han"
REPO_REMOTE="${HAN_REPO_REMOTE:-$(git -C "${ROOT_DIR}" remote get-url origin 2>/dev/null || true)}"

ensure_env_file() {
  local tier="$1"
  local env_file="${TARGET_ROOT}/deploy/${tier}/.env"
  local example_file="${TARGET_ROOT}/deploy/${tier}/.env.example"
  local sql_root="${REPO_DIR}/sql"
  local tmp_file

  if [[ ! -f "${env_file}" ]]; then
    cp "${example_file}" "${env_file}"
  fi

  tmp_file="$(mktemp)"
  awk -v sql_root="${sql_root}" '
    BEGIN { seen = 0 }
    /^HAN_SQL_ROOT=/ {
      print "HAN_SQL_ROOT=" sql_root
      seen = 1
      next
    }
    { print }
    END {
      if (!seen) {
        print "HAN_SQL_ROOT=" sql_root
      }
    }
  ' "${env_file}" > "${tmp_file}"
  mv "${tmp_file}" "${env_file}"

  if [[ "${tier}" == "full" ]] && docker image inspect preserved-han-gen:latest >/dev/null 2>&1; then
    tmp_file="$(mktemp)"
    awk -v gen_image="preserved-han-gen:latest" '
      BEGIN { seen = 0 }
      /^HAN_GEN_IMAGE=/ {
        print "HAN_GEN_IMAGE=" gen_image
        seen = 1
        next
      }
      { print }
      END {
        if (!seen) {
          print "HAN_GEN_IMAGE=" gen_image
        }
      }
    ' "${env_file}" > "${tmp_file}"
    mv "${tmp_file}" "${env_file}"
  fi
}

echo "[deploy-95] target root: ${TARGET_ROOT}"
echo "[deploy-95] repo dir: ${REPO_DIR}"

mkdir -p \
  "${TARGET_ROOT}/repo" \
  "${TARGET_ROOT}/deploy/small" \
  "${TARGET_ROOT}/deploy/medium" \
  "${TARGET_ROOT}/deploy/full" \
  "${TARGET_ROOT}/logs" \
  "${TARGET_ROOT}/archive" \
  "${TARGET_ROOT}/backups/pre-rebuild"

if [[ -d "${REPO_DIR}/.git" ]]; then
  git -C "${REPO_DIR}" fetch origin
  git -C "${REPO_DIR}" checkout master
  git -C "${REPO_DIR}" pull --ff-only origin master
else
  if [[ -z "${REPO_REMOTE}" ]]; then
    echo "[deploy-95] HAN_REPO_REMOTE is required when repo is absent" >&2
    exit 1
  fi
  git clone --branch master "${REPO_REMOTE}" "${REPO_DIR}"
fi

rsync -a --delete "${REPO_DIR}/deploy/small/" "${TARGET_ROOT}/deploy/small/"
rsync -a --delete "${REPO_DIR}/deploy/medium/" "${TARGET_ROOT}/deploy/medium/"
rsync -a --delete "${REPO_DIR}/deploy/full/" "${TARGET_ROOT}/deploy/full/"

ensure_env_file small
ensure_env_file medium
ensure_env_file full

echo "[deploy-95] deploy skeleton synced"
