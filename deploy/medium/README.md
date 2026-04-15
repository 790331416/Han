# medium 部署说明

- 正式 Compose：`deploy/medium/docker-compose.yml`
- 环境变量样板：`deploy/medium/.env.example`
- PostgreSQL 初始化：`sql/tiers/medium/medium-init.sql`
- Nacos 导入：`sql/tiers/medium/medium-nacos-derby-import.sql`

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
14. UI

对外端口：

- PostgreSQL `25432`
- Redis `26379`
- Nacos `28848`
- RustFS `29000`
- RustFS Console `29001`
- Gateway `29090`
- Auth `29200`
- System `29201`
- Tenant `29202`
- Workflow `29203`
- Job `29204`
- Open `29205`
- File `29207`
- UI `3200`
