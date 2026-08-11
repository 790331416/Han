# small 部署说明

- PostgreSQL Compose（默认）：`deploy/small/docker-compose.yml`
- MySQL Compose：`deploy/small/docker-compose-mysql.yml`
- 环境变量样板：`deploy/small/.env.example`
- PostgreSQL 初始化：`sql/tiers/small/small-init.sql`
- MySQL 初始化：`sql/tiers/small/small-init-mysql.sql`
- Nacos 导入：`sql/tiers/small/small-nacos-derby-import.sql`

初始化顺序：

1. PostgreSQL 或 MySQL（二选一）
2. Redis
3. Nacos
4. Gateway
5. Auth
6. System
7. Job
8. UI

对外端口：

- PostgreSQL `15432`
- MySQL `13306`
- Redis `16379`
- Nacos `18848`
- Gateway `19090`
- Auth `19200`
- System `19201`
- Job `19204`
- UI `3100`

上述端口是默认值，可通过 `.env` 中的 `HAN_*_HOST_PORT` 变量覆盖。需要在同一台服务器并行验证多套 small 环境时，必须同时使用独立 Compose project name 和独立宿主机端口；这样容器、网络、数据卷和端口都不会覆盖既有环境。例如：

```bash
docker compose -p hansdfz \
  --env-file /opt/han/config/sdfz-small.env \
  -f /opt/han/deploy/small/docker-compose-mysql.yml config
```

先执行 `config` 检查最终配置和端口，确认无冲突后再分阶段启动。测试环境的数据库、Redis 和 Nacos 使用该 project 自己的数据卷，不与正式 small/medium/full 共享；镜像应固定到已验证的提交标签，不使用 `latest` 作为验收证据。
