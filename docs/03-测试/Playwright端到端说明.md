# Playwright端到端说明

## 目标

- 统一 Han UI 的 E2E 执行方式
- 用真实浏览器覆盖登录、菜单、通知、租户、OSS、AI 等关键链路
- 页面级健康回归必须检测内部接口错误，而不是只看页面壳子

## 目录

```text
han-ui/
  tests/e2e/
  output/playwright/
```

## 推荐执行

```bash
cd han-ui
pnpm build
pnpm test:e2e
```

## 关键约束

- 默认使用 `PW_WORKERS=1`
- 每个 spec 建议使用独立输出目录，避免 artifact 冲突
- 页面级健康回归必须捕获 HTTP 错误、业务错误、页面异常和控制台异常
