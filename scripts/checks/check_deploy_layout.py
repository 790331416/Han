import re
import sys
from pathlib import Path
from typing import List

ROOT = Path(__file__).resolve().parents[2]
DEPLOY = ROOT / "deploy"
SQL = ROOT / "sql"

# 根目录三个 compose 属于过渡入口，仍被 check_repo_layout.py 白名单认可。
# 它们曾经挂载了已归档的 sql/postgres/*.sql，路径不存在时 Docker 会静默创建空目录，
# Postgres 初始化成一个没有任何业务表的空库（S-89）。这里做双向校验防止复发。
LEGACY_ROOT_COMPOSE = {
    "docker-compose-small.yml": "small",
    "docker-compose.yml": "medium",
    "docker-compose-full.yml": "full",
}

BIND_MOUNT_SQL = re.compile(r"-\s+(\./sql/[^:\s]+):")


def check_legacy_root_compose(violations: List[str]) -> None:
    for name, tier in LEGACY_ROOT_COMPOSE.items():
        compose = ROOT / name
        if not compose.exists():
            continue

        text = compose.read_text(encoding="utf-8")
        formal_ref = f"./sql/tiers/{tier}/{tier}-init.sql:"
        if formal_ref not in text:
            violations.append(f"{name} does not mount {tier}-init.sql")

        for source in BIND_MOUNT_SQL.findall(text):
            if not (ROOT / source[2:]).exists():
                violations.append(f"{name} mounts a missing SQL path: {source}")


def main() -> int:
    violations = []  # type: List[str]

    for tier in ("small", "medium", "full"):
        tier_dir = DEPLOY / tier
        compose = tier_dir / "docker-compose.yml"
        init_sql = SQL / "tiers" / tier / f"{tier}-init.sql"

        for name in ("docker-compose.yml", ".env.example", "init-order.md"):
            if not (tier_dir / name).exists():
                violations.append(f"missing deploy entry file: {tier_dir / name}")

        if not init_sql.exists():
            violations.append(f"missing formal tier init SQL: {init_sql}")

        if compose.exists():
            text = compose.read_text(encoding="utf-8")
            formal_ref = f"/tiers/{tier}/{tier}-init.sql:"
            if formal_ref not in text:
                violations.append(f"{compose} does not mount {tier}-init.sql")
            if f"/tiers/{tier}/postgres/" in text:
                violations.append(f"{compose} references removed split postgres SQL layout")

    for name in (
        "deploy-95.sh",
        "cleanup-95.sh",
        "verify-95.sh",
        "verify-file-service-95.sh",
        "rehearse-postgres-upgrades.sh",
        "rehearse-postgres-backup-upgrades.sh",
        "publish-service-images-95.sh",
        "generate-image-release-manifest-95.sh",
        "rehearse-image-digest-deploy-95.sh",
    ):
        if not (DEPLOY / "scripts" / name).exists():
            violations.append(f"missing deploy script: deploy/scripts/{name}")

    check_legacy_root_compose(violations)

    release_manifests = DEPLOY / "release-manifests"
    if not release_manifests.exists():
        violations.append("missing deploy release manifest directory: deploy/release-manifests")
    if not (release_manifests / "README.md").exists():
        violations.append("missing deploy release manifest README: deploy/release-manifests/README.md")

    if violations:
        print("\n".join(violations))
        return 1

    print("deploy layout ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
