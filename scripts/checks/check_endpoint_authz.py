#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""接口访问控制门禁：每个请求映射方法都必须有访问控制注解。

与运行期的 PermissionCheckPostProcessor 是互补关系，不是重复：

- 运行期校验器只扫 @AdminAuth 控制器，以及「已经用了权限注解」的 @RestController。
  一个完全没有任何权限注解、也没有 @AdminAuth 的控制器，它一个方法都不会检查——
  而那恰恰是未授权接口最可能藏身的地方。本门禁不做这层筛选，全量扫。
- 运行期校验器默认 fail-fast=false（见 PermissionCheckProperties），
  违规只打日志不阻断启动，且要等到部署后才发现。本门禁在提交前就拦。

判定口径与运行期保持一致：注解可以挂在方法上、父类同名方法上、类上或父类上，
四者任一命中即视为已有访问控制。本仓库 A/B/I 三层控制器（ASysUserController
extends BSysUserController）大量依赖父类注解，不解析继承会产生大量误报。

允许的注解与运行期同一份清单：
@PreAuthorize、@RequiresPermission、@RequiresRole、@InnerAuth、@PermissionExempt。
@RequiresLogin 与 @AllowClient 没有对应切面实现，不计入。
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

PERMISSION_ANNOTATIONS = (
    "PreAuthorize",
    "RequiresPermission",
    "RequiresRole",
    "InnerAuth",
    "PermissionExempt",
)

MAPPING_RE = re.compile(
    r"@(?:Get|Post|Put|Delete|Patch|Request)Mapping\b"
)
CLASS_DECL_RE = re.compile(
    r"(?:public\s+)?(?:abstract\s+)?class\s+(\w+)(?:\s*<[^>]*>)?\s*"
    r"(?:extends\s+([\w.]+)(?:\s*<[^>]*>)?)?"
)
METHOD_DECL_RE = re.compile(r"(?:public|protected)\s+[\w<>,\[\]\s.?]+\s+(\w+)\s*\(")

SKIP_PARTS = {"target", "node_modules", ".git", "build"}


def iter_controller_files():
    for path in ROOT.rglob("*.java"):
        if SKIP_PARTS & set(path.parts):
            continue
        if "src/test/java" in path.as_posix():
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        if "@RestController" not in text and "@Controller" not in text:
            continue
        yield path, text


def parse_controller(path: Path, text: str) -> dict | None:
    match = CLASS_DECL_RE.search(text)
    if not match:
        return None
    lines = text.splitlines()
    header = text[: match.start()]

    methods = []
    for index, line in enumerate(lines):
        if not MAPPING_RE.search(line):
            continue

        name = None
        for offset in range(index, min(len(lines), index + 12)):
            found = METHOD_DECL_RE.search(lines[offset])
            if found and not MAPPING_RE.search(lines[offset]):
                name = found.group(1)
                break
        if name is None:
            continue

        # 注解块可能在 @XxxMapping 之前，也可能在之后，两侧都要看
        block = []
        for offset in range(index, max(-1, index - 12), -1):
            stripped = lines[offset].strip()
            if stripped.startswith(("@", "//", "*", "/*")) or not stripped:
                block.append(stripped)
            else:
                break
        for offset in range(index + 1, min(len(lines), index + 12)):
            stripped = lines[offset].strip()
            if stripped.startswith("@"):
                block.append(stripped)
            elif METHOD_DECL_RE.search(stripped):
                break
        blob = " ".join(block)

        methods.append({
            "name": name,
            "line": index + 1,
            "annotations": {a for a in PERMISSION_ANNOTATIONS if "@" + a in blob},
        })

    return {
        "file": path.relative_to(ROOT).as_posix(),
        "parent": match.group(2).split(".")[-1] if match.group(2) else None,
        "class_annotations": {a for a in PERMISSION_ANNOTATIONS if "@" + a in header},
        "methods": methods,
    }


def resolve_class_annotations(classes: dict, name: str, seen: set | None = None) -> set:
    seen = seen or set()
    if name not in classes or name in seen:
        return set()
    seen.add(name)
    entry = classes[name]
    found = set(entry["class_annotations"])
    if entry["parent"]:
        found |= resolve_class_annotations(classes, entry["parent"], seen)
    return found


def resolve_method_annotations(classes: dict, name: str, method: str, seen: set | None = None) -> set:
    """方法级注解沿类层次向上找，与 Spring 的 AnnotationUtils.findAnnotation 口径一致。"""
    seen = seen or set()
    if name not in classes or name in seen:
        return set()
    seen.add(name)
    entry = classes[name]
    for candidate in entry["methods"]:
        if candidate["name"] == method and candidate["annotations"]:
            return set(candidate["annotations"])
    if entry["parent"]:
        return resolve_method_annotations(classes, entry["parent"], method, seen)
    return set()


def main() -> int:
    classes = {}
    for path, text in iter_controller_files():
        parsed = parse_controller(path, text)
        if parsed:
            classes[CLASS_DECL_RE.search(text).group(1)] = parsed

    violations = []
    method_total = 0
    for name in sorted(classes):
        entry = classes[name]
        inherited = resolve_class_annotations(classes, name)
        for method in entry["methods"]:
            method_total += 1
            if resolve_method_annotations(classes, name, method["name"]) | inherited:
                continue
            violations.append(
                f"{entry['file']}:{method['line']}: {name}.{method['name']} 缺访问控制注解"
                f"（@PreAuthorize / @RequiresPermission / @RequiresRole / @InnerAuth / @PermissionExempt 任选其一；"
                f"公开接口用 @PermissionExempt 并写明原因）"
            )

    # 扫到 0 个方法说明解析口径失效，与运行期校验器同样把空转视为异常
    if method_total == 0:
        print("endpoint authz check 未扫描到任何请求映射方法，解析逻辑很可能已失效")
        return 1

    if violations:
        print("\n".join(violations))
        print(f"\n共 {len(violations)} 处违规（扫描 {len(classes)} 个控制器，{method_total} 个映射方法）")
        return 1

    print(f"endpoint authz ok（{len(classes)} 个控制器，{method_total} 个映射方法）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
