# full 初始化顺序

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

`aivideo`（容器端口 `9209`）挂在 `aivideo` compose profile 下，默认不参与启动，
不进入本初始化顺序；启用方式见 `deploy/full/README.md`。

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
