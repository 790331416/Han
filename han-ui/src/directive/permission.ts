import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限指令。
 *
 * 用法（参数同时支持字符串与字符串数组，三个前端组统一按数组写）：
 *   <el-button v-hasPermi="['system:user:add']">新增</el-button>
 *   <el-button v-hasPermi="'system:user:add'">新增</el-button>
 *   <el-button v-hasPermi.all="['system:user:edit', 'system:user:query']">编辑</el-button>
 *
 * 默认语义是「任一命中即放行」，加 `.all` 修饰符后要求全部命中。
 *
 * 降级约定：权限点种子（S-14）补齐前，`sql/tiers` 里缺少 `:add` / `:edit` 这类 F 型菜单，
 * 直接强制会让所有非超管角色丢失写操作入口，属于功能缩水。因此默认走宽松模式：
 * 命中失败只在控制台告警、不摘除元素；种子补齐后把 `VITE_PERMISSION_ENFORCE` 置为 `true`
 * 即可全局切到强制模式，前端页面无需改动。
 */
const ENFORCE_PERMISSION = import.meta.env.VITE_PERMISSION_ENFORCE === 'true'

/** 同一个权限点只告警一次，避免列表页每行按钮刷屏。 */
const warnedCodes = new Set<string>()

function normalizeCodes(value: unknown): string[] {
  if (typeof value === 'string') {
    return value.trim() ? [value.trim()] : []
  }
  if (Array.isArray(value)) {
    return value.filter((item): item is string => typeof item === 'string' && !!item.trim()).map((item) => item.trim())
  }
  return []
}

function warnOnce(message: string, key: string): void {
  if (warnedCodes.has(key)) {
    return
  }
  warnedCodes.add(key)
  console.warn(message)
}

function removeElement(el: HTMLElement): void {
  el.parentNode?.removeChild(el)
}

/**
 * 统一的指令判定：返回 true 表示保留元素。
 *
 * 判定顺序刻意做成「未知状态一律放行」：
 * 1. 没写权限点是调用方笔误，不应该静默吞掉按钮；
 * 2. 用户资料还没拉回来时权限清单必然为空，此时隐藏会造成整页按钮消失。
 */
function resolveVisible(
  binding: DirectiveBinding,
  identityLoaded: boolean,
  matcher: (code: string) => boolean,
  label: string
): boolean {
  const codes = normalizeCodes(binding.value)
  if (codes.length === 0) {
    warnOnce(`[${label}] 指令缺少权限标识，已按可见处理：${String(binding.value)}`, `${label}:empty`)
    return true
  }

  if (!identityLoaded) {
    return true
  }

  const requireAll = binding.modifiers.all === true
  const matched = requireAll ? codes.every(matcher) : codes.some(matcher)
  if (matched) {
    return true
  }

  if (!ENFORCE_PERMISSION) {
    warnOnce(
      `[${label}] 当前账号缺少 ${codes.join(requireAll ? ' + ' : ' / ')}，` +
        '宽松模式下仍然渲染。权限点种子补齐后请打开 VITE_PERMISSION_ENFORCE。',
      `${label}:${codes.join('|')}`
    )
    return true
  }

  return false
}

/**
 * `v-hasPermi`：按权限标识控制元素是否渲染。
 */
export const hasPermi: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const visible = resolveVisible(
      binding,
      !!userStore.userInfo,
      (code) => userStore.hasPermission(code),
      'v-hasPermi'
    )
    if (!visible) {
      removeElement(el)
    }
  }
}

/**
 * `v-hasRole`：按角色标识控制元素是否渲染，语义与 `v-hasPermi` 一致。
 */
export const hasRole: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const visible = resolveVisible(
      binding,
      !!userStore.userInfo,
      (code) => userStore.hasRole(code),
      'v-hasRole'
    )
    if (!visible) {
      removeElement(el)
    }
  }
}
