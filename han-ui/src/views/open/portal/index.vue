<template>
  <div class="portal-page" data-testid="open-portal-page">
    <div class="portal-header">
      <div>
        <h1>厂商开放平台</h1>
        <p>管理当前账号所属厂商的应用、接口授权和分环境凭证。</p>
      </div>
      <el-tag type="info">仅展示当前账号有权数据</el-tag>
    </div>

    <el-alert
      title="应用创建、授权申请和凭证操作都会经过后端归属与权限校验；查看者角色只保留查询能力。"
      type="info"
      :closable="false"
      show-icon
      class="portal-notice"
    />

    <el-tabs v-model="activeTab" type="card" @tab-change="handleTabChange">
      <el-tab-pane label="我的厂商" name="vendors">
        <el-card shadow="never">
          <template #header><div class="card-header"><span>关联厂商</span><el-button :icon="Refresh" @click="loadVendors">刷新</el-button></div></template>
          <el-table v-loading="vendorLoading" :data="vendors" empty-text="暂无关联厂商">
            <el-table-column label="厂商名称" prop="name" min-width="180" />
            <el-table-column label="统一社会信用代码" prop="qualificationNo" min-width="190" />
            <el-table-column label="联系人" prop="contactName" min-width="120" />
            <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="vendorStatusType(row.status)">{{ vendorStatusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="100"><template #default="{ row }"><el-button type="primary" link @click="openVendorDetail(row)">详情</el-button></template></el-table-column>
          </el-table>
        </el-card>
        <el-card v-if="vendorDetail" shadow="never" class="detail-card">
          <template #header><span>厂商详情：{{ vendorDetail.name }}</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="厂商名称">{{ vendorDetail.name }}</el-descriptions-item>
            <el-descriptions-item label="统一社会信用代码">{{ vendorDetail.qualificationNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="所属行业">{{ vendorDetail.industry || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag :type="vendorStatusType(vendorDetail.status)">{{ vendorStatusLabel(vendorDetail.status) }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="联系人">{{ vendorDetail.contactName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ vendorDetail.contactPhone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="审核说明" :span="2">{{ vendorDetail.reviewInfo || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="应用管理" name="apps">
        <el-card shadow="never">
          <template #header>
            <div class="card-header"><span>我的应用</span><div><el-button :icon="Refresh" @click="loadApps">刷新</el-button><el-button v-if="canAppAdd" type="primary" :icon="Plus" @click="openAppCreate">创建应用</el-button></div></div>
          </template>
          <el-table v-loading="appLoading" :data="apps" empty-text="暂无应用">
            <el-table-column label="应用名称" prop="appName" min-width="180" />
            <el-table-column label="厂商" min-width="150"><template #default="{ row }">{{ vendorName(row.vendorId) }}</template></el-table-column>
            <el-table-column label="应用类型" prop="appType" width="100" />
            <el-table-column label="生命周期" width="130"><template #default="{ row }"><el-tag :type="lifecycleTagType(row.lifecycleStatus)">{{ lifecycleLabel(row.lifecycleStatus) }}</el-tag></template></el-table-column>
            <el-table-column label="操作" min-width="260" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canAppEdit" type="primary" link @click="openAppEdit(row)">编辑</el-button>
                <el-button v-if="canAppEdit && nextLifecycleStatus(row.lifecycleStatus) !== undefined" type="success" link @click="advanceLifecycle(row)">{{ lifecycleActionLabel(row.lifecycleStatus) }}</el-button>
                <el-button v-if="canAppRemove && row.lifecycleStatus === 0" type="danger" link @click="removeApp(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="接口目录" name="resources">
        <el-card shadow="never">
          <template #header><div class="card-header"><span>可申请接口</span><el-button :icon="Refresh" @click="loadResources">刷新</el-button></div></template>
          <el-table v-loading="resourceLoading" :data="resources" empty-text="暂无接口目录">
            <el-table-column label="接口名称" prop="resourceName" min-width="180" />
            <el-table-column label="请求方式" prop="httpMethod" width="100" />
            <el-table-column label="路径" prop="path" min-width="260" show-overflow-tooltip />
            <el-table-column label="Scope" prop="scopeCode" min-width="180" />
            <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '启用' : '停用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="100"><template #default="{ row }"><el-button type="primary" link @click="openResourceDetail(row)">查看详情</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="授权申请" name="grants">
        <el-card v-if="canGrantApply" shadow="never" class="form-card">
          <template #header><span>申请接口授权</span></template>
          <el-form :model="grantForm" label-width="110px" @submit.prevent="submitGrant">
            <div class="form-grid">
              <el-form-item label="应用" required><el-select v-model="grantForm.appId" clearable placeholder="选择应用" style="width: 100%"><el-option v-for="app in apps" :key="app.appId" :label="app.appName" :value="app.appId" /></el-select></el-form-item>
              <el-form-item label="环境" required><el-radio-group v-model="grantForm.environment"><el-radio value="SANDBOX">沙箱</el-radio><el-radio value="PROD">生产</el-radio></el-radio-group></el-form-item>
              <el-form-item label="接口" required class="full-row"><el-select v-model="grantForm.resourceIds" multiple filterable clearable placeholder="选择一个或多个接口" style="width: 100%"><el-option v-for="resource in applicableResources" :key="resource.id" :label="`${resource.resourceName}（${resource.httpMethod} ${resource.path}）`" :value="resource.id ?? ''" /></el-select></el-form-item>
              <el-form-item label="Scope"><el-input v-model="grantForm.scopes" placeholder="多个 Scope 用逗号分隔；为空时使用接口默认 Scope" /></el-form-item>
              <el-form-item label="调用配额"><el-input-number v-model="grantForm.quota" :min="0" :step="100" style="width: 100%" /></el-form-item>
              <el-form-item label="有效期（天）"><el-input-number v-model="grantForm.expireDays" :min="0" style="width: 100%" /><span class="form-tip">0 表示永久</span></el-form-item>
              <el-form-item label="数据范围" class="full-row"><el-input v-model="grantForm.dataScope" type="textarea" :rows="2" placeholder="按接口约定填写 JSON 数据范围，可留空" /></el-form-item>
              <el-form-item label="申请理由" required class="full-row"><el-input v-model="grantForm.applyReason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明业务场景和数据使用范围" /></el-form-item>
            </div>
            <div class="form-actions"><el-button type="primary" :loading="grantSubmitting" @click="submitGrant">提交授权申请</el-button></div>
          </el-form>
        </el-card>
        <el-card shadow="never" class="table-card">
          <template #header><div class="card-header"><span>我的授权申请</span><el-button :icon="Refresh" @click="loadGrantRequests">刷新</el-button></div></template>
          <el-table v-loading="grantLoading" :data="grantRequests" empty-text="暂无授权申请">
            <el-table-column label="应用" min-width="160"><template #default="{ row }">{{ appName(row.appId) }}</template></el-table-column>
            <el-table-column label="环境" width="90"><template #default="{ row }">{{ environmentLabel(row.environment) }}</template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="requestTagType(row.status)">{{ requestStatusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="申请理由" prop="reason" min-width="220" show-overflow-tooltip />
            <el-table-column label="申请时间" prop="createTime" min-width="170" :formatter="(_row: OpenAuthorizationRequest, _column: unknown, value: string) => formatDate(value)" />
          </el-table>
        </el-card>
        <el-card shadow="never" class="table-card">
          <template #header><div class="card-header"><span>应用已授权资源</span><el-select v-model="grantAppId" clearable placeholder="选择应用" style="width: 220px" @change="loadAppGrants"><el-option v-for="app in apps" :key="app.appId" :label="app.appName" :value="app.appId" /></el-select></div></template>
          <el-table v-loading="grantDetailLoading" :data="appGrants" empty-text="请选择应用查看授权资源">
            <el-table-column label="资源" min-width="180"><template #default="{ row }">{{ row.resourceName || row.resourceCode || row.resourceId }}</template></el-table-column>
            <el-table-column label="环境" width="90"><template #default="{ row }">{{ environmentLabel(row.environment) }}</template></el-table-column>
            <el-table-column label="Scope" prop="scopes" min-width="180" />
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '有效' : '无效' }}</el-tag></template></el-table-column>
            <el-table-column label="到期时间" prop="expiresAt" min-width="170" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="凭证管理" name="credentials">
        <el-card shadow="never">
          <template #header><div class="card-header"><span>分环境客户端凭证</span><el-button :icon="Refresh" @click="loadCredentials">刷新</el-button></div></template>
          <div class="toolbar"><el-select v-model="credentialAppId" clearable placeholder="选择应用" style="width: 240px" @change="loadCredentials"><el-option v-for="app in apps" :key="app.appId" :label="app.appName" :value="app.appId" /></el-select><el-select v-model="credentialEnvironment" style="width: 130px" @change="loadCredentials"><el-option label="沙箱" value="SANDBOX" /><el-option label="生产" value="PROD" /></el-select><el-button v-if="canCredentialManage && credentialAppId" type="primary" :icon="Key" @click="generateCredential">生成凭证</el-button></div>
          <el-table v-loading="credentialLoading" :data="credentials" empty-text="请选择应用查看凭证">
            <el-table-column label="Client ID" prop="clientId" min-width="230" />
            <el-table-column label="环境" width="90"><template #default="{ row }">{{ environmentLabel(row.environment) }}</template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '有效' : '已失效' }}</el-tag></template></el-table-column>
            <el-table-column label="到期时间" prop="expireAt" min-width="170" />
            <el-table-column label="操作" width="100"><template #default="{ row }"><el-button v-if="canCredentialManage && row.status === 0" type="warning" link @click="rotateCredential(row)">轮换</el-button></template></el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-card shadow="never" class="debug-card">
      <template #header>
        <div class="card-header"><span>在线调测</span><el-tag type="warning">仅调用已发布目录</el-tag></div>
      </template>
      <el-alert
        title="浏览器直连同源开放接口；平台不做代理。Client Secret 和 Access Token 只在当前页面内存中使用，不会写入存储、日志或调测审计。"
        type="warning"
        :closable="false"
        show-icon
        class="debug-notice"
      />
      <el-form v-if="canGrantApply" label-width="110px" class="debug-form">
        <div class="form-grid">
          <el-form-item label="应用" required>
            <el-select v-model="debugAppId" clearable placeholder="选择应用" style="width: 100%" @change="handleDebugAppChange">
              <el-option v-for="app in apps" :key="app.appId" :label="app.appName" :value="app.appId" />
            </el-select>
          </el-form-item>
          <el-form-item label="环境" required>
            <el-radio-group v-model="debugEnvironment" @change="loadDebugHistory">
              <el-radio value="SANDBOX">沙箱</el-radio><el-radio value="PROD">生产</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="接口" required class="full-row">
            <el-select v-model="debugResourceId" clearable filterable placeholder="选择已发布且允许调测的接口" style="width: 100%" @change="loadDebugResource">
              <el-option v-for="resource in debugResources" :key="resource.id" :label="`${resource.resourceName}（${resource.httpMethod} ${resource.path}）`" :value="resource.id ?? ''" />
            </el-select>
          </el-form-item>
          <el-form-item label="Client ID" required><el-input v-model="debugClientId" autocomplete="off" /></el-form-item>
          <el-form-item label="Client Secret" required><el-input v-model="debugClientSecret" type="password" show-password autocomplete="new-password" /></el-form-item>
          <el-form-item label="请求 JSON" required class="full-row">
            <div class="debug-editor-wrap">
              <el-input v-model="debugRequestJson" type="textarea" :rows="7" maxlength="50000" show-word-limit placeholder="GET 接口填写对象用于 Query；POST 等接口作为 JSON Body" />
              <el-button link type="primary" @click="fillRequestExample">填入请求示例</el-button>
            </div>
          </el-form-item>
          <el-form-item label="预期响应" class="full-row">
            <div class="debug-editor-wrap">
              <el-input v-model="debugExpectedResponse" type="textarea" :rows="5" maxlength="50000" show-word-limit readonly placeholder="可从当前版本响应示例一键填充，用于人工对照" />
              <el-button link type="primary" @click="fillResponseExample">填入响应示例</el-button>
            </div>
          </el-form-item>
        </div>
        <div class="form-actions"><el-button type="primary" :loading="debugRunning" :disabled="!debugResourceId || !debugAppId" @click="runOnlineDebug">获取 Token 并调测</el-button></div>
      </el-form>
      <el-empty v-else description="当前账号没有在线调测权限；查看者只能查看调测记录。" :image-size="70" />

      <el-card v-if="debugResponse" shadow="never" class="debug-result-card">
        <template #header><div class="card-header"><span>最近一次响应</span><span><el-tag :type="debugResponse.ok ? 'success' : 'danger'">{{ debugResponse.statusCode || '网络错误' }}</el-tag><el-tag type="info">{{ debugResponse.durationMs }} ms / {{ debugResponse.responseSize }} bytes</el-tag></span></div></template>
        <pre class="debug-response">{{ debugResponse.body }}</pre>
      </el-card>

      <el-card shadow="never" class="debug-history-card">
        <template #header><div class="card-header"><span>调测记录（最近 50 条）</span><el-button :icon="Refresh" @click="loadDebugHistory">刷新</el-button></div></template>
        <el-table v-loading="debugHistoryLoading" :data="debugHistory" empty-text="暂无调测记录">
          <el-table-column label="接口" min-width="260"><template #default="{ row }">{{ row.requestMethod }} {{ row.requestPath }}</template></el-table-column>
          <el-table-column label="环境" width="90"><template #default="{ row }">{{ environmentLabel(row.environment) }}</template></el-table-column>
          <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">{{ row.result === 'SUCCESS' ? '成功' : '失败' }}</el-tag></template></el-table-column>
          <el-table-column label="状态码" prop="statusCode" width="90" />
          <el-table-column label="耗时" width="100"><template #default="{ row }">{{ row.durationMs }} ms</template></el-table-column>
          <el-table-column label="响应大小" width="120"><template #default="{ row }">{{ row.responseSize ?? '-' }} bytes</template></el-table-column>
          <el-table-column label="时间" prop="createTime" min-width="170" :formatter="(_row: OpenApiTestRun, _column: unknown, value: string) => formatDate(value)" />
        </el-table>
      </el-card>
    </el-card>

    <el-dialog v-model="appDialogVisible" :title="appForm.appId ? '编辑应用' : '创建应用'" width="620px" destroy-on-close>
      <el-form ref="appFormRef" :model="appForm" :rules="appRules" label-width="110px">
        <el-form-item label="所属厂商" prop="vendorId"><el-select v-model="appForm.vendorId" placeholder="选择厂商" style="width: 100%"><el-option v-for="vendor in vendors" :key="vendor.id" :label="vendor.name" :value="vendor.id" /></el-select></el-form-item>
        <el-form-item label="应用名称" prop="appName"><el-input v-model="appForm.appName" /></el-form-item>
        <el-form-item label="应用类型" prop="appType"><el-select v-model="appForm.appType" style="width: 100%"><el-option label="Web 应用" value="web" /><el-option label="移动应用" value="mobile" /><el-option label="服务端" value="server" /></el-select></el-form-item>
        <el-form-item label="应用描述"><el-input v-model="appForm.appDesc" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="回调地址"><el-input v-model="redirectUrisText" type="textarea" :rows="2" placeholder="多个地址换行填写" /></el-form-item>
        <el-form-item label="身份授权范围">
          <el-checkbox-group v-model="identityScopes"><el-checkbox v-for="scope in identityScopeOptions" :key="scope.value" :label="scope.value">{{ scope.label }}</el-checkbox></el-checkbox-group>
        </el-form-item>
        <el-form-item label="业务 Scope"><el-input v-model="scopeText" placeholder="仅填写已申请的业务 Scope，多个用逗号分隔" /></el-form-item>
        <el-form-item label="环境策略"><el-select v-model="appForm.environmentPolicy" style="width: 100%"><el-option label="先沙箱" value="SANDBOX_FIRST" /><el-option label="仅生产" value="PROD_ONLY" /><el-option label="全部环境" value="ALL" /></el-select></el-form-item>
        <el-form-item label="联系人"><el-input v-model="appForm.contactName" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="appDialogVisible = false">取消</el-button><el-button type="primary" :loading="appSubmitting" @click="submitApp">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="resourceDialogVisible" title="接口详情" width="760px" destroy-on-close>
      <el-descriptions v-if="resourceDetail" :column="2" border>
        <el-descriptions-item label="接口名称">{{ resourceDetail.resourceName }}</el-descriptions-item>
        <el-descriptions-item label="Scope">{{ resourceDetail.scopeCode }}</el-descriptions-item>
        <el-descriptions-item label="请求方式">{{ resourceDetail.httpMethod }}</el-descriptions-item>
        <el-descriptions-item label="路径">{{ resourceDetail.path }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ resourceDetail.description || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">版本</el-divider>
      <el-table v-if="resourceDetail" :data="resourceDetail.versions || []" size="small" empty-text="暂无版本"><el-table-column label="版本" prop="version" /><el-table-column label="状态" prop="status" /><el-table-column label="发布时间" prop="publishedAt" /></el-table>
    </el-dialog>

    <el-dialog v-model="credentialSecretVisible" title="凭证已生成" width="560px" destroy-on-close @closed="clearSecret">
      <el-alert title="Client Secret 只显示一次，请立即保存。关闭窗口后不会再次返回。" type="warning" :closable="false" show-icon />
      <el-form label-width="110px" class="secret-form"><el-form-item label="Client ID"><el-input :model-value="credentialClientId" readonly /></el-form-item><el-form-item label="Client Secret"><el-input :model-value="credentialSecret" type="password" show-password readonly /></el-form-item></el-form>
      <template #footer><el-button type="primary" @click="credentialSecretVisible = false">我已保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Key, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { formatDate } from '@/utils/request'
import { listOpenApp, getOpenApp, addOpenApp, updateOpenApp, deleteOpenApp, changeAppLifecycleStatus, submitAppLifecycleApply, type OpenApp, type OpenAppForm } from '@/api/open/app'
import { getOpenVendor, type OpenVendor } from '@/api/open/vendor'
import { listOpenApiResource, getOpenApiResourceDetail, type OpenApiResource, type OpenApiResourceDetail } from '@/api/open/resource'
import { listAuthorizationRequests, listAppGrants, listAppCredentials, generateAppCredential, rotateAppCredential, type OpenAuthorizationRequest, type OpenAuthorizationRequestQuery, type OpenCredential, type OpenCredentialSecret, type OpenGrant } from '@/api/open/authorization'
import { addOpenApiTestRun, listOpenApiTestRuns, listMyOpenVendors, submitGrantApply, type GrantApplyForm, type OpenApiTestRun } from '@/api/open/portal'
import { loadDictOptions, OPEN_IDENTITY_SCOPE_DICT, type DictOption } from '@/utils/dict-options'

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger'
const userStore = useUserStore()
const activeTab = ref('vendors')
const vendorLoading = ref(false)
const vendors = ref<OpenVendor[]>([])
const vendorDetail = ref<OpenVendor | null>(null)
const appLoading = ref(false)
const apps = ref<OpenApp[]>([])
const resourceLoading = ref(false)
const resources = ref<OpenApiResource[]>([])
const resourceDetail = ref<OpenApiResourceDetail | null>(null)
const resourceDialogVisible = ref(false)
const grantLoading = ref(false)
const grantRequests = ref<OpenAuthorizationRequest[]>([])
const grantSubmitting = ref(false)
const grantDetailLoading = ref(false)
const grantAppId = ref<string | number>()
const appGrants = ref<OpenGrant[]>([])
const credentialLoading = ref(false)
const credentialAppId = ref<string | number>()
const credentialEnvironment = ref('SANDBOX')
const credentials = ref<OpenCredential[]>([])
const credentialSecretVisible = ref(false)
const credentialClientId = ref('')
const credentialSecret = ref('')
const appDialogVisible = ref(false)
const appSubmitting = ref(false)
const appFormRef = ref<FormInstance>()
const debugAppId = ref<string | number>()
const debugResourceId = ref<string | number>()
const debugEnvironment = ref<'SANDBOX' | 'PROD'>('SANDBOX')
const debugClientId = ref('')
const debugClientSecret = ref('')
const debugRequestJson = ref('{}')
const debugExpectedResponse = ref('')
const debugResourceDetail = ref<OpenApiResourceDetail | null>(null)
const debugRunning = ref(false)
const debugHistoryLoading = ref(false)
const debugHistory = ref<OpenApiTestRun[]>([])
const debugResponse = ref<{ ok: boolean; statusCode?: number; durationMs: number; responseSize: number; body: string } | null>(null)

const canAppAdd = computed(() => userStore.hasPermission('open:app:add'))
const canAppEdit = computed(() => userStore.hasPermission('open:app:edit'))
const canAppRemove = computed(() => userStore.hasPermission('open:app:remove'))
const canGrantApply = computed(() => userStore.hasPermission('open:grant:apply'))
const canCredentialManage = computed(() => userStore.hasPermission('open:credential:manage'))
const applicableResources = computed(() => resources.value.filter(item => item.status === 0 && item.allowApply !== 0 && item.publishStatus !== 3))
const debugResources = computed(() => resources.value.filter(item => item.status === 0 && item.publishStatus === 2 && item.allowTest === 1))

const appForm = reactive<OpenAppForm>({ appName: '', appDesc: '', appType: 'web', vendorId: undefined, redirectUris: [], scopes: [], grantTypes: ['authorization_code', 'refresh_token', 'client_credentials'], environmentPolicy: 'SANDBOX_FIRST', contactName: '' })
const identityScopeOptions = ref<DictOption[]>([])
const identityScopes = ref<string[]>([])
const redirectUrisText = computed({ get: () => (appForm.redirectUris || []).join('\n'), set: (value: string) => { appForm.redirectUris = value.split('\n').map(item => item.trim()).filter(Boolean) } })
const scopeText = computed({ get: () => (appForm.scopes || []).filter(scope => scope !== 'openid' && scope !== 'profile').join(','), set: (value: string) => { appForm.scopes = [...identityScopes.value, ...value.split(',').map(item => item.trim()).filter(Boolean)] } })
const appRules: FormRules = { vendorId: [{ required: true, message: '请选择厂商', trigger: 'change' }], appName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }], appType: [{ required: true, message: '请选择应用类型', trigger: 'change' }] }
const grantForm = reactive({ appId: undefined as string | number | undefined, environment: 'SANDBOX' as 'SANDBOX' | 'PROD', resourceIds: [] as Array<string | number>, scopes: '', dataScope: '', quota: 0, expireDays: 0, applyReason: '' })

async function loadVendors() {
  vendorLoading.value = true
  try { vendors.value = (await listMyOpenVendors()).data || []; if (!vendorDetail.value && vendors.value[0]) await openVendorDetail(vendors.value[0]) } catch (error) { vendors.value = []; notifyError(error, '加载厂商失败') } finally { vendorLoading.value = false }
}

async function openVendorDetail(vendor: OpenVendor) {
  try { vendorDetail.value = (await getOpenVendor(vendor.id)).data } catch (error) { notifyError(error, '加载厂商详情失败') }
}

async function loadApps() {
  appLoading.value = true
  try { const data = (await listOpenApp({ pageNum: 1, pageSize: 100 })).data; apps.value = (data as typeof data & { records?: OpenApp[] })?.records || data?.rows || [] } catch (error) { apps.value = []; notifyError(error, '加载应用失败') } finally { appLoading.value = false }
}

async function loadResources() {
  resourceLoading.value = true
  try { resources.value = (await listOpenApiResource()).data || [] } catch (error) { resources.value = []; notifyError(error, '加载接口目录失败') } finally { resourceLoading.value = false }
}

async function openResourceDetail(resource: OpenApiResource) {
  if (!resource.id) return
  try { resourceDetail.value = (await getOpenApiResourceDetail(resource.id)).data; resourceDialogVisible.value = true } catch (error) { notifyError(error, '加载接口详情失败') }
}

async function handleDebugAppChange() {
  await loadDebugHistory()
}

async function loadDebugResource() {
  debugResourceDetail.value = null
  debugResponse.value = null
  if (!debugResourceId.value) return
  try {
    debugResourceDetail.value = (await getOpenApiResourceDetail(debugResourceId.value)).data
    fillRequestExample()
    fillResponseExample()
  } catch (error) {
    notifyError(error, '加载调测接口详情失败')
  }
}

async function loadDebugHistory() {
  if (!debugAppId.value) { debugHistory.value = []; return }
  debugHistoryLoading.value = true
  try { debugHistory.value = (await listOpenApiTestRuns(debugAppId.value)).data || [] } catch (error) { debugHistory.value = []; notifyError(error, '加载调测记录失败') } finally { debugHistoryLoading.value = false }
}

function fillRequestExample() {
  const example = debugResourceDetail.value?.currentVersion?.requestExample
  if (example !== undefined) debugRequestJson.value = toJsonText(example)
}

function fillResponseExample() {
  const example = debugResourceDetail.value?.currentVersion?.responseExamples
  if (example !== undefined) debugExpectedResponse.value = toJsonText(example)
}

function toJsonText(value: unknown) {
  if (typeof value === 'string') return value
  try { return JSON.stringify(value ?? {}, null, 2) || '{}' } catch { return '{}' }
}

function parseDebugRequest() {
  try {
    const parsed = JSON.parse(debugRequestJson.value || '{}')
    if (parsed === null || typeof parsed !== 'object') throw new Error('请求 JSON 必须是对象或数组')
    return parsed as Record<string, unknown>
  } catch (error) {
    throw new Error(error instanceof Error ? error.message : '请求 JSON 格式错误')
  }
}

function buildDebugTarget(resource: OpenApiResource, requestBody: Record<string, unknown>) {
  if (!resource.path.startsWith('/open/api/')) throw new Error('接口路径不在开放平台目录范围内')
  const method = resource.httpMethod.toUpperCase()
  if (method === 'GET' || method === 'DELETE') {
    const query = new URLSearchParams()
    Object.entries(requestBody).forEach(([key, value]) => {
      if (value !== undefined && value !== null) query.set(key, typeof value === 'string' ? value : JSON.stringify(value))
    })
    const suffix = query.toString()
    return suffix ? `${resource.path}?${suffix}` : resource.path
  }
  return resource.path
}

async function runOnlineDebug() {
  const resource = debugResourceDetail.value
  if (!debugAppId.value || !resource || String(resource.id) !== String(debugResourceId.value)
    || resource.status !== 0 || resource.publishStatus !== 2 || resource.allowTest !== 1) {
    ElMessage.warning('请选择应用和已发布调测接口'); return
  }
  if (!debugClientId.value.trim() || !debugClientSecret.value) { ElMessage.warning('请输入 Client ID 和 Client Secret'); return }
  let clientSecret = debugClientSecret.value
  let accessToken = ''
  let apiStartedAt: number | null = null
  let apiAttempted = false
  let auditSubmitted = false
  debugRunning.value = true
  try {
    const tokenBody = new URLSearchParams({ grant_type: 'client_credentials', client_id: debugClientId.value.trim(), client_secret: clientSecret, scope: resource.scopeCode })
    const tokenResponse = await fetch('/open/oauth2/token', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: tokenBody })
    tokenBody.delete('client_secret')
    clientSecret = ''
    debugClientSecret.value = ''
    const tokenPayload = await tokenResponse.json().catch(() => ({})) as { access_token?: string; msg?: string }
    if (!tokenResponse.ok || !tokenPayload.access_token) throw new Error(tokenPayload.msg || '客户端凭证校验失败')
    accessToken = tokenPayload.access_token

    const requestBody = parseDebugRequest()
    const method = resource.httpMethod.toUpperCase()
    const target = buildDebugTarget(resource, requestBody)
    const startedAt = performance.now()
    apiStartedAt = startedAt
    apiAttempted = true
    const response = await fetch(target, {
      method,
      headers: { Authorization: `Bearer ${accessToken}`, ...(method === 'GET' || method === 'DELETE' ? {} : { 'Content-Type': 'application/json' }) },
      body: method === 'GET' || method === 'DELETE' ? undefined : JSON.stringify(requestBody)
    })
    const responseText = await response.text()
    const durationMs = Math.max(0, Math.round(performance.now() - startedAt))
    const responseSize = new Blob([responseText]).size
    debugResponse.value = { ok: response.ok, statusCode: response.status, durationMs, responseSize, body: formatResponse(responseText) }
    await addOpenApiTestRun({ appId: debugAppId.value, resourceId: resource.id ?? '', environment: debugEnvironment.value, statusCode: response.status, durationMs, responseSize, traceId: response.headers.get('X-Trace-Id') || undefined })
    auditSubmitted = true
    await loadDebugHistory()
  } catch (error) {
    const message = error instanceof Error ? error.message : '在线调测失败'
    if (apiAttempted && !auditSubmitted && apiStartedAt !== null) {
      try {
        await addOpenApiTestRun({ appId: debugAppId.value, resourceId: resource.id ?? '', environment: debugEnvironment.value, statusCode: 0, durationMs: Math.max(0, Math.round(performance.now() - apiStartedAt)), responseSize: 0 })
      } catch {
        // 审计失败不覆盖原始调测错误；页面仍明确显示本次请求未取得 HTTP 响应。
      }
    }
    debugResponse.value = { ok: false, durationMs: 0, responseSize: 0, body: message }
    notifyError(error, '在线调测失败')
  } finally {
    clientSecret = ''
    accessToken = ''
    debugClientSecret.value = ''
    debugRunning.value = false
  }
}

function formatResponse(value: string) {
  try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value }
}

async function loadGrantRequests() {
  grantLoading.value = true
  try { const query: OpenAuthorizationRequestQuery = { pageNum: 1, pageSize: 100 }; const data = (await listAuthorizationRequests(query)).data; grantRequests.value = data?.rows || [] } catch (error) { grantRequests.value = []; notifyError(error, '加载授权申请失败') } finally { grantLoading.value = false }
}

async function submitGrant() {
  if (!grantForm.appId || !grantForm.resourceIds.length || !grantForm.applyReason.trim()) { ElMessage.warning('请选择应用和接口，并填写申请理由'); return }
  const resourcesById = new Map(resources.value.map(item => [String(item.id), item]))
  const payload: GrantApplyForm = { appId: grantForm.appId, environment: grantForm.environment, applyReason: grantForm.applyReason.trim(), resources: grantForm.resourceIds.map(id => ({ resourceId: id, scopes: grantForm.scopes.trim() || resourcesById.get(String(id))?.scopeCode || '', dataScope: grantForm.dataScope.trim() || undefined, quota: grantForm.quota || 0, expireDays: grantForm.expireDays || 0 })) }
  if (payload.resources.some(item => !item.scopes)) { ElMessage.warning('所选接口缺少默认 Scope，请手动填写'); return }
  grantSubmitting.value = true
  try { await submitGrantApply(payload); ElMessage.success('授权申请已提交'); grantForm.resourceIds = []; grantForm.applyReason = ''; await loadGrantRequests() } catch (error) { notifyError(error, '提交授权申请失败') } finally { grantSubmitting.value = false }
}

async function loadAppGrants() {
  if (!grantAppId.value) { appGrants.value = []; return }
  grantDetailLoading.value = true
  try { appGrants.value = (await listAppGrants(grantAppId.value)).data || [] } catch (error) { appGrants.value = []; notifyError(error, '加载授权资源失败') } finally { grantDetailLoading.value = false }
}

async function loadCredentials() {
  if (!credentialAppId.value) { credentials.value = []; return }
  credentialLoading.value = true
  try { const data = (await listAppCredentials(credentialAppId.value)).data || []; credentials.value = data.filter(item => !credentialEnvironment.value || item.environment === credentialEnvironment.value) } catch (error) { credentials.value = []; notifyError(error, '加载凭证失败') } finally { credentialLoading.value = false }
}

async function generateCredential() {
  if (!credentialAppId.value) return
  try { showCredential(await generateAppCredential(credentialAppId.value, credentialEnvironment.value)); await loadCredentials() } catch (error) { notifyError(error, '生成凭证失败') }
}

async function rotateCredential(row: OpenCredential) {
  try { await ElMessageBox.confirm('轮换后旧凭证会立即失效，是否继续？', '确认轮换', { type: 'warning' }); showCredential(await rotateAppCredential(row.id)); await loadCredentials() } catch (error) { if (!isCancel(error)) notifyError(error, '轮换凭证失败') }
}

function showCredential(response: { data: OpenCredentialSecret }) {
  credentialClientId.value = response.data?.clientId || ''
  credentialSecret.value = response.data?.clientSecret || ''
  credentialSecretVisible.value = true
}
function clearSecret() { credentialClientId.value = ''; credentialSecret.value = '' }

function openAppCreate() { resetAppForm(); appForm.vendorId = vendors.value[0]?.id; appDialogVisible.value = true }
async function openAppEdit(row: OpenApp) { resetAppForm(); try { Object.assign(appForm, await getOpenApp(row.appId).then(response => response.data)); identityScopes.value = (appForm.scopes || []).filter(scope => scope === 'openid' || scope === 'profile'); appDialogVisible.value = true } catch (error) { notifyError(error, '加载应用详情失败') } }
function resetAppForm() { Object.assign(appForm, { appId: undefined, appName: '', appDesc: '', appType: 'web', vendorId: undefined, redirectUris: [], scopes: [], grantTypes: ['authorization_code', 'refresh_token', 'client_credentials'], environmentPolicy: 'SANDBOX_FIRST', contactName: '' }); identityScopes.value = [] }
async function submitApp() { if (!(await appFormRef.value?.validate())) return; appSubmitting.value = true; try { if (appForm.appId) { await updateOpenApp(appForm) } else { await addOpenApp(appForm) } ElMessage.success(appForm.appId ? '应用已更新' : '应用草稿已创建，请提交沙箱审核'); appDialogVisible.value = false; await loadApps() } catch (error) { notifyError(error, '保存应用失败') } finally { appSubmitting.value = false } }
async function removeApp(row: OpenApp) { try { await ElMessageBox.confirm(`确认删除应用“${row.appName}”？`, '确认删除', { type: 'warning' }); await deleteOpenApp(row.appId); ElMessage.success('应用已删除'); await loadApps() } catch (error) { if (!isCancel(error)) notifyError(error, '删除应用失败') } }
async function advanceLifecycle(row: OpenApp) { const next = nextLifecycleStatus(row.lifecycleStatus); if (next === undefined) return; try { await ElMessageBox.confirm(`确认${lifecycleActionLabel(row.lifecycleStatus)}“${row.appName}”？`, '确认操作', { type: 'warning' }); if (next === 1 || next === 4) await submitAppLifecycleApply(row.appId); else await changeAppLifecycleStatus(row.appId, next); ElMessage.success(next === 1 || next === 4 ? '已提交，等待平台审核' : '已进入调测'); await loadApps() } catch (error) { if (!isCancel(error)) notifyError(error, '生命周期操作失败') } }

function handleTabChange(name: string | number) { if (name === 'apps') loadApps(); if (name === 'resources') loadResources(); if (name === 'grants') { loadApps(); loadResources(); loadGrantRequests() } if (name === 'credentials') loadApps() }
function vendorName(id?: string | number) { return vendors.value.find(item => String(item.id) === String(id))?.name || '-' }
function appName(id?: string | number) { return apps.value.find(item => String(item.appId) === String(id))?.appName || id || '-' }
function nextLifecycleStatus(status?: number) { return ({ 0: 1, 2: 3, 3: 4 } as Record<number, number>)[status ?? -1] }
function lifecycleActionLabel(status?: number) { return ({ 0: '提交沙箱审核', 2: '开始调测', 3: '提交生产审核' } as Record<number, string>)[status ?? -1] || '' }
function lifecycleLabel(status?: number) { return ({ 0: '草稿', 1: '待审核', 2: '沙箱已开通', 3: '调测中', 4: '生产待审', 5: '生产已开通', 6: '已暂停', 7: '已撤销' } as Record<number, string>)[status ?? -1] || '未知' }
function lifecycleTagType(status?: number): TagType { return status === 5 ? 'success' : status === 6 ? 'warning' : status === 7 ? 'danger' : status === 1 || status === 4 ? 'warning' : 'primary' }
function vendorStatusLabel(status?: number) { return ({ 0: '待提交', 1: '待验证', 2: '待审核', 3: '补充材料', 4: '审核通过', 5: '审核驳回', 6: '已暂停', 7: '已注销' } as Record<number, string>)[status ?? -1] || '未知' }
function vendorStatusType(status?: number): TagType { return status === 4 ? 'success' : status === 5 || status === 7 ? 'danger' : status === 6 ? 'warning' : 'info' }
function environmentLabel(value?: string) { return value === 'PROD' ? '生产' : value === 'SANDBOX' ? '沙箱' : value || '-' }
function requestStatusLabel(status?: number) { return ({ 0: '待审核', 1: '已通过', 2: '已驳回', 3: '已撤销' } as Record<number, string>)[status ?? -1] || '未知' }
function requestTagType(status?: number): TagType { return status === 1 ? 'success' : status === 2 || status === 3 ? 'danger' : 'warning' }
function isCancel(error: unknown) { return error === 'cancel' || (error as { message?: string })?.message === 'cancel' }
function notifyError(error: unknown, fallback: string) { ElMessage.error(error instanceof Error && error.message !== '请求失败' ? error.message : fallback) }

watch(identityScopes, scopes => { appForm.scopes = [...new Set([...(appForm.scopes || []).filter(scope => scope !== 'openid' && scope !== 'profile'), ...scopes])] })
onMounted(async () => { identityScopeOptions.value = await loadDictOptions(OPEN_IDENTITY_SCOPE_DICT, [{ label: '用户唯一标识（openid）', value: 'openid' }, { label: '用户基础资料（profile）', value: 'profile' }]); await loadVendors(); await Promise.all([loadApps(), loadResources()]) })
</script>

<style lang="scss" scoped>
.portal-page { padding: 20px; }
.portal-header, .card-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.portal-header { margin-bottom: 16px; }
.portal-header h1 { margin: 0 0 6px; color: var(--el-text-color-primary); font-size: 24px; }
.portal-header p { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
.portal-notice { margin-bottom: 16px; }
.detail-card, .table-card, .form-card, .debug-card { margin-top: 16px; }
.debug-notice { margin-bottom: 16px; }
.debug-form { margin-top: 16px; }
.debug-editor-wrap { width: 100%; }
.debug-editor-wrap .el-button { padding-left: 0; }
.debug-result-card, .debug-history-card { margin-top: 16px; }
.debug-response { max-height: 360px; margin: 0; overflow: auto; padding: 12px; border-radius: 4px; background: var(--el-fill-color-light); white-space: pre-wrap; word-break: break-word; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; }
.full-row { grid-column: 1 / -1; }
.form-actions { display: flex; justify-content: flex-end; }
.form-tip { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.toolbar { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.secret-form { margin-top: 20px; }
@media (max-width: 760px) { .portal-page { padding: 12px; } .form-grid { grid-template-columns: 1fr; } .full-row { grid-column: auto; } .portal-header { align-items: flex-start; flex-direction: column; } }
</style>
