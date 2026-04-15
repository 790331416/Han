# Han Cloud 能力矩阵

## 总览

Han Cloud 当前统一使用 `HAN_DEPLOY_TIER=small|medium|full` 表示部署层级，服务间内部调用统一使用 `HAN_INNER_AUTH_SECRET` 做签名校验。前端会优先读取 `/system/runtime/capabilities`，按后端返回的真实层级和可选中间件状态进行降级展示。

## 三档部署

| Tier | Compose 文件 | 基础中间件 | 默认业务服务 | 可选/增强能力 |
|------|--------------|------------|--------------|---------------|
| `small` | `docker-compose-small.yml` | PostgreSQL、Redis、Nacos | gateway、auth、system、job | 仅保留用户中心、公告通知、参数配置、基础监控 |
| `medium` | `docker-compose.yml` | `small` 全部能力 + RustFS + RabbitMQ | tenant、workflow、open、file | 多租户、工作流、开放平台、文件服务；代码生成器可按需单独启用 |
| `full` | `docker-compose-full.yml` | `medium` 全部能力 | ai | Kafka、Elasticsearch、Prometheus/Grafana/ELK 建议按环境按需外挂，不强绑在默认 compose 里 |

## 运行时规则

| 主题 | 规则 |
|------|------|
| 部署层级 | 所有服务共享同一个 `HAN_DEPLOY_TIER` |
| 内部鉴权 | 所有需要内部 RPC 的服务共享同一个 `HAN_INNER_AUTH_SECRET` |
| 前端菜单 | 先看后端菜单，再叠加 `/system/runtime/capabilities` 做层级过滤 |
| 可选中间件 | 通过环境变量和配置探测，不要求每个 tier 强行装满全部组件 |
| 代码生成器 | 当前作为可选能力保留，建议通过显式开关单独启用 |

## 默认端口

| 服务 | 端口 |
|------|------|
| gateway | `8080` |
| auth | `9200` |
| system | `9201` |
| tenant | `9202` |
| workflow | `9203` |
| job | `9204` |
| open | `9205` |
| file | `9207` |
| ai | `9208` |

## 推荐校验

```bash
curl http://localhost:9090/system/runtime/capabilities
curl http://localhost:9090/auth/captcha
```

返回结果中至少应核对：

- `tier` 是否和部署目标一致
- `enabledModules` 是否覆盖当前 tier 的默认模块
- `optionalServices` 是否正确识别 RustFS、RabbitMQ、Kafka、Elasticsearch
