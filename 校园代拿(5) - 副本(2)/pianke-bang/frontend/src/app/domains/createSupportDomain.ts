import { computed, ref, type Ref } from 'vue'
import { apiOrigin, authBase } from '../../constants/api'
import type {
  ChatMessageItem,
  TaskItem,
  TaskStatus,
  UserPublicProfile,
  WalletOverview,
  WalletTransactionItem,
  WalletWithdrawItem
} from '../../types/app'
import { resolveTaskStatusLabel, statusLabelMap } from '../../utils/taskStatus'
import { usernameMatches } from '../../utils/taskParse'

type SupportRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type SupportDomainDeps = {
  isLoggedIn: Ref<boolean>
  myName: Ref<string>
  request: SupportRequest
  errorMsg: Ref<string>
  showRealtimeToast: (payload: {
    title: string
    message: string
    kind: 'chat' | 'task'
    chatUser?: string
  }) => void
  goWallet: (subModule?: 'overview' | 'withdraws' | 'transactions') => void | Promise<void>
  goProfile: () => void
  goRiderApply: () => void
  goCoupons: () => void | Promise<void>
  openTaskDetail: (task: TaskItem) => void | Promise<void>
  fetchMyTasksForProfile: () => void | Promise<void>
  fetchWalletData: () => void | Promise<void>
  walletOverview: Ref<WalletOverview>
  walletTransactions: Ref<WalletTransactionItem[]>
  walletWithdraws: Ref<WalletWithdrawItem[]>
  mineTasks: Ref<TaskItem[]>
  allTasks: Ref<TaskItem[]>
  tasks: Ref<TaskItem[]>
  creditScore: Ref<number>
  blockedUntil: Ref<string>
  isRiderApproved: Ref<boolean>
  riderApplicationPending: Ref<boolean>
  openAuthPage: (mode: 'login' | 'register') => void
  hallFaqItems: ReadonlyArray<{ question: string; answer: string }>
  unreadMap: Ref<Record<string, number>>
}

export type SupportChatMessage = {
  role: 'user' | 'assistant' | 'human' | 'system'
  content: string
  card?: SupportToolCard | null
  chatMsgId?: number
}

export type SupportToolCard =
  | {
      kind: 'wallet'
      balance?: number | string
      totalIncome?: number | string
      totalWithdrawn?: number | string
      withdrawingAmount?: number | string
    }
  | {
      kind: 'tasks'
      total?: number
      matched?: number
      shown?: number
      scope?: string
      scopeLabel?: string
      tasks?: Array<{
        id?: number | string
        title?: string
        status?: string
        statusLabel?: string
        reward?: number | string
        role?: string
        roleLabel?: string
      }>
    }
  | {
      kind: 'task_detail'
      id?: number | string
      title?: string
      status?: string
      statusLabel?: string
      reward?: number | string
      role?: string
      roleLabel?: string
      pickupLocation?: string
      dropoffLocation?: string
      latestDeliveryAt?: string
      updatedAt?: string
      bountyPrepaid?: boolean
    }
  | {
      kind: 'withdrawals'
      matched?: number
      shown?: number
      items?: Array<{
        id?: number | string
        amount?: number | string
        fee?: number | string
        payout?: number | string
        status?: string
        statusLabel?: string
        accountMasked?: string
        auditRemark?: string
        createdAt?: string
      }>
    }
  | {
      kind: 'credit'
      creditScore?: number
      minToClaim?: number
      riderApproved?: boolean
      blocked?: boolean
      blockedUntil?: string
      canClaim?: boolean
    }
  | {
      kind: 'rider_cert'
      riderApproved?: boolean
      riderApplicationPending?: boolean
      statusLabel?: string
      appliedAt?: string
    }
  | {
      kind: 'appeals'
      matched?: number
      shown?: number
      items?: Array<{
        taskId?: number | string
        title?: string
        taskStatusLabel?: string
        appealApproved?: boolean
        appealStatusLabel?: string
        resultHint?: string
        appealRemark?: string
        auditedAt?: string
      }>
    }
  | {
      kind: 'coupons'
      matched?: number
      unusedCount?: number
      usedCount?: number
      shown?: number
      items?: Array<{
        id?: number | string
        name?: string
        status?: string
        statusLabel?: string
        discountAmount?: number | string
        discountPercent?: number | string
        expireAt?: string
        bannerImageUrl?: string
      }>
    }
  | {
      kind: 'urgent_quota'
      weeklyLimit?: number
      used?: number
      remaining?: number
      feeMin?: number | string
      feeMax?: number | string
      boostMinutes?: number | string
    }
  | {
      kind: 'transactions'
      matched?: number
      shown?: number
      items?: Array<{
        id?: number | string
        type?: string
        typeLabel?: string
        amount?: number | string
        balanceAfter?: number | string
        taskId?: number | string
        remark?: string
        createdAt?: string
      }>
    }
  | {
      kind: 'handoff'
      status: 'waiting' | 'joined' | 'offline'
      agent?: string
    }

/**
 * 智能客服 / 人工转接：聊天窗状态、工具卡片、人工轮询与评价。
 */
export function createSupportDomain(deps: SupportDomainDeps) {
  const safeAllTasks = computed(() => (Array.isArray(deps.allTasks.value) ? deps.allTasks.value : []))

  const showSupportChat = ref(false)
  const supportChatSending = ref(false)
  const supportTransferLoading = ref(false)
  const supportChatMessages = ref<SupportChatMessage[]>([])
  const supportAgentOnline = ref<boolean | null>(null)
  const supportAgentStatusText = ref('')
  /** 人工客服在智能客服窗内接入（不跳通讯录） */
  const supportHumanMode = ref(false)
  const supportAssignedAgent = ref('')
  const supportAssignedAgentAvatar = ref('')
  const supportHandoffJoined = ref(false)
  /** 仅同步此 ID 之后的客服消息，避免把历史会话灌进本次窗口 */
  const supportHumanHistoryAfterId = ref(0)
  let supportHumanPollTimer: number | null = null
  const supportIngestedChatIds = ref<Set<number>>(new Set())
  
  const SUPPORT_CS_RATING_TAGS = ['解决了问题', '态度很好', '回复及时', '专业耐心', '有待改进'] as const
  const supportCsRatingVisible = ref(false)
  const supportCsRatingAgent = ref('')
  const supportCsRatingScore = ref(5)
  const supportCsRatingTags = ref<string[]>([])
  const supportCsRatingComment = ref('')
  const supportCsRatingSubmitting = ref(false)
  const supportCsRatingDone = ref(false)

  function openSupportChat() {
    showSupportChat.value = true
    void refreshSupportAgentAvailability()
  }
  
  async function refreshSupportAgentAvailability() {
    if (!deps.isLoggedIn.value) {
      supportAgentOnline.value = null
      supportAgentStatusText.value = '登录后可转接人工客服'
      return
    }
    try {
      const res = await deps.request<{
        online?: boolean
        message?: string
        configured?: boolean
        onlineCount?: number
      }>(`${apiOrigin}/api/support/agent-availability`)
      supportAgentOnline.value = !!res.online
      supportAgentStatusText.value =
        res.message ||
        (res.online ? '人工客服在线，可为您转接' : '人工客服当前不在线，请稍后再试或继续使用智能客服')
    } catch {
      supportAgentOnline.value = null
      supportAgentStatusText.value = ''
    }
  }
  
  function closeSupportChat() {
    showSupportChat.value = false
  }
  
  /** 退出/切账号时清空客服窗，避免未刷新时串到下一账号 */
  function clearSupportChatState() {
    stopSupportHumanPolling()
    showSupportChat.value = false
    supportChatSending.value = false
    supportTransferLoading.value = false
    supportChatMessages.value = []
    supportAgentOnline.value = null
    supportAgentStatusText.value = ''
    supportHumanMode.value = false
    supportAssignedAgent.value = ''
    supportAssignedAgentAvatar.value = ''
    supportHandoffJoined.value = false
    supportHumanHistoryAfterId.value = 0
    supportIngestedChatIds.value = new Set()
    resetSupportCsRatingForm()
  }
  
  function normalizeSupportToolCard(
    source: string | undefined,
    payload: SupportToolCard | Record<string, unknown> | null | undefined
  ): SupportToolCard | null {
    if (!payload || typeof payload !== 'object') return null
    const kind = String((payload as { kind?: string }).kind || '').trim()
    if (kind === 'wallet' || source === 'tool:wallet') {
      const p = payload as Record<string, unknown>
      const num = (v: unknown) => {
        const n = Number(v)
        return Number.isFinite(n) ? n : 0
      }
      return {
        kind: 'wallet',
        balance: num(p.balance),
        totalIncome: num(p.totalIncome ?? p.total_income),
        totalWithdrawn: num(p.totalWithdrawn ?? p.total_withdrawn),
        withdrawingAmount: num(p.withdrawingAmount ?? p.withdrawing_amount)
      }
    }
    if (kind === 'tasks' || source === 'tool:tasks') {
      const p = payload as Extract<SupportToolCard, { kind: 'tasks' }>
      return {
        kind: 'tasks',
        total: Number(p.total ?? p.matched ?? 0),
        matched: Number(p.matched ?? p.total ?? 0),
        shown: Number(p.shown ?? (Array.isArray(p.tasks) ? p.tasks.length : 0)),
        scope: p.scope,
        scopeLabel: p.scopeLabel,
        tasks: Array.isArray(p.tasks) ? p.tasks : []
      }
    }
    if (kind === 'task_detail' || source === 'tool:task_detail' || source === 'tool:task_timeline') {
      const p = payload as Extract<SupportToolCard, { kind: 'task_detail' }>
      return {
        kind: 'task_detail',
        id: p.id,
        title: p.title,
        status: p.status,
        statusLabel: p.statusLabel,
        reward: p.reward,
        role: p.role,
        roleLabel: p.roleLabel,
        pickupLocation: p.pickupLocation,
        dropoffLocation: p.dropoffLocation,
        latestDeliveryAt: p.latestDeliveryAt,
        updatedAt: p.updatedAt,
        bountyPrepaid: !!p.bountyPrepaid
      }
    }
    if (kind === 'withdrawals' || source === 'tool:withdrawals') {
      const p = payload as Extract<SupportToolCard, { kind: 'withdrawals' }>
      return {
        kind: 'withdrawals',
        matched: Number(p.matched ?? 0),
        shown: Number(p.shown ?? (Array.isArray(p.items) ? p.items.length : 0)),
        items: Array.isArray(p.items) ? p.items : []
      }
    }
    if (kind === 'credit' || source === 'tool:credit') {
      const p = payload as Extract<SupportToolCard, { kind: 'credit' }>
      return {
        kind: 'credit',
        creditScore: Number(p.creditScore ?? 80),
        minToClaim: Number(p.minToClaim ?? 60),
        riderApproved: !!p.riderApproved,
        blocked: !!p.blocked,
        blockedUntil: p.blockedUntil || '',
        canClaim: !!p.canClaim
      }
    }
    if (kind === 'rider_cert' || source === 'tool:rider_cert') {
      const p = payload as Extract<SupportToolCard, { kind: 'rider_cert' }>
      return {
        kind: 'rider_cert',
        riderApproved: !!p.riderApproved,
        riderApplicationPending: !!p.riderApplicationPending,
        statusLabel: p.statusLabel || '',
        appliedAt: p.appliedAt || ''
      }
    }
    if (kind === 'appeals' || source === 'tool:appeals') {
      const p = payload as Extract<SupportToolCard, { kind: 'appeals' }>
      return {
        kind: 'appeals',
        matched: Number(p.matched ?? 0),
        shown: Number(p.shown ?? (Array.isArray(p.items) ? p.items.length : 0)),
        items: Array.isArray(p.items) ? p.items : []
      }
    }
    if (kind === 'coupons' || source === 'tool:coupons') {
      const p = payload as Extract<SupportToolCard, { kind: 'coupons' }>
      return {
        kind: 'coupons',
        matched: Number(p.matched ?? 0),
        unusedCount: Number(p.unusedCount ?? 0),
        usedCount: Number(p.usedCount ?? 0),
        shown: Number(p.shown ?? (Array.isArray(p.items) ? p.items.length : 0)),
        items: Array.isArray(p.items) ? p.items : []
      }
    }
    if (kind === 'urgent_quota' || source === 'tool:urgent_quota') {
      const p = payload as Extract<SupportToolCard, { kind: 'urgent_quota' }>
      return {
        kind: 'urgent_quota',
        weeklyLimit: Number(p.weeklyLimit ?? 3),
        used: Number(p.used ?? 0),
        remaining: Number(p.remaining ?? 0),
        feeMin: p.feeMin,
        feeMax: p.feeMax,
        boostMinutes: p.boostMinutes
      }
    }
    if (kind === 'transactions' || source === 'tool:transactions') {
      const p = payload as Extract<SupportToolCard, { kind: 'transactions' }>
      return {
        kind: 'transactions',
        matched: Number(p.matched ?? 0),
        shown: Number(p.shown ?? (Array.isArray(p.items) ? p.items.length : 0)),
        items: Array.isArray(p.items) ? p.items : []
      }
    }
    return null
  }
  
  async function openSupportWallet() {
    closeSupportChat()
    await deps.goWallet('overview')
  }
  
  async function openSupportMyTasks() {
    closeSupportChat()
    deps.goProfile()
  }
  
  function openSupportRiderApply() {
    closeSupportChat()
    deps.goRiderApply()
  }
  
  function openSupportCoupons() {
    closeSupportChat()
    deps.goCoupons()
  }
  
  async function openSupportTaskById(taskId: number | string) {
    const id = Number(taskId)
    if (!Number.isFinite(id) || id <= 0) return
    closeSupportChat()
    const cached =
      deps.mineTasks.value.find((t) => t.id === id) ||
      safeAllTasks.value.find((t) => t.id === id)
    if (cached) {
      await deps.openTaskDetail(cached)
      return
    }
    try {
      await deps.fetchMyTasksForProfile()
      const hit =
        deps.mineTasks.value.find((t) => t.id === id) ||
        safeAllTasks.value.find((t) => t.id === id)
      if (hit) {
        await deps.openTaskDetail(hit)
        return
      }
      deps.errorMsg.value = `任务 #${id} 不存在或已删除`
    } catch (err) {
      deps.errorMsg.value = (err as Error).message || `无法打开任务 #${id}`
    }
  }
  
  function looksLikeSupportWalletQuery(text: string) {
    const n = text.trim()
    if (/怎么提现|如何提现|提现规则|提现多久|提现到账|我的提现|提现进度|提现记录|提现申请|提现审核/.test(n)) {
      return false
    }
    return /我的钱包|钱包余额|查余额|看看余额|我有多少钱|余额多少|还有多少钱|^余额$|^钱包$/.test(n)
  }
  
  function looksLikeSupportTasksQuery(text: string) {
    const n = text.trim()
    if (
      /怎么发布|如何发布|怎么接单|如何接单|接单流程|如何成为接单员|任务\s*\d+|订单\s*\d+|单号\s*\d+|#\s*\d+|可以取消|能不能取消|怎么取消|如何取消|取消已接|取消规则/.test(
        n
      )
    ) {
      return false
    }
    return /我的任务|我的订单|查任务|查订单|看看任务|任务列表|订单列表|历史任务|待接单|配送中/.test(n)
  }
  
  function looksLikeSupportTaskPolicyHowTo(text: string) {
    const n = text.trim().replace(/[？?。！!，,、\s]/g, '')
    if (/可以取消|能不能取消|能否取消|怎么取消|如何取消|怎样取消|取消已接|取消规则/.test(n)) return true
    return /取消/.test(n) && /可以|能不能|能否|怎么|如何|吗|规则/.test(n)
  }
  
  function findLocalSupportFaqAnswer(text: string): string | null {
    const raw = text.trim()
    const n = raw.replace(/[？?]/g, '')
    for (const item of deps.hallFaqItems) {
      if (item.question === raw || item.question.replace(/[？?]/g, '') === n) {
        return item.answer
      }
    }
    if (looksLikeSupportTaskPolicyHowTo(raw)) {
      return deps.hallFaqItems.find((x) => x.question.includes('取消'))?.answer || null
    }
    return null
  }
  
  function looksLikeSupportWithdrawalsQuery(text: string) {
    const n = text.trim()
    if (/怎么提现|如何提现|提现规则|提现手续费|提现多久/.test(n)) return false
    return /我的提现|提现进度|提现记录|提现申请|提现审核|提现审到|提现中|提现到账了吗/.test(n)
  }
  
  function looksLikeSupportCreditQuery(text: string) {
    const n = text.trim().replace(/[？?。！!，,、\s]/g, '')
    if (/信誉分规则|超时扣多少|怎么恢复信誉|如何恢复信誉|怎么扣信誉|如何扣分/.test(n)) return false
    // 短问与别名：信誉分 / 信用积分 等，不必带「多少」
    if (/^(我的)?(信誉|信誉分|信用分|信用积分)$/.test(n)) return true
    return /信誉分|信用积分|信用分|我的信誉|信誉多少|信誉分多少|信誉分低|接不了单|不能接单|禁止接单|为什么接不了|被禁接|信用.*(多少|我的|分数|积分)|信誉.*(多少|我的|几分|看看|查)/.test(n)
  }
  
  function looksLikeSupportRiderCertQuery(text: string) {
    const n = text.trim()
    if (/如何成为接单员|怎么申请接单员|怎样成为接单员/.test(n)) return false
    return /认证过了吗|认证状态|接单员审核|申请审了|我是接单员吗|接单员通过了吗|接单员申请.*(我的|审|状态|过了)/.test(n)
  }
  
  function parseSupportTaskId(text: string): number | null {
    const m = text.match(/(?:任务|订单|单号|#)\s*(\d{1,12})/i)
    if (!m) return null
    const id = Number(m[1])
    return Number.isFinite(id) && id > 0 ? id : null
  }
  
  /** 用户明确要求转人工：前端直接分配，避免智能客服空口答应 */
  function looksLikeSupportHandoffRequest(text: string) {
    const n = text.replace(/[？?。！!，,、\s]/g, '').toLowerCase()
    return /转人工|转接人工|转接客服|找人工|要人工|接通人工|连人工|帮我转人工|转一下人工/.test(n)
  }
  
  function buildLocalSupportWalletCard(): Extract<SupportToolCard, { kind: 'wallet' }> {
    const w = deps.walletOverview.value || { balance: 0, totalIncome: 0, totalWithdrawn: 0, withdrawingAmount: 0 }
    return {
      kind: 'wallet',
      balance: w.balance ?? 0,
      totalIncome: w.totalIncome ?? 0,
      totalWithdrawn: w.totalWithdrawn ?? 0,
      withdrawingAmount: w.withdrawingAmount ?? 0
    }
  }
  
  function buildLocalSupportTasksCard(): Extract<SupportToolCard, { kind: 'tasks' }> {
    const me = String(deps.myName.value || '').trim()
    const ACTIVE = new Set(['PENDING_REVIEW', 'OPEN', 'CLAIMED', 'OVERDUE', 'PENDING_CONFIRMATION'])
    const rows = [...deps.mineTasks.value]
      .filter((t) => ACTIVE.has(String(t.status || '')))
      .slice(0, 5)
      .map((t) => {
        const publisher = String(t.publisherName || '').trim() === me
        return {
          id: t.id,
          title: t.title,
          status: t.status,
          statusLabel: statusLabelMap[t.status as TaskStatus] || String(t.status || ''),
          reward: t.reward,
          role: publisher ? 'publisher' : 'claimer',
          roleLabel: publisher ? '你发布的' : '你接的'
        }
      })
    return {
      kind: 'tasks',
      scope: 'active',
      scopeLabel: '进行中',
      total: rows.length,
      matched: rows.length,
      shown: rows.length,
      tasks: rows
    }
  }
  
  function buildLocalSupportWithdrawalsCard(): Extract<SupportToolCard, { kind: 'withdrawals' }> {
    const rows = [...deps.walletWithdraws.value].slice(0, 5).map((w) => ({
      id: w.id,
      amount: w.amount,
      fee: Number(w.amount) < 100 ? 0.1 : 0,
      payout: Math.max(0, Number(w.amount) - (Number(w.amount) < 100 ? 0.1 : 0)),
      status: w.status,
      statusLabel:
        w.status === 'PENDING' ? '审核中' : w.status === 'APPROVED' ? '已通过' : w.status === 'REJECTED' ? '已驳回' : w.status,
      accountMasked: w.accountNo
        ? `****${String(w.accountNo).slice(-4)}`
        : '',
      auditRemark: w.auditRemark || '',
      createdAt: w.createdAt || ''
    }))
    return {
      kind: 'withdrawals',
      matched: deps.walletWithdraws.value.length,
      shown: rows.length,
      items: rows
    }
  }
  
  function buildLocalSupportCreditCard(): Extract<SupportToolCard, { kind: 'credit' }> {
    const score = Number(deps.creditScore.value ?? 80)
    const blockedRaw = deps.blockedUntil.value
    const blockedTs = blockedRaw ? new Date(blockedRaw).getTime() : 0
    const blocked = Number.isFinite(blockedTs) && blockedTs > Date.now()
    const rider = !!deps.isRiderApproved.value
    return {
      kind: 'credit',
      creditScore: score,
      minToClaim: 60,
      riderApproved: rider,
      blocked,
      blockedUntil: blocked && blockedRaw ? String(blockedRaw) : '',
      canClaim: rider && score >= 60 && !blocked
    }
  }
  
  function buildLocalSupportRiderCertCard(): Extract<SupportToolCard, { kind: 'rider_cert' }> {
    const approved = !!deps.isRiderApproved.value
    const pending = !!deps.riderApplicationPending.value
    return {
      kind: 'rider_cert',
      riderApproved: approved,
      riderApplicationPending: pending,
      statusLabel: approved ? '已通过' : pending ? '审核中' : '未认证',
      appliedAt: ''
    }
  }
  
  async function sendSupportChatMessage(message: string) {
    const text = message.trim()
    if (!text || supportChatSending.value) return
    if (!deps.isLoggedIn.value) {
      deps.openAuthPage('login')
      return
    }
  
    // 人工已接入：消息发往客服，留在智能客服窗
    if (supportHumanMode.value && supportAssignedAgent.value && supportHandoffJoined.value) {
      await sendSupportHumanChatMessage(text)
      return
    }
  
    // 「转人工 / 转接人工」等：直接走真实分配，不交给智能客服空口答应
    if (looksLikeSupportHandoffRequest(text)) {
      supportChatMessages.value.push({ role: 'user', content: text })
      await transferToHumanSupport()
      return
    }
  
    // 排队等待中：仍可问小新，同时把用户问题同步给客服台（不会因此标记客服已介入）
    if (supportHumanMode.value && supportAssignedAgent.value && !supportHandoffJoined.value) {
      const agent = supportAssignedAgent.value.trim()
      void deps.request<ChatMessageItem>(`${apiOrigin}/api/chat/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetUser: agent, content: text })
      })
        .then((sent) => {
          const sentId = Number(sent?.id || 0)
          if (sentId > 0) {
            supportIngestedChatIds.value.add(sentId)
            if (sentId > supportHumanHistoryAfterId.value) {
              supportHumanHistoryAfterId.value = sentId
            }
          }
        })
        .catch(() => undefined)
    }
  
    supportChatMessages.value.push({ role: 'user', content: text })
    supportChatSending.value = true
    try {
      const localFaq = findLocalSupportFaqAnswer(text)
      if (localFaq) {
        supportChatMessages.value.push({
          role: 'assistant',
          content: localFaq
        })
        return
      }
  
      const history = supportChatMessages.value.slice(0, -1).map((item) => ({
        role: item.role === 'human' || item.role === 'system' ? 'assistant' : item.role,
        content: item.content
      })).filter((item) => item.content)
      const res = await deps.request<{
        reply: string
        actions?: string[]
        source?: string
        toolPayload?: SupportToolCard
      }>(
        `${apiOrigin}/api/support/chat`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ message: text, history })
        }
      )
      let reply = res.reply?.trim() || '智能客服暂时不可用，请稍后再试或转人工客服。'
      const actions = Array.isArray(res.actions) ? res.actions : []
      if (actions.includes('suggest_handoff') && !reply.includes('转人工')) {
        reply += '\n\n如需人工协助，可直接发送「转人工」，或点顶部转接入口。'
      }
      let card = normalizeSupportToolCard(res.source, res.toolPayload)
      const unavailable = /暂时不可用|查询暂时失败|暂时查不到/.test(reply)
      const taskIdHint = parseSupportTaskId(text)
  
      // 规则问被误当成查任务时：丢掉任务卡，改用取消规则说明
      if (looksLikeSupportTaskPolicyHowTo(text) && card?.kind === 'tasks') {
        card = null
        const faq = findLocalSupportFaqAnswer(text)
        if (faq) reply = faq
      }
  
      // 后端工具失败时，用前端已有数据兜底成聊天卡片
      if ((!card || unavailable) && taskIdHint) {
        try {
          await deps.fetchMyTasksForProfile()
        } catch {
          /* ignore */
        }
        const hit =
          deps.mineTasks.value.find((t) => Number(t.id) === taskIdHint) ||
          safeAllTasks.value.find((t) => Number(t.id) === taskIdHint)
        if (hit) {
          const me = String(deps.myName.value || '').trim()
          const publisher = String(hit.publisherName || '').trim() === me
          card = {
            kind: 'task_detail',
            id: hit.id,
            title: hit.title,
            status: hit.status,
            statusLabel: resolveTaskStatusLabel(hit),
            reward: hit.reward,
            role: publisher ? 'publisher' : 'claimer',
            roleLabel: publisher ? '你发布的' : '你接的',
            pickupLocation: hit.pickupLocation,
            dropoffLocation: hit.dropoffLocation,
            latestDeliveryAt: hit.latestDeliveryAt || '',
            updatedAt: hit.updatedAt || '',
            bountyPrepaid: !!hit.bountyPrepaid
          }
          reply = `任务 #${hit.id}（${card.roleLabel}）当前状态：${card.statusLabel}。`
        }
      } else if ((!card || unavailable) && looksLikeSupportWithdrawalsQuery(text)) {
        try {
          await deps.fetchWalletData()
        } catch {
          /* ignore */
        }
        card = buildLocalSupportWithdrawalsCard()
        const latest = card.items?.[0]
        reply = latest
          ? `最近一笔提现：${latest.statusLabel}，金额 ¥${Number(latest.amount || 0).toFixed(2)}。`
          : '你目前没有提现申请记录。'
      } else if ((!card || unavailable) && looksLikeSupportCreditQuery(text)) {
        card = buildLocalSupportCreditCard()
        reply = `你当前信誉分为 ${card.creditScore} 分（接单需 ≥ ${card.minToClaim}）。`
      } else if ((!card || unavailable) && looksLikeSupportRiderCertQuery(text)) {
        card = buildLocalSupportRiderCertCard()
        reply =
          card.statusLabel === '已通过'
            ? '你的接单员认证已通过，可以接单与提现。'
            : card.statusLabel === '审核中'
              ? '你的接单员申请正在审核中。'
              : '你当前还不是接单员，可去提交申请。'
      } else if ((!card || unavailable) && looksLikeSupportTasksQuery(text)) {
        try {
          await deps.fetchMyTasksForProfile()
        } catch {
          /* ignore */
        }
        card = buildLocalSupportTasksCard()
        const n = card.matched ?? 0
        reply =
          n > 0
            ? `「进行中」任务共 ${n} 条。`
            : '你目前没有进行中的任务。可以说「我的历史任务」再查，或点卡片里的「查看全部任务」。'
      } else if ((!card || unavailable) && looksLikeSupportWalletQuery(text)) {
        try {
          await deps.fetchWalletData()
        } catch {
          /* ignore */
        }
        card = buildLocalSupportWalletCard()
        reply = '这是你当前的钱包概况。'
      }
  
      if (card) {
        supportChatMessages.value.push({ role: 'assistant', content: reply, card })
      } else {
        await typeSupportAssistantReply(reply)
      }
    } catch (err) {
      // 网络失败时也尽量本地兜底
      if (looksLikeSupportTasksQuery(text)) {
        try {
          await deps.fetchMyTasksForProfile()
        } catch {
          /* ignore */
        }
        const card = buildLocalSupportTasksCard()
        supportChatMessages.value.push({
          role: 'assistant',
          content:
            (card.matched ?? 0) > 0
              ? `「进行中」任务共 ${card.matched} 条。`
              : '暂时连不上任务服务，先展示本地缓存；也可点「查看全部任务」。',
          card
        })
      } else if (looksLikeSupportWalletQuery(text)) {
        try {
          await deps.fetchWalletData()
        } catch {
          /* ignore */
        }
        supportChatMessages.value.push({
          role: 'assistant',
          content: '暂时连不上钱包服务，先展示本地缓存。',
          card: buildLocalSupportWalletCard()
        })
      } else {
        await typeSupportAssistantReply((err as Error).message || '智能客服暂时不可用，请稍后再试或转人工客服。')
      }
    } finally {
      supportChatSending.value = false
    }
  }
  
  /** 打字机效果：逐字显示，让回复慢一点 */
  async function typeSupportAssistantReply(fullText: string) {
    const full = String(fullText || '')
    supportChatMessages.value.push({ role: 'assistant', content: '' })
    const idx = supportChatMessages.value.length - 1
    // 保持 sending=true，打字过程中禁止再发；「正在思考」由前端根据空气泡隐藏
    const delayMs = 48
    for (let i = 1; i <= full.length; i++) {
      const row = supportChatMessages.value[idx]
      if (!row || row.role !== 'assistant') break
      supportChatMessages.value[idx] = { role: 'assistant', content: full.slice(0, i) }
      await new Promise((r) => setTimeout(r, delayMs))
    }
    if (supportChatMessages.value[idx]) {
      supportChatMessages.value[idx] = { role: 'assistant', content: full }
    }
  }
  
  function stopSupportHumanPolling() {
    if (supportHumanPollTimer != null) {
      window.clearInterval(supportHumanPollTimer)
      supportHumanPollTimer = null
    }
  }
  
  function startSupportHumanPolling() {
    stopSupportHumanPolling()
    supportHumanPollTimer = window.setInterval(() => {
      if (!supportHumanMode.value || !supportAssignedAgent.value) {
        stopSupportHumanPolling()
        return
      }
      void syncSupportHumanHistory()
    }, 4000)
  }
  
  function markSupportHandoffJoined() {
    if (supportHandoffJoined.value) return
    supportHandoffJoined.value = true
    const agentName = supportAssignedAgent.value.trim() || '人工客服'
    const list = supportChatMessages.value
    for (let i = list.length - 1; i >= 0; i--) {
      const card = list[i]?.card
      if (card && card.kind === 'handoff' && card.status === 'waiting') {
        supportChatMessages.value[i] = {
          ...list[i],
          card: { ...card, status: 'joined', agent: agentName }
        }
        break
      }
    }
    supportChatMessages.value.push({
      role: 'system',
      content: `客服「${agentName}」已介入为您服务，可直接描述你的问题`
    })
  }
  
  async function loadSupportAssignedAgentProfile(username: string) {
    const name = String(username || '').trim()
    if (!name) {
      supportAssignedAgentAvatar.value = ''
      return
    }
    try {
      const profile = await deps.request<UserPublicProfile>(
        `${authBase}/users/${encodeURIComponent(name)}/public-profile`
      )
      supportAssignedAgentAvatar.value = String(profile?.avatarUrl || '').trim()
    } catch {
      supportAssignedAgentAvatar.value = ''
    }
  }
  
  function ingestSupportHumanIncoming(content: string, msgId?: number) {
    const text = String(content || '').trim()
    if (!text) return
    if (/^\[来自智能客服\]/.test(text)) return
    // 订单卡片是用户发出的，绝不能当成客服介入
    if (text.startsWith('[TASK_INQUIRY]') || text.startsWith('【订单咨询】')) return
    const id = Number(msgId || 0)
    if (id > 0) {
      if (supportIngestedChatIds.value.has(id)) return
      // 转接前的历史消息一律忽略
      if (id <= supportHumanHistoryAfterId.value) {
        supportIngestedChatIds.value.add(id)
        return
      }
      supportIngestedChatIds.value.add(id)
    } else {
      const last = supportChatMessages.value[supportChatMessages.value.length - 1]
      if (last?.role === 'human' && last.content === text) return
    }
  
    // 客服主动结束人工服务
    if (text.startsWith('[CS_SESSION_END]')) {
      const tip =
        text.replace(/^\[CS_SESSION_END\]\s*/, '').trim() ||
        '客服已结束本次人工服务，您可继续咨询片刻小新。'
      endSupportHumanSession({
        notifyAgent: false,
        assistantMessage: tip
      })
      if (!showSupportChat.value) {
        showSupportChat.value = true
        deps.showRealtimeToast({
          title: '人工服务已结束',
          message: tip.slice(0, 48),
          kind: 'chat'
        })
      }
      return
    }
  
    // 客服点击「接入会话」
    if (text.startsWith('[CS_SESSION_JOIN]')) {
      const tip =
        text.replace(/^\[CS_SESSION_JOIN\]\s*/, '').trim() ||
        '您好，我是片刻帮人工客服，已接入为您服务。'
      markSupportHandoffJoined()
      supportChatMessages.value.push({
        role: 'human',
        content: tip,
        chatMsgId: id > 0 ? id : undefined
      })
      if (!showSupportChat.value) {
        showSupportChat.value = true
        deps.showRealtimeToast({
          title: '人工客服已接入',
          message: tip.slice(0, 48),
          kind: 'chat'
        })
      }
      return
    }
  
    markSupportHandoffJoined()
    supportChatMessages.value.push({
      role: 'human',
      content: text,
      chatMsgId: id > 0 ? id : undefined
    })
    if (!showSupportChat.value) {
      showSupportChat.value = true
    }
  }
  
  async function captureSupportHumanHistoryWatermark(agent: string) {
    const name = String(agent || '').trim()
    if (!name) {
      supportHumanHistoryAfterId.value = 0
      return
    }
    try {
      const rows = await deps.request<ChatMessageItem[]>(
        `${apiOrigin}/api/chat/history?targetUser=${encodeURIComponent(name)}`
      )
      let maxId = 0
      for (const m of rows || []) {
        const id = Number(m.id || 0)
        if (id > maxId) maxId = id
        if (id > 0) supportIngestedChatIds.value.add(id)
      }
      supportHumanHistoryAfterId.value = maxId
    } catch {
      supportHumanHistoryAfterId.value = 0
    }
  }
  
  async function syncSupportHumanHistory() {
    const agent = supportAssignedAgent.value.trim()
    if (!agent || !deps.isLoggedIn.value) return
    try {
      const rows = await deps.request<ChatMessageItem[]>(
        `${apiOrigin}/api/chat/history?targetUser=${encodeURIComponent(agent)}`
      )
      const afterId = supportHumanHistoryAfterId.value
      for (const m of rows || []) {
        // 只吃「客服 → 我」的新消息；用户自己发的订单/文字不能触发介入
        if (!usernameMatches(m.fromUsername, agent)) continue
        if (usernameMatches(m.fromUsername, deps.myName.value)) continue
        const id = Number(m.id || 0)
        if (id > 0 && id <= afterId) continue
        ingestSupportHumanIncoming(String(m.content || ''), id)
      }
      deps.unreadMap.value[agent] = 0
    } catch {
      /* ignore poll errors */
    }
  }
  
  async function sendSupportHumanChatMessage(message: string) {
    const text = message.trim()
    const agent = supportAssignedAgent.value.trim()
    if (!text || !agent || supportChatSending.value) return
    supportChatMessages.value.push({ role: 'user', content: text })
    supportChatSending.value = true
    try {
      await deps.request<ChatMessageItem>(`${apiOrigin}/api/chat/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetUser: agent, content: text })
      })
      deps.unreadMap.value[agent] = 0
      void syncSupportHumanHistory()
    } catch (err) {
      supportChatMessages.value.push({
        role: 'assistant',
        content: (err as Error).message || '消息发送失败，请稍后再试'
      })
    } finally {
      supportChatSending.value = false
    }
  }
  
  function resetSupportCsRatingForm(agent = '') {
    supportCsRatingVisible.value = false
    supportCsRatingAgent.value = agent
    supportCsRatingScore.value = 5
    supportCsRatingTags.value = []
    supportCsRatingComment.value = ''
    supportCsRatingSubmitting.value = false
    supportCsRatingDone.value = false
  }
  
  function openSupportCsRating(agent: string) {
    const name = String(agent || '').trim()
    if (!name) return
    supportCsRatingAgent.value = name
    supportCsRatingScore.value = 5
    supportCsRatingTags.value = []
    supportCsRatingComment.value = ''
    supportCsRatingSubmitting.value = false
    supportCsRatingDone.value = false
    supportCsRatingVisible.value = true
    showSupportChat.value = true
  }
  
  function supportCsRatingScoreLabel(score: number) {
    const map: Record<number, string> = {
      1: '非常不满意',
      2: '不满意',
      3: '一般',
      4: '满意',
      5: '非常满意'
    }
    return map[score] || ''
  }
  
  function toggleSupportCsRatingTag(tag: string) {
    const list = supportCsRatingTags.value
    if (list.includes(tag)) {
      supportCsRatingTags.value = list.filter((t) => t !== tag)
    } else {
      supportCsRatingTags.value = [...list, tag]
    }
  }
  
  function skipSupportCsRating() {
    supportCsRatingVisible.value = false
    supportCsRatingDone.value = true
    supportChatMessages.value.push({
      role: 'system',
      content: '已跳过客服评价'
    })
  }
  
  async function submitSupportCsRating() {
    const agent = supportCsRatingAgent.value.trim()
    if (!agent || supportCsRatingSubmitting.value || supportCsRatingDone.value) return
    const score = Math.max(1, Math.min(5, Math.round(Number(supportCsRatingScore.value) || 5)))
    const tags = supportCsRatingTags.value.slice(0, 5)
    const comment = supportCsRatingComment.value.trim().slice(0, 200)
    supportCsRatingSubmitting.value = true
    try {
      await deps.request(`${apiOrigin}/api/support/ratings`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          agentUsername: agent,
          score,
          tags,
          comment
        })
      })
      const parts = [`评分：${score}星（${supportCsRatingScoreLabel(score)}）`]
      if (tags.length) parts.push(`标签：${tags.join('、')}`)
      if (comment) parts.push(`评价：${comment}`)
      const content = `[CS_RATING] ${parts.join(' | ')}`
      await deps.request<ChatMessageItem>(`${apiOrigin}/api/chat/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ targetUser: agent, content })
      }).catch(() => {
        /* 落库成功即可，聊天提示失败不阻断 */
      })
      supportCsRatingVisible.value = false
      supportCsRatingDone.value = true
      supportChatMessages.value.push({
        role: 'assistant',
        content: `感谢你的评价（${score} 星）！我们会继续改进服务～`
      })
    } catch (err) {
      supportChatMessages.value.push({
        role: 'assistant',
        content: (err as Error).message || '评价提交失败，请稍后再试'
      })
    } finally {
      supportCsRatingSubmitting.value = false
    }
  }
  
  function endSupportHumanSession(options?: {
    notifyAgent?: boolean
    assistantMessage?: string
    showRating?: boolean
  }) {
    const agent = supportAssignedAgent.value.trim()
    const wasJoined = supportHandoffJoined.value
    const shouldNotify = options?.notifyAgent !== false
    const tip =
      options?.assistantMessage ||
      '已结束人工客服。有问题还可以继续问我片刻小新～'
  
    if (shouldNotify && agent && deps.isLoggedIn.value) {
      void deps.request<ChatMessageItem>(`${apiOrigin}/api/chat/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          targetUser: agent,
          content: '[USER_SESSION_END] 用户已结束人工服务'
        })
      }).catch(() => {
        /* 结束本地会话不因通知失败而阻断 */
      })
    }
  
    stopSupportHumanPolling()
    supportHumanMode.value = false
    supportAssignedAgent.value = ''
    supportAssignedAgentAvatar.value = ''
    supportHandoffJoined.value = false
    supportHumanHistoryAfterId.value = 0
    supportIngestedChatIds.value = new Set()
    supportChatMessages.value.push({
      role: 'assistant',
      content: tip
    })
  
    const wantRating = options?.showRating !== false && wasJoined && !!agent
    if (wantRating) {
      openSupportCsRating(agent)
    } else {
      resetSupportCsRatingForm()
    }
  }
  
  async function transferToHumanSupport() {
    if (!deps.isLoggedIn.value) {
      deps.openAuthPage('login')
      return
    }
    if (supportTransferLoading.value) return
  
    if (supportHumanMode.value && supportAssignedAgent.value) {
      showSupportChat.value = true
      supportChatMessages.value.push({
        role: 'assistant',
        content: supportHandoffJoined.value
          ? '人工客服已在当前窗口为你服务，直接输入问题即可。'
          : '人工客服正在赶来，你可以先把问题发给我，或继续等待客服接入。'
      })
      return
    }
  
    if (supportAgentOnline.value === false) {
      const offlineMsg =
        supportAgentStatusText.value || '人工客服当前不在线，请稍后再试或继续使用智能客服'
      showSupportChat.value = true
      supportChatMessages.value.push({
        role: 'assistant',
        content: offlineMsg,
        card: { kind: 'handoff', status: 'offline' }
      })
      deps.showRealtimeToast({
        kind: 'task',
        title: '人工客服不在线',
        message: offlineMsg
      })
      return
    }
  
    supportTransferLoading.value = true
    showSupportChat.value = true
    let agent = ''
    try {
      const res = await deps.request<{ username: string; online?: boolean }>(`${apiOrigin}/api/support/assign-agent`)
      agent = String(res.username || '').trim()
      if (!agent) {
        throw new Error('暂无可用的客服账号，请稍后再试')
      }
  
      supportAssignedAgent.value = agent
      supportAssignedAgentAvatar.value = ''
      supportHumanMode.value = true
      supportHandoffJoined.value = false
      supportHumanHistoryAfterId.value = 0
      supportIngestedChatIds.value = new Set()
      void loadSupportAssignedAgentProfile(agent)
  
      supportChatMessages.value.push({
        role: 'assistant',
        content: '',
        card: { kind: 'handoff', status: 'waiting', agent }
      })
      supportChatMessages.value.push({
        role: 'system',
        content: `已为你分配客服「${agent}」，正在赶来的路上…`
      })
  
      try {
        await deps.request<ChatMessageItem>(`${apiOrigin}/api/chat/send`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            targetUser: agent,
            content: '[来自智能客服] 用户请求人工客服协助。'
          })
        })
      } catch {
        // 分配成功但提示消息发送失败时不阻断转人工
      }
  
      // 先记下当前会话水位，之后只同步新消息，不把历史灌进来
      await captureSupportHumanHistoryWatermark(agent)
      startSupportHumanPolling()
      void syncSupportHumanHistory()
  
      deps.showRealtimeToast({
        kind: 'task',
        title: '人工客服正在赶来',
        message: '客服将在当前窗口接入，请稍候'
      })
    } catch (err) {
      const msg = (err as Error).message || '转人工失败，请稍后再试'
      supportHumanMode.value = false
      supportAssignedAgent.value = ''
      supportAssignedAgentAvatar.value = ''
      supportHumanHistoryAfterId.value = 0
      supportChatMessages.value.push({
        role: 'assistant',
        content: msg
      })
      if (/不在线/.test(msg)) {
        supportAgentOnline.value = false
        supportAgentStatusText.value = msg
      }
      deps.showRealtimeToast({
        kind: 'task',
        title: /不在线/.test(msg) ? '人工客服不在线' : '转人工失败',
        message: msg
      })
    } finally {
      supportTransferLoading.value = false
    }
  }


  return {
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
    stopSupportHumanPolling,
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
  }
}
