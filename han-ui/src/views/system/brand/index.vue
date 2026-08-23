<template>
  <div class="app-container brand-setting-page">
    <el-card shadow="never" class="brand-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">系统设置</div>
            <div class="card-desc">统一维护系统名称、Logo、登录页文案和开放平台测试安全开关。</div>
          </div>
        </div>
      </template>

      <el-alert
        title="此设置影响所有租户、管理端与校端，只有获得修改权限的角色可以保存。"
        type="warning"
        :closable="false"
        show-icon
        class="mb-5"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px" class="brand-form">
        <el-form-item label="系统全称" prop="fullName">
          <el-input v-model="form.fullName" :disabled="!canEdit" maxlength="64" show-word-limit placeholder="例如：巴蜀云校管理平台" />
        </el-form-item>
        <el-form-item label="系统简称" prop="shortName">
          <el-input v-model="form.shortName" :disabled="!canEdit" maxlength="32" show-word-limit placeholder="例如：巴蜀云校" />
        </el-form-item>
        <el-form-item label="统一展示名称" prop="displayMode">
          <el-radio-group v-model="form.displayMode" :disabled="!canEdit">
            <el-radio value="FULL_NAME">使用系统全称</el-radio>
            <el-radio value="SHORT_NAME">使用系统简称</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="登录页副标题" prop="loginSubtitle">
          <el-input
            v-model="form.loginSubtitle"
            :disabled="!canEdit"
            maxlength="128"
            show-word-limit
            clearable
            placeholder="可留空，留空后登录页不显示副标题"
          />
        </el-form-item>
        <el-form-item>
          <template #label>
            <span class="logo-label">
              品牌 Logo
              <el-tooltip content="透明背景正方形 PNG 效果最好；支持 PNG、JPG、WebP，文件不超过 1MB。" placement="top">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <div class="logo-setting">
            <div class="logo-preview">
              <img v-if="logoPreview" :src="logoPreview" alt="Logo 预览" />
              <span v-else>暂无 Logo</span>
            </div>
            <el-upload
              v-if="canEdit"
              :auto-upload="false"
              :show-file-list="false"
              accept=".png,.jpg,.jpeg,.webp"
              :on-change="handleLogoChange"
            >
              <el-button>选择 Logo</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="展示预览">
          <div class="brand-preview">
            <strong>{{ previewName }}</strong>
            <span v-if="form.loginSubtitle">{{ form.loginSubtitle }}</span>
          </div>
        </el-form-item>
        <el-divider content-position="left">开放平台测试</el-divider>
        <el-form-item label="允许 HTTP 厂商注册">
          <div class="security-setting">
            <el-switch
              v-model="form.allowInsecureVendorRegistration"
              :disabled="!canEdit"
              active-text="测试兼容已开启"
              inactive-text="默认强制加密"
            />
            <el-alert
              title="仅测试环境临时开启。开启后，HTTP 访问的厂商注册可提交明文密码；正式环境请保持关闭并使用 HTTPS。"
              type="warning"
              :closable="false"
              show-icon
            />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button v-if="canEdit" type="primary" :loading="saving" @click="submit">保存设置</el-button>
          <el-button @click="load">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { getSystemBrand, updateSystemBrand, uploadSystemBrandLogo, type BrandDisplayMode } from '@/api/system/brand'
import { useBrandStore } from '@/stores/brand'
import { useUserStore } from '@/stores/user'

const brandStore = useBrandStore()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const saving = ref(false)
const logoFile = ref<File | null>(null)
const logoPreview = ref('')
let objectLogoUrl = ''
const form = reactive({
  fullName: '',
  shortName: '',
  displayMode: 'FULL_NAME' as BrandDisplayMode,
  loginSubtitle: '',
  allowInsecureVendorRegistration: false
})

const rules: FormRules = {
  fullName: [{ required: true, message: '请输入系统全称', trigger: 'blur' }],
  shortName: [{ required: true, message: '请输入系统简称', trigger: 'blur' }],
  displayMode: [{ required: true, message: '请选择统一展示名称', trigger: 'change' }]
}

const previewName = computed(() => form.displayMode === 'SHORT_NAME' ? form.shortName : form.fullName)
const canEdit = computed(() => userStore.hasPermission('system:brand:edit'))

function setLogoPreview(url: string) {
  if (objectLogoUrl) URL.revokeObjectURL(objectLogoUrl)
  objectLogoUrl = url.startsWith('blob:') ? url : ''
  logoPreview.value = url
}

function handleLogoChange(uploadFile: UploadFile) {
  const file = uploadFile.raw
  if (!file) return
  const validType = ['image/png', 'image/jpeg', 'image/webp'].includes(file.type)
    || /\.(png|jpe?g|webp)$/i.test(file.name)
  if (!validType) {
    ElMessage.warning('Logo仅支持PNG、JPG或WebP格式')
    return
  }
  if (file.size > 1024 * 1024) {
    ElMessage.warning('Logo文件不能超过1MB')
    return
  }
  logoFile.value = file
  setLogoPreview(URL.createObjectURL(file))
}

async function load() {
  const res = await getSystemBrand()
  const data = (res as any).data
  Object.assign(form, {
    fullName: data.fullName,
    shortName: data.shortName,
    displayMode: data.displayMode,
    loginSubtitle: data.loginSubtitle || '',
    allowInsecureVendorRegistration: Boolean(data.allowInsecureVendorRegistration)
  })
  logoFile.value = null
  setLogoPreview(data.logoUrl || '')
}

async function submit() {
  const valid = await formRef.value?.validate()
  if (!valid) return
  saving.value = true
  try {
    let res = await updateSystemBrand(form)
    if (logoFile.value) res = await uploadSystemBrandLogo(logoFile.value)
    brandStore.apply((res as any).data)
    logoFile.value = null
    setLogoPreview((res as any).data.logoUrl || '')
    ElMessage.success('系统设置已保存，当前页面已同步更新')
  } finally {
    saving.value = false
  }
}

onMounted(() => { load() })
onBeforeUnmount(() => { if (objectLogoUrl) URL.revokeObjectURL(objectLogoUrl) })
</script>

<style lang="scss" scoped>
.brand-card { max-width: 760px; }
.card-header { display: flex; align-items: center; }
.card-title { color: #111827; font-size: 16px; font-weight: 600; }
.card-desc { color: #6b7280; font-size: 13px; margin-top: 5px; }
.brand-form { max-width: 620px; }
.brand-preview { display: flex; flex-direction: column; gap: 4px; padding: 10px 14px; min-width: 260px; border-radius: 8px; background: #f8fafc; color: #6b7280; }
.brand-preview strong { color: #111827; font-size: 18px; }
.logo-label { display: inline-flex; align-items: center; gap: 4px; }
.logo-label .el-icon { color: #909399; cursor: help; }
.logo-setting { display: flex; align-items: center; gap: 14px; }
.logo-preview { width: 72px; height: 72px; border: 1px solid #dcdfe6; border-radius: 6px; display: flex; align-items: center; justify-content: center; color: #909399; font-size: 12px; overflow: hidden; }
.logo-preview img { width: 100%; height: 100%; object-fit: contain; }
.security-setting { display: grid; gap: 12px; width: min(100%, 520px); }
html.dark .card-title, html.dark .brand-preview strong { color: #f9fafb; }
html.dark .brand-preview { background: #1f2937; color: #9ca3af; }
html.dark .logo-preview { border-color: #4c4d4f; }
</style>
