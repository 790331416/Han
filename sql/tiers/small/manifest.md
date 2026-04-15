# small SQL 清单

## 模块

- `postgres/system`
- `postgres/job`
- `nacos/derby-import.sql`

## 初始化顺序

1. `postgres/system/00-schema.sql`
2. `postgres/system/10-seed.sql`
3. `postgres/job/00-schema.sql`
4. `postgres/job/10-seed.sql`
5. `postgres/job/90-fixup.sql`
6. `nacos/derby-import.sql`

## 说明

- `small` 不包含 tenant、workflow、open、file、ai、gen 正式模块
- PostgreSQL 来源优先采用当前仓库中的 PostgreSQL 母本拆分结果
