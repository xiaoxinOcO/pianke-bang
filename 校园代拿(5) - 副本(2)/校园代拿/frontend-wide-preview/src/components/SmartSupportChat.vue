<script setup lang="ts">
import { computed, nextTick, ref, toRef, watch } from 'vue'
import { useShellStore } from '../stores/useShellStore'

type SupportToolCard =
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

type SupportMessage = {
  role: 'user' | 'assistant' | 'human' | 'system'
  content: string
  card?: SupportToolCard | null
}

const r = useShellStore()

const showSupportChat = toRef(r, 'showSupportChat')
const supportChatSending = toRef(r, 'supportChatSending')
const supportTransferLoading = toRef(r, 'supportTransferLoading')
const supportChatMessages = toRef(r, 'supportChatMessages') as { value: SupportMessage[] }
const supportAgentOnline = toRef(r, 'supportAgentOnline') as { value: boolean | null }
const supportAgentStatusText = toRef(r, 'supportAgentStatusText') as { value: string }
const supportHumanMode = toRef(r, 'supportHumanMode') as { value: boolean }
const supportHandoffJoined = toRef(r, 'supportHandoffJoined') as { value: boolean }
const supportAssignedAgent = toRef(r, 'supportAssignedAgent') as { value: string }
const supportAssignedAgentAvatar = toRef(r, 'supportAssignedAgentAvatar') as { value: string }
const hallFaqItems = toRef(r, 'hallFaqItems') as { value: Array<{ question: string; answer: string }> }
const myName = toRef(r, 'myName') as { value: string }
const myAvatarUrl = toRef(r, 'myAvatarUrl') as { value: string }
const normalizeImageUrl = r.normalizeImageUrl as (url: string) => string

const closeSupportChat = r.closeSupportChat as () => void
const sendSupportChatMessage = r.sendSupportChatMessage as (message: string) => Promise<void>
const transferToHumanSupport = r.transferToHumanSupport as () => void | Promise<void>
const endSupportHumanSession = r.endSupportHumanSession as () => void
const SUPPORT_CS_RATING_TAGS = (r.SUPPORT_CS_RATING_TAGS as string[]) || [
  '解决了问题',
  '态度很好',
  '回复及时',
  '专业耐心',
  '有待改进'
]
const supportCsRatingVisible = toRef(r, 'supportCsRatingVisible') as { value: boolean }
const supportCsRatingAgent = toRef(r, 'supportCsRatingAgent') as { value: string }
const supportCsRatingScore = toRef(r, 'supportCsRatingScore') as { value: number }
const supportCsRatingTags = toRef(r, 'supportCsRatingTags') as { value: string[] }
const supportCsRatingComment = toRef(r, 'supportCsRatingComment') as { value: string }
const supportCsRatingSubmitting = toRef(r, 'supportCsRatingSubmitting') as { value: boolean }
const supportCsRatingScoreLabel = r.supportCsRatingScoreLabel as (score: number) => string
const toggleSupportCsRatingTag = r.toggleSupportCsRatingTag as (tag: string) => void
const skipSupportCsRating = r.skipSupportCsRating as () => void
const submitSupportCsRating = r.submitSupportCsRating as () => void | Promise<void>
const refreshSupportAgentAvailability = r.refreshSupportAgentAvailability as () => void | Promise<void>
const openSupportWallet = r.openSupportWallet as () => void | Promise<void>
const openSupportMyTasks = r.openSupportMyTasks as () => void | Promise<void>
const openSupportTaskById = r.openSupportTaskById as (taskId: number | string) => void | Promise<void>
const openSupportRiderApply = r.openSupportRiderApply as () => void
const openSupportCoupons = r.openSupportCoupons as () => void
const goPublish = r.goPublish as () => void
const fetchWalletData = r.fetchWalletData as () => Promise<void>
const fetchMyTasksForProfile = r.fetchMyTasksForProfile as () => Promise<void>
const sendTaskToCustomerService = r.sendTaskToCustomerService as (task: any) => Promise<void>
const myTasksForCustomerService = toRef(r, 'myTasksForCustomerService') as {
  value: Array<{
    id: number
    title?: string
    status?: string
    reward?: number | string
    publisherName?: string
    claimerName?: string
  }>
}
const sendingTaskToCsId = toRef(r, 'sendingTaskToCsId') as { value: number | null }

const humanSupportDisabled = computed(
  () =>
    supportTransferLoading.value ||
    supportAgentOnline.value === false ||
    supportHumanMode.value
)

const showOrdersPanel = ref(false)

const humanOrderTasks = computed(() => myTasksForCustomerService.value ?? [])

function taskStatusText(status?: string) {
  const key = String(status || '').toUpperCase()
  const map: Record<string, string> = {
    OPEN: '待接单',
    CLAIMED: '配送中',
    PENDING_CONFIRMATION: '待确认',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    REJECTED: '已驳回',
    PENDING_ADMIN_REVIEW: '待审核'
  }
  return map[key] || status || '-'
}

function taskRoleText(task: { publisherName?: string; claimerName?: string }) {
  const me = String(myName.value || '').trim().toLowerCase()
  if (me && String(task.publisherName || '').trim().toLowerCase() === me) return '你发布的'
  if (me && String(task.claimerName || '').trim().toLowerCase() === me) return '你接的'
  return '相关任务'
}

async function onSendOrderToCs(task: { id: number; title?: string }) {
  await sendTaskToCustomerService?.(task)
  showOrdersPanel.value = false
}

const draft = ref('')
const draftInputRef = ref<HTMLInputElement | null>(null)
const chatBodyRef = ref<HTMLElement | null>(null)

const displayName = computed(() => {
  const n = String(myName.value || '').trim()
  return n || '同学'
})

/** 结束人工后仍保留最近一次客服名/头像，用于历史气泡展示 */
const lastAgentName = ref('')
const lastAgentAvatar = ref('')

watch(
  supportAssignedAgent,
  (v) => {
    const n = String(v || '').trim()
    if (n) lastAgentName.value = n
  },
  { immediate: true }
)

watch(
  supportAssignedAgentAvatar,
  (v) => {
    const raw = String(v || '').trim()
    if (raw) lastAgentAvatar.value = raw
  },
  { immediate: true }
)

const agentDisplayName = computed(() => {
  const n = String(supportAssignedAgent.value || lastAgentName.value || '').trim()
  return n || '人工客服'
})

const userAvatarSrc = computed(() => {
  const raw = String(myAvatarUrl.value || '').trim()
  if (!raw) return ''
  try {
    return normalizeImageUrl(raw) || raw
  } catch {
    return raw
  }
})

const agentAvatarSrc = computed(() => {
  const raw = String(supportAssignedAgentAvatar.value || lastAgentAvatar.value || '').trim()
  if (!raw) return '/logo.png'
  try {
    return normalizeImageUrl(raw) || raw
  } catch {
    return raw
  }
})

const showUserAvatarImg = ref(true)
const showAgentAvatarImg = ref(true)
watch(userAvatarSrc, () => {
  showUserAvatarImg.value = true
})
watch(agentAvatarSrc, () => {
  showAgentAvatarImg.value = true
})

const quickQuestions = computed(() => {
  const tools = ['我的钱包', '我的任务', '怎么提现？', '如何发布任务？']
  const fromHall = (hallFaqItems.value ?? []).map((item) => item.question).filter(Boolean)
  const merged = [...tools]
  for (const q of fromHall) {
    if (!merged.includes(q) && merged.length < 6) merged.push(q)
  }
  return merged
})

const showWelcome = computed(() => supportChatMessages.value.length === 0)

const showThinkingBubble = computed(() => {
  // 人工会话不展示智能客服「回复中」
  if (supportHumanMode.value && supportHandoffJoined.value) return false
  if (!supportChatSending.value) return false
  const list = supportChatMessages.value
  const last = list.length ? list[list.length - 1] : null
  return !(last && (last.role === 'assistant' || last.role === 'human' || last.role === 'system'))
})

function money(v: number | string | undefined) {
  const n = Number(v ?? 0)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

async function scrollToBottom() {
  await nextTick()
  const el = chatBodyRef.value
  if (el) el.scrollTop = el.scrollHeight
}

watch(
  () => {
    const list = supportChatMessages.value
    const last = list.length ? list[list.length - 1]?.content : ''
    const cardKind = list.length ? list[list.length - 1]?.card?.kind : ''
    return [list.length, supportChatSending.value, last, cardKind, supportCsRatingVisible.value]
  },
  () => scrollToBottom()
)

watch(showSupportChat, (open) => {
  if (open) {
    document.body.style.overflow = 'hidden'
    scrollToBottom()
    void refreshSupportAgentAvailability()
    void fetchWalletData?.()
    void fetchMyTasksForProfile?.()
  } else {
    document.body.style.overflow = ''
    showOrdersPanel.value = false
  }
})

watch(supportHumanMode, (on) => {
  if (on) {
    showOrdersPanel.value = true
    void fetchMyTasksForProfile?.()
  } else {
    showOrdersPanel.value = false
  }
})

// 点发送会失焦；结束后自动回到输入框，避免每次用鼠标再点
watch(supportChatSending, (sending) => {
  if (!sending) focusDraftInput()
})

function focusDraftInput() {
  void nextTick(() => {
    draftInputRef.value?.focus()
  })
}

async function onSend() {
  const text = draft.value.trim()
  if (!text || supportChatSending.value) return
  draft.value = ''
  await sendSupportChatMessage(text)
  focusDraftInput()
}

async function onQuickAsk(question: string) {
  if (supportChatSending.value) return
  await sendSupportChatMessage(question)
  focusDraftInput()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void onSend()
  }
}

function goPage(fn: () => void | Promise<void>) {
  closeSupportChat()
  void fn()
}

function openTask(id: number | string | undefined) {
  if (id == null || id === '') return
  void openSupportTaskById?.(id)
}

function isUrgentTaskTitle(title: unknown) {
  const t = String(title || '')
  return t.includes('加急') || t.includes('【加急】')
}

function displayTaskTitle(title: unknown, id: unknown) {
  const raw = String(title || '').trim()
  if (!raw) return `任务 #${id ?? ''}`
  return raw.replace(/^【加急】\s*/, '').replace(/^\[加急\]\s*/, '')
}

function isCouponUnused(status: unknown, statusLabel: unknown) {
  const s = String(status || '').toUpperCase()
  const label = String(statusLabel || '')
  return s === 'UNUSED' || s === 'AVAILABLE' || label.includes('可用')
}

function formatCouponExpire(expireAt: unknown) {
  const raw = String(expireAt || '').trim()
  if (!raw) return ''
  return raw.replace('T', ' ').slice(0, 16)
}

function couponValueLabel(c: {
  discountAmount?: number | string
  discountPercent?: number | string
}) {
  const amount = Number(c.discountAmount)
  if (Number.isFinite(amount) && amount > 0) {
    return `¥${money(amount)}`
  }
  const percent = Number(c.discountPercent)
  if (Number.isFinite(percent) && percent > 0) {
    return `${percent}%`
  }
  return ''
}

/** 仅自定义海报；无图 / 默认主题券保持纯白行 */
function couponBannerUrl(raw: unknown) {
  const s = String(raw || '').trim()
  if (!s) return ''
  try {
    return normalizeImageUrl(s) || s
  } catch {
    return s
  }
}

function couponBannerStyle(raw: unknown) {
  const url = couponBannerUrl(raw)
  if (!url) return undefined
  const safe = url.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
  return { '--cs-coupon-banner': `url("${safe}")` } as Record<string, string>
}
</script>

<template>
  <Teleport to="body">
    <div v-if="showSupportChat" class="pk-cs-mask">
      <div class="pk-cs-panel" role="dialog" aria-label="片刻小新智能客服">
        <header class="pk-cs-head">
          <button type="button" class="pk-cs-back" aria-label="返回" @click="closeSupportChat">
            <svg viewBox="0 0 24 24" width="22" height="22" fill="none" aria-hidden="true">
              <path
                d="M15 6 9 12l6 6"
                stroke="currentColor"
                stroke-width="2.4"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            <span>返回</span>
          </button>
          <div class="pk-cs-head-center">
            <strong>
              {{
                supportHumanMode && supportHandoffJoined
                  ? agentDisplayName
                  : supportHumanMode
                    ? '片刻小新 · 转人工中'
                    : '片刻小新'
              }}
            </strong>
            <button
              v-if="supportHumanMode"
              type="button"
              class="pk-cs-head-link"
              @click="endSupportHumanSession"
            >
              {{ supportHandoffJoined ? '结束人工，返回小新' : '取消排队，返回小新' }}
            </button>
            <button
              v-else
              type="button"
              class="pk-cs-head-link"
              :disabled="humanSupportDisabled"
              :title="supportAgentOnline === false ? supportAgentStatusText : '转接人工客服'"
              @click="transferToHumanSupport"
            >
              {{
                supportTransferLoading
                  ? '正在接入人工…'
                  : supportAgentOnline === false
                    ? '人工客服暂离线'
                    : '需要人工？点这里转接'
              }}
            </button>
          </div>
        </header>

        <div ref="chatBodyRef" class="pk-cs-chat">
          <template v-if="showWelcome">
            <div class="pk-cs-msg is-bot">
              <img class="pk-cs-msg-avatar" src="/xiaoxin-robot-mascot.png" alt="" />
              <div class="pk-cs-msg-stack">
                <span class="pk-cs-msg-name">片刻小新</span>
                <div class="pk-cs-bubble pk-cs-bubble--bot pk-cs-bubble--welcome">
                  <p>Hi，我是片刻小新。发布、接单、提现有问题，直接问我就行～</p>
                  <ul class="pk-cs-faq-list">
                    <li v-for="q in quickQuestions.slice(0, 4)" :key="`faq-${q}`">
                      <button type="button" @click="onQuickAsk(q)">
                        <span>{{ q }}</span>
                        <em aria-hidden="true">›</em>
                      </button>
                    </li>
                  </ul>
                </div>
              </div>
            </div>
          </template>

          <div
            v-for="(msg, idx) in supportChatMessages"
            :key="`cs-msg-${idx}`"
            class="pk-cs-msg"
            :class="{
              'is-user': msg.role === 'user',
              'is-bot': msg.role === 'assistant' || msg.role === 'human',
              'is-system': msg.role === 'system'
            }"
          >
            <template v-if="msg.role === 'system'">
              <div class="pk-cs-system-tip">{{ msg.content }}</div>
            </template>
            <template v-else>
            <img
              v-if="msg.role === 'assistant'"
              class="pk-cs-msg-avatar"
              src="/xiaoxin-robot-mascot.png"
              alt=""
            />
            <img
              v-else-if="msg.role === 'human' && agentAvatarSrc && showAgentAvatarImg"
              class="pk-cs-msg-avatar pk-cs-msg-avatar--cs"
              :src="agentAvatarSrc"
              alt=""
              @error="showAgentAvatarImg = false"
            />
            <img
              v-else-if="msg.role === 'human'"
              class="pk-cs-msg-avatar pk-cs-msg-avatar--cs"
              src="/logo.png"
              alt=""
            />
            <div class="pk-cs-msg-stack">
              <span v-if="msg.role === 'assistant'" class="pk-cs-msg-name">片刻小新</span>
              <span v-else-if="msg.role === 'human'" class="pk-cs-msg-name">{{ agentDisplayName }}</span>
              <div
                v-if="msg.content"
                class="pk-cs-bubble"
                :class="{
                  'pk-cs-bubble--user': msg.role === 'user',
                  'pk-cs-bubble--bot': msg.role === 'assistant',
                  'pk-cs-bubble--human': msg.role === 'human'
                }"
              >
                {{ msg.content }}
              </div>

              <div
                v-if="msg.role === 'assistant' && msg.card?.kind === 'wallet'"
                class="pk-cs-inchat-card pk-cs-inchat-card--wallet"
              >
                <div class="pk-cs-wallet">
                  <div class="pk-cs-wallet-top">
                    <h4>我的钱包</h4>
                  </div>

                  <div class="pk-cs-wallet-balance">
                    <span>可用余额</span>
                    <p><small>¥</small>{{ money(msg.card.balance) }}</p>
                  </div>

                  <div class="pk-cs-wallet-metrics">
                    <div>
                      <span>累计收入</span>
                      <strong>¥{{ money(msg.card.totalIncome) }}</strong>
                    </div>
                    <div>
                      <span>已提现</span>
                      <strong>¥{{ money(msg.card.totalWithdrawn) }}</strong>
                    </div>
                    <div>
                      <span>提现中</span>
                      <strong>¥{{ money(msg.card.withdrawingAmount) }}</strong>
                    </div>
                  </div>

                  <button type="button" class="pk-cs-wallet-link" @click="goPage(openSupportWallet)">
                    去钱包 / 提现
                    <span aria-hidden="true">→</span>
                  </button>
                </div>
              </div>

              <div
                v-else-if="msg.role === 'assistant' && msg.card?.kind === 'tasks'"
                class="pk-cs-inchat-card pk-cs-inchat-card--tasks"
              >
                <div class="pk-cs-inchat-tasks-head">
                  <h4>{{ msg.card.scopeLabel || '我的任务' }}</h4>
                  <span class="pk-cs-inchat-tasks-count">
                    {{ msg.card.matched ?? msg.card.total ?? 0 }} 条
                  </span>
                </div>

                <ul v-if="msg.card.tasks?.length" class="pk-cs-inchat-tasks">
                  <li
                    v-for="(t, ti) in msg.card.tasks"
                    :key="`task-${idx}-${ti}`"
                    :style="{ '--i': ti }"
                  >
                    <button
                      type="button"
                      class="pk-cs-inchat-task"
                      :class="{
                        'pk-cs-inchat-task--urgent': isUrgentTaskTitle(t.title),
                        'pk-cs-inchat-task--open': String(t.status || '').toUpperCase() === 'OPEN'
                      }"
                      @click="openTask(t.id)"
                    >
                      <span class="pk-cs-inchat-task-body">
                        <span class="pk-cs-inchat-task-meta">
                          <em v-if="isUrgentTaskTitle(t.title)" class="pk-cs-inchat-urgent-tag">加急</em>
                          <em
                            v-if="t.statusLabel || t.status"
                            class="pk-cs-inchat-status"
                            :data-status="String(t.status || '').toUpperCase()"
                          >
                            {{ t.statusLabel || t.status }}
                          </em>
                          <i>{{ t.roleLabel || t.role || '相关任务' }}</i>
                        </span>
                        <strong>{{ displayTaskTitle(t.title, t.id) }}</strong>
                      </span>
                      <span class="pk-cs-inchat-task-price" v-if="t.reward != null">
                        <small>¥</small>{{ money(t.reward) }}
                      </span>
                    </button>
                  </li>
                </ul>
                <p v-else class="pk-cs-inchat-empty">暂无相关任务</p>
                <button type="button" class="pk-cs-inchat-secondary pk-cs-inchat-tasks-footer" @click="goPage(openSupportMyTasks)">
                  查看全部任务
                  <span aria-hidden="true">→</span>
                </button>
              </div>

              <div
                v-else-if="msg.role === 'assistant' && msg.card?.kind === 'task_detail'"
                class="pk-cs-inchat-card pk-cs-inchat-card--detail"
              >
                <div class="pk-cs-detail">
                  <div class="pk-cs-detail-top">
                    <h4>{{ msg.card.title || `任务 #${msg.card.id}` }}</h4>
                    <span v-if="msg.card.reward != null" class="pk-cs-detail-price">
                      <small>¥</small>{{ money(msg.card.reward) }}
                    </span>
                  </div>
                  <div class="pk-cs-detail-meta">
                    <em
                      v-if="msg.card.statusLabel || msg.card.status"
                      class="pk-cs-inchat-status"
                      :data-status="String(msg.card.status || '').toUpperCase()"
                    >
                      {{ msg.card.statusLabel || msg.card.status }}
                    </em>
                    <i v-if="msg.card.roleLabel">{{ msg.card.roleLabel }}</i>
                  </div>
                  <dl class="pk-cs-detail-kv">
                    <div v-if="msg.card.pickupLocation">
                      <dt>取件</dt>
                      <dd>{{ msg.card.pickupLocation }}</dd>
                    </div>
                    <div v-if="msg.card.dropoffLocation">
                      <dt>送达</dt>
                      <dd>{{ msg.card.dropoffLocation }}</dd>
                    </div>
                    <div v-if="msg.card.latestDeliveryAt">
                      <dt>最晚送达</dt>
                      <dd>{{ msg.card.latestDeliveryAt }}</dd>
                    </div>
                    <div v-if="msg.card.updatedAt">
                      <dt>更新</dt>
                      <dd>{{ msg.card.updatedAt }}</dd>
                    </div>
                  </dl>
                  <button type="button" class="pk-cs-card-link" @click="openTask(msg.card.id)">
                    打开任务详情
                    <span aria-hidden="true">→</span>
                  </button>
                </div>
              </div>

              <div
                v-else-if="msg.role === 'assistant' && msg.card?.kind === 'withdrawals'"
                class="pk-cs-inchat-card pk-cs-inchat-card--withdraw"
              >
                <div class="pk-cs-list-head">
                  <h4>提现记录</h4>
                  <span class="pk-cs-list-count">共 {{ msg.card.matched ?? 0 }} 条</span>
                </div>
                <ul v-if="msg.card.items?.length" class="pk-cs-list">
                  <li
                    v-for="(w, wi) in msg.card.items"
                    :key="`wd-${idx}-${wi}`"
                    :style="{ '--i': wi }"
                  >
                    <div class="pk-cs-list-row">
                      <span class="pk-cs-list-body">
                        <span class="pk-cs-list-meta">
                          <em class="pk-cs-inchat-status">{{ w.statusLabel || w.status }}</em>
                          <i v-if="w.createdAt">{{ w.createdAt }}</i>
                        </span>
                        <strong>¥{{ money(w.amount) }}</strong>
                      </span>
                      <span v-if="w.payout != null" class="pk-cs-list-value">到账 ¥{{ money(w.payout) }}</span>
                    </div>
                  </li>
                </ul>
                <p v-else class="pk-cs-list-empty">暂无提现申请</p>
                <button type="button" class="pk-cs-card-link" @click="goPage(openSupportWallet)">
                  去钱包查看
                  <span aria-hidden="true">→</span>
                </button>
              </div>

              <div
                v-else-if="msg.role === 'assistant' && msg.card?.kind === 'credit'"
                class="pk-cs-inchat-card pk-cs-inchat-card--credit"
              >
                <div class="pk-cs-metric">
                  <div class="pk-cs-metric-top"><h4>我的信誉分</h4></div>
                  <div class="pk-cs-metric-hero">
                    <span>当前分数</span>
                    <p>{{ msg.card.creditScore ?? '-' }} <small>分</small></p>
                  </div>
                  <div class="pk-cs-metric-grid">
                    <div>
                      <span>接单门槛</span>
                      <strong>≥ {{ msg.card.minToClaim ?? 60 }}</strong>
                    </div>
                    <div>
                      <span>能否接单</span>
                      <strong>{{ msg.card.canClaim ? '可以' : '暂不可' }}</strong>
                    </div>
                  </div>
                  <p v-if="msg.card.blocked && msg.card.blockedUntil" class="pk-cs-metric-note">
                    禁止接单至 {{ msg.card.blockedUntil }}
                  </p>
                  <p v-else-if="!msg.card.riderApproved" class="pk-cs-metric-note">
                    需先通过接单员认证
                  </p>
                </div>
              </div>

              <div
                v-else-if="msg.role === 'assistant' && msg.card?.kind === 'rider_cert'"
                class="pk-cs-inchat-card pk-cs-inchat-card--cert"
              >
                <div class="pk-cs-metric">
                  <div class="pk-cs-metric-top"><h4>接单员认证</h4></div>
                  <div class="pk-cs-metric-hero">
                    <span>当前状态</span>
                    <p class="pk-cs-metric-hero-text">{{ msg.card.statusLabel || '未知' }}</p>
                  </div>
                  <p v-if="msg.card.appliedAt" class="pk-cs-metric-note">
                    申请时间：{{ msg.card.appliedAt }}
                  </p>
                  <button
                    v-if="!msg.card.riderApproved"
                    type="button"
                    class="pk-cs-card-link"
                    @click="goPage(openSupportRiderApply)"
                  >
                    {{ msg.card.riderApplicationPending ? '查看申请进度页' : '去申请接单员' }}
                    <span aria-hidden="true">→</span>
                  </button>
                </div>
              </div>

              <div
                v-else-if="msg.role === 'assistant' && msg.card?.kind === 'appeals'"
                class="pk-cs-inchat-card pk-cs-inchat-card--appeals"
              >
                <div class="pk-cs-list-head">
                  <h4>申诉记录</h4>
                  <span class="pk-cs-list-count">共 {{ msg.card.matched ?? 0 }} 条</span>
                </div>
                <ul v-if="msg.card.items?.length" class="pk-cs-list">
                  <li
                    v-for="(a, ai) in msg.card.items"
                    :key="`ap-${idx}-${ai}`"
                    :style="{ '--i': ai }"
                  >
                    <button type="button" class="pk-cs-list-row pk-cs-list-row--btn" @click="openTask(a.taskId)">
                      <span class="pk-cs-list-body">
                        <span class="pk-cs-list-meta">
                          <em class="pk-cs-inchat-status">{{ a.appealStatusLabel }}</em>
                          <i v-if="a.auditedAt">{{ a.auditedAt }}</i>
                        </span>
                        <strong>{{ a.title || `任务 #${a.taskId}` }}</strong>
                      </span>
                      <span v-if="a.resultHint" class="pk-cs-list-value pk-cs-list-value--muted">{{ a.resultHint }}</span>
                    </button>
                  </li>
                </ul>
                <p v-else class="pk-cs-list-empty">暂无已裁决申诉</p>
              </div>

              <div
                v-else-if="msg.role === 'assistant' && msg.card?.kind === 'coupons'"
                class="pk-cs-inchat-card pk-cs-inchat-card--coupons"
              >
                <div class="pk-cs-inchat-coupons-head">
                  <h4>我的优惠券</h4>
                  <span class="pk-cs-inchat-coupons-count">可用 {{ msg.card.unusedCount ?? 0 }} 张</span>
                </div>

                <ul v-if="msg.card.items?.length" class="pk-cs-inchat-coupons">
                  <li
                    v-for="(c, ci) in msg.card.items"
                    :key="`cp-${idx}-${ci}`"
                    :style="{ '--i': ci }"
                  >
                    <div
                      class="pk-cs-inchat-coupon"
                      :class="{
                        'pk-cs-inchat-coupon--unused': isCouponUnused(c.status, c.statusLabel),
                        'pk-cs-inchat-coupon--banner': !!couponBannerUrl(c.bannerImageUrl)
                      }"
                      :style="couponBannerStyle(c.bannerImageUrl)"
                    >
                      <span class="pk-cs-inchat-coupon-body">
                        <span class="pk-cs-inchat-coupon-meta">
                          <em
                            class="pk-cs-inchat-coupon-status"
                            :data-status="String(c.status || '').toUpperCase()"
                          >
                            {{ c.statusLabel || c.status || '优惠券' }}
                          </em>
                          <i v-if="c.expireAt">到期 {{ formatCouponExpire(c.expireAt) }}</i>
                        </span>
                        <strong>{{ c.name || '优惠券' }}</strong>
                      </span>
                      <span v-if="couponValueLabel(c)" class="pk-cs-inchat-coupon-value">
                        {{ couponValueLabel(c) }}
                      </span>
                    </div>
                  </li>
                </ul>
                <p v-else class="pk-cs-inchat-empty pk-cs-inchat-coupons-empty">暂无优惠券</p>

                <button
                  type="button"
                  class="pk-cs-inchat-secondary pk-cs-inchat-coupons-footer"
                  @click="goPage(openSupportCoupons)"
                >
                  去优惠券 / 领券
                  <span aria-hidden="true">→</span>
                </button>
              </div>

              <div
                v-else-if="msg.role === 'assistant' && msg.card?.kind === 'urgent_quota'"
                class="pk-cs-inchat-card pk-cs-inchat-card--urgent"
              >
                <div class="pk-cs-metric">
                  <div class="pk-cs-metric-top"><h4>本周加急配额</h4></div>
                  <div class="pk-cs-metric-hero">
                    <span>剩余可用</span>
                    <p>
                      {{ msg.card.remaining ?? 0 }}
                      <small>/ {{ msg.card.weeklyLimit ?? 3 }} 次</small>
                    </p>
                  </div>
                  <div class="pk-cs-metric-grid">
                    <div>
                      <span>已用</span>
                      <strong>{{ msg.card.used ?? 0 }}</strong>
                    </div>
                    <div>
                      <span>费用区间</span>
                      <strong>¥{{ money(msg.card.feeMin) }}～{{ money(msg.card.feeMax) }}</strong>
                    </div>
                  </div>
                  <button type="button" class="pk-cs-card-link" @click="goPage(goPublish)">
                    去发布页加急
                    <span aria-hidden="true">→</span>
                  </button>
                </div>
              </div>

              <div
                v-else-if="msg.role === 'assistant' && msg.card?.kind === 'transactions'"
                class="pk-cs-inchat-card pk-cs-inchat-card--tx"
              >
                <div class="pk-cs-list-head">
                  <h4>钱包流水</h4>
                  <span class="pk-cs-list-count">展示 {{ msg.card.shown ?? 0 }} 条</span>
                </div>
                <ul v-if="msg.card.items?.length" class="pk-cs-list">
                  <li
                    v-for="(t, ti) in msg.card.items"
                    :key="`tx-${idx}-${ti}`"
                    :style="{ '--i': ti }"
                  >
                    <div class="pk-cs-list-row">
                      <span class="pk-cs-list-body">
                        <span class="pk-cs-list-meta">
                          <em class="pk-cs-inchat-status">{{ t.typeLabel || t.type }}</em>
                          <i v-if="t.createdAt">{{ t.createdAt }}</i>
                        </span>
                        <strong v-if="t.remark">{{ t.remark }}</strong>
                        <strong v-else-if="t.taskId">任务 #{{ t.taskId }}</strong>
                        <strong v-else>{{ t.typeLabel || t.type || '流水' }}</strong>
                      </span>
                      <span class="pk-cs-list-value">¥{{ money(t.amount) }}</span>
                    </div>
                  </li>
                </ul>
                <p v-else class="pk-cs-list-empty">暂无流水</p>
                <button type="button" class="pk-cs-card-link" @click="goPage(openSupportWallet)">
                  去钱包查看
                  <span aria-hidden="true">→</span>
                </button>
              </div>

              <div
                v-else-if="msg.role === 'assistant' && msg.card?.kind === 'handoff'"
                class="pk-cs-inchat-card pk-cs-inchat-card--handoff"
              >
                <p v-if="msg.card.status === 'waiting'" class="pk-cs-handoff-text">
                  客服「{{ msg.card.agent || agentDisplayName }}」<span>正在赶来的路上</span>，您可继续向片刻小新提问，Ta 可能已有现成解决方案。
                </p>
                <p v-else-if="msg.card.status === 'joined'" class="pk-cs-handoff-text">
                  客服「{{ msg.card.agent || agentDisplayName }}」已接入，请直接在下方输入你的问题。
                </p>
                <p v-else class="pk-cs-handoff-text">
                  人工客服暂时不在线，可先问片刻小新，稍后再试转人工。
                </p>
                <div class="pk-cs-handoff-actions">
                  <button type="button" class="pk-cs-handoff-btn" @click="onQuickAsk('我的钱包')">
                    查询钱包
                  </button>
                  <button type="button" class="pk-cs-handoff-btn pk-cs-handoff-btn--primary" @click="onQuickAsk('我的任务')">
                    咨询任务问题
                  </button>
                </div>
                <ul class="pk-cs-handoff-topics">
                  <li>
                    <button type="button" @click="onQuickAsk('怎么提现？')">
                      <span>提现到账 / 钱包余额问题</span><em>›</em>
                    </button>
                  </li>
                  <li>
                    <button type="button" @click="onQuickAsk('如何发布任务？')">
                      <span>发布 / 接单 / 任务状态问题</span><em>›</em>
                    </button>
                  </li>
                  <li>
                    <button type="button" @click="onQuickAsk('可以取消已接的任务吗？')">
                      <span>取消任务 / 纠纷申诉</span><em>›</em>
                    </button>
                  </li>
                </ul>
              </div>
            </div>
            <img
              v-if="msg.role === 'user' && userAvatarSrc && showUserAvatarImg"
              class="pk-cs-msg-avatar pk-cs-msg-avatar--user-img"
              :src="userAvatarSrc"
              alt=""
              @error="showUserAvatarImg = false"
            />
            <span
              v-else-if="msg.role === 'user'"
              class="pk-cs-msg-avatar pk-cs-msg-avatar--user"
              aria-hidden="true"
            >{{ displayName.slice(0, 1) }}</span>
            </template>
          </div>

          <div v-if="supportTransferLoading" class="pk-cs-msg is-bot">
            <img class="pk-cs-msg-avatar" src="/xiaoxin-robot-mascot.png" alt="" />
            <div class="pk-cs-msg-stack">
              <span class="pk-cs-msg-name">片刻小新</span>
              <div class="pk-cs-bubble pk-cs-bubble--bot">正在接入人工客服…</div>
            </div>
          </div>
          <div v-if="showThinkingBubble" class="pk-cs-msg is-bot">
            <img class="pk-cs-msg-avatar" src="/xiaoxin-robot-mascot.png" alt="" />
            <div class="pk-cs-msg-stack">
              <span class="pk-cs-msg-name">片刻小新</span>
              <div class="pk-cs-bubble pk-cs-bubble--bot pk-cs-bubble--typing">回复中…</div>
            </div>
          </div>

          <div v-if="supportCsRatingVisible" class="pk-cs-rating-card">
            <div class="pk-cs-rating-head">
              <strong>请为本次人工客服评分</strong>
              <span>客服「{{ supportCsRatingAgent || '人工客服' }}」</span>
            </div>
            <div class="pk-cs-rating-stars" role="radiogroup" aria-label="客服星级">
              <button
                v-for="n in 5"
                :key="`cs-star-${n}`"
                type="button"
                class="pk-cs-rating-star"
                :class="{ on: supportCsRatingScore >= n }"
                :aria-checked="supportCsRatingScore === n"
                role="radio"
                @click="supportCsRatingScore = n"
              >
                ★
              </button>
              <em>{{ supportCsRatingScoreLabel(supportCsRatingScore) }}</em>
            </div>
            <div class="pk-cs-rating-tags">
              <button
                v-for="tag in SUPPORT_CS_RATING_TAGS"
                :key="`cs-tag-${tag}`"
                type="button"
                class="pk-cs-rating-tag"
                :class="{ on: supportCsRatingTags.includes(tag) }"
                @click="toggleSupportCsRatingTag(tag)"
              >
                {{ tag }}
              </button>
            </div>
            <textarea
              v-model="supportCsRatingComment"
              class="pk-cs-rating-comment"
              rows="2"
              maxlength="200"
              placeholder="想说的可以写在这里（选填）"
            />
            <div class="pk-cs-rating-actions">
              <button type="button" class="pk-cs-rating-skip" :disabled="supportCsRatingSubmitting" @click="skipSupportCsRating">
                暂不评价
              </button>
              <button
                type="button"
                class="pk-cs-rating-submit"
                :disabled="supportCsRatingSubmitting"
                @click="submitSupportCsRating"
              >
                {{ supportCsRatingSubmitting ? '提交中…' : '提交评价' }}
              </button>
            </div>
          </div>
        </div>

        <div class="pk-cs-footer">
          <div v-if="supportHumanMode" class="pk-cs-orders">
            <button
              type="button"
              class="pk-cs-orders-toggle"
              @click="showOrdersPanel = !showOrdersPanel"
            >
              <span>我的订单 / 申请</span>
              <em>{{ showOrdersPanel ? '收起' : `展开（${humanOrderTasks.length}）` }}</em>
            </button>
            <div v-if="showOrdersPanel" class="pk-cs-orders-panel">
              <p v-if="!humanOrderTasks.length" class="pk-cs-orders-empty">暂无相关订单，可先用文字说明问题</p>
              <ul v-else class="pk-cs-orders-list">
                <li v-for="t in humanOrderTasks" :key="`cs-order-${t.id}`">
                  <div class="pk-cs-orders-main">
                    <strong>{{ t.title || `任务 #${t.id}` }}</strong>
                    <span>{{ taskStatusText(t.status) }} · {{ taskRoleText(t) }}</span>
                  </div>
                  <button
                    type="button"
                    class="pk-cs-orders-send"
                    :disabled="sendingTaskToCsId === t.id"
                    @click="onSendOrderToCs(t)"
                  >
                    {{ sendingTaskToCsId === t.id ? '发送中…' : '发给客服' }}
                  </button>
                </li>
              </ul>
            </div>
          </div>
          <div class="pk-cs-chips">
            <button
              v-if="supportHumanMode"
              type="button"
              class="pk-cs-chip pk-cs-chip--handoff"
              @click="endSupportHumanSession"
            >
              {{ supportHandoffJoined ? '结束人工' : '取消排队' }}
            </button>
            <button
              v-else
              type="button"
              class="pk-cs-chip pk-cs-chip--handoff"
              :disabled="supportChatSending || supportTransferLoading || humanSupportDisabled"
              @click="transferToHumanSupport"
            >
              转人工
            </button>
            <button
              v-for="q in quickQuestions"
              :key="`chip-${q}`"
              type="button"
              class="pk-cs-chip"
              :disabled="supportChatSending"
              @click="onQuickAsk(q)"
            >
              {{ q }}
            </button>
            <button type="button" class="pk-cs-chip" @click="goPage(goPublish)">发布任务</button>
          </div>
          <div class="pk-cs-composer">
            <input
              ref="draftInputRef"
              v-model="draft"
              type="text"
              :placeholder="
                supportHumanMode && supportHandoffJoined
                  ? `向「${agentDisplayName}」描述你的问题～`
                  : supportHumanMode
                    ? '可先问片刻小新，或等待人工接入～'
                    : '在这儿输入你的问题试试～'
              "
              @keydown="onKeydown"
            />
            <button
              type="button"
              class="pk-cs-send"
              :disabled="!draft.trim() || supportChatSending"
              @click="onSend"
            >
              发送
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
