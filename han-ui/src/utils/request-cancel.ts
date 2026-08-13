import { getCurrentInstance, onBeforeUnmount } from 'vue'

/**
 * 可取消请求的组合式封装。
 *
 * 解决两个问题：
 * 1. 快速翻页 / 连点切换页签时，先发出的慢请求返回后仍会写入组件状态，造成数据错乱；
 * 2. 组件卸载后回调继续执行，触碰已销毁的 ref。
 *
 * 用法：
 * ```ts
 * const { nextSignal } = useCancelableRequest()
 *
 * async function loadList() {
 *   // 每次调用自动取消上一次未完成的同类请求
 *   const res = await listUsers(query, { signal: nextSignal() })
 *   rows.value = res.data.rows
 * }
 * ```
 *
 * 被取消的请求在 `utils/request.ts` 的错误拦截器里会被识别为主动取消，
 * 不打日志也不弹提示，调用方按需要自行 catch 即可。
 */
export function useCancelableRequest() {
  let controller: AbortController | null = null

  /** 取消上一次请求并返回一个新的 signal。 */
  function nextSignal(): AbortSignal {
    abort()
    controller = new AbortController()
    return controller.signal
  }

  /** 主动取消当前请求。 */
  function abort(): void {
    controller?.abort()
    controller = null
  }

  if (getCurrentInstance()) {
    onBeforeUnmount(abort)
  }

  return { nextSignal, abort }
}
