# MVP2 场景图发布触发记录

更新时间：2026-05-26

## 目标

- 将 MVP2 场景图候选生成链路打包为线上镜像。
- 覆盖服务：`han-ai`、`han-aivideo`、`han-file`、`han-ui`。
- 发布方式：GitHub Actions 打包并推送 ACR，腾讯云 `tengx2` 拉取镜像后滚动更新相关容器。

## 当前业务提交

- `f1877ad feat: add aivideo scene image workflow`

## 触发方式

- 普通 git push 触发 `ai-image`、`aivideo-image`、`full-app-image`。
- 本次只补充 workflow 注释和发布记录，用于产生可追踪的发布触发提交。
- 不改业务代码，不调整 SQL 结构，不写入任何密钥。

## 回滚方式

- 服务端镜像标签切回上一版，并重启对应容器。
- 必要时 revert 本发布触发提交。
- SQL 升级脚本为幂等新增字段和模板；回滚前需确认是否已有场景图业务数据。

## 待验证

- GitHub Actions 是否生成并推送新镜像。
- `tengx2` 是否拉取新镜像并恢复 healthy。
- 场景图生成是否能调用 IMAGE 模型、上传到 `han-file`，并在工作台候选图抽屉中 2 选 1。

## 2026-05-26 发布修正

- 发现 `han-file:065cfc3` 启动失败：新增 `JdbcTemplate` 后，Docker 配置缺少 PostgreSQL datasource。
- 修正方式：给 `han-file` 的 `application-docker.yml` 补充数据库连接配置，保持运行时从 `.env` 注入真实环境变量。
- 临时处置：线上 `han-file` 已先回滚到本地旧 `latest` 保持健康，其它 `han-ai`、`han-aivideo`、`han-ui` 继续运行 `065cfc3`。
