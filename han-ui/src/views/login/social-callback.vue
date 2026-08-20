<template>
  <div class="login-container" data-testid="social-callback-page">
    <div class="login-form callback-card">
      <h3 class="title">{{ brandStore.displayName }}</h3>
      <p class="subtitle">{{ providerLabel }}登录</p>

      <!-- 处理中 -->
      <div v-if="status === 'processing'" class="callback-status" data-testid="social-processing">
        <el-icon class="is-loading status-icon"><Loading /></el-icon>
        <p class="status-text">正在处理{{ providerLabel }}登录，请稍候...</p>
      </div>

      <!-- 多租户绑定：选择租户登录 -->
      <div v-else-if="status === 'select-tenant'" data-testid="social-select-tenant">
        <p class="section-hint">该{{ providerLabel }}账号绑定了多个租户，请选择要登录的租户</p>
        <div class="tenant-list">
          <div
            v-for="t in tenantOptions"
            :key="String(t.tenantId)"
            class="tenant-item"
            :class="{ disabled: loading }"
            @click="handleSelectTenant(t)"
          >
            <el-icon><OfficeBuilding /></el-icon>
            <span class="tenant-name">{{ t.tenantName || `租户 ${t.tenantId}` }}</span>
            <el-icon class="arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <!-- 未绑定：账号密码绑定并登录 -->
      <div v-else-if="status === 'bind'" data-testid="social-bind-form">
        <div class="bind-profile">
          <el-avatar :size="56" :src="socialAvatar || undefined">
            {{ (socialNickname || providerLabel).slice(0, 1) }}
          </el-avatar>
          <p class="bind-nickname">{{ socialNickname || `${providerLabel}用户` }}</p>
          <p class="section-hint">该{{ providerLabel }}账号尚未绑定系统账号，请输入账号密码完成绑定并登录</p>
        </div>

        <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" @submit.prevent>
          <el-form-item v-if="deployTier !== 'small'" prop="tenantId">
            <el-select
              v-model="bindForm.tenantId"
              data-testid="social-bind-tenant"
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
              v-model="bindForm.username"
              data-testid="social-bind-username"
              placeholder="用户名"
              size="large"
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="bindForm.password"
              data-testid="social-bind-password"
              type="password"
              placeholder="密码"
              size="large"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
              @keyup.enter="handleBind"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              class="login-btn"
              data-testid="social-bind-submit"
              @click="handleBind"
            >
              绑定并登录
            </el-button>
          </el-form-item>
        </el-form>
        <div class="back-line">
          <el-link type="info" :underline="false" @click="backToLogin">返回登录页</el-link>
        </div>
      </div>

      <!-- 失败：引导重新扫码 -->
      <div v-else class="callback-status" data-testid="social-error">
        <el-icon class="status-icon error"><CircleCloseFilled /></el-icon>
        <p class="status-text">{{ errorMessage }}</p>
        <el-button type="primary" size="large" class="login-btn" @click="backToLogin">
          返回登录页重新扫码
        </el-button>
      </div>
    </div>

    <div class="footer">
      <span>Copyright 2024 {{ brandStore.displayName }}. All Rights Reserved.</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, CircleCloseFilled, Loading, Lock, OfficeBuilding, User } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useBrandStore } from '@/stores/brand'
import {
  getPublicKey,
  socialBind,
  socialCallback,
  socialLoginByTicket,
  type SocialTenantOption,
  type TenantSimple
} from '@/api/auth'
import { rsaEncrypt } from '@/utils/crypto'
import { get } from '@/utils/request'
import type { LoginVO } from '@/types'
import type { FormInstance, FormRules } from 'element-plus'

type CallbackStatus = 'processing' | 'select-tenant' | 'bind' | 'error'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const brandStore = useBrandStore()

const status = ref<CallbackStatus>('processing')
const loading = ref(false)
const errorMessage = ref('登录失败，请重新发起登录')

const provider = ref(String(route.query.provider || ''))
const providerLabel = computed(() => {
  if (provider.value === 'wechat') return '微信'
  if (provider.value === 'github') return 'GitHub'
  return provider.value || '第三方'
})

const deployTier = computed(() => appStore.deployTier || import.meta.env.VITE_DEPLOY_TIER || 'full')

// 一次性绑定票据（服务端 10 分钟有效）
const ticket = ref('')
const tenantOptions = ref<SocialTenantOption[]>([])
const socialNickname = ref('')
const socialAvatar = ref('')

const bindFormRef = ref<FormInstance>()
const bindForm = reactive({
  tenantId: undefined as string | number | undefined,
  username: '',
  password: ''
})

const bindRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const tenantLoading = ref(false)
const tenantList = ref<TenantSimple[]>([])
const encryptEnabled = ref(false)
const rsaPublicKey = ref('')

const backToLogin = () => {
  router.replace('/login')
}

// ticket 作废（密码试错超限 / 过期）时后端提示均含「重新扫码」，此时表单已无法继续
const isTicketDeadMessage = (message: string) => message.includes('重新扫码')

const enterErrorState = (message: string) => {
  errorMessage.value = message || '登录失败，请重新发起登录'
  status.value = 'error'
}

const handleLoginSuccess = (login: LoginVO) => {
  userStore.applySession(login.accessToken, login.refreshToken, login.userInfo?.userId ?? null)
  if (login.forceChangePassword) {
    ElMessage.warning('您的密码需要修改，请先修改密码')
    router.replace('/user/profile?tab=password')
  } else {
    ElMessage.success('登录成功')
    router.replace('/')
  }
}

const handleCallback = async () => {
  const code = String(route.query.code || '')
  const state = String(route.query.state || '')
  if (!provider.value || !code) {
    enterErrorState('授权信息缺失，可能已取消授权，请返回登录页重新发起登录')
    return
  }
  try {
    const res = await socialCallback(provider.value, code, state)
    const data = res.data
    if (data?.bound && data.login) {
      handleLoginSuccess(data.login)
      return
    }
    if (data?.bound && data.multiTenant) {
      ticket.value = data.ticket || ''
      tenantOptions.value = data.tenants || []
      status.value = 'select-tenant'
      return
    }
    if (data && !data.bound) {
      ticket.value = data.ticket || ''
      socialNickname.value = data.nickname || ''
      socialAvatar.value = data.avatar || ''
      status.value = 'bind'
      loadBindSupports()
      return
    }
    enterErrorState('登录响应异常，请重新发起登录')
  } catch (e: any) {
    enterErrorState(e?.message || '登录失败，请重新发起登录')
  }
}

const handleSelectTenant = async (tenant: SocialTenantOption) => {
  if (loading.value) return
  loading.value = true
  try {
    const res = await socialLoginByTicket(ticket.value, tenant.tenantId)
    handleLoginSuccess(res.data)
  } catch (e: any) {
    const message = e?.message || '登录失败，请重新扫码'
    if (isTicketDeadMessage(message)) {
      enterErrorState(message)
    } else {
      ElMessage.error(message)
    }
  } finally {
    loading.value = false
  }
}

const handleBind = async () => {
  const valid = await bindFormRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    let password = bindForm.password
    if (encryptEnabled.value && rsaPublicKey.value) {
      password = await rsaEncrypt(bindForm.password, rsaPublicKey.value)
    }
    const res = await socialBind({
      ticket: ticket.value,
      username: bindForm.username,
      password,
      tenantId: bindForm.tenantId !== undefined && bindForm.tenantId !== '' ? String(bindForm.tenantId) : undefined
    })
    handleLoginSuccess(res.data)
  } catch (e: any) {
    const message = e?.message || '绑定失败，请重试'
    if (isTicketDeadMessage(message)) {
      enterErrorState(message)
    } else {
      ElMessage.error(message)
    }
  } finally {
    loading.value = false
  }
}

// 绑定表单的支撑数据：租户列表（多租户部署时）与 RSA 公钥（密码加密传输，与登录页一致）
const loadBindSupports = () => {
  if (deployTier.value !== 'small') {
    loadTenantList()
  }
  loadPublicKey()
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
  handleCallback()
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
  margin-bottom: 28px;
  font-size: 14px;
  font-weight: 400;
}

.callback-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 12px 0 4px;

  .status-icon {
    font-size: 40px;
    color: #3b82f6;

    &.error {
      color: #ef4444;
    }
  }

  .status-text {
    color: #4b5563;
    font-size: 14px;
    text-align: center;
    line-height: 1.6;
    margin: 0;
  }
}

.section-hint {
  color: #6b7280;
  font-size: 13px;
  text-align: center;
  line-height: 1.6;
  margin: 0 0 16px;
}

.tenant-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tenant-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  color: #374151;
  transition: all 0.15s ease;

  .tenant-name {
    flex: 1;
    font-size: 14px;
    font-weight: 500;
  }

  .arrow {
    color: #9ca3af;
  }

  &:hover {
    border-color: #3b82f6;
    background: #eff6ff;
  }

  &.disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
}

.bind-profile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;

  .bind-nickname {
    color: #111827;
    font-size: 15px;
    font-weight: 600;
    margin: 0;
  }
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
}

.back-line {
  text-align: center;
  margin-top: -6px;
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
</style>
