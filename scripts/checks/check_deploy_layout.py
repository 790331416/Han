from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEPLOY = ROOT / "deploy"


def main() -> int:
    violations: list[str] = []
    for tier in ("small", "medium", "full"):
        tier_dir = DEPLOY / tier
        for name in ("docker-compose.yml", ".env.example", "init-order.md"):
            if not (tier_dir / name).exists():
                violations.append(f"缺少部署入口文件: {tier_dir / name}")
    for name in ("deploy-95.sh", "cleanup-95.sh", "verify-95.sh"):
        if not (DEPLOY / "scripts" / name).exists():
            violations.append(f"缺少部署脚本: deploy/scripts/{name}")
    if violations:
        print("\n".join(violations))
        return 1
    print("deploy layout ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
