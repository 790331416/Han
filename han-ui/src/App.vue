<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'

/**
 * el-config-provider 的优先级高于 `app.use(ElementPlus, { locale })`，
 * 这里原来写死 zhCn，导致切到 English 后业务文案变了、Element Plus 内置文案
 * （分页「共 x 条」、日期选择器、表格空态、确认按钮）仍然是中文。
 * 改成跟着 i18n 当前语言走。
 */
const { locale } = useI18n()
const elementLocale = computed(() => (locale.value === 'en-US' ? en : zhCn))
</script>

<style scoped>
</style>
