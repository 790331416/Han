# Han

Han 是一个按 `small / medium / full` 三档部署组织的微服务平台仓库。当前正式入口已经统一收口到：

- 文档入口：`docs/`
- SQL 入口：`sql/`
- 部署入口：`deploy/`
- 结构校验：`scripts/checks/`

## 快速导航

- [文档索引](./docs/index.md)
- [仓库全局整理与95重建执行计划](./docs/00-%E6%B2%BB%E7%90%86/%E4%BB%93%E5%BA%93%E5%85%A8%E5%B1%80%E6%95%B4%E7%90%86%E4%B8%8E95%E9%87%8D%E5%BB%BA%E6%89%A7%E8%A1%8C%E8%AE%A1%E5%88%92.md)
- [牛马协作规则](./docs/00-%E6%B2%BB%E7%90%86/%E7%89%9B%E9%A9%AC%E5%8D%8F%E4%BD%9C%E8%A7%84%E5%88%99.md)
- [能力矩阵](./docs/01-%E6%9E%B6%E6%9E%84/%E8%83%BD%E5%8A%9B%E7%9F%A9%E9%98%B5.md)
- [测试总账](./docs/03-%E6%B5%8B%E8%AF%95/%E6%B5%8B%E8%AF%95%E6%80%BB%E8%B4%A6.md)
- [95部署指南](./docs/04-%E9%83%A8%E7%BD%B2/95%E9%83%A8%E7%BD%B2%E6%8C%87%E5%8D%97.md)
- [SQL 说明](./sql/README.md)

## 部署入口

- `small`：`deploy/small/docker-compose.yml`
- `medium`：`deploy/medium/docker-compose.yml`
- `full`：`deploy/full/docker-compose.yml`

## 规则摘要

- `master` 是唯一长期分支
- 正式文档只允许进入 `docs/`
- 正式 SQL 只允许进入 `sql/`
- 正式部署只允许进入 `deploy/`
- 95 只允许从 `/opt/han/repo/Han` 与 `/opt/han/deploy/{small,medium,full}` 发布
- 禁止提交 `han-ui/dist`、缓存目录、压缩包、日志和测试输出

## 历史材料

旧的阶段性验证、流程图、分析稿和过往部署说明已经迁入 `docs/归档/`。如需追溯历史过程，请从归档目录进入，不再从仓库根目录或 `doc/` 查找。
