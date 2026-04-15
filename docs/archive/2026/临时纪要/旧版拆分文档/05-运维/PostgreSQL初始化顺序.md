# PostgreSQL初始化顺序

## 通用顺序

1. 基础 schema
2. 基础 seed
3. tier 增量模块 schema
4. tier 增量模块 seed
5. 升级脚本

## tier 口径

- `small`：system、job
- `medium`：在 `small` 基础上增加 tenant、workflow、open、file
- `full`：在 `medium` 基础上增加 ai、gen
