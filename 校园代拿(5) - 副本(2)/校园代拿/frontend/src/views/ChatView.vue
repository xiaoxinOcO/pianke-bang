<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useChatStore } from '../stores/useChatStore'
import { isCustomerServiceUsername } from '../constants/brand'
import { statusClass, statusLabelMap } from '../utils/taskStatus'
import { parseTaskInquiryMessage, type TaskInquiryData } from '../utils/taskInquiryMessage'
import type { SystemNoticeFeedItem } from '../types/app'

const r = useChatStore()
const search = ref('')
const selectedRelatedTask = ref<any | null>(null)
const chatMessagePanelEl = ref<HTMLElement | null>(null)
const showUserDropdown = ref(false)
const chatImageInputEl = ref<HTMLInputElement | null>(null)
const CHAT_IMAGE_PREFIX = '[CHAT_IMAGE]'
const showChatCropper = ref(false)
const cropImageEl = ref<HTMLImageElement | null>(null)
const cropBox = ref({ x: 0, y: 0, w: 0, h: 0 })
const cropDragStart = ref<{ x: number; y: number } | null>(null)
const chatInputEl = ref<HTMLTextAreaElement | null>(null)
const showEmojiPanel = ref(false)
const contactMenu = ref<{
  username: string
  x: number
  y: number
  kind?: 'normal' | 'fixed'
  title?: string
} | null>(null)

const filteredContacts = computed(() => {
  const kw = search.value.trim().toLowerCase()
  const list = (Array.isArray(r.chatContacts) ? r.chatContacts : []).filter(
    (c) => !r.isSystemNoticeContact?.(c.username)
  )
  const filtered = kw
    ? list.filter((c) => String(c.username || '').toLowerCase().includes(kw))
    : list
  const pinOrder = Array.isArray(r.chatPinnedUsernames) ? r.chatPinnedUsernames : []
  const pinRank = (username: string) => {
    const idx = pinOrder.indexOf(username)
    return idx >= 0 ? idx : Number.MAX_SAFE_INTEGER
  }
  return [...filtered].sort((a, b) => {
    const ar = pinRank(a.username)
    const br = pinRank(b.username)
    if (ar !== br) return ar - br
    return a.username.localeCompare(b.username, 'zh-CN')
  })
})

const isSystemNoticeActive = computed(() =>
  typeof r.isSystemNoticeContact === 'function' && r.isSystemNoticeContact(r.chatTarget)
)

const showSupportEntry = computed(() => {
  const kw = search.value.trim().toLowerCase()
  if (!kw) return true
  return '帮小新'.includes(kw) || '小新'.includes(kw) || '智能客服'.includes(kw) || '客服'.includes(kw)
})

function openXiaoxinSupport() {
  contactMenu.value = null
  if (typeof r.openSupportChat === 'function') {
    r.openSupportChat()
    return
  }
}

const publishedNotices = computed(() =>
  Array.isArray(r.publishedNoticesForChat) ? r.publishedNoticesForChat : []
)

const systemNoticeFeed = computed<SystemNoticeFeedItem[]>(() =>
  Array.isArray(r.systemNoticeFeed) ? r.systemNoticeFeed : []
)

function systemNoticeCategoryLabel(category?: string) {
  const key = String(category || '').toUpperCase()
  if (key === 'TASK') return '任务'
  if (key === 'WALLET') return '钱包'
  return '通知'
}

function contactDisplayName(contact: { username: string; displayName?: string }) {
  return contact.displayName || contact.username
}

function contactPreviewText(contact: { contactPreview?: string }) {
  return contact.contactPreview || '点击进入聊天'
}

function closeContactMenu() {
  contactMenu.value = null
}

function onContactContextMenu(event: MouseEvent, username: string) {
  event.preventDefault()
  event.stopPropagation()
  if (r.isSystemNoticeContact?.(username)) {
    openFixedEntryMenu(event, '系统通知')
    return
  }
  const menuW = 168
  const menuH = 96
  const x = Math.min(event.clientX, window.innerWidth - menuW - 8)
  const y = Math.min(event.clientY, window.innerHeight - menuH - 8)
  contactMenu.value = { username, x, y, kind: 'normal' }
}

function openFixedEntryMenu(event: MouseEvent, title: string) {
  event.preventDefault()
  event.stopPropagation()
  const menuW = 200
  const menuH = 72
  const x = Math.min(event.clientX, window.innerWidth - menuW - 8)
  const y = Math.min(event.clientY, window.innerHeight - menuH - 8)
  contactMenu.value = { username: '', x, y, kind: 'fixed', title }
}

function openContactActionsMenu(event: MouseEvent, username: string) {
  const rect = (event.currentTarget as HTMLElement | null)?.getBoundingClientRect()
  const menuW = 168
  const menuH = 96
  const x = rect
    ? Math.min(rect.right - menuW, window.innerWidth - menuW - 8)
    : Math.min(event.clientX, window.innerWidth - menuW - 8)
  const y = rect
    ? Math.min(rect.bottom + 4, window.innerHeight - menuH - 8)
    : Math.min(event.clientY, window.innerHeight - menuH - 8)
  event.preventDefault()
  event.stopPropagation()
  contactMenu.value = { username, x, y, kind: 'normal' }
}

function onPinContactFromMenu() {
  const name = contactMenu.value?.username
  if (name) r.pinChatContact?.(name)
  closeContactMenu()
}

function onDeleteContactFromMenu() {
  const name = contactMenu.value?.username
  if (!name) return
  if (!window.confirm(`确定删除与「${name}」的会话吗？有新消息时会重新出现。`)) {
    closeContactMenu()
    return
  }
  r.deleteChatContact?.(name)
  closeContactMenu()
}

function isContactPinned(username: string) {
  return typeof r.isChatContactPinned === 'function'
    ? r.isChatContactPinned(username)
    : Array.isArray(r.chatPinnedUsernames) && r.chatPinnedUsernames.includes(username)
}

function onClearAllUnread() {
  void r.clearAllChatUnread?.()
}

function onGlobalPointerDown(event: MouseEvent) {
  // 右键用于打开菜单，不要在 mousedown 时先关掉
  if (event.button !== 0) return
  const target = event.target as HTMLElement | null
  if (target?.closest('.chat-contact-menu')) return
  if (target?.closest('.chat-contact-more-btn')) return
  closeContactMenu()
}

onMounted(() => {
  document.addEventListener('mousedown', onGlobalPointerDown)
  document.addEventListener('scroll', closeContactMenu, true)
})

onUnmounted(() => {
  document.removeEventListener('mousedown', onGlobalPointerDown)
  document.removeEventListener('scroll', closeContactMenu, true)
})

const EMOJIS = [
  '😀',
  '😄',
  '😆',
  '🥳',
  '🙂',
  '😉',
  '😍',
  '😘',
  '😜',
  '🤔',
  '😴',
  '😭',
  '😡',
  '👍',
  '👎',
  '🙏',
  '🤝',
  '👏',
  '💪',
  '🔥',
  '✨',
  '⭐',
  '❤️',
  '💔',
  '🎉',
  '💯'
]

const currentContact = computed(() => {
  const target = String(r.chatTarget || '').trim()
  if (!target) return null
  if (r.isSystemNoticeContact?.(target)) return r.SYSTEM_NOTICE_CONTACT || null
  return filteredContacts.value.find((c) => c.username === target) || null
})

const activeMessages = computed(() => (Array.isArray(r.chatMessages) ? r.chatMessages : []))

const relatedTasks = computed(() => {
  const myName = String(r.myName || '').trim().toLowerCase()
  const contactName = String(r.chatTarget || currentContact.value?.username || '').trim().toLowerCase()
  if (!myName || !contactName) return []
  const tasks = Array.isArray(r.safeAllTasks) ? r.safeAllTasks : []
  return tasks
    .filter((t: any) => {
      const publisher = String(t.publisherName || '').trim().toLowerCase()
      const claimer = String(t.claimerName || '').trim().toLowerCase()
      const status = String(t.status || '').toUpperCase()
      const ongoingStatuses = new Set(['CLAIMED', 'PENDING_CONFIRMATION'])
      const isBetweenTwoUsers =
        (publisher === myName && claimer === contactName) ||
        (publisher === contactName && claimer === myName)
      return isBetweenTwoUsers && ongoingStatuses.has(status)
    })
    .slice()
    .sort((a: any, b: any) => String(b.updatedAt || b.createdAt || '').localeCompare(String(a.updatedAt || a.createdAt || '')))
})

function isCustomerServiceContact(contact: { username?: string; customerServiceStaff?: boolean } | null) {
  if (!contact || contact.isSystemNotice) return false
  return !!contact.customerServiceStaff || isCustomerServiceUsername(contact.username)
}

const isCustomerServiceActive = computed(() => isCustomerServiceContact(currentContact.value))

const customerServiceTasks = computed(() =>
  Array.isArray(r.myTasksForCustomerService) ? r.myTasksForCustomerService : []
)

function taskRoleLabel(task: any) {
  const me = String(r.myName || '').trim().toLowerCase()
  const publisher = String(task?.publisherName || '').trim().toLowerCase()
  const claimer = String(task?.claimerName || '').trim().toLowerCase()
  if (me && publisher === me) return '发布者'
  if (me && claimer === me) return '接单员'
  return '相关用户'
}

async function onSendTaskToCustomerService(task: any, event?: Event) {
  event?.stopPropagation()
  await r.sendTaskToCustomerService?.(task)
}

function taskStatusLabel(task: any) {
  const key = String(task?.status || '').toUpperCase()
  return statusLabelMap[key as keyof typeof statusLabelMap] || task?.status || '-'
}

function taskStatusClass(task: any) {
  return statusClass(String(task?.status || '').toUpperCase() as any) || ''
}

function onPickContact(username: string) {
  selectedRelatedTask.value = null
  r.openChatWith(username)
  void nextTick().then(() => r.loadChatHistory?.())
}

function openTaskProgress(task: any) {
  selectedRelatedTask.value = task
}

function closeTaskProgress() {
  selectedRelatedTask.value = null
}

function findTaskForInquiry(inquiry: TaskInquiryData) {
  const id = Number(inquiry.taskId)
  if (!Number.isFinite(id)) return null
  const pools = [
    ...(Array.isArray(r.safeAllTasks) ? r.safeAllTasks : []),
    ...(Array.isArray(r.myTasksForCustomerService) ? r.myTasksForCustomerService : [])
  ]
  return pools.find((task: any) => Number(task.id) === id) ?? null
}

function inquiryToTaskStub(inquiry: TaskInquiryData) {
  const rewardRaw = String(inquiry.reward || '').replace(/[¥,\s]/g, '')
  const id = Number(inquiry.taskId)
  return {
    id: Number.isFinite(id) ? id : inquiry.taskId,
    title: inquiry.title || '任务订单',
    status: inquiry.status || '-',
    reward: Number.parseFloat(rewardRaw) || 0,
    publisherName: inquiry.publisher || '-',
    claimerName: inquiry.claimer && inquiry.claimer !== '待接单' ? inquiry.claimer : '',
    pickupLocation: inquiry.pickup || '-',
    dropoffLocation: inquiry.dropoff || '-'
  }
}

async function openTaskInquiry(inquiry: TaskInquiryData) {
  const hit = findTaskForInquiry(inquiry)
  if (hit && typeof r.openTaskDetail === 'function') {
    await r.openTaskDetail(hit)
    return
  }
  openTaskProgress(hit || inquiryToTaskStub(inquiry))
}

function onTaskInquiryClick(msg: any) {
  const inquiry = chatTaskInquiry(msg)
  if (inquiry) void openTaskInquiry(inquiry)
}

function onMessageKeydown(e: KeyboardEvent) {
  if ((e.key === 'Backspace' || e.key === 'Delete') && String(r.chatImageDraft || '').trim()) {
    const hasText = String(r.chatInput || '').length > 0
    const el = chatInputEl.value
    const start = el && typeof el.selectionStart === 'number' ? el.selectionStart : 0
    const end = el && typeof el.selectionEnd === 'number' ? el.selectionEnd : 0
    const hasSelection = end > start
    // 没有文字，或光标在最前且没有选中文本时，按删除键就删掉图片（像删“前缀内容”一样）
    if (!hasText || (!hasSelection && start === 0)) {
      e.preventDefault()
      r.clearChatImageDraft()
      return
    }
  }
  if (e.key !== 'Enter') return
  if (e.shiftKey) return
  e.preventDefault()
  void r.sendChatMessage()
}

function pickChatImage() {
  chatImageInputEl.value?.click()
}

function toggleEmojiPanel() {
  if (!r.chatTarget) return
  showEmojiPanel.value = !showEmojiPanel.value
}

function closeEmojiPanel() {
  showEmojiPanel.value = false
}

function insertEmoji(emoji: string) {
  if (!r.chatTarget) return
  const el = chatInputEl.value
  const current = String(r.chatInput || '')
  const e = String(emoji || '')
  if (!el) {
    r.chatInput = `${current}${e}`
    closeEmojiPanel()
    return
  }
  const start = typeof el.selectionStart === 'number' ? el.selectionStart : current.length
  const end = typeof el.selectionEnd === 'number' ? el.selectionEnd : start
  const next = current.slice(0, start) + e + current.slice(end)
  r.chatInput = next
  closeEmojiPanel()
  void nextTick().then(() => {
    el.focus()
    const pos = start + e.length
    try {
      el.setSelectionRange(pos, pos)
    } catch {
      // ignore
    }
  })
}

function openChatCropper() {
  if (!String(r.chatImageDraft || '').trim()) return
  showChatCropper.value = true
  cropDragStart.value = null
  void nextTick(() => initCropBox())
}

function closeChatCropper() {
  showChatCropper.value = false
  cropDragStart.value = null
}

function onCropPointerDown(event: PointerEvent) {
  const img = cropImageEl.value
  if (!img) return
  const rect = img.getBoundingClientRect()
  const x = Math.max(0, Math.min(rect.width, event.clientX - rect.left))
  const y = Math.max(0, Math.min(rect.height, event.clientY - rect.top))
  cropDragStart.value = { x, y }
  cropBox.value = { x, y, w: 1, h: 1 }
}

function onCropPointerMove(event: PointerEvent) {
  if (!cropDragStart.value) return
  const img = cropImageEl.value
  if (!img) return
  const rect = img.getBoundingClientRect()
  const x = Math.max(0, Math.min(rect.width, event.clientX - rect.left))
  const y = Math.max(0, Math.min(rect.height, event.clientY - rect.top))
  const sx = cropDragStart.value.x
  const sy = cropDragStart.value.y
  cropBox.value = {
    x: Math.min(sx, x),
    y: Math.min(sy, y),
    w: Math.max(1, Math.abs(x - sx)),
    h: Math.max(1, Math.abs(y - sy))
  }
}

function onCropPointerUp() {
  cropDragStart.value = null
}

function initCropBox() {
  const img = cropImageEl.value
  if (!img) return
  const rect = img.getBoundingClientRect()
  if (rect.width <= 1 || rect.height <= 1) return
  cropBox.value = {
    x: rect.width * 0.15,
    y: rect.height * 0.15,
    w: rect.width * 0.7,
    h: rect.height * 0.7
  }
}

function applyChatCrop() {
  const img = cropImageEl.value
  if (!img) return
  const rect = img.getBoundingClientRect()
  const cw = cropBox.value.w
  const ch = cropBox.value.h
  if (cw < 8 || ch < 8) return
  const scaleX = img.naturalWidth / Math.max(1, rect.width)
  const scaleY = img.naturalHeight / Math.max(1, rect.height)
  const sx = Math.max(0, Math.floor(cropBox.value.x * scaleX))
  const sy = Math.max(0, Math.floor(cropBox.value.y * scaleY))
  const sw = Math.max(1, Math.floor(cw * scaleX))
  const sh = Math.max(1, Math.floor(ch * scaleY))
  const canvas = document.createElement('canvas')
  canvas.width = sw
  canvas.height = sh
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.drawImage(img, sx, sy, sw, sh, 0, 0, sw, sh)
  r.setChatImageDraft(canvas.toDataURL('image/jpeg', 0.9))
  closeChatCropper()
}

function parseChatImagePayload(content?: string) {
  const raw = String(content || '')
  if (!raw.startsWith(CHAT_IMAGE_PREFIX)) return null
  try {
    const payload = JSON.parse(raw.slice(CHAT_IMAGE_PREFIX.length)) as { image?: string; text?: string }
    const image = String(payload?.image || '').trim()
    if (!image) return null
    return {
      image,
      text: String(payload?.text || '').trim()
    }
  } catch {
    return null
  }
}

function chatMessageImage(msg: any) {
  return parseChatImagePayload(msg?.content)?.image || ''
}

function chatMessageText(msg: any) {
  const parsed = parseChatImagePayload(msg?.content)
  return parsed ? parsed.text : String(msg?.content || '')
}

function isSystemMessage(content?: string) {
  return String(content || '').trim().startsWith('[来自智能客服]')
}

function chatTaskInquiry(msg: any) {
  return parseTaskInquiryMessage(msg?.content)
}

function scrollChatToLatest() {
  const el = chatMessagePanelEl.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

watch(
  () => r.systemNoticeFeed,
  async () => {
    if (!isSystemNoticeActive.value) return
    await nextTick()
    scrollChatToLatest()
  },
  { deep: true }
)

watch(
  () => r.chatMessages,
  async () => {
    await nextTick()
    scrollChatToLatest()
  },
  { deep: true }
)

watch(
  () => r.chatTarget,
  async () => {
    await nextTick()
    scrollChatToLatest()
  }
)
</script>

<template>
  <main class="chat-shell-page" @click="closeEmojiPanel">
    <div v-if="r.isLoggedIn" class="page-top-right-avatar">
      <div class="user-entry" @click.stop="showUserDropdown = !showUserDropdown">
        <div class="rider-avatar-wrap" :class="{ 'rider-ring': r.isRiderApproved }">
          <img v-if="r.myAvatarUrl" :src="r.normalizeImageUrl(r.myAvatarUrl)" alt="avatar" class="user-entry-avatar" />
          <div v-else class="user-entry-avatar user-entry-fallback">{{ (r.myName || 'U').slice(0, 1).toUpperCase() }}</div>
          <span v-if="r.isRiderApproved" class="rider-star-badge" title="接单员">★</span>
        </div>

        <div v-if="showUserDropdown" class="user-dropdown" @click.stop>
          <button class="user-dropdown-item" @click="r.goPersonalInfo()">个人资料</button>
          <button class="user-dropdown-item" @click="r.goProfile(); r.profileSubModule = 'published'">我的任务</button>
          <button class="user-dropdown-item" @click="r.goProfile(); r.profileSubModule = 'history'">历史记录</button>
          <button class="user-dropdown-item" @click="r.goRiderApply()">接单员申请</button>
          <button class="user-dropdown-item" @click="r.goWallet">我的钱包</button>
          <button class="user-dropdown-item" @click="r.goHall(); showUserDropdown = false;">返回大厅</button>
          <button class="user-dropdown-item danger" @click="r.logout()">退出登录</button>
        </div>
      </div>
    </div>

    <section class="chat-shell-card">
      <header class="chat-shell-header">
        <div class="chat-shell-title-block">
          <button type="button" class="chat-back-link" @click="r.goHall()">
            ← 返回大厅
          </button>
          <h1>聊天</h1>
        </div>
        <button class="ghost-local chat-refresh-btn" type="button" @click="r.fetchChatContacts()">刷新联系人</button>
      </header>

      <div class="chat-shell-body">
        <aside class="chat-list-pane">
          <div class="chat-list-toolbar">
            <span class="chat-list-title">消息</span>
            <div class="chat-list-tools">
              <button
                type="button"
                class="chat-list-tool-btn"
                title="清除未读"
                aria-label="清除未读"
                @click="onClearAllUnread()"
              >
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M4 7h16M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" stroke-linecap="round" />
                  <path d="M6 7l1 13h10l1-13" stroke-linejoin="round" />
                  <path d="M10 11v5M14 11v5" stroke-linecap="round" />
                </svg>
              </button>
            </div>
          </div>
          <div class="chat-search-box">
            <span class="chat-search-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="7" />
                <path d="M20 20l-3-3" stroke-linecap="round" />
              </svg>
            </span>
            <input v-model="search" type="text" placeholder="搜索最近联系人" />
          </div>

          <ul class="chat-contact-list" @contextmenu.prevent>
            <li
              v-if="r.SYSTEM_NOTICE_USERNAME"
              :class="{ active: isSystemNoticeActive }"
              class="chat-contact-item--system"
              @contextmenu.prevent="openFixedEntryMenu($event, '系统通知')"
            >
              <button
                type="button"
                class="chat-contact-row"
                @click="onPickContact(r.SYSTEM_NOTICE_USERNAME)"
                @contextmenu.prevent.stop="openFixedEntryMenu($event, '系统通知')"
              >
                <div class="chat-contact-avatar-wrap">
                  <div class="chat-contact-avatar chat-contact-avatar-fallback chat-contact-avatar--system" aria-hidden="true">
                    🔔
                  </div>
                </div>
                <div class="chat-contact-meta">
                  <div class="chat-contact-name-row">
                    <span class="chat-contact-name">{{ r.SYSTEM_NOTICE_CONTACT?.displayName || '系统通知' }}</span>
                    <span
                      v-if="r.unreadMap[r.SYSTEM_NOTICE_USERNAME] > 0"
                      class="chat-contact-unread"
                    >
                      {{ r.unreadMap[r.SYSTEM_NOTICE_USERNAME] }}
                    </span>
                  </div>
                  <p class="chat-contact-preview">
                    {{ r.SYSTEM_NOTICE_CONTACT?.contactPreview || '平台公告与重要提醒' }}
                  </p>
                </div>
              </button>
            </li>
            <li
              v-if="showSupportEntry"
              class="chat-contact-item--system chat-contact-item--xiaoxin"
              @contextmenu.prevent="openFixedEntryMenu($event, '帮小新')"
            >
              <button
                type="button"
                class="chat-contact-row"
                @click="openXiaoxinSupport"
                @contextmenu.prevent.stop="openFixedEntryMenu($event, '帮小新')"
              >
                <div class="chat-contact-avatar-wrap">
                  <img
                    class="chat-contact-avatar chat-contact-avatar--brand"
                    src="/xiaoxin-robot-mascot.png"
                    alt=""
                  />
                </div>
                <div class="chat-contact-meta">
                  <div class="chat-contact-name-row">
                    <span class="chat-contact-name">帮小新</span>
                  </div>
                  <p class="chat-contact-preview">智能客服 · 发布 / 接单 / 提现随时问</p>
                </div>
              </button>
            </li>
            <li
              v-for="c in filteredContacts"
              :key="c.username"
              :class="{ active: c.username === r.chatTarget, pinned: isContactPinned(c.username) }"
              @contextmenu.prevent="onContactContextMenu($event, c.username)"
            >
              <div class="chat-contact-row">
                <button
                  type="button"
                  class="chat-contact-main"
                  @click="onPickContact(c.username)"
                  @contextmenu.prevent.stop="onContactContextMenu($event, c.username)"
                >
                  <div class="chat-contact-avatar-wrap" :class="{ 'rider-ring': r.isRiderUser(c.username, c.riderApproved) }">
                    <img
                      v-if="r.chatAvatarUrl(c.username)"
                      class="chat-contact-avatar chat-contact-avatar--brand"
                      :src="r.chatAvatarUrl(c.username)"
                      alt="avatar"
                    />
                    <div v-else class="chat-contact-avatar chat-contact-avatar-fallback">{{ c.username.slice(0, 1).toUpperCase() }}</div>
                    <span v-if="r.isRiderUser(c.username, c.riderApproved)" class="rider-star-badge rider-star-large" title="接单员">★</span>
                  </div>

                  <div class="chat-contact-meta">
                    <div class="chat-contact-name-row">
                      <span class="chat-contact-name">{{ contactDisplayName(c) }}</span>
                      <span v-if="r.unreadMap[c.username] > 0" class="chat-contact-unread">{{ r.unreadMap[c.username] }}</span>
                    </div>
                    <p class="chat-contact-preview">{{ contactPreviewText(c) }}</p>
                  </div>
                </button>
                <button
                  type="button"
                  class="chat-contact-more-btn"
                  title="更多操作"
                  aria-label="更多操作"
                  @click.stop="openContactActionsMenu($event, c.username)"
                >
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true">
                    <circle cx="12" cy="5" r="1.8" />
                    <circle cx="12" cy="12" r="1.8" />
                    <circle cx="12" cy="19" r="1.8" />
                  </svg>
                </button>
              </div>
            </li>
          </ul>
        </aside>

        <teleport to="body">
          <div
            v-if="contactMenu"
            class="chat-contact-menu"
            :style="{ left: `${contactMenu.x}px`, top: `${contactMenu.y}px` }"
            @contextmenu.prevent
          >
            <template v-if="contactMenu.kind === 'fixed'">
              <div class="chat-contact-menu-tip">
                「{{ contactMenu.title || '该入口' }}」为固定入口，不支持置顶/删除
              </div>
            </template>
            <template v-else>
              <button type="button" class="chat-contact-menu-item" @click="onPinContactFromMenu()">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M12 19V5M12 5l4 4M12 5 8 9" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                <span>{{ isContactPinned(contactMenu.username) ? '取消置顶' : '置顶' }}</span>
              </button>
              <button type="button" class="chat-contact-menu-item danger" @click="onDeleteContactFromMenu()">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M4 7h16M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" stroke-linecap="round" />
                  <path d="M6 7l1 13h10l1-13" stroke-linejoin="round" />
                </svg>
                <span>删除</span>
              </button>
            </template>
          </div>
        </teleport>

        <section class="chat-main-pane">
          <div class="chat-main-topbar">
            <div class="chat-main-title">
              <h2>{{ isSystemNoticeActive ? (r.SYSTEM_NOTICE_CONTACT?.displayName || '系统通知') : (r.chatTarget || '请选择联系人') }}</h2>
              <p>
                {{
                  isSystemNoticeActive
                    ? (r.SYSTEM_NOTICE_CONTACT?.contactPreview || '平台公告与重要提醒')
                    : currentContact
                      ? '点击头像可查看资料'
                      : '在左侧列表选择联系人开始聊天'
                }}
              </p>
            </div>
            <div class="chat-main-tools">
              <button class="ghost-local" type="button" :disabled="!r.chatTarget" @click="r.loadChatHistory()">刷新消息</button>
              <button
                v-if="!isSystemNoticeActive"
                class="ghost-local"
                type="button"
                :disabled="!currentContact"
                @click="currentContact && r.openContactProfile(currentContact.username)"
              >
                资料
              </button>
            </div>
          </div>

          <div ref="chatMessagePanelEl" class="chat-message-panel">
            <template v-if="isSystemNoticeActive">
              <div v-if="systemNoticeFeed.length === 0" class="chat-empty-state">暂无系统通知</div>
              <article
                v-for="item in systemNoticeFeed"
                :key="item.key"
                class="chat-notice-card"
                :class="{ 'chat-notice-card--unread': item.unread }"
              >
                <div class="chat-notice-card-head">
                  <h3>{{ item.title }}</h3>
                  <span v-if="item.pinned" class="chat-notice-pin">置顶</span>
                  <span v-else-if="item.kind === 'personal'" class="chat-notice-category">
                    {{ systemNoticeCategoryLabel(item.category) }}
                  </span>
                </div>
                <p class="chat-notice-card-time">{{ r.formatNoticeChatTime(item.time) }}</p>
                <div class="chat-notice-card-body">{{ item.content }}</div>
              </article>
            </template>
            <template v-else-if="r.chatTarget">
              <div v-if="activeMessages.length === 0" class="chat-empty-state">暂无消息，开始聊天吧</div>
              <template v-for="(msg, idx) in activeMessages" :key="`msg-${msg.id}`">
                <div v-if="r.shouldShowChatDivider(idx)" class="chat-date-divider">{{ r.formatChatDateDivider(msg.createdAt) }}</div>
                <div v-if="isSystemMessage(msg.content)" class="chat-message-row chat-message-row--system">
                  <div class="chat-system-chip">{{ String(msg.content || '').trim() }}</div>
                </div>
                <div
                  v-else
                  class="chat-message-row"
                  :class="[
                    msg.fromUsername === r.myName ? 'me' : 'other',
                    {
                      'chat-message-row--image': !!chatMessageImage(msg),
                      'chat-message-row--task-inquiry': !!chatTaskInquiry(msg)
                    }
                  ]"
                >
                  <div class="chat-message-avatar-wrap">
                    <img
                      v-if="r.chatAvatarUrl(msg.fromUsername)"
                      :src="r.chatAvatarUrl(msg.fromUsername)"
                      class="chat-message-avatar"
                      :class="{ 'chat-message-avatar--brand': r.chatAvatarUrl(msg.fromUsername).includes('/logo.png') }"
                      alt="avatar"
                    />
                    <div v-else class="chat-message-avatar chat-message-avatar-fallback">{{ msg.fromUsername.slice(0, 1).toUpperCase() }}</div>
                  </div>
                  <div class="chat-message-body">
                    <div class="chat-message-meta">
                      <span class="chat-message-name">{{ msg.fromUsername }}</span>
                      <span class="chat-message-time">{{ r.formatChatTime(msg.createdAt) }}</span>
                    </div>
                    <div class="chat-bubble" :class="{ 'chat-bubble--task-inquiry': !!chatTaskInquiry(msg) }">
                      <img
                        v-if="chatMessageImage(msg)"
                        class="chat-inline-image"
                        :src="chatMessageImage(msg)"
                        alt="chat-image"
                        @click="r.openImagePreview(chatMessageImage(msg))"
                      />
                      <article
                        v-else-if="chatTaskInquiry(msg)"
                        class="chat-task-inquiry-card chat-task-inquiry-card--clickable"
                        role="button"
                        tabindex="0"
                        title="点击查看订单详情"
                        @click="onTaskInquiryClick(msg)"
                        @keydown.enter.prevent="onTaskInquiryClick(msg)"
                        @keydown.space.prevent="onTaskInquiryClick(msg)"
                      >
                        <header class="chat-task-inquiry-head">
                          <span class="chat-task-inquiry-badge">订单咨询</span>
                          <span class="chat-task-inquiry-id">#{{ chatTaskInquiry(msg)?.taskId || '—' }}</span>
                        </header>
                        <h4 class="chat-task-inquiry-title">{{ chatTaskInquiry(msg)?.title || '任务订单' }}</h4>
                        <dl class="chat-task-inquiry-meta">
                          <div><dt>状态</dt><dd>{{ chatTaskInquiry(msg)?.status || '—' }}</dd></div>
                          <div><dt>角色</dt><dd>{{ chatTaskInquiry(msg)?.role || '—' }}</dd></div>
                          <div><dt>赏金</dt><dd>{{ chatTaskInquiry(msg)?.reward || '—' }}</dd></div>
                          <div><dt>路线</dt><dd>{{ chatTaskInquiry(msg)?.pickup || '—' }} → {{ chatTaskInquiry(msg)?.dropoff || '—' }}</dd></div>
                        </dl>
                        <footer class="chat-task-inquiry-foot">点击查看详情</footer>
                      </article>
                      <p v-else-if="chatMessageText(msg)" class="chat-inline-text">{{ chatMessageText(msg) }}</p>
                    </div>
                  </div>
                </div>
              </template>
            </template>
            <div v-else class="chat-empty-select">请选择左侧联系人开始聊天</div>
          </div>

          <footer v-if="!isSystemNoticeActive" class="chat-composer">
            <input
              ref="chatImageInputEl"
              type="file"
              accept="image/*"
              style="display:none;"
              :disabled="!r.chatTarget"
              @change="r.onChatImageSelected"
            />
            <div class="chat-composer-modern">
              <div class="chat-composer-toolbar" @click.stop>
                <div class="chat-emoji-wrap">
                  <button type="button" class="chat-tool-btn" :disabled="!r.chatTarget" title="表情" @click="toggleEmojiPanel">☺</button>
                  <div v-if="showEmojiPanel" class="chat-emoji-panel">
                    <button
                      v-for="em in EMOJIS"
                      :key="`em-${em}`"
                      type="button"
                      class="chat-emoji-item"
                      @click="insertEmoji(em)"
                    >
                      {{ em }}
                    </button>
                  </div>
                </div>
                <button type="button" class="chat-tool-btn" :disabled="!r.chatTarget" title="图片" @click="pickChatImage">
                  <span class="chat-image-btn-icon" aria-hidden="true"></span>
                </button>
                <span class="chat-char-count">{{ String(r.chatInput || '').length }} / 500</span>
              </div>
              <div class="chat-input-wrap">
                <img
                  v-if="r.chatImageDraft"
                  class="chat-input-image"
                  :src="r.chatImageDraft"
                  alt="chat-image-draft"
                  title="单击查看，双击裁剪（可用删除键移除）"
                  @click="r.openImagePreview(r.chatImageDraft)"
                  @dblclick.prevent="openChatCropper()"
                />
                <textarea
                  ref="chatInputEl"
                  v-model="r.chatInput"
                  rows="3"
                  maxlength="500"
                  placeholder="请输入消息，按Enter键 或 点击发送按钮发送"
                  :disabled="!r.chatTarget"
                  :class="{ 'chat-textarea--with-image': !!r.chatImageDraft }"
                  @keydown="onMessageKeydown"
                />
              </div>
              <div class="chat-composer-actions">
                <button type="button" class="chat-send-btn chat-send-btn-modern" :disabled="!r.chatTarget" @click="r.sendChatMessage">发送</button>
              </div>
            </div>
          </footer>
        </section>

        <div
          v-if="showChatCropper"
          class="detail-mask"
          @pointerup="onCropPointerUp"
          @pointercancel="onCropPointerUp"
          @click.self="closeChatCropper"
        >
          <section class="detail-panel chat-crop-panel">
            <div class="detail-head">
              <h3>裁剪截图</h3>
              <button class="ghost-local" type="button" @click="closeChatCropper">关闭</button>
            </div>
            <p class="meta">拖拽选择要发送的区域</p>
            <div class="chat-crop-canvas" @pointerdown.prevent="onCropPointerDown" @pointermove.prevent="onCropPointerMove">
              <img ref="cropImageEl" :src="r.chatImageDraft" alt="crop-source" draggable="false" @load="initCropBox" />
              <div
                v-if="cropBox.w > 0 && cropBox.h > 0"
                class="chat-crop-rect"
                :style="{ left: `${cropBox.x}px`, top: `${cropBox.y}px`, width: `${cropBox.w}px`, height: `${cropBox.h}px` }"
              ></div>
            </div>
            <div class="actions" style="margin-top:12px;">
              <button type="button" class="ghost-local" @click="closeChatCropper">取消</button>
              <button type="button" class="primary" @click="applyChatCrop">确认裁剪</button>
            </div>
          </section>
        </div>
      </div>
    </section>

    <teleport to="body">
      <div v-if="selectedRelatedTask" class="detail-mask chat-task-progress-mask" @click.self="closeTaskProgress">
        <section class="detail-panel chat-task-progress-panel">
          <div class="chat-task-progress-header">
            <div class="chat-task-progress-header-left">
              <p class="chat-task-progress-kicker">任务进度</p>
              <h3>{{ selectedRelatedTask.title }}</h3>
              <p class="chat-task-progress-id">#{{ selectedRelatedTask.id }}</p>
            </div>
            <button type="button" class="ghost-local chat-task-progress-close" @click="closeTaskProgress">关闭</button>
          </div>

          <div class="chat-task-progress-status-row">
            <span class="chat-task-progress-status-pill" :class="taskStatusClass(selectedRelatedTask)">{{ taskStatusLabel(selectedRelatedTask) }}</span>
            <p class="chat-task-progress-summary-text">查看任务的核心信息与当前状态，整体更清晰一点。</p>
          </div>

          <div class="chat-task-progress-grid">
            <div class="chat-task-progress-card">
              <span class="chat-task-progress-label">赏金</span>
              <strong>¥ {{ Number(selectedRelatedTask.reward).toFixed(2) }}</strong>
            </div>
            <div class="chat-task-progress-card">
              <span class="chat-task-progress-label">发布人</span>
              <strong>{{ selectedRelatedTask.publisherName }}</strong>
            </div>
            <div class="chat-task-progress-card">
              <span class="chat-task-progress-label">接单人</span>
              <strong>{{ selectedRelatedTask.claimerName || '待接单' }}</strong>
            </div>
          </div>

          <div class="chat-task-progress-timeline">
            <div class="chat-task-progress-item">
              <span class="chat-task-progress-dot"></span>
              <div class="chat-task-progress-item-content">
                <p class="chat-task-progress-label">取件地点</p>
                <p class="chat-task-progress-value">{{ selectedRelatedTask.pickupLocation }}</p>
              </div>
            </div>
            <div class="chat-task-progress-item">
              <span class="chat-task-progress-dot"></span>
              <div class="chat-task-progress-item-content">
                <p class="chat-task-progress-label">送达地点</p>
                <p class="chat-task-progress-value">{{ selectedRelatedTask.dropoffLocation }}</p>
              </div>
            </div>
            <div v-if="selectedRelatedTask.latestDeliveryAt" class="chat-task-progress-item">
              <span class="chat-task-progress-dot"></span>
              <div class="chat-task-progress-item-content">
                <p class="chat-task-progress-label">最晚送达</p>
                <p class="chat-task-progress-value">{{ String(selectedRelatedTask.latestDeliveryAt).replace('T', ' ').slice(0, 16) }}</p>
              </div>
            </div>
            <div v-if="selectedRelatedTask.description" class="chat-task-progress-item chat-task-progress-item-note">
              <span class="chat-task-progress-dot"></span>
              <div class="chat-task-progress-item-content">
                <p class="chat-task-progress-label">备注</p>
                <p class="chat-task-progress-value">{{ selectedRelatedTask.description }}</p>
              </div>
            </div>
          </div>
        </section>
      </div>
    </teleport>
  </main>
</template>

<style scoped>
.chat-shell-page {
  position: relative;
  height: calc(100vh - 88px);
  max-height: calc(100vh - 88px);
  min-height: 0;
  padding: 18px 20px 28px;
  box-sizing: border-box;
  overflow: hidden;
}

.page-top-right-avatar {
  position: fixed;
  top: 16px;
  right: 24px;
  z-index: 9999;
  display: flex;
  align-items: center;
  gap: 10px;
}

.quick-chat-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.35);
  transition: all 0.25s ease;
}

.quick-chat-btn:hover {
  transform: scale(1.08) translateY(-2px);
  box-shadow: 0 4px 14px rgba(59, 130, 246, 0.5);
}

.user-entry {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.rider-avatar-wrap {
  position: relative;
  display: inline-block;
}

.user-entry-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid #dbeafe;
  display: block;
}

.user-entry-fallback {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid #dbeafe;
  display: grid;
  place-items: center;
  background: #e0e7ff;
  color: #1e3a8a;
  font-weight: 700;
  font-size: 14px;
}

.rider-star-badge {
  position: absolute;
  bottom: -2px;
  right: -2px;
  background: #f59e0b;
  color: #fff;
  font-size: 12px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  z-index: 2;
}

.user-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 180px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.15), 0 4px 10px rgba(15, 23, 42, 0.08);
  padding: 6px 0;
  overflow: hidden;
}

.user-dropdown-item {
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  color: #374151;
  padding: 9px 16px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
  display: block;
  font-weight: 500;
}

.user-dropdown-item:hover {
  background: #f3f4f6;
}

.user-dropdown-item.danger {
  color: #dc2626;
}

.chat-shell-card {
  width: min(1280px, 100%);
  height: 100%;
  min-height: 0;
  margin: 0 auto;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(18px);
  border: 1px solid rgba(255, 255, 255, 0.72);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.08);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.chat-shell-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 26px 12px;
}

.chat-shell-title-block {
  min-width: 0;
}

.chat-back-link {
  display: inline-flex;
  align-items: center;
  margin: 0 0 8px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #9a3412;
  font-size: 13px;
  font-weight: 650;
  line-height: 1;
  cursor: pointer;
}

.chat-back-link:hover {
  color: #c2410c;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.chat-shell-header h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.1;
  color: #0f172a;
  font-weight: 900;
}

.chat-shell-body {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 8px;
  padding: 10px 12px 12px;
  flex: 1 1 auto;
  min-height: 0;
}

.chat-list-pane {
  padding: 4px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 24px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.chat-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 8px 4px;
}

.chat-list-title {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.chat-list-tools {
  display: flex;
  align-items: center;
  gap: 4px;
}

.chat-list-tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  line-height: 0;
  transition: background 0.15s, color 0.15s;
}

.chat-list-tool-btn svg {
  display: block;
  flex-shrink: 0;
}

.chat-list-tool-btn:hover {
  background: #f3f4f6;
  color: #111827;
}

.chat-contact-menu {
  position: fixed;
  z-index: 12000;
  min-width: 148px;
  padding: 6px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.14), 0 0 0 1px rgba(15, 23, 42, 0.06);
}

.chat-contact-menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #111827;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  text-align: left;
}

.chat-contact-menu-item:hover {
  background: #f3f4f6;
}

.chat-contact-menu-item.danger {
  color: #dc2626;
}

.chat-contact-menu-item.danger:hover {
  background: #fef2f2;
}

.chat-contact-menu-tip {
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.45;
  color: #64748b;
  max-width: 200px;
}

.chat-contact-list li.pinned .chat-contact-row {
  background: #f2f2f2;
}

.chat-contact-list li.pinned .chat-contact-row:hover {
  background: #e8e8e8 !important;
}

.chat-search-box {
  display: flex;
  align-items: stretch;
  gap: 8px;
  height: 36px;
  padding: 0 12px;
  margin: 0 8px 8px;
  border-radius: 999px;
  background: #f5f5f5;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;
}

.chat-search-box:focus-within {
  background: #fff;
  border-color: rgba(93, 64, 55, 0.12);
  box-shadow: 0 0 0 3px rgba(93, 64, 55, 0.06);
}

.chat-search-box input {
  flex: 1;
  min-width: 0;
  height: 100%;
  margin: 0;
  border: 0;
  border-radius: 0;
  outline: none;
  background: transparent;
  width: 100%;
  padding: 0;
  font-size: 13px;
  line-height: 36px;
  color: #111827;
  box-shadow: none;
  appearance: none;
  -webkit-appearance: none;
}

.chat-search-box input:focus {
  border: 0;
  box-shadow: none;
}

.chat-search-box input::placeholder {
  color: #9ca3af;
  line-height: 36px;
}

.chat-search-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  align-self: center;
  flex-shrink: 0;
  color: #9ca3af;
  line-height: 0;
}

.chat-search-icon svg {
  display: block;
}

.chat-contact-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0px;
  overflow-y: auto;
  overflow-x: hidden;
  min-height: 0;
  flex: 1 1 auto;
}

.chat-contact-list li { margin: 0; padding: 0; }

.chat-contact-item--system .chat-contact-row {
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
}

.chat-contact-avatar--system {
  background: linear-gradient(135deg, #f59e0b, #ea580c) !important;
  font-size: 18px;
  line-height: 1;
}

.chat-contact-item--xiaoxin .chat-contact-avatar {
  background: #fff7ed;
  object-fit: contain;
  padding: 2px;
  box-sizing: border-box;
}

.chat-contact-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px 4px 0;
  border-radius: 0;
  border: none;
  background: transparent;
  text-align: left;
}

button.chat-contact-row {
  padding: 8px 10px;
  gap: 8px;
  cursor: pointer;
  font: inherit;
  color: inherit;
}

.chat-contact-row:hover {
  background: #f3f4f6 !important;
}

.chat-contact-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 4px 8px 10px;
  border: 0;
  border-radius: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
}

.chat-contact-more-btn {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  margin-right: 4px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #94a3b8;
  display: inline-grid;
  place-items: center;
  opacity: 0;
  cursor: pointer;
}

.chat-contact-row:hover .chat-contact-more-btn,
.chat-contact-list li.active .chat-contact-more-btn,
.chat-contact-more-btn:focus-visible {
  opacity: 1;
}

.chat-contact-more-btn:hover {
  background: #e5e7eb;
  color: #334155;
}

.chat-contact-avatar-wrap,
.chat-message-avatar-wrap,
.chat-profile-avatar-wrap {
  position: relative;
  flex: 0 0 auto;
}

.chat-contact-avatar,
.chat-message-avatar,
.chat-profile-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
  background: linear-gradient(135deg, #2563eb, #8b5cf6);
  color: #fff;
}

.chat-contact-avatar-fallback,
.chat-message-avatar-fallback,
.chat-profile-avatar-fallback {
  display: grid;
  place-items: center;
  font-weight: 800;
}

.chat-contact-avatar--brand,
.chat-profile-avatar--brand,
.chat-message-avatar--brand {
  object-fit: contain;
  background: #fff;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.06);
}

.chat-contact-meta { min-width: 0; flex: 1; }
.chat-contact-name-row { display:flex; align-items:center; justify-content:space-between; gap:8px; }
.chat-contact-name { font-weight: 600; color:#111827; font-size: 14px; line-height: 1.2; }
.chat-contact-preview { margin: 2px 0 0; color:#6b7280; font-size: 12px; line-height: 1.2; }
.chat-contact-unread {
  min-width: 20px; height: 20px; padding: 0 6px; border-radius: 999px; background:#ef4444; color:#fff; font-size:11px; display:inline-grid; place-items:center;
}
.chat-empty-contact { color:#64748b; padding: 14px 8px; }

.chat-main-pane,
.chat-profile-card {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 24px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04);
}

.chat-main-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  min-width: 0;
}

.chat-main-topbar {
  padding: 12px 14px 8px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  display:flex;
  align-items:flex-start;
  justify-content:space-between;
  gap: 16px;
  flex: 0 0 auto;
}

.chat-main-title h2 { margin: 0; font-size: 24px; color:#0f172a; }
.chat-main-title p { margin: 4px 0 0; color:#64748b; font-size: 12px; }
.chat-main-tools { display:flex; gap:10px; flex-wrap: wrap; }

.chat-message-panel {
  flex: 1 1 auto;
  min-height: 0;
  max-height: none;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px 12px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  scroll-behavior: auto;
}

.chat-message-panel::-webkit-scrollbar {
  width: 8px;
}

.chat-message-panel::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.5);
  border-radius: 999px;
}

.chat-message-panel::-webkit-scrollbar-track {
  background: rgba(241, 245, 249, 0.85);
}

.chat-empty-state,
.chat-empty-select,
.chat-profile-empty {
  min-height: 280px;
  display:grid;
  place-items:center;
  color:#94a3b8;
}

.chat-notice-card {
  margin-bottom: 14px;
  padding: 14px 16px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.chat-notice-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.chat-notice-card-head h3 {
  margin: 0;
  font-size: 16px;
  line-height: 1.4;
  color: #0f172a;
}

.chat-notice-pin {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 999px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 11px;
  font-weight: 700;
}

.chat-notice-category {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

.chat-notice-card--unread {
  border-color: #bfdbfe;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.08);
}

.chat-notice-card-time {
  margin: 6px 0 10px;
  color: #94a3b8;
  font-size: 12px;
}

.chat-notice-card-body {
  color: #334155;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-date-divider {
  text-align: center;
  margin: 16px 0 20px;
  color: #a8a29e;
  font-size: 12px;
}

.chat-date-divider::before,
.chat-date-divider::after {
  content: '';
  display: inline-block;
  width: 40px;
  height: 1px;
  background: #d6d3d1;
  vertical-align: middle;
  margin: 0 10px;
}

.chat-message-row--system {
  justify-content: center;
  margin: 12px auto 16px;
}

.chat-system-chip {
  padding: 6px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid #e7e5e4;
  color: #78716c;
  font-size: 12px;
  line-height: 1.5;
  max-width: 90%;
  text-align: center;
}

.chat-message-row {
  display:flex;
  gap: 10px;
  margin-bottom: 12px;
  align-items:flex-end;
}
.chat-message-row--image {
  align-items: flex-start;
}

.chat-message-row.me { flex-direction: row-reverse; }
.chat-message-body { max-width: min(82%, 640px); }
.chat-message-meta { display:flex; align-items:center; gap:10px; margin-bottom: 6px; color:#64748b; font-size:12px; }
.chat-message-row.me .chat-message-meta { justify-content:flex-end; }
.chat-message-name { color:#0f172a; font-weight:700; }
.chat-bubble {
  padding: 12px 14px;
  border-radius: 18px;
  background:#fff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 10px 20px rgba(15,23,42,.04);
  line-height:1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.chat-inline-image {
  display: block;
  width: auto;
  max-width: min(180px, 42vw);
  max-height: 220px;
  border-radius: 10px;
  cursor: zoom-in;
  margin-bottom: 6px;
  object-fit: cover;
}
.chat-inline-text {
  margin: 0;
}
.chat-message-row.me .chat-bubble { background: linear-gradient(180deg, #dbeafe, #eff6ff); border-color:#bfdbfe; }
.chat-bubble--task-inquiry {
  padding: 0;
  background: #fff !important;
  border: 1px solid #fde68a;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 8px 20px rgba(251, 191, 36, 0.14);
  white-space: normal;
}
.chat-message-row.me .chat-bubble--task-inquiry {
  background: #fff !important;
  border-color: #fde68a;
}

.chat-composer {
  flex: 0 0 auto;
  padding: 8px 10px 10px;
  border-top: 1px solid rgba(226, 232, 240, 0.9);
  background: #fff;
}
.chat-composer-modern {
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #fff;
  padding: 8px 10px 10px;
}
.chat-composer-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.chat-emoji-wrap {
  position: relative;
  display: inline-flex;
}
.chat-emoji-panel {
  position: absolute;
  bottom: calc(100% + 10px);
  left: 0;
  z-index: 30;
  width: min(640px, calc(100vw - 40px));
  max-height: 240px;
  overflow-y: auto;
  padding: 14px 14px 12px;
  border-radius: 12px;
  border: 1px solid rgba(209, 213, 219, 0.9);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 55px rgba(15, 23, 42, 0.18);
  display: grid;
  grid-template-columns: repeat(10, minmax(0, 1fr));
  gap: 10px;
}
.chat-emoji-item {
  width: 100%;
  aspect-ratio: 1 / 1;
  border: none;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  font-size: 28px;
  line-height: 1;
  display: grid;
  place-items: center;
}
.chat-emoji-item:hover {
  background: #f3f4f6;
}
.chat-emoji-panel::-webkit-scrollbar {
  width: 10px;
}
.chat-emoji-panel::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.55);
  border-radius: 999px;
  border: 2px solid rgba(255, 255, 255, 0.9);
}
.chat-emoji-panel::-webkit-scrollbar-track {
  background: rgba(241, 245, 249, 0.8);
  border-radius: 999px;
}
.chat-tool-btn {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: #111827;
  font-size: 22px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
}
.chat-tool-btn:hover {
  background: #f3f4f6;
}
.chat-char-count {
  margin-left: auto;
  color: #9ca3af;
  font-size: 12px;
}
.chat-composer textarea {
  width: 100%;
  resize: none;
  border-radius: 10px;
  border: none;
  padding: 2px 4px 0;
  outline: none;
  font: inherit;
  min-height: 50px;
  box-shadow: none;
}
.chat-input-wrap {
  position: relative;
}
.chat-input-image {
  position: absolute;
  top: 6px;
  left: 6px;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid #e5e7eb;
  cursor: zoom-in;
}
.chat-textarea--with-image {
  padding-left: 58px !important;
  padding-top: 8px;
}
.chat-composer-actions {
  display:flex;
  align-items:center;
  justify-content:flex-end;
  margin-top: 6px;
}
.chat-send-btn { padding: 10px 18px; border-radius: 999px; }
.chat-send-btn-modern {
  background: #3b82f6;
  border: none;
  color: #fff;
  min-width: 72px;
  font-weight: 700;
}
.chat-send-btn-modern:hover { background: #2563eb; }
.chat-tool-btn:disabled,
.chat-send-btn:disabled,
.chat-composer textarea:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.chat-image-btn-icon {
  width: 16px;
  height: 16px;
  display: inline-block;
  background-repeat: no-repeat;
  background-position: center;
  background-size: contain;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%236b7280' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round'%3E%3Crect x='3' y='4' width='18' height='16' rx='2' ry='2'/%3E%3Ccircle cx='9' cy='10' r='1.8'/%3E%3Cpath d='M21 16l-5.4-5.4a1 1 0 0 0-1.4 0L7 18'/%3E%3C/svg%3E");
}
.chat-crop-panel {
  width: min(760px, calc(100vw - 20px));
}
.chat-crop-canvas {
  position: relative;
  user-select: none;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
  background: #f8fafc;
}
.chat-crop-canvas img {
  display: block;
  width: 100%;
  max-height: 66vh;
  object-fit: contain;
}
.chat-crop-rect {
  position: absolute;
  border: 2px solid #3b82f6;
  background: rgba(59, 130, 246, 0.18);
  pointer-events: none;
}

@media (max-width: 900px) {
  .chat-main-pane {
    min-height: 0;
  }

  .chat-message-panel {
    max-height: calc(100vh - 420px);
  }
}

.chat-profile-pane { min-height: 0; }
.chat-profile-card { padding: 10px; height: 100%; min-height: 0; display:flex; flex-direction:column; overflow:hidden; }
.chat-profile-kicker { margin: 0 0 6px; color:#64748b; font-size: 12px; font-weight:700; }

.chat-profile-info-row {
  display:flex;
  align-items:center;
  gap: 10px;
  margin-bottom: 6px;
}

.chat-profile-avatar-wrap {
  width: fit-content;
  flex-shrink: 0;
  margin-bottom: 0;
}

.chat-profile-avatar { width: 48px; height: 48px; }

.chat-profile-text-info { min-width: 0; flex:1; }
.chat-profile-card h3 { margin: 0; font-size: 15px; line-height: 1.2; }
.chat-profile-desc { margin: 2px 0 0; color:#64748b; font-size: 11px; line-height: 1.2; }

.chat-profile-btn {
  width: auto;
  margin-bottom: 8px;
  padding: 5px 14px;
  font-size: 12px;
  border-radius: 20px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.25);
}

.chat-profile-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.35);
}
.chat-related-orders { min-height: 0; flex: 1 1 auto; display:flex; flex-direction:column; }
.chat-related-head { display:flex; align-items:center; justify-content:space-between; margin: 4px 0 5px; font-size: 13px; font-weight: 700; }
.chat-related-head strong { font-size: 14px; }
.chat-related-list { display:grid; gap: 5px; overflow-y:auto; overflow-x:hidden; min-height: 0; flex: 1 1 auto; padding-right: 2px; max-height: none; }
.chat-related-item { padding: 7px 9px; border-radius: 10px; background:#f8fafc; border:1px solid #e2e8f0; }
.chat-related-item-clickable { cursor:pointer; }
.chat-related-item-top { display:flex; justify-content:space-between; gap:8px; align-items:flex-start; }
.chat-related-item-title { font-weight:700; color:#0f172a; font-size: 13px; }
.status-compact { font-size: 10px !important; padding: 2px 6px !important; border-radius: 6px !important; }
.chat-related-item-meta,
.chat-related-item-hint { margin: 4px 0 0; color:#64748b; font-size: 11px; line-height: 1.3; }
.chat-related-tip { margin: 0 0 8px; color:#64748b; font-size: 11px; line-height: 1.45; }
.chat-related-item-actions { margin-top: 8px; display:flex; justify-content:flex-end; }
.chat-send-order-btn {
  border: none;
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(180deg, #5d4037, #4e342e);
  cursor: pointer;
}
.chat-send-order-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.chat-task-inquiry-card {
  min-width: min(100%, 280px);
  padding: 12px 14px;
  background: linear-gradient(180deg, #fffbeb 0%, #fff 55%);
}
.chat-task-inquiry-card--clickable {
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}
.chat-task-inquiry-card--clickable:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 24px rgba(245, 158, 11, 0.16);
}
.chat-task-inquiry-card--clickable:active {
  transform: translateY(0);
}
.chat-task-inquiry-foot {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed #fde68a;
  font-size: 11px;
  font-weight: 700;
  color: #b45309;
  text-align: right;
}
.chat-task-progress-mask {
  z-index: 10050;
}
.chat-task-inquiry-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}
.chat-task-inquiry-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #92400e;
  background: #fef3c7;
  border: 1px solid #fde68a;
}
.chat-task-inquiry-id {
  font-size: 11px;
  font-weight: 700;
  color: #b45309;
}
.chat-task-inquiry-title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.35;
}
.chat-task-inquiry-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 10px;
  margin: 0;
}
.chat-task-inquiry-meta div {
  display: grid;
  gap: 2px;
}
.chat-task-inquiry-meta div:last-child {
  grid-column: 1 / -1;
}
.chat-task-inquiry-meta dt {
  margin: 0;
  font-size: 10px;
  color: #94a3b8;
}
.chat-task-inquiry-meta dd {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  line-height: 1.4;
}
.chat-related-empty { color:#94a3b8; padding: 8px 2px; font-size: 12px; }

.chat-task-progress-panel {
  width: min(680px, calc(100vw - 24px));
  max-height: min(80vh, 720px);
  overflow: auto;
  padding: 0;
  border-radius: 22px;
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.12), transparent 28%),
    linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  border: 1px solid rgba(59, 130, 246, 0.12);
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.18);
}

.chat-task-progress-header {
  display:flex; justify-content:space-between; gap:14px; align-items:flex-start; padding: 18px 20px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}
.chat-task-progress-header-left h3 { margin: 0; font-size: 26px; line-height:1.1; color:#0f172a; font-weight:900; }
.chat-task-progress-id { margin: 4px 0 0; color:#94a3b8; font-size:12px; }
.chat-task-progress-kicker, .chat-task-progress-summary-text { margin:0; color:#64748b; font-size:12px; }
.chat-task-progress-status-row { display:flex; align-items:center; justify-content:space-between; gap:14px; padding: 14px 20px 8px; }
.chat-task-progress-status-pill { display:inline-flex; align-items:center; justify-content:center; min-width:84px; padding:8px 14px; border-radius:999px; font-weight:700; font-size:13px; }
.chat-task-progress-grid { display:grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; padding: 6px 20px 0; }
.chat-task-progress-card { position:relative; overflow:hidden; padding: 14px 14px 12px 16px; border-radius:18px; background:#fff; border:1px solid #e2e8f0; }
.chat-task-progress-card::before { content:''; position:absolute; left:0; top:0; bottom:0; width:4px; background: linear-gradient(180deg, #2563eb, #7c3aed); }
.chat-task-progress-label { display:block; margin-bottom: 5px; color:#64748b; font-size: 11px; font-weight: 700; }
.chat-task-progress-card strong { font-size: 15px; }
.chat-task-progress-timeline { display:grid; gap: 8px; padding: 14px 20px 20px; }
.chat-task-progress-item { padding: 12px 14px; border-radius:14px; background:#f8fafc; border:1px solid #e2e8f0; display:flex; gap:10px; }
.chat-task-progress-dot { width:8px; height:8px; border-radius:50%; background: linear-gradient(135deg, #2563eb, #7c3aed); margin-top: 5px; flex:0 0 auto; }
.chat-task-progress-value { margin: 2px 0 0; font-size:13px; line-height:1.4; }

@media (max-width: 1200px) {
  .chat-shell-body { grid-template-columns: 220px minmax(0, 1fr); }
}

@media (max-width: 900px) {
  .chat-shell-page { padding: 12px; }
  .chat-shell-header { padding: 18px 16px 10px; }
  .chat-shell-body { grid-template-columns: 1fr; gap: 12px; }
  .chat-list-pane,
  .chat-main-pane { min-height: auto; }
  .chat-message-body { max-width: 86%; }
  .chat-related-list { max-height: none; }
}

@media (max-width: 720px) {
  .chat-task-progress-panel { width: min(100vw - 16px, 720px); border-radius: 22px; }
  .chat-task-progress-header,
  .chat-task-progress-status-row,
  .chat-task-progress-grid,
  .chat-task-progress-timeline { padding-left: 16px; padding-right: 16px; }
  .chat-task-progress-header { flex-direction: column; padding-top: 18px; padding-bottom: 14px; }
  .chat-task-progress-header-left h3 { font-size: 26px; }
  .chat-task-progress-status-row { flex-direction: column; align-items:flex-start; }
  .chat-task-progress-grid { grid-template-columns: 1fr; }
}
</style>