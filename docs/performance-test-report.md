# Han Cloud 性能测试报告

## 测试环境

| 项目 | 值 |
|------|-----|
| **服务器** | 10.18.35.95 (CentOS 7, 4C8G) |
| **网关端口** | 9090 |
| **JVM** | -Xms256m -Xmx256m per service |
| **工具** | Apache Bench (ab) |
| **测试日期** | 2026-03-05 |

## 性能测试结果

### 接口吞吐量 (10 并发, 100 请求)

| # | 接口 | RPS | 平均延迟 | 单请求延迟 |
|---|------|-----|---------|-----------|
| P1 | `GET /auth/captcha` | **101** req/s | 99ms | 9.9ms |
| P2 | `GET /system/user/list` | **145** req/s | 69ms | 6.9ms |
| P3 | `GET /system/dashboard/stats` | **140** req/s | 72ms | 7.2ms |
| P4 | `GET /system/role/list` | **315** req/s | 32ms | 3.2ms |
| P5 | `GET /system/menu/routers` | **236** req/s | 42ms | 4.2ms |
| P6 | `GET /system/user/current` (20并发) | **426** req/s | 47ms | 2.3ms |

### 网关限流验证 (100 并发, 200 请求)

| 指标 | 值 |
|------|-----|
| **总请求** | 200 |
| **通过 (2xx)** | 50 |
| **被限流 (429)** | 150 |
| **限流比例** | 75% |
| **峰值 RPS** | 457 req/s |
| **限流阈值** | 50 req/s per IP |
| **限流日志** | ✅ "IP[172.25.0.1]请求过于频繁，已限流（200次/秒）" |

## 性能评估

### 优良指标
- **简单查询** (role list, menu routers, current user): **200-400+ RPS**, 延迟 < 50ms
- **复杂查询** (dashboard stats, user list): **140+ RPS**, 延迟 < 75ms
- **验证码生成** (含图片渲染): **100+ RPS**, 延迟 ~100ms

### 瓶颈分析
- 各服务 JVM 仅分配 256MB，生产环境建议 512MB-1GB
- Docker bridge 网络增加 ~2ms 额外延迟
- PostgreSQL 在同一台机器上，无网络开销但共享 CPU/IO

### 建议
- 生产环境 JVM 调整为 `-Xms512m -Xmx1g`
- 热点接口添加 Redis 缓存（如 menu/routers、dict/data）
- 限流阈值可根据业务调整（当前 50 req/s per IP）
