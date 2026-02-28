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
              <el-button type="primary" link class="change-avatar-btn" @click="showAvatarDialog = true">
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

    <!-- 修改头像对话框 -->
    <el-dialog v-model="showAvatarDialog" title="修改头像" width="400px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="头像地址">
          <el-input v-model="avatarUrl" placeholder="请输入头像URL地址" />
        </el-form-item>
        <div class="avatar-preview" v-if="avatarUrl">
          <span class="label">预览：</span>
          <el-avatar :size="80" :src="avatarUrl" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Iphone, Message, OfficeBuilding, Calendar } from '@element-plus/icons-vue'
import { getUserProfile, updateUserProfile, updateUserPassword, updateUserAvatar } from '@/api/system/user'
import { useUserStore } from '@/stores/user'
import type { FormInstance, FormRules } from 'element-plus'

const userStore = useUserStore()
const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

const activeTab = ref('info')
const infoLoading = ref(false)
const pwdLoading = ref(false)
const avatarLoading = ref(false)
const showAvatarDialog = ref(false)
const avatarUrl = ref('')

const infoFormRef = ref<FormInstance>()
const pwdFormRef = ref<FormInstance>()

// 用户信息
const userInfo = reactive({
  userId: 0,
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
      window.location.href = '/login'
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

// 修改头像
const handleUpdateAvatar = async () => {
  if (!avatarUrl.value) {
    ElMessage.warning('请输入头像地址')
    return
  }
  avatarLoading.value = true
  try {
    await updateUserAvatar({ avatar: avatarUrl.value })
    ElMessage.success('头像修改成功')
    showAvatarDialog.value = false
    await loadProfile()
    await userStore.getInfo()
  } finally {
    avatarLoading.value = false
  }
}

onMounted(() => {
  loadProfile()
})
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
      border-bottom: 1px solid #f0f0f0;
      font-size: 14px;

      &:last-child {
        border-bottom: none;
      }

      .el-icon {
        color: #409eff;
        margin-right: 8px;
        font-size: 16px;
      }

      .label {
        color: #606266;
        width: 70px;
      }

      .value {
        color: #333;
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
    color: #606266;
    font-size: 14px;
  }
}
</style>
