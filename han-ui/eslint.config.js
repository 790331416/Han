import tsParser from '@typescript-eslint/parser'
import tsPlugin from '@typescript-eslint/eslint-plugin'
import vuePlugin from 'eslint-plugin-vue'
import vueParser from 'vue-eslint-parser'

/**
 * Han UI ESLint flat config.
 *
 * 目标是先把前端测试规范要求的 lint 基线跑通：
 * - 兼容 ESLint 9 flat config
 * - 正确解析 TypeScript 与 Vue SFC
 * - 暂不引入激进规则，优先保证可执行与可持续扩展
 */
export default [
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'output/**',
      'src/types/auto-imports.d.ts',
      'src/types/components.d.ts'
    ]
  },
  {
    files: ['**/*.{js,mjs,cjs,ts,mts,cts}'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module'
      }
    },
    plugins: {
      '@typescript-eslint': tsPlugin
    },
    rules: {}
  },
  {
    files: ['**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tsParser,
        ecmaVersion: 'latest',
        sourceType: 'module',
        extraFileExtensions: ['.vue']
      }
    },
    plugins: {
      vue: vuePlugin,
      '@typescript-eslint': tsPlugin
    },
    rules: {}
  }
]
