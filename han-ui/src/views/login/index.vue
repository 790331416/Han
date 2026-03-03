<template>
  <div class="login-container">
    <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" class="login-form">
      <h3 class="title">HAN Cloud</h3>
      <p class="subtitle">企业级多租户微服务平台</p>
      
      <el-form-item prop="tenantId">
        <el-select
          v-model="loginForm.tenantId"
          placeholder="请选择租户"
          size="large"
          style="width: 100%"
          filterable
          :loading="tenantLoading"
        >
          <template #prefix><el-icon><OfficeBuilding /></el-icon></template>
          <el-option v-for="t in tenantList" :key="t.tenantId" :label="t.tenantName" :value="t.tenantId" />
        </el-select>
      </el-form-item>

      <el-form-item prop="username">
        <el-input
          v-model="loginForm.username"
          placeholder="用户名"
          size="large"
          :prefix-icon="User"
        />
      </el-form-item>
      
      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          type="password"
          placeholder="密码"
          size="large"
          :prefix-icon="Lock"
          show-password
          @keyup.enter="handleLogin"
        />
      </el-form-item>
      
      <el-form-item v-if="captchaEnabled" prop="code">
        <div class="captcha-row">
          <el-input
            v-model="loginForm.code"
            placeholder="验证码"
            size="large"
            :prefix-icon="Key"
            @keyup.enter="handleLogin"
          />
          <img :src="captchaImg" class="captcha-img" @click="getCaptchaImg" />
        </div>
      </el-form-item>
      
      <el-form-item>
        <el-checkbox v-model="rememberMe">记住密码</el-checkbox>
      </el-form-item>
      
      <el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          class="login-btn"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form-item>
    </el-form>
    
    <div class="footer">
      <span>Copyright 2024 HAN Cloud. All Rights Reserved.</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key, OfficeBuilding } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getCaptcha, type TenantSimple } from '@/api/auth'
import { get } from '@/utils/request'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const captchaEnabled = ref(true)
const captchaImg = ref('')
const rememberMe = ref(false)

const tenantLoading = ref(false)
const tenantList = ref<TenantSimple[]>([])

const loginForm = reactive({
  tenantId: undefined as string | number | undefined,
  username: 'admin',
  password: 'admin123',
  code: '',
  uuid: ''
})

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

// 获取验证码
const getCaptchaImg = async () => {
  try {
    const res = await getCaptcha()
    captchaImg.value = 'data:image/gif;base64,' + res.data.img
    loginForm.uuid = res.data.uuid
  } catch (e) {
    captchaEnabled.value = false
  }
}

// 登录
const handleLogin = async () => {
  const valid = await loginFormRef.value?.validate()
  if (!valid) return
  
  loading.value = true
  try {
    await userStore.login(loginForm)
    ElMessage.success('登录成功')
    
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch (e: any) {
    getCaptchaImg()
  } finally {
    loading.value = false
  }
}

const loadTenantList = async () => {
  tenantLoading.value = true
  try {
    const res = await get<TenantSimple[]>('/tenant/all')
    tenantList.value = (res as any).data || []
    if (tenantList.value.length === 1) {
      loginForm.tenantId = tenantList.value[0].tenantId
    }
  } catch { /* tenant list not available */ } finally {
    tenantLoading.value = false
  }
}

onMounted(() => {
  getCaptchaImg()
  loadTenantList()
})
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: #f9fafb;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: -40%;
    right: -20%;
    width: 600px;
    height: 600px;
    border-radius: 50%;
    background: radial-gradient(circle, #dbeafe 0%, transparent 70%);
    pointer-events: none;
  }
  &::after {
    content: '';
    position: absolute;
    bottom: -30%;
    left: -10%;
    width: 500px;
    height: 500px;
    border-radius: 50%;
    background: radial-gradient(circle, #ede9fe 0%, transparent 70%);
    pointer-events: none;
  }
}

.login-form {
  width: 420px;
  padding: 48px 40px 40px;
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #f3f4f6;
  box-shadow: 0 4px 24px rgb(0 0 0 / 0.06);
  position: relative;
  z-index: 1;
}

.title {
  text-align: center;
  color: #111827;
  margin-bottom: 6px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.subtitle {
  text-align: center;
  color: #9ca3af;
  margin-bottom: 36px;
  font-size: 14px;
  font-weight: 400;
}

.captcha-row {
  display: flex;
  gap: 12px;
  width: 100%;
  
  .el-input { flex: 1; }
  
  .captcha-img {
    width: 120px;
    height: 40px;
    cursor: pointer;
    border-radius: 8px;
    border: 1px solid #e5e7eb;
    transition: border-color 0.15s ease;
    &:hover { border-color: #d1d5db; }
  }
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
}

.footer {
  position: fixed;
  bottom: 24px;
  left: 0;
  right: 0;
  text-align: center;
  color: #9ca3af;
  font-size: 12px;
  z-index: 1;
}
</style>
