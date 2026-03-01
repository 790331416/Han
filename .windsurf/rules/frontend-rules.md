---
trigger: always_on
---
# Han Cloud 前端开发规范

## 技术栈（强制锁定）

| 技术 | 版本 | 用途 |
|------|------|------|
| **Vue** | 3.5.x | 核心框架 |
| **Vite** | 6.x | 构建工具 |
| **TypeScript** | 5.7.x | 类型系统 |
| **Element Plus** | 2.9.x | UI 组件库 |
| **UnoCSS** | 66.x | 原子化 CSS 引擎 |
| **VueUse** | 14.x | 组合式工具库 |
| **Pinia** | 2.3.x | 状态管理 |
| **Vue Router** | 4.x | 路由管理 |
| **Axios** | 1.7.x | HTTP 客户端 |
| **pnpm** | 10.x | 包管理器（**强制**） |

---

## 包管理器规则

- **必须使用 pnpm**，禁止 npm / yarn
- 安装依赖：`pnpm install`
- 添加依赖：`pnpm add <pkg>` / `pnpm add -D <pkg>`
- 启动开发：`pnpm dev`（端口 3000）
- 构建生产：`pnpm build`
- lockfile：`pnpm-lock.yaml`（禁止提交 `package-lock.json` 或 `yarn.lock`）

---

## UI 设计规范 — 现代极简白

### 设计原则
- **Notion / Linear 风格**：纯白背景、轻量边框、柔和阴影、蓝色主色调
- 侧边栏：白色背景 + 1px 右边框 (`#f3f4f6`)
- 导航栏：白色背景 + 1px 底边框
- 内容区：`#f9fafb` 浅灰背景
- 卡片：白色 + `border-radius: 12px` + 极浅阴影

### 色彩体系

| 用途 | 色值 | 说明 |
|------|------|------|
| 主色 | `#2563eb` | 按钮、链接、活跃态 |
| 主色深 | `#1d4ed8` | hover 态 |
| 主色浅 | `#eff6ff` | 活跃菜单背景 |
| 标题文字 | `#111827` | 一级文字 |
| 正文文字 | `#374151` | 二级文字 |
| 辅助文字 | `#6b7280` | 标签、描述 |
| 占位符 | `#9ca3af` | placeholder |
| 边框 | `#e5e7eb` | 输入框边框 |
| 分割线 | `#f3f4f6` | 卡片边框、分割线 |
| 页面背景 | `#f9fafb` | 内容区背景 |

### 圆角规范
- 卡片/对话框：`12px` ~ `16px`
- 按钮/输入框：`8px`
- 小标签/badge：`6px`
- Logo 图标：`8px`

### 字体
- 主字体：`Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif`
- 字号：`14px` 基准
- 标题：`font-weight: 700`，`letter-spacing: -0.02em`
- 正文：`font-weight: 400` ~ `500`

---

## 样式编写规则

### 优先级
1. **UnoCSS 原子类**：用于布局和间距（`flex`, `gap-4`, `p-5`, `rounded-xl` 等）
2. **Scoped SCSS**：用于组件特有样式
3. **全局 SCSS** (`index.scss`)：仅用于 Element Plus 变量覆盖和全局 reset
4. 禁止内联 style（除动态绑定外）

### UnoCSS 快捷方式（已配置）
```
card       → bg-white rounded-xl shadow-sm border border-gray-100
card-header → px-5 py-4 border-b border-gray-100 font-medium text-gray-800
card-body  → p-5
page-container → p-5 space-y-4
btn-icon   → inline-flex items-center justify-center w-8 h-8 rounded-lg hover:bg-gray-100 transition-colors cursor-pointer
```

---

## 文件结构规范

```
han-ui/src/
├── api/              # API 接口（按模块分文件）
│   ├── auth.ts
│   └── system/
│       ├── user.ts
│       ├── role.ts
│       └── ...
├── assets/
│   ├── icons/        # SVG 图标
│   └── styles/
│       └── index.scss  # 全局样式 + Element Plus 主题覆盖
├── layout/
│   ├── index.vue     # 主布局
│   └── components/
│       ├── Sidebar.vue
│       └── Navbar.vue
├── router/           # 路由配置
├── stores/           # Pinia 状态管理
├── types/            # TypeScript 类型定义
├── utils/            # 工具函数
│   └── request.ts    # Axios 封装
└── views/            # 页面视图（按模块分目录）
    ├── dashboard/
    ├── login/
    ├── system/
    │   ├── user/
    │   ├── role/
    │   └── ...
    └── job/
```

---

## API 调用规范

### 请求方式
- **仅使用 GET 和 POST**（与后端 AIB 规范一致）
- GET：查询（list / getById / tree）
- POST：写操作（add / edit / remove）

### 命名规范
```typescript
// 列表查询
export function listUser(query: UserQuery) {
  return get<PageResult<User>>('/system/user/list', query)
}

// 新增
export function addUser(data: UserForm) {
  return post<void>('/system/user', data)
}

// 修改
export function updateUser(data: UserForm) {
  return post<void>('/system/user/edit', data)
}

// 删除
export function deleteUser(userId: number) {
  return post<void>(`/system/user/remove/${userId}`)
}
```

### baseURL
- 开发环境：`http://localhost:8080`（直连 Gateway）
- 在 `.env.development` 中配置 `VITE_APP_BASE_API`

---

## 组件编写规范

### Vue SFC 结构顺序
```vue
<template>
  <!-- 模板 -->
</template>

<script setup lang="ts">
// 1. imports
// 2. props / emits
// 3. composables (useRoute, useRouter, stores)
// 4. reactive state
// 5. computed
// 6. methods
// 7. lifecycle hooks (onMounted, etc.)
</script>

<style lang="scss" scoped>
/* 组件样式 */
</style>
```

### 自动导入
以下 API 已通过 `unplugin-auto-import` 自动导入，**无需手动 import**：
- `vue`：`ref`, `reactive`, `computed`, `watch`, `onMounted` 等
- `vue-router`：`useRoute`, `useRouter`
- `pinia`：`defineStore`, `storeToRefs`
- `@vueuse/core`：`useDark`, `useToggle`, `useStorage`, `useWindowSize` 等

---

## 禁止事项

1. **禁止** 使用 npm / yarn（必须 pnpm）
2. **禁止** 使用 Tailwind CSS（已选用 UnoCSS）
3. **禁止** 在全局样式中添加过于具体的选择器
4. **禁止** 使用 `!important`（除 Element Plus 覆盖外）
5. **禁止** 在 `<script setup>` 中使用 Options API
6. **禁止** 使用 `any` 类型（除临时接口调试外）
7. **禁止** 硬编码 API 地址（必须通过 env 变量）
8. **禁止** 在组件中直接使用 `axios`（必须通过 `@/utils/request` 封装）
