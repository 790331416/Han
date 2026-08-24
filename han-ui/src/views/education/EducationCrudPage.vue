<template>
  <div class="app-container">
    <el-card shadow="never" class="search-form">
      <el-form :model="query" :inline="true">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="编码或名称" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item v-if="entity === 'people'" label="人员类型">
          <el-select v-model="query.personType" clearable style="width: 140px">
            <el-option label="教师" value="TEACHER" />
            <el-option label="学生" value="STUDENT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="entity === 'people' || entity === 'subjects'" label="学校">
          <EducationSchoolSelector v-model="query.schoolId" :nodes="organizations" clearable style="width: 220px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ config.title }}列表</span>
          <div>
            <el-button
              v-if="userStore.hasPermission(`${config.permission}:remove`)"
              type="danger"
              plain
              :icon="Delete"
              :disabled="selection.length === 0"
              @click="handleRemove()"
            >删除</el-button>
            <el-button
              v-if="userStore.hasPermission(`${config.permission}:add`)"
              type="primary"
              :icon="Plus"
              @click="handleAdd"
            >新增{{ config.title }}</el-button>
            <el-dropdown v-if="entity === 'people' && userStore.hasPermission('education:person:import')" trigger="click">
              <el-button type="primary" plain :icon="Upload">导入人员<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleImport">选择文件导入</el-dropdown-item>
                  <el-dropdown-item @click="handleDownloadTemplate">下载导入模板</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="records" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" :selectable="isLocalRow" />
        <el-table-column
          v-for="column in config.columns"
          :key="column.key"
          :label="column.label"
          :prop="column.key"
          :min-width="column.width || 130"
          show-overflow-tooltip
        >
          <template v-if="column.format" #default="{ row }">{{ column.format(row) }}</template>
        </el-table-column>
        <el-table-column v-if="config.showSource !== false" label="来源" prop="sourceSystem" width="110">
          <template #default="{ row }">
            <el-tag :type="row.sourceSystem === 'HAN' ? 'success' : 'info'">
              {{ row.sourceSystem === 'HAN' ? '管理端' : '数字校园' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="userStore.hasPermission(`${config.permission}:edit`)"
              type="primary"
              link
              :icon="Edit"
              :disabled="!isLocalRow(row)"
              @click="handleEdit(row)"
            >编辑</el-button>
            <el-button
              v-if="entity === 'people' && row.userId && userStore.hasPermission('education:person:resetPwd')"
              type="primary"
              link
              :icon="Key"
              @click="handleResetPassword(row)"
            >重置密码</el-button>
            <el-button
              v-else-if="entity === 'people' && !row.userId && userStore.hasPermission('education:person:edit')"
              type="warning"
              link
              :icon="Connection"
              @click="handleRebindAccount(row)"
            >重新绑定并设置密码</el-button>
            <el-button
              v-if="userStore.hasPermission(`${config.permission}:remove`)"
              type="danger"
              link
              :icon="Delete"
              :disabled="!isLocalRow(row)"
              @click="handleRemove(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="140px">
        <template v-for="field in visibleFields" :key="field.key">
          <el-form-item
            :prop="field.key"
            :rules="field.required ? [{ required: true, message: `请填写${field.label}`, trigger: field.selector ? 'change' : 'blur' }] : []"
          >
            <template #label>
              <span class="field-label">{{ fieldLabel(field) }}</span>
              <el-tooltip v-if="field.hint" :content="field.hint" placement="top">
                <el-icon class="field-help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <el-select v-if="field.type === 'status'" v-model="form[field.key]" style="width: 100%">
              <el-option label="正常" :value="0" />
              <el-option label="停用" :value="1" />
            </el-select>
            <el-select v-else-if="field.type === 'flag'" v-model="form[field.key]" style="width: 100%">
              <el-option label="否" :value="0" />
              <el-option label="是" :value="1" />
            </el-select>
            <el-switch v-else-if="field.type === 'switch'" v-model="form[field.key]" />
            <el-date-picker
              v-else-if="field.type === 'date'"
              v-model="form[field.key]"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
            <EducationSchoolSelector v-else-if="field.selector === 'school'" v-model="form[field.key]" :nodes="organizations" style="width: 100%" />
            <EducationTeachingClassSelector v-else-if="field.selector === 'teachingClass'" v-model="form[field.key]" :school-id="form.schoolId" :teacher="form.personType !== 'STUDENT'" />
            <EducationClassSelector v-else-if="field.selector === 'class'" v-model="form[field.key]" :school-id="form.schoolId" :multiple="field.multiple" />
            <EducationSubjectSelector v-else-if="field.selector === 'subject'" v-model="form[field.key]" :school-id="form.schoolId" :multiple="field.multiple" />
            <EducationPlaceSelector v-else-if="field.selector === 'place'" v-model="form[field.key]" :school-id="form.schoolId" />
            <el-tree-select
              v-else-if="field.type === 'deviceScene'"
              v-model="form.applicationTypes"
              :data="deviceSceneTree"
              multiple
              show-checkbox
              check-strictly
              default-expand-all
              filterable
              clearable
              node-key="value"
              :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
              style="width: 100%"
              @change="handleDeviceSceneChange"
            />
            <el-select
              v-else-if="field.type === 'select'"
              v-model="form[field.key]"
              :clearable="field.clearable !== false"
              filterable
              :disabled="field.key === 'deviceType' && Array.isArray(form.applicationTypes) && form.applicationTypes.length > 0"
              style="width: 100%"
            >
              <el-option
                v-for="option in fieldOptions(field)"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-select
              v-else-if="field.type === 'multi'"
              v-model="form[field.key]"
              multiple
              clearable
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="option in fieldOptions(field)"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-input-number
              v-else-if="field.type === 'number'"
              v-model="form[field.key]"
              :min="0"
              controls-position="right"
              style="width: 100%"
            />
            <el-input
              v-else
              v-model="form[field.key]"
              :type="field.type === 'textarea' ? 'textarea' : field.type === 'password' ? 'password' : 'text'"
              :rows="field.type === 'textarea' ? 3 : undefined"
              :show-password="field.type === 'password'"
              clearable
            />
          </el-form-item>
        </template>
        <div
          v-if="entity === 'people' && form.accountMode === 'LINK'"
          v-loading="linkAccountChecking"
          class="account-link-preview"
        >
          <el-alert
            v-if="linkAccountPreview"
            type="success"
            :closable="false"
            show-icon
            :title="`将关联已有账号：${maskPhone(linkAccountPreview.phone)}（${linkAccountPreview.nickname || '—'}）`"
          />
          <el-alert
            v-else-if="!linkAccountChecking"
            type="warning"
            :closable="false"
            show-icon
            title="未找到与该手机号匹配的已有账号，保存时将按手机号创建新账号"
          />
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialogVisible" title="人员导入结果" width="860px" destroy-on-close>
      <el-table :data="importResults" max-height="520">
        <el-table-column prop="rowNumber" label="行号" width="80" />
        <el-table-column prop="personName" label="姓名" min-width="120" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="message" label="处理说明" min-width="320" show-overflow-tooltip />
      </el-table>
      <template #footer><el-button @click="importDialogVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ArrowDown, Connection, Delete, Edit, Key, Plus, QuestionFilled, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  addEducation,
  addPerson,
  downloadPersonImportTemplate,
  importPeople,
  listEducation,
  listLinkableAccounts,
  listPersonAssignments,
  listPersonMemberships,
  listPersonRoles,
  resetPersonPassword,
  removeEducation,
  updateEducation,
  updatePerson,
  type EducationEntity,
  type EducationQuery,
  type EducationOrganizationNode,
  type EducationRecord,
  type LinkableAccount,
  type PersonImportResult,
  listOrganizationTree
} from '@/api/education'
import { listAllRoles } from '@/api/system/role'
import { useUserStore } from '@/stores/user'
import EducationSchoolSelector from '@/components/education/EducationSchoolSelector.vue'
import EducationClassSelector from '@/components/education/EducationClassSelector.vue'
import EducationPlaceSelector from '@/components/education/EducationPlaceSelector.vue'
import EducationSubjectSelector from '@/components/education/EducationSubjectSelector.vue'
import EducationTeachingClassSelector from '@/components/education/EducationTeachingClassSelector.vue'
import { schoolOptions as flattenSchoolOptions } from '@/utils/education-school-tree'
import { findDictLabel, loadDictOptions } from '@/utils/dict-options'

type FieldType = 'text' | 'number' | 'textarea' | 'status' | 'flag' | 'switch' | 'password' | 'date' | 'select' | 'multi' | 'deviceScene'
type OptionSource = 'roles' | 'schools' | 'schoolDuties' | 'deviceTypes' | 'deviceApplications' | 'assetStatuses'
type Selector = 'school' | 'class' | 'teachingClass' | 'subject' | 'place'
interface Field {
  key: string
  label: string
  required?: boolean
  type?: FieldType
  source?: OptionSource
  selector?: Selector
  multiple?: boolean
  clearable?: boolean
  hint?: string
  initial?: unknown
  visibleWhen?: (form: EducationRecord) => boolean
}
interface EntityConfig {
  title: string
  permission: string
  showSource?: boolean
  columns: Array<{ key: string; label: string; width?: number; format?: (row: EducationRecord) => string }>
  fields: Field[]
}

// 字典未初始化时仍保留第一期两个职务，避免页面因部署顺序短暂不可用。
const DUTY_FALLBACKS = [
  { label: '管理员', value: 'SCHOOL_ADMIN' },
  { label: '普通教师', value: 'TEACHER' }
]
const DEVICE_TYPE_FALLBACKS = [{ label: '视频分析', value: 'VIDEO_ANALYSIS' }, { label: '录播设备', value: 'RECORDER' }]
const DEVICE_APPLICATION_FALLBACKS = [
  { label: '校园监控', value: 'VIDEO_ANALYSIS:CAMPUS_MONITORING' },
  { label: '智能巡课', value: 'VIDEO_ANALYSIS:INTELLIGENT_PATROL' },
  { label: '直播', value: 'RECORDER:LIVE' },
  { label: '录制', value: 'RECORDER:RECORD' }
]
const ASSET_STATUS_FALLBACKS = [
  { label: '在用', value: 'IN_USE' },
  { label: '闲置', value: 'IDLE' },
  { label: '报废', value: 'SCRAPPED' }
]

// 人员登录账号模式：创建新账号 / 关联已有账号 / 暂不启用登录。
// 后端按手机号自动区分「关联已有账号」与「新建账号」，前端只负责表达意图并脱敏确认。
const ACCOUNT_MODE_OPTIONS = [
  { label: '创建新账号', value: 'CREATE' },
  { label: '关联已有账号', value: 'LINK' },
  { label: '暂不启用登录', value: 'DISABLED' }
]

const props = defineProps<{ entity: EducationEntity }>()
const userStore = useUserStore()
const router = useRouter()

const configs: Record<EducationEntity, EntityConfig> = {
  schools: {
    title: '学校', permission: 'education:school',
    columns: [{ key: 'schoolName', label: '学校名称', width: 220 }, { key: 'schoolRole', label: '学校角色', format: (row) => schoolRoleLabel(row.schoolRole) }],
    fields: [{ key: 'parentId', label: '上级学校', type: 'select', source: 'schools' }, { key: 'schoolName', label: '学校名称', required: true, hint: '保存后按学校名称自动生成学校编码' }, { key: 'schoolRole', label: '学校角色', required: true }, { key: 'areaCode', label: '区域编码' }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  classes: {
    title: '班级', permission: 'education:class',
    columns: [{ key: 'className', label: '班级名称', width: 220 }, { key: 'classRole', label: '班级角色', format: (row) => classRoleLabel(row.classRole) }, { key: 'schoolId', label: '学校', format: (row) => schoolName(row.schoolId) }],
    fields: [{ key: 'schoolId', label: '学校', required: true, selector: 'school' }, { key: 'className', label: '班级名称', required: true, hint: '请按“X年级X班”填写；保存后自动生成年级编码和班级编码' }, { key: 'classRole', label: '班级角色', required: true }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  people: {
    title: '人员', permission: 'education:person',
    columns: [{ key: 'personName', label: '姓名', width: 160 }, { key: 'personType', label: '人员类型', format: (row) => personTypeLabel(row.personType) }, { key: 'dutyCode', label: '校内岗位', format: (row: EducationRecord) => row.personType === 'STUDENT' && !row.dutyCode ? '—' : dutyLabel(row.dutyCode) }, { key: 'phone', label: '手机号' }],
    fields: [
      { key: 'schoolId', label: '学校', required: true, selector: 'school' },
      { key: 'personName', label: '姓名', required: true, hint: '保存后按姓名自动生成人员编号' },
      { key: 'personType', label: '人员类型', required: true, type: 'select' },
      {
        key: 'dutyCode',
        label: '校内岗位',
        required: true,
        type: 'select',
        source: 'schoolDuties',
        // 新增人员默认普通教师：管理岗只能是管理员主动选出来的，不能靠缺省发出去。
        initial: 'TEACHER',
        visibleWhen: (form) => form.personType !== 'STUDENT',
        hint: '管理员可进入课程预约、授课统计、学校设置、学校直播间；普通教师不可。与人员类型是两个维度，不会互相推导'
      },
      { key: 'phone', label: '手机号', required: true, hint: '校端使用手机号和密码登录' },
      { key: 'classIds', label: '任教班级', selector: 'teachingClass', multiple: true, hint: '教师可勾选年级或班级；勾选年级会自动关联其下全部班级。学生只能选择一个班级' },
      { key: 'subjectIds', label: '任教科目', selector: 'subject', multiple: true, visibleWhen: (form) => form.personType !== 'STUDENT' },
      { key: 'leaveFlag', label: '离校状态', type: 'flag', hint: '离校只影响教育身份，不改动登录账号的启用状态' },
      { key: 'accountMode', label: '登录账号', type: 'select', clearable: false, initial: 'CREATE', hint: '创建新账号 / 关联已有账号 / 暂不启用登录；关联已有账号按手机号匹配并脱敏确认' },
      { key: 'password', label: '初始密码', type: 'password', visibleWhen: (form) => form.accountMode === 'CREATE', hint: '留空则由系统生成并要求首次登录修改' },
      { key: 'managementAccess', label: '设置管理端权限', type: 'switch', initial: false, visibleWhen: (form) => form.personType === 'TEACHER' && form.accountMode === 'CREATE', hint: '开启后才可选择管理端角色；不会改变校端职务' },
      { key: 'roleIds', label: '系统管理权限', type: 'multi', source: 'roles', visibleWhen: (form) => form.personType === 'TEACHER' && !!form.managementAccess && form.accountMode === 'CREATE' },
      { key: 'status', label: '状态', required: true, type: 'status' },
      { key: 'remark', label: '备注', type: 'textarea' }
    ]
  },
  subjects: {
    title: '科目', permission: 'education:subject',
    columns: [{ key: 'subjectName', label: '科目名称', width: 220 }, { key: 'schoolId', label: '学校', format: (row: EducationRecord) => schoolName(row.schoolId) }, { key: 'sort', label: '排序' }],
    fields: [{ key: 'schoolId', label: '学校', required: true, selector: 'school' }, { key: 'subjectName', label: '科目名称', required: true, hint: '保存后按学校和科目名称自动生成科目编码' }, { key: 'sort', label: '排序', required: true, type: 'number' }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  devices: {
    title: '设备', permission: 'education:device',
     columns: [{ key: 'deviceCode', label: '设备编码' }, { key: 'deviceName', label: '设备名称', width: 180 }, { key: 'deviceType', label: '设备类型', format: (row) => dictLabel('deviceType', row.deviceType) }, { key: 'applicationTypes', label: '设备应用场景', width: 220, format: (row) => String(row.applicationTypes || '').split(',').filter(Boolean).map(value => dictLabel('applicationTypes', value)).join('、') || '—' }, { key: 'serialNumber', label: '序列号' }, { key: 'assetStatus', label: '资产状态', format: (row) => dictLabel('assetStatus', row.assetStatus) }],
     fields: [{ key: 'schoolId', label: '学校', required: true, selector: 'school' }, { key: 'roomId', label: '教室', selector: 'place' }, { key: 'deviceCode', label: '设备编码', required: true }, { key: 'deviceName', label: '设备名称', required: true }, { key: 'applicationTypes', label: '设备应用场景', required: true, type: 'deviceScene', source: 'deviceApplications', hint: '先选一个设备类型，再选择该类型下的应用场景；录播设备只能选一个，其他设备可多选' }, { key: 'model', label: '型号' }, { key: 'serialNumber', label: '序列号' }, { key: 'assetStatus', label: '资产状态', required: true, type: 'select', source: 'assetStatuses' }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  },
  // 学期不在这里：它没有 source_system 列，还有日期区间与三态生命周期要渲染，走 views/education/semester 独立页。
  rooms: {
    title: '教室', permission: 'education:room',
    columns: [{ key: 'roomName', label: '教室名称', width: 220 }, { key: 'roomType', label: '教室类型', format: (row) => roomTypeLabel(row.roomType) }, { key: 'schoolId', label: '学校', format: (row) => schoolName(row.schoolId) }],
    fields: [{ key: 'schoolId', label: '学校', required: true, selector: 'school' }, { key: 'roomCode', label: '教室编码', required: true }, { key: 'roomName', label: '教室名称', required: true }, { key: 'roomType', label: '教室类型' }, { key: 'status', label: '状态', required: true, type: 'status' }, { key: 'remark', label: '备注', type: 'textarea' }]
  }
}

const config = computed(() => configs[props.entity])
const loading = ref(false)
const submitLoading = ref(false)
const records = ref<EducationRecord[]>([])
const selection = ref<EducationRecord[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const importDialogVisible = ref(false)
const importResults = ref<PersonImportResult[]>([])
const form = reactive<EducationRecord>({})
const options = reactive<Record<string, Array<{ label: string; value: any }>>>({})
const schoolOptions = ref<Array<{ label: string; value: any }>>([])
const organizations = ref<EducationOrganizationNode[]>([])
const query = reactive<EducationQuery>({ keyword: '', status: '', personType: '', pageNum: 1, pageSize: 20 })

// 「关联已有账号」模式的候选账号脱敏确认（按手机号匹配）。
const linkAccountPreview = ref<LinkableAccount | null>(null)
const linkAccountChecking = ref(false)

const visibleFields = computed(() => config.value.fields.filter((field) => !field.visibleWhen || field.visibleWhen(form)))
const deviceSceneTree = computed(() => {
  const types = options.deviceType?.length ? options.deviceType : DEVICE_TYPE_FALLBACKS
  const applications = options.applicationTypes?.length ? options.applicationTypes : DEVICE_APPLICATION_FALLBACKS
  return types.map((type) => ({
    value: String(type.value),
    label: type.label,
    disabled: true,
    children: applications
      .filter((item) => String(item.value).startsWith(`${type.value}:`))
      .map((item) => ({ value: String(item.value), label: item.label }))
  }))
})

function fieldLabel(field: Field) {
  return props.entity === 'people' && field.key === 'classIds'
    ? form.personType === 'STUDENT' ? '所属班级' : '任教班级'
    : field.label
}
function fieldOptions(field: Field) {
  const values = options[field.key] || []
  if (field.key !== 'applicationTypes') return values
  const selected = Array.isArray(form.applicationTypes) ? form.applicationTypes.map(String) : []
  const selectedType = selected.map(applicationDeviceType).find(Boolean) || String(form.deviceType || '')
  if (!selectedType) return values
  const matching = values.filter((item) => String(item.value).startsWith(`${selectedType}:`))
  return matching.length ? matching : values
}
function applicationDeviceType(value: unknown) {
  const text = String(value || '')
  const separator = text.indexOf(':')
  return separator > 0 ? text.slice(0, separator) : ''
}
function handleDeviceSceneChange(value: unknown) {
  const selected = (Array.isArray(value) ? value : value ? [value] : []).map(String)
  const deviceType = applicationDeviceType(selected[selected.length - 1])
  const scoped = deviceType ? selected.filter((item) => applicationDeviceType(item) === deviceType) : []
  form.applicationTypes = (deviceType === 'RECORDER' ? scoped.slice(-1) : scoped)
  form.deviceType = deviceType || undefined
}
function personTypeLabel(value: unknown) { return ({ TEACHER: '教师', STUDENT: '学生' } as Record<string, string>)[String(value || '')] || '—' }
function schoolRoleLabel(value: unknown) { return ({ MAIN: '主讲学校', ATTEND: '听讲学校', NORMAL: '普通学校' } as Record<string, string>)[String(value || '')] || String(value || '—') }
function classRoleLabel(value: unknown) { return ({ MAIN: '主讲班级', ATTEND: '听讲班级', NORMAL: '普通班级' } as Record<string, string>)[String(value || '')] || String(value || '—') }
function roomTypeLabel(value: unknown) { return ({ LIVE: '直播教室', ATTEND: '听讲教室', CLASSROOM: '普通教室' } as Record<string, string>)[String(value || '')] || String(value || '—') }
function dutyLabel(value: unknown) {
  return findDictLabel(options.dutyCode || [], typeof value === 'string' || typeof value === 'number' ? value : undefined, '普通教师')
}
function maskPhone(value: unknown) {
  const phone = String(value || '')
  if (phone.length < 7) return phone
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`
}
function dictLabel(key: string, value: unknown) {
  return findDictLabel(options[key] || [], typeof value === 'string' || typeof value === 'number' ? value : undefined, '—')
}

onMounted(async () => { await loadSchoolOptions(); await loadEntityOptions(); await getList() })

// 学校变化时清空从属对象，实际候选由通用选择器按学校重新读取。
watch(() => form.schoolId, (schoolId, previous) => {
  if (previous && String(previous) !== String(schoolId)) {
    form.classIds = []
    form.subjectIds = []
    form.roomId = undefined
  }
})

watch(() => form.deviceType, (deviceType, previous) => {
  if (previous && String(previous) !== String(deviceType)) {
    form.applicationTypes = (form.applicationTypes || []).filter((value: string) => String(value).startsWith(`${deviceType}:`))
  }
})

watch(() => form.applicationTypes, (applicationTypes) => {
  const selected = Array.isArray(applicationTypes) ? applicationTypes.map(String) : []
  const deviceTypes = [...new Set(selected.map(applicationDeviceType).filter(Boolean))]
  if (deviceTypes.length === 0) return
  const deviceType = deviceTypes[0]
  if (deviceTypes.length > 1) {
    form.applicationTypes = selected.filter((value) => applicationDeviceType(value) === deviceType)
  }
  if (String(form.deviceType || '') !== deviceType) form.deviceType = deviceType
}, { deep: true })

watch(() => form.personType, (personType, previous) => {
  if (props.entity !== 'people' || !previous || personType === previous) return
  if (personType === 'STUDENT') {
    form.dutyCode = undefined
    form.managementAccess = false
    form.roleIds = []
  } else if (!form.dutyCode) {
    form.dutyCode = 'TEACHER'
  }
})

// 「关联已有账号」模式：按手机号匹配候选账号并脱敏展示供确认。
watch([() => form.accountMode, () => form.phone], async ([mode, phone]) => {
  if (props.entity !== 'people' || mode !== 'LINK' || !phone) {
    linkAccountPreview.value = null
    linkAccountChecking.value = false
    return
  }
  linkAccountChecking.value = true
  try {
    const response = await listLinkableAccounts()
    const list = response.data || []
    linkAccountPreview.value = list.find((item) => String(item.phone || '').trim() === String(phone).trim()) || null
  } catch {
    linkAccountPreview.value = null
  } finally {
    linkAccountChecking.value = false
  }
})

async function getList() {
  loading.value = true
  try {
    const response = await listEducation(props.entity, query)
    const data = response.data
    records.value = data?.rows || []
    total.value = data?.total || 0
  } finally {
    loading.value = false
  }
}

function isLocalRow(row: EducationRecord) {
  return config.value.showSource === false || row.sourceSystem === 'HAN'
}

function onSelectionChange(rows: EducationRecord[]) {
  selection.value = rows
}

function handleQuery() {
  query.pageNum = 1
  getList()
}

function resetQuery() {
  query.keyword = ''
  query.status = ''
  query.personType = ''
  query.schoolId = undefined
  handleQuery()
}

function resetForm() {
  for (const key of Object.keys(form)) delete form[key]
  for (const field of config.value.fields) {
    if (field.initial !== undefined) form[field.key] = field.initial
    else if (field.type === 'multi' || field.multiple) form[field.key] = []
    else if (field.type === 'switch') form[field.key] = false
    else if (field.type === 'status' || field.type === 'flag' || field.type === 'number') form[field.key] = 0
    else form[field.key] = ''
  }
}

async function handleAdd() {
  resetForm()
  await loadFormOptions()
  dialogTitle.value = `新增${config.value.title}`
  dialogVisible.value = true
}

async function handleEdit(row: EducationRecord) {
  if (!isLocalRow(row)) return
  resetForm()
  Object.assign(form, row)
  if (props.entity === 'devices') {
    form.applicationTypes = String(row.applicationTypes || '').split(',').filter(Boolean)
  }
  form.accountMode = row.userId ? 'CREATE' : 'DISABLED'
  // 引入岗位维度之前建的人员 duty_code 是空的，服务端按普通教师解释，表单要显示成同一个值。
  if (props.entity === 'people' && form.personType === 'TEACHER' && !form.dutyCode) form.dutyCode = 'TEACHER'
  await loadFormOptions()
  if (props.entity === 'people' && row.id) {
    const [memberships, assignments, roles] = await Promise.all([
      listPersonMemberships(row.id),
      listPersonAssignments(row.id),
      listPersonRoles(row.id)
    ])
    form.classIds = (memberships.data || []).map((item) => String(item.classId))
    form.subjectIds = (assignments.data || []).map((item) => String(item.subjectId))
    const availableRoleIds = new Set((options.roleIds || []).map((item) => String(item.value)))
    form.roleIds = (roles.data || []).map((item) => String(item)).filter((item) => availableRoleIds.has(item))
    form.managementAccess = form.personType === 'TEACHER' && form.roleIds.length > 0
  }
  dialogTitle.value = `编辑${config.value.title}`
  dialogVisible.value = true
}

async function handleRebindAccount(row: EducationRecord) {
  await handleEdit(row)
  form.accountMode = 'CREATE'
  form.password = ''
  dialogTitle.value = '重新绑定并设置密码'
}

async function handleResetPassword(row: EducationRecord) {
  const result = await ElMessageBox.prompt(
    `请输入“${row.personName}”的新密码\n\n注意：该账号可能关联多个学校身份，重置后会影响该账号在全部学校的登录身份。`,
    '重置人员登录密码',
    {
      inputPattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?]).{8,32}$/,
      inputErrorMessage: '密码须为8-32位且包含大小写字母、数字和特殊字符'
    }
  ) as unknown as { value: string }
  await resetPersonPassword(row.id!, result.value)
  ElMessage.success('重置成功')
}

async function handleRemove(row?: EducationRecord) {
  const targets = row ? [row] : selection.value
  if (targets.length === 0) return
  await ElMessageBox.confirm(
    `确认删除选中的 ${targets.length} 条${config.value.title}？删除后业务编码可重新使用，历史记录按 ID 保留。`,
    '确认删除',
    { type: 'warning' }
  )
  const removed = await removeEducation(props.entity, targets.map((item) => item.id!))
  ElMessage.success(`已删除 ${removed.data ?? targets.length} 条`)
  selection.value = []
  await getList()
}

async function loadFormOptions() {
  const sources = new Set(config.value.fields.map((field) => field.source).filter(Boolean) as OptionSource[])
  await Promise.all([...sources].map((source) => loadOptions(source)))
  if (!form.id && config.value.fields.some((field) => field.selector === 'school') && schoolOptions.value.length === 1) {
    form.schoolId = schoolOptions.value[0].value
  }
  if (props.entity === 'people') {
    options.personType = [
      { label: '教师', value: 'TEACHER' },
      { label: '学生', value: 'STUDENT' }
    ]
    options.accountMode = ACCOUNT_MODE_OPTIONS
    if (!options.dutyCode?.length) options.dutyCode = DUTY_FALLBACKS
  }
}

async function loadEntityOptions() {
  if (props.entity === 'people') {
    options.personType = [
      { label: '教师', value: 'TEACHER' },
      { label: '学生', value: 'STUDENT' }
    ]
    options.accountMode = ACCOUNT_MODE_OPTIONS
    await loadOptions('schoolDuties')
    if (!options.dutyCode?.length) options.dutyCode = DUTY_FALLBACKS
    return
  }
  if (props.entity !== 'devices') return
  await Promise.all(['deviceTypes', 'deviceApplications', 'assetStatuses'].map((source) => loadOptions(source as OptionSource)))
}

async function loadOptions(source: OptionSource) {
  const keys = config.value.fields.filter((field) => field.source === source).map((field) => field.key)
  const targetKeys = keys.length > 0 ? keys : source === 'deviceTypes' ? ['deviceType'] : []
  if (targetKeys.length === 0) return
  let items: Array<{ label: string; value: any }> = []
  if (source === 'roles') {
    const response = await listAllRoles()
    items = (response.data || [])
      .filter((role) => !['teacher', 'student'].includes(String(role.roleKey || '').toLowerCase()))
      .map((role) => ({ label: role.roleName!, value: String(role.id) }))
  } else if (source === 'schools') {
    await loadSchoolOptions()
    items = schoolOptions.value
  } else if (source === 'deviceTypes') {
    items = await loadDictOptions('edu_device_type', DEVICE_TYPE_FALLBACKS)
  } else if (source === 'deviceApplications') {
    items = await loadDictOptions('edu_device_application', DEVICE_APPLICATION_FALLBACKS)
  } else if (source === 'assetStatuses') {
    items = await loadDictOptions('edu_asset_status', ASSET_STATUS_FALLBACKS)
  } else if (source === 'schoolDuties') {
    items = await loadDictOptions('edu_school_duty', DUTY_FALLBACKS)
  }
  targetKeys.forEach((key) => { options[key] = items })
}

function schoolName(value: unknown) {
  return schoolOptions.value.find(item => String(item.value) === String(value))?.label || String(value || '—')
}

async function loadSchoolOptions() {
  const response = await listOrganizationTree(0)
  organizations.value = response.data || []
  schoolOptions.value = flattenSchoolOptions(organizations.value).map(item => ({ label: item.schoolName, value: String(item.id) }))
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (props.entity === 'people') await submitPerson()
    else if (form.id) await updateEducation(props.entity, form)
    else await addEducation(props.entity, form)
    if (props.entity !== 'people') ElMessage.success(form.id ? '修改成功' : '新增成功')
    dialogVisible.value = false
    await getList()
  } finally {
    submitLoading.value = false
  }
}

async function submitPerson() {
  const payload: EducationRecord = { ...form }
  const isStudent = payload.personType === 'STUDENT'
  const accountMode = payload.accountMode === 'LINK' ? 'LINK' : payload.accountMode === 'DISABLED' ? 'DISABLED' : 'CREATE'
  // accountMode 是前端意图字段，不进入后端 Person 表单。
  delete payload.accountMode

  // 登录账号开关：CREATE / LINK 都视为启用登录，DISABLED 明确关停。
  payload.loginEnabled = accountMode !== 'DISABLED'

  if (isStudent) {
    delete payload.dutyCode
    delete payload.roleIds
    if (payload.id && payload.userId) payload.clearRoles = true
  } else if (accountMode !== 'CREATE') {
    // 关联已有账号：不建新号、不动账号角色；暂不启用登录：只停用账号。两者都不触碰角色。
    delete payload.roleIds
    delete payload.clearRoles
  } else if (!payload.managementAccess) {
    if (payload.id && payload.userId) {
      await ElMessageBox.confirm('关闭管理端权限后将清除该人员的管理端角色，确认继续吗？', '确认变更', { type: 'warning' })
      payload.clearRoles = true
    }
    delete payload.roleIds
  } else if (!payload.roleIds?.length) {
    ElMessage.warning('请选择管理端角色')
    return
  } else {
    delete payload.clearRoles
  }

  if (!payload.loginEnabled) {
    delete payload.username
    delete payload.password
  } else if (accountMode === 'LINK') {
    // 关联已有账号：口令由既有账号保持，表单不提交口令与登录名。
    delete payload.password
    delete payload.username
  } else if (!isStudent && payload.managementAccess) {
    if (!payload.password) delete payload.password
  }

  const response = form.id ? await updatePerson(payload) : await addPerson(payload)
  const result = response.data
  if (result?.initialPassword) {
    await ElMessageBox.alert(
      `手机号：${payload.phone}\n初始密码：${result.initialPassword}\n\n该密码只显示这一次，请立即转交本人，首次登录须修改。`,
      '账号创建成功',
      { type: 'success', confirmButtonText: '我已记录' }
    )
  } else {
    ElMessage.success(form.id ? '修改成功' : '新增成功')
  }
  if (!payload.id && payload.managementAccess && result?.userId) {
    try {
      await ElMessageBox.confirm('管理端角色已分配，请继续设置该人员可管理的教育局或学校。', '设置数据范围', {
        type: 'info', confirmButtonText: '立即设置', cancelButtonText: '稍后设置'
      })
      await router.push({ path: '/education/scope', query: { userId: String(result.userId) } })
    } catch { /* 稍后可从数据范围授权菜单进入 */ }
  }
}

function downloadBlob(data: Blob, filename: string) {
  const link = document.createElement('a')
  link.href = URL.createObjectURL(data)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
}

async function handleDownloadTemplate() {
  if (!query.schoolId) {
    ElMessage.warning('请先选择导入学校')
    return
  }
  try {
    const response = await downloadPersonImportTemplate(query.schoolId)
    downloadBlob((response as any).data, '人员导入模板.xlsx')
  } catch {
    ElMessage.error('下载导入模板失败')
  }
}

function handleImport() {
  if (!query.schoolId) {
    ElMessage.warning('请先选择导入学校')
    return
  }
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.onchange = async (event: Event) => {
    const file = (event.target as HTMLInputElement).files?.[0]
    if (!file) return
    try {
      const response = await importPeople(file, query.schoolId!)
      importResults.value = response.data || []
      importDialogVisible.value = true
      const failed = importResults.value.filter(item => !item.success).length
      ElMessage[failed ? 'warning' : 'success'](`导入完成：成功 ${importResults.value.length - failed} 条，失败 ${failed} 条`)
      await getList()
    } catch {
      ElMessage.error('导入失败，请检查模板格式')
    }
  }
  input.click()
}
</script>

<style scoped>
.app-container { padding: 20px; }
.search-form { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pagination { margin-top: 16px; justify-content: flex-end; }
.field-label { display: inline-flex; align-items: center; gap: 3px; white-space: nowrap; }
.field-help { color: var(--el-text-color-secondary); cursor: help; vertical-align: middle; }
.account-link-preview { margin: -6px 0 16px 140px; min-height: 24px; }
</style>
