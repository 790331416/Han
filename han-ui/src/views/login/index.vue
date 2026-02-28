<template>
  <div class="login-container">
    <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" class="login-form">
      <h3 class="title">HAN Cloud</h3>
      <p class="subtitle">企业级多租户微服务平台</p>
      
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
import { User, Lock, Key } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { getCaptcha } from '@/api/auth'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const captchaEnabled = ref(true)
const captchaImg = ref('')
const rememberMe = ref(false)

const loginForm = reactive({
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

onMounted(() => {
  getCaptchaImg()
})
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-form {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.title {
  text-align: center;
  color: #333;
  margin-bottom: 10px;
  font-size: 28px;
}

.subtitle {
  text-align: center;
  color: #999;
  margin-bottom: 30px;
  font-size: 14px;
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
  
  .el-input {
    flex: 1;
  }
  
  .captcha-img {
    width: 120px;
    height: 40px;
    cursor: pointer;
    border-radius: 4px;
  }
}

.login-btn {
  width: 100%;
}

.footer {
  position: fixed;
  bottom: 20px;
  left: 0;
  right: 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
}
</style>
