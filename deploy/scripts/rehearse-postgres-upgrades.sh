#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POSTGRES_IMAGE="${HAN_POSTGRES_IMAGE:-registry.cn-hangzhou.aliyuncs.com/xzy0112/postgres:18.1}"
CONTAINER_NAME="${HAN_REHEARSAL_CONTAINER:-han-postgres-upgrade-rehearsal-$(date +%Y%m%d%H%M%S)}"

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
  "sql/upgrades/postgres/20260612_ai_generic_dict_alignment.sql"
  "sql/upgrades/postgres/20260702_ai_prompt_template_audit_columns.sql"
  "sql/upgrades/postgres/20260702_sys_oper_log_module_alignment.sql"
  "sql/upgrades/postgres/20260703_ai_agent_share_key.sql"
  "sql/upgrades/postgres/20260703_ai_chat_multimodal.sql"
  "sql/upgrades/postgres/20260703_ai_flow_meta.sql"
  "sql/upgrades/postgres/20260703_file_manage_menu.sql"
  "sql/upgrades/postgres/20260715_sys_dict_type_exact_duplicate_alignment.sql"
)
cleanup() {
  docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

psql_admin() {
  docker exec -i "${CONTAINER_NAME}" psql -v ON_ERROR_STOP=1 -U han -d postgres "$@"
}

psql_db() {
  local db="$1"
  shift
  docker exec -i "${CONTAINER_NAME}" psql -v ON_ERROR_STOP=1 -U han -d "${db}" "$@"
}

run_file() {
  local db="$1"
  local file="$2"
  echo "[upgrade-rehearsal] ${db} <- ${file}"
  psql_db "${db}" -f "/workspace/${file}" >/dev/null
}

run_upgrades() {
  local db="$1"
  for file in "${UPGRADE_FILES[@]}"; do
    run_file "${db}" "${file}"
  done
}

assert_no_deleted_columns() {
  local db="$1"
  psql_db "${db}" -At <<'SQL'
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

assert_required_columns() {
  local db="$1"
  psql_db "${db}" -At <<'SQL'
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
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'ai_video_project'
    ) THEN
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
    ELSE
        RAISE NOTICE 'ai_video_project absent; skipping AIVideo-specific assertions';
    END IF;
END $$;
SQL
}

seed_legacy_schema() {
  local db="$1"
  psql_db "${db}" -At <<'SQL'
CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(200),
    deleted SMALLINT DEFAULT 0,
    CONSTRAINT sys_user_username_key UNIQUE (username)
);
INSERT INTO sys_user (id, username, password, deleted) VALUES (1, 'admin', 'x', 0);

CREATE TABLE sys_dept (id BIGINT PRIMARY KEY, dept_name VARCHAR(100), deleted SMALLINT DEFAULT 0);
CREATE TABLE sys_role (id BIGINT PRIMARY KEY, role_key VARCHAR(50), deleted SMALLINT DEFAULT 0);
CREATE TABLE sys_post (id BIGINT PRIMARY KEY, post_code VARCHAR(50), sort INT DEFAULT 0, deleted SMALLINT DEFAULT 0);
CREATE TABLE sys_dict_type (id BIGINT PRIMARY KEY, dict_type VARCHAR(100), deleted SMALLINT DEFAULT 0);
CREATE TABLE sys_dict_data (id BIGINT PRIMARY KEY, dict_type VARCHAR(100), deleted SMALLINT DEFAULT 0);
CREATE TABLE sys_config (id BIGINT PRIMARY KEY, config_key VARCHAR(100), deleted SMALLINT DEFAULT 0);
CREATE TABLE sys_client (id BIGINT PRIMARY KEY, client_id VARCHAR(100), deleted SMALLINT DEFAULT 0);
CREATE TABLE sys_tenant (id BIGINT PRIMARY KEY, tenant_name VARCHAR(100), deleted SMALLINT DEFAULT 0);
CREATE TABLE sys_tenant_package (id BIGINT PRIMARY KEY, package_name VARCHAR(100), deleted SMALLINT DEFAULT 0);

CREATE TABLE sys_menu (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    menu_name VARCHAR(100),
    parent_id BIGINT,
    ancestors VARCHAR(500),
    sort INT DEFAULT 0,
    path VARCHAR(200),
    component VARCHAR(255),
    query VARCHAR(255),
    menu_type CHAR(1),
    visible SMALLINT DEFAULT 0,
    status SMALLINT DEFAULT 0,
    perms VARCHAR(200),
    icon VARCHAR(100),
    is_frame SMALLINT DEFAULT 1,
    is_cache SMALLINT DEFAULT 0,
    deleted SMALLINT DEFAULT 0
);
CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

CREATE TABLE sys_login_log (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50),
    tenant_id BIGINT,
    ipaddr VARCHAR(128),
    msg VARCHAR(255),
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_job (
    id BIGINT PRIMARY KEY,
    job_name VARCHAR(64),
    job_group VARCHAR(64),
    tenant_id BIGINT
);
CREATE TABLE sys_job_log (
    id BIGINT PRIMARY KEY,
    job_name VARCHAR(64),
    job_group VARCHAR(64)
);

CREATE TABLE ai_conversation (
    conversation_id BIGINT PRIMARY KEY,
    tenant_id BIGINT
);
CREATE TABLE ai_chat_message (
    message_id BIGINT PRIMARY KEY,
    conversation_id BIGINT,
    content TEXT
);
CREATE TABLE ai_agent (
    agent_id BIGSERIAL PRIMARY KEY,
    agent_name VARCHAR(100) NOT NULL,
    deleted INT DEFAULT 0
);

CREATE TABLE ai_prompt_template (
    template_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    template_name VARCHAR(200) NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'system',
    content TEXT NOT NULL,
    variables TEXT DEFAULT '[]',
    description VARCHAR(1000) DEFAULT '',
    built_in INTEGER DEFAULT 0,
    status CHAR(1) DEFAULT '0'
);
SQL
}

echo "[upgrade-rehearsal] image: ${POSTGRES_IMAGE}"
echo "[upgrade-rehearsal] container: ${CONTAINER_NAME}"

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
    break
  fi
  sleep 1
done

psql_admin -c "CREATE DATABASE clean_full;" >/dev/null
run_file clean_full "sql/tiers/full/full-init.sql"
run_upgrades clean_full
assert_no_deleted_columns clean_full
assert_required_columns clean_full

psql_admin -c "CREATE DATABASE legacy_synthetic;" >/dev/null
seed_legacy_schema legacy_synthetic
run_upgrades legacy_synthetic
assert_no_deleted_columns legacy_synthetic
assert_required_columns legacy_synthetic

echo "[upgrade-rehearsal] postgres upgrade rehearsal passed"
