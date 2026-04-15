# small 部署说明

- 正式 Compose：`deploy/small/docker-compose.yml`
- 环境变量样板：`deploy/small/.env.example`
- PostgreSQL 初始化：`sql/tiers/small/small-init.sql`
- Nacos 导入：`sql/tiers/small/small-nacos-derby-import.sql`

初始化顺序：

1. PostgreSQL
2. Redis
3. Nacos
4. Gateway
5. Auth
6. System
7. Job
8. UI

对外端口：

- PostgreSQL `15432`
- Redis `16379`
- Nacos `18848`
- Gateway `19090`
- Auth `19200`
- System `19201`
- Job `19204`
- UI `3100`
