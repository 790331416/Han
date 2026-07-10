const allowedTags = new Set([
  'A', 'BLOCKQUOTE', 'BR', 'CODE', 'DEL', 'DIV', 'EM', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
  'HR', 'IMG', 'LI', 'OL', 'P', 'PRE', 'SPAN', 'STRONG', 'SUP', 'TABLE', 'TBODY', 'TD', 'TH',
  'THEAD', 'TR', 'UL'
])

const dangerousTags = new Set(['EMBED', 'IFRAME', 'MATH', 'OBJECT', 'SCRIPT', 'STYLE', 'SVG'])

const allowedAttrs: Record<string, string[]> = {
  A: ['href', 'title'],
  CODE: ['class'],
  IMG: ['alt', 'height', 'src', 'title', 'width'],
  SUP: ['class', 'data-citation-index', 'title'],
  TH: ['align'],
  TD: ['align']
}

function hasSafeUrl(value: string, allowImageData = false): boolean {
  const compact = value.trim().replace(/[\u0000-\u0020\u007f]+/g, '')
  if (!compact) return false
  if (compact.startsWith('/') || compact.startsWith('#')) return true
  if (allowImageData && /^data:image\/(?:png|jpe?g|gif|webp);base64,/i.test(compact)) return true
  const scheme = compact.match(/^([a-z][a-z0-9+.-]*):/i)?.[1]?.toLowerCase()
  return scheme ? ['http', 'https', 'mailto', 'tel', 'blob'].includes(scheme) : true
}

export function sanitizeHtml(html: string | null | undefined): string {
  if (!html || typeof document === 'undefined') return ''

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