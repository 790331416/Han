const allowedTags = new Set([
  'A', 'BLOCKQUOTE', 'BR', 'CODE', 'DEL', 'DIV', 'EM', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
  'HR', 'IMG', 'LI', 'OL', 'P', 'PRE', 'SPAN', 'STRONG', 'SUP', 'TABLE', 'TBODY', 'TD', 'TH',
  'THEAD', 'TR', 'UL'
])

/**
 * 整体丢弃（连子节点一起删）的标签。
 *
 * <p>除脚本类标签外，还包含 `NOSCRIPT` / `TEMPLATE` / `XMP` 这类“内容按原始文本解析”的容器 ——
 * 它们是 mXSS 的经典触发点：序列化再解析时内部文本会被重新当成标签。
 */
const dangerousTags = new Set([
  'BASE', 'EMBED', 'FRAME', 'FRAMESET', 'IFRAME', 'LINK', 'MATH', 'META', 'NOEMBED', 'NOFRAMES',
  'NOSCRIPT', 'OBJECT', 'PLAINTEXT', 'SCRIPT', 'STYLE', 'SVG', 'TEMPLATE', 'TITLE', 'XMP'
])

const allowedAttrs: Record<string, string[]> = {
  A: ['href', 'title'],
  CODE: ['class'],
  IMG: ['alt', 'height', 'src', 'title', 'width'],
  SPAN: ['class'],
  SUP: ['class', 'data-citation-index', 'title'],
  TH: ['align'],
  TD: ['align']
}

/**
 * `class` 取值白名单：只放行渲染必需的类名，避免 AI 输出注入任意 class 干扰全局样式。
 *
 * <p>`SPAN` 必须放行 `hljs-*`，否则 highlight.js 产出的着色 span 会被整体剥掉，
 * 代码块只剩深色底 + 单色文字（等于引入了 highlight.js 却没有高亮效果）。
 */
const allowedClassMatchers: Record<string, RegExp[]> = {
  CODE: [/^hljs$/, /^language-[\w+#.-]+$/],
  SPAN: [/^hljs(?:-[\w-]+)?$/],
  SUP: [/^citation-badge$/]
}

/** 净化后再解析比对的最大轮数，用于收敛 mXSS 造成的结构漂移。 */
const MAX_SANITIZE_PASSES = 3

/**
 * URL 协议白名单判定（净化策略的纯函数部分，单独导出便于回归测试）。
 *
 * <p>先剥离 `\u0000-\u0020` 控制字符再判协议，用于挡住 `jav\tascript:` 这类绕过。
 */
export function hasSafeUrl(value: string, allowImageData = false): boolean {
  const compact = value.trim().replace(/[\u0000-\u0020\u007f]+/g, '')
  if (!compact) return false
  if (compact.startsWith('/') || compact.startsWith('#')) return true
  if (allowImageData && /^data:image\/(?:png|jpe?g|gif|webp);base64,/i.test(compact)) return true
  const scheme = compact.match(/^([a-z][a-z0-9+.-]*):/i)?.[1]?.toLowerCase()
  return scheme ? ['http', 'https', 'mailto', 'tel', 'blob'].includes(scheme) : true
}

/**
 * 按标签白名单过滤 class 取值，返回保留下来的类名；全部不合法时返回空串。
 *
 * <p>净化策略的纯函数部分，单独导出便于回归测试。
 */
export function filterClassValue(tagName: string, value: string): string {
  const matchers = allowedClassMatchers[tagName]
  if (!matchers) return ''
  return value
    .split(/\s+/)
    .filter((token) => token && matchers.some((matcher) => matcher.test(token)))
    .join(' ')
}

function escapeText(text: string): string {
  const holder = document.createElement('div')
  holder.textContent = text
  return holder.innerHTML
}

function sanitizePass(html: string): string {
  const template = document.createElement('template')
  template.innerHTML = html

  for (const element of Array.from(template.content.querySelectorAll('*'))) {
    if (dangerousTags.has(element.tagName)) {
      element.remove()
      continue
    }
    if (!allowedTags.has(element.tagName)) {
      element.replaceWith(...Array.from(element.childNodes))
      continue
    }

    for (const attr of Array.from(element.attributes)) {
      const name = attr.name.toLowerCase()
      const allowed = allowedAttrs[element.tagName]?.includes(name) || false
      if (!allowed || name.startsWith('on')) {
        element.removeAttribute(attr.name)
        continue
      }
      if (name === 'class') {
        const kept = filterClassValue(element.tagName, attr.value)
        if (kept) {
          element.setAttribute('class', kept)
        } else {
          element.removeAttribute(attr.name)
        }
      }
      if (name === 'href' && !hasSafeUrl(attr.value)) {
        element.removeAttribute(attr.name)
      }
      if (name === 'src' && !hasSafeUrl(attr.value, element.tagName === 'IMG')) {
        element.removeAttribute(attr.name)
      }
    }

    if (element.tagName === 'A' && element.getAttribute('href')) {
      element.setAttribute('target', '_blank')
      element.setAttribute('rel', 'noopener noreferrer')
    }
    if (element.tagName === 'IMG' && element.getAttribute('src')) {
      element.setAttribute('loading', 'lazy')
      element.setAttribute('referrerpolicy', 'no-referrer')
    }
  }

  return template.innerHTML
}

/**
 * 净化不可信 HTML（AI 输出、通知公告富文本等）。
 *
 * <p>净化结果最终会交给 `v-html` **再次解析**，这正是 mXSS 的触发条件：
 * 序列化与反序列化不对称时可能“复活”出净化前不存在的标签结构。
 * 因此这里反复净化直到结果收敛（不动点）；若在上限轮数内仍不稳定，
 * 说明存在结构漂移，直接降级为纯文本。
 */
export function sanitizeHtml(html: string | null | undefined): string {
  if (!html || typeof document === 'undefined') return ''

  let current = sanitizePass(html)
  for (let pass = 1; pass < MAX_SANITIZE_PASSES; pass++) {
    const next = sanitizePass(current)
    if (next === current) {
      return current
    }
    current = next
  }

  return escapeText(html)
}
