from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
ALLOWED_ROOT_FILES = {
    "index.md",
    "01-产品与架构总览.md",
    "02-开发手册.md",
    "03-部署手册.md",
    "04-测试与验收手册.md",
    "05-运维与95环境手册.md",
    "06-牛马协作总规则.md",
    "07-仓库整理与重构执行计划.md",
    "08-AI短剧开发手册.md",
    "09-代码注释规范.md",
    "10-系统枚举治理与公共能力落位.md",
    "11-PostgreSQL-MySQL兼容与切换手册.md",
    "巴蜀云校一账号多学校身份实施与验收方案-20260824.md",
    "巴蜀云校多学校身份生产发布记录-20260825.md",
    "巴蜀云校统一文件与多对象存储实施方案-20260825.md",
}
ALLOWED_ROOT_DIRS = {"archive", "aivideo"}


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
            violations.append(f"docs 根目录发现未登记文件: {child.name}")
        if child.is_dir() and child.name not in ALLOWED_ROOT_DIRS:
            violations.append(f"docs 根目录发现未登记目录: {child.name}")

    index_text = (DOCS / "index.md").read_text(encoding="utf-8")
    for required in sorted(ALLOWED_ROOT_FILES - {"index.md"}):
        if required not in index_text:
            violations.append(f"docs/index.md 未写入正式手册入口: {required}")

    for path in tracked_docs():
        rel = path.relative_to("docs")
        if not rel.parts:
            continue
        if rel.parts[0] == "archive":
            continue
        if rel.parts[0] == "aivideo":
            continue
        if len(rel.parts) != 1:
            violations.append(f"正式文档不允许继续使用拆分目录: {path.as_posix()}")
            continue
        if rel.name not in ALLOWED_ROOT_FILES:
            violations.append(f"docs 跟踪到未登记正式文档: {path.as_posix()}")

    if violations:
        print("\n".join(violations))
        return 1

    print("doc layout ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
