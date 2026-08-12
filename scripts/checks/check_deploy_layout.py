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

# generic-v2 通用底座不含 han-modules/han-aivideo，CI 也不构建 han-aivideo 镜像。
# 这条检查以前是反着写的：它强制 full compose 必须含 aivideo: 服务、强制
# deploy/full/.env.example 的 HAN_UI_IMAGE 必须指向 han-aivideo-ui，
# 于是每次把短剧业务从通用底座摘出去都会被 repo-guard 判违规再拉回来
# （提交历史 a91824a 移除、8abcd60 恢复）。现在改为守住通用边界：
# aivideo 服务可以留在文件里，但必须挂 profile，不得进入默认启动路径。
AIVIDEO_SERVICE = re.compile(r"^  aivideo:\s*$", re.MULTILINE)
AIVIDEO_PROFILE = re.compile(r"^  aivideo:\s*\n(?:\s*#.*\n)*    profiles:\s*\n\s+-\s*aivideo\s*$", re.MULTILINE)


# compose 里 ${VAR:?...} 是「缺失即启动失败」的必填注入项，对应的 .env.example
# 必须给出占位符，否则运维照抄样板也起不来。
REQUIRED_ENV_VAR = re.compile(r"\$\{(\w+):\?")


def env_example_keys(path: Path) -> set:
    keys = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        keys.add(stripped.split("=", 1)[0].strip())
    return keys


def check_required_env_coverage(violations: List[str], compose: Path, env_example: Path) -> None:
    if not compose.exists() or not env_example.exists():
        return
    defined = env_example_keys(env_example)
    for name in sorted(set(REQUIRED_ENV_VAR.findall(compose.read_text(encoding="utf-8")))):
        if name not in defined:
            violations.append(
                f"{compose} requires {name} but {env_example} does not define it"
            )


def check_generic_base_boundary(violations: List[str]) -> None:
    compose = DEPLOY / "full" / "docker-compose.yml"
    if compose.exists():
        text = compose.read_text(encoding="utf-8")
        if AIVIDEO_SERVICE.search(text) and not AIVIDEO_PROFILE.search(text):
            violations.append(
                f"{compose} defines an aivideo service outside the 'aivideo' compose profile"
            )

    env_example = DEPLOY / "full" / ".env.example"
    if env_example.exists():
        text = env_example.read_text(encoding="utf-8")
        for line in text.splitlines():
            if line.startswith("HAN_UI_IMAGE=") and "han-aivideo-ui" in line:
                violations.append(
                    f"{env_example} must not default the generic full UI to han-aivideo-ui"
                )


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

        check_required_env_coverage(violations, compose, ROOT / ".env.example")


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

        check_required_env_coverage(violations, compose, tier_dir / ".env.example")

        # 三档均已开放 MySQL 入口，每档都必须有独立的 MySQL compose 与初始化 SQL
        mysql_compose = tier_dir / "docker-compose-mysql.yml"
        mysql_init_sql = SQL / "tiers" / tier / f"{tier}-init-mysql.sql"
        if not mysql_compose.exists():
            violations.append(f"missing MySQL {tier} compose entry: {mysql_compose}")
        if not mysql_init_sql.exists():
            violations.append(f"missing MySQL {tier} init SQL: {mysql_init_sql}")
        if mysql_compose.exists():
            mysql_text = mysql_compose.read_text(encoding="utf-8")
            for token in ("mysql:8.4.10", f"/tiers/{tier}/{tier}-init-mysql.sql:", "jdbc:mysql://"):
                if token not in mysql_text:
                    violations.append(f"{mysql_compose} missing MySQL token: {token}")
            # MySQL 入口不得再挂 PostgreSQL 的库或初始化脚本
            if "postgres" in mysql_text.lower():
                violations.append(f"{mysql_compose} 仍残留 PostgreSQL 引用，MySQL 入口必须完全切换")

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
    check_generic_base_boundary(violations)

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
