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
        <el-form-item label="回调地址">
          <el-input v-model="redirectUrisStr" type="textarea" placeholder="多个地址用换行分隔" :rows="3" data-testid="open-app-form-redirect-uris" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" placeholder="请输入联系人" data-testid="open-app-form-contact-name" />
        </el-form-item>
        <el-form-item label="AccessToken有效期">
          <el-input-number v-model="form.accessTokenTtl" :min="60" :step="3600" data-testid="open-app-form-access-token-ttl" />
          <span class="form-hint">秒</span>
        </el-form-item>
        <el-form-item label="RefreshToken有效期">
          <el-input-number v-model="form.refreshTokenTtl" :min="60" :step="3600" data-testid="open-app-form-refresh-token-ttl" />
          <span class="form-hint">秒</span>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Search, Refresh, Plus, Edit, Delete, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listOpenApp, getOpenApp, addOpenApp, updateOpenApp, deleteOpenApp, resetAppSecret, changeAppStatus, type OpenApp, type OpenAppForm } from '@/api/open/app'

const loading = ref(false)
const submitLoading = ref(false)
const appList = ref<OpenApp[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const queryParams = reactive({ appName: '', appType: '' as string | undefined, status: undefined as number | undefined, pageNum: 1, pageSize: 10 })

const form = reactive<OpenAppForm>({ appName: '', appDesc: '', appType: 'web', redirectUris: [], contactName: '', accessTokenTtl: 7200, refreshTokenTtl: 604800, status: 0 })

const redirectUrisStr = computed({
  get: () => (form.redirectUris || []).join('\n'),
  set: (val: string) => { form.redirectUris = val.split('\n').map(s => s.trim()).filter(s => s) }
})

const rules: FormRules = {
  appName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }],
  appType: [{ required: true, message: '请选择应用类型', trigger: 'change' }]
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
    Object.assign(form, { appId: d.appId, appName: d.appName, appDesc: d.appDesc, appType: d.appType, redirectUris: d.redirectUris || [], contactName: d.contactName, accessTokenTtl: d.accessTokenTtl, refreshTokenTtl: d.refreshTokenTtl, status: d.status })
  } catch { /* ignore */ }
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
  try {
    await changeAppStatus(row.appId, newStatus)
    ElMessage.success(val ? '启用成功' : '停用成功')
    getList()
  } catch { /* ignore */ }
}

async function handleResetSecret(row: OpenApp) {
  try {
    await ElMessageBox.confirm(`确认重置应用"${row.appName}"的密钥？重置后旧密钥将失效。`, '提示', { type: 'warning' })
    const res = await resetAppSecret(row.appId)
    const newSecret = (res as any).data
    ElMessageBox.alert(`新密钥: ${newSecret}`, '密钥已重置', { confirmButtonText: '已复制', type: 'success' })
  } catch { /* cancel */ }
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
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
  } finally {
    submitLoading.value = false
  }
}

function resetForm() {
  form.appId = undefined; form.appName = ''; form.appDesc = ''; form.appType = 'web'; form.redirectUris = []; form.contactName = ''; form.accessTokenTtl = 7200; form.refreshTokenTtl = 604800; form.status = 0
}
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.search-form { margin-bottom: 16px; }
.mt-pagination { margin-top: 16px; justify-content: flex-end; }
.form-hint { margin-left: 8px; color: #999; font-size: 12px; }
</style>
