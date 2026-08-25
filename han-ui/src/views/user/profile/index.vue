<template>
  <div class="app-container profile-container">
    <el-row :gutter="20">
      <!-- 左侧：用户信息卡片 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <span>个人信息</span>
          </template>
          <div class="user-info-card">
            <div class="avatar-section">
              <el-avatar :size="100" :src="userInfo.avatar || defaultAvatar" />
              <el-button type="primary" link class="change-avatar-btn" @click="openAvatarDialog">
                修改头像
              </el-button>
            </div>
            <ul class="user-info-list">
              <li>
                <el-icon><User /></el-icon>
                <span class="label">用户名</span>
                <span class="value">{{ userInfo.username }}</span>
              </li>
              <li>
                <el-icon><Iphone /></el-icon>
                <span class="label">手机号</span>
                <span class="value">{{ userInfo.phone || '未设置' }}</span>
              </li>
              <li>
                <el-icon><Message /></el-icon>
                <span class="label">邮箱</span>
                <span class="value">{{ userInfo.email || '未设置' }}</span>
              </li>
              <li>
                <el-icon><OfficeBuilding /></el-icon>
                <span class="label">部门</span>
                <span class="value">{{ userInfo.deptName || '-' }}</span>
              </li>
              <li>
                <el-icon><Calendar /></el-icon>
                <span class="label">创建时间</span>
                <span class="value">{{ userInfo.createTime || '-' }}</span>
              </li>
            </ul>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：Tabs -->
      <el-col :span="16">
        <el-card shadow="never">
          <el-tabs v-model="activeTab">
            <!-- 基本资料 -->
            <el-tab-pane label="基本资料" name="info">
              <el-form ref="infoFormRef" :model="infoForm" :rules="infoRules" label-width="80px" style="max-width: 500px;">
                <el-form-item label="昵称" prop="nickname">
                  <el-input v-model="infoForm.nickname" placeholder="请输入昵称" />
                </el-form-item>
                <el-form-item label="手机号" prop="phone">
                  <el-input v-model="infoForm.phone" placeholder="请输入手机号" maxlength="11" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                  <el-input v-model="infoForm.email" placeholder="请输入邮箱" />
                </el-form-item>
                <el-form-item label="性别" prop="sex">
                  <el-radio-group v-model="infoForm.sex">
                    <el-radio :value="1">男</el-radio>
                    <el-radio :value="2">女</el-radio>
                    <el-radio :value="0">未知</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="infoLoading" @click="handleUpdateInfo">保存</el-button>
                  <el-button @click="resetInfoForm">重置</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 两步验证 -->
            <el-tab-pane label="两步验证" name="totp">
              <div v-if="totpLoading" v-loading="true" style="min-height: 200px" />
              <div v-else-if="totpEnabled" class="totp-section">
                <el-result icon="success" title="两步验证已启用" sub-title="您的账号已启用 Authenticator 两步验证保护">
                  <template #extra>
                    <el-button type="danger" @click="showUnbindDialog = true">解除绑定</el-button>
                  </template>
                </el-result>
              </div>
              <div v-else class="totp-section">
                <el-result icon="info" title="两步验证未启用" sub-title="启用后，登录时需要输入 Authenticator APP 中的验证码">
                  <template #extra>
                    <el-button type="primary" @click="handleSetupTotp">立即启用</el-button>
                  </template>
                </el-result>
              </div>
            </el-tab-pane>

            <!-- 修改密码 -->
            <el-tab-pane label="修改密码" name="password">
              <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="80px" style="max-width: 500px;">
                <el-form-item label="旧密码" prop="oldPassword">
                  <el-input v-model="pwdForm.oldPassword" type="password" placeholder="请输入旧密码" show-password />
                </el-form-item>
                <el-form-item label="新密码" prop="newPassword">
                  <el-input v-model="pwdForm.newPassword" type="password" placeholder="请输入新密码" show-password />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                  <el-input v-model="pwdForm.confirmPassword" type="password" placeholder="请确认新密码" show-password />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="pwdLoading" @click="handleUpdatePwd">保存</el-button>
                  <el-button @click="resetPwdForm">重置</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>

    <!-- 2FA 绑定对话框 -->
    <el-dialog v-model="showSetupDialog" title="绑定两步验证" width="460px" :close-on-click-modal="false" destroy-on-close>
      <div class="totp-setup">
        <p class="totp-step">1. 使用 Google Authenticator 或 Microsoft Authenticator 扫描下方二维码</p>
        <div class="totp-qr">
          <img v-if="totpSetupData.qrCode" :src="'data:image/png;base64,' + totpSetupData.qrCode" alt="TOTP QR Code" />
        </div>
        <p class="totp-step">2. 或手动输入密钥：</p>
        <el-input :model-value="totpSetupData.secret" readonly class="totp-secret">
          <template #append>
            <el-button @click="copySecret">复制</el-button>
          </template>
        </el-input>
        <p class="totp-step">3. 输入 APP 中显示的 6 位验证码确认绑定：</p>
        <el-input v-model="totpBindCode" placeholder="000000" maxlength="6" size="large" class="totp-code-input" @keyup.enter="handleBindTotp" />
      </div>
      <template #footer>
        <el-button @click="showSetupDialog = false">取消</el-button>
        <el-button type="primary" :loading="totpBindLoading" @click="handleBindTotp">确认绑定</el-button>
      </template>
    </el-dialog>

    <!-- 2FA 解绑对话框 -->
    <el-dialog v-model="showUnbindDialog" title="解除两步验证" width="400px" :close-on-click-modal="false" destroy-on-close>
      <p style="color: #6b7280; margin-bottom: 16px;">解除绑定后，登录将不再需要两步验证码。请输入当前密码确认操作。</p>
      <el-input v-model="unbindPassword" type="password" placeholder="请输入当前密码" show-password size="large" @keyup.enter="handleUnbindTotp" />
      <template #footer>
        <el-button @click="showUnbindDialog = false">取消</el-button>
        <el-button type="danger" :loading="unbindLoading" @click="handleUnbindTotp">确认解绑</el-button>
      </template>
    </el-dialog>

    <!-- 修改头像对话框 -->
    <el-dialog v-model="showAvatarDialog" title="修改头像" width="40%" class="dialog-sm" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="头像图片">
          <el-upload :auto-upload="false" :show-file-list="false" accept=".png,.jpg,.jpeg,.webp" :on-change="handleAvatarChange">
            <el-button>选择图片</el-button>
            <template #tip><div class="el-upload__tip">支持 PNG、JPG、WebP，文件不超过 2MB。</div></template>
          </el-upload>
        </el-form-item>
        <div class="avatar-preview">
          <span class="label">预览：</span>
          <el-avatar :size="80" :src="avatarUrl || defaultAvatar" />
        </div>
      </el-form>
      <template #footer>
        <el-button @click="showAvatarDialog = false">取消</el-button>
        <el-button type="primary" :loading="avatarLoading" @click="handleUpdateAvatar">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onBeforeUnmount, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Iphone, Message, OfficeBuilding, Calendar } from '@element-plus/icons-vue'
import { getUserProfile, updateUserProfile, updateUserPassword, updateUserAvatar, uploadUserAvatar } from '@/api/system/user'
import { getTotpStatus, getTotpSetup, bindTotp, unbindTotp } from '@/api/system/totp'
import { useUserStore } from '@/stores/user'
import { useRoute } from 'vue-router'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'

const userStore = useUserStore()
const route = useRoute()
const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

const activeTab = ref(route.query.tab as string || 'info')
const infoLoading = ref(false)
const pwdLoading = ref(false)
const avatarLoading = ref(false)
const showAvatarDialog = ref(false)
const avatarUrl = ref('')
const avatarFile = ref<File | null>(null)
let avatarObjectUrl = ''

const infoFormRef = ref<FormInstance>()
const pwdFormRef = ref<FormInstance>()

// 用户信息
const userInfo = reactive({
  userId: 0 as string | number,
  username: '',
  nickname: '',
  phone: '',
  email: '',
  sex: 0,
  avatar: '',
  deptName: '',
  createTime: ''
})

// 基本信息表单
const infoForm = reactive({
  nickname: '',
  phone: '',
  email: '',
  sex: 0
})

// 密码表单
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 信息表单校验
const infoRules: FormRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱', trigger: 'blur' }]
}

// 密码表单校验
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 32, message: '密码长度在8到32个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 加载用户信息
const loadProfile = async () => {
  try {
    const res = await getUserProfile()
    const data = res.data
    userInfo.userId = data.userId
    userInfo.username = data.username
    userInfo.nickname = data.nickname || ''
    userInfo.phone = data.phone || ''
    userInfo.email = data.email || ''
    userInfo.sex = data.sex || 0
    userInfo.avatar = data.avatar || ''
    userInfo.deptName = data.deptName || ''
    userInfo.createTime = data.createTime || ''

    // 同步到表单
    infoForm.nickname = userInfo.nickname
    infoForm.phone = userInfo.phone
    infoForm.email = userInfo.email
    infoForm.sex = userInfo.sex
  } catch { /* 接口不可用 */ }
}

// 修改个人信息
const handleUpdateInfo = async () => {
  const valid = await infoFormRef.value?.validate()
  if (!valid) return

  infoLoading.value = true
  try {
    await updateUserProfile(infoForm)
    ElMessage.success('修改成功')
    // 刷新用户信息
    await loadProfile()
    await userStore.getInfo()
  } finally {
    infoLoading.value = false
  }
}

// 重置信息表单
const resetInfoForm = () => {
  infoForm.nickname = userInfo.nickname
  infoForm.phone = userInfo.phone
  infoForm.email = userInfo.email
  infoForm.sex = userInfo.sex
}

// 修改密码
const handleUpdatePwd = async () => {
  const valid = await pwdFormRef.value?.validate()
  if (!valid) return

  pwdLoading.value = true
  try {
    await updateUserPassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    resetPwdForm()
    // 修改密码后退出重新登录
    setTimeout(async () => {
      await userStore.logout()
      window.location.assign(`${import.meta.env.BASE_URL}login`)
    }, 1500)
  } finally {
    pwdLoading.value = false
  }
}

// 重置密码表单
const resetPwdForm = () => {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value?.resetFields()
}

function openAvatarDialog() {
  avatarFile.value = null
  avatarUrl.value = userInfo.avatar || ''
  showAvatarDialog.value = true
}

function handleAvatarChange(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file) return
  const validType = ['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || /\.(png|jpe?g|webp)$/i.test(file.name)
  if (!validType) {
    ElMessage.warning('头像仅支持 PNG、JPG 或 WebP 格式')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像文件不能超过 2MB')
    return
  }
  if (avatarObjectUrl) URL.revokeObjectURL(avatarObjectUrl)
  avatarObjectUrl = URL.createObjectURL(file)
  avatarFile.value = file
  avatarUrl.value = avatarObjectUrl
}

// 修改头像
const handleUpdateAvatar = async () => {
  if (!avatarFile.value) {
    ElMessage.warning('请选择头像图片')
    return
  }
  avatarLoading.value = true
  try {
    const uploaded = await uploadUserAvatar(avatarFile.value)
    const uploadedUrl = uploaded.data?.url
    if (!uploadedUrl) throw new Error('头像上传失败')
    await updateUserAvatar({ avatar: uploadedUrl })
    ElMessage.success('头像修改成功')
    showAvatarDialog.value = false
    await loadProfile()
    await userStore.getInfo()
  } finally {
    avatarLoading.value = false
  }
}

// ==================== 两步验证 ====================
const totpLoading = ref(false)
const totpEnabled = ref(false)
const showSetupDialog = ref(false)
const showUnbindDialog = ref(false)
const totpSetupData = reactive({ secret: '', qrCode: '', otpAuthUrl: '' })
const totpBindCode = ref('')
const totpBindLoading = ref(false)
const unbindPassword = ref('')
const unbindLoading = ref(false)

const loadTotpStatus = async () => {
  totpLoading.value = true
  try {
    const res = await getTotpStatus()
    totpEnabled.value = (res.data as any)?.enabled === true
  } catch { /* ignore */ } finally {
    totpLoading.value = false
  }
}

const handleSetupTotp = async () => {
  try {
    const res = await getTotpSetup()
    const data = res.data as any
    totpSetupData.secret = data.secret
    totpSetupData.qrCode = data.qrCode
    totpSetupData.otpAuthUrl = data.otpAuthUrl
    totpBindCode.value = ''
    showSetupDialog.value = true
  } catch { /* error handled by interceptor */ }
}

const handleBindTotp = async () => {
  if (!totpBindCode.value || totpBindCode.value.length !== 6) {
    ElMessage.warning('请输入6位验证码')
    return
  }
  totpBindLoading.value = true
  try {
    await bindTotp(totpSetupData.secret, totpBindCode.value)
    ElMessage.success('两步验证绑定成功')
    showSetupDialog.value = false
    totpEnabled.value = true
  } catch { /* error handled */ } finally {
    totpBindLoading.value = false
  }
}

const copySecret = () => {
  navigator.clipboard.writeText(totpSetupData.secret)
  ElMessage.success('密钥已复制')
}

const handleUnbindTotp = async () => {
  if (!unbindPassword.value) {
    ElMessage.warning('请输入当前密码')
    return
  }
  unbindLoading.value = true
  try {
    await unbindTotp(unbindPassword.value)
    ElMessage.success('两步验证已解除')
    showUnbindDialog.value = false
    unbindPassword.value = ''
    totpEnabled.value = false
  } catch { /* error handled */ } finally {
    unbindLoading.value = false
  }
}

onMounted(() => {
  loadProfile()
  loadTotpStatus()
})
onBeforeUnmount(() => { if (avatarObjectUrl) URL.revokeObjectURL(avatarObjectUrl) })
</script>

<style lang="scss" scoped>
.profile-container {
  padding: 20px;
}

.user-info-card {
  .avatar-section {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 20px 0;

    .change-avatar-btn {
      margin-top: 10px;
    }
  }

  .user-info-list {
    list-style: none;
    padding: 0;
    margin: 0;

    li {
      display: flex;
      align-items: center;
      padding: 12px 0;
      border-bottom: 1px solid #f3f4f6;
      font-size: 14px;

      &:last-child {
        border-bottom: none;
      }

      .el-icon {
        color: #2563eb;
        margin-right: 8px;
        font-size: 16px;
      }

      .label {
        color: #6b7280;
        width: 70px;
      }

      .value {
        color: #111827;
        flex: 1;
      }
    }
  }
}

.avatar-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding-left: 80px;

  .label {
    color: #6b7280;
    font-size: 14px;
  }
}

.totp-section {
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.totp-setup {
  .totp-step {
    color: #374151;
    font-size: 14px;
    margin: 16px 0 8px;
    font-weight: 500;

    &:first-child { margin-top: 0; }
  }

  .totp-qr {
    display: flex;
    justify-content: center;
    padding: 16px 0;

    img {
      width: 200px;
      height: 200px;
      border: 1px solid #e5e7eb;
      border-radius: 8px;
    }
  }

  .totp-secret {
    font-family: monospace;
    font-size: 13px;
  }

  .totp-code-input {
    :deep(.el-input__inner) {
      text-align: center;
      font-size: 22px;
      letter-spacing: 6px;
      font-weight: 600;
    }
  }
}
</style>
