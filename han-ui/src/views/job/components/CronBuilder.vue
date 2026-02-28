<template>
  <div class="cron-builder">
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
import { ref, watch, onMounted } from 'vue'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const activeTab = ref('second')
const cronExpression = ref('* * * * * ?')

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
      return values.length > 0 ? values.sort((a, b) => a - b).join(',') : '*'
    default:
      return '*'
  }
}

// 更新cron表达式
const updateCron = () => {
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

  cronExpression.value = `${second} ${minute} ${hour} ${finalDay} ${month} ${finalWeek}`
  emit('update:modelValue', cronExpression.value)
}

// 监听各字段变化
watch([secondRange, secondStep], updateCron, { deep: true })
watch([minuteRange, minuteStep], updateCron, { deep: true })
watch([hourRange, hourStep], updateCron, { deep: true })
watch([dayRange, dayStep], updateCron, { deep: true })
watch([monthRange, monthStep], updateCron, { deep: true })
watch([weekRange], updateCron, { deep: true })

// 解析传入的cron表达式
const parseCron = (cron: string) => {
  if (!cron) return
  const parts = cron.split(' ')
  if (parts.length < 6) return

  // 简单解析，只处理基本格式
  const [second, _minute, _hour, _day, _month, _week] = parts

  // 解析秒
  if (second === '*') {
    secondType.value = 'every'
  } else if (second.includes('/')) {
    secondType.value = 'step'
    const [start, step] = second.split('/')
    secondStep.value = { start: parseInt(start), step: parseInt(step) }
  } else if (second.includes('-')) {
    secondType.value = 'range'
    const [start, end] = second.split('-')
    secondRange.value = { start: parseInt(start), end: parseInt(end) }
  } else if (second.includes(',')) {
    secondType.value = 'specify'
    secondValues.value = second.split(',').map(Number)
  }

  // 类似处理其他字段...
}

onMounted(() => {
  if (props.modelValue) {
    parseCron(props.modelValue)
  }
  updateCron()
})

watch(() => props.modelValue, (val) => {
  if (val && val !== cronExpression.value) {
    parseCron(val)
  }
})
</script>

<style lang="scss" scoped>
.cron-builder {
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
