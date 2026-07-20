#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ROOT_DIR="${HAN_REPO_ROOT:-${SCRIPT_ROOT}}"
if [[ ! -d "${ROOT_DIR}/sql/upgrades/postgres" && -d "${PWD}/sql/upgrades/postgres" ]]; then
  ROOT_DIR="${PWD}"
fi
if [[ ! -d "${ROOT_DIR}/sql/upgrades/postgres" ]]; then
  echo "[backup-rehearsal] cannot resolve Han repo root; set HAN_REPO_ROOT=/path/to/Han" >&2
  exit 1
fi
POSTGRES_IMAGE="${HAN_POSTGRES_IMAGE:-registry.cn-hangzhou.aliyuncs.com/xzy0112/postgres:18.1}"
CONTAINER_NAME="${HAN_BACKUP_REHEARSAL_CONTAINER:-han-postgres-backup-rehearsal-$(date +%Y%m%d%H%M%S)}"
TMP_DIR="${HAN_BACKUP_REHEARSAL_TMP_DIR:-$(mktemp -d /tmp/han-backup-upgrade-rehearsal.XXXXXX)}"
KEEP_TEMP="${HAN_BACKUP_REHEARSAL_KEEP_TEMP:-0}"
DATABASE_NAME="${HAN_BACKUP_REHEARSAL_DATABASE:-backup_rehearsal}"

UPGRADE_FILES=(
  "sql/upgrades/postgres/20260415_upgrade_phase1_legacy.sql"
  "sql/upgrades/postgres/phase1_tenant.sql"
  "sql/upgrades/postgres/20260415_system_del_flag_alignment.sql"
  "sql/upgrades/postgres/phase3_security.sql"
  "sql/upgrades/postgres/phase4_management.sql"
  "sql/upgrades/postgres/phase5_unique_constraint.sql"
  "sql/upgrades/postgres/phase6_notice_center.sql"
  "sql/upgrades/postgres/phase7_login_log_alignment.sql"
  "sql/upgrades/postgres/phase8_prompt_template_alignment.sql"
  "sql/upgrades/postgres/phase9_base_menu_backfill.sql"
  "sql/upgrades/postgres/phase10_sys_dept_leader_id_compat.sql"
  "sql/upgrades/postgres/20260415_upgrade_phase2_ai_legacy.sql"
  "sql/upgrades/postgres/20260415_ai_agent_backfill.sql"
  "sql/upgrades/postgres/20260415_gen_table_migration.sql"
  "sql/upgrades/postgres/20260415_gen_tenant_alignment.sql"
  "sql/upgrades/postgres/20260415_job_log_tenant_alignment.sql"
  "sql/upgrades/postgres/jobflow_v1_trace_id.sql"
  "sql/upgrades/postgres/20260415_ai_chat_message_tenant_alignment.sql"
  "sql/upgrades/postgres/20260415_ip_location_migration.sql"
  "sql/upgrades/postgres/20260415_password_policy_migration.sql"
  "sql/upgrades/postgres/20260415_social_login_migration.sql"
  "sql/upgrades/postgres/20260415_tenant_del_flag_alignment.sql"
  "sql/upgrades/postgres/20260415_totp_2fa_migration.sql"
  "sql/upgrades/postgres/20260415_system_login_log_message_alignment.sql"
  "sql/upgrades/postgres/20260415_system_post_sort_alignment.sql"
  "sql/upgrades/postgres/20260521_aivideo_mvp0.sql"
  "sql/upgrades/postgres/20260521_aivideo_mvp1_text.sql"
  "sql/upgrades/postgres/20260526_aivideo_mvp2_scene_image.sql"
  "sql/upgrades/postgres/20260526_aivideo_prompt_stream.sql"
  "sql/upgrades/postgres/20260527_aivideo_character_image_workflow.sql"
  "sql/upgrades/postgres/20260527_aivideo_media_preview_access.sql"
  "sql/upgrades/postgres/20260527_aivideo_scene_prompt_and_candidate_fill.sql"
  "sql/upgrades/postgres/20260527_aivideo_shot_video_workflow.sql"
  "sql/upgrades/postgres/20260529_aivideo_shot_video_continuity.sql"
  "sql/upgrades/postgres/20260601_aivideo_shot_action_budget.sql"
  "sql/upgrades/postgres/20260601_aivideo_shot_video_av_character_scene_continuity.sql"
  "sql/upgrades/postgres/20260602_aivideo_character_turnaround_prompt.sql"
  "sql/upgrades/postgres/20260602_aivideo_character_turnaround_prompt_sanitize.sql"
  "sql/upgrades/postgres/20260602_aivideo_video_ready_reference_prompts.sql"
  "sql/upgrades/postgres/20260603_aivideo_shot_spatial_continuity.sql"
  "sql/upgrades/postgres/20260605_aivideo_shot_transition_plan.sql"
  "sql/upgrades/postgres/20260607_aivideo_audio_track_prompt.sql"
  "sql/upgrades/postgres/20260609_aivideo_prompt_template_alignment.sql"
  "sql/upgrades/postgres/20260610_aivideo_model_config_alignment.sql"
  "sql/upgrades/postgres/20260610_aivideo_shot_sound_cues.sql"
  "sql/upgrades/postgres/20260610_aivideo_sound_design_prompt.sql"
  "sql/upgrades/postgres/20260610_aivideo_tts_voice_assets.sql"
  "sql/upgrades/postgres/20260611_ai_builtin_dict_alignment.sql"
  "sql/upgrades/postgres/20260611_ai_dict_options.sql"
  "sql/upgrades/postgres/20260611_aivideo_prop_assets.sql"
  "sql/upgrades/postgres/20260612_ai_generic_dict_alignment.sql"
  "sql/upgrades/postgres/20260615_aivideo_action_budget_prop_link.sql"
  "sql/upgrades/postgres/20260615_aivideo_tts_prompt_alignment.sql"
  "sql/upgrades/postgres/20260623_aivideo_short_script_shot_split.sql"
  "sql/upgrades/postgres/20260702_ai_prompt_template_audit_columns.sql"
  "sql/upgrades/postgres/20260702_sys_oper_log_module_alignment.sql"
  "sql/upgrades/postgres/20260703_ai_agent_share_key.sql"
  "sql/upgrades/postgres/20260703_ai_chat_multimodal.sql"
  "sql/upgrades/postgres/20260703_ai_flow_meta.sql"
  "sql/upgrades/postgres/20260703_file_manage_menu.sql"
  "sql/upgrades/postgres/20260715_aivideo_admin_menu_alignment.sql"
  "sql/upgrades/postgres/20260715_sys_dict_type_exact_duplicate_alignment.sql"
  "sql/upgrades/postgres/20260720_ai_agent_chat_tuning.sql"
  "sql/upgrades/postgres/20260720_wechat_social_login.sql"
)
BACKUP_INPUTS=()
COMPOSE_TIER=""

usage() {
  cat <<'EOF'
Usage:
  rehearse-postgres-backup-upgrades.sh --from-compose-tier <small|medium|full>
  rehearse-postgres-backup-upgrades.sh --backup <path> [--backup <path> ...]

Options:
  --from-compose-tier  Create a read-only pg_dump from /opt/han/deploy/<tier>/postgres, then rehearse upgrades.
  --backup             Restore an existing backup into a temporary PostgreSQL container before replaying upgrades.
                       Supported files: .sql, .sql.gz, .dump, .backup, .tar.
  --help               Show this help.

Environment:
  HAN_POSTGRES_IMAGE                 PostgreSQL image used for the temporary rehearsal container.
  HAN_BACKUP_REHEARSAL_CONTAINER     Override temporary container name.
  HAN_BACKUP_REHEARSAL_TMP_DIR       Override temporary directory.
  HAN_BACKUP_REHEARSAL_KEEP_TEMP=1   Keep temporary files for inspection.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --from-compose-tier)
      [[ $# -ge 2 ]] || { echo "--from-compose-tier requires a value" >&2; exit 2; }
      COMPOSE_TIER="$2"
      shift 2
      ;;
    --backup)
      [[ $# -ge 2 ]] || { echo "--backup requires a value" >&2; exit 2; }
      BACKUP_INPUTS+=("$2")
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -n "${COMPOSE_TIER}" && ${#BACKUP_INPUTS[@]} -gt 0 ]]; then
  echo "--from-compose-tier and --backup cannot be used together" >&2
  exit 2
fi

if [[ -z "${COMPOSE_TIER}" && ${#BACKUP_INPUTS[@]} -eq 0 ]]; then
  usage >&2
  exit 2
fi

case "${COMPOSE_TIER}" in
  ""|small|medium|full)
    ;;
  *)
    echo "--from-compose-tier must be one of: small, medium, full" >&2
    exit 2
    ;;
esac

cleanup() {
  docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  if [[ "${KEEP_TEMP}" != "1" ]]; then
    rm -rf "${TMP_DIR}"
  else
    echo "[backup-rehearsal] kept temp dir: ${TMP_DIR}"
  fi
}
trap cleanup EXIT

psql_admin() {
  docker exec -i "${CONTAINER_NAME}" psql -v ON_ERROR_STOP=1 -U han -d postgres "$@"
}

psql_db() {
  docker exec -i "${CONTAINER_NAME}" psql -v ON_ERROR_STOP=1 -U han -d "${DATABASE_NAME}" "$@"
}

run_file() {
  local file="$1"
  echo "[backup-rehearsal] ${DATABASE_NAME} <- ${file}"
  psql_db -f "/workspace/${file}" >/dev/null
}

run_upgrades() {
  local file
  for file in "${UPGRADE_FILES[@]}"; do
    run_file "${file}"
  done
}

assert_no_deleted_columns() {
  psql_db -At <<'SQL'
DO $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND column_name = 'deleted';
    IF v_count > 0 THEN
        RAISE EXCEPTION 'deleted column remains in public schema: %', v_count;
    END IF;
END $$;
SQL
}

assert_no_duplicate_dictionary_rows() {
  psql_db -At <<'SQL'
DO $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM (
        SELECT tenant_id, dict_type
        FROM sys_dict_type
        WHERE COALESCE(del_flag, 0) = 0
        GROUP BY tenant_id, dict_type
        HAVING COUNT(*) > 1
    ) duplicates;
    IF v_count > 0 THEN
        RAISE EXCEPTION 'duplicate active dictionary types remain: %', v_count;
    END IF;

    SELECT COUNT(*) INTO v_count
    FROM (
        SELECT tenant_id, dict_type, dict_value
        FROM sys_dict_data
        WHERE COALESCE(del_flag, 0) = 0
        GROUP BY tenant_id, dict_type, dict_value
        HAVING COUNT(*) > 1
    ) duplicates;
    IF v_count > 0 THEN
        RAISE EXCEPTION 'duplicate active dictionary values remain: %', v_count;
    END IF;
END $$;
SQL
}

assert_required_columns() {
  psql_db -At <<'SQL'
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_user' AND column_name = 'pwd_reset_flag'
    ) THEN
        RAISE EXCEPTION 'sys_user.pwd_reset_flag missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_user' AND column_name = 'totp_enabled'
    ) THEN
        RAISE EXCEPTION 'sys_user.totp_enabled missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'ai_agent' AND column_name = 'del_flag'
    ) THEN
        RAISE EXCEPTION 'ai_agent.del_flag missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_menu' AND column_name = 'sort'
    ) THEN
        RAISE EXCEPTION 'sys_menu.sort missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'ai_video_project'
    ) THEN
        RAISE EXCEPTION 'ai_video_project missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'ai_video_project_setting' AND column_name = 'character_image_prompt_template_id'
    ) THEN
        RAISE EXCEPTION 'ai_video_project_setting.character_image_prompt_template_id missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM ai_prompt_template
        WHERE category = 'aivideo_text' AND template_name = 'AI短剧原文润色'
    ) THEN
        RAISE EXCEPTION 'AI short-drama prompt templates missing';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM ai_prompt_template
        WHERE category = 'aivideo_image' AND template_name = 'AI短剧角色图生成'
    ) THEN
        RAISE EXCEPTION 'AI short-drama character image prompt template missing';
    END IF;
END $$;
SQL
}

assert_has_tables() {
  local table_count
  table_count="$(psql_db -At <<'SQL'
SELECT COUNT(*)
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE';
SQL
)"
  if [[ "${table_count}" -le 0 ]]; then
    echo "[backup-rehearsal] restored backup has no public base tables" >&2
    exit 1
  fi
  echo "[backup-rehearsal] restored public table count: ${table_count}"
}

resolve_compose_dir() {
  local tier="$1"
  local official="/opt/han/deploy/${tier}"
  local local_dir="${ROOT_DIR}/deploy/${tier}"
  if [[ -f "${official}/docker-compose.yml" ]]; then
    printf '%s\n' "${official}"
  elif [[ -f "${local_dir}/docker-compose.yml" ]]; then
    printf '%s\n' "${local_dir}"
  else
    echo "Cannot find compose directory for tier: ${tier}" >&2
    exit 1
  fi
}

create_runtime_dump() {
  local tier="$1"
  local compose_dir
  local dump_file
  compose_dir="$(resolve_compose_dir "${tier}")"
  dump_file="${TMP_DIR}/${tier}-runtime.sql"
  echo "[backup-rehearsal] creating read-only pg_dump from ${compose_dir}"
  (
    cd "${compose_dir}"
    docker compose exec -T postgres pg_dump -U han -d han --no-owner --no-privileges
  ) > "${dump_file}"
  if [[ ! -s "${dump_file}" ]]; then
    echo "[backup-rehearsal] runtime dump is empty: ${dump_file}" >&2
    exit 1
  fi
  BACKUP_INPUTS+=("${dump_file}")
}

start_rehearsal_container() {
  echo "[backup-rehearsal] image: ${POSTGRES_IMAGE}"
  echo "[backup-rehearsal] container: ${CONTAINER_NAME}"
  docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  docker run -d \
    --name "${CONTAINER_NAME}" \
    -e POSTGRES_DB=postgres \
    -e POSTGRES_USER=han \
    -e POSTGRES_PASSWORD=han-rehearsal \
    -v "${ROOT_DIR}:/workspace:ro" \
    "${POSTGRES_IMAGE}" >/dev/null

  for _ in $(seq 1 60); do
    if docker exec "${CONTAINER_NAME}" pg_isready -U han >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done

  echo "[backup-rehearsal] PostgreSQL did not become ready" >&2
  exit 1
}

restore_backup() {
  local backup="$1"
  local lower
  local container_path
  if [[ ! -f "${backup}" ]]; then
    echo "[backup-rehearsal] backup not found: ${backup}" >&2
    exit 1
  fi

  lower="${backup,,}"
  echo "[backup-rehearsal] restoring backup: ${backup}"
  case "${lower}" in
    *.sql)
      psql_db < "${backup}" >/dev/null
      ;;
    *.sql.gz)
      gzip -dc "${backup}" | psql_db >/dev/null
      ;;
    *.dump|*.backup)
      docker exec -i "${CONTAINER_NAME}" pg_restore --no-owner --no-privileges -U han -d "${DATABASE_NAME}" < "${backup}" >/dev/null
      ;;
    *.tar)
      container_path="/tmp/$(basename "${backup}")"
      docker cp "${backup}" "${CONTAINER_NAME}:${container_path}" >/dev/null
      docker exec -i "${CONTAINER_NAME}" pg_restore --no-owner --no-privileges -U han -d "${DATABASE_NAME}" "${container_path}" >/dev/null
      ;;
    *)
      echo "[backup-rehearsal] unsupported backup type: ${backup}" >&2
      exit 2
      ;;
  esac
}

mkdir -p "${TMP_DIR}"

if [[ -n "${COMPOSE_TIER}" ]]; then
  create_runtime_dump "${COMPOSE_TIER}"
fi

start_rehearsal_container
psql_admin -c "CREATE DATABASE ${DATABASE_NAME};" >/dev/null

for backup in "${BACKUP_INPUTS[@]}"; do
  restore_backup "${backup}"
done

assert_has_tables
run_upgrades
assert_no_deleted_columns
assert_no_duplicate_dictionary_rows
assert_required_columns

echo "[backup-rehearsal] backup upgrade rehearsal passed"
