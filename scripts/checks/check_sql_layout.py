from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SQL = ROOT / "sql"

EXPECTED = {
    "small": ["system", "job"],
    "medium": ["system", "job", "tenant", "workflow", "open", "file"],
    "full": ["system", "job", "tenant", "workflow", "open", "file", "ai", "gen"],
}

ALLOWED_SQL_TOP_LEVEL = {
    "README.md",
    "archive",
    "shared",
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

    for tier, modules in EXPECTED.items():
        base = SQL / "tiers" / tier
        if not (base / "manifest.md").exists():
            violations.append(f"缺少 manifest: sql/tiers/{tier}/manifest.md")
        if not (base / "nacos" / "derby-import.sql").exists():
            violations.append(f"缺少 Nacos 导入脚本: sql/tiers/{tier}/nacos/derby-import.sql")
        for module in modules:
            module_dir = base / "postgres" / module
            if not (module_dir / "00-schema.sql").exists():
                violations.append(f"缺少 schema: {module_dir / '00-schema.sql'}")
            if not (module_dir / "10-seed.sql").exists():
                violations.append(f"缺少 seed: {module_dir / '10-seed.sql'}")
        for sql_file in base.joinpath("postgres").rglob("*.sql"):
            text = sql_file.read_text(encoding="utf-8")
            for token in FORBIDDEN_SQL_TOKENS:
                if token in text:
                    violations.append(f"Tier PostgreSQL SQL 不能包含 MySQL 语法 {token!r}: {sql_file}")
            if "/postgres/system/" in sql_file.as_posix():
                lowered = text.lower()
                for token in FORBIDDEN_SYSTEM_TOKENS:
                    if token in lowered:
                        violations.append(f"system SQL 必须使用 del_flag，不能再写回 deleted: {sql_file}")
                        break
    if violations:
        print("\n".join(violations))
        return 1
    print("sql layout ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
