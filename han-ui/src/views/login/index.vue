<template>
  <div class="login-container" data-testid="login-page">
    <el-form
      ref="loginFormRef"
      :model="loginForm"
      :rules="loginRules"
      class="login-form"
      data-testid="login-form"
      autocomplete="on"
    >
      <img v-if="brandStore.logoUrl" class="brand-logo" :src="brandStore.logoUrl" alt="" />
      <h3 class="title">{{ brandStore.displayName }}</h3>
      <p v-if="brandStore.loginSubtitle" class="subtitle">{{ brandStore.loginSubtitle }}</p>
      
      <el-form-item v-if="deployTier !== 'small'" prop="tenantId">
        <el-select
          v-model="loginForm.tenantId"
          data-testid="login-tenant-select"
          placeholder="平台管理员无需选择"
          size="large"
          style="width: 100%"
          filterable
          clearable
          :loading="tenantLoading"
        >
          <template #prefix><el-icon><OfficeBuilding /></el-icon></template>
          <el-option v-for="t in tenantList" :key="t.tenantId" :label="t.tenantName" :value="t.tenantId" />
        </el-select>
      </el-form-item>

      <el-form-item prop="username">
        <el-input
          v-model="loginForm.username"
          data-testid="login-username"
          placeholder="用户名"
          size="large"
          :prefix-icon="User"
          autocomplete="username"
        />
      </el-form-item>
      
      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          data-testid="login-password"
          type="password"
          placeholder="密码"
          size="large"
          :prefix-icon="Lock"
          show-password
          autocomplete="current-password"
          @keyup.enter="handleLogin"
        />
      </el-form-item>
      
      <el-form-item v-if="captchaEnabled" prop="code">
        <div class="captcha-row">
          <el-input
            v-model="loginForm.code"
            data-testid="login-captcha"
            placeholder="验证码"
            size="large"
            :prefix-icon="Key"
            @keyup.enter="handleLogin"
          />
          <img :src="captchaImg" class="captcha-img" data-testid="login-captcha-image" @click="getCaptchaImg" />
        </div>
      </el-form-item>
      
      <el-form-item>
        <el-checkbox v-model="rememberAccount">记住账号</el-checkbox>
      </el-form-item>
      
      <el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          class="login-btn"
          data-testid="login-submit"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form-item>

      <div class="vendor-apply-link">
        <el-link type="primary" :underline="false" data-testid="vendor-apply-link" @click="router.push('/open/vendor-apply')">
          厂商入驻申请
        </el-link>
      </div>

      <div v-if="socialProviders.github || socialProviders.wechat" class="social-login">
        <el-divider>其他登录方式</el-divider>
        <div class="social-icons">
          <el-tooltip v-if="socialProviders.wechat" content="微信扫码登录" placement="bottom">
            <div class="social-icon wechat" data-testid="login-wechat" @click="handleSocialLogin('wechat')">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><path d="M9.5 4C5.36 4 2 6.8 2 10.25c0 1.92 1.05 3.63 2.7 4.78l-.68 2.05a.3.3 0 0 0 .44.35l2.37-1.37c.83.24 1.73.37 2.67.37l.44-.01a5.32 5.32 0 0 1-.24-1.57c0-3.25 3.13-5.88 7-5.88l.42.01C16.55 6.19 13.35 4 9.5 4zM7.13 7.36a.94.94 0 1 1 0 1.88.94.94 0 0 1 0-1.88zm4.75 0a.94.94 0 1 1 0 1.88.94.94 0 0 1 0-1.88zM16.7 10c-3.2 0-5.8 2.16-5.8 4.85 0 2.68 2.6 4.85 5.8 4.85.66 0 1.3-.1 1.9-.27l1.86 1.08a.25.25 0 0 0 .37-.29l-.5-1.55c1.44-.9 2.37-2.3 2.37-3.82 0-2.69-2.6-4.85-5.8-4.85h-.2zm-2.05 2.6a.78.78 0 1 1 0 1.57.78.78 0 0 1 0-1.57zm4.1 0a.78.78 0 1 1 0 1.57.78.78 0 0 1 0-1.57z"/></svg>
            </div>
          </el-tooltip>
          <el-tooltip v-if="socialProviders.github" content="GitHub 登录" placement="bottom">
            <div class="social-icon github" @click="handleSocialLogin('github')">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.3 3.44 9.8 8.2 11.39.6.11.82-.26.82-.58v-2.03c-3.34.73-4.04-1.61-4.04-1.61-.55-1.39-1.34-1.76-1.34-1.76-1.09-.75.08-.73.08-.73 1.2.08 1.84 1.24 1.84 1.24 1.07 1.83 2.81 1.3 3.5 1 .11-.78.42-1.3.76-1.6-2.67-.3-5.47-1.33-5.47-5.93 0-1.31.47-2.38 1.24-3.22-.13-.3-.54-1.52.12-3.18 0 0 1-.32 3.3 1.23a11.5 11.5 0 0 1 6 0c2.28-1.55 3.28-1.23 3.28-1.23.66 1.66.25 2.88.12 3.18.77.84 1.24 1.91 1.24 3.22 0 4.61-2.81 5.63-5.48 5.92.43.37.81 1.1.81 2.22v3.29c0 .32.22.7.82.58C20.56 21.8 24 17.3 24 12c0-6.63-5.37-12-12-12z"/></svg>
            </div>
          </el-tooltip>
        </div>
      </div>
    </el-form>
    
    <div class="footer">
      <span>Copyright 2024 {{ brandStore.displayName }}. All Rights Reserved.</span>
    </div>

    <!-- 2FA TOTP 验证弹窗 -->
    <el-dialog v-model="totpVisible" title="两步验证" width="380px" :close-on-click-modal="false" destroy-on-close>
      <p class="totp-hint">请输入您的 Authenticator APP 中的 6 位验证码</p>
      <el-input
        v-model="totpCode"
        placeholder="000000"
        maxlength="6"
        size="large"
        class="totp-input"
        @keyup.enter="handleTotpVerify"
      />
      <template #footer>
        <el-button @click="totpVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleTotpVerify">验证</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key, OfficeBuilding } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useBrandStore } from '@/stores/brand'
import { getCaptcha, getPublicKey, getSocialProviders, getSocialAuthorizeUrl, type TenantSimple } from '@/api/auth'
import { rsaEncrypt } from '@/utils/crypto'
import { get } from '@/utils/request'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()
const brandStore = useBrandStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const captchaEnabled = ref(true)
const captchaImg = ref('')
const rememberAccount = ref(false)
const deployTier = computed(() => appStore.deployTier || import.meta.env.VITE_DEPLOY_TIER || 'full')
const rememberedAccountKey = 'han-login-account'

const tenantLoading = ref(false)
const tenantList = ref<TenantSimple[]>([])
const encryptEnabled = ref(false)
const rsaPublicKey = ref('')

const totpVisible = ref(false)
const totpCode = ref('')
let pendingLoginData: any = null

const loginForm = reactive({
  tenantId: undefined as string | number | undefined,
  username: 'admin',
  password: '',
  code: '',
  uuid: ''
})

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{
    validator: (_rule, value, callback) => {
      if (captchaEnabled.value && !value) {
        callback(new Error('请输入验证码'))
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }]
}

// 获取验证码（后台 sys.account.captchaEnabled=false 时隐藏验证码输入框）
const getCaptchaImg = async () => {
  try {
    const res = await getCaptcha()
    if (res.data?.enabled === 'false') {
      captchaEnabled.value = false
      loginForm.code = ''
      loginForm.uuid = ''
      return
    }
    captchaEnabled.value = true
    captchaImg.value = 'data:image/gif;base64,' + (res.data.img || '')
    loginForm.uuid = res.data.uuid || ''
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
    const loginData = { ...loginForm }
    if (encryptEnabled.value && rsaPublicKey.value) {
      loginData.password = await rsaEncrypt(loginForm.password, rsaPublicKey.value)
    }
    const loginRes = await userStore.login(loginData)
    if (loginRes.data.requireTotp) {
      // 需要 2FA 验证，弹出 TOTP 输入框
      pendingLoginData = { ...loginData }
      totpCode.value = ''
      totpVisible.value = true
      return
    }
    handleLoginSuccess(loginRes)
  } catch (e: any) {
    getCaptchaImg()
  } finally {
    loading.value = false
  }
}

const handleLoginSuccess = (loginRes: any) => {
  persistRememberedAccount()
  if (loginRes.data.forceChangePassword) {
    ElMessage.warning('您的密码需要修改，请先修改密码')
    router.push('/profile?tab=password&force=1')
  } else {
    ElMessage.success('登录成功')
    const redirect = route.query.redirect as string | undefined
    router.replace(!redirect || redirect === '/404' || redirect.endsWith('/404') ? '/' : redirect)
  }
}

const loadRememberedAccount = () => {
  try {
    const raw = window.localStorage.getItem(rememberedAccountKey)
    if (!raw) return
    const data = JSON.parse(raw)
    if (data?.username) {
      loginForm.username = data.username
      rememberAccount.value = true
    }
    if (data?.tenantId !== undefined && data.tenantId !== null && data.tenantId !== '') {
      loginForm.tenantId = data.tenantId
    }
  } catch {
    window.localStorage.removeItem(rememberedAccountKey)
  }
}

const persistRememberedAccount = () => {
  if (!rememberAccount.value) {
    window.localStorage.removeItem(rememberedAccountKey)
    return
  }
  window.localStorage.setItem(rememberedAccountKey, JSON.stringify({
    username: loginForm.username,
    tenantId: loginForm.tenantId ?? ''
  }))
}

const handleTotpVerify = async () => {
  if (!totpCode.value || totpCode.value.length !== 6) {
    ElMessage.warning('请输入6位验证码')
    return
  }
  loading.value = true
  try {
    const loginData = { ...pendingLoginData, totpCode: totpCode.value }
    const loginRes = await userStore.login(loginData)
    totpVisible.value = false
    handleLoginSuccess(loginRes)
  } catch (e: any) {
    // 验证失败，保持弹窗打开
  } finally {
    loading.value = false
  }
}

const loadTenantList = async () => {
  tenantLoading.value = true
  try {
    const res = await get<TenantSimple[]>('/tenant/all', undefined, { silentError: true })
    tenantList.value = (res as any).data || []
  } catch { /* tenant list not available */ } finally {
    tenantLoading.value = false
  }
}

// ==================== 社交登录 ====================
const socialProviders = reactive({ github: false, wechat: false })

const loadSocialProviders = async () => {
  try {
    const res = await getSocialProviders()
    if (res.data) {
      socialProviders.github = res.data.github === true
      socialProviders.wechat = res.data.wechat === true
    }
  } catch { /* social login not available */ }
}

const handleSocialLogin = async (provider: 'github' | 'wechat') => {
  try {
    const redirectUri = window.location.origin + '/social/callback?provider=' + provider
    const res = await getSocialAuthorizeUrl(provider, redirectUri)
    const url = res.data?.authorizeUrl
    if (url) window.location.href = url
  } catch { /* error handled */ }
}

const loadPublicKey = async () => {
  try {
    const res = await getPublicKey()
    if (res.data?.enabled && res.data?.publicKey) {
      encryptEnabled.value = true
      rsaPublicKey.value = res.data.publicKey
    }
  } catch { /* encryption not available */ }
}

onMounted(async () => {
  await appStore.loadRuntimeCapabilities()
  loadRememberedAccount()
  getCaptchaImg()
  if (deployTier.value !== 'small') {
    loadTenantList()
  }
  loadPublicKey()
  loadSocialProviders()
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

.brand-logo {
  display: block;
  width: 64px;
  height: 64px;
  margin: 0 auto 12px;
  object-fit: contain;
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

.vendor-apply-link {
  margin: -2px 0 12px;
  text-align: center;
  font-size: 13px;
}

.footer {
  position: fixed;
  bottom: 12px;
  left: 0;
  right: 0;
  text-align: center;
  color: #9ca3af;
  font-size: 12px;
  z-index: 0;
  pointer-events: none;
}

.totp-hint {
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 16px;
  text-align: center;
}

.totp-input {
  :deep(.el-input__inner) {
    text-align: center;
    font-size: 24px;
    letter-spacing: 8px;
    font-weight: 600;
  }
}

.social-login {
  margin-top: -8px;

  :deep(.el-divider__text) {
    color: #9ca3af;
    font-size: 12px;
    background: #ffffff;
  }
}

.social-icons {
  display: flex;
  justify-content: center;
  gap: 16px;
}

.social-icon {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid #e5e7eb;
  color: #374151;

  &:hover {
    transform: scale(1.1);
  }

  &.github:hover {
    background: #24292e;
    color: #fff;
    border-color: #24292e;
  }

  &.wechat:hover {
    background: #07c160;
    color: #fff;
    border-color: #07c160;
  }
}
</style>
