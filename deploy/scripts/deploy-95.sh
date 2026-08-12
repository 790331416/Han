#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET_ROOT="${HAN_95_ROOT:-/opt/han}"
REPO_DIR="${TARGET_ROOT}/repo/Han"
if [[ -n "${HAN_REPO_REMOTE:-}" ]]; then
  REPO_REMOTE="${HAN_REPO_REMOTE}"
else
  REPO_REMOTE="$(cd "${ROOT_DIR}" && git remote get-url origin 2>/dev/null || true)"
fi

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

  # 历史兜底：han-gen 的 compose 默认值以前不是仓库地址，full 档只能靠
  # cleanup-95.sh 保下来的本地 preserved-han-gen:latest 苟住。默认值已经修成
  # 完整 ACR 地址，等确认 registry 上的 han-gen 稳定可拉之后，把
  # HAN_95_PREFER_PRESERVED_GEN 设成 0 即可关掉这段特判。
  if [[ "${tier}" == "full" && "${HAN_95_PREFER_PRESERVED_GEN:-1}" == "1" ]] \
    && docker image inspect preserved-han-gen:latest >/dev/null 2>&1; then
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

# compose 里的 ${VAR:?...} 是「缺失即启动失败」的必填注入项。直接让 compose 报错
# 只会给出一行看不出上下文的提示，这里提前把缺什么列清楚。
require_env_vars() {
  local tier="$1"
  local env_file="${TARGET_ROOT}/deploy/${tier}/.env"
  local compose_file="${TARGET_ROOT}/deploy/${tier}/docker-compose.yml"
  local name line value
  declare -a missing=()
  declare -a placeholder=()

  [[ -f "${compose_file}" && -f "${env_file}" ]] || return 0

  while read -r name; do
    [[ -n "${name}" ]] || continue
    line="$(grep -E "^${name}=" "${env_file}" | tail -n 1 || true)"
    value="${line#*=}"
    if [[ -z "${line}" || -z "${value}" ]]; then
      missing+=("${name}")
    elif [[ "${value}" == "change-me" ]]; then
      placeholder+=("${name}")
    fi
  done < <(grep -oE '\$\{[A-Z_]+:\?' "${compose_file}" | sed 's/^\${//; s/:?$//' | sort -u)

  if [[ "${#placeholder[@]}" -gt 0 ]]; then
    echo "[deploy-95] WARNING: ${env_file} still holds the change-me placeholder for:"
    printf '  - %s\n' "${placeholder[@]}"
  fi

  if [[ "${#missing[@]}" -gt 0 ]]; then
    echo "[deploy-95] ${env_file} is missing required variables:" >&2
    printf '  - %s\n' "${missing[@]}" >&2
    echo "[deploy-95] see deploy/${tier}/.env.example; the tier cannot start without them" >&2
    return 1
  fi

  # Docker 对不存在的 bind mount 源会静默创建空目录，Postgres 于是初始化成一个
  # 没有任何业务表的空库，且容器日志显示 database system is ready。宁可在这里断。
  local sql_root init_sql
  sql_root="$(grep -E '^HAN_SQL_ROOT=' "${env_file}" | tail -n 1 || true)"
  sql_root="${sql_root#*=}"
  init_sql="${sql_root}/tiers/${tier}/${tier}-init.sql"
  if [[ -n "${sql_root}" && ! -f "${init_sql}" ]]; then
    echo "[deploy-95] tier init SQL not found: ${init_sql}" >&2
    echo "[deploy-95] HAN_SQL_ROOT in ${env_file} points at a path without the tier init SQL;" >&2
    echo "[deploy-95] starting now would initialise an empty database" >&2
    return 1
  fi
}

sync_tier_dir() {
  local tier="$1"
  rsync -a --delete \
    --exclude '.env' \
    --exclude '.env.local' \
    "${REPO_DIR}/deploy/${tier}/" \
    "${TARGET_ROOT}/deploy/${tier}/"
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
  (
    cd "${REPO_DIR}"
    git fetch origin
    git checkout master
    git pull --ff-only origin master
  )
else
  if [[ -z "${REPO_REMOTE}" ]]; then
    echo "[deploy-95] HAN_REPO_REMOTE is required when repo is absent" >&2
    exit 1
  fi
  git clone --branch master "${REPO_REMOTE}" "${REPO_DIR}"
fi

sync_tier_dir small
sync_tier_dir medium
sync_tier_dir full

ensure_env_file small
ensure_env_file medium
ensure_env_file full

require_env_vars small
require_env_vars medium
require_env_vars full

echo "[deploy-95] deploy skeleton synced"
