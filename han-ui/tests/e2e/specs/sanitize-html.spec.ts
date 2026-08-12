import { expect, test } from '@playwright/test'
import { filterClassValue, hasSafeUrl } from '../../../src/utils/sanitize-html'

/**
 * 净化器策略回归。
 *
 * <p>覆盖两条曾经出问题的规则：
 * 1. `SPAN` 没有 class 白名单，导致 highlight.js 的着色 class 被整体剥掉、代码高亮实际失效；
 * 2. class 取值不做限制时，AI 输出可以注入任意 class 干扰全局样式。
 *
 * <p>`sanitizeHtml` 主体依赖 DOM 解析，由浏览器侧用例覆盖；这里只测纯函数策略部分。
 */
test.describe('sanitize-html class policy', () => {
  test('keeps hljs colouring classes on span so code highlight actually renders', () => {
    expect(filterClassValue('SPAN', 'hljs-keyword')).toBe('hljs-keyword')
    expect(filterClassValue('SPAN', 'hljs-string hljs-subst')).toBe('hljs-string hljs-subst')
  })

  test('keeps hljs and language classes on code block wrapper', () => {
    expect(filterClassValue('CODE', 'hljs language-ts')).toBe('hljs language-ts')
    expect(filterClassValue('CODE', 'language-c++')).toBe('language-c++')
  })

  test('drops classes that are not on the whitelist', () => {
    expect(filterClassValue('SPAN', 'el-button')).toBe('')
    expect(filterClassValue('SPAN', 'hljs-keyword evil-overlay')).toBe('hljs-keyword')
    expect(filterClassValue('CODE', 'hljs sneaky')).toBe('hljs')
    expect(filterClassValue('SUP', 'citation-badge')).toBe('citation-badge')
    expect(filterClassValue('SUP', 'fake-badge')).toBe('')
  })

  test('drops classes on tags that never carry one', () => {
    expect(filterClassValue('DIV', 'anything')).toBe('')
    expect(filterClassValue('A', 'anything')).toBe('')
  })
})

test.describe('sanitize-html url policy', () => {
  test('allows normal navigable schemes and relative urls', () => {
    expect(hasSafeUrl('https://example.com/docs')).toBe(true)
    expect(hasSafeUrl('http://example.com')).toBe(true)
    expect(hasSafeUrl('mailto:a@b.com')).toBe(true)
    expect(hasSafeUrl('/ai/chat')).toBe(true)
    expect(hasSafeUrl('#anchor')).toBe(true)
    expect(hasSafeUrl('docs/readme.md')).toBe(true)
  })

  test('rejects script schemes including control-character obfuscation', () => {
    expect(hasSafeUrl('javascript:alert(1)')).toBe(false)
    expect(hasSafeUrl('jav\tascript:alert(1)')).toBe(false)
    expect(hasSafeUrl('  JaVaScRiPt:alert(1)')).toBe(false)
    expect(hasSafeUrl('vbscript:msgbox(1)')).toBe(false)
    expect(hasSafeUrl('')).toBe(false)
  })

  test('only allows base64 image data urls on img src', () => {
    const payload = 'data:image/png;base64,iVBORw0KGgo='
    expect(hasSafeUrl(payload, true)).toBe(true)
    expect(hasSafeUrl(payload)).toBe(false)
    expect(hasSafeUrl('data:text/html;base64,PHNjcmlwdD4=', true)).toBe(false)
  })
})
