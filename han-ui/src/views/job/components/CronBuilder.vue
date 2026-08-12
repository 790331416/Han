<template>
  <div class="cron-builder">
    <el-alert
      v-if="unsupported"
      type="warning"
      show-icon
      :closable="false"
      class="cron-alert"
      data-testid="cron-unsupported-alert"
    >
      当前表达式包含生成器不支持的语法，已原样保留。若在下方任意修改，确定后原表达式将被覆盖。
    </el-alert>
    <el-alert
      v-if="invalidRanges.length"
      type="error"
      show-icon
      :closable="false"
      class="cron-alert"
      data-testid="cron-range-alert"
    >
      {{ invalidRanges.join('、') }} 的区间起始值大于结束值，表达式不合法，请调整。
    </el-alert>
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 秒 -->
      <el-tab-pane label="秒" name="second">
        <el-radio-group v-model="secondType" @change="updateCron">
          <el-radio value="every">每秒</el-radio>
          <el-radio value="range">
            从 <el-input-number v-model="secondRange.start" :min="0" :max="59" size="small" /> 秒 到
            <el-input-number v-model="secondRange.end" :min="0" :max="59" size="small" /> 秒
          </el-radio>
          <el-radio value="step">
            从 <el-input-number v-model="secondStep.start" :min="0" :max="59" size="small" /> 秒开始，每
            <el-input-number v-model="secondStep.step" :min="1" :max="59" size="small" /> 秒执行一次
          </el-radio>
          <el-radio value="specify">指定</el-radio>
        </el-radio-group>
        <div v-if="secondType === 'specify'" class="specify-values">
          <el-checkbox-group v-model="secondValues" @change="updateCron">
            <el-checkbox v-for="i in 60" :key="i - 1" :value="i - 1">{{ i - 1 }}</el-checkbox>
          </el-checkbox-group>
        </div>
      </el-tab-pane>

      <!-- 分 -->
      <el-tab-pane label="分" name="minute">
        <el-radio-group v-model="minuteType" @change="updateCron">
          <el-radio value="every">每分钟</el-radio>
          <el-radio value="range">
            从 <el-input-number v-model="minuteRange.start" :min="0" :max="59" size="small" /> 分 到
            <el-input-number v-model="minuteRange.end" :min="0" :max="59" size="small" /> 分
          </el-radio>
          <el-radio value="step">
            从 <el-input-number v-model="minuteStep.start" :min="0" :max="59" size="small" /> 分开始，每
            <el-input-number v-model="minuteStep.step" :min="1" :max="59" size="small" /> 分钟执行一次
          </el-radio>
          <el-radio value="specify">指定</el-radio>
        </el-radio-group>
        <div v-if="minuteType === 'specify'" class="specify-values">
          <el-checkbox-group v-model="minuteValues" @change="updateCron">
            <el-checkbox v-for="i in 60" :key="i - 1" :value="i - 1">{{ i - 1 }}</el-checkbox>
          </el-checkbox-group>
        </div>
      </el-tab-pane>

      <!-- 时 -->
      <el-tab-pane label="时" name="hour">
        <el-radio-group v-model="hourType" @change="updateCron">
          <el-radio value="every">每小时</el-radio>
          <el-radio value="range">
            从 <el-input-number v-model="hourRange.start" :min="0" :max="23" size="small" /> 时 到
            <el-input-number v-model="hourRange.end" :min="0" :max="23" size="small" /> 时
          </el-radio>
          <el-radio value="step">
            从 <el-input-number v-model="hourStep.start" :min="0" :max="23" size="small" /> 时开始，每
            <el-input-number v-model="hourStep.step" :min="1" :max="23" size="small" /> 小时执行一次
          </el-radio>
          <el-radio value="specify">指定</el-radio>
        </el-radio-group>
        <div v-if="hourType === 'specify'" class="specify-values">
          <el-checkbox-group v-model="hourValues" @change="updateCron">
            <el-checkbox v-for="i in 24" :key="i - 1" :value="i - 1">{{ i - 1 }}</el-checkbox>
          </el-checkbox-group>
        </div>
      </el-tab-pane>

      <!-- 日 -->
      <el-tab-pane label="日" name="day">
        <el-radio-group v-model="dayType" @change="updateCron">
          <el-radio value="every">每天</el-radio>
          <el-radio value="notSpecify">不指定（使用周设置）</el-radio>
          <el-radio value="range">
            从 <el-input-number v-model="dayRange.start" :min="1" :max="31" size="small" /> 日 到
            <el-input-number v-model="dayRange.end" :min="1" :max="31" size="small" /> 日
          </el-radio>
          <el-radio value="step">
            从 <el-input-number v-model="dayStep.start" :min="1" :max="31" size="small" /> 日开始，每
            <el-input-number v-model="dayStep.step" :min="1" :max="31" size="small" /> 天执行一次
          </el-radio>
          <el-radio value="last">每月最后一天</el-radio>
          <el-radio value="specify">指定</el-radio>
        </el-radio-group>
        <div v-if="dayType === 'specify'" class="specify-values">
          <el-checkbox-group v-model="dayValues" @change="updateCron">
            <el-checkbox v-for="i in 31" :key="i" :value="i">{{ i }}</el-checkbox>
          </el-checkbox-group>
        </div>
      </el-tab-pane>

      <!-- 月 -->
      <el-tab-pane label="月" name="month">
        <el-radio-group v-model="monthType" @change="updateCron">
          <el-radio value="every">每月</el-radio>
          <el-radio value="range">
            从 <el-input-number v-model="monthRange.start" :min="1" :max="12" size="small" /> 月 到
            <el-input-number v-model="monthRange.end" :min="1" :max="12" size="small" /> 月
          </el-radio>
          <el-radio value="step">
            从 <el-input-number v-model="monthStep.start" :min="1" :max="12" size="small" /> 月开始，每
            <el-input-number v-model="monthStep.step" :min="1" :max="12" size="small" /> 个月执行一次
          </el-radio>
          <el-radio value="specify">指定</el-radio>
        </el-radio-group>
        <div v-if="monthType === 'specify'" class="specify-values">
          <el-checkbox-group v-model="monthValues" @change="updateCron">
            <el-checkbox v-for="i in 12" :key="i" :value="i">{{ i }}月</el-checkbox>
          </el-checkbox-group>
        </div>
      </el-tab-pane>

      <!-- 周 -->
      <el-tab-pane label="周" name="week">
        <el-radio-group v-model="weekType" @change="updateCron">
          <el-radio value="every">每周</el-radio>
          <el-radio value="notSpecify">不指定（使用日设置）</el-radio>
          <el-radio value="range">
            从 <el-select v-model="weekRange.start" size="small" style="width: 80px">
              <el-option v-for="item in weekOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select> 到
            <el-select v-model="weekRange.end" size="small" style="width: 80px">
              <el-option v-for="item in weekOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-radio>
          <el-radio value="specify">指定</el-radio>
        </el-radio-group>
        <div v-if="weekType === 'specify'" class="specify-values">
          <el-checkbox-group v-model="weekValues" @change="updateCron">
            <el-checkbox v-for="item in weekOptions" :key="item.value" :value="item.value">{{ item.label }}</el-checkbox>
          </el-checkbox-group>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Cron表达式预览 -->
    <div class="cron-result">
      <el-input v-model="cronExpression" readonly>
        <template #prepend>Cron表达式</template>
      </el-input>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch, onMounted } from 'vue'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const activeTab = ref('second')
const cronExpression = ref('* * * * * ?')

/** 传入的表达式无法被生成器还原时置为 true：此时只展示原值，绝不回写。 */
const unsupported = ref(false)
/** 第 7 位年份字段生成器没有控件，原样保留，避免重新拼装时被丢掉。 */
const yearField = ref('')

// 秒
const secondType = ref('every')
const secondRange = ref({ start: 0, end: 59 })
const secondStep = ref({ start: 0, step: 1 })
const secondValues = ref<number[]>([])

// 分
const minuteType = ref('every')
const minuteRange = ref({ start: 0, end: 59 })
const minuteStep = ref({ start: 0, step: 1 })
const minuteValues = ref<number[]>([])

// 时
const hourType = ref('every')
const hourRange = ref({ start: 0, end: 23 })
const hourStep = ref({ start: 0, step: 1 })
const hourValues = ref<number[]>([])

// 日
const dayType = ref('every')
const dayRange = ref({ start: 1, end: 31 })
const dayStep = ref({ start: 1, step: 1 })
const dayValues = ref<number[]>([])

// 月
const monthType = ref('every')
const monthRange = ref({ start: 1, end: 12 })
const monthStep = ref({ start: 1, step: 1 })
const monthValues = ref<number[]>([])

// 周
const weekType = ref('notSpecify')
const weekRange = ref({ start: 1, end: 7 })
const weekValues = ref<number[]>([])

const weekOptions = [
  { label: '周日', value: 1 },
  { label: '周一', value: 2 },
  { label: '周二', value: 3 },
  { label: '周三', value: 4 },
  { label: '周四', value: 5 },
  { label: '周五', value: 6 },
  { label: '周六', value: 7 }
]

// 生成单个字段的cron值
const generateField = (
  type: string,
  range: { start: number; end: number },
  step: { start: number; step: number },
  values: number[],
  _isDay = false,
  _isWeek = false
): string => {
  switch (type) {
    case 'every':
      return '*'
    case 'notSpecify':
      return '?'
    case 'range':
      return `${range.start}-${range.end}`
    case 'step':
      return `${step.start}/${step.step}`
    case 'last':
      return 'L'
    case 'specify':
      // 不能原地排序：values 是响应式数组，在生成链路里改动它会触发额外的更新轮次
      return values.length > 0 ? [...values].sort((a, b) => a - b).join(',') : '*'
    default:
      return '*'
  }
}

// 按当前控件状态拼出表达式（纯函数，不写状态、不 emit）
const buildExpression = (): string => {
  const second = generateField(secondType.value, secondRange.value, secondStep.value, secondValues.value)
  const minute = generateField(minuteType.value, minuteRange.value, minuteStep.value, minuteValues.value)
  const hour = generateField(hourType.value, hourRange.value, hourStep.value, hourValues.value)
  const day = generateField(dayType.value, dayRange.value, dayStep.value, dayValues.value, true)
  const month = generateField(monthType.value, monthRange.value, monthStep.value, monthValues.value)
  const week = generateField(weekType.value, weekRange.value, { start: 1, step: 1 }, weekValues.value, false, true)

  // 日和周不能同时为*或?，需要互斥
  let finalDay = day
  let finalWeek = week
  if (day !== '?' && week !== '?') {
    if (dayType.value === 'notSpecify') {
      finalDay = '?'
    } else {
      finalWeek = '?'
    }
  }
  if (day === '?' && week === '?') {
    finalDay = '*'
  }

  const fields = [second, minute, hour, finalDay, month, finalWeek]
  if (yearField.value) {
    fields.push(yearField.value)
  }
  return fields.join(' ')
}

/**
 * 回填控件状态期间为 true。
 * 区间/步长用的是 deep watch，回填时同样会触发，必须屏蔽掉，
 * 否则解析结果又会被当成用户改动回写给父组件。
 */
let syncing = false

// 用户改动控件后才更新并回写表达式
const updateCron = () => {
  if (syncing) return
  unsupported.value = false
  cronExpression.value = buildExpression()
  emit('update:modelValue', cronExpression.value)
}

// 区间必须满足 start <= end，否则会拼出 59-0 这类非法片段
const invalidRanges = computed(() => {
  const checks: Array<[string, string, { start: number; end: number }]> = [
    ['秒', secondType.value, secondRange.value],
    ['分', minuteType.value, minuteRange.value],
    ['时', hourType.value, hourRange.value],
    ['日', dayType.value, dayRange.value],
    ['月', monthType.value, monthRange.value],
    ['周', weekType.value, weekRange.value]
  ]
  return checks
    .filter(([, type, range]) => type === 'range' && range.start > range.end)
    .map(([label]) => label)
})

// 监听各字段变化
watch([secondRange, secondStep], updateCron, { deep: true })
watch([minuteRange, minuteStep], updateCron, { deep: true })
watch([hourRange, hourStep], updateCron, { deep: true })
watch([dayRange, dayStep], updateCron, { deep: true })
watch([monthRange, monthStep], updateCron, { deep: true })
watch([weekRange], updateCron, { deep: true })

// ==================== 表达式解析 ====================

interface ParsedField {
  type: string
  range?: { start: number; end: number }
  step?: { start: number; step: number }
  values?: number[]
}

interface FieldOptions {
  allowNotSpecify?: boolean
  allowLast?: boolean
  allowStep?: boolean
}

/** 解析单个整数，越界或非数字一律判为不可解析（不再产出 NaN 片段）。 */
const parseBoundedInt = (raw: string, min: number, max: number): number | null => {
  if (!/^\d+$/.test(raw.trim())) return null
  const value = Number.parseInt(raw.trim(), 10)
  if (Number.isNaN(value) || value < min || value > max) return null
  return value
}

/** 解析单个 cron 字段，无法用生成器控件表达时返回 null。 */
const parseField = (raw: string, min: number, max: number, options: FieldOptions = {}): ParsedField | null => {
  const text = (raw || '').trim()
  if (!text) return null
  if (text === '*') return { type: 'every' }
  if (text === '?') return options.allowNotSpecify ? { type: 'notSpecify' } : null
  if (text.toUpperCase() === 'L') return options.allowLast ? { type: 'last' } : null

  if (text.includes('/')) {
    if (options.allowStep === false) return null
    const [startRaw, stepRaw, ...rest] = text.split('/')
    if (rest.length > 0) return null
    const start = startRaw === '*' ? min : parseBoundedInt(startRaw, min, max)
    const step = parseBoundedInt(stepRaw, 1, max)
    if (start === null || step === null) return null
    return { type: 'step', step: { start, step } }
  }

  if (text.includes('-')) {
    const [startRaw, endRaw, ...rest] = text.split('-')
    if (rest.length > 0) return null
    const start = parseBoundedInt(startRaw, min, max)
    const end = parseBoundedInt(endRaw, min, max)
    if (start === null || end === null || start > end) return null
    return { type: 'range', range: { start, end } }
  }

  const values: number[] = []
  for (const item of text.split(',')) {
    const value = parseBoundedInt(item, min, max)
    if (value === null) return null
    values.push(value)
  }
  return { type: 'specify', values: [...new Set(values)].sort((a, b) => a - b) }
}

const applyField = (
  parsed: ParsedField,
  typeRef: { value: string },
  rangeRef: { value: { start: number; end: number } },
  stepRef: { value: { start: number; step: number } } | null,
  valuesRef: { value: number[] }
) => {
  typeRef.value = parsed.type
  if (parsed.range) rangeRef.value = { ...parsed.range }
  if (parsed.step && stepRef) stepRef.value = { ...parsed.step }
  valuesRef.value = parsed.values ? [...parsed.values] : []
}

/**
 * 解析已有表达式并回填控件状态。
 *
 * 返回 false 表示生成器无法完整还原该表达式，调用方必须保留原值，
 * 绝不能用控件默认值重新拼一个表达式回写（历史缺陷：会静默改成「每秒执行一次」）。
 */
const parseCron = (cron: string): boolean => {
  const parts = (cron || '').trim().split(/\s+/).filter(Boolean)
  if (parts.length < 6 || parts.length > 7) return false

  const second = parseField(parts[0], 0, 59)
  const minute = parseField(parts[1], 0, 59)
  const hour = parseField(parts[2], 0, 23)
  const day = parseField(parts[3], 1, 31, { allowNotSpecify: true, allowLast: true })
  const month = parseField(parts[4], 1, 12)
  const week = parseField(parts[5], 1, 7, { allowNotSpecify: true, allowStep: false })
  if (!second || !minute || !hour || !day || !month || !week) return false

  const previousYear = yearField.value
  applyField(second, secondType, secondRange, secondStep, secondValues)
  applyField(minute, minuteType, minuteRange, minuteStep, minuteValues)
  applyField(hour, hourType, hourRange, hourStep, hourValues)
  applyField(day, dayType, dayRange, dayStep, dayValues)
  applyField(month, monthType, monthRange, monthStep, monthValues)
  applyField(week, weekType, weekRange, null, weekValues)
  yearField.value = parts[6] || ''

  // 兜底：只有能原样还原才算解析成功，否则宁可标记为不支持也不回写
  if (buildExpression() !== parts.join(' ')) {
    yearField.value = previousYear
    return false
  }
  return true
}

/** 用外部传入的表达式同步控件状态；解析失败时只展示原值，不 emit。 */
const syncFromModel = async (value: string) => {
  syncing = true
  try {
    const text = (value || '').trim()
    if (!text) {
      unsupported.value = false
      cronExpression.value = buildExpression()
      return
    }
    const parsed = parseCron(text)
    unsupported.value = !parsed
    cronExpression.value = parsed ? buildExpression() : text
  } finally {
    // deep watch 是异步 flush 的，要等这一轮调度跑完才能解除屏蔽
    await nextTick()
    syncing = false
  }
}

onMounted(() => {
  // 只同步不回写：历史实现在这里无条件 emit，导致「打开生成器即改写任务」
  syncFromModel(props.modelValue)
})

watch(() => props.modelValue, (val) => {
  if (val && val !== cronExpression.value) {
    syncFromModel(val)
  }
})
</script>

<style lang="scss" scoped>
.cron-builder {
  .cron-alert {
    margin-bottom: 12px;
  }

  .el-radio-group {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;

    .el-radio {
      display: flex;
      align-items: center;
      height: 32px;
      margin-right: 0;

      .el-input-number {
        margin: 0 8px;
      }
    }
  }

  .specify-values {
    margin-top: 12px;
    padding: 12px;
    background: #f5f7fa;
    border-radius: 4px;

    .el-checkbox-group {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;

      .el-checkbox {
        width: 50px;
        margin-right: 0;
      }
    }
  }

  .cron-result {
    margin-top: 16px;

    :deep(.el-input-group__prepend) {
      background-color: #f5f7fa;
    }
  }
}
</style>
