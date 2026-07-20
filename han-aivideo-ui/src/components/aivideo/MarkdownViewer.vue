<template>
  <div class="markdown-viewer" v-html="safeHtml" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import { sanitizeHtml } from '@/utils/sanitize-html'

const props = defineProps<{
  content?: string
}>()

const safeHtml = computed(() => {
  const html = marked.parse(props.content || '', {
    async: false,
    breaks: true,
    gfm: true
  }) as string
  return wrapTables(sanitizeHtml(html))
})

// 宽表格放进可横向滚动的容器，避免撑破外层栅格布局
function wrapTables(html: string): string {
  if (typeof document === 'undefined' || !html.includes('<table')) return html
  const template = document.createElement('template')
  template.innerHTML = html
  for (const table of Array.from(template.content.querySelectorAll('table'))) {
    const wrap = document.createElement('div')
    wrap.className = 'table-scroll'
    table.replaceWith(wrap)
    wrap.appendChild(table)
  }
  return template.innerHTML
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

  :deep(.table-scroll) {
    margin: 10px 0;
    max-width: 100%;
    overflow-x: auto;
  }

  :deep(table) {
    width: max-content;
    min-width: 100%;
    border-collapse: collapse;
    margin: 0;
  }

  :deep(th),
  :deep(td) {
    padding: 8px;
    border: 1px solid #e5e7eb;
    text-align: left;
    min-width: 64px;
    max-width: 340px;
  }
}
</style>
