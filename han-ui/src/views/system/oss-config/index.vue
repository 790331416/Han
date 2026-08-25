<template>
  <div class="app-container" data-testid="oss-config-page">
    <el-card shadow="never" class="search-form">
      <el-form :inline="true">
        <el-form-item label="配置Key">
          <el-input v-model="queryParams.configKey" placeholder="请输入" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
            <el-option label="只读" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>OSS存储配置</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd" data-testid="oss-config-add-button">新增配置</el-button>
        </div>
      </template>

      <el-table :data="dataList" v-loading="loading" stripe data-testid="oss-config-table">
        <el-table-column prop="configName" label="配置名称" min-width="140" />
        <el-table-column prop="configKey" label="配置Key" min-width="120" />
        <el-table-column prop="providerType" label="协议" width="90" />
        <el-table-column prop="endpoint" label="访问站点" min-width="180" show-overflow-tooltip />
        <el-table-column prop="bucketName" label="桶名称" min-width="100" />
        <el-table-column prop="region" label="域" min-width="100" show-overflow-tooltip />
        <el-table-column prop="prefix" label="前缀" min-width="80" show-overflow-tooltip />
        <el-table-column label="HTTPS" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isHttps === '0' ? 'success' : 'info'" size="small">
              {{ row.isHttps === '0' ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
              {{ row.status === '0' ? '正常' : row.status === '2' ? '只读' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="handleTest(row)">测试连接</el-button>
            <el-button link type="success" size="small" @click="handleChangeStatus(row)" v-if="row.status === '0'">设为默认写入</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next"
          @size-change="getList" @current-change="getList" />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="form.ossConfigId ? '编辑配置' : '新增配置'" width="55%" class="dialog-md" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="配置名称">
          <el-input v-model="form.configName" placeholder="如：天翼云业务对象存储" />
        </el-form-item>
        <el-form-item label="配置Key" prop="configKey">
          <el-input v-model="form.configKey" placeholder="如: ctyun-school, rustfs-local" />
        </el-form-item>
        <el-form-item label="存储协议">
          <el-select v-model="form.providerType" disabled><el-option label="S3兼容" value="S3" /></el-select>
        </el-form-item>
        <el-form-item label="访问站点" prop="endpoint">
          <el-input v-model="form.endpoint" placeholder="如: http://localhost:9000" />
        </el-form-item>
        <el-form-item label="外网站点">
          <el-input v-model="form.publicEndpoint" placeholder="私有文件临时下载使用；为空时使用访问站点" />
        </el-form-item>
        <el-form-item label="AccessKey" prop="accessKey">
          <el-input v-model="form.accessKey" placeholder="新增时必填；编辑留空则保留原密钥" autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="SecretKey" prop="secretKey">
          <el-input v-model="form.secretKey" placeholder="新增时必填；编辑留空则保留原密钥" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="桶名称" prop="bucketName">
          <el-input v-model="form.bucketName" placeholder="如: HAN" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="域">
              <el-input v-model="form.region" placeholder="如: us-east-1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="前缀">
              <el-input v-model="form.prefix" placeholder="文件路径前缀" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="HTTPS">
          <el-radio-group v-model="form.isHttps">
            <el-radio value="0">是</el-radio>
            <el-radio value="1">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="路径式访问">
          <el-switch v-model="form.pathStyle" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
            <el-radio value="2">只读</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { get, post } from '@/utils/request'
import type { FormInstance, FormRules } from 'element-plus'

const loading = ref(false)
const dataList = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const queryParams = reactive({ configKey: '', status: '', pageNum: 1, pageSize: 10 })

const defaultForm = () => ({
  ossConfigId: undefined as any, configName: '', configKey: '', providerType: 'S3', accessKey: '', secretKey: '',
  bucketName: '', prefix: '', endpoint: '', publicEndpoint: '', region: '', isHttps: '1', pathStyle: true, status: '1', remark: ''
})
const form = reactive<any>(defaultForm())

const rules: FormRules = {
  configKey: [{ required: true, message: '请输入配置Key', trigger: 'blur' }],
  endpoint: [{ required: true, message: '请输入访问站点', trigger: 'blur' }],
  accessKey: [{ required: true, message: '请输入AccessKey', trigger: 'blur' }],
  secretKey: [{ required: true, message: '请输入SecretKey', trigger: 'blur' }],
  bucketName: [{ required: true, message: '请输入桶名称', trigger: 'blur' }]
}

const getList = async () => {
  loading.value = true
  try {
    const res = await get<any>('/system/oss/config/list', queryParams)
    dataList.value = res.data.rows
    total.value = res.data.total
  } catch { /* ignore */ } finally { loading.value = false }
}

const handleQuery = () => { queryParams.pageNum = 1; getList() }
const resetQuery = () => { queryParams.configKey = ''; queryParams.status = ''; handleQuery() }

const handleAdd = () => { Object.assign(form, defaultForm()); dialogVisible.value = true }

const handleEdit = (row: any) => {
  Object.assign(form, { ...row, accessKey: '', secretKey: '' })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  submitLoading.value = true
  try {
    if (form.ossConfigId) { await post('/system/oss/config/edit', form); ElMessage.success('修改成功') }
    else { await post('/system/oss/config', form); ElMessage.success('新增成功') }
    dialogVisible.value = false
    getList()
  } catch { /* ignore */ } finally { submitLoading.value = false }
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确定删除配置"${row.configKey}"吗?`, '提示', { type: 'warning' })
  await post(`/system/oss/config/remove/${row.ossConfigId}`)
  ElMessage.success('删除成功')
  getList()
}

const handleTest = async (row: any) => {
  await post(`/system/oss/config/${row.ossConfigId}/test`)
  ElMessage.success('连接测试通过：已完成上传、读取校验和清理')
}

const handleChangeStatus = async (row: any) => {
  await ElMessageBox.confirm(`确定将配置"${row.configKey}"设为默认写入存储吗？历史文件仍从原存储读取。`, '提示', { type: 'warning' })
  await post(`/system/oss/config/changeStatus/${row.ossConfigId}`)
  ElMessage.success('已启用')
  getList()
}

onMounted(() => getList())
</script>

<style lang="scss" scoped>
.app-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>
