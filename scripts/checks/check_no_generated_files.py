from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FORBIDDEN_SUBSTRINGS = [
    "han-ui/dist/",
    "han-aivideo-ui/dist/",
    ".m2/",
    ".codex-temp/",
    "output/playwright/",
]
FORBIDDEN_SUFFIXES = [".log", ".tar.gz", ".zip", ".tsbuildinfo"]


def main() -> int:
    result = subprocess.run(
        ["git", "ls-files"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    violations: list[str] = []
    for line in result.stdout.splitlines():
        path = line.strip()
        if not path:
            continue
        if not (ROOT / path).exists():
            continue
        if any(token in path for token in FORBIDDEN_SUBSTRINGS):
            violations.append(f"检测到不应提交的产物: {path}")
        if any(path.endswith(suffix) for suffix in FORBIDDEN_SUFFIXES):
            violations.append(f"检测到压缩包或日志产物: {path}")
    if violations:
        print("\n".join(violations))
        return 1
    print("generated file check ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
