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


def _split_sql_values(segment: str) -> list:
    """按逗号分列，忽略单引号内的逗号（ancestors 形如 '0,1'）。"""
    out, buf, in_quote = [], [], False
    for ch in segment:
        if ch == "'":
            in_quote = not in_quote
            buf.append(ch)
        elif ch == "," and not in_quote:
            out.append("".join(buf).strip())
            buf = []
        else:
            buf.append(ch)
    out.append("".join(buf).strip())
    return out


def collect_seed_menus(path: Path) -> dict:
    """取出 init 脚本里静态播种的 sys_menu 行：{菜单ID: (菜单名, 权限串)}。

    DO $$ 块里用变量做 ID 的动态插入不参与静态比对。
    """
    menus: dict = {}
    for stmt_text in split_sql_statements(strip_sql_comments(path.read_text(encoding="utf-8", errors="replace"))):
        stmt = re.match(r"(?is)\s*INSERT\s+INTO\s+sys_menu\s*\(([^)]*)\)\s*VALUES(.*)", stmt_text)
        if not stmt:
            continue
        cols = [c.strip().strip('`"') for c in stmt.group(1).split(",")]
        if not {"id", "menu_name", "perms"} <= set(cols):
            continue
        i_id, i_name, i_perms = cols.index("id"), cols.index("menu_name"), cols.index("perms")
        for row in re.finditer(r"\(((?:[^()']|'[^']*')*)\)", stmt.group(2)):
            vals = _split_sql_values(row.group(1))
            if len(vals) != len(cols) or not re.fullmatch(r"\d+", vals[i_id]):
                continue
            perms = vals[i_perms].strip("'")
            menus[int(vals[i_id])] = (
                vals[i_name].strip("'"),
                "" if perms.upper() == "NULL" else perms,
            )
    return menus


def check_tier_menu_division(violations: list) -> None:
    """档位菜单划分必须自洽：同档两种数据库完全一致，且 small ⊆ medium ⊆ full。

    档位之间菜单本来就该不同（small 只播系统/监控/任务调度，medium 追加租户等，
    AI 与代码生成只进 full），但必须是「逐级追加」而不是各写各的：
    一旦某档漏播或多播，登录后看到的菜单就和该档实际部署的模块对不上。
    同一档位的 PostgreSQL 与 MySQL 更必须逐条相同，否则换数据库就换了一套菜单。
    """
    seeds = {}
    for tier in ("small", "medium", "full"):
        for label, name in (("PG", f"{tier}-init.sql"), ("MySQL", f"{tier}-init-mysql.sql")):
            path = SQL / "tiers" / tier / name
            seeds[(tier, label)] = collect_seed_menus(path) if path.exists() else None

    for tier in ("small", "medium", "full"):
        pg, my = seeds[(tier, "PG")], seeds[(tier, "MySQL")]
        if pg is None or my is None:
            continue
        if pg != my:
            only_pg = sorted(set(pg) - set(my))
            only_my = sorted(set(my) - set(pg))
            differ = sorted(i for i in set(pg) & set(my) if pg[i] != my[i])
            violations.append(
                f"{tier} 档 PostgreSQL 与 MySQL 播种菜单不一致："
                f"PG 独有={only_pg} MySQL 独有={only_my} 同 ID 内容不同={differ}"
            )

    for lower, upper in (("small", "medium"), ("medium", "full")):
        low, up = seeds[(lower, "PG")], seeds[(upper, "PG")]
        if low is None or up is None:
            continue
        missing = sorted(set(low) - set(up))
        if missing:
            violations.append(f"档位菜单划分不是逐级追加：{lower} 有而 {upper} 没有的菜单 ID={missing}")


def _seed_tables_with_tenant(path: Path) -> tuple:
    """返回 (有种子的表集合, 种子里写入了非 NULL tenant_id 的表集合)。

    PostgreSQL 侧常用 `SELECT v.id, 1, ... FROM (VALUES ...)` 投影出 tenant_id，
    MySQL 侧是普通 `INSERT ... VALUES`，两种写法都要能识别到。
    只统计非 NULL 取值：PostgreSQL 显式写 `tenant_id = NULL` 与 MySQL 省略该列
    落库结果相同，不构成差异。
    """
    seeded, with_tenant = set(), set()
    for stmt_text in split_sql_statements(strip_sql_comments(path.read_text(encoding="utf-8", errors="replace"))):
        m = re.match(r"(?is)\s*INSERT\s+(?:IGNORE\s+)?INTO\s+[`\"]?(\w+)[`\"]?\s*\(([^)]*)\)(.*)", stmt_text)
        if not m:
            continue
        table = m.group(1).lower()
        seeded.add(table)
        cols = [c.strip().strip('`"').lower() for c in m.group(2).split(",")]
        if "tenant_id" not in cols:
            continue
        idx, body = cols.index("tenant_id"), m.group(3)
        values = []
        projection = re.search(r"(?is)SELECT\s+(.*?)\s+FROM\s*\(\s*VALUES", body)
        if projection:
            parts = [p.strip() for p in projection.group(1).split(",")]
            if idx < len(parts):
                values.append(parts[idx])
        else:
            for row in re.finditer(r"\(((?:[^()']|'[^']*')*)\)", body):
                parts = _split_sql_values(row.group(1))
                if len(parts) == len(cols):
                    values.append(parts[idx])
        if any(v.strip().upper() not in ("NULL", "") for v in values):
            with_tenant.add(table)
    return seeded, with_tenant


def split_sql_statements(text: str) -> list:
    """按分号切分 SQL 语句，忽略单引号字符串内部的分号。

    不能直接用正则 `(.*?);` 找语句结尾：种子数据里就有带分号的文本，
    例如黑名单说明「多个匹配项以;分隔」，那样会把语句从中间截断，
    后半截的行全部漏统计，检查看起来通过其实什么都没查到。
    """
    out, buf, in_quote = [], [], False
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        if ch == "'":
            # SQL 里 '' 表示字符串内的单引号
            if in_quote and i + 1 < n and text[i + 1] == "'":
                buf.append("''")
                i += 2
                continue
            in_quote = not in_quote
            buf.append(ch)
        elif ch == ";" and not in_quote:
            out.append("".join(buf))
            buf = []
        else:
            buf.append(ch)
        i += 1
    if "".join(buf).strip():
        out.append("".join(buf))
    return out


def _seed_row_counts(path: Path) -> dict:
    """统计每张表被静态播种的行数。

    PostgreSQL 侧写法是 `INSERT ... SELECT ... FROM (VALUES (..),(..)) AS v(..)`，
    MySQL 侧是 `INSERT ... VALUES (..),(..)`，两种都按值元组个数计。
    末尾的 `AS v(列名...)` 也是一对括号，需要排除，否则 PG 侧会多算一行。
    """
    counts: dict = {}
    for stmt in split_sql_statements(strip_sql_comments(path.read_text(encoding="utf-8", errors="replace"))):
        m = re.match(r"(?is)\s*INSERT\s+(?:IGNORE\s+)?INTO\s+[`\"]?(\w+)[`\"]?\s*\([^)]*\)(.*)", stmt)
        if not m:
            continue
        table, body = m.group(1).lower(), m.group(2)
        body = re.sub(r"(?is)\)\s*AS\s+\w+\s*\([^)]*\)\s*$", ")", body.rstrip())
        rows = len(re.findall(r"\((?:[^()']|'[^']*')*\)", body))
        if rows:
            counts[table] = counts.get(table, 0) + rows
    return counts


def check_seed_row_count_parity(violations: list) -> None:
    """同一档位、同一张表，两种数据库播种的行数必须相等。

    漏播单行不会让导入报错，也不会被「表数/菜单数」这类粗粒度对比发现：
    small 的 MySQL 版曾漏掉 sys_config 里 `sys.login.wechatEnabled` 一行，
    结果是换到 MySQL 后微信扫码登录开关整个不存在。
    """
    for tier in ("small", "medium", "full"):
        pg_path = SQL / "tiers" / tier / f"{tier}-init.sql"
        my_path = SQL / "tiers" / tier / f"{tier}-init-mysql.sql"
        if not (pg_path.exists() and my_path.exists()):
            continue
        pg_counts, my_counts = _seed_row_counts(pg_path), _seed_row_counts(my_path)
        for table in sorted(set(pg_counts) & set(my_counts)):
            # sys_menu 由 check_tier_menu_division 逐条比对 ID/名称/权限串，比行数严格；
            # 且 PostgreSQL 侧有 DO $$ 块用变量动态插入，按行数比会误判，这里跳过。
            if table == "sys_menu":
                continue
            if pg_counts[table] != my_counts[table]:
                violations.append(
                    f"{tier} 档 {table} 播种行数两库不一致："
                    f"PostgreSQL {pg_counts[table]} 行 / MySQL {my_counts[table]} 行"
                )


def check_button_perms_have_endpoint(violations: list) -> None:
    """按钮型菜单（menu_type='F'）的权限串必须有后端接口声明它。

    按钮权限的唯一作用就是给某个接口做鉴权门控，后端没有任何 @PreAuthorize
    引用它，说明这个按钮点下去没有对应接口，属于纯死权限点。
    页面型菜单（'C'）不在此列：页面可见性权限可以只被前端路由使用，
    不要求后端一定有同名注解。

    仓库里并存两种权限注解写法，都要认：
    Spring Security 的 {@code @PreAuthorize("@ss.hasAuthority('x')")}（多数模块），
    以及自研的 {@code @RequiresPermission("x")}（han-tenant 等模块）。
    """
    declared = set()
    for java in ROOT.rglob("*.java"):
        if "target" in java.parts:
            continue
        try:
            text = java.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        declared.update(re.findall(r"hasAuthority\('([^']+)'\)", text))
        declared.update(re.findall(r'RequiresPermission\(\s*"([^"]+)"', text))

    for tier in ("small", "medium", "full"):
        path = SQL / "tiers" / tier / f"{tier}-init.sql"
        if not path.exists():
            continue
        for stmt_text in split_sql_statements(strip_sql_comments(path.read_text(encoding="utf-8", errors="replace"))):
            stmt = re.match(r"(?is)\s*INSERT\s+INTO\s+sys_menu\s*\(([^)]*)\)\s*VALUES(.*)", stmt_text)
            if not stmt:
                continue
            cols = [c.strip() for c in stmt.group(1).split(",")]
            if not {"menu_type", "perms"} <= set(cols):
                continue
            i_type, i_perms = cols.index("menu_type"), cols.index("perms")
            for row in re.finditer(r"\(((?:[^()']|'[^']*')*)\)", stmt.group(2)):
                vals = _split_sql_values(row.group(1))
                if len(vals) != len(cols) or vals[i_type].strip("'") != "F":
                    continue
                perm = vals[i_perms].strip("'")
                # DO $$ 块里用变量拼出来的权限串（v_action.perms 之类）不是字面量，跳过
                if not re.fullmatch(r"[a-z][a-zA-Z]*(?::[a-zA-Z]+)+", perm):
                    continue
                if perm not in declared:
                    violations.append(
                        f"{path}: 按钮权限 {perm!r} 在后端没有任何 @PreAuthorize 声明，"
                        f"该按钮没有对应接口"
                    )


def check_seed_tenant_parity(violations: list) -> None:
    """PostgreSQL 种子写了 tenant_id 的表，MySQL 种子也必须写。

    行数相同不代表内容相同：sys_config 两边都是 6 行，但 MySQL 侧漏了 tenant_id 列、
    落库为 NULL，而列表接口按租户过滤，结果是换到 MySQL 后参数设置、字典管理整页为空。
    这种缺陷只有跑起服务查列表才看得见，这里用静态检查提前拦住。
    """
    for tier in ("small", "medium", "full"):
        pg_path = SQL / "tiers" / tier / f"{tier}-init.sql"
        my_path = SQL / "tiers" / tier / f"{tier}-init-mysql.sql"
        if not (pg_path.exists() and my_path.exists()):
            continue
        pg_seeded, pg_tenant = _seed_tables_with_tenant(pg_path)
        my_seeded, my_tenant = _seed_tables_with_tenant(my_path)
        for table in sorted(pg_tenant & my_seeded):
            if table not in my_tenant:
                violations.append(
                    f"{my_path}: {table} 的种子缺少 tenant_id 列，"
                    f"PostgreSQL 版写了而 MySQL 版没写，落库为 NULL 会被租户过滤掉"
                )


def check_no_duplicate_create_table(violations: list, path: Path) -> None:
    """同一份初始化脚本里同一张表只能建一次。

    合并或 cherry-pick 时，两处位置各自新增同名建表语句不会产生文本冲突，
    但导入时第二次 CREATE TABLE 直接报 "already exists" 并中断整个初始化。
    """
    statements = strip_sql_comments(path.read_text(encoding="utf-8", errors="replace"))
    seen: dict[str, int] = {}
    for m in re.finditer(r"(?is)CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`\"]?(\w+)[`\"]?\s*\(", statements):
        name = m.group(1).lower()
        seen[name] = seen.get(name, 0) + 1
    for name, count in sorted(seen.items()):
        if count > 1:
            violations.append(f"{path}: 表 {name} 被重复建表 {count} 次，导入会在第二次处中断")


def check_insert_columns_exist(violations: list, path: Path) -> None:
    """校验每条 INSERT 的列清单都能在同一文件的 CREATE TABLE 里找到。

    这类错位（建表写 post_sort、种子 INSERT 写 sort）在纯文本比对下完全看不出来，
    只有真正导入数据库才会以 "Unknown column" 报错并中断整个初始化，
    后果是建了一半的库。静态拦住它，避免依赖实库导入才能发现。
    """
    statements = strip_sql_comments(path.read_text(encoding="utf-8", errors="replace"))

    # 建表列：取 CREATE TABLE 到配对右括号之间，每个「行首标识符」视为列名
    tables: dict[str, set[str]] = {}
    for m in re.finditer(r"(?is)CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`\"]?(\w+)[`\"]?\s*\((.*?)\n\s*\)", statements):
        cols = set()
        for line in m.group(2).splitlines():
            cm = re.match(r"\s*[`\"]?([a-z_][a-z0-9_]*)[`\"]?\s+", line, re.I)
            if cm and cm.group(1).lower() not in {
                "primary", "unique", "key", "constraint", "index", "foreign", "check",
            }:
                cols.add(cm.group(1).lower())
        tables[m.group(1).lower()] = cols

    for m in re.finditer(r"(?is)INSERT\s+(?:IGNORE\s+)?INTO\s+[`\"]?(\w+)[`\"]?\s*\(([^)]*)\)", statements):
        table = m.group(1).lower()
        if table not in tables or not tables[table]:
            continue
        for raw in m.group(2).split(","):
            col = raw.strip().strip('`"').lower()
            if col and re.fullmatch(r"[a-z_][a-z0-9_]*", col) and col not in tables[table]:
                violations.append(
                    f"{path}: INSERT INTO {table} 引用了建表语句里不存在的列 {col!r}"
                )


def strip_sql_comments(text: str) -> str:
    """去掉 SQL 注释后再做「禁用语法」判定。

    方言禁用词检查必须只看真实语句：脚本头部本来就要用中文注释说明
    「PostgreSQL 的 BIGSERIAL 对应 MySQL 的 BIGINT AUTO_INCREMENT」这类转换约定，
    连注释一起搜会把这种说明文字判成违规，逼得写脚本的人不敢把约定写清楚。
    """
    without_block = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return "\n".join(re.sub(r"--.*$", "", line) for line in without_block.splitlines())


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
            statements = strip_sql_comments(text)
            check_insert_columns_exist(violations, init_file)
            check_no_duplicate_create_table(violations, init_file)
            for token in FORBIDDEN_SQL_TOKENS:
                if token in statements:
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
                check_insert_columns_exist(violations, mysql_init_file)
                check_no_duplicate_create_table(violations, mysql_init_file)
                mysql_upper = strip_sql_comments(mysql_text).upper()
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

    check_tier_menu_division(violations)
    check_seed_tenant_parity(violations)
    check_button_perms_have_endpoint(violations)
    check_seed_row_count_parity(violations)

    if violations:
        print("\n".join(violations))
        return 1

    print("sql layout ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
