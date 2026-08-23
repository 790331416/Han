<template>
  <div class="public-page" data-testid="vendor-apply-page">
    <div class="public-card">
      <div class="page-header">
        <div>
          <h1>厂商入驻申请</h1>
          <p>提交资料后由平台审核，审核结果可凭联系人电话查询。</p>
        </div>
        <el-button link @click="router.push('/login')">返回登录</el-button>
      </div>

      <el-alert
        title="请使用真实的联系人和企业信息；账号密码仅用于本次注册请求，不会保存到浏览器。"
        type="info"
        :closable="false"
        show-icon
        class="notice"
      />
      <el-alert
        v-if="httpTestMode"
        title="当前为 HTTP 测试兼容模式，密码会以明文提交。仅限受控测试环境，请在正式环境关闭该开关并使用 HTTPS。"
        type="warning"
        :closable="false"
        show-icon
        class="notice"
        data-testid="vendor-apply-insecure-http-warning"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-width="138px" class="apply-form">
        <section class="form-section">
          <div class="section-heading"><strong>账号信息</strong><span>用于登录厂商门户和查询申请进度</span></div>
          <div class="form-grid">
            <el-form-item label="登录账号" prop="username">
              <el-input v-model="form.username" data-testid="vendor-apply-username" autocomplete="username" placeholder="请输入登录账号" />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" data-testid="vendor-apply-nickname" placeholder="请输入联系人昵称" />
            </el-form-item>
            <el-form-item label="登录密码" prop="password">
              <el-input v-model="form.password" data-testid="vendor-apply-password" type="password" show-password autocomplete="new-password" placeholder="请输入登录密码" />
            </el-form-item>
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" data-testid="vendor-apply-phone" autocomplete="tel" placeholder="用于查询审核结果" />
            </el-form-item>
            <el-form-item v-if="captchaEnabled" label="验证码" prop="code" data-testid="vendor-apply-captcha">
              <div class="captcha-row">
                <el-input v-model="form.code" data-testid="vendor-apply-code" placeholder="请输入验证码" />
                <img :src="captchaImg" alt="验证码" class="captcha-img" @click="loadCaptcha" />
              </div>
            </el-form-item>
            <el-alert v-if="captchaError" :title="captchaError" type="error" :closable="false" show-icon class="field-alert" data-testid="vendor-apply-captcha-error" />
          </div>
        </section>

        <section class="form-section">
          <div class="section-heading"><strong>企业资料</strong><span>带 <em>*</em> 的项目为必填</span></div>
          <div class="form-grid">
            <el-form-item label="厂商名称" prop="name">
              <el-input v-model="form.name" data-testid="vendor-apply-name" placeholder="请输入企业/机构名称" />
            </el-form-item>
            <el-form-item label="统一社会信用代码" prop="qualificationNo">
              <el-input v-model="form.qualificationNo" data-testid="vendor-apply-qualification" placeholder="请输入统一社会信用代码" />
            </el-form-item>
            <el-form-item label="企业联系人" prop="contactName">
              <el-input v-model="form.contactName" data-testid="vendor-apply-contact-name" placeholder="请输入企业联系人" />
            </el-form-item>
            <el-form-item label="联系人电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" data-testid="vendor-apply-contact-phone" placeholder="请输入联系人电话" />
            </el-form-item>
          </div>
          <el-collapse class="optional-section">
            <el-collapse-item title="补充信息（选填）" name="optional">
              <div class="form-grid optional-grid">
                <el-form-item label="联系邮箱" prop="email">
                  <el-input v-model="form.email" autocomplete="email" placeholder="请输入账号邮箱" />
                </el-form-item>
                <el-form-item label="所属行业">
                  <el-input v-model="form.industry" placeholder="请输入所属行业" />
                </el-form-item>
                <el-form-item label="官网地址">
                  <el-input v-model="form.website" placeholder="https://" />
                </el-form-item>
                <el-form-item label="联系人邮箱">
                  <el-input v-model="form.contactEmail" placeholder="请输入联系人邮箱" />
                </el-form-item>
                <el-form-item label="申请说明" class="full-row">
                  <el-input v-model="form.applyReason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请说明接入场景和申请原因" />
                </el-form-item>
              </div>
            </el-collapse-item>
          </el-collapse>
        </section>

        <el-alert v-if="publicKeyError" :title="publicKeyError" type="error" :closable="false" show-icon class="field-alert" data-testid="vendor-apply-public-key-error" />
        <div class="form-actions">
          <el-button @click="router.push('/login')">取消</el-button>
          <el-button type="primary" :loading="submitting" :disabled="!canSubmit" data-testid="vendor-apply-submit" @click="submitApplication">提交申请</el-button>
        </div>
      </el-form>

      <el-divider content-position="left">查询申请进度</el-divider>
      <el-form :model="statusQuery" :inline="true" class="status-form" @submit.prevent="queryStatus">
        <el-form-item label="联系人电话">
          <el-input v-model="statusQuery.contactPhone" data-testid="vendor-apply-status-phone" placeholder="提交申请时的联系人电话" clearable />
        </el-form-item>
        <el-button type="primary" :loading="queryLoading" @click="queryStatus">查询</el-button>
      </el-form>
      <el-descriptions v-if="statusResult" :column="2" border class="status-result">
        <el-descriptions-item label="状态"><el-tag :type="statusTagType(statusResult.status)">{{ statusLabel(statusResult.status, statusResult.statusName) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ formatDate(statusResult.createTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ formatDate(statusResult.reviewTime) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核说明" :span="2">{{ statusResult.reason || '暂无审核说明' }}</el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { getCaptcha } from '@/api/auth'
import { getVendorPublicKey, submitPublicVendorApplication, getPublicVendorApplication, type PublicVendorApplicationForm, type PublicVendorApplicationRequest, type PublicVendorApplicationStatus } from '@/api/open/portal'
import { isWebCryptoAvailable, rsaEncrypt } from '@/utils/crypto'
import { formatDate } from '@/utils/request'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const queryLoading = ref(false)
const captchaEnabled = ref(true)
const captchaReady = ref(false)
const captchaImg = ref('')
const captchaError = ref('')
const encryptEnabled = ref(false)
const rsaPublicKey = ref('')
const publicKeyError = ref('')
const statusResult = ref<PublicVendorApplicationStatus>()
const webCryptoAvailable = isWebCryptoAvailable()
const allowInsecureHttp = ref(false)
const httpTestMode = computed(() => !webCryptoAvailable && allowInsecureHttp.value)
const canSubmit = computed(() => !submitting.value
  && (!captchaEnabled.value || captchaReady.value)
  && (webCryptoAvailable ? encryptEnabled.value && Boolean(rsaPublicKey.value) : httpTestMode.value))

const form = reactive<PublicVendorApplicationForm>({
  username: '', nickname: '', password: '', phone: '', email: '', name: '', qualificationNo: '', industry: '',
  contactName: '', contactPhone: '', contactEmail: '', website: '', applyReason: '', code: '', uuid: ''
})
const statusQuery = reactive({ contactPhone: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [{ required: true, min: 8, message: '密码至少8位', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  code: [{ validator: (_rule, value, callback) => captchaEnabled.value && !value ? callback(new Error('请输入验证码')) : callback(), trigger: 'blur' }],
  name: [{ required: true, message: '请输入厂商名称', trigger: 'blur' }],
  qualificationNo: [{ required: true, message: '请输入统一社会信用代码', trigger: 'blur' }],
  contactName: [{ required: true, message: '请输入企业联系人', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请输入联系人电话', trigger: 'blur' }]
}

async function loadCaptcha() {
  captchaReady.value = false
  captchaError.value = ''
  try {
    const response = await getCaptcha()
    const enabled = (response.data as { enabled?: unknown } | undefined)?.enabled
    if (enabled === 'false' || enabled === false) {
      captchaEnabled.value = false
      captchaReady.value = true
      form.code = ''
      form.uuid = ''
      return
    }
    captchaEnabled.value = true
    captchaImg.value = `data:image/gif;base64,${response.data?.img || ''}`
    form.uuid = response.data?.uuid || ''
    if (!response.data?.uuid || !response.data?.img) {
      captchaError.value = '验证码加载失败，请刷新页面后重试'
      return
    }
    captchaReady.value = true
  } catch {
    captchaEnabled.value = true
    captchaReady.value = false
    captchaImg.value = ''
    form.code = ''
    form.uuid = ''
    captchaError.value = '验证码加载失败，请刷新页面后重试'
  }
}

async function loadPublicKey() {
  encryptEnabled.value = false
  rsaPublicKey.value = ''
  publicKeyError.value = ''
  allowInsecureHttp.value = false
  try {
    const response = await getVendorPublicKey()
    allowInsecureHttp.value = response.data?.allowInsecureHttp === true
    if (!webCryptoAvailable) {
      if (!allowInsecureHttp.value) {
        publicKeyError.value = '当前站点使用HTTP，系统未开启测试兼容。请使用HTTPS，或由管理员在系统设置中临时开启测试兼容。'
      }
      return
    }
    if (response.data?.enabled && response.data.publicKey) {
      encryptEnabled.value = true
      rsaPublicKey.value = response.data.publicKey
      return
    }
    publicKeyError.value = '注册加密公钥暂不可用，请刷新页面后重试'
  } catch {
    encryptEnabled.value = false
    publicKeyError.value = '注册加密公钥加载失败，请刷新页面后重试'
  }
}

async function submitApplication() {
  let encryptedPassword: string | undefined
  let payload: PublicVendorApplicationRequest | undefined
  try {
    if (!(await formRef.value?.validate())) return
    if (captchaEnabled.value && !captchaReady.value) {
      captchaError.value = '验证码加载失败，请刷新页面后重试'
      ElMessage.error(captchaError.value)
      return
    }
    submitting.value = true
    if (webCryptoAvailable) {
      if (!encryptEnabled.value || !rsaPublicKey.value) {
        publicKeyError.value = publicKeyError.value || '注册加密公钥加载失败，请刷新页面后重试'
        ElMessage.error(publicKeyError.value)
        return
      }
      encryptedPassword = await rsaEncrypt(form.password, rsaPublicKey.value)
    } else if (!httpTestMode.value) {
      publicKeyError.value = publicKeyError.value || '当前环境不允许HTTP明文提交注册密码'
      ElMessage.error(publicKeyError.value)
      return
    }
    payload = {
      username: form.username,
      nickname: form.nickname,
      phone: form.phone,
      email: form.email,
      name: form.name,
      qualificationNo: form.qualificationNo,
      industry: form.industry,
      contactName: form.contactName,
      contactPhone: form.contactPhone,
      contactEmail: form.contactEmail,
      website: form.website,
      applyReason: form.applyReason,
      encryptedPassword,
      plainPassword: webCryptoAvailable ? undefined : form.password,
      captchaCode: form.code || undefined,
      captchaUuid: form.uuid || undefined
    }
    const response = await submitPublicVendorApplication(payload)
    const data = response.data
    const applicationNo = typeof data === 'string' ? data : data?.applicationNo
    if (!applicationNo) throw new Error('申请已提交，但未返回申请编号')
    statusQuery.contactPhone = form.contactPhone
    statusResult.value = undefined
    ElMessage.success('申请已提交，可凭联系人电话查询审核进度')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交入驻申请失败')
    if (captchaEnabled.value) await loadCaptcha()
  } finally {
    submitting.value = false
    form.password = ''
    if (payload) {
      payload.encryptedPassword = undefined
      payload.plainPassword = undefined
    }
    payload = undefined
    encryptedPassword = undefined
  }
}

async function queryStatus() {
  if (!statusQuery.contactPhone) {
    ElMessage.warning('请输入联系人电话')
    return
  }
  queryLoading.value = true
  try {
    const response = await getPublicVendorApplication(statusQuery.contactPhone.trim())
    statusResult.value = response.data
  } catch (error) {
    statusResult.value = undefined
    ElMessage.error(error instanceof Error ? error.message : '查询申请进度失败')
  } finally {
    queryLoading.value = false
  }
}

function statusLabel(status?: number, statusName?: string) {
  if (statusName) return statusName
  return ({ 0: '待提交', 1: '待审核', 2: '审核通过', 3: '审核驳回' } as Record<number, string>)[status ?? -1] || '未知'
}

function statusTagType(status?: number) {
  return ({ 2: 'success', 3: 'danger', 1: 'warning' } as Record<number, 'success' | 'danger' | 'warning'>)[status ?? -1] || 'info'
}

onMounted(() => { loadCaptcha(); loadPublicKey() })
</script>

<style lang="scss" scoped>
.public-page { min-height: 100vh; padding: 32px 16px; background: #f5f7fa; }
.public-card { max-width: 980px; margin: 0 auto; padding: 28px 32px; background: #fff; border-radius: 12px; box-shadow: 0 8px 30px rgb(15 23 42 / 0.06); }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.page-header h1 { margin: 0 0 8px; color: #1f2937; font-size: 24px; }
.page-header p { margin: 0; color: #6b7280; font-size: 13px; }
.notice { margin: 16px 0; }
.apply-form { display: grid; gap: 16px; }
.form-section { padding: 20px 20px 4px; border: 1px solid #e9edf3; border-radius: 10px; background: #fff; }
.section-heading { display: flex; align-items: baseline; gap: 10px; margin-bottom: 16px; }
.section-heading strong { color: #1f2937; font-size: 15px; }
.section-heading span { color: #909399; font-size: 12px; }
.section-heading em { color: var(--el-color-danger); font-style: normal; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; }
.full-row { grid-column: 1 / -1; }
.optional-section { margin: 0 0 16px; border-top: 1px solid #ebeef5; }
.optional-section :deep(.el-collapse-item__header) { color: #606266; font-size: 13px; }
.optional-section :deep(.el-collapse-item__wrap) { border-bottom: 0; }
.captcha-row { display: flex; gap: 10px; width: 100%; }
.captcha-row :deep(.el-input) { flex: 1; }
.captcha-img { width: 118px; height: 32px; cursor: pointer; border: 1px solid var(--el-border-color); border-radius: 4px; object-fit: cover; }
.form-actions { display: flex; justify-content: center; gap: 12px; margin: 4px 0 18px; }
.field-alert { grid-column: 1 / -1; margin: 0 0 12px; }
.status-form { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.status-form :deep(.el-form-item) { margin-bottom: 12px; }
.status-result { margin-top: 8px; }
@media (max-width: 720px) {
  .public-card { padding: 20px 16px; }
  .form-section { padding: 16px 14px 2px; }
  .form-grid { grid-template-columns: 1fr; }
  .full-row { grid-column: auto; }
  .page-header { align-items: center; }
}
</style>
