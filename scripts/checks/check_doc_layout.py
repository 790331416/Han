from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
ALLOWED_ROOT_FILES = {"index.md"}
ALLOWED_DIRS = {"00-治理", "01-架构", "02-开发", "03-测试", "04-部署", "05-运维", "归档"}


def tracked_docs() -> list[Path]:
    result = subprocess.run(
        ["git", "ls-files", "docs"],
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
    for child in DOCS.iterdir():
        if child.is_file() and child.name not in ALLOWED_ROOT_FILES:
            violations.append(f"docs 根目录仅允许保留 index.md，发现: {child.name}")
        if child.is_dir() and child.name not in ALLOWED_DIRS:
            violations.append(f"docs 发现未登记目录: {child.name}")
    index_text = (DOCS / "index.md").read_text(encoding="utf-8")
    for path in tracked_docs():
        rel = path.relative_to("docs")
        if rel.parts and rel.parts[0] == "归档":
            continue
        if rel.name == "index.md":
            continue
        if rel.name not in index_text:
            violations.append(f"文档未写入 docs/index.md: {path.as_posix()}")
    if violations:
        print("\n".join(violations))
        return 1
    print("doc layout ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
