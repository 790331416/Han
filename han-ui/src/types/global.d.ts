import 'vue'

declare module 'vue' {
  interface ComponentCustomProperties {
    $formatDate: (value: string | null | undefined) => string
  }
}
