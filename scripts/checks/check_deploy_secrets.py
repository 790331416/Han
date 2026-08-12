"""部署面明文凭据扫描。

repo-guard 此前只查目录布局，没有任何 secret scanning，这也是十几处明文口令
能一路进主干的原因。本检查覆盖部署与 CI 相关的文件，命中已知泄漏口令或
明显的明文凭据写法即失败。

**当前只覆盖部署面**（deploy/、scripts/、.github/、docker/、根目录 compose 与
.env.example）。仓库其它位置仍有历史遗留的明文口令，主要是：

- ``sql/tiers/*/*-nacos-derby-import.sql``（Nacos 配置内容里的 DB / Redis 口令）
- 各服务的 ``application.yml`` / ``application-docker.yml``（``${DB_PASSWORD:han@2026}`` 形式）
- ``han-starter/han-starter-storage`` 的 ``StorageProperties``、
  ``han-common-core`` 的 ``InnerAuthProperties``

这些不在部署面的所有权范围内，等对应模块清理完成后把 SCAN_ROOTS 扩到全仓即可。
注意：``han@2026`` 已经在 Git 历史里，改配置不等于消除泄漏，口令必须轮换。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

SCAN_DIRS = ["deploy", "scripts", ".github", "docker"]
SCAN_FILES = [
    ".env.example",
    "docker-compose.yml",
    "docker-compose-small.yml",
    "docker-compose-full.yml",
]

SCAN_SUFFIXES = {
    ".yml",
    ".yaml",
    ".sh",
    ".bat",
    ".ps1",
    ".py",
    ".env",
    ".example",
    ".conf",
    ".md",
}

# 本检查自身必然包含这些字面量，跳过。
SELF = Path(__file__).relative_to(ROOT).as_posix()

LEAKED_LITERALS = [
    "han@2026",
    "han-cloud-inner-auth",
    "aGFuQDIwMjZoYW5AMjAyNmhhbkAyMDI2aGFuQDIwMjZoYW5AMjAyNg==",
]

# ${VAR:-<value>} / ${VAR-<value>} 形式给敏感变量兜底默认值，等于把口令写进仓库。
# ${VAR:-} 是空默认值，不算泄漏；${VAR:?...} 是强制注入，本来就是目标写法。
SENSITIVE_DEFAULT = re.compile(
    r"\$\{(\w*(?:PASSWORD|SECRET|TOKEN|API_KEY|ACCESS_KEY)\w*):?-([^}]+)\}"
)

# 允许保留默认值的非机密项。
ALLOWED_DEFAULT_VARS = {"RUSTFS_ACCESS_KEY"}


def scan_targets() -> list[Path]:
    targets: list[Path] = []
    for name in SCAN_FILES:
        path = ROOT / name
        if path.is_file():
            targets.append(path)
    for name in SCAN_DIRS:
        base = ROOT / name
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*")):
            if path.is_file() and path.suffix in SCAN_SUFFIXES:
                targets.append(path)
    return targets


def main() -> int:
    violations: list[str] = []

    for path in scan_targets():
        rel = path.relative_to(ROOT).as_posix()
        if rel == SELF:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            violations.append(f"{rel}: 不是 UTF-8，无法扫描")
            continue

        for lineno, line in enumerate(text.splitlines(), 1):
            for literal in LEAKED_LITERALS:
                if literal in line:
                    violations.append(f"{rel}:{lineno} 命中已泄漏的明文凭据: {literal}")

            for var, default in SENSITIVE_DEFAULT.findall(line):
                if var in ALLOWED_DEFAULT_VARS:
                    continue
                violations.append(
                    f"{rel}:{lineno} 敏感变量 {var} 带明文兜底默认值 ({default.strip()})；"
                    f"改用 ${{{var}:?...}} 强制注入"
                )

    if violations:
        print("\n".join(violations))
        return 1

    print("deploy secret scan ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
