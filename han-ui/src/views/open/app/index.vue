<template>
  <div class="app-container" data-testid="open-app-page">
    <el-card shadow="never" class="search-form">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="queryParams.appName" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="应用类型" prop="appType">
          <el-select v-model="queryParams.appType" placeholder="请选择" clearable style="width: 140px">
            <el-option label="Web应用" value="web" />
            <el-option label="移动应用" value="mobile" />
            <el-option label="服务端" value="server" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" data-testid="open-app-search-button" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" data-testid="open-app-reset-button" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>开放应用列表</span>
          <el-button type="primary" :icon="Plus" data-testid="open-app-add-button" @click="handleAdd">新增应用</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="appList" data-testid="open-app-table">
        <el-table-column label="应用名称" prop="appName" min-width="180" show-overflow-tooltip />
        <el-table-column label="AppKey" prop="appKey" min-width="250" show-overflow-tooltip />
        <el-table-column label="应用类型" prop="appType" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.appType === 'web'">Web</el-tag>
            <el-tag v-else-if="row.appType === 'mobile'" type="success">移动端</el-tag>
            <el-tag v-else type="info">{{ row.appType || '服务端' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="联系人" prop="contactName" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 0"
              :data-testid="`open-app-status-switch-${row.appId}`"
              @change="(val: any) => handleStatusChange(row, !!val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" min-width="180" :formatter="(_r: any, _c: any, v: any) => $formatDate(v)" />
        <el-table-column label="操作" min-width="250">
          <template #default="{ row }">
            <el-button type="primary" link :icon="Edit" :data-testid="`open-app-edit-button-${row.appId}`" @click="handleEdit(row)">编辑</el-button>
            <el-button type="warning" link :icon="RefreshRight" :data-testid="`open-app-reset-secret-button-${row.appId}`" @click="handleResetSecret(row)">重置密钥</el-button>
            <el-button type="danger" link :icon="Delete" :data-testid="`open-app-delete-button-${row.appId}`" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="mt-pagination"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="55%" class="dialog-md" destroy-on-close data-testid="open-app-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="form.appName" placeholder="请输入应用名称" data-testid="open-app-form-name" />
        </el-form-item>
        <el-form-item label="应用类型" prop="appType">
          <el-select v-model="form.appType" placeholder="请选择" data-testid="open-app-form-type">
            <el-option label="Web应用" value="web" />
            <el-option label="移动应用" value="mobile" />
            <el-option label="服务端" value="server" />
          </el-select>
        </el-form-item>
        <el-form-item label="应用描述">
          <el-input v-model="form.appDesc" type="textarea" placeholder="请输入应用描述" data-testid="open-app-form-desc" />
        </el-form-item>

        <el-divider content-position="left">OAuth 配置</el-divider>

        <el-form-item label="授权类型" prop="grantTypes">
          <el-checkbox-group v-model="form.grantTypes" data-testid="open-app-form-grant-types">
            <el-checkbox v-for="item in grantTypeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-checkbox>
          </el-checkbox-group>
          <div class="form-hint-block">后端按此列表校验 grant_type，未勾选的类型无法签发令牌。</div>
        </el-form-item>
        <el-form-item label="授权范围">
          <el-select
            v-model="form.scopes"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入授权范围"
            style="width: 100%"
            data-testid="open-app-form-scopes"
          >
            <el-option v-for="item in scopeSuggestions" :key="item" :label="item" :value="item" />
          </el-select>
          <div class="form-hint-block">留空时后端按默认值 openid,profile 处理。</div>
        </el-form-item>
        <el-form-item label="回调地址" prop="redirectUris">
          <el-input v-model="redirectUrisStr" type="textarea" placeholder="多个地址用换行分隔，需为 http/https 完整地址" :rows="3" data-testid="open-app-form-redirect-uris" />
          <div class="form-hint-block">授权时按登记地址精确匹配，必须与接入方实际回调地址完全一致。</div>
        </el-form-item>
        <el-form-item label="登出回调地址" prop="logoutUri">
          <el-input v-model="form.logoutUri" placeholder="可选，需为 http/https 完整地址" data-testid="open-app-form-logout-uri" />
        </el-form-item>
        <el-form-item label="强制 PKCE">
          <el-switch
            :model-value="form.requirePkce === 1"
            data-testid="open-app-form-require-pkce"
            @update:model-value="(val: any) => (form.requirePkce = val ? 1 : 0)"
          />
          <span class="form-hint">公共客户端（移动端 / 单页应用）建议开启</span>
        </el-form-item>
        <el-form-item label="免授权确认">
          <el-switch
            :model-value="form.autoApprove === 1"
            data-testid="open-app-form-auto-approve"
            @update:model-value="(val: any) => (form.autoApprove = val ? 1 : 0)"
          />
          <span class="form-hint">开启后用户授权时不再展示确认页</span>
        </el-form-item>
        <el-form-item label="AccessToken有效期">
          <el-input-number v-model="form.accessTokenTtl" :min="60" :step="3600" data-testid="open-app-form-access-token-ttl" />
          <span class="form-hint">秒</span>
        </el-form-item>
        <el-form-item label="RefreshToken有效期">
          <el-input-number v-model="form.refreshTokenTtl" :min="60" :step="3600" data-testid="open-app-form-refresh-token-ttl" />
          <span class="form-hint">秒</span>
        </el-form-item>

        <el-divider content-position="left">联系人与备注</el-divider>

        <el-form-item label="联系人">
          <el-input v-model="form.contactName" placeholder="请输入联系人" data-testid="open-app-form-contact-name" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" data-testid="open-app-form-contact-phone" />
        </el-form-item>
        <el-form-item label="联系邮箱" prop="contactEmail">
          <el-input v-model="form.contactEmail" placeholder="请输入联系邮箱" data-testid="open-app-form-contact-email" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" data-testid="open-app-form-remark" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status" data-testid="open-app-form-status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button data-testid="open-app-dialog-cancel" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" data-testid="open-app-dialog-submit" @click="submitForm" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密钥结果弹窗：密钥只在这一次可见 -->
    <el-dialog v-model="secretVisible" title="密钥已重置" width="45%" class="dialog-sm" data-testid="open-app-secret-dialog">
      <el-alert type="warning" show-icon :closable="false" class="secret-alert">
        新密钥仅本次可见，关闭后无法再次查看，请立即复制并交给接入方。
      </el-alert>
      <el-input :model-value="newSecret" readonly data-testid="open-app-secret-value">
        <template #append>
          <el-button :icon="CopyDocument" data-testid="open-app-secret-copy" @click="handleCopySecret">
            {{ secretCopied ? '已复制' : '复制' }}
          </el-button>
        </template>
      </el-input>
      <template #footer>
        <el-button type="primary" data-testid="open-app-secret-close" @click="secretVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh, Plus, Edit, Delete, RefreshRight, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  listOpenApp, getOpenApp, addOpenApp, updateOpenApp, deleteOpenApp, resetAppSecret, changeAppStatus,
  grantTypeOptions, scopeSuggestions,
  type OpenApp, type OpenAppForm
} from '@/api/open/app'

const loading = ref(false)
const submitLoading = ref(false)
const appList = ref<OpenApp[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const secretVisible = ref(false)
const secretCopied = ref(false)
const newSecret = ref('')

const queryParams = reactive({ appName: '', appType: '' as string | undefined, status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

const defaultForm = (): OpenAppForm => ({
  appId: undefined,
  appName: '',
  appDesc: '',
  appType: 'web',
  logoutUri: '',
  redirectUris: [],
  scopes: ['openid', 'profile'],
  grantTypes: ['authorization_code', 'refresh_token'],
  accessTokenTtl: 7200,
  refreshTokenTtl: 604800,
  requirePkce: 0,
  autoApprove: 0,
  status: 0,
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  remark: ''
})

const form = reactive<OpenAppForm>(defaultForm())

const redirectUrisStr = computed({
  get: () => (form.redirectUris || []).join('\n'),
  set: (val: string) => { form.redirectUris = val.split('\n').map(s => s.trim()).filter(s => s) }
})

/** 回调地址是 OAuth 授权的安全基石，必须是带 host 的 http/https 绝对地址 */
function isValidHttpUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return (url.protocol === 'http:' || url.protocol === 'https:') && !!url.host
  } catch {
    return false
  }
}

const rules: FormRules = {
  appName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
  appType: [{ required: true, message: '请选择应用类型', trigger: 'change' }],
  grantTypes: [{
    validator: (_rule, _value, callback) => {
      if (!form.grantTypes || form.grantTypes.length === 0) {
        callback(new Error('请至少选择一种授权类型'))
        return
      }
      callback()
    },
    trigger: 'change'
  }],
  redirectUris: [{
    validator: (_rule, _value, callback) => {
      const invalid = (form.redirectUris || []).filter(uri => !isValidHttpUrl(uri))
      if (invalid.length) {
        callback(new Error(`回调地址格式不合法：${invalid.join('、')}`))
        return
      }
      callback()
    },
    trigger: 'blur'
  }],
  logoutUri: [{
    validator: (_rule, value, callback) => {
      if (value && !isValidHttpUrl(value)) {
        callback(new Error('登出回调地址必须是 http/https 完整地址'))
        return
      }
      callback()
    },
    trigger: 'blur'
  }],
  contactEmail: [{
    validator: (_rule, value, callback) => {
      if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
        callback(new Error('联系邮箱格式不合法'))
        return
      }
      callback()
    },
    trigger: 'blur'
  }]
}

onMounted(() => { getList() })

async function getList() {
  loading.value = true
  try {
    const res = await listOpenApp(queryParams)
    const data = (res as any).data
    appList.value = data?.records || data?.rows || []
    total.value = data?.total || 0
  } catch { /* 接口不可用 */ } finally {
    loading.value = false
  }
}

function handleQuery() { queryParams.pageNum = 1; getList() }

function resetQuery() {
  queryParams.appName = ''; queryParams.appType = undefined; queryParams.status = undefined
  handleQuery()
}

function handleAdd() {
  resetForm()
  dialogTitle.value = '新增应用'
  dialogVisible.value = true
}

async function handleEdit(row: OpenApp) {
  resetForm()
  dialogTitle.value = '编辑应用'
  try {
    const res = await getOpenApp(row.appId)
    const d = (res as any).data
    // 后端已建模的字段全部回填，缺一个就会在保存时被后端按默认值覆盖
    Object.assign(form, {
      appId: d.appId,
      appName: d.appName,
      appDesc: d.appDesc,
      appType: d.appType,
      logoutUri: d.logoutUri || '',
      redirectUris: d.redirectUris || [],
      scopes: d.scopes || [],
      grantTypes: d.grantTypes || [],
      accessTokenTtl: d.accessTokenTtl,
      refreshTokenTtl: d.refreshTokenTtl,
      requirePkce: d.requirePkce ?? 0,
      autoApprove: d.autoApprove ?? 0,
      status: d.status,
      contactName: d.contactName,
      contactPhone: d.contactPhone,
      contactEmail: d.contactEmail,
      remark: d.remark
    })
  } catch { /* 失败提示由请求层统一处理 */ }
  dialogVisible.value = true
}

async function handleDelete(row: OpenApp) {
  try {
    await ElMessageBox.confirm(`确认删除应用"${row.appName}"？`, '提示', { type: 'warning' })
    await deleteOpenApp(row.appId)
    ElMessage.success('删除成功')
    getList()
  } catch { /* cancel */ }
}

async function handleStatusChange(row: OpenApp, val: boolean) {
  const newStatus = val ? 0 : 1
  if (!val) {
    // 停用会立刻让所有使用该 AppKey 的接入方鉴权失败，必须二次确认
    try {
      await ElMessageBox.confirm(
        `停用应用"${row.appName}"后，所有使用该 AppKey 的接入方将立即无法获取令牌。确认停用吗？`,
        '提示',
        { type: 'warning' }
      )
    } catch {
      getList()
      return
    }
  }
  try {
    await changeAppStatus(row.appId, newStatus)
    ElMessage.success(val ? '启用成功' : '停用成功')
  } catch { /* 失败提示由请求层统一处理 */ } finally {
    // 无论成功失败都重新拉取，避免开关停在与后端不一致的视觉状态上
    getList()
  }
}

async function handleResetSecret(row: OpenApp) {
  try {
    await ElMessageBox.confirm(`确认重置应用"${row.appName}"的密钥？重置后旧密钥将失效。`, '提示', { type: 'warning' })
  } catch { return }
  try {
    const res = await resetAppSecret(row.appId)
    newSecret.value = (res as any).data || ''
    secretCopied.value = false
    secretVisible.value = true
  } catch { /* 失败提示由请求层统一处理 */ }
}

async function handleCopySecret() {
  if (!newSecret.value) return
  try {
    await navigator.clipboard.writeText(newSecret.value)
    secretCopied.value = true
    ElMessage.success('密钥已复制到剪贴板')
  } catch {
    ElMessage.warning('浏览器拒绝了剪贴板访问，请手动选中复制')
  }
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.appId) {
      await updateOpenApp(form)
      ElMessage.success('修改成功')
    } else {
      await addOpenApp(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } catch { /* 失败提示由请求层统一处理 */ } finally {
    submitLoading.value = false
  }
}

function resetForm() {
  Object.assign(form, defaultForm())
}
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
.mt-pagination { margin-top: 16px; justify-content: flex-end; }
.form-hint { margin-left: 8px; color: #999; font-size: 12px; }
.form-hint-block { color: #909399; font-size: 12px; line-height: 1.5; margin-top: 4px; }
.secret-alert { margin-bottom: 12px; }
</style>
