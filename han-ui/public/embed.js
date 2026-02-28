/**
 * HAN Cloud AI 嵌入式对话 JS SDK
 *
 * 使用方式:
 * <script src="https://your-domain/embed.js" data-agent-id="1" data-base-url="https://your-domain"></script>
 *
 * 配置项 (data-* 属性):
 *   data-agent-id    - 智能体ID (必填)
 *   data-base-url    - 后端地址 (默认当前域名)
 *   data-position    - 按钮位置 right|left (默认 right)
 *   data-color       - 主题色 (默认 #409eff)
 *   data-title       - 标题 (默认 AI助手)
 *   data-width       - 宽度 (默认 420px)
 *   data-height      - 高度 (默认 600px)
 */
;(function () {
  'use strict'

  var script = document.currentScript
  if (!script) return

  var agentId = script.getAttribute('data-agent-id')
  if (!agentId) { console.error('[HAN Embed] data-agent-id is required'); return }

  var baseUrl = script.getAttribute('data-base-url') || window.location.origin
  var position = script.getAttribute('data-position') || 'right'
  var color = script.getAttribute('data-color') || '#409eff'
  var title = script.getAttribute('data-title') || 'AI助手'
  var width = script.getAttribute('data-width') || '420px'
  var height = script.getAttribute('data-height') || '600px'

  // 创建悬浮按钮
  var btn = document.createElement('div')
  btn.id = 'HAN-embed-btn'
  btn.innerHTML = '<svg viewBox="0 0 24 24" width="28" height="28" fill="white"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/></svg>'
  btn.style.cssText = 'position:fixed;bottom:24px;' + position + ':24px;width:56px;height:56px;border-radius:50%;background:' + color + ';display:flex;align-items:center;justify-content:center;cursor:pointer;box-shadow:0 4px 16px rgba(0,0,0,0.2);z-index:99999;transition:transform 0.2s;'
  btn.onmouseenter = function () { btn.style.transform = 'scale(1.1)' }
  btn.onmouseleave = function () { btn.style.transform = 'scale(1)' }

  // 创建对话窗口容器
  var container = document.createElement('div')
  container.id = 'HAN-embed-container'
  container.style.cssText = 'position:fixed;bottom:90px;' + position + ':24px;width:' + width + ';height:' + height + ';border-radius:16px;overflow:hidden;box-shadow:0 8px 32px rgba(0,0,0,0.15);z-index:99998;display:none;background:#fff;'

  // iframe 加载嵌入页面
  var iframe = document.createElement('iframe')
  iframe.src = baseUrl + '/embed/chat/' + agentId + '?fullscreen=1'
  iframe.style.cssText = 'width:100%;height:100%;border:none;'
  container.appendChild(iframe)

  // 切换显示/隐藏
  var visible = false
  btn.onclick = function () {
    visible = !visible
    container.style.display = visible ? 'block' : 'none'
    btn.innerHTML = visible
      ? '<svg viewBox="0 0 24 24" width="28" height="28" fill="white"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>'
      : '<svg viewBox="0 0 24 24" width="28" height="28" fill="white"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/></svg>'
  }

  document.body.appendChild(container)
  document.body.appendChild(btn)
})()
