# full SQL 清单

## 模块

- `postgres/system`
- `postgres/job`
- `postgres/tenant`
- `postgres/workflow`
- `postgres/open`
- `postgres/file`
- `postgres/ai`
- `postgres/gen`
- `nacos/derby-import.sql`

## 初始化顺序

1. `postgres/system/00-schema.sql`
2. `postgres/system/10-seed.sql`
3. `postgres/job/00-schema.sql`
4. `postgres/job/10-seed.sql`
5. `postgres/job/90-fixup.sql`
6. `postgres/tenant/00-schema.sql`
7. `postgres/tenant/10-seed.sql`
8. `postgres/tenant/90-fixup.sql`
9. `postgres/workflow/00-schema.sql`
10. `postgres/workflow/10-seed.sql`
11. `postgres/open/00-schema.sql`
12. `postgres/open/10-seed.sql`
13. `postgres/file/00-schema.sql`
14. `postgres/file/10-seed.sql`
15. `postgres/ai/00-schema.sql`
16. `postgres/ai/10-seed.sql`
17. `postgres/gen/00-schema.sql`
18. `postgres/gen/10-seed.sql`
19. `nacos/derby-import.sql`

## 说明

- `ai` 与 `gen` 仅出现在 `full`
- `AI Graph` 虽然数据库结构存在于 AI 模块，但当前功能仍按未开发边界管理
