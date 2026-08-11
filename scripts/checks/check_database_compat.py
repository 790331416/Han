from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def main() -> int:
    violations: list[str] = []

    mybatis_config = ROOT / "han-common/han-common-mybatis/src/main/java/com/han/common/mybatis/config/MybatisPlusConfig.java"
    config_text = read(mybatis_config)
    if "new PaginationInnerInterceptor()" not in config_text:
        violations.append("pagination dialect must be auto-detected")
    if "DbType.POSTGRE_SQL" in config_text or "DbType.MYSQL" in config_text:
        violations.append("pagination dialect must not be hard-coded")
    for token in ('properties.setProperty("PostgreSQL", "postgresql")', 'properties.setProperty("MySQL", "mysql")'):
        if token not in config_text:
            violations.append(f"missing databaseId mapping: {token}")

    mapper = ROOT / "han-modules/han-gen/src/main/resources/mapper/GenTableMapper.xml"
    mapper_text = read(mapper)
    for token in ('databaseId="postgresql"', 'databaseId="mysql"', "information_schema.columns"):
        if token not in mapper_text:
            violations.append(f"generator mapper missing token: {token}")

    datasource_pom = ROOT / "han-common/han-common-datasource/pom.xml"
    datasource_text = read(datasource_pom)
    for artifact in ("<artifactId>postgresql</artifactId>", "<artifactId>mysql-connector-j</artifactId>"):
        if artifact not in datasource_text:
            violations.append(f"common datasource missing driver: {artifact}")

    module_poms = sorted((ROOT / "han-modules").glob("*/pom.xml"))
    for pom in module_poms:
        text = read(pom)
        if "<artifactId>postgresql</artifactId>" in text and "<artifactId>mysql-connector-j</artifactId>" not in text:
            violations.append(f"database module missing MySQL driver: {pom.relative_to(ROOT)}")

    configs = sorted((ROOT / "han-modules").glob("*/src/main/resources/*.yml"))
    for config in configs:
        text = read(config)
        if "jdbc:postgresql:" not in text:
            continue
        if "${DB_URL:" not in text:
            violations.append(f"database URL is not switchable: {config.relative_to(ROOT)}")
        if "driver-class-name: org.postgresql.Driver" in text:
            violations.append(f"database driver is hard-coded: {config.relative_to(ROOT)}")

    if violations:
        print("\n".join(violations))
        return 1

    print("database compatibility layout ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
