<script setup lang="ts">
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { apiOrigin, authBase, mainAppUrl } from './constants/api'

const deskPublicUrl =
  typeof window !== 'undefined'
    ? `${window.location.origin}${import.meta.env.BASE_URL || '/'}`
    : 'https://api.xiaoxin1.xyz/cs/'
import type { AuthResponse, ChatContactItem, ChatMessageItem, MeResponse, RealtimeEvent, UserPublicProfile } from './types/chat'
import { request } from './utils/http'
import { normalizeImageUrl } from './utils/image'
import { clearToken, getToken, parseUsername, setToken } from './utils/token'
import {
  CS_RATING_MARKER,
  CS_SESSION_END_MARKER,
  CS_SESSION_JOIN_MARKER,
  formatSessionControlMessage,
  isSessionControlMessage,
  USER_SESSION_END_MARKER
} from './constants/quickReplies'
import {
  newQuickReplyGroup,
  newQuickReplyItem,
  type QuickReplyGroup
} from './constants/defaultQuickReplies'
import CsContextPanel from './components/CsContextPanel.vue'
import type { CsUserContextPayload } from './types/csContext'
import { parseTaskInquiryMessage, type TaskInquiryData } from './utils/taskInquiryMessage'
import {
  deriveSessionStatus,
  formatDuration,
  loadNotes,
  loadNotifyPrefs,
  loadQuickReplyGroups,
  loadSessionMetaMap,
  loadTodayStats,
  markHandledToday,
  playNotifyBeep,
  recordFirstReplyMs,
  requestDesktopNotify,
  resetQuickReplyGroups,
  saveNote,
  saveNotifyPrefs,
  saveQuickReplyGroups,
  saveSessionMetaMap,
  touchSessionMeta,
  type NotifyPrefs,
  type SessionMeta,
  type SessionStatus,
  type TodayStats
} from './utils/csDeskStorage'

type CsRatingItem = {
  id: number
  fromUsername: string
  agentUsername: string
  score: number
  tags: string[]
  comment: string
  createdAt: string
}

type CsRatingPageResponse = {
  records: CsRatingItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  averageScore: number
  totalCount: number
}

const CS_AVATAR_URL = '/logo.png'

type SessionFilter = 'all' | 'unread' | 'waiting' | 'serving' | 'ended'
const WAIT_TIMEOUT_MS = 3 * 60 * 1000
const REPLY_TIMEOUT_MS = 2 * 60 * 1000

const sessionFilter = ref<SessionFilter>('all')
const quickReplyGroups = ref<QuickReplyGroup[]>(loadQuickReplyGroups())
const quickReplyGroupTab = ref(0)
const showQuickEditor = ref(false)
const notifyPrefs = ref<NotifyPrefs>(loadNotifyPrefs())
const sessionMetaMap = ref<Record<string, SessionMeta>>(loadSessionMetaMap())
const notesMap = ref<Record<string, string>>(loadNotes())
const todayStats = ref<TodayStats>(loadTodayStats())
const nowTs = ref(Date.now())
let nowTimer: number | null = null

const userContext = ref<CsUserContextPayload | null>(null)
const userContextLoading = ref(false)
const userContextError = ref('')
const chatSearch = ref('')
const chatSearchHit = ref(0)
/** 选中会话后需点「接入会话」才能回复，避免误触快捷语直接发出导致用户端显示「已介入」 */
const sessionAcceptedMap = ref<Record<string, boolean>>({})
const acceptingSession = ref(false)

/** 用户点击「转人工」时发给客服的信令前缀 */
const HANDOFF_REQUEST_PREFIX = '[来自智能客服]'

function isHandoffRequestMessage(content: string) {
  return String(content || '').trim().startsWith(HANDOFF_REQUEST_PREFIX)
}

/** 当前选中会话是否处于可接待的转人工轮次（等待中 / 接待中） */
const activeHandoffOpen = computed(() => {
  const name = activeUser.value.trim()
  if (!name) return false
  const status = deriveSessionStatus(sessionMetaMap.value[name], unreadMap.value[name] || 0)
  return status === 'waiting' || status === 'serving'
})

const canAcceptSession = computed(() => {
  const name = activeUser.value.trim()
  if (!name) return false
  return deriveSessionStatus(sessionMetaMap.value[name], unreadMap.value[name] || 0) === 'waiting'
})

const canCompose = computed(() => {
  const name = activeUser.value.trim()
  if (!name || !sessionAcceptedMap.value[name]) return false
  return deriveSessionStatus(sessionMetaMap.value[name], unreadMap.value[name] || 0) === 'serving'
})

const canEndSession = computed(() => activeHandoffOpen.value)

const activeQuickGroup = computed(
  () => quickReplyGroups.value[quickReplyGroupTab.value] || quickReplyGroups.value[0] || null
)

const SESSION_FILTERS: Array<{ key: SessionFilter; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'unread', label: '未读' },
  { key: 'waiting', label: '等待中' },
  { key: 'serving', label: '接待中' },
  { key: 'ended', label: '已结束' }
]

const loggedIn = ref(!!getToken())
const myName = ref(getToken() ? parseUsername(getToken()!) : '')
const myAvatarUrl = ref('')
const loginForm = ref({ username: '', password: '' })
const loginError = ref('')
const loginLoading = ref(false)
const agreeLegal = ref(false)
const agreeHint = ref('')
const loginToast = ref('')
let agreeHintTimer: number | null = null
let loginToastTimer: number | null = null

function clearAgreeHint() {
  agreeHint.value = ''
  if (agreeHintTimer != null) {
    window.clearTimeout(agreeHintTimer)
    agreeHintTimer = null
  }
}

function flashAgreeHint(text: string) {
  clearAgreeHint()
  requestAnimationFrame(() => {
    agreeHint.value = text
    agreeHintTimer = window.setTimeout(() => {
      agreeHint.value = ''
      agreeHintTimer = null
    }, 3000)
  })
}

function showLoginToast(text: string) {
  loginToast.value = text
  if (loginToastTimer != null) window.clearTimeout(loginToastTimer)
  loginToastTimer = window.setTimeout(() => {
    loginToast.value = ''
    loginToastTimer = null
  }, 3200)
}

type LegalDocKey = 'terms' | 'privacy'
const legalDoc = ref<LegalDocKey | null>(null)

const LEGAL_DOCS: Record<
  LegalDocKey,
  { title: string; updated: string; paragraphs: string[] }
> = {
  terms: {
    title: '用户协议',
    updated: '2026-08-01',
    paragraphs: [
      '欢迎使用「片刻帮」校园互助平台及人工客服工作台。登录或使用本服务，即表示你已阅读、理解并同意本协议全部内容。若不同意，请停止使用。',
      '一、服务说明：客服工作台用于接待用户端转人工咨询，协助核实订单、钱包与平台规则相关问题；客服账号由管理员创建与管理。',
      '二、客服账号：你应妥善保管账号与密码，不得出借、转让或与他人共用。因个人保管不善导致的损失，由账号使用人自行承担。',
      '三、接待规范：接入后应及时回复；先安抚再办事，用语礼貌清晰。能查先查，答复前尽量核实订单、进度或钱包信息。',
      '四、信息安全：不得索要用户密码；不得泄露用户隐私、订单敏感信息或内部数据。涉及金额、退款等事项须核实后再答复。',
      '五、会话与评价：问题谈妥后再结束会话；用户可对本次服务评分，请保持态度与时效。禁止辱骂、骚扰或发送违法内容。',
      '六、违规处理：对违反本协议或影响平台安全的行为，平台有权采取警告、限制功能、停用账号等措施，并保留依法追究责任的权利。',
      '七、免责声明：因不可抗力、网络故障、第三方服务异常，或用户自身原因导致的服务中断、延误或损失，平台在法律允许范围内不承担责任。',
      '八、协议更新：我们可能根据运营与法规要求更新本协议；重大变更将通过站内提示等方式告知。你继续使用即视为接受更新后的内容。'
    ]
  },
  privacy: {
    title: '隐私政策',
    updated: '2026-08-01',
    paragraphs: [
      '「片刻帮」重视个人信息与隐私安全。本政策说明我们如何收集、使用、存储与保护相关信息。使用客服工作台即表示你理解并同意本政策。',
      '一、收集的信息：客服账号与登录凭证；接待会话与消息记录；用户为处理咨询所提供的订单/账号相关信息；设备、日志与安全风控所需的技术信息。',
      '二、使用目的：用于客服登录核验、人工接待与跟进、服务质量评估、风险控制与纠纷处理，以及在匿名或聚合后用于改进产品体验。',
      '三、共享与披露：我们不会出售个人信息。仅在完成客服支持所必需、法律法规要求，或保护用户与平台合法权益所必需时，可能依法披露。',
      '四、存储与期限：信息保存在实现服务目的所必需的期限内；账号停用或注销完成后，我们将按规则删除或匿名化处理，法律法规另有规定的除外。',
      '五、安全措施：我们采取合理的技术与管理措施保护数据。请勿向他人泄露账号密码，亦勿在会话中引导用户提供密码等高度敏感凭证。',
      '六、政策更新：本政策如有更新，我们将及时公示。你继续使用服务，即表示理解并同意更新后的隐私政策。'
    ]
  }
}

const legalDocView = computed(() => (legalDoc.value ? LEGAL_DOCS[legalDoc.value] : null))

function openLegalDoc(key: LegalDocKey) {
  legalDoc.value = key
}

function closeLegalDoc() {
  legalDoc.value = null
}

const contacts = ref<ChatContactItem[]>([])
const tempContacts = ref<string[]>([])
const search = ref('')
const activeUser = ref('')
const messages = ref<ChatMessageItem[]>([])
const draft = ref('')
const sending = ref(false)
const endingSession = ref(false)
const loadingContacts = ref(false)
const loadingMessages = ref(false)
const wsConnected = ref(false)
const unreadMap = ref<Record<string, number>>({})
const avatarBroken = ref<Record<string, boolean>>({})
const profileCache = ref<Record<string, { avatarUrl?: string; riderApproved?: boolean }>>({})

const selectedTaskInquiry = ref<TaskInquiryData | null>(null)

const showMyRatings = ref(false)
const myRatingsLoading = ref(false)
const myRatings = ref<CsRatingItem[]>([])
const myRatingsPaging = ref({ page: 1, size: 20, totalPages: 0, totalElements: 0 })
const myRatingsAverage = ref(0)
const myRatingsTotal = ref(0)

const chatBodyRef = ref<HTMLElement | null>(null)
let stompClient: Client | null = null
let pollTimer: number | null = null
let presenceTimer: number | null = null

const sortedContacts = computed(() => {
  const kw = search.value.trim().toLowerCase()
  const merged = new Map<string, ChatContactItem>()
  for (const c of contacts.value) {
    if (c.username) merged.set(c.username, c)
  }
  for (const name of tempContacts.value) {
    if (!merged.has(name)) {
      const cached = profileCache.value[name]
      merged.set(name, { username: name, avatarUrl: cached?.avatarUrl, riderApproved: cached?.riderApproved })
    }
  }
  for (const name of Object.keys(unreadMap.value)) {
    if (name && (unreadMap.value[name] || 0) > 0 && !merged.has(name)) {
      const cached = profileCache.value[name]
      merged.set(name, { username: name, avatarUrl: cached?.avatarUrl, riderApproved: cached?.riderApproved })
    }
  }
  const current = activeUser.value.trim()
  if (current && !merged.has(current)) {
    merged.set(current, { username: current })
  }
  let list = Array.from(merged.values())
  if (kw) list = list.filter((c) => c.username.toLowerCase().includes(kw))
  if (sessionFilter.value !== 'all') {
    list = list.filter((c) => {
      const unread = unreadMap.value[c.username] || 0
      const status = deriveSessionStatus(sessionMetaMap.value[c.username], unread)
      if (sessionFilter.value === 'unread') return unread > 0
      return status === sessionFilter.value
    })
  }
  return list.sort((a, b) => {
    const ua = unreadMap.value[a.username] || 0
    const ub = unreadMap.value[b.username] || 0
    if (ua !== ub) return ub - ua
    const oa = isSessionOverdue(cMeta(a.username), ua) ? 1 : 0
    const ob = isSessionOverdue(cMeta(b.username), ub) ? 1 : 0
    if (oa !== ob) return ob - oa
    return a.username.localeCompare(b.username, 'zh-CN')
  })
})

function cMeta(name: string) {
  return sessionMetaMap.value[name]
}

function isSessionOverdue(meta: SessionMeta | undefined, unread: number) {
  const status = deriveSessionStatus(meta, unread)
  if (status === 'ended') return false
  if (status === 'waiting') {
    const since = meta?.waitingSince || meta?.firstSeenAt || meta?.lastUserAt
    return !!since && nowTs.value - since >= WAIT_TIMEOUT_MS
  }
  if (status === 'serving') {
    const lastAgent = meta?.lastAgentAt || meta?.acceptedAt
    const lastUser = meta?.lastUserAt
    if (lastUser && (!lastAgent || lastUser > lastAgent)) {
      return nowTs.value - lastUser >= REPLY_TIMEOUT_MS
    }
  }
  return false
}

function sessionStatusOf(name: string): SessionStatus {
  return deriveSessionStatus(sessionMetaMap.value[name], unreadMap.value[name] || 0)
}

function sessionStatusLabel(status: SessionStatus) {
  if (status === 'waiting') return '等待中'
  if (status === 'serving') return '接待中'
  return '已结束'
}

function persistSessionMeta() {
  saveSessionMetaMap(sessionMetaMap.value)
}

function updateSessionFromMessage(from: string, to: string, content: string, createdAt?: string) {
  const agent = myName.value
  const peer = from === agent ? to : from
  if (!peer || peer === agent) return
  const ts = createdAt ? Date.parse(createdAt) : Date.now()
  const when = Number.isFinite(ts) ? ts : Date.now()
  const text = String(content || '').trim()
  let map = sessionMetaMap.value
  let prev = map[peer] || {}
  const roundOpen = !prev.endedAt && !!(prev.waitingSince || prev.acceptedAt)

  if (text.startsWith(CS_SESSION_END_MARKER) || text.startsWith(USER_SESSION_END_MARKER)) {
    // 无进行中的转人工时忽略结束信令，避免误点「结束服务」污染状态
    if (!roundOpen) {
      return
    }
    map = touchSessionMeta(map, peer, {
      endedAt: when,
      acceptedAt: undefined,
      waitingSince: undefined
    })
    sessionMetaMap.value = map
    sessionAcceptedMap.value = { ...sessionAcceptedMap.value, [peer]: false }
    persistSessionMeta()
    return
  }
  if (text.startsWith(CS_RATING_MARKER)) return

  // 仅用户「转人工」信令可开启新一轮；其它消息在结束后一律忽略
  if (isHandoffRequestMessage(text)) {
    map = touchSessionMeta(map, peer, {
      endedAt: undefined,
      acceptedAt: undefined,
      firstSeenAt: when,
      waitingSince: when,
      lastUserAt: when,
      lastAgentAt: undefined
    })
    sessionAcceptedMap.value = { ...sessionAcceptedMap.value, [peer]: false }
    sessionMetaMap.value = map
    persistSessionMeta()
    return
  }

  if (prev.endedAt || (!roundOpen && !prev.acceptedAt && !prev.waitingSince)) {
    // 不在转人工轮次内：不改会话态（防止客服误接入把已结束打成接待中）
    return
  }

  if (from !== agent) {
    map = touchSessionMeta(map, peer, {
      lastUserAt: when
    })
  } else {
    const isFirstAccept = !prev.acceptedAt
    map = touchSessionMeta(map, peer, {
      acceptedAt: prev.acceptedAt || when,
      lastAgentAt: when,
      waitingSince: undefined,
      endedAt: undefined
    })
    sessionAcceptedMap.value = { ...sessionAcceptedMap.value, [peer]: true }
    if (isFirstAccept && prev.lastUserAt) {
      todayStats.value = recordFirstReplyMs(when - prev.lastUserAt)
    } else if (isFirstAccept && prev.firstSeenAt) {
      todayStats.value = recordFirstReplyMs(when - prev.firstSeenAt)
    }
    todayStats.value = markHandledToday(peer)
  }
  sessionMetaMap.value = map
  persistSessionMeta()
}

/**
 * 从完整历史重建会话态。
 * 规则：只有用户发出的「转人工」信令开启一轮；结束后必须再次转人工才能等待/接入。
 */
function rebuildSessionMetaFromHistory(peer: string, rows: ChatMessageItem[]) {
  const agent = myName.value
  const name = peer.trim()
  if (!name) return

  let endedAt: number | undefined
  let acceptedAt: number | undefined
  let firstSeenAt: number | undefined
  let waitingSince: number | undefined
  let lastUserAt: number | undefined
  let lastAgentAt: number | undefined
  let roundOpen = false

  for (const m of rows) {
    const ts = Date.parse(String(m.createdAt || ''))
    const when = Number.isFinite(ts) ? ts : Date.now()
    const text = String(m.content || '').trim()

    if (text.startsWith(CS_SESSION_END_MARKER) || text.startsWith(USER_SESSION_END_MARKER)) {
      if (!roundOpen) continue
      endedAt = when
      acceptedAt = undefined
      waitingSince = undefined
      roundOpen = false
      continue
    }
    if (text.startsWith(CS_RATING_MARKER)) continue

    if (isHandoffRequestMessage(text)) {
      endedAt = undefined
      acceptedAt = undefined
      firstSeenAt = when
      waitingSince = when
      lastUserAt = when
      lastAgentAt = undefined
      roundOpen = true
      continue
    }

    if (!roundOpen) continue

    if (m.fromUsername === agent) {
      if (!acceptedAt) acceptedAt = when
      lastAgentAt = when
      waitingSince = undefined
    } else {
      lastUserAt = when
    }
  }

  const prev = sessionMetaMap.value[name] || {}
  const manualAccepted = !!sessionAcceptedMap.value[name]
  // 仅在本轮仍开放（用户已转人工且未结束）时保留「刚点接入」
  if (manualAccepted && roundOpen && !endedAt) {
    acceptedAt = acceptedAt || prev.acceptedAt || Date.now()
    waitingSince = undefined
  }

  const next: SessionMeta = {
    firstSeenAt,
    acceptedAt,
    lastAgentAt,
    lastUserAt,
    endedAt,
    waitingSince
  }
  ;(Object.keys(next) as (keyof SessionMeta)[]).forEach((key) => {
    if (next[key] === undefined) delete next[key]
  })
  sessionMetaMap.value = { ...sessionMetaMap.value, [name]: next }
  persistSessionMeta()

  if (acceptedAt && !endedAt && roundOpen) {
    sessionAcceptedMap.value = { ...sessionAcceptedMap.value, [name]: true }
  } else {
    sessionAcceptedMap.value = { ...sessionAcceptedMap.value, [name]: false }
  }
}

const handoffReason = computed(() => {
  // 取当前轮次（最后一次转人工之后）的原因
  let reason = ''
  for (const m of messages.value) {
    const text = String(m.content || '').trim()
    if (isHandoffRequestMessage(text)) {
      reason = text.replace(/^\[来自智能客服\]\s*/, '').trim() || '用户请求人工客服协助'
    } else if (
      text.startsWith(CS_SESSION_END_MARKER) ||
      text.startsWith(USER_SESSION_END_MARKER)
    ) {
      reason = ''
    }
  }
  return reason
})

const chatOrdersFromMessages = computed(() => {
  const seen = new Set<string>()
  const list: TaskInquiryData[] = []
  for (const m of messages.value) {
    const inquiry = parseTaskInquiryMessage(m.content)
    if (!inquiry || seen.has(inquiry.taskId)) continue
    seen.add(inquiry.taskId)
    list.push(inquiry)
  }
  return list.slice(0, 6)
})

const activeNote = computed({
  get: () => notesMap.value[activeUser.value] || '',
  set: (v: string) => {
    const name = activeUser.value.trim()
    if (!name) return
    notesMap.value = { ...notesMap.value, [name]: v }
    saveNote(name, v)
  }
})

const activeWaitLabel = computed(() => {
  const meta = sessionMetaMap.value[activeUser.value]
  if (!meta || meta.endedAt) return '—'
  const since = meta.waitingSince || meta.firstSeenAt
  if (!since) return '—'
  if (meta.acceptedAt) return formatDuration(meta.acceptedAt - since)
  return formatDuration(nowTs.value - since)
})

const activeServeLabel = computed(() => {
  const meta = sessionMetaMap.value[activeUser.value]
  if (!meta?.acceptedAt) return '未接入'
  if (meta.endedAt) return formatDuration(meta.endedAt - meta.acceptedAt)
  return formatDuration(nowTs.value - meta.acceptedAt)
})

const activeOverdue = computed(() =>
  isSessionOverdue(sessionMetaMap.value[activeUser.value], unreadMap.value[activeUser.value] || 0)
)

const todayOverview = computed(() => {
  const stats = todayStats.value
  const handled = stats.handled.length
  const avg =
    stats.firstReplyMs.length > 0
      ? Math.round(stats.firstReplyMs.reduce((a, b) => a + b, 0) / stats.firstReplyMs.length)
      : null
  return {
    handled,
    avgLabel: avg == null ? '—' : formatDuration(avg),
    scoreLabel: myRatingsTotal.value > 0 ? `${myRatingsAverage.value.toFixed(1)}★` : '—'
  }
})

const filteredChatRows = computed(() => {
  const kw = chatSearch.value.trim().toLowerCase()
  if (!kw) return chatRows.value
  return chatRows.value.filter((row) => {
    if (row.kind === 'date') return false
    return String(row.item.content || '')
      .toLowerCase()
      .includes(kw)
  })
})

async function loadUserContext(username: string) {
  const name = username.trim()
  if (!name) {
    userContext.value = null
    userContextError.value = ''
    return
  }
  userContextLoading.value = true
  userContextError.value = ''
  try {
    const data = await request<CsUserContextPayload & { message?: string }>(
      `${apiOrigin}/api/support/cs/user-context?username=${encodeURIComponent(name)}`
    )
    userContext.value = data
  } catch (err) {
    userContext.value = null
    userContextError.value = (err as Error).message || '加载用户上下文失败'
  } finally {
    userContextLoading.value = false
  }
}

function persistQuickReplies() {
  saveQuickReplyGroups(quickReplyGroups.value)
}

function openQuickEditor() {
  showQuickEditor.value = true
}

function closeQuickEditor() {
  persistQuickReplies()
  showQuickEditor.value = false
}

function addQuickGroup() {
  quickReplyGroups.value = [...quickReplyGroups.value, newQuickReplyGroup()]
  quickReplyGroupTab.value = quickReplyGroups.value.length - 1
  persistQuickReplies()
}

function addQuickItem() {
  const g = activeQuickGroup.value
  if (!g) return
  g.items.push(newQuickReplyItem())
  persistQuickReplies()
}

function removeQuickItem(groupId: string, itemId: string) {
  quickReplyGroups.value = quickReplyGroups.value.map((g) =>
    g.id === groupId ? { ...g, items: g.items.filter((i) => i.id !== itemId) } : g
  )
  persistQuickReplies()
}

function restoreDefaultQuickReplies() {
  if (!window.confirm('恢复默认快捷话术？当前自定义内容将被覆盖。')) return
  quickReplyGroups.value = resetQuickReplyGroups()
  quickReplyGroupTab.value = 0
}

function toggleNotifySound() {
  notifyPrefs.value = { ...notifyPrefs.value, sound: !notifyPrefs.value.sound }
  saveNotifyPrefs(notifyPrefs.value)
}

async function toggleNotifyDesktop() {
  const next = !notifyPrefs.value.desktop
  if (next && typeof Notification !== 'undefined' && Notification.permission === 'default') {
    await Notification.requestPermission()
  }
  notifyPrefs.value = { ...notifyPrefs.value, desktop: next }
  saveNotifyPrefs(notifyPrefs.value)
}

function jumpChatSearch(dir: 1 | -1) {
  const rows = filteredChatRows.value.filter((r) => r.kind === 'msg')
  if (!rows.length) return
  chatSearchHit.value = (chatSearchHit.value + dir + rows.length) % rows.length
  const key = rows[chatSearchHit.value]?.key
  if (!key) return
  void nextTick(() => {
    document.querySelector(`[data-msg-key="${key}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  })
}

const totalUnread = computed(() =>
  Object.values(unreadMap.value).reduce((sum, n) => sum + (n || 0), 0)
)

const unreadContacts = computed(() =>
  sortedContacts.value.filter((c) => (unreadMap.value[c.username] || 0) > 0)
)

const activeContact = computed(() =>
  sortedContacts.value.find((c) => c.username === activeUser.value) ?? null
)

const showMsgNotify = ref(false)
const notifyPulse = ref(false)
type MsgToast = { id: number; from: string; preview: string }
const msgToasts = ref<MsgToast[]>([])
let msgToastSeq = 0
let notifyPulseTimer: number | null = null

watch(totalUnread, (n) => {
  document.title = n > 0 ? `(${n}) 客服工作台` : '客服工作台'
})

function previewText(raw: string) {
  const text = String(raw || '').replace(/\s+/g, ' ').trim()
  if (!text) return '发来一条新消息'
  if (isSessionControlMessage(text)) return formatSessionControlMessage(text)
  if (text.startsWith('[TASK_INQUIRY]')) return '发送了订单咨询'
  if (text.length > 36) return `${text.slice(0, 36)}…`
  return text
}

function pushMsgToast(from: string, content: string) {
  const id = ++msgToastSeq
  const preview = previewText(content)
  msgToasts.value = [{ id, from, preview }, ...msgToasts.value].slice(0, 3)
  notifyPulse.value = true
  if (notifyPulseTimer != null) window.clearTimeout(notifyPulseTimer)
  notifyPulseTimer = window.setTimeout(() => {
    notifyPulse.value = false
    notifyPulseTimer = null
  }, 1600)
  window.setTimeout(() => {
    msgToasts.value = msgToasts.value.filter((t) => t.id !== id)
  }, 5200)
  if (notifyPrefs.value.sound) playNotifyBeep()
  if (notifyPrefs.value.desktop) void requestDesktopNotify(`${from} 发来消息`, preview)
}

function toggleMsgNotify() {
  showMsgNotify.value = !showMsgNotify.value
}

function closeMsgNotify() {
  showMsgNotify.value = false
}

async function openUnreadContact(username: string) {
  closeMsgNotify()
  dismissMsgToastByUser(username)
  await selectContact(username)
}

function dismissMsgToastByUser(username: string) {
  msgToasts.value = msgToasts.value.filter((t) => t.from !== username)
}

type ChatRow =
  | { kind: 'date'; label: string; key: string }
  | { kind: 'msg'; item: ChatMessageItem; key: string }

const chatRows = computed<ChatRow[]>(() => {
  const rows: ChatRow[] = []
  let lastDateKey = ''
  for (const msg of messages.value) {
    const d = new Date(msg.createdAt)
    const dateKey = Number.isNaN(d.getTime())
      ? String(msg.createdAt)
      : `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
    if (dateKey !== lastDateKey) {
      rows.push({ kind: 'date', label: formatDateLabel(msg.createdAt), key: `d-${dateKey}` })
      lastDateKey = dateKey
    }
    rows.push({ kind: 'msg', item: msg, key: `m-${msg.id}` })
  }
  return rows
})

function formatDateLabel(raw: string) {
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  if (sameDay) return '今天'
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  const isYesterday =
    d.getFullYear() === yesterday.getFullYear() &&
    d.getMonth() === yesterday.getMonth() &&
    d.getDate() === yesterday.getDate()
  if (isYesterday) return '昨天'
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}

function isSystemMessage(content: string) {
  const text = String(content || '').trim()
  return text.startsWith('[来自智能客服]') || isSessionControlMessage(text)
}

function displaySystemMessage(content: string) {
  const text = String(content || '').trim()
  if (isSessionControlMessage(text)) return formatSessionControlMessage(text)
  return text
}

function isRatingMessage(content: string) {
  return String(content || '').trim().startsWith(CS_RATING_MARKER)
}

function chatTaskInquiry(content: string) {
  return parseTaskInquiryMessage(content)
}

function openTaskInquiry(inquiry: TaskInquiryData) {
  selectedTaskInquiry.value = inquiry
}

function closeTaskInquiry() {
  selectedTaskInquiry.value = null
}

async function fetchMyRatings(page = 1) {
  if (!getToken()) return
  myRatingsLoading.value = true
  try {
    const params = new URLSearchParams()
    params.set('page', String(Math.max(1, page)))
    params.set('size', String(myRatingsPaging.value.size))
    const data = await request<CsRatingPageResponse>(
      `${apiOrigin}/api/support/ratings/mine?${params.toString()}`
    )
    myRatings.value = data.records || []
    myRatingsPaging.value = {
      page: data.page,
      size: data.size,
      totalPages: data.totalPages,
      totalElements: data.totalElements
    }
    myRatingsAverage.value = Number(data.averageScore || 0)
    myRatingsTotal.value = Number(data.totalCount || 0)
  } catch (err) {
    window.alert((err as Error).message || '加载评价失败')
  } finally {
    myRatingsLoading.value = false
  }
}

async function openMyRatings() {
  showMyRatings.value = true
  await fetchMyRatings(1)
}

function closeMyRatings() {
  showMyRatings.value = false
}

function scoreStars(score?: number) {
  const n = Math.max(0, Math.min(5, Math.round(Number(score) || 0)))
  return '★'.repeat(n) + '☆'.repeat(5 - n)
}

function formatRatingTime(raw?: string) {
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return String(raw)
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}


function avatarStyle(name: string) {
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = (hash + name.charCodeAt(i) * (i + 1)) % 360
  return {
    background: `linear-gradient(135deg, hsl(${hash} 72% 58%), hsl(${(hash + 36) % 360} 68% 48%))`
  }
}

function avatarInitial(name: string) {
  const text = String(name || '').trim()
  if (!text) return '?'
  if (text === myName.value) return '客'
  return text.slice(0, 1).toUpperCase()
}

function avatarSrc(username: string) {
  const name = String(username || '').trim()
  if (!name || avatarBroken.value[name]) return ''
  if (name === myName.value) {
    const raw = myAvatarUrl.value || CS_AVATAR_URL
    return raw ? normalizeImageUrl(raw) : ''
  }
  const fromContact = contacts.value.find((c) => c.username === name)?.avatarUrl
  const fromCache = profileCache.value[name]?.avatarUrl
  const raw = fromContact || fromCache || ''
  return raw ? normalizeImageUrl(raw) : ''
}

function onAvatarError(username: string) {
  const name = String(username || '').trim()
  if (!name) return
  avatarBroken.value[name] = true
}

async function ensureContactProfile(username: string) {
  const name = username.trim()
  if (!name || name === myName.value) return
  const cached = profileCache.value[name]?.avatarUrl
  const inList = contacts.value.find((c) => c.username === name)?.avatarUrl
  if (String(cached || inList || '').trim()) return

  try {
    const profile = await request<UserPublicProfile>(
      `${authBase}/users/${encodeURIComponent(name)}/public-profile`
    )
    if (!profile) return
    profileCache.value[name] = {
      avatarUrl: profile.avatarUrl || profileCache.value[name]?.avatarUrl,
      riderApproved: profile.riderApproved
    }
    const idx = contacts.value.findIndex((c) => c.username === name)
    if (idx >= 0) {
      contacts.value[idx] = {
        ...contacts.value[idx],
        avatarUrl: profile.avatarUrl || contacts.value[idx].avatarUrl,
        riderApproved: profile.riderApproved ?? contacts.value[idx].riderApproved
      }
    }
  } catch {
    // ignore
  }
}

function formatTime(raw: string) {
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  if (sameDay) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

async function scrollToBottom() {
  await nextTick()
  const el = chatBodyRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function hydrateMe() {
  const token = getToken()
  if (!token) {
    loggedIn.value = false
    return
  }
  try {
    const me = await request<MeResponse>(`${authBase}/me`)
    if (!me.customerServiceStaff) {
      clearToken()
      loggedIn.value = false
      myName.value = ''
      loginError.value = '该账号不是人工客服账号'
      return
    }
    myName.value = me.username
    myAvatarUrl.value = me.avatarUrl || CS_AVATAR_URL
    loggedIn.value = true
  } catch {
    clearToken()
    loggedIn.value = false
    myName.value = ''
  }
}

async function login() {
  loginError.value = ''
  if (!agreeLegal.value) {
    flashAgreeHint('请先勾选同意用户协议与隐私政策')
    showLoginToast('请先勾选同意用户协议与隐私政策')
    return
  }
  clearAgreeHint()
  loginLoading.value = true
  try {
    const res = await fetch(`${authBase}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: loginForm.value.username.trim(),
        password: loginForm.value.password,
        csConsole: true
      })
    })
    const data = (await res.json().catch(() => ({}))) as AuthResponse & { message?: string }
    if (!res.ok || !data.token) {
      throw new Error(data.message || '登录失败')
    }
    setToken(data.token)
    const me = await request<MeResponse>(`${authBase}/me`)
    if (!me.customerServiceStaff) {
      clearToken()
      throw new Error('该账号不是人工客服账号，请使用管理员创建的客服账号登录')
    }
    myName.value = me.username || data.username
    myAvatarUrl.value = me.avatarUrl || CS_AVATAR_URL
    loggedIn.value = true
    await bootstrapDesk()
  } catch (err) {
    loginError.value = (err as Error).message || '登录失败'
  } finally {
    loginLoading.value = false
  }
}

function logout() {
  stopRealtime()
  stopPolling()
  stopPresenceHeartbeat()
  clearToken()
  loggedIn.value = false
  myName.value = ''
  myAvatarUrl.value = ''
  contacts.value = []
  tempContacts.value = []
  activeUser.value = ''
  messages.value = []
  unreadMap.value = {}
  avatarBroken.value = {}
  profileCache.value = {}
  sessionAcceptedMap.value = {}
  draft.value = ''
  showMsgNotify.value = false
  msgToasts.value = []
  notifyPulse.value = false
  document.title = '客服工作台'
}

function ensureContactInList(username: string) {
  const name = username.trim()
  if (!name || name === myName.value) return
  if (contacts.value.some((c) => c.username === name)) return
  if (!tempContacts.value.includes(name)) {
    tempContacts.value = [...tempContacts.value, name]
  }
  void ensureContactProfile(name)
}

async function fetchContacts() {
  loadingContacts.value = true
  try {
    const [list, temps] = await Promise.all([
      request<ChatContactItem[]>(`${apiOrigin}/api/chat/contacts`),
      request<string[]>(`${apiOrigin}/api/chat/temporary-contacts`)
    ])
    contacts.value = list
    const known = new Set(list.map((c) => c.username))
    const mergedTemps = new Set<string>([...temps, ...tempContacts.value])
    for (const name of Object.keys(unreadMap.value)) {
      if (name) mergedTemps.add(name)
    }
    const current = activeUser.value.trim()
    if (current) mergedTemps.add(current)
    tempContacts.value = [...mergedTemps].filter(
      (name) => name && !known.has(name) && name !== myName.value
    )
    await Promise.all(
      contacts.value.filter((c) => !String(c.avatarUrl || '').trim()).map((c) => ensureContactProfile(c.username))
    )
  } finally {
    loadingContacts.value = false
  }
}

async function loadHistory(target: string, scroll = true) {
  if (!target) return
  loadingMessages.value = true
  try {
    messages.value = await request<ChatMessageItem[]>(
      `${apiOrigin}/api/chat/history?targetUser=${encodeURIComponent(target)}`
    )
    rebuildSessionMetaFromHistory(target, messages.value)
    unreadMap.value[target] = 0
    if (scroll) await scrollToBottom()
  } finally {
    loadingMessages.value = false
  }
}

async function selectContact(username: string) {
  activeUser.value = username
  chatSearch.value = ''
  chatSearchHit.value = 0
  draft.value = ''
  dismissMsgToastByUser(username)
  closeMsgNotify()
  // 会话态完全由历史消息重建，选中联系人时不要伪造「等待中」
  todayStats.value = markHandledToday(username)
  await ensureContactProfile(username)
  await Promise.all([loadHistory(username), loadUserContext(username)])
}

async function acceptCurrentSession() {
  const name = activeUser.value.trim()
  if (!name || acceptingSession.value || sending.value) return
  if (!canAcceptSession.value) {
    window.alert('用户尚未转人工，或本轮服务已结束。请等待用户再次点击「转人工」。')
    return
  }
  acceptingSession.value = true
  try {
    sessionAcceptedMap.value = { ...sessionAcceptedMap.value, [name]: true }
    sessionMetaMap.value = touchSessionMeta(sessionMetaMap.value, name, {
      acceptedAt: Date.now(),
      waitingSince: undefined,
      endedAt: undefined
    })
    persistSessionMeta()
    todayStats.value = markHandledToday(name)

    // 发接入信令：用户端据此切到「已介入」，客服台状态也不会被历史重放打回等待中
    const content = `${CS_SESSION_JOIN_MARKER} 您好，我是片刻帮人工客服，已接入为您服务，请问有什么可以帮您？`
    const sent = await request<ChatMessageItem>(`${apiOrigin}/api/chat/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ toUsername: name, content })
    })
    messages.value.push(sent)
    updateSessionFromMessage(sent.fromUsername, sent.toUsername, sent.content, sent.createdAt)
    await scrollToBottom()
    await fetchContacts()
  } catch (err) {
    // 发送失败时回滚接入态，避免「本地接待中、用户未转人工」
    sessionAcceptedMap.value = { ...sessionAcceptedMap.value, [name]: false }
    sessionMetaMap.value = touchSessionMeta(sessionMetaMap.value, name, {
      acceptedAt: undefined,
      waitingSince: Date.now()
    })
    persistSessionMeta()
    window.alert((err as Error).message || '接入通知发送失败，请重试')
  } finally {
    acceptingSession.value = false
  }
}

async function sendMessage() {
  const text = draft.value.trim()
  const target = activeUser.value.trim()
  if (!text || !target || sending.value) return
  if (!canCompose.value) {
    window.alert('当前没有进行中的人工接待，请等待用户转人工并接入后再回复')
    return
  }
  sending.value = true
  try {
    const sent = await request<ChatMessageItem>(`${apiOrigin}/api/chat/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ toUsername: target, content: text })
    })
    messages.value.push(sent)
    updateSessionFromMessage(sent.fromUsername, sent.toUsername, sent.content, sent.createdAt)
    draft.value = ''
    await scrollToBottom()
    await fetchContacts()
  } catch (err) {
    window.alert((err as Error).message || '发送失败')
  } finally {
    sending.value = false
  }
}

async function endCurrentSession() {
  const target = activeUser.value.trim()
  if (!target || endingSession.value || sending.value) return
  if (!canEndSession.value) {
    window.alert('当前没有进行中的转人工会话，无需结束服务。')
    return
  }
  const ok = window.confirm(`确认结束与「${target}」的人工服务？\n结束后用户将返回片刻小新。`)
  if (!ok) return
  endingSession.value = true
  try {
    const content = `${CS_SESSION_END_MARKER} 客服已结束本次人工服务，您可继续咨询片刻小新。`
    const sent = await request<ChatMessageItem>(`${apiOrigin}/api/chat/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ toUsername: target, content })
    })
    messages.value.push(sent)
    updateSessionFromMessage(sent.fromUsername, sent.toUsername, sent.content, sent.createdAt)
    draft.value = ''
    await scrollToBottom()
    await fetchContacts()
    window.setTimeout(() => {
      if (activeUser.value === target) {
        activeUser.value = ''
        messages.value = []
        userContext.value = null
      }
    }, 800)
  } catch (err) {
    window.alert((err as Error).message || '结束服务失败，请重试')
  } finally {
    endingSession.value = false
  }
}

function onDraftKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    if (!canCompose.value) return
    void sendMessage()
  }
}

function applyQuickReply(text: string) {
  if (!canCompose.value) {
    window.alert('请先等待用户转人工并接入会话后，再使用快捷语')
    return
  }
  draft.value = String(text || '').trim()
}

function handleRealtime(raw: string) {
  let evt: RealtimeEvent
  try {
    evt = JSON.parse(raw) as RealtimeEvent
  } catch {
    return
  }
  if (evt.type !== 'CHAT_MESSAGE' || !evt.data) return
  const from = String(evt.data.fromUsername || '').trim()
  const to = String(evt.data.toUsername || '').trim()
  if (!from || !to) return
  const incoming = String(evt.data.content || '')
  const createdAt = String(evt.data.createdAt || '')
  updateSessionFromMessage(from, to, incoming, createdAt)
  if (from === myName.value) {
    ensureContactInList(to)
    void fetchContacts()
    if (activeUser.value === to) void loadHistory(to, true)
    return
  }
  ensureContactInList(from)
  void ensureContactProfile(from)
  void fetchContacts()
  const userEnded = incoming.trim().startsWith(USER_SESSION_END_MARKER)
  const userRated = incoming.trim().startsWith(CS_RATING_MARKER)
  if (activeUser.value === from) {
    void loadHistory(from, true)
    if (userEnded) {
      pushMsgToast(from, '用户已结束人工服务')
    } else if (userRated) {
      pushMsgToast(from, formatSessionControlMessage(incoming))
    }
  } else {
    unreadMap.value[from] = (unreadMap.value[from] || 0) + 1
    pushMsgToast(
      from,
      userEnded ? '用户已结束人工服务' : userRated ? formatSessionControlMessage(incoming) : incoming
    )
  }
}

function startRealtime() {
  const token = getToken()
  if (!token) return
  stopRealtime()
  const endpoint = `${apiOrigin || ''}/ws?token=${encodeURIComponent(token)}`
  const client = new Client({
    webSocketFactory: () => new SockJS(endpoint),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {}
  })
  client.onConnect = () => {
    wsConnected.value = true
    stopPolling()
    client.subscribe('/user/queue/events', (msg) => handleRealtime(String(msg.body || '')))
  }
  client.onWebSocketClose = () => {
    wsConnected.value = false
    startPolling()
  }
  client.onStompError = () => {
    wsConnected.value = false
    startPolling()
  }
  stompClient = client
  client.activate()
}

function stopRealtime() {
  wsConnected.value = false
  if (stompClient) {
    try {
      stompClient.deactivate()
    } catch {
      // ignore
    }
    stompClient = null
  }
}

/** WebSocket 断开时靠轮询补会话态；否则转人工后会一直停在「已结束」 */
async function syncSessionMetaForContacts() {
  const names = [
    ...new Set([
      ...contacts.value.map((c) => c.username),
      ...tempContacts.value,
      activeUser.value.trim()
    ])
  ].filter((n) => n && n !== myName.value)
  const batch = names.slice(0, 12)
  await Promise.all(
    batch.map(async (name) => {
      try {
        const rows = await request<ChatMessageItem[]>(
          `${apiOrigin}/api/chat/history?targetUser=${encodeURIComponent(name)}`
        )
        rebuildSessionMetaFromHistory(name, rows)
        if (activeUser.value === name) {
          messages.value = rows
        }
      } catch {
        // ignore single-peer failures
      }
    })
  )
}

function startPolling() {
  if (pollTimer != null) return
  pollTimer = window.setInterval(() => {
    void (async () => {
      await fetchContacts()
      await syncSessionMetaForContacts()
      if (activeUser.value) void loadHistory(activeUser.value, false)
    })()
  }, 5000)
}

function stopPolling() {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPresenceHeartbeat() {
  if (presenceTimer != null) return
  const tick = () => {
    if (!getToken()) return
    void request<MeResponse>(`${authBase}/me`).catch(() => {})
  }
  tick()
  presenceTimer = window.setInterval(tick, 45_000)
}

function stopPresenceHeartbeat() {
  if (presenceTimer != null) {
    window.clearInterval(presenceTimer)
    presenceTimer = null
  }
}

async function bootstrapDesk() {
  todayStats.value = loadTodayStats()
  await fetchContacts()
  void fetchMyRatings(1)
  startRealtime()
  startPresenceHeartbeat()
  if (!wsConnected.value) startPolling()
  if (nowTimer != null) window.clearInterval(nowTimer)
  nowTimer = window.setInterval(() => {
    nowTs.value = Date.now()
  }, 15000)
}

watch(
  () => messages.value.length,
  async () => {
    if (activeUser.value) await scrollToBottom()
  }
)

watch(loggedIn, (ok) => {
  if (ok) void bootstrapDesk()
  else {
    stopRealtime()
    stopPolling()
    stopPresenceHeartbeat()
  }
})

onMounted(async () => {
  await hydrateMe()
  if (loggedIn.value) await bootstrapDesk()
})

onUnmounted(() => {
  stopRealtime()
  stopPolling()
  stopPresenceHeartbeat()
  clearAgreeHint()
  if (loginToastTimer != null) window.clearTimeout(loginToastTimer)
  if (nowTimer != null) {
    window.clearInterval(nowTimer)
    nowTimer = null
  }
})
</script>

<template>
  <div class="desk-root">
    <!-- 登录 -->
    <section v-if="!loggedIn" class="login-scene">
      <div class="login-visual">
        <div class="login-decor" aria-hidden="true">
          <div class="login-grid" />
          <div class="login-orb login-orb-a" />
          <div class="login-orb login-orb-b" />
          <div class="login-orb login-orb-c" />
          <div class="login-glow login-glow-a" />
          <div class="login-glow login-glow-b" />
          <span class="login-float login-float-a">转人工接待</span>
          <span class="login-float login-float-b">会话队列</span>
          <span class="login-float login-float-c">服务评价</span>
        </div>
        <div class="login-visual-stack">
          <div class="login-brand-card">
            <img class="login-brand-logo" src="/logo.png" alt="片刻帮" />
            <p>片刻帮 · 人工客服</p>
            <small>在线咨询接待工作台</small>
          </div>
          <aside class="login-tips" aria-label="客服接待须知">
            <h2>接待须知</h2>
            <ul>
              <li>
                <strong>先安抚再办事</strong>
                <span>可开场：「您好，我是片刻帮客服，请问遇到什么问题了？」</span>
              </li>
              <li>
                <strong>语气礼貌清晰</strong>
                <span>不推诿、不指责，用短句说明原因与下一步</span>
              </li>
              <li>
                <strong>能查先查</strong>
                <span>订单、钱包、进度能核实的先核实后再答复</span>
              </li>
              <li>
                <strong>收尾有交代</strong>
                <span>结束前复述结论，并确认「还有其他需要帮忙的吗？」</span>
              </li>
            </ul>
          </aside>
        </div>
      </div>
      <div class="login-panel">
        <span class="login-tag">5174 工作台</span>
        <h1>欢迎回来</h1>
        <p class="login-desc">使用管理员创建的客服账号登录，接待来自用户端的转人工咨询。</p>
        <form class="login-form" @submit.prevent="login">
          <label class="field">
            <span>客服账号</span>
            <input
              v-model="loginForm.username"
              autocomplete="username"
              placeholder="账号 ID 或登录名"
            />
          </label>
          <label class="field">
            <span>密码</span>
            <input
              v-model="loginForm.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
            />
          </label>
          <p v-if="loginError" class="field-error">{{ loginError }}</p>
          <button class="btn-brand" type="submit" :disabled="loginLoading">
            {{ loginLoading ? '登录中…' : '进入工作台' }}
          </button>
        </form>
        <label
          class="login-agree-row"
          :class="{ warn: !!agreeHint && !agreeLegal, shake: !!agreeHint && !agreeLegal }"
        >
          <input
            v-model="agreeLegal"
            type="checkbox"
            id="cs-agree-legal"
            @change="clearAgreeHint()"
          />
          <span>
            我已阅读并同意
            <button type="button" class="login-foot-link" @click.stop="openLegalDoc('terms')">用户协议</button>
            与
            <button type="button" class="login-foot-link" @click.stop="openLegalDoc('privacy')">隐私政策</button>
          </span>
        </label>
        <p v-if="agreeHint && !agreeLegal" class="login-agree-hint">{{ agreeHint }}</p>
        <ul class="login-highlights">
          <li>
            <i class="login-hi-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path
                  d="M8 10h8M8 14h5M7 4h10a2 2 0 0 1 2 2v14l-4-2-3 2-3-2-4 2V6a2 2 0 0 1 2-2Z"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linejoin="round"
                />
              </svg>
            </i>
            <span>接待用户转人工咨询</span>
          </li>
          <li>
            <i class="login-hi-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path d="M4 7h16M4 12h10M4 17h13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              </svg>
            </i>
            <span>多会话排队与跟进</span>
          </li>
          <li>
            <i class="login-hi-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path
                  d="M12 3l2.4 4.9 5.4.8-3.9 3.8.9 5.4L12 15.8 7.2 18l.9-5.4L4.2 8.7l5.4-.8L12 3Z"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linejoin="round"
                />
              </svg>
            </i>
            <span>查看客服服务评价</span>
          </li>
        </ul>
        <a class="login-link" :href="mainAppUrl" target="_blank" rel="noopener">前往用户端 →</a>
      </div>

      <div
        v-if="legalDocView"
        class="legal-mask"
        role="dialog"
        aria-modal="true"
        aria-labelledby="cs-legal-doc-title"
        @click.self="closeLegalDoc"
      >
        <section class="legal-sheet">
          <header class="legal-sheet-head">
            <div>
              <h2 id="cs-legal-doc-title">{{ legalDocView.title }}</h2>
              <p>更新日期 {{ legalDocView.updated }}</p>
            </div>
            <button type="button" class="legal-close" aria-label="关闭" @click="closeLegalDoc">
              <svg viewBox="0 0 14 14" width="14" height="14" fill="none" aria-hidden="true">
                <path
                  d="M3.5 3.5l7 7M10.5 3.5l-7 7"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                />
              </svg>
            </button>
          </header>
          <div class="legal-sheet-body">
            <p v-for="(para, idx) in legalDocView.paragraphs" :key="`cs-legal-p-${idx}`">{{ para }}</p>
          </div>
          <footer class="legal-sheet-foot">
            <button type="button" class="btn-brand" @click="closeLegalDoc">我知道了</button>
          </footer>
        </section>
      </div>

      <div v-if="loginToast" class="login-toast" role="status">{{ loginToast }}</div>
    </section>

    <!-- 工作台 -->
    <section v-else class="workbench">
      <aside class="session-panel">
        <header class="agent-bar">
          <div class="agent-profile">
            <span class="agent-avatar avatar-slot" :style="!avatarSrc(myName) ? avatarStyle(myName) : undefined">
              <img v-if="avatarSrc(myName)" :src="avatarSrc(myName)" :alt="myName" @error="onAvatarError(myName)" />
              <template v-else>{{ avatarInitial(myName) }}</template>
            </span>
            <div class="agent-info">
              <strong>{{ myName }}</strong>
              <span class="agent-status" :class="{ live: wsConnected }">
                <i />{{ wsConnected ? '实时在线' : '轮询同步' }}
              </span>
            </div>
          </div>
          <div class="agent-tools">
            <span v-if="totalUnread > 0" class="badge-total">{{ totalUnread > 99 ? '99+' : totalUnread }}</span>
            <button type="button" class="btn-text" @click="openMyRatings">我的评价</button>
            <button type="button" class="btn-text" @click="logout">退出</button>
          </div>
          <div class="today-overview" title="今日工作概览（本机统计）">
            <span><em>{{ todayOverview.handled }}</em>接待</span>
            <span><em>{{ todayOverview.avgLabel }}</em>均响</span>
            <span><em>{{ todayOverview.scoreLabel }}</em>评分</span>
          </div>
          <div class="notify-prefs">
            <button
              type="button"
              class="pref-chip"
              :class="{ on: notifyPrefs.sound }"
              @click="toggleNotifySound"
            >
              声音{{ notifyPrefs.sound ? '开' : '关' }}
            </button>
            <button
              type="button"
              class="pref-chip"
              :class="{ on: notifyPrefs.desktop }"
              @click="toggleNotifyDesktop"
            >
              桌面通知{{ notifyPrefs.desktop ? '开' : '关' }}
            </button>
          </div>
        </header>

        <div class="session-search">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.8" />
            <path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
          <input v-model="search" type="search" placeholder="搜索用户或会话…" />
        </div>

        <div class="session-filters" role="tablist" aria-label="会话状态筛选">
          <button
            v-for="f in SESSION_FILTERS"
            :key="f.key"
            type="button"
            role="tab"
            class="session-filter"
            :class="{ active: sessionFilter === f.key }"
            :aria-selected="sessionFilter === f.key"
            @click="sessionFilter = f.key"
          >
            {{ f.label }}
          </button>
        </div>

        <div class="session-list">
          <p v-if="loadingContacts" class="session-hint">加载会话中…</p>
          <button
            v-for="c in sortedContacts"
            :key="c.username"
            type="button"
            class="session-item"
            :class="{
              active: activeUser === c.username,
              overdue: isSessionOverdue(sessionMetaMap[c.username], unreadMap[c.username] || 0)
            }"
            @click="selectContact(c.username)"
          >
            <span
              class="session-avatar avatar-slot"
              :style="!avatarSrc(c.username) ? avatarStyle(c.username) : undefined"
            >
              <img
                v-if="avatarSrc(c.username)"
                :src="avatarSrc(c.username)"
                :alt="c.username"
                @error="onAvatarError(c.username)"
              />
              <template v-else>{{ avatarInitial(c.username) }}</template>
            </span>
            <span class="session-body">
              <span class="session-top">
                <em>{{ c.username }}</em>
                <span v-if="unreadMap[c.username]" class="badge-dot">
                  {{ unreadMap[c.username] > 99 ? '99+' : unreadMap[c.username] }}
                </span>
              </span>
              <small>
                <i class="status-pill" :data-status="sessionStatusOf(c.username)">
                  {{ sessionStatusLabel(sessionStatusOf(c.username)) }}
                </i>
                · {{ c.riderApproved ? '认证接单员' : '普通用户' }}
              </small>
            </span>
          </button>
          <div v-if="!loadingContacts && sortedContacts.length === 0" class="session-empty">
            <div class="empty-icon">💬</div>
            <p>暂无待接待会话</p>
            <small>让用户在 5173 端点击「转人工」即可分配到这里</small>
          </div>
        </div>

        <footer class="session-foot">
          <a :href="mainAppUrl" target="_blank" rel="noopener">用户端 {{ mainAppUrl.replace(/^https?:\/\//, '') }}</a>
        </footer>
      </aside>

      <main class="chat-panel">
        <template v-if="activeUser">
          <header class="chat-head">
            <div class="chat-peer">
              <span
                class="chat-peer-avatar avatar-slot"
                :style="!avatarSrc(activeUser) ? avatarStyle(activeUser) : undefined"
              >
                <img
                  v-if="avatarSrc(activeUser)"
                  :src="avatarSrc(activeUser)"
                  :alt="activeUser"
                  @error="onAvatarError(activeUser)"
                />
                <template v-else>{{ avatarInitial(activeUser) }}</template>
              </span>
              <div>
                <h2>{{ activeUser }}</h2>
                <p>
                  {{ activeContact?.riderApproved ? '认证接单员' : '普通用户' }}
                  ·
                  <span :class="{ 'text-overdue': activeOverdue }">
                    {{ sessionStatusLabel(sessionStatusOf(activeUser)) }}
                    <template v-if="sessionStatusOf(activeUser) !== 'ended'">
                      · 等待 {{ activeWaitLabel }}
                    </template>
                  </span>
                </p>
              </div>
            </div>
            <div class="chat-search-box">
              <input
                v-model="chatSearch"
                type="search"
                placeholder="会话内搜索…"
                @keydown.enter.prevent="jumpChatSearch(1)"
              />
              <button type="button" class="btn-outline btn-xs" :disabled="!chatSearch.trim()" @click="jumpChatSearch(-1)">
                上一条
              </button>
              <button type="button" class="btn-outline btn-xs" :disabled="!chatSearch.trim()" @click="jumpChatSearch(1)">
                下一条
              </button>
            </div>
            <div class="chat-head-actions">
              <div class="msg-notify" @keydown.escape="closeMsgNotify">
                <button
                  type="button"
                  class="msg-notify-btn"
                  :class="{ 'has-unread': totalUnread > 0, pulse: notifyPulse }"
                  :aria-expanded="showMsgNotify"
                  aria-label="消息提醒"
                  @click="toggleMsgNotify"
                >
                  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path
                      d="M6 9a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"
                      stroke="currentColor"
                      stroke-width="1.9"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                    <path
                      d="M10 19a2 2 0 0 0 4 0"
                      stroke="currentColor"
                      stroke-width="1.9"
                      stroke-linecap="round"
                    />
                  </svg>
                  <span v-if="totalUnread > 0" class="msg-notify-badge">
                    {{ totalUnread > 99 ? '99+' : totalUnread }}
                  </span>
                </button>
                <div v-if="showMsgNotify" class="msg-notify-mask" @click="closeMsgNotify" />
                <div v-if="showMsgNotify" class="msg-notify-panel">
                  <div class="msg-notify-panel-head">
                    <strong>未读消息</strong>
                    <span>{{ totalUnread > 0 ? `${totalUnread} 条` : '暂无' }}</span>
                  </div>
                  <ul v-if="unreadContacts.length" class="msg-notify-list">
                    <li v-for="c in unreadContacts" :key="`unread-${c.username}`">
                      <button type="button" @click="openUnreadContact(c.username)">
                        <span
                          class="msg-notify-avatar avatar-slot"
                          :style="!avatarSrc(c.username) ? avatarStyle(c.username) : undefined"
                        >
                          <img
                            v-if="avatarSrc(c.username)"
                            :src="avatarSrc(c.username)"
                            :alt="c.username"
                            @error="onAvatarError(c.username)"
                          />
                          <template v-else>{{ avatarInitial(c.username) }}</template>
                        </span>
                        <span class="msg-notify-meta">
                          <em>{{ c.username }}</em>
                          <small>{{ c.riderApproved ? '认证接单员' : '普通用户' }}</small>
                        </span>
                        <span class="msg-notify-count">
                          {{ unreadMap[c.username] > 99 ? '99+' : unreadMap[c.username] }}
                        </span>
                      </button>
                    </li>
                  </ul>
                  <p v-else class="msg-notify-empty">没有未读会话</p>
                </div>
              </div>
              <button
                v-if="canEndSession"
                type="button"
                class="btn-end-session"
                :disabled="endingSession || sending"
                @click="endCurrentSession"
              >
                {{ endingSession ? '结束中…' : '结束服务' }}
              </button>
              <button type="button" class="btn-outline" @click="loadHistory(activeUser)">
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M4 4v5h5M20 20v-5h-5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                  <path d="M20 9a8 8 0 0 0-14.9-3M4 15a8 8 0 0 0 14.9 3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                </svg>
                刷新
              </button>
            </div>
          </header>

          <div ref="chatBodyRef" class="chat-stream">
            <p v-if="loadingMessages" class="chat-loading">消息加载中…</p>
            <p v-else-if="chatSearch.trim() && filteredChatRows.length === 0" class="chat-loading">无匹配消息</p>
            <template v-for="row in chatSearch.trim() ? filteredChatRows : chatRows" :key="row.key">
              <div v-if="row.kind === 'date'" class="chat-date">{{ row.label }}</div>
              <div
                v-else
                class="chat-row"
                :data-msg-key="row.key"
                :class="{
                  'is-agent': row.item.fromUsername === myName && !isSystemMessage(row.item.content),
                  'is-user': row.item.fromUsername !== myName && !isSystemMessage(row.item.content),
                  'is-system': isSystemMessage(row.item.content),
                  'is-hit':
                    !!chatSearch.trim() &&
                    String(row.item.content || '')
                      .toLowerCase()
                      .includes(chatSearch.trim().toLowerCase())
                }"
              >
                <template v-if="isSystemMessage(row.item.content)">
                  <div class="system-chip" :class="{ 'is-rating': isRatingMessage(row.item.content) }">
                    {{ displaySystemMessage(row.item.content) }}
                  </div>
                </template>
                <template v-else>
                  <span
                    v-if="row.item.fromUsername !== myName"
                    class="bubble-avatar avatar-slot"
                    :style="!avatarSrc(row.item.fromUsername) ? avatarStyle(row.item.fromUsername) : undefined"
                  >
                    <img
                      v-if="avatarSrc(row.item.fromUsername)"
                      :src="avatarSrc(row.item.fromUsername)"
                      :alt="row.item.fromUsername"
                      @error="onAvatarError(row.item.fromUsername)"
                    />
                    <template v-else>{{ avatarInitial(row.item.fromUsername) }}</template>
                  </span>
                  <div class="bubble-wrap">
                    <div class="bubble-meta">
                      <span>{{ row.item.fromUsername === myName ? '我' : row.item.fromUsername }}</span>
                      <time>{{ formatTime(row.item.createdAt) }}</time>
                    </div>
                    <article
                      v-if="chatTaskInquiry(row.item.content)"
                      class="bubble bubble--task-inquiry bubble--task-inquiry-clickable"
                      role="button"
                      tabindex="0"
                      title="点击查看订单详情"
                      @click="openTaskInquiry(chatTaskInquiry(row.item.content)!)"
                      @keydown.enter.prevent="openTaskInquiry(chatTaskInquiry(row.item.content)!)"
                      @keydown.space.prevent="openTaskInquiry(chatTaskInquiry(row.item.content)!)"
                    >
                      <header class="task-inquiry-head">
                        <span class="task-inquiry-badge">订单咨询</span>
                        <span class="task-inquiry-id">#{{ chatTaskInquiry(row.item.content)?.taskId || '—' }}</span>
                      </header>
                      <h4 class="task-inquiry-title">{{ chatTaskInquiry(row.item.content)?.title || '任务订单' }}</h4>
                      <dl class="task-inquiry-meta">
                        <div><dt>状态</dt><dd>{{ chatTaskInquiry(row.item.content)?.status || '—' }}</dd></div>
                        <div><dt>角色</dt><dd>{{ chatTaskInquiry(row.item.content)?.role || '—' }}</dd></div>
                        <div><dt>赏金</dt><dd>{{ chatTaskInquiry(row.item.content)?.reward || '—' }}</dd></div>
                        <div><dt>取件</dt><dd>{{ chatTaskInquiry(row.item.content)?.pickup || '—' }}</dd></div>
                        <div><dt>送达</dt><dd>{{ chatTaskInquiry(row.item.content)?.dropoff || '—' }}</dd></div>
                        <div><dt>发布者</dt><dd>{{ chatTaskInquiry(row.item.content)?.publisher || '—' }}</dd></div>
                        <div><dt>接单员</dt><dd>{{ chatTaskInquiry(row.item.content)?.claimer || '—' }}</dd></div>
                      </dl>
                      <footer class="task-inquiry-foot">点击查看详情</footer>
                    </article>
                    <div v-else class="bubble">{{ row.item.content }}</div>
                  </div>
                  <span
                    v-if="row.item.fromUsername === myName"
                    class="bubble-avatar avatar-slot is-agent-avatar"
                    :style="!avatarSrc(myName) ? avatarStyle(myName) : undefined"
                  >
                    <img
                      v-if="avatarSrc(myName)"
                      :src="avatarSrc(myName)"
                      :alt="myName"
                      @error="onAvatarError(myName)"
                    />
                    <template v-else>{{ avatarInitial(myName) }}</template>
                  </span>
                </template>
              </div>
            </template>
          </div>

          <footer class="chat-compose">
            <div v-if="canAcceptSession" class="compose-gate">
              <p>用户已转人工并在排队等待。确认后点击下方按钮接入，再开始回复。</p>
              <button
                type="button"
                class="btn-brand"
                :disabled="acceptingSession"
                @click="acceptCurrentSession"
              >
                {{ acceptingSession ? '接入中…' : '接入会话' }}
              </button>
            </div>
            <div v-else-if="!canCompose" class="compose-gate compose-gate--idle">
              <p>
                当前没有进行中的转人工。用户需在主站点击「转人工」后，这里才会出现「接入会话」。
                已结束的历史会话仅供查看。
              </p>
            </div>
            <template v-else>
              <div class="compose-quick">
                <div class="compose-quick-head">
                  <span class="compose-quick-label">快捷语</span>
                  <div class="compose-quick-tabs">
                    <button
                      v-for="(g, idx) in quickReplyGroups"
                      :key="g.id"
                      type="button"
                      class="qr-tab"
                      :class="{ active: quickReplyGroupTab === idx }"
                      @click="quickReplyGroupTab = idx"
                    >
                      {{ g.name }}
                    </button>
                  </div>
                  <button type="button" class="btn-text btn-xs" @click="openQuickEditor">编辑</button>
                </div>
                <div class="compose-quick-list">
                  <button
                    v-for="item in activeQuickGroup?.items || []"
                    :key="item.id"
                    type="button"
                    class="compose-quick-chip"
                    :title="`${item.text}\n\n单击填入输入框，再点发送`"
                    :disabled="sending"
                    @click="applyQuickReply(item.text)"
                  >
                    {{ item.label }}
                  </button>
                </div>
              </div>
              <div class="compose-box">
                <textarea
                  v-model="draft"
                  rows="2"
                  placeholder="输入回复内容，Enter 发送 · Shift+Enter 换行"
                  :disabled="sending"
                  @keydown="onDraftKeydown"
                />
                <div class="compose-bar">
                  <span class="compose-tip">与用户端实时互通 · 快捷语仅填入，需点发送才会发出</span>
                  <button type="button" class="btn-brand btn-send" :disabled="!draft.trim() || sending" @click="sendMessage">
                    {{ sending ? '发送中' : '发送' }}
                  </button>
                </div>
              </div>
            </template>
          </footer>
        </template>

        <div v-else class="chat-welcome">
          <header class="chat-welcome-bar">
            <div class="chat-welcome-bar-title">
              <strong>客服工作台</strong>
              <span>{{ totalUnread > 0 ? `${totalUnread} 条未读待处理` : '暂无未读消息' }}</span>
            </div>
            <div class="msg-notify" @keydown.escape="closeMsgNotify">
              <button
                type="button"
                class="msg-notify-btn"
                :class="{ 'has-unread': totalUnread > 0, pulse: notifyPulse }"
                :aria-expanded="showMsgNotify"
                aria-label="消息提醒"
                @click="toggleMsgNotify"
              >
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path
                    d="M6 9a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"
                    stroke="currentColor"
                    stroke-width="1.9"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                  <path
                    d="M10 19a2 2 0 0 0 4 0"
                    stroke="currentColor"
                    stroke-width="1.9"
                    stroke-linecap="round"
                  />
                </svg>
                <span v-if="totalUnread > 0" class="msg-notify-badge">
                  {{ totalUnread > 99 ? '99+' : totalUnread }}
                </span>
              </button>
              <div v-if="showMsgNotify" class="msg-notify-mask" @click="closeMsgNotify" />
              <div v-if="showMsgNotify" class="msg-notify-panel">
                <div class="msg-notify-panel-head">
                  <strong>未读消息</strong>
                  <span>{{ totalUnread > 0 ? `${totalUnread} 条` : '暂无' }}</span>
                </div>
                <ul v-if="unreadContacts.length" class="msg-notify-list">
                  <li v-for="c in unreadContacts" :key="`welcome-unread-${c.username}`">
                    <button type="button" @click="openUnreadContact(c.username)">
                      <span
                        class="msg-notify-avatar avatar-slot"
                        :style="!avatarSrc(c.username) ? avatarStyle(c.username) : undefined"
                      >
                        <img
                          v-if="avatarSrc(c.username)"
                          :src="avatarSrc(c.username)"
                          :alt="c.username"
                          @error="onAvatarError(c.username)"
                        />
                        <template v-else>{{ avatarInitial(c.username) }}</template>
                      </span>
                      <span class="msg-notify-meta">
                        <em>{{ c.username }}</em>
                        <small>{{ c.riderApproved ? '认证接单员' : '普通用户' }}</small>
                      </span>
                      <span class="msg-notify-count">
                        {{ unreadMap[c.username] > 99 ? '99+' : unreadMap[c.username] }}
                      </span>
                    </button>
                  </li>
                </ul>
                <p v-else class="msg-notify-empty">没有未读会话</p>
              </div>
            </div>
          </header>
          <div class="welcome-card">
            <div class="welcome-icon">
              <svg viewBox="0 0 64 64" fill="none" aria-hidden="true">
                <rect x="8" y="14" width="48" height="36" rx="10" stroke="currentColor" stroke-width="2.5" />
                <path d="M8 24h48" stroke="currentColor" stroke-width="2.5" />
                <circle cx="20" cy="38" r="3" fill="currentColor" />
                <circle cx="32" cy="38" r="3" fill="currentColor" />
                <circle cx="44" cy="38" r="3" fill="currentColor" />
              </svg>
            </div>
            <h2>选择左侧用户开始接待</h2>
            <p>用户从主站智能客服点击「转人工」后，会话会自动出现在左侧列表。</p>
            <ul class="welcome-links">
              <li>
                <span>用户端</span>
                <a :href="mainAppUrl" target="_blank" rel="noopener">{{ mainAppUrl }}</a>
              </li>
              <li>
                <span>工作台</span>
                <em>{{ deskPublicUrl }}</em>
              </li>
            </ul>
          </div>
        </div>
      </main>

      <CsContextPanel
        :username="activeUser"
        :loading="userContextLoading"
        :error="userContextError"
        :context="userContext"
        :handoff-reason="handoffReason"
        :note="activeNote"
        :wait-label="activeWaitLabel"
        :serve-label="activeServeLabel"
        :overdue="activeOverdue"
        :chat-orders="chatOrdersFromMessages"
        @update:note="activeNote = $event"
        @refresh="loadUserContext(activeUser)"
      />
    </section>

    <teleport to="body">
      <div v-if="showQuickEditor" class="qr-editor-mask" @click.self="closeQuickEditor">
        <section class="qr-editor" role="dialog" aria-label="编辑快捷话术">
          <header class="qr-editor-head">
            <div>
              <h3>编辑快捷话术</h3>
              <p>按场景分组，保存在本机浏览器</p>
            </div>
            <div class="qr-editor-actions">
              <button type="button" class="btn-text" @click="restoreDefaultQuickReplies">恢复默认</button>
              <button type="button" class="btn-text" @click="addQuickGroup">加分组</button>
              <button type="button" class="btn-brand btn-xs" @click="closeQuickEditor">完成</button>
            </div>
          </header>
          <div class="qr-editor-tabs">
            <button
              v-for="(g, idx) in quickReplyGroups"
              :key="`ed-${g.id}`"
              type="button"
              class="qr-tab"
              :class="{ active: quickReplyGroupTab === idx }"
              @click="quickReplyGroupTab = idx"
            >
              {{ g.name }}
            </button>
          </div>
          <div v-if="activeQuickGroup" class="qr-editor-body">
            <label class="qr-field">
              <span>分组名</span>
              <input v-model="activeQuickGroup.name" @change="persistQuickReplies" />
            </label>
            <div v-for="item in activeQuickGroup.items" :key="item.id" class="qr-item-edit">
              <input v-model="item.label" placeholder="标签" @change="persistQuickReplies" />
              <textarea v-model="item.text" rows="2" placeholder="话术内容" @change="persistQuickReplies" />
              <button type="button" class="btn-text" @click="removeQuickItem(activeQuickGroup.id, item.id)">删除</button>
            </div>
            <button type="button" class="btn-outline" @click="addQuickItem">添加话术</button>
          </div>
        </section>
      </div>
    </teleport>

    <teleport to="body">
      <div v-if="loggedIn && msgToasts.length" class="msg-toast-stack">
        <button
          v-for="t in msgToasts"
          :key="t.id"
          type="button"
          class="msg-toast"
          @click="openUnreadContact(t.from)"
        >
          <span class="msg-toast-dot" />
          <span class="msg-toast-body">
            <strong>{{ t.from }}</strong>
            <small>{{ t.preview }}</small>
          </span>
          <span class="msg-toast-action">查看</span>
        </button>
      </div>
    </teleport>


    <teleport to="body">
      <div v-if="showMyRatings" class="my-ratings-mask" @click.self="closeMyRatings">
        <section class="my-ratings-panel" role="dialog" aria-label="我的评价">
          <header class="my-ratings-head">
            <div>
              <h3>我的评价</h3>
              <p>仅展示用户对你本人的评价</p>
            </div>
            <button type="button" class="btn-text" @click="closeMyRatings">关闭</button>
          </header>
          <div class="my-ratings-kpis">
            <div>
              <span>评价数</span>
              <strong>{{ myRatingsTotal }}</strong>
            </div>
            <div>
              <span>平均星级</span>
              <strong>{{ Number(myRatingsAverage || 0).toFixed(1) }}</strong>
            </div>
          </div>
          <div v-if="myRatingsLoading" class="my-ratings-empty">加载中…</div>
          <ul v-else-if="myRatings.length" class="my-ratings-list">
            <li v-for="item in myRatings" :key="`mine-rating-${item.id}`">
              <div class="my-ratings-row-top">
                <strong>{{ scoreStars(item.score) }} {{ item.score }} 星</strong>
                <time>{{ formatRatingTime(item.createdAt) }}</time>
              </div>
              <p class="my-ratings-user">来自 {{ item.fromUsername }}</p>
              <div v-if="item.tags?.length" class="my-ratings-tags">
                <span
                  v-for="tag in item.tags"
                  :key="`mine-tag-${item.id}-${tag}`"
                  class="my-ratings-tag"
                  :class="{ warn: tag === '有待改进' }"
                >{{ tag }}</span>
              </div>
              <p class="my-ratings-comment">{{ item.comment || '（用户未留言）' }}</p>
            </li>
          </ul>
          <div v-else class="my-ratings-empty">还没有收到评价</div>
          <footer v-if="myRatingsPaging.totalPages > 1" class="my-ratings-foot">
            <button type="button" class="btn-text" :disabled="myRatingsPaging.page <= 1" @click="fetchMyRatings(myRatingsPaging.page - 1)">上一页</button>
            <span>{{ myRatingsPaging.page }} / {{ myRatingsPaging.totalPages }}</span>
            <button type="button" class="btn-text" :disabled="myRatingsPaging.page >= myRatingsPaging.totalPages" @click="fetchMyRatings(myRatingsPaging.page + 1)">下一页</button>
          </footer>
        </section>
      </div>
    </teleport>

    <teleport to="body">
      <div v-if="selectedTaskInquiry" class="task-inquiry-mask" @click.self="closeTaskInquiry">
        <section class="task-inquiry-panel">
          <header class="task-inquiry-panel-head">
            <div>
              <p class="task-inquiry-panel-kicker">订单咨询详情</p>
              <h3>{{ selectedTaskInquiry.title || '任务订单' }}</h3>
              <p class="task-inquiry-panel-id">#{{ selectedTaskInquiry.taskId }}</p>
            </div>
            <button type="button" class="task-inquiry-panel-close" @click="closeTaskInquiry">关闭</button>
          </header>

          <div class="task-inquiry-panel-status">
            <span class="task-inquiry-badge">订单咨询</span>
            <span class="task-inquiry-panel-role">{{ selectedTaskInquiry.role || '—' }}</span>
          </div>

          <dl class="task-inquiry-panel-meta">
            <div><dt>状态</dt><dd>{{ selectedTaskInquiry.status || '—' }}</dd></div>
            <div><dt>赏金</dt><dd>{{ selectedTaskInquiry.reward || '—' }}</dd></div>
            <div><dt>发布者</dt><dd>{{ selectedTaskInquiry.publisher || '—' }}</dd></div>
            <div><dt>接单员</dt><dd>{{ selectedTaskInquiry.claimer || '—' }}</dd></div>
            <div class="task-inquiry-panel-route"><dt>取件</dt><dd>{{ selectedTaskInquiry.pickup || '—' }}</dd></div>
            <div class="task-inquiry-panel-route"><dt>送达</dt><dd>{{ selectedTaskInquiry.dropoff || '—' }}</dd></div>
          </dl>
        </section>
      </div>
    </teleport>
  </div>
</template>

<style scoped>
.desk-root {
  height: 100vh;
  overflow: hidden;
}

/* ── 登录页 ── */
.login-scene {
  height: 100vh;
  overflow-y: auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #faf8f5;
}

.login-visual {
  position: relative;
  display: grid;
  place-items: center;
  overflow: hidden;
  padding: 28px 20px;
  background:
    radial-gradient(ellipse at 20% 20%, rgba(255, 143, 31, 0.3), transparent 42%),
    radial-gradient(ellipse at 80% 70%, rgba(255, 179, 71, 0.38), transparent 48%),
    linear-gradient(150deg, #fff7ed 0%, #ffedd5 42%, #fdba74 100%);
}

.login-decor {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.login-visual-stack {
  position: relative;
  z-index: 2;
  width: min(100%, 380px);
  display: grid;
  gap: 14px;
}

.login-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(154, 52, 18, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(154, 52, 18, 0.055) 1px, transparent 1px);
  background-size: 36px 36px;
  mask-image: radial-gradient(ellipse at center, #000 35%, transparent 78%);
  opacity: 0.7;
}

.login-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(40px);
  opacity: 0.55;
  animation: login-orb-drift 12s ease-in-out infinite alternate;
}

.login-orb-a {
  width: 280px;
  height: 280px;
  background: #ff8f1f;
  top: 10%;
  left: -8%;
}

.login-orb-b {
  width: 220px;
  height: 220px;
  background: #ffb347;
  bottom: 8%;
  right: -5%;
  animation-delay: -4s;
}

.login-orb-c {
  width: 140px;
  height: 140px;
  background: #fdba74;
  top: 58%;
  left: 42%;
  opacity: 0.35;
  animation-delay: -7s;
}

@keyframes login-orb-drift {
  from { transform: translate(0, 0) scale(1); }
  to { transform: translate(18px, -14px) scale(1.06); }
}

.login-glow {
  position: absolute;
  border-radius: 999px;
  pointer-events: none;
  filter: blur(2px);
  opacity: 0.55;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.55) 0%, transparent 70%);
  animation: login-glow-pulse 6s ease-in-out infinite;
}

.login-glow-a {
  width: 180px;
  height: 180px;
  top: 12%;
  right: 8%;
}

.login-glow-b {
  width: 140px;
  height: 140px;
  bottom: 14%;
  left: 10%;
  animation-delay: -2.5s;
}

@keyframes login-glow-pulse {
  0%, 100% { opacity: 0.28; transform: scale(0.96); }
  50% { opacity: 0.55; transform: scale(1.04); }
}

.login-float {
  position: absolute;
  z-index: 1;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(8px);
  color: #9a3412;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
  box-shadow: 0 8px 20px rgba(234, 88, 12, 0.1);
  animation: login-float-bob 5.5s ease-in-out infinite;
}

.login-float-a {
  top: 18%;
  right: 12%;
}

.login-float-b {
  bottom: 22%;
  left: 10%;
  animation-delay: -1.8s;
}

.login-float-c {
  top: 62%;
  right: 16%;
  animation-delay: -3.2s;
}

@keyframes login-float-bob {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.login-brand-card {
  position: relative;
  z-index: 2;
  text-align: center;
  padding: 28px 36px 22px;
  background: rgba(255, 255, 255, 0.76);
  backdrop-filter: blur(12px);
  border-radius: 24px;
  box-shadow: 0 24px 60px rgba(234, 88, 12, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.85);
  animation: login-brand-in 0.7s ease-out both;
}

.login-tips {
  padding: 16px 18px 14px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px);
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 14px 36px rgba(234, 88, 12, 0.1);
  animation: login-brand-in 0.75s ease-out 0.08s both;
}

.login-tips h2 {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 800;
  color: #9a3412;
  letter-spacing: 0.04em;
}

.login-tips ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 9px;
}

.login-tips li {
  display: grid;
  gap: 2px;
  padding-left: 12px;
  border-left: 2.5px solid #fdba74;
}

.login-tips strong {
  font-size: 13px;
  font-weight: 700;
  color: #7c2d12;
  line-height: 1.3;
}

.login-tips span {
  font-size: 12px;
  font-weight: 500;
  color: #9a3412;
  opacity: 0.82;
  line-height: 1.45;
}

@keyframes login-brand-in {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-brand-logo {
  display: block;
  width: 80px;
  height: 80px;
  margin: 0 auto 12px;
  object-fit: contain;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(255, 106, 0, 0.15);
}

.login-brand-icon {
  display: inline-flex;
  width: 72px;
  height: 72px;
  align-items: center;
  justify-content: center;
  border-radius: 20px;
  background: linear-gradient(135deg, #ff8f1f, #ff6a00);
  color: #fff;
  font-size: 32px;
  font-weight: 800;
  margin-bottom: 16px;
  box-shadow: 0 12px 28px rgba(255, 106, 0, 0.35);
}

.login-brand-card p {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #9a3412;
  letter-spacing: 0.02em;
}

.login-brand-card small {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #ea580c;
  opacity: 0.9;
}

.login-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px clamp(28px, 6vw, 72px);
  background: #fff;
}

.login-tag {
  display: inline-flex;
  width: fit-content;
  padding: 5px 12px;
  border-radius: 999px;
  background: #fff7ed;
  color: #ea580c;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  margin-bottom: 20px;
}

.login-panel h1 {
  margin: 0 0 10px;
  font-size: clamp(26px, 3vw, 34px);
  font-weight: 800;
  color: #1c1917;
  letter-spacing: -0.02em;
}

.login-desc {
  margin: 0 0 28px;
  color: #78716c;
  font-size: 15px;
  line-height: 1.65;
  max-width: 400px;
}

.login-form {
  display: grid;
  gap: 18px;
  max-width: 400px;
}

.login-agree-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 14px 0 0;
  max-width: 400px;
  font-size: 12px;
  line-height: 1.6;
  color: #78716c;
  cursor: pointer;
  user-select: none;
}

.login-agree-row.warn {
  color: #b91c1c;
}

.login-agree-row.shake {
  animation: login-agree-shake 0.4s ease;
}

@keyframes login-agree-shake {
  0%,
  100% {
    transform: translateX(0);
  }
  20% {
    transform: translateX(-4px);
  }
  40% {
    transform: translateX(4px);
  }
  60% {
    transform: translateX(-3px);
  }
  80% {
    transform: translateX(3px);
  }
}

.login-agree-row input[type='checkbox'] {
  width: 16px;
  height: 16px;
  min-width: 16px;
  margin: 2px 0 0;
  accent-color: #ea580c;
  cursor: pointer;
}

.login-agree-row span {
  flex: 1;
  min-width: 0;
}

.login-foot-link {
  display: inline;
  margin: 0;
  padding: 0;
  border: 0;
  background: none;
  color: #c2410c;
  font: inherit;
  font-weight: 700;
  line-height: inherit;
  vertical-align: baseline;
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: rgba(234, 88, 12, 0.35);
  text-decoration-thickness: 1px;
  text-underline-offset: 2px;
}

.login-foot-link:hover {
  color: #9a3412;
  text-decoration-color: rgba(234, 88, 12, 0.7);
}

.login-foot-link:focus {
  outline: none;
}

.login-foot-link:focus-visible {
  outline: 2px solid rgba(234, 88, 12, 0.4);
  outline-offset: 3px;
  border-radius: 2px;
}

.login-agree-hint {
  margin: 6px 0 0;
  max-width: 400px;
  font-size: 12px;
  font-weight: 600;
  color: #b91c1c;
}

.login-toast {
  position: fixed;
  left: 50%;
  bottom: 28px;
  z-index: 90;
  transform: translateX(-50%);
  max-width: min(90vw, 360px);
  padding: 12px 16px;
  border-radius: 12px;
  background: rgba(28, 25, 23, 0.9);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.22);
  pointer-events: none;
}

.legal-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(28, 25, 23, 0.45);
  backdrop-filter: blur(3px);
}

.legal-sheet {
  width: min(520px, 100%);
  max-height: min(82vh, 640px);
  display: flex;
  flex-direction: column;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(154, 52, 18, 0.22);
  overflow: hidden;
}

.legal-sheet-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px 12px;
  border-bottom: 1px solid rgba(234, 88, 12, 0.14);
}

.legal-sheet-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #9a3412;
}

.legal-sheet-head p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #78716c;
}

.legal-close {
  box-sizing: border-box;
  width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: rgba(254, 215, 170, 0.7);
  color: #9a3412;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  line-height: 0;
  font-size: 0;
  -webkit-appearance: none;
  appearance: none;
}

.legal-close svg {
  width: 14px;
  height: 14px;
  display: block;
  flex: none;
  pointer-events: none;
}

.legal-close:hover {
  background: rgba(254, 215, 170, 1);
}

.legal-sheet-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 20px;
  display: grid;
  gap: 12px;
}

.legal-sheet-body p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #44403c;
}

.legal-sheet-foot {
  padding: 12px 20px 18px;
  border-top: 1px solid rgba(234, 88, 12, 0.12);
}

.legal-sheet-foot .btn-brand {
  width: 100%;
}

.login-highlights {
  list-style: none;
  margin: 22px 0 0;
  padding: 0;
  display: grid;
  gap: 10px;
  max-width: 400px;
}

.login-highlights li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #57534e;
}

.login-hi-icon {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #fff7ed;
  color: #ea580c;
  flex-shrink: 0;
}

.login-hi-icon svg {
  width: 16px;
  height: 16px;
}

@media (prefers-reduced-motion: reduce) {
  .login-orb,
  .login-glow,
  .login-float,
  .login-brand-card,
  .login-tips {
    animation: none !important;
  }
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  font-size: 13px;
  font-weight: 600;
  color: #57534e;
}

.field input {
  border: 1.5px solid #e7e5e4;
  border-radius: 12px;
  padding: 12px 14px;
  background: #fafaf9;
  color: #1c1917;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.field input:focus {
  outline: none;
  border-color: #fb923c;
  box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.18);
  background: #fff;
}

.field-error {
  margin: 0;
  color: #dc2626;
  font-size: 13px;
}

.btn-brand {
  border: 0;
  border-radius: 12px;
  padding: 13px 20px;
  background: linear-gradient(135deg, #ff9f3f 0%, #ff6a00 100%);
  color: #fff;
  font-weight: 700;
  font-size: 15px;
  box-shadow: 0 8px 24px rgba(255, 106, 0, 0.28);
  transition: transform 0.12s, box-shadow 0.12s;
}

.btn-brand:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(255, 106, 0, 0.34);
}

.btn-brand:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.login-link {
  margin-top: 18px;
  color: #ea580c;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
}

.login-link:hover {
  color: #c2410c;
  text-decoration: underline;
}

/* ── 工作台布局 ── */
.workbench {
  height: 100vh;
  min-height: 0;
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr) 300px;
  background: #f5f5f4;
  overflow: hidden;
}

.today-overview {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 2px;
}

.today-overview span {
  display: grid;
  gap: 2px;
  padding: 8px 6px;
  border-radius: 10px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 11px;
  text-align: center;
}

.today-overview em {
  font-style: normal;
  font-size: 13px;
  font-weight: 800;
  color: #c2410c;
}

.notify-prefs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pref-chip {
  border: 1px solid #e7e5e4;
  background: #fafaf9;
  color: #78716c;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.pref-chip.on {
  border-color: #fdba74;
  background: #fff7ed;
  color: #c2410c;
}

.session-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 12px 10px;
}

.session-filter {
  border: 1px solid #e7e5e4;
  background: #fff;
  color: #57534e;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.session-filter.active {
  border-color: #fb923c;
  background: #fff7ed;
  color: #c2410c;
}

.session-item.overdue {
  background: #fef2f2;
}

.status-pill {
  font-style: normal;
  font-weight: 700;
}

.status-pill[data-status='waiting'] {
  color: #d97706;
}

.status-pill[data-status='serving'] {
  color: #059669;
}

.status-pill[data-status='ended'] {
  color: #a8a29e;
}

.chat-search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 160px;
  justify-content: flex-end;
}

.chat-search-box input {
  width: min(180px, 28vw);
  border: 1px solid #e7e5e4;
  border-radius: 10px;
  padding: 6px 10px;
  font-size: 12px;
  background: #fafaf9;
}

.chat-search-box input:focus {
  outline: none;
  border-color: #fb923c;
}

.btn-xs {
  padding: 4px 8px !important;
  font-size: 12px !important;
  border-radius: 8px !important;
}

.text-overdue {
  color: #b91c1c !important;
  font-weight: 700;
}

.chat-row.is-hit .bubble {
  outline: 2px solid rgba(251, 146, 60, 0.55);
}

.compose-gate {
  display: grid;
  gap: 12px;
  justify-items: start;
  padding: 4px 2px 8px;
}

.compose-gate p {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: #78716c;
  max-width: 520px;
}

.compose-gate--idle p {
  color: #a8a29e;
}

.compose-quick-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.compose-quick-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.qr-tab {
  border: 0;
  background: #f5f5f4;
  color: #78716c;
  border-radius: 999px;
  padding: 3px 9px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.qr-tab.active {
  background: #ffedd5;
  color: #c2410c;
}

.qr-editor-mask {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(28, 25, 23, 0.45);
}

.qr-editor {
  width: min(640px, 100%);
  max-height: min(82vh, 720px);
  overflow: auto;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 24px 60px rgba(154, 52, 18, 0.2);
  padding: 18px;
}

.qr-editor-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.qr-editor-head h3 {
  margin: 0;
  font-size: 17px;
}

.qr-editor-head p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #78716c;
}

.qr-editor-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.qr-editor-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.qr-editor-body {
  display: grid;
  gap: 12px;
}

.qr-field {
  display: grid;
  gap: 6px;
  font-size: 12px;
  color: #78716c;
}

.qr-field input,
.qr-item-edit input,
.qr-item-edit textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #e7e5e4;
  border-radius: 10px;
  padding: 8px 10px;
  font: inherit;
  font-size: 13px;
}

.qr-item-edit {
  display: grid;
  gap: 6px;
  padding: 10px;
  border-radius: 12px;
  background: #fafaf9;
  border: 1px solid #f0eeec;
}

@media (max-width: 1100px) {
  .workbench {
    grid-template-columns: 280px minmax(0, 1fr);
  }

  .workbench :deep(.ctx-panel) {
    display: none;
  }
}

/* ── 左侧会话栏 ── */
.session-panel {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 0;
  overflow: hidden;
  background: #fff;
  border-right: 1px solid #eceae8;
  box-shadow: 4px 0 24px rgba(28, 25, 23, 0.04);
}

.agent-bar {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid #f0eeec;
}

.agent-profile {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
  width: 100%;
}

.agent-avatar {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 800;
  font-size: 16px;
}

.avatar-slot {
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
}

.avatar-slot img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.agent-info {
  min-width: 0;
  flex: 1;
}

.agent-info strong {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: #1c1917;
  line-height: 1.35;
  word-break: break-all;
  overflow-wrap: anywhere;
}

.agent-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #a8a29e;
  margin-top: 2px;
}

.agent-status i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #d6d3d1;
}

.agent-status.live {
  color: #16a34a;
}

.agent-status.live i {
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.2);
}

.agent-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.badge-total {
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.btn-text {
  border: 0;
  background: transparent;
  color: #78716c;
  font-size: 13px;
  font-weight: 600;
  padding: 6px 8px;
  border-radius: 8px;
}

.btn-text:hover {
  background: #f5f5f4;
  color: #44403c;
}

.session-search {
  position: relative;
  padding: 12px 14px;
}

.session-search svg {
  position: absolute;
  left: 26px;
  top: 50%;
  transform: translateY(-50%);
  width: 16px;
  height: 16px;
  color: #a8a29e;
  pointer-events: none;
}

.session-search input {
  width: 100%;
  border: 1.5px solid #eceae8;
  border-radius: 12px;
  padding: 10px 12px 10px 36px;
  background: #fafaf9;
  color: #1c1917;
  font-size: 13px;
}

.session-search input:focus {
  outline: none;
  border-color: #fdba74;
  background: #fff;
}

.session-list {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 4px 10px 10px;
}

.session-hint {
  padding: 16px;
  text-align: center;
  color: #a8a29e;
  font-size: 13px;
}

.session-item {
  width: 100%;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 14px;
  text-align: left;
  transition: background 0.12s;
}

.session-item:hover {
  background: #fafaf9;
}

.session-item.active {
  background: #fff7ed;
  box-shadow: inset 0 0 0 1.5px #fed7aa;
}

.session-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 15px;
}

.session-body {
  flex: 1;
  min-width: 0;
}

.session-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-top em {
  font-style: normal;
  font-weight: 700;
  font-size: 14px;
  color: #1c1917;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-body small {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #a8a29e;
}

.badge-dot {
  flex-shrink: 0;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #ff6a00;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.session-empty {
  padding: 32px 16px;
  text-align: center;
}

.empty-icon {
  font-size: 36px;
  margin-bottom: 10px;
}

.session-empty p {
  margin: 0 0 6px;
  font-weight: 700;
  color: #44403c;
  font-size: 14px;
}

.session-empty small {
  color: #a8a29e;
  font-size: 12px;
  line-height: 1.6;
}

.session-foot {
  padding: 12px 16px 16px;
  border-top: 1px solid #f0eeec;
  font-size: 12px;
}

.session-foot a {
  color: #ea580c;
  text-decoration: none;
  font-weight: 600;
}

/* ── 右侧聊天区 ── */
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #eceae8;
  flex-shrink: 0;
}

.chat-head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.btn-end-session {
  border: 1.5px solid #fecaca;
  border-radius: 12px;
  background: #fef2f2;
  color: #dc2626;
  font-size: 13px;
  font-weight: 700;
  padding: 9px 14px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.btn-end-session:hover:not(:disabled) {
  background: #fee2e2;
  border-color: #f87171;
}

.btn-end-session:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.msg-notify {
  position: relative;
  z-index: 30;
}

.msg-notify-btn {
  position: relative;
  width: 42px;
  height: 42px;
  border: 1.5px solid #e7e5e4;
  border-radius: 12px;
  background: #fff;
  color: #57534e;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s, box-shadow 0.15s;
}

.msg-notify-btn svg {
  width: 20px;
  height: 20px;
}

.msg-notify-btn:hover {
  border-color: #fdba74;
  color: #ea580c;
  background: #fff7ed;
}

.msg-notify-btn.has-unread {
  border-color: #fb923c;
  color: #ea580c;
  background: #fff7ed;
  box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.16);
}

.msg-notify-btn.pulse {
  animation: msg-notify-pulse 0.9s ease-out 2;
}

.msg-notify-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  line-height: 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.45);
  border: 2px solid #fff;
}

.msg-notify-mask {
  position: fixed;
  inset: 0;
  z-index: 28;
  background: transparent;
}

.msg-notify-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 320px;
  max-height: min(420px, 70vh);
  overflow: auto;
  background: #fff;
  border: 1px solid #eceae8;
  border-radius: 16px;
  box-shadow: 0 18px 48px rgba(28, 25, 23, 0.14);
  z-index: 31;
}

.msg-notify-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 16px 10px;
  border-bottom: 1px solid #f0eeec;
}

.msg-notify-panel-head strong {
  font-size: 14px;
  color: #1c1917;
}

.msg-notify-panel-head span {
  font-size: 12px;
  color: #a8a29e;
  font-weight: 600;
}

.msg-notify-list {
  list-style: none;
  margin: 0;
  padding: 6px;
}

.msg-notify-list li button {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 0;
  background: transparent;
  border-radius: 12px;
  padding: 10px;
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.msg-notify-list li button:hover {
  background: #fff7ed;
}

.msg-notify-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.msg-notify-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.msg-notify-meta em {
  font-style: normal;
  font-size: 13px;
  font-weight: 700;
  color: #1c1917;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-notify-meta small {
  font-size: 11px;
  color: #a8a29e;
}

.msg-notify-count {
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.msg-notify-empty {
  margin: 0;
  padding: 28px 16px;
  text-align: center;
  font-size: 13px;
  color: #a8a29e;
}

.msg-toast-stack {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 1200;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: min(360px, calc(100vw - 32px));
  pointer-events: none;
}

.msg-toast {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  border: 1px solid #fed7aa;
  border-radius: 14px;
  padding: 12px 14px;
  background: #fff;
  box-shadow: 0 16px 40px rgba(28, 25, 23, 0.16);
  text-align: left;
  cursor: pointer;
  font: inherit;
  animation: msg-toast-in 0.28s ease-out;
}

.msg-toast:hover {
  border-color: #fb923c;
}

.msg-toast-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ef4444;
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.18);
  flex-shrink: 0;
}

.msg-toast-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.msg-toast-body strong {
  font-size: 13px;
  color: #1c1917;
}

.msg-toast-body small {
  font-size: 12px;
  color: #78716c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-toast-action {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 700;
  color: #ea580c;
}

@keyframes msg-notify-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.45);
  }
  70% {
    box-shadow: 0 0 0 12px rgba(239, 68, 68, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0);
  }
}

@keyframes msg-toast-in {
  from {
    opacity: 0;
    transform: translateY(-10px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.chat-peer {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.chat-peer-avatar {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  font-size: 16px;
}

.chat-head h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  color: #1c1917;
}

.chat-head p {
  margin: 3px 0 0;
  font-size: 12px;
  color: #a8a29e;
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1.5px solid #e7e5e4;
  background: #fff;
  color: #57534e;
  border-radius: 10px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.btn-outline svg {
  width: 16px;
  height: 16px;
}

.btn-outline:hover {
  border-color: #fdba74;
  color: #ea580c;
}

.chat-stream {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  padding: 20px 24px 28px;
  background:
    radial-gradient(circle at 20% 10%, rgba(255, 159, 63, 0.06), transparent 35%),
    #f5f5f4;
}

.chat-stream::-webkit-scrollbar {
  width: 8px;
}

.chat-stream::-webkit-scrollbar-thumb {
  background: rgba(168, 162, 158, 0.45);
  border-radius: 999px;
}

.chat-stream::-webkit-scrollbar-track {
  background: transparent;
}

.chat-loading {
  text-align: center;
  color: #a8a29e;
  font-size: 13px;
  padding: 24px;
}

.chat-date {
  text-align: center;
  margin: 16px 0 20px;
  font-size: 12px;
  color: #a8a29e;
}

.chat-date::before,
.chat-date::after {
  content: '';
  display: inline-block;
  width: 40px;
  height: 1px;
  background: #d6d3d1;
  vertical-align: middle;
  margin: 0 10px;
}

.chat-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin-bottom: 16px;
  max-width: min(680px, 88%);
}

.chat-row.is-user {
  align-self: flex-start;
}

.chat-row.is-agent {
  align-self: flex-end;
  margin-left: auto;
}

.chat-row.is-system {
  justify-content: center;
  max-width: 100%;
  margin: 12px auto 16px;
}

.system-chip {
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

.system-chip.is-rating {
  border-radius: 14px;
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
  font-weight: 650;
  padding: 10px 14px;
  text-align: left;
  white-space: pre-wrap;
}

.bubble-avatar {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.is-agent-avatar:not(:has(img)) {
  background: linear-gradient(135deg, #ff9f3f, #ff6a00) !important;
}

.is-agent-avatar img,
.agent-avatar img {
  object-fit: contain;
  background: #fff;
}

.bubble-wrap {
  min-width: 0;
  max-width: 100%;
}

.bubble-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 11px;
  color: #a8a29e;
}

.chat-row.is-agent .bubble-meta {
  justify-content: flex-end;
}

.bubble {
  display: inline-block;
  max-width: 100%;
  padding: 10px 14px;
  border-radius: 16px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
}

.chat-row.is-user .bubble {
  background: #fff;
  color: #1c1917;
  border: 1px solid #eceae8;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(28, 25, 23, 0.05);
}

.chat-row.is-agent .bubble {
  background: linear-gradient(135deg, #ff9f3f 0%, #ff6a00 100%);
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 4px 14px rgba(255, 106, 0, 0.22);
}

.bubble--task-inquiry {
  display: block;
  width: min(100%, 320px);
  padding: 12px 14px;
  white-space: normal;
  background: linear-gradient(180deg, #fffbeb 0%, #fff 55%) !important;
  border: 1px solid #fde68a !important;
  color: #0f172a !important;
  border-radius: 14px !important;
  box-shadow: 0 8px 20px rgba(251, 191, 36, 0.14) !important;
}

.bubble--task-inquiry-clickable {
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.bubble--task-inquiry-clickable:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(251, 191, 36, 0.22) !important;
}

.task-inquiry-foot {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed #fde68a;
  font-size: 11px;
  font-weight: 700;
  color: #b45309;
  text-align: right;
}

.task-inquiry-mask {
  position: fixed;
  inset: 0;
  z-index: 10050;
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(4px);
}

.task-inquiry-panel {
  width: min(560px, calc(100vw - 32px));
  max-height: min(80vh, 680px);
  overflow: auto;
  padding: 0;
  border-radius: 20px;
  background: linear-gradient(180deg, #fffbeb 0%, #fff 42%);
  border: 1px solid #fde68a;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.18);
}

.task-inquiry-panel-head {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
  padding: 18px 20px;
  border-bottom: 1px solid rgba(251, 191, 36, 0.25);
}

.task-inquiry-panel-head h3 {
  margin: 4px 0 0;
  font-size: 22px;
  line-height: 1.2;
  color: #0f172a;
}

.task-inquiry-panel-kicker,
.task-inquiry-panel-id {
  margin: 0;
  color: #64748b;
  font-size: 12px;
}

.task-inquiry-panel-close {
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #334155;
  border-radius: 999px;
  padding: 8px 14px;
  cursor: pointer;
}

.task-inquiry-panel-status {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px 6px;
}

.task-inquiry-panel-role {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.task-inquiry-panel-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
  margin: 0;
  padding: 10px 20px 20px;
}

.task-inquiry-panel-meta div {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid #f1f5f9;
}

.task-inquiry-panel-route {
  grid-column: 1 / -1;
}

.chat-row.is-user .bubble--task-inquiry {
  border-bottom-left-radius: 4px !important;
}

.task-inquiry-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.task-inquiry-badge {
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

.task-inquiry-id {
  font-size: 11px;
  font-weight: 700;
  color: #b45309;
}

.task-inquiry-title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.35;
}

.task-inquiry-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 10px;
  margin: 0;
}

.task-inquiry-meta div {
  display: grid;
  gap: 2px;
}

.task-inquiry-meta dt {
  margin: 0;
  font-size: 10px;
  color: #94a3b8;
}

.task-inquiry-meta dd {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  line-height: 1.4;
}

.chat-compose {
  flex-shrink: 0;
  padding: 12px 24px 22px;
  background: #fff;
  border-top: 1px solid #eceae8;
}

.compose-quick {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
  min-width: 0;
}

.compose-quick-label {
  flex-shrink: 0;
  margin-top: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #78716c;
  letter-spacing: 0.04em;
}

.compose-quick-list {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.compose-quick-chip {
  border: 1px solid #e7e5e4;
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #57534e;
  background: #fafaf9;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s, transform 0.12s;
  user-select: none;
}

.compose-quick-chip:hover:not(:disabled) {
  color: #c2410c;
  border-color: #fdba74;
  background: #fff7ed;
  transform: translateY(-1px);
}

.compose-quick-chip:active:not(:disabled) {
  transform: translateY(0);
}

.compose-quick-chip:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.compose-box {
  border: 1.5px solid #e7e5e4;
  border-radius: 16px;
  background: #fafaf9;
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.compose-box:focus-within {
  border-color: #fdba74;
  box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.12);
  background: #fff;
}

.compose-box textarea {
  width: 100%;
  border: 0;
  resize: none;
  padding: 14px 16px 8px;
  background: transparent;
  color: #1c1917;
  line-height: 1.55;
}

.compose-box textarea:focus {
  outline: none;
}

.compose-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px 12px 16px;
}

.compose-tip {
  font-size: 12px;
  color: #a8a29e;
}

.btn-send {
  padding: 9px 22px;
  font-size: 14px;
}

/* ── 空状态 ── */
.chat-welcome {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: auto;
  background:
    radial-gradient(circle at 70% 20%, rgba(255, 159, 63, 0.08), transparent 40%),
    #f5f5f4;
}

.chat-welcome-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #eceae8;
  flex-shrink: 0;
}

.chat-welcome-bar-title {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.chat-welcome-bar-title strong {
  font-size: 16px;
  color: #1c1917;
}

.chat-welcome-bar-title span {
  font-size: 12px;
  color: #a8a29e;
  font-weight: 600;
}

.chat-welcome .welcome-card {
  margin: auto;
  padding: 40px 36px;
}

.welcome-card {
  text-align: center;
  max-width: 420px;
  padding: 40px 36px;
  background: #fff;
  border-radius: 24px;
  border: 1px solid #eceae8;
  box-shadow: 0 20px 50px rgba(28, 25, 23, 0.06);
}

.welcome-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
  display: grid;
  place-items: center;
  border-radius: 20px;
  background: #fff7ed;
  color: #ff6a00;
}

.welcome-icon svg {
  width: 40px;
  height: 40px;
}

.welcome-card h2 {
  margin: 0 0 10px;
  font-size: 20px;
  font-weight: 800;
  color: #1c1917;
}

.welcome-card > p {
  margin: 0;
  color: #78716c;
  font-size: 14px;
  line-height: 1.65;
}

.welcome-links {
  list-style: none;
  padding: 0;
  margin: 24px 0 0;
  display: grid;
  gap: 10px;
  text-align: left;
}

.welcome-links li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 12px;
  background: #fafaf9;
  font-size: 13px;
}

.welcome-links span {
  color: #a8a29e;
  font-weight: 600;
}

.welcome-links a,
.welcome-links em {
  color: #ea580c;
  font-style: normal;
  font-weight: 600;
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .login-scene {
    grid-template-columns: 1fr;
  }

  .login-visual {
    min-height: auto;
    padding: 20px 16px 16px;
  }

  .login-float,
  .login-glow {
    display: none;
  }

  .login-visual-stack {
    width: min(100%, 420px);
    gap: 10px;
  }

  .login-brand-card {
    padding: 20px 28px 16px;
  }

  .login-brand-logo {
    width: 64px;
    height: 64px;
    margin-bottom: 8px;
  }

  .login-brand-card p {
    font-size: 16px;
  }

  .login-tips {
    padding: 12px 14px 10px;
  }

  .login-tips ul {
    gap: 7px;
  }

  .login-panel {
    padding: 28px 22px 36px;
  }

  .workbench {
    grid-template-columns: 1fr;
  }

  .session-panel {
    min-height: auto;
    max-height: 44vh;
  }
}

.my-ratings-mask {
  position: fixed;
  inset: 0;
  z-index: 1300;
  background: rgba(28, 25, 23, 0.35);
  display: grid;
  place-items: center;
  padding: 20px;
}

.my-ratings-panel {
  width: min(480px, 100%);
  max-height: min(80vh, 720px);
  overflow: auto;
  background: #fff;
  border-radius: 18px;
  border: 1px solid #eceae8;
  box-shadow: 0 24px 60px rgba(28, 25, 23, 0.18);
}

.my-ratings-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 18px 10px;
  border-bottom: 1px solid #f0eeec;
}

.my-ratings-head h3 {
  margin: 0;
  font-size: 17px;
  color: #1c1917;
}

.my-ratings-head p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #a8a29e;
}

.my-ratings-kpis {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding: 14px 18px;
}

.my-ratings-kpis > div {
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 12px;
  padding: 12px;
}

.my-ratings-kpis span {
  display: block;
  font-size: 12px;
  color: #c2410c;
}

.my-ratings-kpis strong {
  display: block;
  margin-top: 4px;
  font-size: 22px;
  color: #9a3412;
}

.my-ratings-list {
  list-style: none;
  margin: 0;
  padding: 0 12px 16px;
}

.my-ratings-list li {
  border: 1px solid #f0eeec;
  border-radius: 12px;
  padding: 12px;
  margin-bottom: 10px;
  background: #fafaf9;
}

.my-ratings-row-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: #d97706;
}

.my-ratings-row-top time {
  color: #a8a29e;
  font-size: 12px;
}

.my-ratings-user {
  margin: 6px 0 0;
  font-size: 12px;
  color: #78716c;
}

.my-ratings-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 8px 0 0;
}

.my-ratings-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 9px;
  border-radius: 999px;
  border: 1px solid #fdba74;
  background: #fff7ed;
  color: #c2410c;
  font-size: 12px;
  font-weight: 650;
}

.my-ratings-tag.warn {
  border-color: #fca5a5;
  background: #fef2f2;
  color: #b91c1c;
}

.my-ratings-comment {
  margin: 8px 0 0;
  font-size: 13px;
  color: #1c1917;
  line-height: 1.5;
  white-space: pre-wrap;
}

.my-ratings-empty {
  padding: 36px 18px;
  text-align: center;
  color: #a8a29e;
  font-size: 13px;
}

.my-ratings-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 18px 16px;
  color: #78716c;
  font-size: 12px;
}

</style>
