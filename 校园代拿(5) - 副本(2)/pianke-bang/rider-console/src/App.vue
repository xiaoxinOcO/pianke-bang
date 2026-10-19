<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import TaskChatPanel from './components/TaskChatPanel.vue'
import { authBase, apiOrigin, mainAppUrl, tasksBase, walletBase } from './constants/api'
import type {
  AuthResponse,
  MeResponse,
  TaskItem,
  TaskPageResponse,
  WalletMeResponse,
  WalletOverview,
  WalletWithdrawItem
} from './types'
import { request, setAuthLostHandler } from './utils/http'
import { resolveTaskStatusLabel, statusBadgeClass } from './utils/taskStatus'
import { formatUrgentBonusText, isTaskUrgent, taskUrgentRiderBonus } from './utils/taskUrgent'
import { clearToken, getToken, parseUsername, setToken } from './utils/token'

type TabKey = 'hall' | 'mine' | 'history'
type HallSortMode = 'reward' | 'new' | 'reward_asc' | 'wait_longest' | 'deadline_soon'

const HALL_SORT_OPTIONS: { value: HallSortMode; label: string }[] = [
  { value: 'reward', label: '赏金从高到低' },
  { value: 'new', label: '最新发布' },
  { value: 'reward_asc', label: '赏金从低到高' },
  { value: 'wait_longest', label: '时间最长' },
  { value: 'deadline_soon', label: '最近截止' }
]

const HALL_SORT_STORAGE_KEY = 'rider_console_hall_sort_v2'

const VALID_HALL_SORT_MODES = new Set<HallSortMode>(HALL_SORT_OPTIONS.map((o) => o.value))

function readStoredHallSort(): HallSortMode {
  try {
    const raw = localStorage.getItem(HALL_SORT_STORAGE_KEY)
    if (raw && VALID_HALL_SORT_MODES.has(raw as HallSortMode)) return raw as HallSortMode
    const legacy = localStorage.getItem('rider_console_hall_sort_v1')
    if (legacy && VALID_HALL_SORT_MODES.has(legacy as HallSortMode)) return legacy as HallSortMode
  } catch {
    // ignore
  }
  return 'reward'
}

function taskSortTime(task: TaskItem) {
  return String(task.updatedAt || task.createdAt || '')
}

function taskDeadlineTs(task: TaskItem): number | null {
  const raw = task.latestDeliveryAt || task.deliveryEndAt
  if (!raw) return null
  const ts = Date.parse(raw)
  return Number.isFinite(ts) ? ts : null
}

function taskTotalReward(task: TaskItem) {
  return Number(task.reward || 0) + taskUrgentRiderBonus(task)
}

function sortTasks(list: TaskItem[], mode: HallSortMode) {
  const copy = [...list]
  if (mode === 'new') {
    return copy.sort((a, b) => {
      const ua = isTaskUrgent(a) ? 1 : 0
      const ub = isTaskUrgent(b) ? 1 : 0
      if (ub !== ua) return ub - ua
      return taskSortTime(b).localeCompare(taskSortTime(a))
    })
  }
  if (mode === 'wait_longest') {
    return copy.sort((a, b) => taskSortTime(a).localeCompare(taskSortTime(b)))
  }
  if (mode === 'deadline_soon') {
    return copy.sort((a, b) => {
      const da = taskDeadlineTs(a)
      const db = taskDeadlineTs(b)
      if (da == null && db == null) return taskSortTime(b).localeCompare(taskSortTime(a))
      if (da == null) return 1
      if (db == null) return -1
      return da - db
    })
  }
  if (mode === 'reward_asc') {
    return copy.sort((a, b) => taskTotalReward(a) - taskTotalReward(b))
  }
  // 赏金从高到低：含加急赏；同额时加急优先
  return copy.sort((a, b) => {
    const diff = taskTotalReward(b) - taskTotalReward(a)
    if (diff !== 0) return diff
    return (isTaskUrgent(b) ? 1 : 0) - (isTaskUrgent(a) ? 1 : 0)
  })
}

const loggedIn = ref(!!getToken())
const myName = ref(getToken() ? parseUsername(getToken()!) : '')
const loginForm = ref({ phone: '', password: '' })
const loginError = ref('')
const loginLoading = ref(false)
const agreeLegal = ref(false)
const agreeHint = ref('')
let agreeHintTimer: number | null = null

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
      '欢迎使用「片刻帮」校园互助平台及接单员工作台。登录或使用本服务，即表示你已阅读、理解并同意本协议全部内容。若不同意，请停止使用。',
      '一、服务说明：平台提供校园代取快递、代买、代打印、排队等互助任务的信息发布、撮合、订单管理与沟通工具，不直接从事线下配送，亦不对互助双方的线下行为作担保。',
      '二、接单员账号：你应使用已通过认证的接单员账号登录工作台，妥善保管账号与密码，不得出借、转让或与他人共用。因个人保管不善导致的损失，由账号使用人自行承担。',
      '三、接单与履约：接单前请确认取送地点、时限、赏金及加急要求；接单后应及时与发布者沟通并按约定完成。取件码、手机号等隐私信息仅可用于完成本单，不得外传。',
      '四、取消与争议：接单后应优先协商处理异常；因超时、虚假完成、恶意取消等情形，平台可依据规则调整订单状态、信誉或限制部分功能。',
      '五、赏金与提现：完成订单后的赏金入账、提现、手续费等以页面展示及钱包流水为准。请勿进行套现、刷单或其他扰乱交易秩序的行为。',
      '六、沟通与评价：请文明使用站内私聊；不得辱骂、骚扰或发送违法内容。服务评价应真实客观，禁止恶意刷评或打击报复。',
      '七、违规处理：对违反本协议或影响平台安全的行为，平台有权采取警告、限制功能、冻结交易、注销账号等措施，并保留依法追究责任的权利。',
      '八、免责声明：因不可抗力、网络故障、第三方服务异常，或用户自身原因导致的服务中断、延误或损失，平台在法律允许范围内不承担责任。',
      '九、协议更新：我们可能根据运营与法规要求更新本协议；重大变更将通过站内提示等方式告知。你继续使用即视为接受更新后的内容。'
    ]
  },
  privacy: {
    title: '隐私政策',
    updated: '2026-08-01',
    paragraphs: [
      '「片刻帮」重视你的个人信息与隐私安全。本政策说明我们如何收集、使用、存储与保护你的信息。使用接单员工作台即表示你理解并同意本政策。',
      '一、收集的信息：账号与手机号、登录凭证；昵称、头像等资料；接单与订单内容（含取送地点、备注）；钱包与提现记录；私聊沟通记录；设备、日志与安全风控所需的技术信息。',
      '二、使用目的：用于登录核验、任务大厅展示与接单履约、支付结算与提现、客服支持、风险控制、纠纷处理，以及在匿名或聚合后用于改进产品体验。',
      '三、共享与披露：我们不会出售你的个人信息。仅在完成订单所必需时向发布者展示必要信息；在法律法规要求、或保护用户与平台合法权益所必需时，可能依法披露。',
      '四、第三方服务：支付、短信验证、提现渠道等能力可能由合作方提供，我们仅在必要范围内向其提供信息，并要求其依法保护。',
      '五、存储与期限：信息保存在实现服务目的所必需的期限内；超出期限或账号注销完成后，我们将删除或匿名化处理，法律法规另有规定的除外。',
      '六、你的权利：可在个人资料中查看与更新部分信息；可通过用户端客服申请更正、删除或账号注销，我们将在合理期限内核实并处理。',
      '七、安全措施：我们采取合理的技术与管理措施保护数据，但互联网环境无法保证绝对安全。请勿向他人泄露验证码、密码及取件码等敏感信息。',
      '八、政策更新：本政策如有更新，我们将及时公示。你继续使用服务，即表示理解并同意更新后的隐私政策。'
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

const me = ref<MeResponse | null>(null)
const walletOverview = ref<WalletOverview>({
  balance: 0,
  totalIncome: 0,
  totalWithdrawn: 0,
  withdrawingAmount: 0
})
const walletBalance = ref(0)
const walletWithdraws = ref<WalletWithdrawItem[]>([])
const showWithdrawSheet = ref(false)
const withdrawSubmitting = ref(false)
const withdrawHint = ref<{ type: 'error' | 'success'; text: string } | null>(null)
const WITHDRAW_MIN = 1
const WITHDRAW_STEP = 0.1
const WITHDRAW_FEE_THRESHOLD = 100
const WITHDRAW_FEE = 0.1
/** 与后端单笔上限一致 */
const WITHDRAW_HARD_MAX = 10000

const withdrawForm = ref({
  amount: WITHDRAW_MIN,
  amountText: WITHDRAW_MIN.toFixed(1),
  alipayAccount: '',
  accountName: '沙箱环境'
})

function floorToWithdrawStep(n: number) {
  if (!Number.isFinite(n) || n <= 0) return 0
  return Math.floor(n * 10 + 1e-8) / 10
}

/** 当前最多可提（不超过余额，且不超过单笔上限，按 0.1 向下取整） */
const maxWithdrawable = computed(() => {
  const bal = Number(walletOverview.value.balance || 0)
  return floorToWithdrawStep(Math.min(bal, WITHDRAW_HARD_MAX))
})

function calcWithdrawFee(amount: number) {
  if (!Number.isFinite(amount) || amount < WITHDRAW_MIN) return 0
  return amount < WITHDRAW_FEE_THRESHOLD ? WITHDRAW_FEE : 0
}

function calcWithdrawPayout(amount: number) {
  const a = Number(amount)
  if (!Number.isFinite(a)) return 0
  return Math.max(0, Math.round((a - calcWithdrawFee(a)) * 100) / 100)
}

function isValidWithdrawStep(amount: number) {
  if (!Number.isFinite(amount)) return false
  // 0.1 步进：到分后须为 10 的倍数
  const fen = Math.round(amount * 100)
  return fen % 10 === 0
}

/** 输入时只允许最多 1 位小数，并限制不超过可提上限 */
function sanitizeWithdrawAmountInput(raw: string) {
  let s = String(raw || '').replace(/[^\d.]/g, '')
  const firstDot = s.indexOf('.')
  if (firstDot >= 0) {
    s = s.slice(0, firstDot + 1) + s.slice(firstDot + 1).replace(/\./g, '')
    const [intPart, decPart = ''] = s.split('.')
    s = `${intPart}.${decPart.slice(0, 1)}`
  }
  if (s.includes('.')) {
    const [i, d] = s.split('.')
    const intNorm = i.replace(/^0+(?=\d)/, '') || '0'
    s = `${intNorm}.${d}`
  } else if (s.length > 1) {
    s = s.replace(/^0+(?=\d)/, '')
  }

  const max = maxWithdrawable.value
  const maxIntLen = String(Math.floor(WITHDRAW_HARD_MAX)).length
  if (s.includes('.')) {
    const [i, d] = s.split('.')
    if (i.length > maxIntLen) s = `${i.slice(0, maxIntLen)}.${d}`
  } else if (s.length > maxIntLen) {
    s = s.slice(0, maxIntLen)
  }

  if (s !== '' && s !== '.') {
    const n = Number(s)
    if (Number.isFinite(n) && max >= 0 && n > max) {
      s = max > 0 ? max.toFixed(1) : '0'
    }
  }
  return s
}

function onWithdrawAmountInput(e: Event) {
  const el = e.target as HTMLInputElement
  const next = sanitizeWithdrawAmountInput(el.value)
  el.value = next
  withdrawForm.value.amountText = next
  if (next === '' || next === '.') {
    withdrawForm.value.amount = Number.NaN
    return
  }
  withdrawForm.value.amount = Number(next)
}

function normalizeWithdrawAmount() {
  const max = maxWithdrawable.value
  let a = Number(withdrawForm.value.amount)
  if (!Number.isFinite(a)) {
    a = max >= WITHDRAW_MIN ? Math.min(WITHDRAW_MIN, max) : 0
  } else {
    a = Math.round(a * 10) / 10
    if (max >= WITHDRAW_MIN) {
      if (a < WITHDRAW_MIN) a = WITHDRAW_MIN
      if (a > max) a = max
    } else {
      a = 0
    }
  }
  withdrawForm.value.amount = a
  withdrawForm.value.amountText = a > 0 ? a.toFixed(1) : ''
}

function fillMaxWithdraw() {
  const max = maxWithdrawable.value
  if (max < WITHDRAW_MIN) return
  withdrawForm.value.amount = max
  withdrawForm.value.amountText = max.toFixed(1)
}

const withdrawFeePreview = computed(() => {
  const a = Number(withdrawForm.value.amount)
  if (!Number.isFinite(a) || a <= 0) return 0
  return calcWithdrawFee(a)
})
const withdrawPayoutPreview = computed(() => {
  const a = Number(withdrawForm.value.amount)
  if (!Number.isFinite(a) || a <= 0) return 0
  return calcWithdrawPayout(a)
})
const activeTab = ref<TabKey>('hall')
const hallSortMode = ref<HallSortMode>(readStoredHallSort())
const search = ref('')
const selectedTaskId = ref<number | null>(null)
const openTasks = ref<TaskItem[]>([])
const myTasks = ref<TaskItem[]>([])
const loadingList = ref(false)
const actionLoading = ref(false)
const toast = ref('')
const toastTimer = ref<number | null>(null)
const lastRefreshAt = ref('')

const completeDialogTaskId = ref<number | null>(null)
const completeProofText = ref('')
const completeProofImages = ref<string[]>([])
const completeProofFileInputRef = ref<HTMLInputElement | null>(null)
const previewImageUrl = ref('')

const taskCompleteProofStoreKey = 'task_complete_proof_map_v1'

function persistTaskCompleteProof(taskId: number, note: string, images: string[]) {
  try {
    const raw = localStorage.getItem(taskCompleteProofStoreKey)
    const parsed = raw ? (JSON.parse(raw) as Record<string, { note?: string; images?: string[] }>) : {}
    parsed[String(taskId)] = { note, images }
    localStorage.setItem(taskCompleteProofStoreKey, JSON.stringify(parsed))
  } catch {
    // ignore storage errors
  }
}

function parseCompletionProofImages(task?: TaskItem | null) {
  const raw = task?.completionProofImages
  if (Array.isArray(raw)) {
    return raw.filter((u) => typeof u === 'string' && u.trim()).map((u) => u.trim())
  }
  if (typeof raw === 'string') {
    const s = raw.trim()
    if (!s) return []
    try {
      const arr = JSON.parse(s)
      if (Array.isArray(arr)) {
        return arr.filter((u) => typeof u === 'string' && String(u).trim()).map((u) => String(u).trim())
      }
    } catch {
      // ignore
    }
  }
  return []
}

function readLocalCompletionProof(taskId: number) {
  try {
    const raw = localStorage.getItem(taskCompleteProofStoreKey)
    if (!raw) return undefined
    const parsed = JSON.parse(raw) as Record<string, { note?: string; images?: string[] }>
    const item = parsed[String(taskId)]
    if (!item) return undefined
    const note = String(item.note || '').trim()
    const images = Array.isArray(item.images)
      ? item.images.map((x) => String(x || '').trim()).filter(Boolean)
      : []
    if (!note && !images.length) return undefined
    return { note, images }
  } catch {
    return undefined
  }
}

function getTaskCompletionProof(task?: TaskItem | null) {
  if (!task) return undefined
  const note = String(task.completionNote || '').trim()
  const images = parseCompletionProofImages(task)
  if (note || images.length) return { note, images }
  return readLocalCompletionProof(task.id)
}

function shouldShowCompletionProof(task?: TaskItem | null) {
  if (!task?.claimerName || !myName.value) return false
  if (task.claimerName.toLowerCase() !== myName.value.toLowerCase()) return false
  return task.status === 'PENDING_CONFIRMATION' || task.status === 'COMPLETED'
}

let pollTimer: number | null = null

const creditScore = computed(() => me.value?.creditScore ?? 80)

const myAvatarSrc = computed(() => normalizeImageUrl(me.value?.avatarUrl || ''))

const myClaimerRatingAvg = ref<number | null>(null)
const myClaimerRatingCount = ref(0)
const showRatingSheet = ref(false)
const myRatingList = ref<
  Array<{
    taskId: number
    title: string
    rating: number
    comment?: string | null
    publisherName?: string
    ratedAt?: string | null
  }>
>([])
const ratingListLoading = ref(false)

const myRatingLevel = computed(() => {
  const n = myClaimerRatingCount.value
  const avg = myClaimerRatingAvg.value
  if (!n || avg == null) return { label: '暂无评价', tone: 'empty' as const }
  if (avg >= 4.5) return { label: '优秀', tone: 'good' as const }
  if (avg >= 4.0) return { label: '良好', tone: 'ok' as const }
  if (avg >= 3.0) return { label: '一般', tone: 'mid' as const }
  return { label: '待提升', tone: 'low' as const }
})

function formatRating(score?: number | null) {
  if (score == null || !Number.isFinite(score)) return ''
  return Number.isInteger(score) ? `${score}` : score.toFixed(1)
}

function ratingStars(score?: number | null) {
  const n = Math.max(0, Math.min(5, Math.round(Number(score) || 0)))
  return '★'.repeat(n) + '☆'.repeat(5 - n)
}

const creditMeta = computed(() => {
  const score = creditScore.value
  if (score >= 80) return { label: '优秀', tone: 'good' as const, hint: '保持良好服务可维持高信誉' }
  if (score >= 60) return { label: '正常', tone: 'ok' as const, hint: '请按时完成订单，避免取消或超时' }
  return { label: '受限', tone: 'bad' as const, hint: '信誉分低于 60，暂时无法接取新任务' }
})

const canAcceptOrders = computed(() => {
  if (!me.value?.riderApproved) return false
  if (creditScore.value < 60) return false
  const blocked = me.value?.blockedUntil?.trim()
  if (!blocked) return true
  const ts = Date.parse(blocked)
  return !Number.isFinite(ts) || ts <= Date.now()
})

const blockReason = computed(() => {
  if (!me.value?.riderApproved) return '账号未通过接单员认证'
  if (creditScore.value < 60) return `信誉分 ${creditScore.value} 分，低于接单门槛 60 分`
  const blocked = me.value?.blockedUntil?.trim()
  if (blocked) {
    const ts = Date.parse(blocked)
    if (Number.isFinite(ts) && ts > Date.now()) {
      return `账号暂停接单至 ${blocked.replace('T', ' ').slice(0, 16)}`
    }
  }
  return ''
})

const filteredOpenTasks = computed(() =>
  openTasks.value.filter((t) => t.publisherName !== myName.value)
)

const sortedOpenTasks = computed(() => sortTasks(filteredOpenTasks.value, hallSortMode.value))

const activeMyTasks = computed(() =>
  sortTasks(
    myTasks.value.filter((t) => ['CLAIMED', 'PENDING_CONFIRMATION', 'OVERDUE'].includes(t.status)),
    'new'
  )
)

const historyTasks = computed(() =>
  sortTasks(
    myTasks.value.filter((t) => ['COMPLETED', 'CANCELLED'].includes(t.status)),
    'new'
  )
)

const visibleTasks = computed(() => {
  const kw = search.value.trim().toLowerCase()
  const base =
    activeTab.value === 'hall'
      ? sortedOpenTasks.value
      : activeTab.value === 'mine'
        ? activeMyTasks.value
        : historyTasks.value

  if (!kw) return base
  return base.filter(
    (t) =>
      t.title.toLowerCase().includes(kw) ||
      t.pickupLocation.toLowerCase().includes(kw) ||
      t.dropoffLocation.toLowerCase().includes(kw) ||
      t.publisherName.toLowerCase().includes(kw)
  )
})

const selectedTask = computed(() =>
  visibleTasks.value.find((t) => t.id === selectedTaskId.value) ??
  sortedOpenTasks.value.find((t) => t.id === selectedTaskId.value) ??
  activeMyTasks.value.find((t) => t.id === selectedTaskId.value) ??
  historyTasks.value.find((t) => t.id === selectedTaskId.value) ??
  null
)

const hallCount = computed(() => sortedOpenTasks.value.length)
const activeMineCount = computed(() => activeMyTasks.value.length)
const historyCount = computed(() => historyTasks.value.length)

const hallSortHint = computed(() => {
  if (hallSortMode.value === 'new') return '按发布时间从新到旧'
  if (hallSortMode.value === 'reward_asc') return '按赏金从低到高'
  if (hallSortMode.value === 'wait_longest') return '按等待时间从长到短（最早发布的在前）'
  if (hallSortMode.value === 'deadline_soon') return '按送达截止时间从近到远（最急的在前面）'
  return '按赏金从高到低'
})

function setHallSortMode(mode: HallSortMode) {
  hallSortMode.value = mode
  try {
    localStorage.setItem(HALL_SORT_STORAGE_KEY, mode)
  } catch {
    // ignore
  }
}

const taskProgressStep = computed(() => {
  const task = selectedTask.value
  if (!task) return 0
  if (task.status === 'OPEN') return 0
  if (task.status === 'CLAIMED' || task.status === 'OVERDUE') return 1
  if (task.status === 'PENDING_CONFIRMATION') return 2
  if (task.status === 'COMPLETED') return 3
  return 0
})

const canChatWithPublisher = computed(() => {
  const task = selectedTask.value
  if (!task || activeTab.value !== 'mine') return false
  if (task.claimerName?.toLowerCase() !== myName.value.toLowerCase()) return false
  return ['CLAIMED', 'PENDING_CONFIRMATION', 'OVERDUE'].includes(task.status)
})

const detailViewMode = ref<'chat' | 'info'>('info')

const isChatImmersion = computed(() => {
  if (!canChatWithPublisher.value || detailViewMode.value !== 'chat') return false
  return !!selectedTask.value?.publisherName?.trim()
})

/** username -> avatarUrl（空字符串表示已请求过但无头像） */
const publisherAvatarMap = ref<Record<string, string>>({})

function normalizeImageUrl(url?: string) {
  const raw = (url || '').trim()
  if (!raw) return ''
  if (raw.startsWith('data:image/')) return raw
  if (raw.startsWith('http://') || raw.startsWith('https://')) return raw
  if (raw.startsWith('/9j/')) return `data:image/jpeg;base64,${raw}`
  if (raw.startsWith('iVBOR')) return `data:image/png;base64,${raw}`
  if (raw.startsWith('UklGR')) return `data:image/webp;base64,${raw}`
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

function publisherAvatarSrc(name?: string) {
  const key = String(name || '').trim()
  if (!key) return ''
  return normalizeImageUrl(publisherAvatarMap.value[key] || '')
}

async function ensurePublisherAvatar(name?: string) {
  const key = String(name || '').trim()
  if (!key || Object.prototype.hasOwnProperty.call(publisherAvatarMap.value, key)) return
  publisherAvatarMap.value = { ...publisherAvatarMap.value, [key]: '' }
  try {
    const profile = await request<{ avatarUrl?: string }>(
      `${authBase}/users/${encodeURIComponent(key)}/public-profile`
    )
    publisherAvatarMap.value = {
      ...publisherAvatarMap.value,
      [key]: String(profile.avatarUrl || '').trim()
    }
  } catch {
    // keep empty fallback letter avatar
  }
}

watch(
  () => selectedTask.value?.publisherName,
  (name) => {
    void ensurePublisherAvatar(name)
  },
  { immediate: true }
)

function showToast(text: string) {
  toast.value = text
  if (toastTimer.value != null) window.clearTimeout(toastTimer.value)
  toastTimer.value = window.setTimeout(() => {
    toast.value = ''
  }, 3200)
}

function formatMoney(n: number) {
  return `¥${Number(n || 0).toFixed(2)}`
}

function formatTime(raw?: string) {
  if (!raw) return '—'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw.replace('T', ' ').slice(0, 16)
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

/** 模糊相对时间，避免精确到可对上订单 */
function formatRelativeTimeAgo(raw?: string | null) {
  if (!raw) return '近期'
  const ts = Date.parse(String(raw))
  if (!Number.isFinite(ts)) return '近期'
  const diffMs = Date.now() - ts
  if (diffMs < 0) return '近期'
  const mins = Math.floor(diffMs / 60000)
  if (mins < 60) return '约 1 小时内'
  const hours = Math.floor(mins / 60)
  if (hours < 24) return hours <= 2 ? '约几小时前' : '约 1 天内'
  const days = Math.floor(hours / 24)
  if (days < 7) return days === 1 ? '约 1 天前' : `约 ${days} 天前`
  const weeks = Math.floor(days / 7)
  if (weeks < 5) return weeks === 1 ? '约 1 周前' : `约 ${weeks} 周前`
  const months = Math.floor(days / 30)
  if (months < 12) return months <= 1 ? '约 1 个月前' : `约 ${months} 个月前`
  return '约 1 年前'
}

function taskTypeLabel(type?: string) {
  const map: Record<string, string> = {
    EXPRESS: '快递',
    FOOD: '外卖',
    PRINT: '打印',
    QUEUE: '排队',
    OTHER: '其他',
    DELIVERY: '代拿',
    BUY: '代买'
  }
  const key = String(type || '').trim().toUpperCase()
  return map[key] || '其他'
}

function avatarStyle(name: string) {
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = (hash + name.charCodeAt(i) * (i + 1)) % 360
  return {
    background: `linear-gradient(135deg, hsl(${hash} 62% 48%), hsl(${(hash + 40) % 360} 58% 38%))`
  }
}

function ensureDefaultSelection() {
  if (visibleTasks.value.length === 0) {
    selectedTaskId.value = null
    return
  }
  const stillVisible = visibleTasks.value.some((t) => t.id === selectedTaskId.value)
  if (!stillVisible) selectedTaskId.value = visibleTasks.value[0]?.id ?? null
}

async function copyText(text: string) {
  const value = String(text || '').trim()
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    showToast('已复制到剪贴板')
  } catch {
    showToast('复制失败，请手动复制')
  }
}

async function hydrateMe() {
  const token = getToken()
  if (!token) {
    loggedIn.value = false
    return
  }
  try {
    const profile = await request<MeResponse>(`${authBase}/me`)
    if (!profile.riderApproved) {
      clearToken()
      loggedIn.value = false
      myName.value = ''
      loginError.value = profile.riderApplicationPending
        ? '接单员申请审核中，通过后再登录工作台'
        : '该账号尚未通过接单员认证，请先在用户端提交申请'
      return
    }
    me.value = profile
    myName.value = profile.username
    loggedIn.value = true
    void fetchMyClaimerRating()
  } catch (err) {
    // 401 已由 http.ts 清 token 并触发 handleAuthLost；其它错误（超时/网关未起）勿误清登录态
    const msg = (err as Error)?.message || ''
    if (!getToken()) {
      loggedIn.value = false
      myName.value = ''
      me.value = null
      if (msg) loginError.value = msg
      return
    }
    showToast(msg || '登录态校验失败，请稍后重试')
  }
}

async function fetchMyClaimerRating() {
  const name = myName.value.trim()
  if (!name) {
    myClaimerRatingAvg.value = null
    myClaimerRatingCount.value = 0
    return
  }
  try {
    const profile = await request<{
      claimerAverageRating?: number | null
      claimerRatingCount?: number | null
    }>(`${authBase}/users/${encodeURIComponent(name)}/public-profile`)
    const avg = profile.claimerAverageRating
    myClaimerRatingAvg.value =
      avg == null || !Number.isFinite(Number(avg)) ? null : Number(avg)
    myClaimerRatingCount.value = Math.max(0, Number(profile.claimerRatingCount || 0))
  } catch {
    // keep previous snapshot
  }
}

async function fetchMyRatingList() {
  const name = myName.value.trim()
  if (!name) {
    myRatingList.value = []
    return
  }
  ratingListLoading.value = true
  try {
    const rows = await request<
      Array<{
        taskId: number
        title: string
        rating: number
        comment?: string | null
        publisherName?: string
        ratedAt?: string | null
      }>
    >(`${authBase}/users/${encodeURIComponent(name)}/claimer-ratings`)
    myRatingList.value = Array.isArray(rows) ? rows : []
  } catch (err) {
    showToast((err as Error).message || '评价列表加载失败')
  } finally {
    ratingListLoading.value = false
  }
}

async function openRatingSheet() {
  showRatingSheet.value = true
  await Promise.all([fetchMyClaimerRating(), fetchMyRatingList()])
}

function closeRatingSheet() {
  showRatingSheet.value = false
}

async function fetchWallet() {
  try {
    const data = await request<WalletMeResponse>(`${walletBase}/me`)
    const ov = data.overview || { balance: 0, totalIncome: 0, totalWithdrawn: 0, withdrawingAmount: 0 }
    walletOverview.value = {
      balance: Number(ov.balance ?? 0),
      totalIncome: Number(ov.totalIncome ?? 0),
      totalWithdrawn: Number(ov.totalWithdrawn ?? 0),
      withdrawingAmount: Number(ov.withdrawingAmount ?? 0)
    }
    walletBalance.value = walletOverview.value.balance
    walletWithdraws.value = Array.isArray(data.withdrawRequests) ? data.withdrawRequests : []
  } catch {
    // ignore
  }
}

function openWithdrawSheet() {
  withdrawHint.value = null
  normalizeWithdrawAmount()
  showWithdrawSheet.value = true
}

function closeWithdrawSheet() {
  showWithdrawSheet.value = false
  withdrawHint.value = null
}

function withdrawStatusLabel(status: WalletWithdrawItem['status']) {
  if (status === 'PENDING') return '审核中'
  if (status === 'APPROVED') return '已通过'
  return '已驳回'
}

function withdrawStatusClass(status: WalletWithdrawItem['status']) {
  if (status === 'PENDING') return 'wd-badge--pending'
  if (status === 'APPROVED') return 'wd-badge--ok'
  return 'wd-badge--reject'
}

async function submitWithdrawApply() {
  withdrawHint.value = null

  normalizeWithdrawAmount()
  const max = maxWithdrawable.value
  if (max < WITHDRAW_MIN) {
    withdrawHint.value = {
      type: 'error',
      text: `当前最多可提 ¥${max.toFixed(1)}，未达最低提现额 ¥${WITHDRAW_MIN.toFixed(2)}`
    }
    return
  }
  const amount = Math.round(Number(withdrawForm.value.amount) * 100) / 100
  if (!Number.isFinite(amount) || amount < WITHDRAW_MIN) {
    withdrawHint.value = { type: 'error', text: `单笔提现最低 ¥${WITHDRAW_MIN.toFixed(2)}` }
    return
  }
  if (!isValidWithdrawStep(amount)) {
    withdrawHint.value = { type: 'error', text: '提现金额须为 0.1 的整数倍（如 1.0、1.1、1.2）' }
    return
  }
  if (amount > max) {
    withdrawHint.value = { type: 'error', text: `提现金额不能超过当前最多可提 ¥${max.toFixed(1)}` }
    return
  }

  const alipayAccount = withdrawForm.value.alipayAccount.trim()
  const accountName = withdrawForm.value.accountName.trim() || '沙箱环境'
  if (!alipayAccount) {
    withdrawHint.value = { type: 'error', text: '请填写沙箱支付宝账号（买家登录号）' }
    return
  }

  withdrawSubmitting.value = true
  try {
    const res = await request<{ message?: string }>(`${walletBase}/alipay/withdraw`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        amount,
        alipayAccount,
        accountName
      })
    })

    withdrawForm.value.amount = WITHDRAW_MIN
    withdrawForm.value.amountText = WITHDRAW_MIN.toFixed(1)
    withdrawForm.value.alipayAccount = ''

    await fetchWallet()
    const fee = calcWithdrawFee(amount)
    const payout = calcWithdrawPayout(amount)
    const msg =
      res.message ||
      `提现申请已提交（手续费 ¥${fee.toFixed(2)}，预计到账 ¥${payout.toFixed(2)}），等待管理员审核`
    withdrawHint.value = { type: 'success', text: msg }
    showToast(msg)
  } catch (err) {
    withdrawHint.value = { type: 'error', text: (err as Error).message || '提现失败' }
  } finally {
    withdrawSubmitting.value = false
  }
}

async function fetchOpenTasks() {
  const params = new URLSearchParams({ status: 'OPEN', page: '1', size: '100' })
  const data = await request<TaskPageResponse | TaskItem[]>(`${tasksBase}?${params}`)
  openTasks.value = Array.isArray(data) ? data : data.records ?? []
}

async function fetchMyTasks() {
  const me = myName.value.trim()
  if (!me) {
    myTasks.value = []
    return
  }
  try {
    const data = await request<TaskItem[]>(`${tasksBase}/mine`)
    const records = Array.isArray(data) ? data : []
    myTasks.value = records.filter((t) => t.claimerName?.toLowerCase() === me.toLowerCase())
  } catch {
    const params = new URLSearchParams({ page: '1', size: '50' })
    const data = await request<TaskPageResponse | TaskItem[]>(`${tasksBase}?${params}`)
    const records = Array.isArray(data) ? data : data.records ?? []
    myTasks.value = records.filter((t) => t.claimerName?.toLowerCase() === me.toLowerCase())
  }
}

async function refreshDesk() {
  loadingList.value = true
  try {
    await fetchOpenTasks()
    const results = await Promise.allSettled([fetchMyTasks(), fetchWallet(), fetchMyClaimerRating()])
    const failed = results.find((r) => r.status === 'rejected') as PromiseRejectedResult | undefined
    if (failed) {
      showToast((failed.reason as Error)?.message || '部分数据刷新失败')
    }
    lastRefreshAt.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    ensureDefaultSelection()
  } catch (err) {
    showToast((err as Error).message || '刷新失败')
  } finally {
    loadingList.value = false
  }
}

async function login() {
  loginError.value = ''
  if (!agreeLegal.value) {
    flashAgreeHint('请先勾选同意用户协议与隐私政策')
    showToast('请先勾选同意用户协议与隐私政策')
    return
  }
  const phone = loginForm.value.phone.replace(/\D/g, '')
  if (!/^1\d{10}$/.test(phone)) {
    loginError.value = '请输入11位注册手机号'
    return
  }
  if (!loginForm.value.password.trim()) {
    loginError.value = '请输入密码'
    return
  }
  clearAgreeHint()
  loginLoading.value = true
  try {
    const res = await fetch(`${authBase}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        account: phone,
        password: loginForm.value.password,
        clientChannel: 'rider'
      })
    })
    const data = (await res.json().catch(() => ({}))) as AuthResponse & { message?: string }
    if (!res.ok || !data.token) {
      throw new Error(data.message || '登录失败')
    }
    setToken(data.token)
    await hydrateMe()
    if (!loggedIn.value) return
    await bootstrapDesk()
  } catch (err) {
    loginError.value = (err as Error).message || '登录失败'
  } finally {
    loginLoading.value = false
  }
}

function logout() {
  stopPolling()
  clearToken()
  loggedIn.value = false
  myName.value = ''
  me.value = null
  myClaimerRatingAvg.value = null
  myClaimerRatingCount.value = 0
  myRatingList.value = []
  showRatingSheet.value = false
  openTasks.value = []
  myTasks.value = []
  selectedTaskId.value = null
}

function handleAuthLost(message: string) {
  if (!loggedIn.value) return
  logout()
  const text = message || '登录已失效，请重新登录'
  loginError.value = text
  showToast(text)
}

function selectTask(id: number) {
  selectedTaskId.value = id
}

function switchTab(tab: TabKey) {
  activeTab.value = tab
  selectedTaskId.value = null
  ensureDefaultSelection()
}

async function claimTask(taskId: number) {
  if (!canAcceptOrders.value) {
    showToast(blockReason.value || '当前不可接单')
    return
  }
  actionLoading.value = true
  try {
    const claimed = await request<TaskItem>(`${tasksBase}/${taskId}/claim`, { method: 'POST' })
    showToast('接单成功，请尽快完成配送')
    activeTab.value = 'mine'
    if (claimed?.id) {
      const rest = myTasks.value.filter((t) => t.id !== claimed.id)
      myTasks.value = [claimed, ...rest]
    }
    await refreshDesk()
    selectedTaskId.value = taskId
  } catch (err) {
    showToast((err as Error).message || '接单失败')
  } finally {
    actionLoading.value = false
  }
}

async function completeTask(taskId: number) {
  actionLoading.value = true
  try {
    const note = completeProofText.value.trim()
    const images = [...completeProofImages.value]
    const saved = await request<TaskItem>(`${tasksBase}/${taskId}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        note: note || undefined,
        proofImages: images.length ? images : undefined
      })
    })
    const fromServer = getTaskCompletionProof(saved)
    persistTaskCompleteProof(taskId, fromServer?.note || note, fromServer?.images || images)
    // 立刻把服务端字段合并进本地列表，历史/详情可马上看到凭证
    const idx = myTasks.value.findIndex((t) => t.id === taskId)
    if (idx >= 0) {
      myTasks.value[idx] = { ...myTasks.value[idx], ...saved }
    }
    closeCompleteDialog()
    showToast('已提交完成，等待发布方确认')
    await refreshDesk()
  } catch (err) {
    showToast((err as Error).message || '提交失败')
  } finally {
    actionLoading.value = false
  }
}

function openCompleteDialog(taskId: number) {
  completeDialogTaskId.value = taskId
  completeProofText.value = ''
  completeProofImages.value = []
}

function closeCompleteDialog() {
  completeDialogTaskId.value = null
  completeProofText.value = ''
  completeProofImages.value = []
  if (completeProofFileInputRef.value) {
    completeProofFileInputRef.value.value = ''
  }
}

function triggerCompleteProofFilePick() {
  completeProofFileInputRef.value?.click()
}

async function onCompleteProofFilesSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (!files.length) return

  const imageFiles = files.filter((f) => f.type.startsWith('image/'))
  if (!imageFiles.length) {
    showToast('请上传图片文件（jpg/png/webp）')
    input.value = ''
    return
  }

  const maxRemain = Math.max(0, 6 - completeProofImages.value.length)
  if (maxRemain <= 0) {
    showToast('最多上传 6 张凭证图片')
    input.value = ''
    return
  }

  const selected = imageFiles.slice(0, maxRemain)
  const urls = await Promise.all(
    selected.map(
      (file) =>
        new Promise<string>((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => resolve(String(reader.result || ''))
          reader.onerror = () => reject(new Error('图片读取失败'))
          reader.readAsDataURL(file)
        })
    )
  ).catch(() => {
    showToast('图片读取失败，请重试')
    return [] as string[]
  })

  if (urls.length) {
    completeProofImages.value = [...completeProofImages.value, ...urls]
  }
  input.value = ''
}

function removeCompleteProofImage(idx: number) {
  completeProofImages.value = completeProofImages.value.filter((_, i) => i !== idx)
}

function openImagePreview(url: string) {
  previewImageUrl.value = url
}

function closeImagePreview() {
  previewImageUrl.value = ''
}

async function cancelClaim(taskId: number) {
  if (!window.confirm('确定放弃该订单？任务将重新开放给其他接单员。')) return
  actionLoading.value = true
  try {
    await request<TaskItem>(`${tasksBase}/${taskId}/cancel`, { method: 'POST' })
    showToast('已放弃订单')
    activeTab.value = 'hall'
    await refreshDesk()
  } catch (err) {
    showToast((err as Error).message || '操作失败')
  } finally {
    actionLoading.value = false
  }
}

function startPolling() {
  if (pollTimer != null) return
  pollTimer = window.setInterval(() => {
    void refreshDesk()
  }, 12_000)
}

function stopPolling() {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function bootstrapDesk() {
  await refreshDesk()
  startPolling()
}

watch(visibleTasks, () => ensureDefaultSelection())

watch([selectedTaskId, canChatWithPublisher], () => {
  detailViewMode.value = canChatWithPublisher.value ? 'chat' : 'info'
})

watch(loggedIn, (ok) => {
  if (ok) void bootstrapDesk()
  else stopPolling()
})

onMounted(async () => {
  setAuthLostHandler(handleAuthLost)
  await hydrateMe()
  if (loggedIn.value) await bootstrapDesk()
})

onUnmounted(() => {
  setAuthLostHandler(null)
  stopPolling()
  if (toastTimer.value != null) window.clearTimeout(toastTimer.value)
  clearAgreeHint()
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
          <span class="login-float login-float-a">任务大厅</span>
          <span class="login-float login-float-b">配送跟进</span>
          <span class="login-float login-float-c">钱包结算</span>
        </div>
        <div class="login-visual-stack">
          <div class="login-brand-card">
            <img class="login-brand-logo" src="/logo.png" alt="片刻帮" />
            <p>片刻帮 · 接单员</p>
            <small>校园互助配送工作台</small>
          </div>
          <aside class="login-tips" aria-label="接单员须知">
            <h2>接单员须知</h2>
            <ul>
              <li>
                <strong>接单先看清</strong>
                <span>确认取送地点、时限与赏金后再接单</span>
              </li>
              <li>
                <strong>沟通要及时</strong>
                <span>接单后尽快联系发布者，说明预计送达时间</span>
              </li>
              <li>
                <strong>配送守约定</strong>
                <span>按约定路线与时间完成，异常情况先沟通</span>
              </li>
              <li>
                <strong>完成留凭证</strong>
                <span>提交完成时附上必要说明或照片，方便确认</span>
              </li>
            </ul>
          </aside>
        </div>
      </div>
      <div class="login-panel">
        <span class="login-tag">5175 工作台</span>
        <h1>接单员登录</h1>
        <p class="login-desc">
          使用已认证接单员绑定的手机号登录，在独立工作台查看任务大厅、管理配送订单与余额。
        </p>
        <form class="login-form" @submit.prevent="login">
          <label class="field">
            <span>手机号</span>
            <input
              v-model="loginForm.phone"
              type="tel"
              inputmode="numeric"
              autocomplete="tel"
              maxlength="11"
              placeholder="11 位注册手机号"
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
            id="rider-agree-legal"
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
                <path d="M4 7h16M4 12h10M4 17h13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              </svg>
            </i>
            <span>任务大厅实时可接</span>
          </li>
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
            <span>配送进度与发布者私聊</span>
          </li>
          <li>
            <i class="login-hi-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <rect x="3" y="6" width="18" height="12" rx="2" stroke="currentColor" stroke-width="1.8" />
                <path d="M3 10h18" stroke="currentColor" stroke-width="1.8" />
              </svg>
            </i>
            <span>余额提现到支付宝</span>
          </li>
        </ul>
        <a class="login-link" :href="mainAppUrl" target="_blank" rel="noopener">前往用户端申请/管理 →</a>
      </div>

      <div
        v-if="legalDocView"
        class="legal-mask"
        role="dialog"
        aria-modal="true"
        aria-labelledby="rider-legal-doc-title"
        @click.self="closeLegalDoc"
      >
        <section class="legal-sheet">
          <header class="legal-sheet-head">
            <div>
              <h2 id="rider-legal-doc-title">{{ legalDocView.title }}</h2>
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
            <p v-for="(para, idx) in legalDocView.paragraphs" :key="`rider-legal-p-${idx}`">{{ para }}</p>
          </div>
          <footer class="legal-sheet-foot">
            <button type="button" class="btn-brand" @click="closeLegalDoc">我知道了</button>
          </footer>
        </section>
      </div>
    </section>

    <!-- 工作台 -->
    <section v-else class="workbench" :class="{ 'workbench--focus-chat': activeTab === 'mine' && canChatWithPublisher }">
      <aside class="side-panel">
        <div class="brand-row">
          <img src="/logo.png" alt="片刻帮" class="brand-logo" />
          <div>
            <strong>片刻帮</strong>
            <span>接单员工作台</span>
          </div>
        </div>

        <header class="rider-bar">
          <div class="rider-profile">
            <span
              class="rider-avatar"
              :style="myAvatarSrc ? undefined : avatarStyle(myName)"
            >
              <img v-if="myAvatarSrc" :src="myAvatarSrc" alt="" />
              <template v-else>{{ myName.slice(0, 1).toUpperCase() }}</template>
            </span>
            <div class="rider-info">
              <strong>{{ myName }}</strong>
              <div class="rider-meta-row">
                <span class="rider-badge">已认证接单员</span>
                <button
                  type="button"
                  class="rider-rating"
                  :class="`rider-rating--${myRatingLevel.tone}`"
                  :title="
                    myClaimerRatingCount > 0
                      ? `查看发布者评价明细（均分 ${formatRating(myClaimerRatingAvg)}，共 ${myClaimerRatingCount} 条）`
                      : '查看评价明细'
                  "
                  @click="openRatingSheet"
                >
                  <template v-if="myClaimerRatingCount > 0">
                    <i aria-hidden="true">★</i>
                    {{ formatRating(myClaimerRatingAvg) }}
                    <em>{{ myRatingLevel.label }}</em>
                    <small>{{ myClaimerRatingCount }}条</small>
                  </template>
                  <template v-else>暂无评价</template>
                </button>
              </div>
            </div>
          </div>
          <button type="button" class="btn-text" @click="logout">退出</button>
        </header>

        <div class="stat-strip">
          <div class="stat-item" :class="`stat-item--${creditMeta.tone}`">
            <span class="stat-label">信誉分</span>
            <strong>{{ creditScore }}</strong>
            <small>{{ creditMeta.label }}</small>
          </div>
          <div class="stat-item stat-item--money">
            <span class="stat-label">余额</span>
            <strong>{{ formatMoney(walletBalance) }}</strong>
            <button type="button" class="stat-withdraw-btn" @click="openWithdrawSheet">提现</button>
          </div>
          <div class="stat-item">
            <span class="stat-label">进行中</span>
            <strong>{{ activeMineCount }}</strong>
          </div>
        </div>

        <nav class="tab-nav">
          <button type="button" class="tab-btn" :class="{ active: activeTab === 'hall' }" @click="switchTab('hall')">
            任务大厅
            <em v-if="hallCount">{{ hallCount > 99 ? '99+' : hallCount }}</em>
          </button>
          <button type="button" class="tab-btn" :class="{ active: activeTab === 'mine' }" @click="switchTab('mine')">
            我的配送
            <em v-if="activeMineCount">{{ activeMineCount }}</em>
          </button>
          <button type="button" class="tab-btn" :class="{ active: activeTab === 'history' }" @click="switchTab('history')">
            历史
            <em v-if="historyCount">{{ historyCount > 99 ? '99+' : historyCount }}</em>
          </button>
        </nav>

        <div class="list-tools">
          <div class="list-search">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.8" />
              <path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
            <input v-model="search" type="search" placeholder="搜索标题、地点、发布者…" />
          </div>
          <div v-if="activeTab === 'hall'" class="sort-bar" role="group" aria-label="任务排序">
            <button
              v-for="opt in HALL_SORT_OPTIONS"
              :key="opt.value"
              type="button"
              class="sort-chip"
              :class="{ active: hallSortMode === opt.value }"
              @click="setHallSortMode(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
          <div class="list-sync">
            <span v-if="lastRefreshAt" class="sync-hint">更新于 {{ lastRefreshAt }}</span>
            <button type="button" class="btn-outline-sm btn-refresh" :disabled="loadingList" @click="refreshDesk">
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" :class="{ spinning: loadingList }">
                <path
                  d="M20 12a8 8 0 1 1-2.34-5.66M20 4v5h-5"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
              {{ loadingList ? '刷新中…' : '刷新' }}
            </button>
          </div>
        </div>

        <div class="task-list">
          <p v-if="loadingList && visibleTasks.length === 0" class="list-hint">加载中…</p>
          <button
            v-for="task in visibleTasks"
            :key="task.id"
            type="button"
            class="task-item"
            :class="{ active: selectedTaskId === task.id, 'task-item--urgent': isTaskUrgent(task) }"
            @click="selectTask(task.id)"
          >
            <div class="task-item-head">
              <div class="task-item-tags">
                <span class="task-type">{{ taskTypeLabel(task.taskType) }}</span>
                <span v-if="isTaskUrgent(task)" class="urgent-badge">加急</span>
              </div>
              <div class="task-item-reward">
                <span class="reward">{{ formatMoney(Number(task.reward)) }}</span>
                <small v-if="formatUrgentBonusText(task)" class="urgent-bonus">
                  加急赏 +¥{{ formatUrgentBonusText(task) }}
                </small>
              </div>
            </div>
            <strong class="task-title">{{ task.title }}</strong>
            <p class="task-item-route">
              <span>{{ task.pickupLocation }}</span>
              <i aria-hidden="true">→</i>
              <span>{{ task.dropoffLocation }}</span>
            </p>
            <div class="task-item-foot">
              <span class="status-badge" :class="statusBadgeClass(task.status)">
                {{ resolveTaskStatusLabel(task) }}
              </span>
              <span
                v-if="
                  activeTab === 'mine' &&
                  task.claimerName?.toLowerCase() === myName.toLowerCase() &&
                  ['CLAIMED', 'PENDING_CONFIRMATION', 'OVERDUE'].includes(task.status)
                "
                class="task-chat-badge"
              >
                可私聊
              </span>
              <span
                v-if="
                  (activeTab === 'history' || activeTab === 'mine') &&
                  shouldShowCompletionProof(task) &&
                  getTaskCompletionProof(task)
                "
                class="task-proof-badge"
              >
                有凭证
              </span>
              <time>{{ formatTime(task.updatedAt || task.createdAt) }}</time>
            </div>
          </button>
          <div v-if="!loadingList && visibleTasks.length === 0" class="list-empty">
            <div class="empty-icon">{{ activeTab === 'hall' ? '📋' : activeTab === 'mine' ? '🛵' : '📁' }}</div>
            <p>{{ activeTab === 'hall' ? '暂无可接任务' : activeTab === 'mine' ? '暂无进行中的配送' : '暂无历史记录' }}</p>
            <small>
              {{
                activeTab === 'hall'
                  ? '新任务审核通过后会自动出现在这里'
                  : activeTab === 'mine'
                    ? '从任务大厅接单后在此跟进'
                    : '已完成或取消的订单会归档在此'
              }}
            </small>
          </div>
        </div>
      </aside>

      <main class="detail-panel" :class="{ 'detail-panel--chat': isChatImmersion }">
        <div v-if="blockReason" class="global-alert">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
          <div>
            <strong>接单受限</strong>
            <p>{{ blockReason }}。{{ creditMeta.hint }}</p>
          </div>
        </div>

        <template v-if="selectedTask">
          <div v-if="isChatImmersion" class="delivery-chat-page">
            <header class="messenger-topbar">
              <button type="button" class="messenger-back" @click="detailViewMode = 'info'">
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M15 6l-6 6 6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                订单
              </button>
              <div class="messenger-peer">
                <div
                  class="messenger-avatar"
                  :style="
                    publisherAvatarSrc(selectedTask.publisherName)
                      ? undefined
                      : avatarStyle(selectedTask.publisherName)
                  "
                >
                  <img
                    v-if="publisherAvatarSrc(selectedTask.publisherName)"
                    :src="publisherAvatarSrc(selectedTask.publisherName)"
                    alt=""
                  />
                  <template v-else>
                    {{ selectedTask.publisherName.slice(0, 1).toUpperCase() }}
                  </template>
                </div>
                <div class="messenger-peer-text">
                  <strong>{{ selectedTask.publisherName }}</strong>
                  <span>{{ selectedTask.title }} · {{ resolveTaskStatusLabel(selectedTask) }}</span>
                </div>
              </div>
              <div class="messenger-topbar-actions">
                <span class="messenger-reward">{{ formatMoney(Number(selectedTask.reward)) }}</span>
                <button
                  v-if="selectedTask.status === 'CLAIMED'"
                  type="button"
                  class="messenger-btn messenger-btn--primary"
                  :disabled="actionLoading"
                  @click="openCompleteDialog(selectedTask.id)"
                >
                  完成
                </button>
                <button
                  v-if="selectedTask.status === 'CLAIMED'"
                  type="button"
                  class="messenger-btn"
                  :disabled="actionLoading"
                  @click="cancelClaim(selectedTask.id)"
                >
                  放弃
                </button>
              </div>
            </header>
            <TaskChatPanel
              :key="`chat-${selectedTask.id}-${selectedTask.publisherName}`"
              :target-user="selectedTask.publisherName"
              :my-name="myName"
              :task-title="selectedTask.title"
              :peer-avatar-url="publisherAvatarSrc(selectedTask.publisherName)"
              :my-avatar-url="myAvatarSrc"
              expanded
              immersive
            />
          </div>

          <template v-else>
          <header class="detail-hero">
            <div class="detail-hero-main">
              <div class="detail-tags">
                <span class="status-badge lg" :class="statusBadgeClass(selectedTask.status)">
                  {{ resolveTaskStatusLabel(selectedTask) }}
                </span>
                <span class="type-chip">{{ taskTypeLabel(selectedTask.taskType) }}</span>
                <span v-if="isTaskUrgent(selectedTask)" class="urgent-badge urgent-badge--lg">加急</span>
              </div>
              <h2>{{ selectedTask.title }}</h2>
              <p class="detail-meta">
                发布者 {{ selectedTask.publisherName }} · 发布 {{ formatTime(selectedTask.createdAt) }}
              </p>
            </div>
            <div class="detail-reward-box">
              <span>赏金</span>
              <strong>{{ formatMoney(Number(selectedTask.reward)) }}</strong>
              <em v-if="formatUrgentBonusText(selectedTask)" class="detail-urgent-bonus">
                加急赏 +¥{{ formatUrgentBonusText(selectedTask) }}
              </em>
            </div>
          </header>

          <div v-if="selectedTask.claimerName === myName && taskProgressStep > 0" class="progress-bar">
            <div class="progress-step" :class="{ done: taskProgressStep >= 1, current: taskProgressStep === 1 }">
              <i>1</i><span>配送中</span>
            </div>
            <div class="progress-line" :class="{ done: taskProgressStep >= 2 }" />
            <div class="progress-step" :class="{ done: taskProgressStep >= 2, current: taskProgressStep === 2 }">
              <i>2</i><span>待确认</span>
            </div>
            <div class="progress-line" :class="{ done: taskProgressStep >= 3 }" />
            <div class="progress-step" :class="{ done: taskProgressStep >= 3, current: taskProgressStep === 3 }">
              <i>3</i><span>已结算</span>
            </div>
          </div>

          <div v-if="canChatWithPublisher" class="detail-view-tabs">
            <button
              type="button"
              class="detail-view-tab"
              :class="{ active: detailViewMode === 'chat' }"
              @click="detailViewMode = 'chat'"
            >
              与发布者私聊
            </button>
            <button
              type="button"
              class="detail-view-tab"
              :class="{ active: detailViewMode === 'info' }"
              @click="detailViewMode = 'info'"
            >
              订单详情
            </button>
          </div>

          <div class="detail-body">
            <div class="detail-info-full">
              <section class="detail-block">
                <h3>配送路线</h3>
                <div class="route-card">
                  <div class="route-point from">
                    <i>取</i>
                    <div>
                      <strong>取件地点</strong>
                      <p>{{ selectedTask.pickupLocation }}</p>
                    </div>
                  </div>
                  <div class="route-line" aria-hidden="true">
                    <span class="route-line-flow" />
                  </div>
                  <div class="route-point to">
                    <i>送</i>
                    <div>
                      <strong>送达地点</strong>
                      <p>{{ selectedTask.dropoffLocation }}</p>
                    </div>
                  </div>
                </div>
              </section>

              <section class="detail-block">
                <h3>任务详情</h3>
                <dl class="info-grid">
                  <div class="full info-publisher">
                    <dt>发布者</dt>
                    <dd class="publisher-row">
                      <span
                        class="publisher-avatar"
                        :style="
                          publisherAvatarSrc(selectedTask.publisherName)
                            ? undefined
                            : avatarStyle(selectedTask.publisherName || '?')
                        "
                      >
                        <img
                          v-if="publisherAvatarSrc(selectedTask.publisherName)"
                          :src="publisherAvatarSrc(selectedTask.publisherName)"
                          alt=""
                        />
                        <template v-else>
                          {{ (selectedTask.publisherName || '?').slice(0, 1).toUpperCase() }}
                        </template>
                      </span>
                      <div class="publisher-text">
                        <strong>{{ selectedTask.publisherName || '—' }}</strong>
                      </div>
                    </dd>
                  </div>
                  <div>
                    <dt>任务类型</dt>
                    <dd>{{ taskTypeLabel(selectedTask.taskType) }}</dd>
                  </div>
                  <div>
                    <dt>发布时间</dt>
                    <dd>{{ formatTime(selectedTask.createdAt) }}</dd>
                  </div>
                  <div>
                    <dt>联系方式</dt>
                    <dd class="contact-row">
                      <span>{{ selectedTask.contact || '—' }}</span>
                      <button
                        v-if="selectedTask.contact"
                        type="button"
                        class="btn-copy"
                        @click="copyText(selectedTask.contact)"
                      >
                        复制
                      </button>
                    </dd>
                  </div>
                  <div>
                    <dt>期望送达</dt>
                    <dd>{{ formatTime(selectedTask.latestDeliveryAt || selectedTask.deliveryEndAt) }}</dd>
                  </div>
                  <div v-if="formatUrgentBonusText(selectedTask)" class="full">
                    <dt>加急赏</dt>
                    <dd class="urgent-bonus-dd">+¥{{ formatUrgentBonusText(selectedTask) }}（接单后计入收入）</dd>
                  </div>
                  <div class="full">
                    <dt>备注说明</dt>
                    <dd>{{ selectedTask.description || '无特殊要求' }}</dd>
                  </div>
                </dl>

                <div v-if="shouldShowCompletionProof(selectedTask)" class="detail-proof-block">
                  <div class="detail-proof-head">
                    <h4>完成凭证</h4>
                    <span>争议/追缴时可核对</span>
                  </div>
                  <template v-if="getTaskCompletionProof(selectedTask)">
                    <p v-if="getTaskCompletionProof(selectedTask)?.note" class="detail-proof-note">
                      {{ getTaskCompletionProof(selectedTask)?.note }}
                    </p>
                    <div
                      v-if="getTaskCompletionProof(selectedTask)?.images?.length"
                      class="complete-proof-grid"
                    >
                      <div
                        v-for="(img, idx) in getTaskCompletionProof(selectedTask)?.images"
                        :key="`hist-proof-${selectedTask.id}-${idx}`"
                        class="complete-proof-item"
                      >
                        <img :src="img" alt="完成凭证" @click="openImagePreview(img)" />
                      </div>
                    </div>
                    <p
                      v-else-if="!getTaskCompletionProof(selectedTask)?.note"
                      class="detail-proof-empty"
                    >
                      提交完成时未附带留言或图片。
                    </p>
                  </template>
                  <p v-else class="detail-proof-empty">
                    未找到该单凭证（可能提交于修复前）。之后提交的完成记录会保存在此。
                  </p>
                </div>
              </section>
            </div>
          </div>

          <footer class="detail-actions">
            <button
              v-if="activeTab === 'hall' && selectedTask.status === 'OPEN'"
              type="button"
              class="btn-brand btn-lg"
              :disabled="actionLoading || !canAcceptOrders"
              @click="claimTask(selectedTask.id)"
            >
              {{ actionLoading ? '处理中…' : `立即接单 · ${formatMoney(taskTotalReward(selectedTask))}` }}
            </button>

            <template v-if="selectedTask.claimerName === myName">
              <button
                v-if="selectedTask.status === 'CLAIMED'"
                type="button"
                class="btn-brand btn-lg"
                :disabled="actionLoading"
                @click="openCompleteDialog(selectedTask.id)"
              >
                提交完成
              </button>
              <button
                v-if="selectedTask.status === 'CLAIMED'"
                type="button"
                class="btn-outline"
                :disabled="actionLoading"
                @click="cancelClaim(selectedTask.id)"
              >
                放弃订单
              </button>
              <p v-if="selectedTask.status === 'PENDING_CONFIRMATION'" class="action-hint success">
                已提交完成，等待发布方确认后赏金将入账钱包。
              </p>
              <p v-if="selectedTask.status === 'COMPLETED'" class="action-hint success">
                任务已完成，赏金已结算至钱包余额。
              </p>
            </template>

            <a class="btn-link" :href="mainAppUrl" target="_blank" rel="noopener">前往用户端查看钱包</a>
          </footer>
          </template>
        </template>

        <div v-else class="detail-welcome">
          <div class="welcome-card">
            <img src="/logo.png" alt="" class="welcome-logo" />
            <h2>欢迎使用接单员工作台</h2>
            <p v-if="hallCount > 0">
              当前有 <strong>{{ hallCount }}</strong> 个可接任务，列表{{ hallSortHint }}。
            </p>
            <p v-else>任务大厅暂无订单，稍后再来看看吧。</p>
            <div class="welcome-stats">
              <div><span>可接任务</span><strong>{{ hallCount }}</strong></div>
              <div><span>进行中</span><strong>{{ activeMineCount }}</strong></div>
              <div><span>钱包余额</span><strong>{{ formatMoney(walletBalance) }}</strong></div>
            </div>
          </div>
        </div>
      </main>
    </section>

    <div v-if="completeDialogTaskId != null" class="complete-mask" @click.self="closeCompleteDialog">
      <section class="complete-sheet" role="dialog" aria-labelledby="complete-title">
        <header class="complete-head">
          <div>
            <h2 id="complete-title">提交完成凭证</h2>
            <p>可上传送达截图/照片，并填写完成说明（最多 6 张）</p>
          </div>
          <button type="button" class="complete-close" aria-label="关闭" @click="closeCompleteDialog">×</button>
        </header>

        <textarea
          v-model="completeProofText"
          rows="4"
          maxlength="500"
          class="complete-textarea"
          placeholder="例如：已送达到宿舍门口，对方已确认签收。"
        />

        <div class="complete-proof-actions">
          <input
            ref="completeProofFileInputRef"
            type="file"
            accept="image/*"
            multiple
            class="complete-proof-file"
            @change="onCompleteProofFilesSelected"
          />
          <button type="button" class="btn-outline-sm" @click="triggerCompleteProofFilePick">上传照片/截图</button>
          <span class="complete-proof-count">已上传 {{ completeProofImages.length }} / 6</span>
        </div>

        <div v-if="completeProofImages.length" class="complete-proof-grid">
          <div v-for="(img, idx) in completeProofImages" :key="`proof-${idx}`" class="complete-proof-item">
            <img :src="img" alt="完成凭证" @click="openImagePreview(img)" />
            <button type="button" class="complete-proof-remove" @click="removeCompleteProofImage(idx)">删除</button>
          </div>
        </div>

        <footer class="complete-foot">
          <button type="button" class="btn-outline" @click="closeCompleteDialog">取消</button>
          <button
            type="button"
            class="btn-brand"
            :disabled="actionLoading"
            @click="completeDialogTaskId != null && completeTask(completeDialogTaskId)"
          >
            {{ actionLoading ? '提交中…' : '确认提交完成' }}
          </button>
        </footer>
      </section>
    </div>

    <div v-if="previewImageUrl" class="complete-mask preview-mask" @click.self="closeImagePreview">
      <img class="preview-image" :src="previewImageUrl" alt="凭证预览" />
    </div>

    <div v-if="showWithdrawSheet" class="withdraw-mask">
      <section class="withdraw-sheet withdraw-sheet--docked" role="dialog" aria-labelledby="withdraw-title">
        <header class="withdraw-head">
          <div>
            <h2 id="withdraw-title">提现到支付宝沙箱</h2>
            <p>提交后由管理员审核，通过后转入沙箱支付宝账号</p>
          </div>
          <button type="button" class="withdraw-close" aria-label="关闭" @click="closeWithdrawSheet">×</button>
        </header>

        <div class="withdraw-body">
        <div class="withdraw-summary">
          <div>
            <span>可用余额</span>
            <strong>{{ formatMoney(walletOverview.balance) }}</strong>
          </div>
          <div>
            <span>提现中</span>
            <strong>{{ formatMoney(walletOverview.withdrawingAmount) }}</strong>
          </div>
          <div>
            <span>已提现</span>
            <strong>{{ formatMoney(walletOverview.totalWithdrawn) }}</strong>
          </div>
        </div>

        <form class="withdraw-form" @submit.prevent="submitWithdrawApply">
          <p
            v-if="withdrawHint"
            class="withdraw-hint"
            :class="withdrawHint.type === 'success' ? 'withdraw-hint--ok' : 'withdraw-hint--err'"
          >
            {{ withdrawHint.text }}
          </p>

          <div class="withdraw-rules">
            <p><strong>提现规则</strong></p>
            <ul>
              <li>单笔最低 <em>¥1.00</em>，金额按 <em>¥0.1</em> 递增（如 1.0、1.1、1.2）</li>
              <li>本次最多可提 <em>¥{{ maxWithdrawable.toFixed(1) }}</em>（不超过可用余额，单笔上限 ¥{{ WITHDRAW_HARD_MAX }}）</li>
              <li>提现金额 <em>低于 ¥100</em> 时，每笔收取手续费 <em>¥0.10</em>；≥ ¥100 免手续费</li>
              <li>手续费从提现金额中扣除：实际到账 = 提现金额 − 手续费</li>
            </ul>
          </div>

          <label class="field">
            <span class="withdraw-amount-label">
              提现金额
              <button
                v-if="maxWithdrawable >= WITHDRAW_MIN"
                type="button"
                class="withdraw-max-btn"
                @click="fillMaxWithdraw"
              >
                全部提现 ¥{{ maxWithdrawable.toFixed(1) }}
              </button>
            </span>
            <input
              :value="withdrawForm.amountText"
              type="text"
              inputmode="decimal"
              autocomplete="off"
              :placeholder="maxWithdrawable >= WITHDRAW_MIN ? `最多 ${maxWithdrawable.toFixed(1)}` : '余额不足最低提现额'"
              :disabled="maxWithdrawable < WITHDRAW_MIN"
              required
              @input="onWithdrawAmountInput"
              @blur="normalizeWithdrawAmount"
            />
          </label>
          <div class="withdraw-fee-row">
            <span>手续费 <strong>¥{{ withdrawFeePreview.toFixed(2) }}</strong></span>
            <span>预计到账 <strong>¥{{ withdrawPayoutPreview.toFixed(2) }}</strong></span>
          </div>
          <label class="field">
            <span>沙箱支付宝账号</span>
            <input
              v-model="withdrawForm.alipayAccount"
              placeholder="如 xxx@sandbox.com"
              required
            />
          </label>
          <label class="field">
            <span>收款姓名</span>
            <input v-model="withdrawForm.accountName" placeholder="沙箱环境" required />
          </label>
          <button
            class="btn-brand"
            type="submit"
            :disabled="withdrawSubmitting || maxWithdrawable < WITHDRAW_MIN"
          >
            {{ withdrawSubmitting ? '提交中…' : '提交提现申请' }}
          </button>
        </form>

        <p class="withdraw-tip">
          账号与姓名可在
          <a href="https://openhome.alipay.com/develop/sandbox/account" target="_blank" rel="noopener">支付宝开放平台 · 沙箱账号</a>
          的「买家信息」中查看。
        </p>

        <section v-if="walletWithdraws.length" class="withdraw-records">
          <h3>最近提现</h3>
          <ul>
            <li v-for="w in walletWithdraws.slice(0, 5)" :key="w.id">
              <div class="wd-row">
                <strong>{{ formatMoney(Number(w.amount)) }}</strong>
                <span class="wd-badge" :class="withdrawStatusClass(w.status)">
                  {{ withdrawStatusLabel(w.status) }}
                </span>
              </div>
              <p class="wd-meta">
                {{ w.accountNo || '—' }} · {{ formatTime(w.createdAt) }}
              </p>
              <p v-if="w.status === 'REJECTED' && w.auditRemark" class="wd-reject">
                驳回原因：{{ w.auditRemark }}
              </p>
            </li>
          </ul>
        </section>
        </div>
      </section>
    </div>

    <div v-if="showRatingSheet" class="withdraw-mask" @click.self="closeRatingSheet">
      <section class="withdraw-sheet rating-sheet" role="dialog" aria-labelledby="rating-title">
        <header class="withdraw-head">
          <div>
            <h2 id="rating-title">我的接单评价</h2>
            <p>仅作服务反馈参考，不展示订单与发布者信息</p>
          </div>
          <button type="button" class="withdraw-close" aria-label="关闭" @click="closeRatingSheet">×</button>
        </header>

        <div class="rating-summary">
          <div>
            <span>均分</span>
            <strong>
              {{ myClaimerRatingCount > 0 ? formatRating(myClaimerRatingAvg) : '—' }}
            </strong>
          </div>
          <div>
            <span>等级</span>
            <strong :class="`rating-level-text rating-level-text--${myRatingLevel.tone}`">
              {{ myRatingLevel.label }}
            </strong>
          </div>
          <div>
            <span>评价数</span>
            <strong>{{ myClaimerRatingCount }}</strong>
          </div>
        </div>

        <p v-if="ratingListLoading" class="rating-list-hint">加载中…</p>
        <p v-else-if="myRatingList.length === 0" class="rating-list-hint">
          暂无评价。完成订单后，发布者打分会出现在这里。
        </p>
        <ul v-else class="rating-list">
          <li v-for="(item, idx) in myRatingList" :key="`rating-${idx}`">
            <div class="rating-item-head">
              <span class="rating-item-score">
                <i aria-hidden="true">{{ ratingStars(item.rating) }}</i>
                {{ formatRating(Number(item.rating)) }}
              </span>
              <span class="rating-item-meta">{{ formatRelativeTimeAgo(item.ratedAt) }}</span>
            </div>
            <p v-if="item.comment" class="rating-item-comment">{{ item.comment }}</p>
            <p v-else class="rating-item-comment rating-item-comment--empty">未填写文字评价</p>
          </li>
        </ul>
      </section>
    </div>

    <Transition name="toast">
      <div v-if="toast" class="toast" role="status">{{ toast }}</div>
    </Transition>
  </div>
</template>

<style scoped>
.desk-root {
  min-height: 100vh;
}

/* ── 登录（接单员绿） ── */
.login-scene {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #f8faf9;
}

.login-visual {
  position: relative;
  display: grid;
  place-items: center;
  overflow: hidden;
  padding: 28px 20px;
  background:
    radial-gradient(ellipse at 20% 20%, rgba(16, 185, 129, 0.28), transparent 42%),
    radial-gradient(ellipse at 80% 70%, rgba(52, 211, 153, 0.35), transparent 48%),
    linear-gradient(150deg, #ecfdf5 0%, #d1fae5 42%, #86efac 100%);
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
    linear-gradient(rgba(6, 95, 70, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(6, 95, 70, 0.05) 1px, transparent 1px);
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
  background: #10b981;
  top: 10%;
  left: -8%;
}

.login-orb-b {
  width: 220px;
  height: 220px;
  background: #34d399;
  bottom: 8%;
  right: -5%;
  animation-delay: -4s;
}

.login-orb-c {
  width: 140px;
  height: 140px;
  background: #6ee7b7;
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
  color: #065f46;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
  box-shadow: 0 8px 20px rgba(5, 150, 105, 0.1);
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
  box-shadow: 0 24px 60px rgba(5, 150, 105, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.85);
  animation: login-brand-in 0.7s ease-out both;
}

.login-tips {
  padding: 16px 18px 14px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px);
  border-radius: 18px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  box-shadow: 0 14px 36px rgba(5, 150, 105, 0.1);
  animation: login-brand-in 0.75s ease-out 0.08s both;
}

.login-tips h2 {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 800;
  color: #065f46;
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
  border-left: 2.5px solid #6ee7b7;
}

.login-tips strong {
  font-size: 13px;
  font-weight: 700;
  color: #064e3b;
  line-height: 1.3;
}

.login-tips span {
  font-size: 12px;
  font-weight: 500;
  color: #047857;
  opacity: 0.85;
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
  width: 96px;
  height: 96px;
  margin: 0 auto 16px;
  object-fit: contain;
  border-radius: 22px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(5, 150, 105, 0.15);
}

.login-brand-card p {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #065f46;
}

.login-brand-card small {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #059669;
  opacity: 0.85;
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
  background: #ecfdf5;
  color: #059669;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 20px;
}

.login-panel h1 {
  margin: 0 0 10px;
  font-size: clamp(26px, 3vw, 34px);
  font-weight: 800;
  color: #1c1917;
}

.login-desc {
  margin: 0 0 28px;
  color: #78716c;
  font-size: 15px;
  line-height: 1.65;
  max-width: 420px;
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
  accent-color: #059669;
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
  color: #047857;
  font: inherit;
  font-weight: 700;
  line-height: inherit;
  vertical-align: baseline;
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: rgba(5, 150, 105, 0.35);
  text-decoration-thickness: 1px;
  text-underline-offset: 2px;
}

.login-foot-link:hover {
  color: #065f46;
  text-decoration-color: rgba(5, 150, 105, 0.7);
}

.login-foot-link:focus {
  outline: none;
}

.login-foot-link:focus-visible {
  outline: 2px solid rgba(5, 150, 105, 0.4);
  outline-offset: 3px;
  border-radius: 2px;
}

.login-agree-hint {
  margin: 6px 0 0;
  max-width: 400px;
  font-size: 12px;
  color: #b91c1c;
}

.legal-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(3px);
}

.legal-sheet {
  width: min(520px, 100%);
  max-height: min(82vh, 640px);
  display: flex;
  flex-direction: column;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(6, 78, 59, 0.22);
  overflow: hidden;
}

.legal-sheet-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px 12px;
  border-bottom: 1px solid rgba(5, 150, 105, 0.12);
}

.legal-sheet-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #14532d;
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
  background: rgba(167, 243, 208, 0.55);
  color: #065f46;
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
  background: rgba(167, 243, 208, 0.9);
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
  border-top: 1px solid rgba(5, 150, 105, 0.1);
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
  background: #ecfdf5;
  color: #059669;
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
}

.field input:focus {
  outline: none;
  border-color: #6ee7b7;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15);
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
  background: linear-gradient(135deg, #34d399 0%, #059669 100%);
  color: #fff;
  font-weight: 700;
  font-size: 15px;
  box-shadow: 0 8px 24px rgba(5, 150, 105, 0.25);
  transition: transform 0.12s, box-shadow 0.12s;
}

.btn-brand:hover:not(:disabled) {
  transform: translateY(-1px);
}

.btn-brand:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.btn-brand.btn-lg {
  padding: 14px 28px;
  font-size: 16px;
}

.login-link {
  margin-top: 18px;
  color: #059669;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
}

.login-link:hover {
  color: #047857;
}

/* ── 工作台：大厅优先，详情区收窄 ── */
.workbench {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(460px, 48%) minmax(0, 1fr);
  background: #f5f5f4;
}

.workbench--focus-chat {
  grid-template-columns: minmax(400px, 40%) minmax(0, 1fr);
}

.side-panel {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-height: 100vh;
  overflow: hidden;
  background: #fff;
  border-right: 1px solid #eceae8;
  box-shadow: 4px 0 24px rgba(28, 25, 23, 0.04);
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid #f0eeec;
}

.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  object-fit: contain;
  background: #fff;
}

.brand-row strong {
  display: block;
  font-size: 14px;
  line-height: 1.2;
}

.brand-row span {
  font-size: 11px;
  color: #a8a29e;
  font-weight: 600;
}

.rider-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #f0eeec;
}

.rider-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.rider-avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 800;
  flex-shrink: 0;
  overflow: hidden;
  background: #e7e5e4;
}

.rider-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.rider-info strong {
  display: block;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rider-meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 2px;
}

.rider-badge {
  font-size: 11px;
  color: #059669;
  font-weight: 600;
}

.rider-rating {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 7px;
  border: 0;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.4;
  background: #f5f5f4;
  color: #78716c;
  cursor: pointer;
  font-family: inherit;
}

.rider-rating:hover {
  filter: brightness(0.97);
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.06);
}

.rider-rating i {
  font-style: normal;
  color: #f59e0b;
  font-size: 10px;
}

.rider-rating em {
  font-style: normal;
  font-weight: 800;
}

.rider-rating small {
  font-size: 10px;
  font-weight: 600;
  opacity: 0.75;
}

.rider-rating--good {
  background: #ecfdf5;
  color: #047857;
}

.rider-rating--ok {
  background: #eff6ff;
  color: #1d4ed8;
}

.rider-rating--mid {
  background: #fffbeb;
  color: #b45309;
}

.rider-rating--low {
  background: #fef2f2;
  color: #b91c1c;
}

.rider-rating--empty {
  background: #f5f5f4;
  color: #a8a29e;
  font-weight: 600;
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
}

.stat-strip {
  display: grid;
  grid-template-columns: 1.1fr 1fr 0.8fr;
  gap: 8px;
  padding: 10px 14px 12px;
}

.stat-item {
  padding: 10px 8px;
  border-radius: 12px;
  background: #fafaf9;
  border: 1px solid #eceae8;
  text-align: center;
}

.stat-item--good {
  background: #ecfdf5;
  border-color: #bbf7d0;
}

.stat-item--ok {
  background: #fffbeb;
  border-color: #fde68a;
}

.stat-item--bad {
  background: #fef2f2;
  border-color: #fecaca;
}

.stat-item--money strong {
  color: #059669;
}

.stat-withdraw-btn {
  margin-top: 6px;
  border: 0;
  border-radius: 999px;
  padding: 3px 10px;
  background: #ecfdf5;
  color: #047857;
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
}

.stat-withdraw-btn:hover {
  background: #d1fae5;
}

.stat-label {
  display: block;
  font-size: 10px;
  color: #a8a29e;
  font-weight: 600;
}

.stat-item strong {
  display: block;
  margin-top: 2px;
  font-size: 15px;
  line-height: 1.2;
}

.stat-item small {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  color: #78716c;
}

.stat-item--bad strong {
  color: #dc2626;
}

.tab-nav {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  padding: 0 14px 10px;
}

.tab-btn {
  border: 1.5px solid #eceae8;
  background: #fafaf9;
  border-radius: 10px;
  padding: 9px 4px;
  font-size: 12px;
  font-weight: 700;
  color: #57534e;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.tab-btn em {
  font-style: normal;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #059669;
  color: #fff;
  font-size: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.tab-btn.active {
  background: #ecfdf5;
  border-color: #6ee7b7;
  color: #065f46;
}

.list-tools {
  flex-shrink: 0;
  padding: 0 14px 10px;
}

.list-search {
  position: relative;
}

.list-sync {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
}

.list-search svg {
  position: absolute;
  left: 26px;
  top: 50%;
  transform: translateY(-50%);
  width: 16px;
  height: 16px;
  color: #a8a29e;
  pointer-events: none;
}

.list-search input {
  width: 100%;
  border: 1.5px solid #eceae8;
  border-radius: 12px;
  padding: 10px 12px 10px 36px;
  background: #fafaf9;
  font-size: 13px;
}

.list-search input:focus {
  outline: none;
  border-color: #6ee7b7;
  background: #fff;
}

.sort-bar {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: none;
}

.sort-bar::-webkit-scrollbar {
  display: none;
}

.sort-chip {
  flex-shrink: 0;
  border: 1.5px solid #eceae8;
  background: #fafaf9;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 11px;
  font-weight: 700;
  color: #57534e;
  cursor: pointer;
}

.sort-chip.active {
  background: #ecfdf5;
  border-color: #6ee7b7;
  color: #065f46;
}

.task-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 2px 10px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.list-hint {
  text-align: center;
  color: #a8a29e;
  font-size: 13px;
  padding: 16px;
}

.task-item {
  width: 100%;
  border: 1px solid #eceae8;
  background: #fff;
  text-align: left;
  padding: 13px 14px;
  border-radius: 14px;
  margin: 0;
  box-shadow: 0 1px 2px rgba(28, 25, 23, 0.04);
  transition: background 0.12s, border-color 0.12s, box-shadow 0.12s;
}

.task-item:hover {
  background: #fafaf9;
  border-color: #e7e5e4;
}

.task-item.active {
  background: #ecfdf5;
  border-color: #6ee7b7;
  box-shadow: inset 0 0 0 1px #6ee7b7;
}

.task-item--urgent {
  background: linear-gradient(135deg, #fff7ed 0%, #fffbeb 100%);
  border-color: #fdba74;
  box-shadow: inset 0 0 0 1px rgba(253, 186, 116, 0.45);
}

.task-item--urgent.active {
  background: linear-gradient(135deg, #ffedd5 0%, #ecfdf5 100%);
  border-color: #f97316;
  box-shadow: inset 0 0 0 1px #f97316;
}

.task-item-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.task-item-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.task-type {
  font-size: 11px;
  font-weight: 700;
  color: #78716c;
  background: #f5f5f4;
  padding: 3px 8px;
  border-radius: 999px;
}

.urgent-badge {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 800;
  color: #c2410c;
  background: #ffedd5;
  padding: 3px 8px;
  border-radius: 999px;
  letter-spacing: 0.02em;
}

.urgent-badge--lg {
  font-size: 12px;
  padding: 4px 10px;
}

.task-title {
  display: block;
  font-size: 16px;
  line-height: 1.35;
  margin-bottom: 6px;
}

.task-item-reward {
  flex-shrink: 0;
  text-align: right;
}

.reward {
  display: block;
  color: #059669;
  font-weight: 800;
  font-size: 16px;
}

.urgent-bonus {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  font-weight: 700;
  color: #ea580c;
  line-height: 1.2;
}

.detail-urgent-bonus {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
  color: #ea580c;
}

.task-item-route {
  margin: 0 0 8px;
  font-size: 13px;
  color: #78716c;
  line-height: 1.45;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.task-item-route i {
  font-style: normal;
  color: #d6d3d1;
}

.task-item-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.task-item-foot time {
  font-size: 11px;
  color: #a8a29e;
}

.task-chat-badge {
  margin-left: auto;
  padding: 2px 8px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 10px;
  font-weight: 700;
}

.status-badge {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.status-badge.lg {
  font-size: 12px;
  padding: 4px 10px;
}

.badge-open { background: #dbeafe; color: #1d4ed8; }
.badge-claimed { background: #fef3c7; color: #b45309; }
.badge-pending { background: #ede9fe; color: #6d28d9; }
.badge-done { background: #d1fae5; color: #047857; }
.badge-muted { background: #f5f5f4; color: #78716c; }

.list-empty {
  padding: 28px 16px;
  text-align: center;
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.list-empty p {
  margin: 0 0 6px;
  font-weight: 700;
  font-size: 14px;
}

.list-empty small {
  color: #a8a29e;
  font-size: 12px;
  line-height: 1.6;
}

.sync-hint {
  font-size: 11px;
  color: #a8a29e;
  white-space: nowrap;
}

.btn-outline-sm {
  border: 1.5px solid #e7e5e4;
  background: #fff;
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 12px;
  font-weight: 600;
  color: #57534e;
}

.btn-refresh {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
}

.btn-refresh svg {
  width: 14px;
  height: 14px;
}

.btn-refresh svg.spinning {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* ── 详情区 ── */
.detail-panel {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.global-alert {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px 20px;
  background: #fef2f2;
  border-bottom: 1px solid #fecaca;
  color: #991b1b;
}

.global-alert svg {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  margin-top: 2px;
}

.global-alert strong {
  display: block;
  font-size: 13px;
  margin-bottom: 2px;
}

.global-alert p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  font-weight: 500;
}

.detail-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 28px;
  background: #fff;
  border-bottom: 1px solid #eceae8;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.type-chip {
  display: inline-flex;
  padding: 4px 10px;
  border-radius: 999px;
  background: #f5f5f4;
  color: #57534e;
  font-size: 12px;
  font-weight: 700;
}

.detail-hero h2 {
  margin: 0;
  font-size: clamp(18px, 1.8vw, 22px);
  font-weight: 800;
  line-height: 1.3;
}

.detail-meta {
  margin: 8px 0 0;
  font-size: 13px;
  color: #a8a29e;
}

.detail-reward-box {
  flex-shrink: 0;
  text-align: center;
  padding: 14px 20px;
  border-radius: 16px;
  background: linear-gradient(135deg, #ecfdf5, #d1fae5);
  border: 1px solid #bbf7d0;
  min-width: 120px;
}

.detail-reward-box span {
  display: block;
  font-size: 12px;
  color: #047857;
  font-weight: 600;
}

.detail-reward-box strong {
  display: block;
  margin-top: 4px;
  font-size: 24px;
  color: #059669;
  font-weight: 800;
}

.progress-bar {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 14px 28px;
  background: #fff;
  border-bottom: 1px solid #f0eeec;
}

.progress-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 64px;
}

.progress-step i {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-style: normal;
  font-size: 12px;
  font-weight: 800;
  background: #f5f5f4;
  color: #a8a29e;
  border: 2px solid #e7e5e4;
}

.progress-step span {
  font-size: 11px;
  color: #a8a29e;
  font-weight: 600;
}

.progress-step.done i {
  background: #d1fae5;
  color: #047857;
  border-color: #6ee7b7;
}

.progress-step.current i {
  background: #059669;
  color: #fff;
  border-color: #059669;
  box-shadow: 0 0 0 4px rgba(5, 150, 105, 0.15);
}

.progress-step.current span {
  color: #065f46;
}

.progress-line {
  flex: 1;
  height: 2px;
  background: #e7e5e4;
  margin: 0 4px;
  margin-bottom: 18px;
}

.progress-line.done {
  background: #6ee7b7;
}

.detail-panel--chat {
  background: #ebebeb;
}

.delivery-chat-page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.messenger-topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #ededed;
  border-bottom: 1px solid #d9d9d9;
  flex-shrink: 0;
}

.messenger-back {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: none;
  background: transparent;
  color: #10b981;
  font-size: 14px;
  font-weight: 600;
  padding: 6px 4px;
  flex-shrink: 0;
  cursor: pointer;
}

.messenger-back svg {
  width: 18px;
  height: 18px;
}

.messenger-peer {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.messenger-avatar {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  background: #10b981;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  overflow: hidden;
}

.messenger-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.messenger-peer-text {
  min-width: 0;
}

.messenger-peer-text strong {
  display: block;
  font-size: 16px;
  color: #111;
  line-height: 1.3;
}

.messenger-peer-text span {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #888;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.messenger-topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.messenger-reward {
  font-size: 14px;
  font-weight: 800;
  color: #047857;
  padding: 4px 10px;
  background: #ecfdf5;
  border-radius: 8px;
}

.messenger-btn {
  border: none;
  background: #fff;
  color: #555;
  font-size: 13px;
  font-weight: 600;
  padding: 7px 12px;
  border-radius: 8px;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

.messenger-btn--primary {
  background: #10b981;
  color: #fff;
}

.messenger-btn:hover:not(:disabled) {
  filter: brightness(0.97);
}

.messenger-btn--primary:hover:not(:disabled) {
  background: #059669;
}

.detail-view-tabs {
  display: flex;
  gap: 0;
  padding: 12px 24px 0;
  background: #fff;
  border-bottom: 1px solid #eceae8;
  flex-shrink: 0;
}

.detail-view-tab {
  border: none;
  background: transparent;
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 600;
  color: #78716c;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.detail-view-tab.active {
  color: #047857;
  border-bottom-color: #10b981;
}

.detail-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 20px 28px 28px;
  background:
    radial-gradient(circle at 20% 0%, rgba(16, 185, 129, 0.06), transparent 35%),
    #f5f5f4;
}

.detail-body--chat {
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.detail-chat-full {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.detail-info-full {
  width: 100%;
  max-width: 640px;
}

.detail-block {
  margin-bottom: 20px;
}

.detail-block h3 {
  margin: 0 0 10px;
  font-size: 13px;
  color: #78716c;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.route-card {
  background: #fff;
  border: 1px solid #eceae8;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(28, 25, 23, 0.04);
}

.route-point {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.route-point i {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-style: normal;
  font-size: 12px;
  font-weight: 800;
  flex-shrink: 0;
}

.route-point.from i { background: #dbeafe; color: #1d4ed8; }
.route-point.to i { background: #d1fae5; color: #047857; }

.route-point strong {
  display: block;
  font-size: 11px;
  color: #a8a29e;
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.route-point p {
  margin: 0;
  font-size: 15px;
  line-height: 1.5;
  font-weight: 600;
}

.route-line {
  position: relative;
  width: 3px;
  height: 28px;
  margin: 6px 0 6px 13.5px;
  border-radius: 999px;
  background: #e7e5e4;
  overflow: hidden;
}

.route-line-flow {
  position: absolute;
  left: 0;
  width: 100%;
  height: 55%;
  border-radius: inherit;
  background: linear-gradient(
    to bottom,
    transparent 0%,
    #60a5fa 35%,
    #34d399 70%,
    transparent 100%
  );
  animation: route-flow 1.25s ease-in-out infinite;
}

@keyframes route-flow {
  0% {
    top: -55%;
    opacity: 0.35;
  }
  20% {
    opacity: 1;
  }
  80% {
    opacity: 1;
  }
  100% {
    top: 100%;
    opacity: 0.35;
  }
}

@media (prefers-reduced-motion: reduce) {
  .route-line-flow {
    animation: none;
    top: 22%;
    opacity: 1;
  }
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin: 0;
  background: #fff;
  border: 1px solid #eceae8;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(28, 25, 23, 0.04);
}

.info-grid div.full { grid-column: 1 / -1; }

.info-publisher {
  padding-bottom: 4px;
  border-bottom: 1px solid #f5f5f4;
  margin-bottom: 2px;
}

.publisher-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.publisher-avatar {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 800;
  flex-shrink: 0;
  overflow: hidden;
  background: #e7e5e4;
}

.publisher-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.publisher-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.publisher-text strong {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.3;
}

.publisher-text small {
  font-size: 12px;
  color: #a8a29e;
  font-weight: 500;
}

.urgent-bonus-dd {
  color: #ea580c;
  font-weight: 700;
}

.info-grid dt {
  font-size: 12px;
  color: #a8a29e;
  margin-bottom: 4px;
}

.info-grid dd {
  margin: 0;
  font-size: 14px;
  line-height: 1.55;
  word-break: break-word;
}

.contact-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.btn-copy {
  border: 1px solid #e7e5e4;
  background: #fafaf9;
  border-radius: 8px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 600;
  color: #57534e;
}

.btn-copy:hover {
  border-color: #6ee7b7;
  color: #047857;
}

.detail-actions {
  padding: 16px 28px 24px;
  background: #fff;
  border-top: 1px solid #eceae8;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  flex-shrink: 0;
}

.btn-outline {
  border: 1.5px solid #e7e5e4;
  background: #fff;
  color: #57534e;
  border-radius: 10px;
  padding: 12px 18px;
  font-size: 14px;
  font-weight: 600;
}

.btn-outline:hover:not(:disabled) {
  border-color: #fecaca;
  color: #dc2626;
}

.btn-link {
  margin-left: auto;
  font-size: 13px;
  font-weight: 600;
  color: #059669;
  text-decoration: none;
}

.btn-link:hover {
  text-decoration: underline;
}

.action-hint {
  margin: 0;
  font-size: 13px;
  color: #78716c;
  width: 100%;
}

.action-hint.success {
  color: #047857;
  font-weight: 600;
}

.detail-welcome {
  flex: 1;
  display: grid;
  place-items: center;
  padding: 32px;
  background:
    radial-gradient(circle at 70% 20%, rgba(16, 185, 129, 0.08), transparent 40%),
    #f5f5f4;
}

.welcome-card {
  text-align: center;
  max-width: 440px;
  padding: 36px 32px;
  background: #fff;
  border-radius: 24px;
  border: 1px solid #eceae8;
  box-shadow: 0 20px 50px rgba(28, 25, 23, 0.06);
}

.welcome-logo {
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
  border-radius: 16px;
}

.welcome-card h2 {
  margin: 0 0 10px;
  font-size: 20px;
}

.welcome-card > p {
  margin: 0;
  color: #78716c;
  font-size: 14px;
  line-height: 1.65;
}

.welcome-card strong {
  color: #059669;
}

.welcome-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 24px;
}

.welcome-stats div {
  padding: 12px 8px;
  border-radius: 12px;
  background: #fafaf9;
}

.welcome-stats span {
  display: block;
  font-size: 11px;
  color: #a8a29e;
  font-weight: 600;
}

.welcome-stats strong {
  display: block;
  margin-top: 4px;
  font-size: 16px;
  color: #1c1917;
}

.toast {
  position: fixed;
  left: 50%;
  bottom: 28px;
  transform: translateX(-50%);
  padding: 12px 20px;
  border-radius: 12px;
  background: rgba(28, 25, 23, 0.92);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  z-index: 100;
  max-width: min(90vw, 480px);
  text-align: center;
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(8px);
}

.withdraw-mask {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
}

.withdraw-sheet {
  width: min(440px, 100%);
  max-height: min(88vh, 640px);
  overflow: auto;
  padding: 20px;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.18);
}

.withdraw-sheet--docked {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0;
}

.withdraw-sheet--docked .withdraw-head {
  flex-shrink: 0;
  margin-bottom: 0;
  padding: 18px 20px 14px;
  border-bottom: 1px solid #f0eeec;
  background: #fff;
  border-radius: 20px 20px 0 0;
  z-index: 2;
}

.withdraw-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 20px 20px;
  scrollbar-width: thin;
  scrollbar-color: #c4c0bb transparent;
}

.withdraw-body::-webkit-scrollbar {
  width: 8px;
}

.withdraw-body::-webkit-scrollbar-track {
  background: transparent;
  margin: 10px 4px 10px 0;
}

.withdraw-body::-webkit-scrollbar-thumb {
  background: #d6d3d1;
  border-radius: 999px;
  border: 2px solid transparent;
  background-clip: padding-box;
}

.withdraw-body::-webkit-scrollbar-thumb:hover {
  background: #a8a29e;
  border: 2px solid transparent;
  background-clip: padding-box;
}

.withdraw-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.withdraw-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
}

.withdraw-head p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #78716c;
}

.withdraw-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: #f5f5f4;
  color: #78716c;
  font-size: 20px;
  line-height: 1;
  flex-shrink: 0;
  cursor: pointer;
}

.withdraw-close:hover {
  background: #eceae8;
}

.rating-sheet {
  width: min(480px, 100%);
}

.rating-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 16px;
}

.rating-summary div {
  padding: 10px 8px;
  border-radius: 12px;
  background: #fafaf9;
  border: 1px solid #eceae8;
  text-align: center;
}

.rating-summary span {
  display: block;
  font-size: 11px;
  color: #a8a29e;
  margin-bottom: 4px;
}

.rating-summary strong {
  font-size: 18px;
  font-weight: 800;
  color: #1c1917;
}

.rating-level-text--good { color: #047857; }
.rating-level-text--ok { color: #1d4ed8; }
.rating-level-text--mid { color: #b45309; }
.rating-level-text--low { color: #b91c1c; }
.rating-level-text--empty { color: #a8a29e; font-size: 14px !important; }

.rating-list-hint {
  margin: 8px 0 0;
  padding: 20px 12px;
  text-align: center;
  font-size: 13px;
  color: #a8a29e;
  background: #fafaf9;
  border-radius: 12px;
}

.rating-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: min(52vh, 420px);
  overflow: auto;
}

.rating-list li {
  padding: 12px 14px;
  border-radius: 14px;
  background: #fafaf9;
  border: 1px solid #eceae8;
}

.rating-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.rating-item-score {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 800;
  color: #b45309;
}

.rating-item-score i {
  font-style: normal;
  font-size: 11px;
  letter-spacing: -1px;
  color: #f59e0b;
}

.rating-item-comment {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: #44403c;
}

.rating-item-comment--empty {
  color: #a8a29e;
}

.rating-item-meta {
  margin: 0;
  font-size: 11px;
  color: #a8a29e;
  font-weight: 600;
}

.withdraw-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 16px;
}

.withdraw-summary div {
  padding: 10px 8px;
  border-radius: 12px;
  background: #fafaf9;
  border: 1px solid #eceae8;
  text-align: center;
}

.withdraw-summary span {
  display: block;
  font-size: 10px;
  color: #a8a29e;
  font-weight: 600;
}

.withdraw-summary strong {
  display: block;
  margin-top: 4px;
  font-size: 14px;
  color: #059669;
}

.withdraw-form {
  display: grid;
  gap: 12px;
}

.withdraw-form .field input:focus {
  border-color: #d6d3d1;
  box-shadow: none;
  background: #fff;
}

.withdraw-rules {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.withdraw-rules p {
  margin: 0 0 6px;
  font-size: 13px;
  color: #1e293b;
}

.withdraw-rules ul {
  margin: 0;
  padding-left: 1.1em;
  display: grid;
  gap: 4px;
  font-size: 12px;
  line-height: 1.55;
  color: #475569;
}

.withdraw-rules em {
  font-style: normal;
  font-weight: 700;
  color: #0f766e;
}

.withdraw-fee-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: -4px;
  padding: 0 2px;
  font-size: 13px;
  color: #57534e;
}

.withdraw-fee-row strong {
  color: #0f766e;
  font-weight: 700;
}

.withdraw-amount-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.withdraw-max-btn {
  border: 0;
  background: transparent;
  color: #059669;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  padding: 0;
}

.withdraw-max-btn:hover {
  text-decoration: underline;
}

.withdraw-form .field input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.withdraw-hint {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
}

.withdraw-hint--ok {
  background: #ecfdf5;
  color: #047857;
}

.withdraw-hint--err {
  background: #fef2f2;
  color: #dc2626;
}

.withdraw-records {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #f0eeec;
}

.withdraw-records h3 {
  margin: 0 0 10px;
  font-size: 13px;
  color: #78716c;
}

.withdraw-records ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.withdraw-records li {
  padding: 10px 12px;
  border-radius: 12px;
  background: #fafaf9;
  border: 1px solid #eceae8;
}

.wd-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.wd-row strong {
  color: #059669;
  font-size: 14px;
}

.wd-badge {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.wd-badge--pending {
  background: #fef3c7;
  color: #b45309;
}

.wd-badge--ok {
  background: #d1fae5;
  color: #047857;
}

.wd-badge--reject {
  background: #fee2e2;
  color: #dc2626;
}

.wd-meta {
  margin: 4px 0 0;
  font-size: 11px;
  color: #a8a29e;
}

.wd-reject {
  margin: 4px 0 0;
  font-size: 11px;
  color: #dc2626;
}

.withdraw-tip {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #78716c;
}

.withdraw-tip a {
  color: #059669;
  font-weight: 600;
}

.complete-mask {
  position: fixed;
  inset: 0;
  z-index: 95;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
}

.complete-sheet {
  width: min(560px, 100%);
  max-height: min(88vh, 720px);
  overflow: auto;
  padding: 20px;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.18);
}

.complete-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.complete-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
}

.complete-head p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #78716c;
  line-height: 1.5;
}

.complete-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: #f5f5f4;
  color: #78716c;
  font-size: 20px;
  line-height: 1;
  flex-shrink: 0;
  cursor: pointer;
}

.complete-textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1.5px solid #e7e5e4;
  border-radius: 12px;
  padding: 12px 14px;
  background: #fafaf9;
  font-size: 14px;
  line-height: 1.55;
  resize: vertical;
}

.complete-textarea:focus {
  outline: none;
  border-color: #6ee7b7;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

.detail-proof-block {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed #d6deea;
}

.detail-proof-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.detail-proof-head h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #1c1917;
}

.detail-proof-head span {
  font-size: 12px;
  color: #78716c;
  font-weight: 600;
}

.detail-proof-note {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #334155;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-proof-empty {
  margin: 0;
  font-size: 13px;
  color: #78716c;
  line-height: 1.5;
}

.task-proof-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  color: #0f766e;
  background: #ccfbf1;
  border: 1px solid #99f6e4;
}

.complete-proof-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.complete-proof-file {
  display: none;
}

.complete-proof-count {
  font-size: 12px;
  color: #78716c;
  font-weight: 600;
}

.complete-proof-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 10px;
}

.complete-proof-item {
  position: relative;
  border: 1px solid #dbe3ef;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}

.complete-proof-item img {
  width: 100%;
  height: 92px;
  object-fit: cover;
  display: block;
  cursor: zoom-in;
}

.complete-proof-remove {
  width: 100%;
  border: 0;
  border-radius: 0;
  padding: 6px 8px;
  background: #fee2e2;
  color: #991b1b;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.complete-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.preview-mask {
  z-index: 96;
}

.preview-image {
  max-width: min(92vw, 920px);
  max-height: 88vh;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
}

@media (max-width: 1100px) {
  .workbench {
    grid-template-columns: minmax(400px, 44%) minmax(0, 1fr);
  }
  .workbench--focus-chat {
    grid-template-columns: minmax(360px, 38%) minmax(0, 1fr);
  }
}

@media (max-width: 960px) {
  .login-scene { grid-template-columns: 1fr; }
  .login-visual { min-height: auto; padding: 20px 16px 16px; }
  .login-float,
  .login-glow { display: none; }
  .login-visual-stack { width: min(100%, 420px); gap: 10px; }
  .login-brand-card { padding: 20px 28px 16px; }
  .login-brand-logo { width: 64px; height: 64px; margin-bottom: 8px; }
  .login-brand-card p { font-size: 16px; }
  .login-tips { padding: 12px 14px 10px; }
  .login-tips ul { gap: 7px; }
  .login-panel { padding: 28px 22px 36px; }
  .workbench { grid-template-columns: 1fr; }
  .side-panel { height: auto; max-height: 52vh; overflow: hidden; }
  .detail-hero { flex-direction: column; }
  .detail-reward-box { align-self: flex-start; }
  .btn-link { margin-left: 0; width: 100%; text-align: center; }
  .workbench--focus-chat { grid-template-columns: 1fr; }
}
</style>
