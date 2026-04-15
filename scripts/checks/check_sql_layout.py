from __future__ import annotations

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

FORBIDDEN_SYSTEM_TOKENS = [
    " deleted ",
    "\tdeleted\t",
    "\ndeleted ",
]


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
            text = init_file.read_text(encoding="utf-8")
            for token in FORBIDDEN_SQL_TOKENS:
                if token in text:
                    violations.append(f"Tier PostgreSQL SQL 不能包含 MySQL 语法 {token!r}: {init_file}")
            lowered = text.lower()
            for token in FORBIDDEN_SYSTEM_TOKENS:
                if token in lowered:
                    violations.append(f"tier init SQL 必须使用 del_flag，不能再写回 deleted: {init_file}")
                    break
        if nacos_file.exists():
            text = nacos_file.read_text(encoding="utf-8")
            if "INSERT INTO nacos.config_info" not in text:
                violations.append(f"Nacos 导入 SQL 缺少 config_info 导入语句: {nacos_file}")

    if violations:
        print("\n".join(violations))
        return 1

    print("sql layout ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
