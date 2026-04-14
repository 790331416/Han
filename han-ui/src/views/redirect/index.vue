<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

onMounted(() => {
  const { params, query } = route
  const { path } = params
  const fullPath = Array.isArray(path) ? '/' + path.join('/') : '/' + path
  router.replace({ path: fullPath, query })
})
</script>
