# full 部署说明

- 正式 Compose：`deploy/full/docker-compose.yml`
- 环境变量样板：`deploy/full/.env.example`
- PostgreSQL 初始化：`sql/tiers/full/full-init.sql`
- Nacos 导入：`sql/tiers/full/full-nacos-derby-import.sql`

`.env.example` 里标为必填的口令在 compose 里是 `${VAR:?...}` 形式，缺失即启动失败，
先 `cp .env.example .env` 并逐项填写真实值再启动。

初始化顺序：

1. PostgreSQL
2. Redis
3. Nacos
4. RustFS
5. RabbitMQ
6. Gateway
7. Auth
8. System
9. Tenant
10. Workflow
11. Open
12. File
13. Job
14. AI
15. Gen
16. UI

对外端口：

- PostgreSQL `5432`
- Redis `6379`
- Nacos `8848` / `9848`
- RustFS `9000`
- RustFS Console `9001`
- RabbitMQ `5672`
- RabbitMQ Management `15672`
- Gateway `9090`
- Auth `9200`
- System `9201`
- Tenant `9202`
- Workflow `9203`
- Job `9204`
- Open `9205`
- File `9207`
- AI `9208`
- UI `3000`

Gen 服务只在 `han-network` 内部可达，没有宿主机端口映射。

## UI 镜像

full 档默认使用通用后台 `han-ui`（`HAN_UI_IMAGE`）。若需要换成其它前端，
覆盖 `.env` 里的 `HAN_UI_IMAGE` 即可，compose 不做任何前端特化。

## AI 短剧（aivideo）可选服务

`docker-compose.yml` 里保留了 `aivideo` 服务定义（容器端口 `9209`），但它挂在
`aivideo` compose profile 下，**默认不启动**：generic-v2 通用底座不含
`han-modules/han-aivideo`，CI 也不构建 `han-aivideo` 镜像。

只有在带该模块的分支上才需要启用：

```bash
docker compose --profile aivideo up -d
```

启用前需要在 `.env` 里配好 `HAN_AIVIDEO_IMAGE` 与
`HAN_AIVIDEO_MEDIA_PUBLIC_FILE_ORIGIN`，并确认该镜像确实已推送到仓库。
