import type { App } from 'vue'
import { hasPermi, hasRole } from './permission'

/**
 * 全局指令注册入口。
 *
 * 新增指令统一在这里挂载，避免 `main.ts` 逐条堆叠 `app.directive(...)`。
 */
export function setupDirectives(app: App): void {
  app.directive('hasPermi', hasPermi)
  app.directive('hasRole', hasRole)
}

export { hasPermi, hasRole }
