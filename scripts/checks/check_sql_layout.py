from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SQL = ROOT / "sql"

ALLOWED_SQL_TOP_LEVEL = {
    "README.md",
    "archive",
    "sdfz",
    "tiers",
    "upgrades",
}

FORBIDDEN_SQL_TOKENS = [
    " COMMENT '",
    "AUTO_INCREMENT",
    " ON UPDATE CURRENT_TIMESTAMP",
    " AFTER ",
]

FORBIDDEN_POSTGRES_LINE_PREFIXES = [
    "USE ",
]

FORBIDDEN_MYSQL_TOKENS = [
    "BIGSERIAL",
    "COMMENT ON ",
    "ON CONFLICT",
    " CASCADE;",
    "DO $$",
    "::BIGINT",
]

FORBIDDEN_SYSTEM_TOKENS = [
    " deleted ",
    "\tdeleted\t",
    "\ndeleted ",
]

FORBIDDEN_DELETED_COLUMN_RE = re.compile(
    r"(?im)^\s*(?:deleted\s+(?:smallint|int|integer|bigint|boolean|char|varchar|text)\b|add\s+column\s+(?:if\s+not\s+exists\s+)?deleted\b)"
)

REQUIRED_SYS_USER_COLUMNS = [
    "pwd_update_time",
    "pwd_reset_flag",
    "totp_secret",
    "totp_enabled",
]

REQUIRED_IDENTITY_TOKENS = [
    "create table sys_user_social",
    "uq_user_social_tenant_provider_openid",
    "uq_user_social_user_provider",
]

UPGRADE_REHEARSAL_SCRIPTS = [
    ROOT / "deploy" / "scripts" / "rehearse-postgres-upgrades.sh",
    ROOT / "deploy" / "scripts" / "rehearse-postgres-backup-upgrades.sh",
]

UPGRADE_FILE_RE = re.compile(r'"(sql/upgrades/postgres/[^"]+\.sql)"')


def tracked_sql_paths() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "sql"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    paths: list[Path] = []
    for line in result.stdout.splitlines():
        if not line.strip():
            continue
        path = Path(line)
        if (ROOT / path).exists():
            paths.append(path)
    return paths


def read_sql(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def main() -> int:
    violations: list[str] = []

    top_levels = {
        path.parts[1]
        for path in tracked_sql_paths()
        if len(path.parts) > 1 and path.parts[0] == "sql"
    }
    for item in sorted(top_levels):
        if item not in ALLOWED_SQL_TOP_LEVEL:
            violations.append(f"sql 顶层条目未入白名单: {item}")

    for tier in ("small", "medium", "full"):
        tier_dir = SQL / "tiers" / tier
        init_file = tier_dir / f"{tier}-init.sql"
        nacos_file = tier_dir / f"{tier}-nacos-derby-import.sql"
        readme_file = tier_dir / "README.md"
        if not readme_file.exists():
            violations.append(f"缺少 tier 说明: {readme_file}")
        if not init_file.exists():
            violations.append(f"缺少初始化 SQL: {init_file}")
        if not nacos_file.exists():
            violations.append(f"缺少 Nacos 导入 SQL: {nacos_file}")
        if init_file.exists():
            text = read_sql(init_file)
            for token in FORBIDDEN_SQL_TOKENS:
                if token in text:
                    violations.append(f"Tier PostgreSQL SQL 不能包含 MySQL 语法 {token!r}: {init_file}")
            lowered = text.lower()
            for column in REQUIRED_SYS_USER_COLUMNS:
                if column not in lowered:
                    violations.append(f"tier init SQL missing sys_user.{column}: {init_file}")
            for token in REQUIRED_IDENTITY_TOKENS:
                if token not in lowered:
                    violations.append(f"tier init SQL missing identity schema token {token}: {init_file}")
            for token in FORBIDDEN_SYSTEM_TOKENS:
                if token in lowered:
                    violations.append(f"tier init SQL 必须使用 del_flag，不能再写回 deleted: {init_file}")
                    break
        if nacos_file.exists():
            text = read_sql(nacos_file)
            if "INSERT INTO nacos.config_info" not in text:
                violations.append(f"Nacos 导入 SQL 缺少 config_info 导入语句: {nacos_file}")

        if tier == "small":
            mysql_init_file = tier_dir / "small-init-mysql.sql"
            if not mysql_init_file.exists():
                violations.append(f"缺少 MySQL small 初始化 SQL: {mysql_init_file}")
            else:
                mysql_text = read_sql(mysql_init_file)
                mysql_upper = mysql_text.upper()
                for token in FORBIDDEN_MYSQL_TOKENS:
                    if token in mysql_upper:
                        violations.append(f"MySQL small SQL 包含 PostgreSQL 语法 {token!r}: {mysql_init_file}")
                mysql_lower = mysql_text.lower()
                for column in REQUIRED_SYS_USER_COLUMNS:
                    if column not in mysql_lower:
                        violations.append(f"MySQL small init SQL missing sys_user.{column}: {mysql_init_file}")
                for token in REQUIRED_IDENTITY_TOKENS:
                    if token not in mysql_lower:
                        violations.append(f"MySQL small init SQL missing identity schema token {token}: {mysql_init_file}")

    upgrade_files = sorted((SQL / "upgrades" / "postgres").glob("*.sql"))
    tracked_upgrade_paths = {
        upgrade_file.relative_to(ROOT).as_posix()
        for upgrade_file in upgrade_files
    }

    for upgrade_file in upgrade_files:
        text = read_sql(upgrade_file)
        if FORBIDDEN_DELETED_COLUMN_RE.search(text):
            violations.append(f"PostgreSQL upgrade SQL 不能创建 deleted 列，必须使用 del_flag: {upgrade_file}")
        for token in FORBIDDEN_SQL_TOKENS:
            if token in text:
                violations.append(f"PostgreSQL upgrade SQL contains forbidden token {token!r}: {upgrade_file}")
        for lineno, line in enumerate(text.splitlines(), start=1):
            stripped = line.strip().upper()
            for prefix in FORBIDDEN_POSTGRES_LINE_PREFIXES:
                if stripped.startswith(prefix):
                    violations.append(f"PostgreSQL upgrade SQL contains forbidden statement at {upgrade_file}:{lineno}: {line.strip()}")

    # 手工执行 sdfz 脚本时客户端字符集常常缺省为 latin1，中文会被双重编码落库。
    # 95 上已经有一批记录被这么写坏过，所以这里把「文件头声明 utf8mb4」变成硬性检查。
    for sdfz_file in sorted((SQL / "sdfz" / "mysql").glob("*.sql")):
        head = "\n".join(read_sql(sdfz_file).splitlines()[:12]).lower()
        if "set names utf8mb4;" not in head:
            violations.append(f"SDFZ MySQL SQL 必须在文件头声明 SET NAMES utf8mb4;: {sdfz_file}")

    sdfz_mysql = SQL / "sdfz" / "mysql" / "20260811_education_master.sql"
    if sdfz_mysql.exists():
        sdfz_text = read_sql(sdfz_mysql).lower()
        for table in (
            "edu_school",
            "edu_class",
            "edu_person",
            "edu_person_class",
            "edu_subject",
            "edu_person_subject",
            "edu_room",
            "edu_device",
            "edu_semester",
        ):
            if f"create table if not exists {table}" not in sdfz_text:
                violations.append(f"SDFZ MySQL SQL missing table: {table}")
        for forbidden in ("online_status", "heartbeat", "stream_url", "record_url"):
            if forbidden in sdfz_text:
                violations.append(f"SDFZ management schema must not own runtime video/device field: {forbidden}")

    sdfz_order = SQL / "sdfz" / "mysql" / "20260812_course_order.sql"
    if sdfz_order.exists():
        order_text = read_sql(sdfz_order).lower()
        for table in ("edu_course_order", "edu_course_order_subject", "edu_course_order_grant"):
            if f"create table if not exists {table}" not in order_text:
                violations.append(f"SDFZ course order SQL missing table: {table}")
        # 「同一听讲班+主讲班+学期只能有一张有效单」由这条唯一键保证，丢了就退化成应用层抢锁。
        if "uq_edu_course_order_active" not in order_text:
            violations.append("SDFZ course order SQL missing unique key: uq_edu_course_order_active")
        # 学期三态必须独立成列，不能复用 status（0 在 edu_* 里已经是「正常」）。
        if "lifecycle_status" not in order_text:
            violations.append("SDFZ course order SQL missing edu_semester.lifecycle_status")

    for script in UPGRADE_REHEARSAL_SCRIPTS:
        if not script.exists():
            violations.append(f"缺少 PostgreSQL 升级演练脚本: {script}")
            continue
        listed_paths = set(UPGRADE_FILE_RE.findall(script.read_text(encoding="utf-8", errors="replace")))
        for listed_path in sorted(listed_paths):
            if listed_path not in tracked_upgrade_paths:
                violations.append(f"升级演练脚本引用不存在的 SQL: {script}: {listed_path}")
        for missing_path in sorted(tracked_upgrade_paths - listed_paths):
            violations.append(f"升级演练脚本未覆盖 PostgreSQL upgrade SQL: {script}: {missing_path}")

    # AIVideo full-init 主键防回退：11 张 ai_video_* 表的自增主键必须为 BIGSERIAL（对应 PO 的 IdType.AUTO）。
    # 防止再次被转写为 BIGINT NOT NULL（无序列）导致种子 NULL 主键或运行时插入失败。
    full_init_file = SQL / "tiers" / "full" / "full-init.sql"
    if full_init_file.exists():
        full_init_text = read_sql(full_init_file)
        for col in ("project_id", "document_id", "version_id", "character_id", "scene_id",
                    "prop_id", "shot_id", "media_id", "task_id", "review_id", "setting_id"):
            if not re.search(rf"^\s+{col}\s+BIGSERIAL\s+PRIMARY\s+KEY", full_init_text, re.M):
                violations.append(f"full-init.sql 的 ai_video 自增主键必须为 BIGSERIAL（防回退）: {col}")

    if violations:
        print("\n".join(violations))
        return 1

    print("sql layout ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
