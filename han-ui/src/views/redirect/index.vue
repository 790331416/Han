<template>
  <div />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

onMounted(() => {
  const { params, query } = route
  const { path } = params
  const segments = Array.isArray(path) ? path.filter(Boolean) : (path ? [path] : [])
  // path 缺失时原来会拼出 "/undefined" 再跳过去，最终落到 404
  if (segments.length === 0) {
    router.replace('/')
    return
  }
  const fullPath = '/' + segments.join('/')
  // 防御协议相对地址：双斜杠开头会被浏览器当成跨站地址
  if (fullPath.startsWith('//') || fullPath.startsWith('/\\')) {
    router.replace('/')
    return
  }
  router.replace({ path: fullPath, query })
})
</script>
