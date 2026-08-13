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

# 2026-08-13 起扫描范围扩到全仓（模块配置、Nacos 配置种子、前端与测试代码）。
# 排除项：构建产物、依赖目录，以及 docs/archive —— 归档是历史记录，
# 里面的口令属于「当时确实这么写过」的事实，改写归档等于篡改历史；
# 真正要做的是轮换口令，而不是把痕迹擦掉。
FULL_SCAN_EXCLUDES = re.compile(
    r"^(target/|.*/target/|node_modules/|.*/node_modules/|\.git/|dist/|.*/dist/"
    r"|archive/|.*/archive/|han-ui/output/|han-aivideo-ui/output/)"
)

SCAN_SUFFIXES = {
    ".yml",
    ".yaml",
    ".sh",
    ".bat",
    ".ps1",
    ".py",
    ".env",
    ".env.example",
    ".example",
    ".conf",
    ".md",
}

# 全仓扫描的文件类型：配置、SQL 与源码。部署面已由 SCAN_SUFFIXES 覆盖，
# 这里补上模块配置、Nacos 配置种子和前后端源码。
#
# 不含 .md：文档不配置任何东西，却需要引用「${VAR:明文口令}」这种反面写法来说明问题，
# 扫进来只会逼着文档把话说含糊。文档里的真实凭据由部署面的已泄漏字面量检查负责。
FULL_SCAN_SUFFIXES = {
    ".yml",
    ".yaml",
    ".sql",
    ".java",
    ".ts",
    ".vue",
    ".js",
    ".properties",
}

# 本检查自身必然包含这些字面量，跳过。
SELF = Path(__file__).relative_to(ROOT).as_posix()

LEAKED_LITERALS = [
    "han@2026",
    "han-cloud-inner-auth",
    "admin123",
    "aGFuQDIwMjZoYW5AMjAyNmhhbkAyMDI2aGFuQDIwMjZoYW5AMjAyNg==",
]

# ${VAR:-<value>} / ${VAR-<value>} 形式给敏感变量兜底默认值，等于把口令写进仓库。
# ${VAR:-} 是空默认值，不算泄漏；${VAR:?...} 是强制注入，本来就是目标写法。
SENSITIVE_DEFAULT = re.compile(
    r"\$\{(\w*(?:PASSWORD|SECRET|TOKEN|API_KEY|ACCESS_KEY)\w*):?-([^}]+)\}"
)

# Spring 占位符 ${VAR:默认值}。上面的 SENSITIVE_DEFAULT 只认 shell 的 ${VAR:-默认值}，
# 认不出这种形式——18 个服务的 application.yml 里 ${NACOS_PASSWORD:han@2026} 一直没被拦下，
# 只是因为字面量恰好在 LEAKED_LITERALS 里才暴露；换个新口令写进去就完全无感了。
# ${VAR:} 是空默认值，等价于不给兜底，放行。
# 默认值不能以 - 或 ? 开头，否则命中的是 shell 的 ${VAR:-空} 与 ${VAR:?必填}，
# 那两种由 SENSITIVE_DEFAULT 负责，且本就是允许的写法。
SPRING_SENSITIVE_DEFAULT = re.compile(
    r"\$\{(\w*(?:PASSWORD|SECRET|TOKEN|API_KEY|ACCESS_KEY)\w*):([^}\s?\-][^}]*)\}"
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


def full_scan_targets() -> list[Path]:
    """全仓范围，只用于「敏感变量带明文兜底默认值」这条规则。

    已泄漏字面量的扫描仍限部署面：铺到全仓会带出大量与本次无关的存量
    （归档 SQL 里的 admin123、E2E 夹具用的登录口令、审计文档里对问题本身的引用），
    那些要单独排期清理，混进来只会让门禁被习惯性忽略。
    """
    targets: list[Path] = []
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or path.suffix not in FULL_SCAN_SUFFIXES:
            continue
        rel = path.relative_to(ROOT).as_posix()
        if FULL_SCAN_EXCLUDES.match(rel):
            continue
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

    for path in full_scan_targets():
        rel = path.relative_to(ROOT).as_posix()
        if rel == SELF:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for lineno, line in enumerate(text.splitlines(), 1):
            for var, default in SPRING_SENSITIVE_DEFAULT.findall(line):
                if var in ALLOWED_DEFAULT_VARS:
                    continue
                violations.append(
                    f"{rel}:{lineno} 敏感变量 {var} 带明文兜底默认值 ({default.strip()})；"
                    f"去掉默认值写成 ${{{var}}}，让缺失时直接启动失败"
                )

    if violations:
        print("\n".join(violations))
        return 1

    print("deploy secret scan ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
