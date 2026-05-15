# 验证与交付规则

## 1. 文件定位

- 本文件用于约束测试、验证、交付、审计和结果表达。
- 任何准备汇报完成、已修复、已定位或已交付时，都必须读取本文件。

## 2. 测试分层要求

- 按风险设计单元测试、集成测试、端到端测试、回归测试、性能测试、安全测试。
- 新功能必须补测试；Bug 修复必须补能复现与防回归的测试。
- 必须覆盖正常路径、边界路径、错误路径、恢复路径、兼容路径。
- 文档变更也要做最基本的一致性、编码、路径引用和命令可用性检查。

## 3. Han 常用验证入口

- 结构检查：
  - `python scripts/checks/check_repo_layout.py`
  - `python scripts/checks/check_doc_layout.py`
  - `python scripts/checks/check_sql_layout.py`
  - `python scripts/checks/check_no_generated_files.py`
  - `python scripts/checks/check_deploy_layout.py`
- 后端最小编译：
  - `mvn -gs settings.workspace.xml -DskipTests compile`
- 前端最小构建：
  - `cd han-ui`
  - `pnpm build`
- 页面级健康回归以 `docs/04-测试与验收手册.md` 为准。

## 4. 验证要求

- 不允许只跑正常路径就报完成。
- 不允许跳过失败测试而不说明。
- 不允许只看代码不看实际运行结果。
- 验证记录必须包含执行命令、样例输入、结果摘要、未覆盖项、阻塞项。
- 如果某项无法验证，必须说明为什么无法验证、潜在风险是什么。

## 5. 交付内容最低要求

- 目标。
- 改动。
- 验证。
- 风险。
- 未完成项。
- 后续建议。

Han 仓库 `AGENTS.md` 要求交付时必须至少说明：改了什么、验证了什么、还有什么未验证或残留风险。

## 6. 证据表达要求

- 所有完成都要有对应证据。
- 所有未完成都要有明确原因。
- 所有高风险点都要有显式提示。
- 修改过的文件、相关命令、测试结果、部署结果要可追溯。

## 7. 文档与规则改动的额外验证

- 检查文件编码是否正确。
- 检查路径引用是否存在。
- 检查规则索引与路径级入口是否一致。
- 检查 `docs/index.md` 是否需要同步。
- 检查结构检查脚本白名单是否需要同步。

## 8. 交付前流程钩子

- 交付前读取 `.codex/hooks/post-delivery-checklist.md`。
- 需要归档经验时，同步更新 `.codex/memory/learned-rules.md` 或 `.codex/memory/evolution-log.md`。
