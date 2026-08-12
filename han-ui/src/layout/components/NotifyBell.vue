<template>
  <el-popover placement="bottom-end" :width="340" trigger="click" @show="loadNotices">
    <template #reference>
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="notify-badge" data-testid="notify-bell-badge">
        <el-icon class="nav-icon" data-testid="notify-bell-trigger"><Bell /></el-icon>
      </el-badge>
    </template>

    <div class="notify-panel" data-testid="notify-panel">
      <div class="notify-header">
        <span class="notify-title">通知公告</span>
        <div class="notify-actions">
          <el-link
            v-if="unreadCount > 0"
            type="primary"
            :underline="false"
            data-testid="notify-mark-all-read"
            @click="handleMarkAllRead"
          >全部已读</el-link>
          <el-link
            v-if="notices.length && canViewAll"
            type="primary"
            :underline="false"
            data-testid="notify-view-all"
            @click="goNoticeList"
          >查看全部</el-link>
        </div>
      </div>

      <el-scrollbar max-height="320px">
        <div v-if="loading" class="notify-empty">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>
        <div v-else-if="notices.length === 0" class="notify-empty" data-testid="notify-empty">暂无通知</div>
        <div v-else data-testid="notify-list">
          <div
            v-for="item in notices"
            :key="item.id"
            class="notify-item"
            :class="{ 'is-unread': item.read === false }"
            :data-notice-title="item.noticeTitle"
            :data-testid="`notify-item-${item.id}`"
            @click="handleOpenNotice(item)"
          >
            <div class="notify-item-header">
              <el-tag :type="item.noticeType === '1' ? 'primary' : 'warning'" size="small">
                {{ item.noticeType === '1' ? '通知' : '公告' }}
              </el-tag>
              <span class="notify-item-title">{{ item.noticeTitle }}</span>
            </div>
            <div class="notify-item-time">{{ formatTime(item.createTime) }}</div>
          </div>
        </div>
      </el-scrollbar>
    </div>
  </el-popover>

  <el-dialog v-model="detailVisible" title="通知详情" width="640px" destroy-on-close>
    <div v-if="currentNotice" class="notice-detail" data-testid="notify-detail">
      <div class="notice-detail-header">
        <el-tag :type="currentNotice.noticeType === '1' ? 'primary' : 'warning'" size="small">
          {{ currentNotice.noticeType === '1' ? '通知' : '公告' }}
        </el-tag>
        <span class="notice-detail-title" data-testid="notify-detail-title">{{ currentNotice.noticeTitle }}</span>
      </div>
      <div class="notice-detail-time">{{ formatTime(currentNotice.createTime) }}</div>
      <div class="notice-detail-content" v-html="sanitizeHtml(currentNotice.noticeContent || '')"></div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, Loading } from '@element-plus/icons-vue'
import { getLatestNotices, getUnreadCount, markAllNoticeRead, markNoticeRead } from '@/api/system/notice'
import type { Notice } from '@/api/system/notice'
import { getToken } from '@/utils/auth'
import { sanitizeHtml } from '@/utils/sanitize-html'
import { tryRefreshSession } from '@/utils/session-refresh'
import { useUserStore } from '@/stores/user'

const NOTICE_POLL_INTERVAL_MS = 60000

const router = useRouter()
const userStore = useUserStore()
const unreadCount = ref(0)
const notices = ref<Notice[]>([])
const loading = ref(false)
const detailVisible = ref(false)
const currentNotice = ref<Notice | null>(null)
const canViewAll = userStore.hasPermission('system:notice:list')
let pollTimer: ReturnType<typeof setInterval> | null = null
let streamAbortController: AbortController | null = null
let isComponentUnmounted = false

const formatTime = (time: string) => {
  if (!time) return ''
  return time.replace('T', ' ').replace(/\.\d+$/, '').substring(0, 16)
}

const refreshNoticeState = async () => {
  if (!getToken()) {
    unreadCount.value = 0
    notices.value = []
    return
  }
  await Promise.all([fetchUnreadCount(), loadNotices()])
}

const fetchUnreadCount = async () => {
  if (!getToken()) return
  try {
    const res = await getUnreadCount()
    unreadCount.value = (res.data as number) || 0
  } catch {
    // ignore
  }
}

const loadNotices = async () => {
  if (!getToken()) {
    notices.value = []
    return
  }
  loading.value = true
  try {
    const res = await getLatestNotices(5)
    notices.value = (res.data as Notice[]) || []
  } catch {
    notices.value = []
  } finally {
    loading.value = false
  }
}

const goNoticeList = () => {
  if (!canViewAll) return
  router.push('/system/notice')
}

const handleOpenNotice = async (notice: Notice) => {
  currentNotice.value = notice
  detailVisible.value = true
  if (notice.read === false) {
    try {
      await markNoticeRead(notice.id)
      await refreshNoticeState()
    } catch {
      ElMessage.warning('通知已打开，但已读状态同步失败')
    }
  }
}

const handleMarkAllRead = async () => {
  try {
    await markAllNoticeRead()
    await refreshNoticeState()
    ElMessage.success('已全部标记为已读')
  } catch {
    // 错误由全局拦截器处理
  }
}

const buildNoticeStreamUrl = (baseUrl: string) => {
  const normalizedBaseUrl = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl
  return normalizedBaseUrl ? `${normalizedBaseUrl}/system/notice/sse` : '/system/notice/sse'
}

const startPolling = () => {
  if (pollTimer || isComponentUnmounted) {
    return
  }
  pollTimer = setInterval(refreshNoticeState, NOTICE_POLL_INTERVAL_MS)
}

const stopPolling = () => {
  if (!pollTimer) {
    return
  }
  clearInterval(pollTimer)
  pollTimer = null
}

const closeNoticeStream = () => {
  if (!streamAbortController) {
    return
  }
  streamAbortController.abort()
  streamAbortController = null
}

const handleNoticeStreamSegment = (segment: string) => {
  if (!segment.trim()) {
    return
  }
  const eventName = segment
    .split(/\r?\n/)
    .find((line) => line.startsWith('event:'))
    ?.replace(/^event:\s*/, '')
    .trim()

  if (eventName === 'notice') {
    void refreshNoticeState()
  }
}

const consumeNoticeStream = async (body: ReadableStream<Uint8Array>, signal: AbortSignal) => {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }

      buffer += decoder.decode(value, { stream: true })
      const segments = buffer.split(/\r?\n\r?\n/)
      buffer = segments.pop() || ''
      segments.forEach(handleNoticeStreamSegment)
    }
  } finally {
    reader.releaseLock()
  }
}

const connectSse = async (accessToken = getToken() || '') => {
  if (!accessToken || isComponentUnmounted) {
    startPolling()
    return
  }

  closeNoticeStream()
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  const controller = new AbortController()
  streamAbortController = controller
  let shouldFallbackToPolling = true

  try {
    const response = await fetch(buildNoticeStreamUrl(baseUrl), {
      headers: {
        Accept: 'text/event-stream',
        Authorization: `Bearer ${accessToken}`
      },
      signal: controller.signal
    })

    if (controller.signal.aborted) {
      return
    }

    if (response.status === 401) {
      const refreshResult = await tryRefreshSession(baseUrl)
      if (refreshResult.status === 'success' && refreshResult.accessToken) {
        shouldFallbackToPolling = false
        await connectSse(refreshResult.accessToken)
        return
      }
      startPolling()
      return
    }

    const contentType = response.headers.get('content-type') || ''
    if (!response.ok || !response.body || !contentType.includes('text/event-stream')) {
      startPolling()
      return
    }

    stopPolling()
    await consumeNoticeStream(response.body, controller.signal)
  } catch {
    if (!controller.signal.aborted) {
      startPolling()
    }
  } finally {
    if (streamAbortController === controller) {
      streamAbortController = null
    }
    if (shouldFallbackToPolling && !controller.signal.aborted && !isComponentUnmounted) {
      startPolling()
    }
  }
}

onMounted(() => {
  isComponentUnmounted = false
  refreshNoticeState()
  void connectSse()
})

onUnmounted(() => {
  isComponentUnmounted = true
  closeNoticeStream()
  stopPolling()
})
</script>

<style lang="scss" scoped>
.notify-badge {
  display: inline-flex;
  align-items: center;
  cursor: pointer;

  :deep(.el-badge__content) {
    font-size: 10px;
  }
}

.nav-icon {
  font-size: 32px;
  color: #6b7280;
  cursor: pointer;
  padding: 8px;
  border-radius: 6px;
  transition: all 0.15s ease;

  &:hover {
    color: #2563eb;
    background: #f3f4f6;
  }
}

.notify-panel {
  margin: -12px;
}

.notify-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f3f4f6;
}

.notify-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.notify-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.notify-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 0;
  color: #9ca3af;
  font-size: 13px;
}

.notify-item {
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.15s ease;
  border-bottom: 1px solid #f9fafb;

  &:hover {
    background: #f9fafb;
  }

  &:last-child {
    border-bottom: none;
  }

  &.is-unread {
    background: #f8fbff;

    .notify-item-title {
      color: #111827;
      font-weight: 600;
    }
  }
}

.notify-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notify-item-title {
  font-size: 13px;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.notify-item-time {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
  padding-left: 52px;
}

.notice-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notice-detail-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notice-detail-title {
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}

.notice-detail-time {
  font-size: 13px;
  color: #9ca3af;
}

.notice-detail-content {
  line-height: 1.75;
  color: #374151;
  word-break: break-word;
}

html.dark {
  .notify-header { border-bottom-color: #1f2937; }
  .notify-title { color: #f9fafb; }
  .notify-item { border-bottom-color: #1f2937; &:hover { background: #1f2937; } }
  .notify-item-title { color: #e5e7eb; }
  .nav-icon { color: #9ca3af; &:hover { color: #3b82f6; background: #1f2937; } }
  .notify-item.is-unread { background: #111827; .notify-item-title { color: #f9fafb; } }
  .notice-detail-title { color: #f9fafb; }
  .notice-detail-content { color: #e5e7eb; }
}
</style>
