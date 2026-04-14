/**
 * 安全水印 — Canvas 绘制 + MutationObserver 防删
 *
 * 使用方式：
 *   import { useWatermark } from '@/utils/watermark'
 *   const { set, clear } = useWatermark()
 *   set('admin 2026-03-05')   // 设置水印
 *   clear()                    // 清除水印
 */

let watermarkEl: HTMLElement | null = null
let observer: MutationObserver | null = null
let currentText = ''

function createWatermarkCanvas(text: string): string {
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')!
  const dpr = window.devicePixelRatio || 1
  const isDark = document.documentElement.classList.contains('dark')

  canvas.width = 300 * dpr
  canvas.height = 200 * dpr
  ctx.scale(dpr, dpr)

  ctx.rotate((-20 * Math.PI) / 180)
  ctx.font = '14px Inter, -apple-system, sans-serif'
  ctx.fillStyle = isDark ? 'rgba(255, 255, 255, 0.04)' : 'rgba(0, 0, 0, 0.04)'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, 150, 120)

  return canvas.toDataURL('image/png')
}

function createWatermarkDom(dataUrl: string): HTMLElement {
  const el = document.createElement('div')
  el.setAttribute('data-watermark', 'true')
  el.style.cssText = `
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    pointer-events: none;
    z-index: 99999;
    background-repeat: repeat;
    background-image: url(${dataUrl});
  `
  return el
}

function startObserver() {
  if (observer) observer.disconnect()

  observer = new MutationObserver((mutations) => {
    for (const m of mutations) {
      // 水印节点被删除 → 重新插入
      if (m.type === 'childList') {
        for (const removed of m.removedNodes) {
          if (removed === watermarkEl) {
            document.body.appendChild(watermarkEl!)
            return
          }
        }
      }
      // 水印节点属性被修改 → 重建
      if (m.type === 'attributes' && m.target === watermarkEl) {
        clear()
        set(currentText)
        return
      }
    }
  })

  observer.observe(document.body, {
    childList: true,
    subtree: false
  })

  if (watermarkEl) {
    observer.observe(watermarkEl, {
      attributes: true,
      attributeFilter: ['style', 'class']
    })
  }
}

export function set(text: string) {
  if (!text) return
  currentText = text

  // 必须先断开 observer，再移除旧水印，否则 observer 会检测到移除并重新插入旧节点
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (watermarkEl && watermarkEl.parentNode) {
    watermarkEl.parentNode.removeChild(watermarkEl)
  }

  const dataUrl = createWatermarkCanvas(text)
  watermarkEl = createWatermarkDom(dataUrl)
  document.body.appendChild(watermarkEl)
  startObserver()
}

export function clear() {
  if (observer) {
    observer.disconnect()
    observer = null
  }
  if (watermarkEl && watermarkEl.parentNode) {
    watermarkEl.parentNode.removeChild(watermarkEl)
  }
  watermarkEl = null
  currentText = ''
}

export function useWatermark() {
  return { set, clear }
}
