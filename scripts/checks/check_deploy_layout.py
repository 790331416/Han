import sys
from pathlib import Path
from typing import List

ROOT = Path(__file__).resolve().parents[2]
DEPLOY = ROOT / "deploy"
SQL = ROOT / "sql"


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
            if tier == "full":
                for token in ("\n  aivideo:", "HAN_AIVIDEO_IMAGE", "HAN_UI_IMAGE"):
                    if token not in text:
                        violations.append(f"{compose} missing AIVideo full-tier token: {token.strip()}")

                env_example = (tier_dir / ".env.example").read_text(encoding="utf-8")
                if "HAN_UI_IMAGE=registry.cn-hangzhou.aliyuncs.com/xzy0112/han-aivideo-ui:" not in env_example:
                    violations.append(
                        f"{tier_dir / '.env.example'} must default full UI to han-aivideo-ui"
                    )

        if tier == "small":
            mysql_compose = tier_dir / "docker-compose-mysql.yml"
            mysql_init_sql = SQL / "tiers" / tier / "small-init-mysql.sql"
            sdfz_education_sql = SQL / "sdfz" / "mysql" / "20260811_education_master.sql"
            if not mysql_compose.exists():
                violations.append(f"missing MySQL small compose entry: {mysql_compose}")
            if not mysql_init_sql.exists():
                violations.append(f"missing MySQL small init SQL: {mysql_init_sql}")
            if not sdfz_education_sql.exists():
                violations.append(f"missing SDFZ education SQL: {sdfz_education_sql}")
            if mysql_compose.exists():
                mysql_text = mysql_compose.read_text(encoding="utf-8")
                for token in (
                    "mysql:8.4.10",
                    "/tiers/small/small-init-mysql.sql:",
                    "/sdfz/mysql/20260811_education_master.sql:",
                    "jdbc:mysql://",
                ):
                    if token not in mysql_text:
                        violations.append(f"{mysql_compose} missing MySQL token: {token}")

                # 新增 sdfz 脚本却忘了挂进 compose，全新部署就会缺表缺数据。
                # 这里按目录反查，任何新脚本都必须同时出现在挂载列表里。
                for sdfz_file in sorted((SQL / "sdfz" / "mysql").glob("*.sql")):
                    mount_token = f"/sdfz/mysql/{sdfz_file.name}:"
                    if mount_token not in mysql_text:
                        violations.append(
                            f"{mysql_compose} 未挂载 SDFZ SQL，全新部署会缺数据: {mount_token}"
                        )

                # han-auth / han-system / han-job 不在此列：它们已不再映射宿主机端口。
                # 三者的 Spring Security 是 permitAll、身份靠网关下发的请求头，
                # 一旦对宿主机开放就能绕过网关认证，所以这里也不能再要求它们有可配端口。
                for token in (
                    "HAN_MYSQL_HOST_PORT",
                    "HAN_REDIS_HOST_PORT",
                    "HAN_NACOS_HTTP_HOST_PORT",
                    "HAN_NACOS_GRPC_HOST_PORT",
                    "HAN_GATEWAY_HOST_PORT",
                    "HAN_UI_HOST_PORT",
                ):
                    if token not in mysql_text:
                        violations.append(f"{mysql_compose} missing configurable host port: {token}")

                for token in ("HAN_AUTH_HOST_PORT", "HAN_SYSTEM_HOST_PORT", "HAN_JOB_HOST_PORT"):
                    if token in mysql_text:
                        violations.append(
                            f"{mysql_compose} 不应再把业务服务端口暴露到宿主机（可绕过网关认证）: {token}"
                        )

            postgres_text = compose.read_text(encoding="utf-8") if compose.exists() else ""
            if "HAN_POSTGRES_HOST_PORT" not in postgres_text:
                violations.append(f"{compose} missing configurable PostgreSQL host port")

    aivideo_nginx = ROOT / "han-aivideo-ui" / "nginx.conf"
    if not aivideo_nginx.exists():
        violations.append("missing han-aivideo-ui/nginx.conf")
    elif "client_max_body_size 320m;" not in aivideo_nginx.read_text(encoding="utf-8"):
        violations.append("han-aivideo-ui nginx upload limit must match the full 320MB request limit")

    deploy_helper = ROOT / "scripts" / "helpers" / "deploy-aivideo-acr.ps1"
    if not deploy_helper.exists():
        violations.append("missing scripts/helpers/deploy-aivideo-acr.ps1")
    else:
        helper_text = deploy_helper.read_text(encoding="utf-8")
        for token in (
            "root@10.18.35.95",
            "/opt/han/deploy/full",
            "origin/master",
            "AllowNonMasterTag",
            "A non-master tag cannot target formal server 95",
        ):
            if token not in helper_text:
                violations.append(f"{deploy_helper} missing formal deploy guard token: {token}")
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
