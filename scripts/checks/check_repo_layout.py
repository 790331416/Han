from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ALLOWED_TOP_LEVEL = {
    ".env.example",
    ".gitattributes",
    ".gitignore",
    ".github",
    ".trae",
    ".windsurfrules",
    ".windsurf",
    "README.md",
    "BUILD_DEPLOY_GUIDE.md",
    "build.sh",
    "build.bat",
    "build.ps1",
    "docker",
    "docker-compose-small.yml",
    "docker-compose.yml",
    "docker-compose-full.yml",
    "han-api",
    "han-auth",
    "han-common",
    "han-gateway",
    "han-modules",
    "han-starter",
    "han-ui",
    "han-visual",
    "nacos",
    "pom.xml",
    "settings.xml",
    "settings.workspace.xml",
    "sql",
    "docs",
    "deploy",
    "scripts",
}


def tracked_paths() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files"],
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
    top_levels = {path.parts[0] for path in tracked_paths() if path.parts}
    for item in sorted(top_levels):
        if item not in ALLOWED_TOP_LEVEL:
            violations.append(f"顶层条目未入白名单: {item}")
    if any(path.parts and path.parts[0] == "doc" for path in tracked_paths()):
        violations.append("doc/ 已退役，仍存在被跟踪文件")
    if violations:
        print("\n".join(violations))
        return 1
    print("repo layout ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
