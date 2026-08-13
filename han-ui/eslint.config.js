import tsParser from '@typescript-eslint/parser'
import tsPlugin from '@typescript-eslint/eslint-plugin'
import vuePlugin from 'eslint-plugin-vue'
import vueParser from 'vue-eslint-parser'

/**
 * Han UI ESLint flat config.
 *
 * 两个规则块原来都是空的 `rules: {}`，插件挂上了却一条规则都没启用，
 * `pnpm lint` 永远 0 error 0 warning，是一道空门禁。
 *
 * 这里接入 @typescript-eslint 与 eslint-plugin-vue 的官方推荐集作为基线。
 * 为了不让存量代码一次性炸出大量错误把门禁堵死，存量高频项先降为 warning
 * 分阶段收敛，真正会导致运行期缺陷的规则保持 error。
 *
 * 变量未定义一类的问题由 `vue-tsc --noEmit` 兜住，这里不再重复启用 no-undef
 * （SFC 里大量 API 走 unplugin-auto-import，没有显式 import）。
 */
const baseRules = {
  'no-debugger': 'error',
  'no-var': 'error',
  'no-empty': ['error', { allowEmptyCatch: true }],
  'no-dupe-keys': 'error',
  'no-unreachable': 'error',
  'no-fallthrough': 'error',
  'no-console': ['warn', { allow: ['warn', 'error'] }],
  'prefer-const': 'warn',
  eqeqeq: ['warn', 'smart']
}

const typescriptRules = {
  ...tsPlugin.configs.recommended.rules,
  ...baseRules,
  // 存量代码大量使用 any 与下划线占位参数，先降级，避免门禁一次性堵死
  '@typescript-eslint/no-explicit-any': 'off',
  '@typescript-eslint/no-empty-object-type': 'off',
  '@typescript-eslint/no-unused-vars': [
    'warn',
    { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' }
  ]
}

export default [
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'output/**',
      // public/ 是原样投递的静态资源，不进构建管线；embed.js 刻意用 ES5 写法保证嵌入侧兼容性
      'public/**',
      'src/types/auto-imports.d.ts',
      'src/types/components.d.ts',
      // vite.config.js 是历史遗留的构建产物，已在 .gitignore 第 413 行忽略；
      // ESLint 不读 .gitignore，本地留有该文件的机器会因为它报 no-var 而红掉门禁。
      'vite.config.js'
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
    rules: typescriptRules
  },
  {
    // 本地脚本就是靠 stdout 与使用者沟通的
    files: ['**/*.mjs', '*.config.js'],
    rules: {
      'no-console': 'off'
    }
  },
  ...vuePlugin.configs['flat/essential'],
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
    rules: {
      ...typescriptRules,
      /**
       * 关闭多单词组件名约束。
       *
       * 本仓库用「目录名 + index.vue」组织页面（system/user/index.vue 等），
       * 48 处命中全部来自这个既定结构。要满足规则就得跨三个前端分组重命名几十个文件，
       * 纯风格收益、零功能收益，不值得在并行修复期引入这种规模的改名。
       */
      'vue/multi-word-component-names': 'off'
    }
  }
]
