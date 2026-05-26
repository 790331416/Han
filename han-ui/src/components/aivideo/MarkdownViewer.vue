<template>
  <div class="markdown-viewer" v-html="safeHtml" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps<{
  content?: string
}>()

const safeHtml = computed(() => {
  const html = marked.parse(props.content || '', {
    async: false,
    breaks: true,
    gfm: true
  }) as string
  return sanitizeHtml(html)
})

function sanitizeHtml(html: string) {
  if (typeof document === 'undefined') {
    return ''
  }
  const template = document.createElement('template')
  template.innerHTML = html
  sanitizeNode(template.content)
  return template.innerHTML
}

function sanitizeNode(root: ParentNode) {
  const allowedTags = new Set([
    'A', 'BLOCKQUOTE', 'BR', 'CODE', 'DEL', 'DIV', 'EM', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
    'HR', 'LI', 'OL', 'P', 'PRE', 'SPAN', 'STRONG', 'TABLE', 'TBODY', 'TD', 'TH', 'THEAD', 'TR', 'UL'
  ])
  const allowedAttrs: Record<string, string[]> = {
    A: ['href', 'title', 'target', 'rel'],
    CODE: ['class'],
    TH: ['align'],
    TD: ['align']
  }

  Array.from(root.childNodes).forEach((node) => {
    if (node.nodeType !== Node.ELEMENT_NODE) {
      return
    }

    const element = node as HTMLElement
    if (!allowedTags.has(element.tagName)) {
      element.replaceWith(...Array.from(element.childNodes))
      sanitizeNode(root)
      return
    }

    Array.from(element.attributes).forEach((attr) => {
      const name = attr.name.toLowerCase()
      const allowed = allowedAttrs[element.tagName]?.includes(name) || false
      if (!allowed || name.startsWith('on')) {
        element.removeAttribute(attr.name)
        return
      }
      if (name === 'href' && /^\s*javascript:/i.test(attr.value)) {
        element.removeAttribute(attr.name)
      }
    })

    if (element.tagName === 'A' && element.getAttribute('href')) {
      element.setAttribute('target', '_blank')
      element.setAttribute('rel', 'noopener noreferrer')
    }

    sanitizeNode(element)
  })
}
</script>

<style lang="scss" scoped>
.markdown-viewer {
  color: #1f2937;
  font-size: 14px;
  line-height: 1.8;
  word-break: break-word;

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 16px 0 8px;
    color: #111827;
    font-weight: 700;
    line-height: 1.35;
  }

  :deep(h1) {
    font-size: 22px;
  }

  :deep(h2) {
    font-size: 19px;
  }

  :deep(h3) {
    font-size: 17px;
  }

  :deep(h4) {
    font-size: 15px;
  }

  :deep(p),
  :deep(ul),
  :deep(ol),
  :deep(blockquote),
  :deep(table),
  :deep(pre) {
    margin: 10px 0;
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 22px;
  }

  :deep(blockquote) {
    padding: 8px 12px;
    border-left: 3px solid #bfdbfe;
    color: #4b5563;
    background: #eff6ff;
  }

  :deep(code) {
    padding: 2px 5px;
    border-radius: 4px;
    background: #f3f4f6;
    color: #374151;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  }

  :deep(pre) {
    overflow: auto;
    padding: 12px;
    border-radius: 8px;
    background: #f9fafb;
  }

  :deep(pre code) {
    padding: 0;
    background: transparent;
  }

  :deep(table) {
    width: 100%;
    border-collapse: collapse;
  }

  :deep(th),
  :deep(td) {
    padding: 8px;
    border: 1px solid #e5e7eb;
    text-align: left;
  }
}
</style>
