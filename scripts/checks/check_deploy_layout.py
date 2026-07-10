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
