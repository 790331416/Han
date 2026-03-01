import {
  defineConfig,
  presetUno,
  presetAttributify,
  presetIcons,
  transformerDirectives,
} from 'unocss'

export default defineConfig({
  presets: [
    presetUno(),
    presetAttributify(),
    presetIcons({
      scale: 1.2,
      warn: true,
    }),
  ],
  transformers: [
    transformerDirectives(),
  ],
  theme: {
    colors: {
      primary: {
        DEFAULT: '#2563eb',
        50: '#eff6ff',
        100: '#dbeafe',
        200: '#bfdbfe',
        300: '#93c5fd',
        400: '#60a5fa',
        500: '#3b82f6',
        600: '#2563eb',
        700: '#1d4ed8',
        800: '#1e40af',
        900: '#1e3a8a',
      },
    },
  },
  shortcuts: {
    'card': 'bg-white rounded-xl shadow-sm border border-gray-100',
    'card-header': 'px-5 py-4 border-b border-gray-100 font-medium text-gray-800',
    'card-body': 'p-5',
    'page-container': 'p-5 space-y-4',
    'btn-icon': 'inline-flex items-center justify-center w-8 h-8 rounded-lg hover:bg-gray-100 transition-colors cursor-pointer',
  },
})
