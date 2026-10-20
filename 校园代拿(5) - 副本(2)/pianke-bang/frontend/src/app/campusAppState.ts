import { computed, markRaw, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import type {
  MeResponse,
  NoticeItem,
  SystemMessageListResponse,
  UserSystemMessageItem,
  TaskItem,
  TaskPageResponse,
  UserPublicProfile,
  WalletTransactionItem
} from '../types/app'
import { MINIPROGRAM_PAGE_OPTIONS } from '../constants/miniprogramPages'
import {
  isSystemNoticeContact,
  SYSTEM_NOTICE_CONTACT,
  SYSTEM_NOTICE_USERNAME
} from '../constants/systemNotice'
import { apiBase, apiOrigin, authBase } from '../constants/api'
import {
  createAuthenticatedRequest,
  fetchAuthMeWithRetry,
  isAuthDisabledMessage,
  isAuthKickedElsewhereMessage,
  isHardAuthFailure,
  isTransientUnauthorizedMessage
} from '../utils/http'
import { isAwaitingBountyPayment } from '../utils/taskStatus'
import { clearToken, getToken, isTokenExpired, parseTokenPayload, setToken } from '../utils/token'
import { createDomainStores } from './domains/createDomainStores'
import { createRiderApplyDomain } from './domains/createRiderApplyDomain'
import { createFeedbackDomain } from './domains/createFeedbackDomain'
import { createCouponsDomain } from './domains/createCouponsDomain'
import { createPersonalInfoDomain } from './domains/createPersonalInfoDomain'
import { createWalletDomain } from './domains/createWalletDomain'
import { createAuthDomain } from './domains/createAuthDomain'
import { createShellDomain } from './domains/createShellDomain'
import { createProfileDomain } from './domains/createProfileDomain'
import { createHallDomain } from './domains/createHallDomain'
import { createPublishDomain } from './domains/createPublishDomain'
import { createSupportDomain } from './domains/createSupportDomain'
import { createChatDomain } from './domains/createChatDomain'
import { createAdminDomain } from './domains/createAdminDomain'
import { createTaskActionsDomain } from './domains/createTaskActionsDomain'
import { createRiderStatsDomain } from './domains/createRiderStatsDomain'
import { buildLatestDeliveryAtByMinutes } from '../utils/datetimeLocal'
import {
  formatChatDateDivider,
  formatChatTime,
  formatDateOnly,
  formatDeadlineCountdown as formatDeadlineCountdownAt,
  formatNoticeChatTime,
  formatRating,
  formatTaskStatusNoticeMessage,
  maskContact,
  maskName,
  starFillState,
  walletTypeLabel,
  withdrawStatusClass,
  withdrawStatusLabel
} from '../utils/displayFormat'
import { heroTaskCardTitle } from '../utils/hallDisplay'
import {
  usernameMatches
} from '../utils/taskParse'
import { randomCaptchaText } from '../utils/captchaDraw'
import { chatReadStoreKey } from '../utils/chatStoreKeys'

/**
 * 页面用 v-if 切换，组件会卸载。状态放在这里做成单例，
 * 各页面通过 stores/use*Store 取自己的那一份，而不是注入整棵根状态。
 */
export function createCampusApp() {
void buildLatestDeliveryAtByMinutes
void heroTaskCardTitle
const tasks = ref<TaskItem[]>([])
const allTasks = ref<TaskItem[]>([])
/** 当前用户任务（/mine + 待支付草稿），个人中心优先使用，避免被全局分页列表漏掉 */
const mineTasks = ref<TaskItem[]>([])
const publicNotices = ref<NoticeItem[]>([])
const publicNoticesLoading = ref(false)
const userSystemMessages = ref<UserSystemMessageItem[]>([])
const userSystemMessagesUnread = ref(0)
const userSystemMessagesLoading = ref(false)
const realtimeConnected = ref(false)
let realtimeClient: { deactivate: () => void | Promise<void>; activate: () => void } | null = null
let realtimeSubscribed = false

function stopRealtime() {
  realtimeSubscribed = false
  realtimeConnected.value = false
  if (realtimeClient) {
    try {
      realtimeClient.deactivate()
    } catch {
      // ignore
    }
    realtimeClient = null
  }
}

function handleRealtimeEvent(raw: string) {
  if (!raw) return
  let evt: any
  try {
    evt = JSON.parse(raw)
  } catch {
    return
  }
  const type = String(evt?.type || '').trim()
  const data = evt?.data
  if (!type) return

  if (type === 'CHAT_MESSAGE') {
    const from = String(data?.fromUsername || '').trim()
    const to = String(data?.toUsername || '').trim()
    if (!from || !to) return
    const msgId = Number(data?.id || 0)
    const content = String(data?.content || '')

    if (from === myName.value) {
      chatSideEffects.handleOutgoingEcho(to)
      return
    }

    const other = from

    // 客服消息：接入智能客服窗，不进通讯录未读
    if (supportSideEffects.handleHumanAgentChat(other, content, msgId)) {
      chatSideEffects.noteSupportIncoming(other, msgId)
      return
    }

    chatSideEffects.handleIncomingChatMessage(other, msgId)
    return
  }

  if (type === 'SYSTEM_MESSAGE') {
    void fetchUserSystemMessages()
    chatSideEffects.onViewingSystemNotice()
    return
  }

  if (type === 'TASK_STATUS') {
    const taskId = Number(data?.taskId || 0)
    const title = String(data?.title || '').trim()
    const status = String(data?.status || '').trim()
    const event = String(data?.event || '').trim()
    const message = formatTaskStatusNoticeMessage({ title, status, event })
    const toastTitle = taskStatusToastTitle(event, status)
    if (taskId > 0) {
      taskStatusSnapshot.value = { ...taskStatusSnapshot.value, [taskId]: status }
      taskStatusNotifyReady = true
      emitTaskStatusNotice(taskId, status, message, toastTitle)
    } else {
      showRealtimeToast({
        title: toastTitle,
        message,
        kind: 'task'
      })
    }
    void hallSideEffects.fetchTasks()
    return
  }
}

async function startRealtime() {
  if (!isLoggedIn.value) return
  const token = getToken()
  if (!token) return

  stopRealtime()
  const [{ Client }, sockMod] = await Promise.all([
    import('@stomp/stompjs'),
    import('sockjs-client/dist/sockjs')
  ])
  const SockJS = (sockMod as { default: new (url: string) => WebSocket }).default
  const endpoint = `${apiOrigin || ''}/ws?token=${encodeURIComponent(token)}`
  const client = new Client({
    webSocketFactory: () => new SockJS(endpoint),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {}
  })

  client.onConnect = () => {
    realtimeConnected.value = true
    // 有 WS 就不需要轮询了；断开时再恢复轮询
    stopChatPolling()
    if (!realtimeSubscribed) {
      realtimeSubscribed = true
      client.subscribe('/user/queue/events', (msg) => handleRealtimeEvent(String(msg.body || '')))
    }
  }

  client.onWebSocketClose = () => {
    realtimeConnected.value = false
    realtimeSubscribed = false
    if (isLoggedIn.value) {
      startChatPolling()
    }
  }

  client.onStompError = () => {
    // 服务器 STOMP 报错：先降级为轮询
    realtimeConnected.value = false
    realtimeSubscribed = false
    if (isLoggedIn.value) {
      startChatPolling()
    }
  }

  realtimeClient = client
  client.activate()
}
const loading = ref(false)
const submitting = ref(false)
const actionLoadingMap = ref<Record<number, boolean>>({})
const errorMsg = ref('')
const myName = ref('')
const myAvatarUrl = ref('')
const myAvatarPending = ref(false)
const isRiderApproved = ref(false)
const riderApplicationPending = ref(false)
const creditScore = ref(80)
const blockedUntil = ref('')
const pendingPenalty = ref(0)
/** 平台管理员（来自 /me.platformAdmin，库内角色） */
const platformAdmin = ref(false)


const profileForm = ref({
  username: '',
  avatarUrl: ''
})


const adminModule = ref<
  | 'task'
  | 'taskOverview'
  | 'user'
  | 'audit'
  | 'appealAudit'
  | 'riderOverview'
  | 'riderRank'
  | 'riderCompare'
  | 'notice'
  | 'logs'
  | 'feedback'
  | 'csRatings'
  | 'walletAudit'
  | 'walletCredit'
  | 'coupons'
  | 'aiModel'
  | 'customerService'
>('task')
const auditSubModule = ref<'task' | 'avatar' | 'rider'>('task')

const previewImageUrl = ref('')
const profileNotice = ref('')
let profileNoticeTimer: number | null = null
const taskStatusSnapshot = ref<Record<number, string>>({})
const lastNotifiedTaskStatus = ref<Record<number, string>>({})
let taskStatusNotifyReady = false
let taskStatusPollTimer: number | null = null
const realtimeToast = ref<{
  title: string
  message: string
  kind: 'chat' | 'task'
  chatUser?: string
} | null>(null)
let realtimeToastTimer: number | null = null

function closeRealtimeToast() {
  realtimeToast.value = null
  if (realtimeToastTimer) {
    window.clearTimeout(realtimeToastTimer)
    realtimeToastTimer = null
  }
}

function showRealtimeToast(payload: {
  title: string
  message: string
  kind: 'chat' | 'task'
  chatUser?: string
}) {
  if (realtimeToastTimer) {
    window.clearTimeout(realtimeToastTimer)
    realtimeToastTimer = null
  }
  realtimeToast.value = payload
  realtimeToastTimer = window.setTimeout(() => {
    realtimeToast.value = null
    realtimeToastTimer = null
  }, 9000)
}

async function openRealtimeToastDetail() {
  const toast = realtimeToast.value
  if (!toast) return
  if (toast.kind === 'chat' && toast.chatUser) {
    await goChat()
    await openChatWith(toast.chatUser)
  } else if (toast.kind === 'task') {
    await hallSideEffects.fetchTasks()
    if (currentView.value !== 'profile' && currentView.value !== 'hall' && currentView.value !== 'admin') {
      setView('hall')
    }
  }
  closeRealtimeToast()
}

function taskStatusToastTitle(event?: string, status?: string) {
  const evt = String(event || '').toUpperCase()
  const st = String(status || '').toUpperCase()
  if (evt === 'TASK_APPROVED') return '任务审核通过'
  if (evt === 'TASK_REJECTED' || st === 'REJECTED') return '任务审核未通过'
  return '任务状态更新'
}

function emitTaskStatusNotice(taskId: number, status: string, message: string, toastTitle: string) {
  if (!message.trim()) return
  if (lastNotifiedTaskStatus.value[taskId] === status) return
  lastNotifiedTaskStatus.value = { ...lastNotifiedTaskStatus.value, [taskId]: status }
  showRealtimeToast({
    title: toastTitle,
    message,
    kind: 'task'
  })
}

function isMyTaskRole(task: TaskItem) {
  const me = myName.value.trim()
  if (!me) return false
  return task.publisherName === me || task.claimerName === me
}

function detectTaskStatusChanges() {
  if (!isLoggedIn.value) return

  const nextSnapshot: Record<number, string> = {}
  for (const task of allTasks.value) {
    if (!task?.id) continue
    const status = String(task.status || '')
    nextSnapshot[task.id] = status

    if (!taskStatusNotifyReady || !isMyTaskRole(task)) continue

    const prevStatus = taskStatusSnapshot.value[task.id]
    if (!prevStatus || prevStatus === status) continue

    let event = ''
    if (prevStatus === 'PENDING_REVIEW' && status === 'OPEN') event = 'TASK_APPROVED'
    else if (prevStatus === 'PENDING_REVIEW' && status === 'REJECTED') event = 'TASK_REJECTED'
    else if (prevStatus === 'OPEN' && status === 'CLAIMED') event = 'TASK_CLAIMED'
    else if (status === 'PENDING_CONFIRMATION') event = 'TASK_PENDING_CONFIRMATION'
    else if (status === 'COMPLETED') event = 'TASK_COMPLETED'

    const message = formatTaskStatusNoticeMessage({ title: task.title, status, event })
    emitTaskStatusNotice(task.id, status, message, taskStatusToastTitle(event, status))
  }

  taskStatusSnapshot.value = nextSnapshot
  taskStatusNotifyReady = true
}

function resetTaskStatusTracking() {
  taskStatusSnapshot.value = {}
  lastNotifiedTaskStatus.value = {}
  taskStatusNotifyReady = false
}

function startTaskStatusPolling() {
  if (taskStatusPollTimer) {
    window.clearInterval(taskStatusPollTimer)
  }
  // 有实时通道时不必高频全量拉任务；后台标签页也跳过，减轻卡顿
  taskStatusPollTimer = window.setInterval(() => {
    if (!isLoggedIn.value) return
    if (typeof document !== 'undefined' && document.hidden) return
    if (realtimeConnected.value) return
    void fetchAllTasksForProfile()
  }, 60000)
}

function stopTaskStatusPolling() {
  if (taskStatusPollTimer) {
    window.clearInterval(taskStatusPollTimer)
    taskStatusPollTimer = null
  }
}


const riderStatsSideEffects = {
  resizeRiderCharts: (() => {}) as () => void,
  disposeRiderCharts: (() => {}) as () => void
}

const onWindowResize = () => {
  riderStatsSideEffects.resizeRiderCharts()
  refreshLoginRainIfNeeded()
  updateNoticeMarquee()
}

const isLoggedIn = ref(false)
/** 页面刷新恢复登录态期间，避免并行 401 请求把用户误踢下线 */
const authBootstrapping = ref(false)
const isAdmin = computed(() => platformAdmin.value)


const taskActionsSideEffects = {
  compressImageForChat: (async (_file: File) => {
    throw new Error('compressImageForChat not ready')
  }) as (file: File) => Promise<string>
}

const shellSideEffects = {
  fetchMyTasksForProfile: (async () => {}) as () => void | Promise<void>,
  loadAdminDashboardData: (() => {}) as () => void
}

const {
  currentView,
  showUserDropdown,
  navGroupOpen,
  setView,
  goHall,
  goProfile,
  goAdmin,
  toggleNavGroup,
  parseViewFromHash
} = createShellDomain({
  isLoggedIn,
  isAdmin,
  fetchMyTasksForProfile: () => shellSideEffects.fetchMyTasksForProfile(),
  loadAdminDashboardData: () => shellSideEffects.loadAdminDashboardData()
})

const hallSideEffects = {
  request: (async () => { throw new Error('hall request not ready') }) as <T>(url: string, init?: RequestInit) => Promise<T>,
  openAuthPage: ((_mode: 'login' | 'register') => {}) as (mode: 'login' | 'register') => void,
  showProfileNotice: ((_t: string) => {}) as (text: string) => void,
  goWallet: (async () => {}) as () => void | Promise<void>,
  goCoupons: (async () => {}) as () => void | Promise<void>,
  fetchTasks: (async () => {}) as () => void | Promise<void>
}

const {
  hallNoticeExpanded,
  hallNoticeActiveId,
  noticeBarVisible,
  noticeTrackRef,
  noticeMeasureRef,
  noticeMarqueeActive,
  hallNoticeCarouselIndex,
  hasMultipleHallNotices,
  hallNoticeBannerText,
  activeHallNoticeId,
  hallTopNotice,
  updateNoticeMarquee,
  pauseNoticeCarousel,
  resumeNoticeCarousel,
  goToHallNotice,
  toggleHallNoticeExpanded,
  activateHallNotice,
  openActiveHallNoticeLink,
  onPublicNoticesLoaded,
  setupNoticeMarqueeObserver,
  disposeHallNoticeUi,
  promoActivities,
  heroPromoIndex,
  claimingHeroPromoId,
  heroPromoSlides,
  activeHeroPromo,
  hasMultipleHeroPromos,
  pauseHeroPromoCarousel,
  resumeHeroPromoCarousel,
  goToHeroPromo,
  heroPromoCtaText,
  fetchPromoActivities,
  claimPromoActivity,
  claimHeroPromo,
  claimNewcomerPack,
  hallZones,
  filters,
  paging,
  visibleTasks,
  hallStats,
  taskTypeOptions,
  hotTaskTypes,
  selectZoneFilter,
  selectSortMode,
  selectTaskTypeFilter,
  applyFilters,
  resetFilters,
  goPrevPage,
  goNextPage,
  heroCategoryTags,
  selectHeroCategoryTag,
  applyHeroSearch,
  scrollToHallTaskList,
  heroSideStats,
  fetchHallCompletionStats,
  startCampusWeatherPolling,
  refreshCampusWeather,
  hallFaqItems,
  hallActivities,
  hallLeaderboard,
  publisherAvatarUrl,
  publisherNameInitial,
  fetchTasks,
  hallStatusLabel,
  hallTaskCategoryLabel,
  hallTaskDistance,
  hallTaskEmoji,
  formatTaskTimeAgo,
  taskTypeLabel
} = createHallDomain({
  publicNotices,
  currentView,
  isLoggedIn,
  setView,
  tasks,
  allTasks,
  loading,
  errorMsg,
  myName,
  myAvatarUrl,
  request: (url, init) => hallSideEffects.request(url, init),
  openAuthPage: (mode) => hallSideEffects.openAuthPage(mode),
  showProfileNotice: (text) => hallSideEffects.showProfileNotice(text),
  goWallet: () => hallSideEffects.goWallet(),
  goCoupons: () => hallSideEffects.goCoupons()
})

hallSideEffects.fetchTasks = fetchTasks

const publishSideEffects = {
  request: (async () => { throw new Error('publish request not ready') }) as <T>(url: string, init?: RequestInit) => Promise<T>,
  fetchWalletBalance: (async () => {}) as () => void | Promise<void>,
  walletBalance: (() => 0) as () => number,
  contactPhone: (() => '') as () => string,
  fetchUrgentQuota: (async () => {}) as () => void | Promise<void>
}

const {
  publishFormHint,
  setPublishFormHint,
  clearPublishFormHintTimer,
  urgentEnabled,
  urgentFee,
  urgentFeePresets,
  urgentRemaining,
  urgentWeeklyLimit,
  urgentFeeValid,
  urgentFeeError,
  urgentRiderBonus,
  urgentPushFee,
  scrollPublishValidationIntoView,
  togglePublishUrgent,
  pickUrgentFee,
  form,
  selectedDeliveryDate,
  latestDeliveryQuickOption,
  deliveryDateOptions,
  deliveryQuickOptions,
  estimateLatestDeliveryText,
  selectedDeliverySummary,
  showDeliveryPicker,
  setSelectedDeliveryDate,
  setLatestDeliveryQuickOption,
  openDeliveryPicker,
  closeDeliveryPicker,
  syncDeliverySelectionFromLatestDeliveryAt,
  pickupBuilding,
  pickupRoom,
  pickupManualInput,
  showPickupCascader,
  pickupCascaderRef,
  dropoffBuilding,
  dropoffRoom,
  dropoffManualInput,
  showDropoffCascader,
  dropoffCascaderRef,
  publishImageInputRef,
  isClassroomBuilding,
  roomOptions,
  dropoffRoomOptions,
  pickupDisplayText,
  isDropoffClassroomBuilding,
  dropoffDisplayText,
  focusPickupCascader,
  focusDropoffCascader,
  onPublishImagesSelected,
  removePublishImage,
  setPickupCascaderRootEl,
  setDropoffCascaderRootEl,
  pickupSelectedText,
  dropoffSelectedText,
  buildingOptions,
  publishPaymentMethod,
  showPublishPaymentSheet,
  publishPaymentSheetMode,
  publishPayCoupons,
  publishSelectedCouponId,
  publishCouponDiscount,
  publishCouponLoading,
  publishPayableTotal,
  publishWalletPayDisabled,
  buildPublishTaskBody,
  publishCouponOptionLabel,
  onPublishCouponSelected,
  validatePublishFormBeforePay,
  openPublishPaymentSheet,
  closePublishPaymentSheet,
  resetPublishFormAfterSuccess
} = createPublishDomain({
  isLoggedIn,
  myName,
  submitting,
  request: (url, init) => publishSideEffects.request(url, init),
  fetchWalletBalance: () => publishSideEffects.fetchWalletBalance(),
  walletBalance: () => publishSideEffects.walletBalance(),
  contactPhone: () => publishSideEffects.contactPhone(),
  fetchUrgentQuota: () => publishSideEffects.fetchUrgentQuota()
})

async function fetchUrgentQuota() {
  if (!isLoggedIn.value) return
  try {
    const q = await request<{ remaining?: number; weeklyLimit?: number }>(
      `${apiOrigin}/api/tasks/urgent-quota`
    )
    const remaining = Number(q?.remaining)
    const weeklyLimit = Number(q?.weeklyLimit)
    if (Number.isFinite(remaining)) urgentRemaining.value = remaining
    if (Number.isFinite(weeklyLimit) && weeklyLimit > 0) urgentWeeklyLimit.value = weeklyLimit
    if (Number.isFinite(remaining) && remaining <= 0 && urgentEnabled.value) {
      urgentEnabled.value = false
    }
  } catch {
    // 配额失败不阻断发布，提交时后端会再校验
  }
}




const kickedDialogOpen = ref(false)
const kickedDialogText = ref('你的账号在别处登录，请重新登录')

function openKickedDialog(text?: string) {
  kickedDialogText.value = text && String(text).trim() ? String(text).trim() : '你的账号在别处登录，请重新登录'
  kickedDialogOpen.value = true
}

function closeKickedDialog() {
  kickedDialogOpen.value = false
}

function goLoginFromKickedDialog() {
  kickedDialogOpen.value = false
  openAuthPage('login')
}

const selectedTask = ref<TaskItem | null>(null)
const historyNowTs = ref(Date.now())
const selectedPublisherProfile = ref<UserPublicProfile | null>(null)
const publisherProfileLoading = ref(false)

let historyTickTimer: number | null = null
let authHeartbeatTimer: number | null = null
const loginRipples = ref<Array<{ id: number; x: number; y: number }>>([])
let loginRippleSeq = 1
const loginRainDrops = ref<
  Array<{ id: number; x: number; y: number; size: number; duration: number; delay: number; opacity: number; drift: number }>
>([])

let lastLoginRainBudget = -1

function loginRainBudget(): number {
  if (typeof window === 'undefined') return 56
  const w = window.innerWidth
  if (w < 480) return 22
  if (w < 768) return 36
  if (w < 1024) return 56
  return 160
}

function refreshLoginRainIfNeeded() {
  if (!showAuthPage.value) return
  const b = loginRainBudget()
  if (b !== lastLoginRainBudget) buildLoginRainDrops()
}


function buildLoginRainDrops(count?: number) {
  const n = count ?? loginRainBudget()
  lastLoginRainBudget = n
  loginRainDrops.value = Array.from({ length: n }, (_, idx) => {
    const near = Math.random() < 0.28
    const size = near ? 2.8 + Math.random() * 2.6 : 1.8 + Math.random() * 2.4
    return {
      id: idx + 1,
      x: Math.random() * 100,
      y: -12 + Math.random() * 108,
      size,
      duration: near ? 1.2 + Math.random() * 1.8 : 1.6 + Math.random() * 2.6,
      delay: -(Math.random() * 6),
      opacity: near ? 0.6 + Math.random() * 0.35 : 0.38 + Math.random() * 0.4,
      drift: near ? -14 + Math.random() * 28 : -10 + Math.random() * 20
    }
  })
}

function addLoginRipple(e: MouseEvent) {
  if (e.button !== 0) return
  const target = e.currentTarget as HTMLElement | null
  if (!target) return
  const rect = target.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  const id = loginRippleSeq++
  const maxRipples = typeof window !== 'undefined' && window.innerWidth < 1024 ? 3 : 8
  loginRipples.value = [...loginRipples.value, { id, x, y }].slice(-maxRipples)

  window.setTimeout(() => {
    loginRipples.value = loginRipples.value.filter((r) => r.id !== id)
  }, 1100)
}



const safeTasks = computed(() => (Array.isArray(tasks.value) ? tasks.value : []))
const safeAllTasks = computed(() => (Array.isArray(allTasks.value) ? allTasks.value : []))

const activeTasks = computed(() =>
  safeTasks.value.filter((t) => t.status === 'OPEN' || t.status === 'CLAIMED' || t.status === 'PENDING_CONFIRMATION')
)
const activeAllTasks = computed(() =>
  safeAllTasks.value.filter((t) => t.status !== 'COMPLETED' && t.status !== 'CANCELLED')
)
const stats = computed(() => {
  const open = activeTasks.value.filter((t) => t.status === 'OPEN').length
  const claimed = activeTasks.value.filter((t) => t.status === 'CLAIMED' || t.status === 'PENDING_CONFIRMATION').length
  const completed = safeTasks.value.filter((t) => t.status === 'COMPLETED').length
  return { open, claimed, completed, total: activeTasks.value.length }
})


const adminVisibleTasks = computed(() =>
  safeAllTasks.value.filter((t) => {
    if (t.status === 'COMPLETED' || t.status === 'CANCELLED') return false
    if (t.status === 'REJECTED' && Boolean(t.overdueRejected)) return false
    if (isAwaitingBountyPayment(t)) return false
    return true
  })
)




const pendingAvatarUsers = computed(() =>
  adminUsers.value
    .filter((u) => u.avatarPending)
    .slice()
    .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
)
const claimerRatingStatsMap = computed(() => {
  const map: Record<string, { avg: number; count: number }> = {}
  for (const t of safeAllTasks.value) {
    if (!t.claimerName || t.claimerRating == null) continue
    const key = String(t.claimerName).toLowerCase()
    if (!map[key]) map[key] = { avg: 0, count: 0 }
    map[key].avg += Number(t.claimerRating)
    map[key].count += 1
  }
  Object.keys(map).forEach((k) => {
    map[k].avg = Number((map[k].avg / Math.max(map[k].count, 1)).toFixed(1))
  })
  return map
})


const pageClass = computed(() => {
  if (currentView.value === 'publish') return 'page-publish'
  if (currentView.value === 'profile') return 'page-profile'
  if (currentView.value === 'admin') return 'page-admin'
  if (currentView.value === 'feedback') return 'page-feedback'
  return 'page-hall'
})

const heroFeaturedTask = computed(() => {
  const open = safeAllTasks.value.filter((t) => t.status === 'OPEN')
  if (!open.length) return null
  return open.reduce((best, cur) => (Number(cur.reward) > Number(best.reward) ? cur : best))
})

const heroLatestTask = computed(() => {
  const open = safeAllTasks.value.filter((t) => t.status === 'OPEN')
  if (!open.length) return null
  return open.reduce((latest, cur) => {
    const curTs = new Date(cur.createdAt || 0).getTime()
    const latestTs = new Date(latest.createdAt || 0).getTime()
    return curTs > latestTs ? cur : latest
  })
})
async function openHeroTaskDetail(task: TaskItem | null) {
  if (task) {
    await openTaskDetail(task)
    return
  }
  scrollToHallTaskList()
}
void heroFeaturedTask
void heroLatestTask
void openHeroTaskDetail

const request = createAuthenticatedRequest(async ({ hadToken, errText }) => {
  if (authBootstrapping.value) return
  // 仅真正的登录失效才踢下线；聊天/公告等接口的临时 401 不应清空登录态
  if (!hadToken || !isHardAuthFailure(401, errText)) return
  isLoggedIn.value = false
  myName.value = ''
  chatSideEffects.stopChatPolling()
  stopAuthHeartbeat()
  currentView.value = 'hall'
  if (hadToken) {
    showAuthPage.value = false
    const t = String(errText || '').trim()
    const generic401 = /^请求失败\s*\(\s*401\s*\)$/.test(t)
    let kickedText = '你的账号在别处登录，请重新登录'
    if (isAuthDisabledMessage(t)) {
      kickedText = '该账号已被管理员禁用，请联系管理员后重试。'
    } else if (t && !generic401) {
      kickedText = isAuthKickedElsewhereMessage(t) ? '你的账号在别处登录，请重新登录' : t
    }
    openKickedDialog(kickedText)
    return
  }
  openAuthPage('login')
  authError.value = errText
}, () => authBootstrapping.value)

const noticeSideEffects = {
  fetchPublicNotices: (async () => {}) as () => Promise<void>,
  fetchUserSystemMessages: (async () => {}) as () => Promise<void>
}

const chatSideEffects = {
  handleOutgoingEcho: ((_to: string) => {}) as (to: string) => void,
  noteSupportIncoming: ((_other: string, _msgId: number) => {}) as (other: string, msgId: number) => void,
  handleIncomingChatMessage: ((_other: string, _msgId: number) => {}) as (other: string, msgId: number) => void,
  onViewingSystemNotice: (() => {}) as () => void,
  stopChatPolling: (() => {}) as () => void,
  resetChatLocalState: (() => {}) as () => void
}

const supportSideEffects = {
  handleHumanAgentChat: ((_o: string, _c: string, _m: number) => false) as (other: string, content: string, msgId: number) => boolean,
  clearSupportChatState: (() => {}) as () => void
}

const authSideEffects = {
  applyAuthLoginSuccess: (async () => {}) as () => Promise<void>
}

const {
  showAuthPage,
  authMode,
  loginSubMode,
  showLoginPassword,
  authLoading,
  authError,
  registerSmsHint,
  registerSmsHintError,
  registerSmsChannel,
  smsSending,
  authForm,
  smsCountdown,
  imageCaptcha,
  imageCaptchaInput,
  captchaCanvasRef,
  forgotImageCaptchaInput,
  forgotCaptchaCanvasRef,
  smsAccountPickerOpen,
  smsPickCandidates,
  forgotPasswordOpen,
  forgotPasswordForm,
  forgotPwdPickToken,
  forgotPwdCandidates,
  forgotPwdSelectedUserId,
  forgotPwdSelectedUsername,
  forgotPwdNeedChooseAccount,
  forgotPwdAccountPickerOpen,
  forgotPwdSmsVerified,
  forgotPwdVerifying,
  forgotPwdSmsCountdown,
  forgotPwdSubmitting,
  forgotPwdModalMsg,
  forgotPwdModalError,
  drawCaptcha,
  refreshImageCaptcha,
  refreshForgotCaptcha,
  selectLoginSmsTab,
  closeSmsAccountPicker,
  openAuthPage,
  closeAuthPage,
  openForgotPasswordModal,
  closeForgotPasswordModal,
  selectForgotPwdAccount,
  closeForgotPwdAccountPicker,
  scheduleForgotPwdSmsVerify,
  verifyForgotPasswordSmsCode,
  sendForgotPasswordSmsCode,
  submitForgotPasswordReset,
  sendSmsCode,
  sendLoginSmsCode,
  submitAuth,
  confirmSmsPickLogin,
  completeLoginWithToken,
  disposeAuthTimers
} = createAuthDomain({
  request,
  buildLoginRainDrops,
  openKickedDialog,
  applyAuthLoginSuccess: () => authSideEffects.applyAuthLoginSuccess(),
  clearSupportChatState: () => supportSideEffects.clearSupportChatState()
})



function formatDeadlineCountdown(latestDeliveryAt?: string) {
  return formatDeadlineCountdownAt(latestDeliveryAt, historyNowTs.value)
}


async function openWalletTaskDetail(tx: WalletTransactionItem) {
  if (!tx.taskId) {
    errorMsg.value = '该流水没有关联任务ID'
    return
  }

  const task = safeAllTasks.value.find((t) => t.id === tx.taskId)
  if (task) {
    await openTaskDetail(task)
    return
  }

  try {
    const data = await request<TaskPageResponse | TaskItem[]>(`${apiBase}?page=1&size=500`)
    const all = Array.isArray(data) ? data : (Array.isArray(data.records) ? data.records : [])
    const hit = all.find((t) => t.id === tx.taskId)
    if (!hit) {
      errorMsg.value = `任务 #${tx.taskId} 不存在或已删除`
      return
    }
    await openTaskDetail(hit)
  } catch (err) {
    errorMsg.value = (err as Error).message
  }
}

async function applyAuthLoginSuccess() {
  loggedInSideEffectsStarted = false
  const me = await hydrateMeState()
  if (me.customerServiceStaff) {
    clearToken()
    isLoggedIn.value = false
    authError.value = '该账号为人工客服专用，请使用客服工作台登录'
    return
  }
  isLoggedIn.value = true
  loadTaskCompleteProofMap()
  await startLoggedInSideEffects()
  closeSmsAccountPicker()
  if (platformAdmin.value || me.platformAdmin) {
    setView('admin')
  } else {
    setView('hall')
  }
  await fetchTasks()
  // 登录页仍打开时，由 AuthPage 播放挥手告别动画后再 closeAuthPage
}
authSideEffects.applyAuthLoginSuccess = applyAuthLoginSuccess

const isScanLoginRoute = ref(
  typeof window !== 'undefined' && window.location.hash.replace(/^#/, '').startsWith('/scan-login')
)

function syncScanLoginRouteFromHash() {
  isScanLoginRoute.value = window.location.hash.replace(/^#/, '').startsWith('/scan-login')
}

function applyMeResponse(me: MeResponse) {
  const prevAvatarPending = myAvatarPending.value
  const prevAvatarUrl = String(myAvatarUrl.value || '').trim()
  myName.value = me.username
  myAvatarUrl.value = me.avatarUrl || ''
  myAvatarPending.value = !!me.avatarPending
  isRiderApproved.value = !!me.riderApproved
  riderApplicationPending.value = !!me.riderApplicationPending
  creditScore.value = Number.isFinite(Number(me.creditScore)) ? Number(me.creditScore) : 80
  const rawBlockedUntil = me.blockedUntil ? String(me.blockedUntil) : ''
  const blockedTs = rawBlockedUntil ? Date.parse(rawBlockedUntil) : NaN
  blockedUntil.value = Number.isFinite(blockedTs) && blockedTs > Date.now() ? rawBlockedUntil : ''
  pendingPenalty.value = Number.isFinite(Number(me.pendingPenalty)) ? Number(me.pendingPenalty) : 0
  platformAdmin.value = !!me.platformAdmin || String(me.accountRole || '').toUpperCase() === 'ADMIN'
  profileForm.value.username = me.username
  const currentAvatarDraft = String(profileForm.value.avatarUrl || '').trim()
  const hasLocalAvatarDraft = currentAvatarDraft.startsWith('data:image/')
  if (!hasLocalAvatarDraft) {
    profileForm.value.avatarUrl =
      me.avatarPending && (me.pendingAvatarUrl || '').trim()
        ? (me.pendingAvatarUrl || '').trim()
        : me.avatarUrl || ''
  }
  form.value.publisherName = me.username
  personalInfoForm.value.realName = me.realName || ''
  personalInfoForm.value.userId = typeof me.userId === 'number' ? me.userId : null
  personalInfoForm.value.studentId = me.studentId || ''
  personalInfoForm.value.phone = me.phone || ''
  form.value.contact = String(me.phone || '').trim()
  if (me.gender === 'female' || me.gender === 'male') {
    personalInfoForm.value.gender = me.gender
  }
  const nextAvatarUrl = String(me.avatarUrl || '').trim()
  if (prevAvatarPending && !myAvatarPending.value && nextAvatarUrl && nextAvatarUrl !== prevAvatarUrl) {
    const text = '你的头像已通过审核，已自动更新。'
    showProfileNotice(text)
    showRealtimeToast({
      title: '头像审核结果',
      message: text,
      kind: 'task'
    })
  }
}

async function hydrateMeState() {
  const me = await request<MeResponse>(`${authBase}/me`)
  applyMeResponse(me)
  return me
}

const AUTH_HANDOFF_REQUEST = 'PK_REQUEST_AUTH_TOKEN'
const AUTH_HANDOFF_RESPONSE = 'PK_AUTH_TOKEN'

function onAuthHandoffMessage(event: MessageEvent) {
  if (event.origin !== window.location.origin) return
  if (event.data?.type !== AUTH_HANDOFF_REQUEST) return
  const token = getToken()
  if (!token || !event.source || typeof (event.source as Window).postMessage !== 'function') return
  ;(event.source as Window).postMessage({ type: AUTH_HANDOFF_RESPONSE, token }, window.location.origin)
}

async function requestAuthFromOpener() {
  if (getToken()) return
  const opener = window.opener
  if (!opener || opener.closed) return

  await new Promise<void>((resolve) => {
    let settled = false
    const finish = () => {
      if (settled) return
      settled = true
      window.removeEventListener('message', onReply)
      window.clearTimeout(timer)
      resolve()
    }
    const onReply = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return
      if (event.data?.type !== AUTH_HANDOFF_RESPONSE) return
      const token = String(event.data.token || '').trim()
      if (!token || isTokenExpired(token)) {
        finish()
        return
      }
      setToken(token)
      applyOptimisticLoginFromToken(token)
      finish()
    }
    window.addEventListener('message', onReply)
    const timer = window.setTimeout(finish, 1200)
    try {
      opener.postMessage({ type: AUTH_HANDOFF_REQUEST }, window.location.origin)
    } catch {
      finish()
    }
  })
}

/** 双击侧栏：重新拉取任务与消息，替代频繁整页刷新 */
async function onLeftNavDblClick() {
  errorMsg.value = ''
  await Promise.all([fetchTasks(), fetchPublicNotices()])
  if (isLoggedIn.value) {
    try {
      await fetchChatContacts()
    } catch {
      // ignore
    }
  }
}

async function onAdminSidebarDblClick() {
  if (!isLoggedIn.value || !isAdmin.value) return
  errorMsg.value = ''
  await loadAdminDashboardData()
}

async function openTaskDetail(task: TaskItem) {
  selectedTask.value = task
  selectedPublisherProfile.value = null
  publisherProfileLoading.value = true

  try {
    selectedPublisherProfile.value = await request<UserPublicProfile>(
      `${authBase}/users/${encodeURIComponent(task.publisherName)}/public-profile`
    )
  } catch {
    selectedPublisherProfile.value = null
  } finally {
    publisherProfileLoading.value = false
  }
}

function closeTaskDetail() {
  selectedTask.value = null
  selectedPublisherProfile.value = null
  publisherProfileLoading.value = false
}

function canRevealTaskContact(task?: Partial<TaskItem> | null) {
  if (!isLoggedIn.value) return false
  if (!task) return false
  const me = String(myName.value || '').trim().toLowerCase()
  if (!me) return false

  const publisher = String(task.publisherName || '').trim().toLowerCase()
  const claimer = String(task.claimerName || '').trim().toLowerCase()
  return publisher === me || claimer === me
}

function normalizeImageUrl(url?: string) {
  const raw = (url || '').trim()
  if (!raw) return ''
  if (raw.startsWith('data:image/')) return raw
  if (raw.startsWith('http://') || raw.startsWith('https://')) return raw
  if (raw.startsWith('/9j/')) return `data:image/jpeg;base64,${raw}`
  if (raw.startsWith('iVBOR')) return `data:image/png;base64,${raw}`
  if (raw.startsWith('UklGR')) return `data:image/webp;base64,${raw}`
  // 兼容后端返回的相对路径（例如 uploads/xxx.jpg），避免在当前路由下被当作相对资源导致 404。
  if (!raw.startsWith('/') && apiOrigin) {
    const looksLikeAssetPath =
      raw.startsWith('uploads/') ||
      raw.startsWith('images/') ||
      raw.startsWith('avatar/') ||
      raw.startsWith('avatars/') ||
      raw.startsWith('files/')
    if (looksLikeAssetPath) return `${apiOrigin}/${raw}`
  }
  return raw
}

function openImagePreview(url?: string) {
  const normalized = normalizeImageUrl(url)
  if (!normalized) return
  previewImageUrl.value = normalized
}

function closeImagePreview() {
  previewImageUrl.value = ''
}

function showProfileNotice(text: string) {
  profileNotice.value = text
  if (profileNoticeTimer) {
    window.clearTimeout(profileNoticeTimer)
  }
  profileNoticeTimer = window.setTimeout(() => {
    profileNotice.value = ''
    profileNoticeTimer = null
  }, 5000)
}

const {
  riderApplyForm,
  riderApplyError,
  applyRider
} = createRiderApplyDomain({
  isLoggedIn,
  isRiderApproved,
  riderApplicationPending,
  request,
  showProfileNotice
})

const {
  openFeedbackPage,
  goFeedback,
  submitUserFeedback
} = createFeedbackDomain({
  isLoggedIn,
  errorMsg,
  currentView,
  request,
  showProfileNotice,
  openAuthPage,
  setView
})

const {
  myCoupons,
  myCouponsLoading,
  couponClaimFocus,
  couponClaimSubmitting,
  fetchMyCoupons,
  redeemCouponCode,
  claimFocusedCoupon,
  goCoupons
} = createCouponsDomain({
  isLoggedIn,
  showUserDropdown,
  request,
  showProfileNotice,
  openAuthPage,
  setView
})

const {
  profileSubModule,
  ratingFormMap,
  ratingDialogTaskId,
  ratingDialogTask,
  isMyPublishedTask,
  rejectedTasks,
  myPublishedTasks,
  myUnpaidPublishDraft,
  myPublishedActiveTasks,
  myClaimedTasks,
  myClaimedActiveTasks,
  myTasksForCustomerService,
  myHistoryTasks,
  getRatingForm,
  setStarScore,
  submitTaskRating
} = createProfileDomain({
  isLoggedIn,
  myName,
  mineTasks,
  safeAllTasks,
  actionLoadingMap,
  errorMsg,
  request,
  showProfileNotice,
  fetchTasks
})


const {
  personalInfoTab,
  personalInfoForm,
  passwordCaptchaInput,
  passwordCaptchaCode,
  passwordCaptchaCanvasRef,
  passwordForm,
  avatarFileInputRef,
  drawPasswordCaptcha,
  refreshPasswordCaptcha,
  ensurePasswordCaptchaReady,
  goPersonalInfo,
  openProfileSettings,
  triggerAvatarFilePick,
  onAvatarFileSelected,
  bindAvatarFileInput,
  bindPasswordCaptchaCanvas,
  saveProfile,
  savePersonalAvatar,
  savePersonalBaseInfo,
  changePassword
} = createPersonalInfoDomain({
  isLoggedIn,
  errorMsg,
  profileNotice,
  showUserDropdown,
  profileForm,
  myName,
  myAvatarUrl,
  myAvatarPending,
  isRiderApproved,
  riderApplicationPending,
  form,
  request,
  setToken,
  showProfileNotice,
  showRealtimeToast,
  setView
})

const {
  walletOverview,
  walletTransactions,
  walletWithdraws,
  walletLoading,
  walletBalanceLoading,
  walletOverviewLoaded,
  walletSubModule,
  withdrawSubmitting,
  walletWithdrawForm,
  walletWithdrawHint,
  walletMaxWithdrawable,
  onWalletWithdrawAmountInput,
  normalizeWalletWithdrawAmount,
  fillWalletMaxWithdraw,
  walletWithdrawFeePreview,
  walletWithdrawPayoutPreview,
  walletWithdrawMin,
  walletWithdrawStep,
  walletWithdrawHardMax,
  walletRechargeForm,
  alipayRechargeSubmitting,
  alipaySyncSubmitting,
  alipayRechargeHint,
  pendingAlipayOutTradeNo,
  openAlipayPayForm,
  fetchWalletBalance,
  fetchWalletData,
  rebuildWalletData,
  prepareWalletAlipayState,
  goWallet,
  submitWithdrawApply,
  submitAlipayRecharge,
  syncAlipayRecharge
} = createWalletDomain({
  isLoggedIn,
  isRiderApproved,
  errorMsg,
  showUserDropdown,
  request,
  setView
})

hallSideEffects.request = request
hallSideEffects.openAuthPage = openAuthPage
hallSideEffects.showProfileNotice = showProfileNotice
hallSideEffects.goWallet = goWallet
hallSideEffects.goCoupons = goCoupons

publishSideEffects.request = request
publishSideEffects.fetchWalletBalance = fetchWalletBalance
publishSideEffects.walletBalance = () => Number(walletOverview.value.balance || 0)
publishSideEffects.contactPhone = () => String(personalInfoForm.value.phone || '')
publishSideEffects.fetchUrgentQuota = fetchUrgentQuota


const {
  pendingAlipayTaskId,
  completeDialogTaskId,
  completeProofText,
  completeProofImages,
  completeProofFileInputRef,
  taskCompletionProofMap,
  proofViewTaskId,
  editingRejectedTaskId,
  taskCompleteProofStoreKey,
  loadTaskCompleteProofMap,
  getTaskCompletionProof,
  openProofView,
  closeProofView,
  fetchAllTasksForProfile,
  fetchMyTasksForProfile,
  submitTask,
  confirmPublishPayment,
  onAlipayWindowMessage,
  syncTaskBountyPaymentForTask,
  resumeTaskBountyPayment,
  syncAwaitingBountyPublishedTasks,
  abandonUnpaidPublishDraft,
  syncTaskAlipayPayment,
  preparePublishAlipayState,
  claimTask,
  completeTask,
  openCompleteDialog,
  closeCompleteDialog,
  triggerCompleteProofFilePick,
  onCompleteProofFilesSelected,
  removeCompleteProofImage,
  cancelTask,
  deleteTask,
  confirmTask,
  approveTask,
  rejectTask,
  editRejectedTask,
  resubmitRejectedTask,
  hydrateCompletionProofFromTasks,
  clearStalePendingAlipayTask,
  findBlockingUnpaidPublishTask,
  handleAlipayReturnFromPopup,
  payTaskBountyViaAlipay,
  persistTaskCompleteProofMap,
  mergeTasksIntoAllTasks,
  mergeTasksIntoMineTasks,
  fetchMyUnpaidPublishDrafts,
  readPendingAlipayTaskId,
  syncTaskAlipayPaymentWithRetry,
  notifyOpenerAlipayTaskPaid,
  handleAlipayTaskPaidFromPopup,
  abandonUnpaidPublishTask
} = createTaskActionsDomain({
  request,
  errorMsg,
  isLoggedIn,
  isAdmin,
  myName,
  loading,
  submitting,
  actionLoadingMap,
  tasks,
  allTasks,
  mineTasks,
  safeTasks,
  safeAllTasks,
  selectedTask,
  currentView,
  setView,
  setPublishFormHint,
  validatePublishFormBeforePay,
  openPublishPaymentSheet,
  closePublishPaymentSheet,
  resetPublishFormAfterSuccess,
  buildPublishTaskBody,
  publishPaymentMethod,
  publishPaymentSheetMode,
  publishSelectedCouponId,
  publishPayableTotal,
  publishWalletPayDisabled,
  form,
  openAuthPage,
  scrollPublishValidationIntoView,
  syncDeliverySelectionFromLatestDeliveryAt,
  myUnpaidPublishDraft,
  isMyPublishedTask,
  rejectedTasks,
  ratingDialogTaskId,
  getRatingForm,
  pendingAlipayOutTradeNo,
  fetchWalletBalance,
  fetchWalletData,
  prepareWalletAlipayState,
  openAlipayPayForm,
  walletOverview,
  shellSideEffects,
  detectTaskStatusChanges,
  showProfileNotice,
  fetchTasks,
  compressImageForChat: (file) => taskActionsSideEffects.compressImageForChat(file),
  isRiderApproved,
  riderApplicationPending,
  blockedUntil,
  creditScore
})
void hydrateCompletionProofFromTasks
void clearStalePendingAlipayTask
void findBlockingUnpaidPublishTask
void handleAlipayReturnFromPopup
void payTaskBountyViaAlipay
void mergeTasksIntoAllTasks
void mergeTasksIntoMineTasks
void fetchMyUnpaidPublishDrafts
void readPendingAlipayTaskId
void syncTaskAlipayPaymentWithRetry
void notifyOpenerAlipayTaskPaid
void handleAlipayTaskPaidFromPopup
void abandonUnpaidPublishTask





function goPublish() {
  if (!isLoggedIn.value) {
    return
  }
  setPublishFormHint(null)
  form.value.publisherName = myName.value.trim()
  form.value.contact = String(personalInfoForm.value.phone || '').trim()
  setView('publish')
  void fetchMyTasksForProfile().then(() => preparePublishAlipayState(false))
  void fetchUrgentQuota()
}

function goRiderApply() {
  if (!isLoggedIn.value) {
    return
  }
  showUserDropdown.value = false
  riderApplyError.value = ''
  setView('riderApply')
}

const chatDeps = {
  isLoggedIn,
  myName,
  myAvatarUrl,
  request,
  errorMsg,
  currentView,
  setView,
  showUserDropdown,
  showRealtimeToast,
  showProfileNotice,
  closeTaskDetail,
  fetchTasks: () => hallSideEffects.fetchTasks(),
  fetchPublicNotices: () => noticeSideEffects.fetchPublicNotices(),
  fetchUserSystemMessages: () => noticeSideEffects.fetchUserSystemMessages(),
  publicNotices,
  userSystemMessages,
  userSystemMessagesUnread,
  normalizeImageUrl,
  realtimeConnected,
  supportHumanMode: ref(false),
  supportAssignedAgent: ref(''),
  supportChatMessages: ref([]) as ReturnType<typeof ref>,
  supportIngestedChatIds: ref(new Set<number>()),
  supportHumanHistoryAfterId: ref(0)
}

const {
  chatTarget,
  chatInput,
  chatImageDraft,
  chatMessages,
  chatContacts,
  temporaryChatContacts,
  chatPinnedUsernames,
  chatHiddenUsernames,
  unreadMap,
  readUpToIncomingIdMap,
  lastSyncedIncomingIdMap,
  contactProfileLoading,
  selectedContactProfile,
  activeContactCard,
  showContactProfileModal,
  sendingTaskToCsId,
  totalUnread,
  publishedNoticesForChat,
  systemNoticeFeed,
  loadChatLocalPrefsForUser,
  isChatContactPinned,
  pinChatContact,
  deleteChatContact,
  clearAllChatUnread,
  loadReadUpToMapForUser,
  persistReadUpToMapForUser,
  loadLatestSeenMapForUser,
  syncSystemNoticeUnread,
  markSystemNoticeRead,
  scrollChatToBottom,
  bindChatMessagesEl,
  shouldShowChatDivider,
  chatAvatarUrl,
  initializeChatUnreadState,
  startChatPolling,
  stopChatPolling,
  resetChatLocalState,
  handleOutgoingEcho,
  noteSupportIncoming,
  handleIncomingChatMessage,
  onViewingSystemNotice,
  goChat,
  askPublisherFirst,
  fetchChatContacts,
  openChatWith,
  openContactProfile,
  closeContactProfileModal,
  loadChatHistory,
  sendChatMessage,
  isCustomerServiceChatTarget,
  sendTaskToCustomerService,
  onChatImageSelected,
  clearChatImageDraft,
  setChatImageDraft,
  compressImageForChat
} = createChatDomain(chatDeps as any)
taskActionsSideEffects.compressImageForChat = compressImageForChat
void lastSyncedIncomingIdMap
void markSystemNoticeRead

const {
  showSupportChat,
  supportChatSending,
  supportTransferLoading,
  supportChatMessages,
  supportAgentOnline,
  supportAgentStatusText,
  supportHumanMode,
  supportAssignedAgent,
  supportAssignedAgentAvatar,
  supportHandoffJoined,
  supportHumanHistoryAfterId,
  supportIngestedChatIds,
  SUPPORT_CS_RATING_TAGS,
  supportCsRatingVisible,
  supportCsRatingAgent,
  supportCsRatingScore,
  supportCsRatingTags,
  supportCsRatingComment,
  supportCsRatingSubmitting,
  supportCsRatingDone,
  openSupportChat,
  closeSupportChat,
  clearSupportChatState,
  refreshSupportAgentAvailability,
  openSupportWallet,
  openSupportMyTasks,
  openSupportRiderApply,
  openSupportCoupons,
  openSupportTaskById,
  sendSupportChatMessage,
  transferToHumanSupport,
  endSupportHumanSession,
  ingestSupportHumanIncoming,
  supportCsRatingScoreLabel,
  toggleSupportCsRatingTag,
  skipSupportCsRating,
  submitSupportCsRating
} = createSupportDomain({
  isLoggedIn,
  myName,
  request,
  errorMsg,
  showRealtimeToast,
  goWallet,
  goProfile,
  goRiderApply,
  goCoupons,
  openTaskDetail,
  fetchMyTasksForProfile,
  fetchWalletData,
  walletOverview,
  walletTransactions,
  walletWithdraws,
  mineTasks,
  allTasks,
  tasks,
  creditScore,
  blockedUntil,
  isRiderApproved,
  riderApplicationPending,
  openAuthPage,
  hallFaqItems,
  unreadMap
})

;(chatDeps as any).supportHumanMode = supportHumanMode
;(chatDeps as any).supportAssignedAgent = supportAssignedAgent
;(chatDeps as any).supportChatMessages = supportChatMessages
;(chatDeps as any).supportIngestedChatIds = supportIngestedChatIds
;(chatDeps as any).supportHumanHistoryAfterId = supportHumanHistoryAfterId

supportSideEffects.clearSupportChatState = clearSupportChatState
supportSideEffects.handleHumanAgentChat = (other, content, msgId) => {
  if (!supportHumanMode.value || !supportAssignedAgent.value || !usernameMatches(other, supportAssignedAgent.value)) return false
  ingestSupportHumanIncoming(content, msgId)
  if (!showSupportChat.value) {
    showRealtimeToast({ title: '人工客服回复', message: content.slice(0, 40) || '客服发来一条消息', kind: 'chat' })
  }
  return true
}

chatSideEffects.handleOutgoingEcho = handleOutgoingEcho
chatSideEffects.noteSupportIncoming = noteSupportIncoming
chatSideEffects.handleIncomingChatMessage = handleIncomingChatMessage
chatSideEffects.onViewingSystemNotice = onViewingSystemNotice
chatSideEffects.stopChatPolling = stopChatPolling
chatSideEffects.resetChatLocalState = resetChatLocalState

async function fetchUserSystemMessages() {
  if (!isLoggedIn.value) {
    userSystemMessages.value = []
    userSystemMessagesUnread.value = 0
    syncSystemNoticeUnread()
    return
  }
  userSystemMessagesLoading.value = true
  try {
    const res = await request<SystemMessageListResponse>(`${apiOrigin}/api/system-messages`)
    userSystemMessages.value = Array.isArray(res.messages) ? res.messages : []
    userSystemMessagesUnread.value = Number(res.unreadCount || 0)
    syncSystemNoticeUnread()
  } catch {
    // 个人系统消息加载失败不阻塞主流程
  } finally {
    userSystemMessagesLoading.value = false
  }
}

async function fetchPublicNotices() {
  publicNoticesLoading.value = true
  try {
    const res = await fetch(`${apiOrigin}/api/notices`)
    if (!res.ok) {
      return
    }
    publicNotices.value = (await res.json()) as NoticeItem[]
    syncSystemNoticeUnread()
    onPublicNoticesLoaded()
  } catch {
    // 公告加载失败不阻塞主流程
  } finally {
    publicNoticesLoading.value = false
  }
}

noticeSideEffects.fetchUserSystemMessages = fetchUserSystemMessages
noticeSideEffects.fetchPublicNotices = fetchPublicNotices

function startAuthHeartbeat() {
  if (authHeartbeatTimer) {
    window.clearInterval(authHeartbeatTimer)
  }
  authHeartbeatTimer = window.setInterval(async () => {
    if (!isLoggedIn.value) return
    const token = getToken()
    if (!token || isTokenExpired(token)) {
      logout()
      return
    }
    try {
      await hydrateMeState()
    } catch (err) {
      if (isTransientUnauthorizedMessage(String((err as Error)?.message || ''))) {
        return
      }
    }
  }, 15000)
}

function stopAuthHeartbeat() {
  if (authHeartbeatTimer) {
    window.clearInterval(authHeartbeatTimer)
    authHeartbeatTimer = null
  }
}

function notifyOffline(opts?: { keepalive?: boolean }) {
  const token = getToken()
  if (!token) return
  fetch(`${authBase}/offline`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    keepalive: !!opts?.keepalive
  }).catch(() => {
    // 页面关闭阶段可能被浏览器中断，失败可忽略
  })
}


const miniprogramPageOptions = MINIPROGRAM_PAGE_OPTIONS

function logout() {
  notifyOffline()
  stopRealtime()
  closeRealtimeToast()
  clearSupportChatState()
  showUserDropdown.value = false
  isLoggedIn.value = false
  myName.value = ''
  myAvatarUrl.value = ''
  isRiderApproved.value = false
  riderApplicationPending.value = false
  creditScore.value = 80
  blockedUntil.value = ''
  pendingPenalty.value = 0
  platformAdmin.value = false
  profileForm.value.username = ''
  profileForm.value.avatarUrl = ''
  resetChatLocalState()
  resetTaskStatusTracking()
  stopTaskStatusPolling()
  mineTasks.value = []
  clearToken()
  loggedInSideEffectsStarted = false
  walletOverview.value = { balance: 0, totalIncome: 0, totalWithdrawn: 0, withdrawingAmount: 0 }
  walletOverviewLoaded.value = false
  stopAuthHeartbeat()
  taskCompletionProofMap.value = {}
  setView('hall')
  authForm.value.password = ''
  authForm.value.confirmPassword = ''
}

function onVisibilityKickCheck() {
  if (!isLoggedIn.value) return
  if (document.visibilityState !== 'visible') return
  const token = getToken()
  if (!token || isTokenExpired(token)) {
    logout()
    return
  }
  hydrateMeState().catch((err) => {
    if (isTransientUnauthorizedMessage(String((err as Error)?.message || ''))) {
      return
    }
  })
  void fetchWalletBalance()
}

function applyOptimisticLoginFromToken(token: string): boolean {
  if (isTokenExpired(token)) return false
  const payload = parseTokenPayload(token)
  if (!payload?.username) return false
  const nextName = String(payload.username || '').trim()
  const prevName = String(myName.value || '').trim()
  if (prevName && nextName && prevName.toLowerCase() !== nextName.toLowerCase()) {
    clearSupportChatState()
  }
  myName.value = nextName
  form.value.publisherName = nextName
  profileForm.value.username = nextName
  isLoggedIn.value = true
  return true
}

let loggedInSideEffectsStarted = false

async function startLoggedInSideEffects() {
  if (loggedInSideEffectsStarted) return
  loggedInSideEffectsStarted = true
  loadTaskCompleteProofMap()
  loadReadUpToMapForUser(myName.value)
  loadLatestSeenMapForUser(myName.value)
  loadChatLocalPrefsForUser(myName.value)
  await initializeChatUnreadState()
  startRealtime()
  if (!realtimeConnected.value) {
    startChatPolling()
  }
  startAuthHeartbeat()
  startTaskStatusPolling()
  void fetchWalletBalance()
  void fetchMyTasksForProfile()
  void fetchUserSystemMessages()
}

async function syncMeStateInBackground() {
  const token = getToken()
  if (!token || !isLoggedIn.value) return
  try {
    await hydrateMeState()
  } catch {
    // 后台同步失败时保留本地登录态
  }
}

async function bootstrapAuth() {
  let savedToken = getToken()
  if (!savedToken) {
    await requestAuthFromOpener()
    savedToken = getToken()
  }
  if (!savedToken) {
    isLoggedIn.value = false
    myName.value = ''
    return
  }

  if (isTokenExpired(savedToken)) {
    clearToken()
    isLoggedIn.value = false
    myName.value = ''
    return
  }

  // 只要本地 token 有效，先立即恢复登录 UI，不等 /me
  applyOptimisticLoginFromToken(savedToken)

  authBootstrapping.value = true
  try {
    const meResult = await fetchAuthMeWithRetry<MeResponse>(authBase, savedToken)

    if (meResult.ok) {
      applyMeResponse(meResult.data)
      isLoggedIn.value = true
    } else if (isHardAuthFailure(meResult.status, meResult.message)) {
      clearToken()
      isLoggedIn.value = false
      myName.value = ''
      stopRealtime()
      stopAuthHeartbeat()
      loggedInSideEffectsStarted = false
      const msg = meResult.message
      if (isAuthDisabledMessage(msg)) {
        openKickedDialog('该账号已被管理员禁用，请联系管理员后重试。')
      } else if (isAuthKickedElsewhereMessage(msg) || msg.includes('重新登录')) {
        openKickedDialog('你的账号在别处登录，请重新登录')
      }
      return
    }

    if (isLoggedIn.value) {
      await startLoggedInSideEffects()
      void syncMeStateInBackground()
    }
  } finally {
    authBootstrapping.value = false
  }
}

function onWindowClickCloseUserDropdown(event: MouseEvent) {
  if (!showUserDropdown.value) return
  const target = event.target
  if (target instanceof Element && (target.closest('.pk-user-menu') || target.closest('.pk-account-hub'))) {
    return
  }
  showUserDropdown.value = false
}

function onWindowClickClosePickupCascader(event: MouseEvent) {
  const target = event.target as Node | null
  if (showPickupCascader.value) {
    if (pickupCascaderRef.value && target && pickupCascaderRef.value.contains(target)) {
      return
    }
    showPickupCascader.value = false
  }
  if (showDropoffCascader.value) {
    if (dropoffCascaderRef.value && target && dropoffCascaderRef.value.contains(target)) {
      return
    }
    showDropoffCascader.value = false
  }
}

watch(currentView, (view) => {
  if (view === 'personalInfo' && personalInfoTab.value === 'password') {
    nextTick(() => ensurePasswordCaptchaReady())
  }
  if (view === 'wallet' && isLoggedIn.value) {
    fetchWalletData()
  }
  if (view === 'coupons' && isLoggedIn.value) {
    fetchMyCoupons()
  }
  if (view === 'admin' && isLoggedIn.value && isAdmin.value) {
    loadAdminDashboardData()
  }
  if (view === 'hall') {
    fetchPublicNotices()
    void refreshCampusWeather()
  }
})

watch([isLoggedIn, isRiderApproved], ([loggedIn]) => {
  if (loggedIn && currentView.value === 'wallet') {
    fetchWalletData()
  }
})

watch([currentView, profileSubModule], ([view, sub]) => {
  if (view === 'profile' && isLoggedIn.value) {
    void fetchMyTasksForProfile()
  }
  if (view === 'profile' && sub === 'published') {
    loadTaskCompleteProofMap()
    void syncAwaitingBountyPublishedTasks()
  }
})

function stopHistoryTick() {
  if (historyTickTimer) {
    window.clearInterval(historyTickTimer)
    historyTickTimer = null
  }
}

function profileHasCountdownTasks() {
  return mineTasks.value.some((t) => {
    if (!t.latestDeliveryAt) return false
    return t.status === 'CLAIMED' || t.status === 'OPEN' || t.status === 'PENDING_CONFIRMATION'
  })
}

function syncHistoryTick() {
  const need =
    isLoggedIn.value &&
    currentView.value === 'profile' &&
    profileHasCountdownTasks()
  if (!need) {
    stopHistoryTick()
    return
  }
  if (historyTickTimer) return
  historyNowTs.value = Date.now()
  historyTickTimer = window.setInterval(() => {
    historyNowTs.value = Date.now()
  }, 1000)
}

watch([currentView, isLoggedIn, mineTasks], () => {
  syncHistoryTick()
})

onMounted(async () => {
  buildLoginRainDrops()
  window.addEventListener('message', onAlipayWindowMessage)
  await bootstrapAuth()
  loadTaskCompleteProofMap()
  syncHistoryTick()

  refreshPasswordCaptcha()
  await fetchTasks()
  startCampusWeatherPolling()
  void fetchHallCompletionStats()
  if (currentView.value === 'profile' && isLoggedIn.value) {
    await fetchMyTasksForProfile()
  }
  fetchPublicNotices()
  if (currentView.value === 'admin' && isLoggedIn.value && isAdmin.value) {
    loadAdminDashboardData()
  }
  if (currentView.value === 'publish' && isLoggedIn.value) {
    void preparePublishAlipayState(false)
    void fetchUrgentQuota()
  }
  if (currentView.value === 'wallet' && isLoggedIn.value) {
    void prepareWalletAlipayState(true).then(() => fetchWalletData())
  }
  window.addEventListener('click', onWindowClickCloseUserDropdown)
  window.addEventListener('click', onWindowClickClosePickupCascader)
  window.addEventListener('resize', onWindowResize)
  document.addEventListener('visibilitychange', onVisibilityKickCheck)
  window.addEventListener('hashchange', () => {
    syncScanLoginRouteFromHash()
    if (isScanLoginRoute.value) return
    const nextView = parseViewFromHash()
    if (nextView === 'admin' && (!isLoggedIn.value || !isAdmin.value)) {
      currentView.value = 'hall'
      return
    }
    if ((nextView === 'profile' || nextView === 'personalInfo' || nextView === 'publish' || nextView === 'chat' || nextView === 'wallet' || nextView === 'riderApply' || nextView === 'feedback') && !isLoggedIn.value) {
      currentView.value = 'hall'
      return
    }
    currentView.value = nextView
    if (nextView === 'profile' && isLoggedIn.value) {
      void fetchMyTasksForProfile()
    }
    if (nextView === 'publish' && isLoggedIn.value) {
      void preparePublishAlipayState(false)
      void fetchUrgentQuota()
    }
    if (nextView === 'wallet' && isLoggedIn.value) {
      void prepareWalletAlipayState(true).then(() => fetchWalletData())
    }
  })
  setupNoticeMarqueeObserver()
})

onUnmounted(() => {
  stopChatPolling()
  stopAuthHeartbeat()
  stopTaskStatusPolling()
  disposeHallNoticeUi()
  window.removeEventListener('click', onWindowClickCloseUserDropdown)
  window.removeEventListener('click', onWindowClickClosePickupCascader)
  window.removeEventListener('resize', onWindowResize)
  window.removeEventListener('message', onAlipayWindowMessage)
  window.removeEventListener('message', onAuthHandoffMessage)
  document.removeEventListener('visibilitychange', onVisibilityKickCheck)
  disposeAuthTimers()
  if (profileNoticeTimer) {
    window.clearTimeout(profileNoticeTimer)
  }
  if (adminNoticeTimer) {
    window.clearTimeout(adminNoticeTimer)
  }
  clearPublishFormHintTimer()
  if (historyTickTimer) {
    window.clearInterval(historyTickTimer)
    historyTickTimer = null
  }
  riderStatsSideEffects.disposeRiderCharts()
})




const {
  adminLogs,
  adminLogsPaging,
  adminFeedbacks,
  adminFeedbacksPaging,
  adminFeedbacksUnreadCount,
  adminFeedbacksLoading,
  adminCsRatings,
  adminCsRatingsPaging,
  adminCsRatingsLoading,
  adminCsRatingsAverage,
  adminCsRatingsTotal,
  adminUsers,
  adminCustomerServiceStaff,
  adminCsForm,
  adminCsCreating,
  adminCsLastCreated,
  adminPendingWithdraws,
  adminNotices,
  adminNoticeLoading,
  adminNoticeEditorId,
  adminNoticeEditor,
  adminNoticeEditorDirty,
  adminWithdrawKeyword,
  adminWithdrawRemarkMap,
  adminWalletCreditUserId,
  adminWalletCreditAmount,
  adminWalletCreditRemark,
  adminWalletCreditSubmitting,
  adminCouponTemplates,
  adminCouponTemplatesLoading,
  adminCouponTab,
  adminHallActivityForm,
  adminHallActivitySubmitting,
  adminHallActivityEditingId,
  adminHallActivityFormError,
  adminHallActivities,
  adminHallActivitiesLoading,
  adminCouponForm,
  adminCouponCreateSubmitting,
  adminCouponIssue,
  adminCouponIssueSubmitting,
  adminCouponRedeem,
  adminCouponRedeemSubmitting,
  adminCouponGeneratedCodes,
  adminCouponRedeemCodes,
  adminCouponRedeemCodesLoading,
  adminCouponRedeemFilterTemplateId,
  adminAiConfig,
  adminAiTestPrompt,
  adminAiTesting,
  aiAutoAuditSubmitting,
  aiAutoAuditEnabled,
  adminAppealLoading,
  adminAppealKeyword,
  adminAppeals,
  adminAppealReply,
  adminAppealReplyText,
  adminAppealReplySubmitting,
  appealDecidingKey,
  adminNotice,
  adminNoticeTone,
  adminNoticeTimer,
  fetchAdminLogs,
  goAdminLogsPrev,
  goAdminLogsNext,
  fetchAdminFeedbacks,
  goAdminFeedbacksPrev,
  goAdminFeedbacksNext,
  fetchAdminCsRatings,
  goAdminCsRatingsPrev,
  goAdminCsRatingsNext,
  setAdminFeedbackRead,
  fetchAdminUsers,
  fetchAdminCustomerServiceStaff,
  createAdminCustomerServiceStaff,
  updateUserCreditScore,
  setUserAccountRole,
  setUserEnabled,
  auditRiderApplication,
  revokeRiderCertification,
  auditUserAvatar,
  showAdminNotice,
  filteredAdminPendingWithdraws,
  adminSelectedNotice,
  resetAdminNoticeEditor,
  selectAdminNotice,
  newAdminNotice,
  fetchAdminNotices,
  saveAdminNotice,
  toggleAdminNoticePublish,
  toggleAdminNoticePin,
  deleteAdminNotice,
  loadAdminDashboardData,
  fetchAdminPendingWithdraws,
  auditWithdrawRequest,
  submitAdminWalletCredit,
  fetchAdminCouponTemplates,
  padCouponDt,
  couponValidityRangeFromDays,
  createAdminCouponTemplate,
  fetchAdminHallActivities,
  defaultHallCta,
  createAdminHallActivity,
  editAdminHallActivity,
  cancelAdminHallActivityEdit,
  setAdminHallActivityStatus,
  deleteAdminHallActivity,
  onAdminHallActivityBannerSelected,
  onAdminHallActivityWebBannerSelected,
  openAdminHallActivityModule,
  hallActionTypeLabel,
  selectedHallCustomPage,
  onHallActionTypeChange,
  fetchAdminCouponRedeemCodes,
  adminCouponTemplateName,
  updateAdminCouponTemplateTheme,
  onAdminCouponThemeChange,
  onAdminCouponBannerSelected,
  onAdminCouponTemplateBannerSelected,
  updateAdminCouponTemplateBanner,
  copyTextAdmin,
  issueAdminCoupon,
  generateAdminCouponRedeemCodes,
  copyAdminCouponCodes,
  setAdminCouponTemplateStatus,
  deleteAdminCouponTemplate,
  fetchAdminAiConfig,
  saveAdminAiConfig,
  testAdminAiConfig,
  autoAuditPendingTasks,
  fetchAiAutoAuditEnabled,
  setAiAutoAuditEnabled,
  refreshAuditCenter,
  appealTask,
  appealText,
  appealSubmitting,
  openAppeal,
  closeAppeal,
  appealProcessedStoreKey,
  appealDecisionStoreKey,
  normalizeAppealDecision,
  loadAppealDecisionMap,
  persistAppealDecisionMap,
  loadAppealProcessedSet,
  persistAppealProcessedSet,
  parseAppealPayload,
  fetchAppealTaskMap,
  resolveAppealDecision,
  fetchAdminAppeals,
  toggleAppealProcessed,
  decideAppeal,
  openAdminAppealReply,
  closeAdminAppealReply,
  sendAppealSystemMessage,
  submitAdminAppealReply,
  filteredAdminAppeals,
  submitAppeal
} = createAdminDomain({
  request,
  errorMsg,
  isLoggedIn,
  isAdmin,
  myName,
  currentView,
  actionLoadingMap,
  tasks,
  safeAllTasks,
  chatContacts,
  temporaryChatContacts,
  fetchChatContacts,
  fetchTasks,
  compressImageForChat,
  shellSideEffects,
  showProfileNotice,
  adminModule,
  auditSubModule
})
void padCouponDt
void couponValidityRangeFromDays
void updateAdminCouponTemplateTheme
void normalizeAppealDecision
void fetchAppealTaskMap
void resolveAppealDecision
void sendAppealSystemMessage

const {
  riderEchartRef,
  riderOverviewEchartRef,
  taskOverviewTrendEchartRef,
  taskOverviewStatusEchartRef,
  taskStatsLoading,
  taskStatsData,
  taskStatsTrendDays,
  adminRiderCompareRows,
  selectedRiderName,
  riderRankMetric,
  selectedRiderRow,
  riderLeaderboardRows,
  riderMetricValue,
  riderRankMaxValue,
  riderTop3,
  riderRankMetricLabel,
  riderChartMax,
  riderChartItems,
  riderOverviewTrendDays,
  riderOverviewKpis,
  riderOverviewTrend,
  riderOverviewDistribution,
  bindRiderEchartEl,
  bindRiderOverviewEchartEl,
  bindTaskOverviewTrendEchartEl,
  bindTaskOverviewStatusEchartEl,
  isRiderUser,
  resizeRiderCharts,
  disposeRiderCharts,
  renderTaskOverviewCharts,
  renderRiderEchart,
  renderRiderOverviewChart,
  fetchAdminTaskStats,
  taskOverviewKpis,
  ensureEchartsLoaded
} = createRiderStatsDomain({
  adminUsers,
  safeAllTasks,
  request,
  currentView,
  adminModule,
  myName,
  isRiderApproved,
  isAdmin,
  errorMsg
})
riderStatsSideEffects.resizeRiderCharts = resizeRiderCharts
riderStatsSideEffects.disposeRiderCharts = disposeRiderCharts
void riderEchartRef
void riderOverviewEchartRef
void taskOverviewTrendEchartRef
void taskOverviewStatusEchartRef
void renderTaskOverviewCharts

const __appBag = {
  actionLoadingMap,
  activateHallNotice,
  openActiveHallNoticeLink,
  activeAllTasks,
  activeContactCard,
  activeTasks,
  addLoginRipple,
  adminAppealKeyword,
  adminAppealLoading,
  adminAppealReply,
  adminAppealReplySubmitting,
  adminAppealReplyText,
  appealDecidingKey,
  adminAppeals,
  adminAiConfig,
  adminAiTestPrompt,
  adminAiTesting,
  adminFeedbacks,
  adminFeedbacksLoading,
  adminFeedbacksPaging,
  adminFeedbacksUnreadCount,
  adminCsRatings,
  adminCsRatingsPaging,
  adminCsRatingsLoading,
  adminCsRatingsAverage,
  adminCsRatingsTotal,
  fetchAdminCsRatings,
  goAdminCsRatingsPrev,
  goAdminCsRatingsNext,
  aiAutoAuditSubmitting,
  aiAutoAuditEnabled,
  adminLogs,
  adminLogsPaging,
  adminModule,
  adminNotice,
  adminNoticeTone,
  adminNoticeEditor,
  adminNoticeEditorDirty,
  adminNoticeEditorId,
  adminNoticeLoading,
  adminNotices,
  adminPendingWithdraws,
  adminRiderCompareRows,
  adminSelectedNotice,
  adminUsers,
  adminVisibleTasks,
  adminWalletCreditAmount,
  adminWalletCreditRemark,
  adminWalletCreditSubmitting,
  adminWalletCreditUserId,
  adminCouponTab,
  adminHallActivityForm,
  adminHallActivitySubmitting,
  adminHallActivityEditingId,
  adminHallActivityFormError,
  adminHallActivities,
  adminHallActivitiesLoading,
  fetchAdminHallActivities,
  createAdminHallActivity,
  editAdminHallActivity,
  cancelAdminHallActivityEdit,
  setAdminHallActivityStatus,
  deleteAdminHallActivity,
  hallActionTypeLabel,
  defaultHallCta,
  miniprogramPageOptions,
  selectedHallCustomPage,
  onHallActionTypeChange,
  onAdminHallActivityBannerSelected,
  onAdminHallActivityWebBannerSelected,
  openAdminHallActivityModule,
  adminCouponTemplates,
  adminCouponTemplatesLoading,
  adminCouponForm,
  adminCouponCreateSubmitting,
  adminCouponIssue,
  adminCouponIssueSubmitting,
  adminCouponRedeem,
  adminCouponRedeemSubmitting,
  adminCouponGeneratedCodes,
  adminCouponRedeemCodes,
  adminCouponRedeemCodesLoading,
  adminCouponRedeemFilterTemplateId,
  fetchAdminCouponTemplates,
  createAdminCouponTemplate,
  issueAdminCoupon,
  generateAdminCouponRedeemCodes,
  copyAdminCouponCodes,
  fetchAdminCouponRedeemCodes,
  adminCouponTemplateName,
  onAdminCouponThemeChange,
  onAdminCouponBannerSelected,
  onAdminCouponTemplateBannerSelected,
  updateAdminCouponTemplateBanner,
  copyTextAdmin,
  setAdminCouponTemplateStatus,
  deleteAdminCouponTemplate,
  adminWithdrawKeyword,
  adminWithdrawRemarkMap,
  allTasks,
  appealDecisionStoreKey,
  appealProcessedStoreKey,
  appealSubmitting,
  appealTask,
  appealText,
  applyFilters,
  applyRider,
  approveTask,
  askPublisherFirst,
  auditRiderApplication,
  revokeRiderCertification,
  updateUserCreditScore,
  setUserAccountRole,
  auditSubModule,
  auditUserAvatar,
  auditWithdrawRequest,
  authError,
  authForm,
  authLoading,
  registerSmsHint,
  registerSmsHintError,
  registerSmsChannel,
  smsSending,
  authMode,
  loginSubMode,
  showLoginPassword,
  imageCaptchaInput,
  forgotImageCaptchaInput,
  forgotCaptchaCanvasRef: markRaw(forgotCaptchaCanvasRef),
  smsAccountPickerOpen,
  smsPickCandidates,
  forgotPasswordOpen,
  forgotPasswordForm,
  forgotPwdPickToken,
  forgotPwdCandidates,
  forgotPwdSelectedUserId,
  forgotPwdSelectedUsername,
  forgotPwdNeedChooseAccount,
  forgotPwdAccountPickerOpen,
  forgotPwdSmsVerified,
  forgotPwdVerifying,
  forgotPwdSmsCountdown,
  forgotPwdSubmitting,
  forgotPwdModalMsg,
  forgotPwdModalError,
  sendLoginSmsCode,
  selectLoginSmsTab,
  openForgotPasswordModal,
  closeForgotPasswordModal,
  sendForgotPasswordSmsCode,
  verifyForgotPasswordSmsCode,
  scheduleForgotPwdSmsVerify,
  selectForgotPwdAccount,
  closeForgotPwdAccountPicker,
  submitForgotPasswordReset,
  closeSmsAccountPicker,
  confirmSmsPickLogin,
  completeLoginWithToken,
  refreshForgotCaptcha,
  refreshImageCaptcha,
  avatarFileInputRef: markRaw(avatarFileInputRef),
  bindAvatarFileInput,
  bindChatMessagesEl,
  bindPasswordCaptchaCanvas,
  bootstrapAuth,
  bindRiderEchartEl,
  bindRiderOverviewEchartEl,
  bindTaskOverviewTrendEchartEl,
  bindTaskOverviewStatusEchartEl,
  buildLoginRainDrops,
  buildingOptions,
  canRevealTaskContact,
  cancelTask,
  captchaCanvasRef: markRaw(captchaCanvasRef),
  changePassword,
  chatAvatarUrl,
  decideAppeal,
  deleteTask,
  chatContacts,
  chatPinnedUsernames,
  chatHiddenUsernames,
  pinChatContact,
  deleteChatContact,
  isChatContactPinned,
  clearAllChatUnread,
  chatImageDraft,
  chatInput,
  chatMessages,
  chatReadStoreKey,
  chatTarget,
  claimTask,
  claimerRatingStatsMap,
  closeAdminAppealReply,
  closeAppeal,
  closeAuthPage,
  closeCompleteDialog,
  closeContactProfileModal,
  clearChatImageDraft,
  setChatImageDraft,
  closeImagePreview,
  closeKickedDialog,
  closeProofView,
  closeTaskDetail,
  completeDialogTaskId,
  completeProofFileInputRef: markRaw(completeProofFileInputRef),
  completeProofImages,
  completeProofText,
  completeTask,
  confirmTask,
  compressImageForChat,
  contactProfileLoading,
  currentView,
  deleteAdminNotice,
  drawCaptcha,
  drawPasswordCaptcha,
  dropoffBuilding,
  setDropoffCascaderRootEl,
  dropoffDisplayText,
  dropoffManualInput,
  dropoffRoom,
  dropoffRoomOptions,
  onPublishImagesSelected,
  removePublishImage,
  publishImageInputRef: markRaw(publishImageInputRef),
  dropoffSelectedText,
  editRejectedTask,
  editingRejectedTaskId,
  ensureEchartsLoaded,
  ensurePasswordCaptchaReady,
  errorMsg,
  fetchAdminAppeals,
  fetchAdminAiConfig,
  fetchAiAutoAuditEnabled,
  fetchAdminLogs,
  fetchAdminFeedbacks,
  goAdminLogsPrev,
  goAdminLogsNext,
  goAdminFeedbacksPrev,
  goAdminFeedbacksNext,
  fetchAdminNotices,
  fetchAdminPendingWithdraws,
  fetchAdminTaskStats,
  fetchAdminUsers,
  fetchAdminCustomerServiceStaff,
  createAdminCustomerServiceStaff,
  adminCustomerServiceStaff,
  adminCsForm,
  adminCsCreating,
  adminCsLastCreated,
  fetchAllTasksForProfile,
  fetchMyTasksForProfile,
  fetchChatContacts,
  fetchPublicNotices,
  fetchTasks,
  fetchWalletData,
  rebuildWalletData,
  fetchWalletBalance,
  walletBalanceLoading,
  walletOverviewLoaded,
  filteredAdminAppeals,
  focusDropoffCascader,
  focusPickupCascader,
  filteredAdminPendingWithdraws,
  filters,
  form,
  taskTypeOptions,
  taskTypeLabel,
  hotTaskTypes,
  hallZones,
  hallStats,
  hallActivities,
  hallLeaderboard,
  hallFaqItems,
  hallStatusLabel,
  hallTaskCategoryLabel,
  hallTaskDistance,
  formatTaskTimeAgo,
  selectZoneFilter,
  selectSortMode,
  heroCategoryTags,
  heroSideStats,
  selectHeroCategoryTag,
  applyHeroSearch,
  noticeBarVisible,
  hallNoticeBannerText,
  selectTaskTypeFilter,
  deliveryDateOptions,
  deliveryQuickOptions,
  estimateLatestDeliveryText,
  latestDeliveryQuickOption,
  selectedDeliverySummary,
  selectedDeliveryDate,
  showDeliveryPicker,
  formatChatDateDivider,
  formatChatTime,
  formatNoticeChatTime,
  isSystemNoticeContact,
  publishedNoticesForChat,
  systemNoticeFeed,
  fetchUserSystemMessages,
  userSystemMessagesUnread,
  SYSTEM_NOTICE_CONTACT,
  SYSTEM_NOTICE_USERNAME,
  formatDateOnly,
  formatDeadlineCountdown,
  formatRating,
  getRatingForm,
  getTaskCompletionProof,
  goAdmin,
  goChat,
  goFeedback,
  openFeedbackPage,
  goHall,
  goLoginFromKickedDialog,
  goNextPage,
  goPersonalInfo,
  goPrevPage,
  goProfile,
  goPublish,
  goRiderApply,
  goWallet,
  goCoupons,
  myCoupons,
  myCouponsLoading,
  couponClaimFocus,
  couponClaimSubmitting,
  claimFocusedCoupon,
  fetchMyCoupons,
  hallNoticeActiveId,
  hallNoticeExpanded,
  hallTaskEmoji,
  hallTopNotice,
  historyNowTs,
  hydrateMeState,
  imageCaptcha,
  isAdmin,
  isClassroomBuilding,
  isDropoffClassroomBuilding,
  isLoggedIn,
  isRiderApproved,
  creditScore,
  blockedUntil,
  pendingPenalty,
  isRiderUser,
  showSupportChat,
  supportChatSending,
  supportTransferLoading,
  supportChatMessages,
  supportAgentOnline,
  supportAgentStatusText,
  supportHumanMode,
  supportHandoffJoined,
  supportAssignedAgent,
  supportAssignedAgentAvatar,
  closeSupportChat,
  openSupportChat,
  openSupportWallet,
  openSupportMyTasks,
  openSupportTaskById,
  openSupportRiderApply,
  openSupportCoupons,
  refreshSupportAgentAvailability,
  sendSupportChatMessage,
  transferToHumanSupport,
  endSupportHumanSession,
  SUPPORT_CS_RATING_TAGS,
  supportCsRatingVisible,
  supportCsRatingAgent,
  supportCsRatingScore,
  supportCsRatingTags,
  supportCsRatingComment,
  supportCsRatingSubmitting,
  supportCsRatingDone,
  supportCsRatingScoreLabel,
  toggleSupportCsRatingTag,
  skipSupportCsRating,
  submitSupportCsRating,
  kickedDialogOpen,
  kickedDialogText,
  loadAdminDashboardData,
  loadAppealDecisionMap,
  loadAppealProcessedSet,
  loadChatHistory,
  loadReadUpToMapForUser,
  loadTaskCompleteProofMap,
  loading,
  loginRainDrops,
  loginRipples,
  logout,
  maskContact,
  maskName,
  publisherAvatarUrl,
  publisherNameInitial,
  myAvatarPending,
  myAvatarUrl,
  myClaimedActiveTasks,
  myClaimedTasks,
  myHistoryTasks,
  myName,
  myPublishedActiveTasks,
  myPublishedTasks,
  myUnpaidPublishDraft,
  navGroupOpen,
  newAdminNotice,
  normalizeImageUrl,
  onAdminSidebarDblClick,
  onAvatarFileSelected,
  onCompleteProofFilesSelected,
  onLeftNavDblClick,
  onVisibilityKickCheck,
  onWindowClickClosePickupCascader,
  onWindowClickCloseUserDropdown,
  onWindowResize,
  openAdminAppealReply,
  openAppeal,
  openAuthPage,
  claimNewcomerPack,
  promoActivities,
  fetchPromoActivities,
  claimPromoActivity,
  redeemCouponCode,
  openChatWith,
  openCompleteDialog,
  openContactProfile,
  openImagePreview,
  openKickedDialog,
  openProfileSettings,
  openProofView,
  openTaskDetail,
  openWalletTaskDetail,
  pageClass,
  paging,
  parseAppealPayload,
  parseViewFromHash,
  passwordCaptchaCanvasRef: markRaw(passwordCaptchaCanvasRef),
  passwordCaptchaCode,
  passwordCaptchaInput,
  passwordForm,
  pendingAvatarUsers,
  persistAppealDecisionMap,
  persistAppealProcessedSet,
  persistReadUpToMapForUser,
  persistTaskCompleteProofMap,
  personalInfoForm,
  personalInfoTab,
  profileNotice,
  pickupBuilding,
  setPickupCascaderRootEl,
  pickupDisplayText,
  pickupManualInput,
  pickupRoom,
  pickupSelectedText,
  previewImageUrl,
  profileForm,
  publishFormHint,
  publishPaymentMethod,
  publishPayableTotal,
  publishWalletPayDisabled,
  publishPayCoupons,
  publishSelectedCouponId,
  publishCouponDiscount,
  publishCouponLoading,
  publishCouponOptionLabel,
  onPublishCouponSelected,
  showPublishPaymentSheet,
  publishPaymentSheetMode,
  closePublishPaymentSheet,
  confirmPublishPayment,
  urgentEnabled,
  urgentFee,
  urgentFeePresets,
  urgentFeeValid,
  urgentFeeError,
  urgentRiderBonus,
  urgentPushFee,
  urgentRemaining,
  urgentWeeklyLimit,
  togglePublishUrgent,
  pickUrgentFee,
  fetchUrgentQuota,
  preparePublishAlipayState,
  profileSubModule,
  walletSubModule,
  proofViewTaskId,
  publicNotices,
  publicNoticesLoading,
  publisherProfileLoading,
  randomCaptchaText,
  ratingDialogTask,
  ratingDialogTaskId,
  ratingFormMap,
  readUpToIncomingIdMap,
  refreshAuditCenter,
  refreshPasswordCaptcha,
  rejectTask,
  rejectedTasks,
  removeCompleteProofImage,
  renderRiderEchart,
  renderRiderOverviewChart,
  request,
  resetAdminNoticeEditor,
  resetFilters,
  resubmitRejectedTask,
  riderApplicationPending,
  riderApplyError,
  riderApplyForm,
  riderChartItems,
  riderChartMax,
  riderLeaderboardRows,
  riderMetricValue,
  riderOverviewDistribution,
  riderOverviewKpis,
  riderOverviewTrend,
  riderOverviewTrendDays,
  taskOverviewKpis,
  taskStatsData,
  taskStatsLoading,
  taskStatsTrendDays,
  riderRankMaxValue,
  riderRankMetric,
  riderRankMetricLabel,
  riderTop3,
  roomOptions,
  safeAllTasks,
  safeTasks,
  saveAdminNotice,
  savePersonalAvatar,
  savePersonalBaseInfo,
  saveProfile,
  scrollChatToBottom,
  selectAdminNotice,
  selectedContactProfile,
  selectedPublisherProfile,
  selectedRiderName,
  selectedRiderRow,
  selectedTask,
  sendChatMessage,
  sendTaskToCustomerService,
  isCustomerServiceChatTarget,
  myTasksForCustomerService,
  sendingTaskToCsId,
  onChatImageSelected,
  sendSmsCode,
  setStarScore,
  setSelectedDeliveryDate,
  setLatestDeliveryQuickOption,
  openDeliveryPicker,
  closeDeliveryPicker,
  setUserEnabled,
  setView,
  shouldShowChatDivider,
  showAdminNotice,
  showAuthPage,
  showContactProfileModal,
  showDropoffCascader,
  showPickupCascader,
  showProfileNotice,
  showUserDropdown,
  smsCountdown,
  starFillState,
  startAuthHeartbeat,
  startChatPolling,
  stats,
  stopAuthHeartbeat,
  stopChatPolling,
  submitAdminAppealReply,
  submitAdminWalletCredit,
  saveAdminAiConfig,
  testAdminAiConfig,
  autoAuditPendingTasks,
  setAiAutoAuditEnabled,
  setAdminFeedbackRead,
  submitAppeal,
  submitAuth,
  submitTask,
  submitTaskRating,
  submitUserFeedback,
  submitWithdrawApply,
  submitting,
  taskCompleteProofStoreKey,
  taskCompletionProofMap,
  tasks,
  temporaryChatContacts,
  toggleAdminNoticePin,
  toggleAdminNoticePublish,
  toggleAppealProcessed,
  toggleHallNoticeExpanded,
  toggleNavGroup,
  totalUnread,
  triggerAvatarFilePick,
  triggerCompleteProofFilePick,
  unreadMap,
  visibleTasks,
  walletLoading,
  walletOverview,
  walletTransactions,
  walletTypeLabel,
  walletWithdrawForm,
  walletWithdrawMin,
  walletWithdrawStep,
  walletWithdrawHardMax,
  walletMaxWithdrawable,
  walletWithdrawFeePreview,
  walletWithdrawPayoutPreview,
  onWalletWithdrawAmountInput,
  normalizeWalletWithdrawAmount,
  fillWalletMaxWithdraw,
  walletRechargeForm,
  alipayRechargeSubmitting,
  alipaySyncSubmitting,
  alipayRechargeHint,
  pendingAlipayOutTradeNo,
  pendingAlipayTaskId,
  submitAlipayRecharge,
  syncAlipayRecharge,
  syncTaskAlipayPayment,
  syncTaskBountyPaymentForTask,
  resumeTaskBountyPayment,
  abandonUnpaidPublishDraft,
  walletWithdrawHint,
  walletWithdraws,
  withdrawStatusClass,
  withdrawStatusLabel,
  withdrawSubmitting
}
const appRoot = reactive(__appBag)
const domains = createDomainStores(__appBag as Record<string, unknown>, appRoot)

// 子组件在 onMounted/bootstrapAuth 之前就会挂载，须同步从 token 恢复登录态
const bootToken = getToken()
if (bootToken && !isTokenExpired(bootToken)) {
  applyOptimisticLoginFromToken(bootToken)
}

// 子组件挂载前即监听，确保作为 opener 时能及时响应新标签页的登录态同步
window.addEventListener('message', onAuthHandoffMessage)
  return {
    appRoot,
    domains,
    actionLoadingMap,
    activeHallNoticeId,
    activeHeroPromo,
    adminAppealReply,
    adminAppealReplySubmitting,
    adminAppealReplyText,
    appealSubmitting,
    appealTask,
    appealText,
    askPublisherFirst,
    canRevealTaskContact,
    chatAvatarUrl,
    claimHeroPromo,
    claimTask,
    claimerRatingStatsMap,
    claimingHeroPromoId,
    closeAdminAppealReply,
    closeAppeal,
    closeCompleteDialog,
    closeContactProfileModal,
    closeImagePreview,
    closeKickedDialog,
    closeProofView,
    closeRealtimeToast,
    closeTaskDetail,
    completeDialogTaskId,
    completeProofFileInputRef,
    completeProofImages,
    completeProofText,
    completeTask,
    confirmTask,
    contactProfileLoading,
    currentView,
    formatDateOnly,
    formatRating,
    getTaskCompletionProof,
    goLoginFromKickedDialog,
    goToHallNotice,
    goToHeroPromo,
    hallTopNotice,
    openActiveHallNoticeLink,
    hallNoticeBannerText,
    hallNoticeCarouselIndex,
    hallTaskEmoji,
    hasMultipleHallNotices,
    hasMultipleHeroPromos,
    heroPromoCtaText,
    heroPromoIndex,
    heroPromoSlides,
    heroSideStats,
    isAdmin,
    isLoggedIn,
    isRiderApproved,
    isScanLoginRoute,
    kickedDialogOpen,
    kickedDialogText,
    maskContact,
    maskName,
    myName,
    normalizeImageUrl,
    noticeBarVisible,
    noticeMarqueeActive,
    noticeMeasureRef,
    noticeTrackRef,
    onCompleteProofFilesSelected,
    openCompleteDialog,
    openImagePreview,
    openRealtimeToastDetail,
    pageClass,
    pauseHeroPromoCarousel,
    pauseNoticeCarousel,
    previewImageUrl,
    proofViewTaskId,
    publicNotices,
    publisherAvatarUrl,
    publisherNameInitial,
    realtimeToast,
    removeCompleteProofImage,
    resumeHeroPromoCarousel,
    resumeNoticeCarousel,
    selectedContactProfile,
    selectedTask,
    showAuthPage,
    showContactProfileModal,
    stats,
    submitAdminAppealReply,
    submitAppeal,
    taskTypeLabel,
    triggerCompleteProofFilePick,
  }
}

let campusAppRuntime: ReturnType<typeof createCampusApp> | null = null

export function getCampusApp() {
  if (!campusAppRuntime) campusAppRuntime = createCampusApp()
  return campusAppRuntime
}
