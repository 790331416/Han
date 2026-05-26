# MVP2 场景图生成开发方案确认稿

版本：v0.1
状态：待 BOSS 确认；未进入代码实现
更新时间：2026-05-26
适用底座：`D:\code\Han`
分支建议：继续使用 `feature/aivideo-mvp1-text`，后续可按阶段重命名或新建 `feature/aivideo-mvp2-scene-image`

## 1. 本轮任务分流

| 项 | 结论 |
| --- | --- |
| 任务类型 | MVP2 场景图生成开发方案拆解 |
| 当前角色 | 规划角色 |
| 是否写代码 | 否 |
| 是否改 SQL | 否 |
| 是否部署 | 否 |
| 是否需要 BOSS 再确认 | 是，确认后才进入代码实现 |

## 2. 开发目标

第一段只做“单场景生成候选图，默认 2 选 1”：
- 用户在工作台“资产 > 场景”选择一个已提取场景。
- 查看本次场景图 Prompt。
- 可填写补充提示词。
- 调用 Han AI 图片模型能力生成 1 到 4 张候选图，默认 2 张。
- 每张候选图必须上传到 `han-file` 后再入库。
- 用户选择一张候选图后，锁定为该场景的场景图。

本轮不做角色图、分镜图、整剧批量图、视频生成。

## 3. 已确认设计决策

| 决策 | 实现口径 |
| --- | --- |
| 生成范围 | 只做单场景 |
| 候选数 | 默认 2，限制 1 到 4 |
| 场景主体 | 强制纯场景、无人、无人物剪影 |
| 图片模型 | 通过 Han AI 模型配置页录入，`model_type = IMAGE` |
| API Key | 可与文本模型复用同一火山方舟 Key，但必须做图片模型连通测试 |
| Prompt 模板 | 新增 `scene_image_prompt_template_id` 到 `ai_video_project_setting` |
| 图片归档 | 必须上传到 `han-file`，不做 URL-only 兜底 |
| 输出方式 | 后端统一 SSE，避免普通请求超时 |

## 4. 火山 Key 与模型配置确认

### 4.1 同 Key 规则

文本和图片可以使用同一个火山方舟 API Key，前提是：
- 图片模型和文本模型属于同一火山方舟账号或项目空间。
- API Key 权限没有只限制到文本模型或文本接入点。
- `model_code` / 接入点 ID 填的是图片模型对应标识。
- Base URL 使用火山方舟 OpenAI-compatible 地址，例如当前文本链路使用过的 `https://ark.cn-beijing.volces.com/api/v3`。

开发门禁：
- 在写完图片客户端后，必须单独执行 `model_type = IMAGE` 的真实连通测试。
- 测试结果必须记录：模型 ID、Base URL、凭据来源、是否同 Key、返回形态、是否成功。
- 测试失败时，不继续往 UI 宣称“图片生成可用”，只展示明确的配置缺失或权限不足提示。

### 4.2 AI 模型页建议录入项

| 字段 | 建议 |
| --- | --- |
| 供应商 | `volcengine` |
| 模型类型 | `IMAGE` |
| 模型标识 | 火山图片模型或接入点 ID |
| Base URL | `https://ark.cn-beijing.volces.com/api/v3` |
| API Key | 可复用开发 Key，也可为图片单独创建 |
| 状态 | `正常` |

## 5. SQL 开发方案

### 5.1 升级脚本

新增升级脚本：

```text
sql/upgrades/postgres/20260526_aivideo_mvp2_scene_image.sql
```

脚本内容建议：
- `ai_video_project_setting` 新增 `scene_image_prompt_template_id bigint`。
- 补充字段注释。
- 插入或更新 `AI短剧场景图生成` Prompt 模板。
- 将全局默认项目设置的场景图模板 ID 绑定到该模板。
- 新建项目默认 `image_candidate_count` 改为 2。

### 5.2 full 初始化

同步更新：

```text
sql/tiers/full/full-init.sql
```

要求：
- 新库直接具备 `scene_image_prompt_template_id` 字段。
- 默认 Prompt 模板包含场景图生成模板。
- 默认图片候选数为 2。

### 5.3 已有项目候选数

推荐口径：
- 新建项目默认 2。
- 全局默认配置改为 2。
- 已有项目如果用户已经保存过参数，不强制批量改，避免覆盖用户设置。
- 当前测试项目如需从 3 改 2，可在部署时单独执行一次针对项目设置的更新 SQL。

待 BOSS 确认：是否把现有项目也统一改为 2。

## 6. 后端开发方案

### 6.1 `han-api-ai`

新增或补齐内部图片生成契约：
- `AiImageGenerateRequest`
- `AiImageGenerateResponse`
- `AiImageCandidate`
- `AiImagePromptPreview` 如已有通用结构可复用

要求：
- 不暴露 API Key。
- 响应里只返回可归档的图片信息，例如临时 URL、base64、mimeType、providerTaskId。

### 6.2 `han-ai`

新增图片模型调用能力：
- 图片模型配置读取：复用现有 AI 模型配置与凭据解析。
- 火山图片客户端：优先兼容 OpenAI-compatible 图片生成接口。
- 支持返回形态：
  - URL
  - base64
  - 异步任务结果
- 统一错误脱敏：
  - 不打印完整 Key。
  - 不打印完整 Authorization。
  - 明确返回模型未配置、凭据缺失、权限不足、供应商超时等业务错误。

建议文件边界：
- `han-modules/han-ai/.../image/*`
- `han-api/han-api-ai/.../image/*`

### 6.3 `han-aivideo`

新增场景图业务编排：
- 读取项目与场景。
- 校验场景属于项目。
- 校验图片候选数 1 到 4。
- 合成场景图 Prompt。
- 创建 `SCENE_IMAGE` 生成任务。
- 调用 `han-ai` 图片生成。
- 将生成图片上传到 `han-file`。
- 写入 `ai_video_media_asset`。
- 通过 SSE 返回生成进度、上传进度、候选图、完成事件。

建议新增或补齐对象：
- `AiVideoMediaAssetPo`
- `AiVideoMediaAssetMapper`
- `AivideoMediaService`
- `AivideoSceneImageGenerateDto`
- `AivideoMediaSelectDto`
- `AivideoMediaAssetVo`
- `AivideoSceneImagePromptPreviewVo`

### 6.4 `han-file`

第一版不改 `han-file` 服务本体，优先复用已有上传接口或内部文件客户端。

实现要求：
- 服务端下载火山返回的图片 URL 时要设置超时和大小限制。
- base64 返回时直接转字节流上传。
- 上传成功后必须拿到 `file_id` 和可访问 `file_url`。
- 上传失败则该候选图标记失败，不进入 `READY`。

### 6.5 防重复

保留并增强：
- 前端按钮 loading 防重。
- 后端同一 `projectId + sceneId + taskType=SCENE_IMAGE + RUNNING` 幂等检查。
- 选择候选图时使用 `projectId + sceneId + mediaId` 级别校验，避免不同场景误判重复提交。

## 7. 前端开发方案

### 7.1 工作台资产页

在 `han-ui` 工作台“资产 > 场景”增加：
- 场景图状态列。
- 已选图缩略图。
- `生成场景图` 按钮。
- `候选图` 按钮。
- `查看场景图 Prompt` 展开区。

### 7.2 候选图选择器

建议新增组件：

```text
han-ui/src/views/studio/components/SceneImageCandidatePanel.vue
```

能力：
- 左侧显示场景摘要。
- 中间显示默认 2 张候选图。
- 右侧显示 Prompt、模型、参数、错误信息。
- 支持选择候选图。
- 支持重新生成。
- 支持刷新候选列表。

### 7.3 SSE 交互

前端调用：

```text
POST /aivideo/studio/media/scene/generate/stream
```

事件建议：
- `meta`：任务、模型、候选数、参数。
- `prompt`：本次最终 Prompt。
- `candidate`：单张图片候选产生。
- `upload`：上传 `han-file` 结果。
- `done`：全部完成。
- `error`：失败信息。

## 8. Prompt 模板草案

模板目标：生成电影级纯净场景图，不出现人物。

关键约束：
- 只输出环境与空间，不出现人、动物、角色剪影、人形轮廓。
- 不出现文字、水印、Logo、字幕。
- 场景应适合后续图生视频，构图干净，空间层次明确。
- 使用场景资产里的视觉特征、氛围、道具、色彩和禁用元素。

模板变量：
- `projectName`
- `targetPlatform`
- `ratio`
- `resolution`
- `style`
- `sceneName`
- `timeDesc`
- `weather`
- `atmosphere`
- `visualFeatures`
- `colorTone`
- `props`
- `negativeElements`
- `scenePromptText`
- `customPrompt`

## 9. 验证计划

### 9.1 本地验证

- `mvn -gs settings.workspace.xml -pl han-api/han-api-ai,han-modules/han-ai,han-modules/han-aivideo -am -DskipTests compile`
- `cd han-ui && pnpm build`
- SQL 布局检查脚本通过。
- 图片模型配置缺失时，页面提示明确。
- 图片模型配置存在时，真实连通测试通过。
- 生成默认 2 张候选图。
- 生成图必须上传到 `han-file`。
- 入库记录 `file_id` 非空。
- 选择候选图后刷新页面仍能看到已选图。
- 同一场景重复点击不会重复提交。
- 不同场景点击不会被误判重复提交。

### 9.2 双机验证

- GitHub Actions 触发 `han-ai`、`han-aivideo`、`han-ui` 镜像构建。
- `tengx2` 拉取新镜像并重建相关容器。
- 公网页面 `http://124.223.116.125:3000/studio/projects/2/workbench` 可访问。
- BOSS 页面实测一个场景默认生成 2 张候选图，选择 1 张，刷新后仍保留。

## 10. 风险与处理

| 风险 | 影响 | 处理 |
| --- | --- | --- |
| 图片模型 ID 未配置 | 不能真实生成 | UI 显示配置缺失；后端返回可理解错误 |
| 同 Key 权限不足 | 文本可用，图片失败 | 图片模型单独连通测试，提示检查 API Key 权限 |
| 图片生成返回临时 URL | URL 过期 | 服务端立即上传 `han-file` |
| 上传 `han-file` 失败 | 图片不可持久访问 | 本次候选失败，不写 READY |
| 图片生成耗时长 | 页面超时 | 统一 SSE，必要时后续接 `han-job` |
| 成本误触 | 多次扣费 | 前后端双重防重复，候选数限制 |

## 11. 回滚方案

- 前端隐藏场景图入口。
- 停用 `AI短剧场景图生成` Prompt 模板。
- 将项目设置里的 `scene_image_prompt_template_id` 置空。
- 禁用 `model_type = IMAGE` 的模型配置。
- 已有媒体候选不删除，只标记未选中或 `DISCARDED`，不影响文字资产。
- SQL 字段保留，不做破坏性回滚。

## 12. 需要 BOSS 最终确认

1. 是否允许新增 `scene_image_prompt_template_id` 字段和对应升级 SQL。
2. 是否按推荐口径：新建项目和全局默认候选数改为 2，已有项目不批量覆盖。
3. 是否由 BOSS 先在 Han AI 模型页新增一条 `model_type = IMAGE` 的火山图片模型配置。
4. 是否允许开发确认后进入代码实现、构建、推送和双机部署。
