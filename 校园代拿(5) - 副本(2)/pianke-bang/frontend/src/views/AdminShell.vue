<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue'
import { computed, nextTick, ref, watch } from 'vue'
import { useAdminStore } from '../stores/useAdminStore'
import type { AdminAppealRow } from '../context/adminShellInject'
import type { AdminOperationLog, TaskItem, TaskStatus } from '../types/app'
import { isAwaitingBountyPayment, isPendingAdminReview, resolveTaskStatusLabel, statusClass, statusLabelMap } from '../utils/taskStatus'

const r = useAdminStore()

/** 仅窄屏：侧栏抽屉开关（样式由 @media (max-width: 960px) 控制，桌面不受影响） */
const adminMobileNavOpen = ref(false)

function toggleAdminMobileNav() {
  adminMobileNavOpen.value = !adminMobileNavOpen.value
}

function closeAdminMobileNav() {
  adminMobileNavOpen.value = false
}

const TASK_GROUP_MODULES = ['task', 'taskOverview'] as const
const AUDIT_GROUP_MODULES = ['audit', 'appealAudit'] as const
const RIDER_GROUP_MODULES = ['riderOverview', 'riderRank', 'riderCompare'] as const
const WALLET_GROUP_MODULES = ['walletAudit', 'walletCredit', 'coupons'] as const
const SYSTEM_GROUP_MODULES = ['notice', 'logs', 'feedback', 'csRatings', 'customerService', 'aiModel'] as const

function isAdminModuleIn(group: readonly string[]) {
  return group.includes(String(r.adminModule))
}

const isTaskGroupActive = computed(() => isAdminModuleIn(TASK_GROUP_MODULES))
const isAuditGroupActive = computed(() => isAdminModuleIn(AUDIT_GROUP_MODULES))
const isRiderGroupActive = computed(() => isAdminModuleIn(RIDER_GROUP_MODULES))
const isWalletGroupActive = computed(() => isAdminModuleIn(WALLET_GROUP_MODULES))
const isSystemGroupActive = computed(() => isAdminModuleIn(SYSTEM_GROUP_MODULES))

function goAdminModule(mod: AdminShellInject['adminModule'], sideEffect?: () => void | Promise<void>) {
  void sideEffect?.()
  r.adminModule = mod
  closeAdminMobileNav()
}

function couponClaimModeLabel(mode: unknown) {
  const m = String(mode || '').toUpperCase()
  if (m === 'PUBLIC') return '公开可领'
  if (m === 'NEWCOMER') return '活动可领'
  return '仅发券/兑换码'
}

function couponTypeLabel(type: unknown) {
  const t = String(type || '').toUpperCase()
  if (t === 'PERCENT') return '折扣'
  if (t === 'THRESHOLD') return '满减'
  return '立减'
}

function couponTemplateBrief(t: Record<string, unknown>) {
  const type = String(t.type || '').toUpperCase()
  if (type === 'PERCENT') {
    const p = Number(t.discountPercent || 0)
    const zhe = (p * 10).toFixed(1).replace(/\.0$/, '')
    const cap = Number(t.maxDiscount || 0)
    return cap > 0 ? `${zhe}折 · 最高减¥${cap.toFixed(2)}` : `${zhe}折`
  }
  const amt = Number(t.discountAmount || 0).toFixed(2)
  if (type === 'THRESHOLD') {
    return `满¥${Number(t.minSpend || 0).toFixed(2)} 减¥${amt}`
  }
  return `立减¥${amt}`
}

function couponThemeLabel(theme: unknown) {
  const t = String(theme || 'warm').toLowerCase()
  if (t === 'cool') return '冰蓝'
  if (t === 'green') return '清新绿'
  if (t === 'blue') return '靛蓝'
  return '暖橙'
}

function formatCouponDateTime(raw: unknown) {
  if (raw == null || raw === '') return ''
  const s = String(raw).replace('T', ' ')
  return s.length >= 16 ? s.slice(0, 16) : s
}

function couponValidityText(t: Record<string, unknown>) {
  if (t.validDays != null && t.validDays !== '') {
    const days = Number(t.validDays)
    if (!Number.isFinite(days) || days <= 0) return '有效期：不限期限'
    if (days === 365) return '有效期：1 年'
    return `有效期：${days} 天`
  }
  const from = formatCouponDateTime(t.validFrom)
  const to = formatCouponDateTime(t.validTo)
  if (!from && !to) return '有效期：不限期限'
  if (from && to) {
    const a = Date.parse(String(t.validFrom).replace(' ', 'T'))
    const b = Date.parse(String(t.validTo).replace(' ', 'T'))
    if (Number.isFinite(a) && Number.isFinite(b) && b >= a) {
      const days = Math.max(1, Math.round((b - a) / 86400000))
      if (days === 365 || days === 366) return '有效期：1 年'
      if (days <= 3650) return `有效期：${days} 天`
    }
    return `有效期：${from} ~ ${to}`
  }
  if (from) return `有效期：${from} 起`
  return `有效期：至 ${to}`
}

function isHallClaimableMode(mode: unknown) {
  const m = String(mode || '').toUpperCase()
  return m === 'PUBLIC' || m === 'NEWCOMER'
}

function hotPlaceMaxCount(rows: Array<{ count?: number }> | null | undefined) {
  if (!rows?.length) return 1
  return Math.max(1, ...rows.map((r) => Number(r.count || 0)))
}

function hotPlaceBarPct(count: number, max: number) {
  return Math.max(6, Math.round((Number(count || 0) / Math.max(1, max)) * 100))
}

function openTaskGroup() {
  goAdminModule('task')
}

function openAuditGroup() {
  r.auditSubModule = 'task'
  goAdminModule('audit', () => r.refreshAuditCenter('task'))
}

function openRiderGroup() {
  goAdminModule('riderOverview', () => r.loadAdminDashboardData())
}

function openWalletGroup() {
  goAdminModule('walletAudit', () => r.fetchAdminPendingWithdraws())
}

function openSystemGroup() {
  goAdminModule('notice', () => r.fetchAdminNotices())
}

const adminLogPageCount = computed(() => Math.max(1, r.adminLogsPaging.totalPages))

const adminUserKeyword = ref('')
const adminUserPage = ref(1)
const adminUserPageSize = ref(20)
const adminUserPageSizeOptions = [20, 50, 100]

type AdminUserTypeFilter = 'all' | 'user' | 'cs'
const adminUserTypeFilter = ref<AdminUserTypeFilter>('all')

type AdminFeedbackTypeFilter = 'all' | 'suggest' | 'bug'
type AdminFeedbackReadFilter = 'all' | 'unread' | 'read'

const adminFeedbackTypeFilter = ref<AdminFeedbackTypeFilter>('all')
const adminFeedbackReadFilter = ref<AdminFeedbackReadFilter>('all')
const adminFeedbackKeyword = ref('')

const adminFeedbackPageCount = computed(() => Math.max(1, r.adminFeedbacksPaging.totalPages))

const adminFeedbackStats = computed(() => {
  const list = r.adminFeedbacks
  return {
    total: r.adminFeedbacksPaging.totalElements,
    unread: r.adminFeedbacksUnreadCount,
    suggest: list.filter((item) => item.feedbackType === 'suggest').length,
    bug: list.filter((item) => item.feedbackType === 'bug').length
  }
})

const selectedFeedbackId = ref<number | null>(null)

const selectedFeedback = computed(() => {
  if (selectedFeedbackId.value == null) return null
  return adminFeedbacksFiltered.value.find((item) => item.id === selectedFeedbackId.value) ?? null
})

const adminFeedbacksFiltered = computed(() => {
  const kw = adminFeedbackKeyword.value.trim().toLowerCase()
  if (!kw) return r.adminFeedbacks
  return r.adminFeedbacks.filter((item) => {
    const username = (item.username || '').toLowerCase()
    const pageName = (item.pageName || '').toLowerCase()
    const content = (item.content || '').toLowerCase()
    const id = String(item.id ?? '')
    return username.includes(kw) || pageName.includes(kw) || content.includes(kw) || id.includes(kw)
  })
})

function refreshAdminFeedbacks(page = 1) {
  void r.fetchAdminFeedbacks(page, adminFeedbackTypeFilter.value, adminFeedbackReadFilter.value)
}


type AdminCsScoreFilter = 'all' | '5' | '4' | '3' | 'low'
const adminCsRatingAgentFilter = ref('')
const adminCsRatingScoreFilter = ref<AdminCsScoreFilter>('all')
const selectedCsRatingId = ref<number | null>(null)

function refreshAdminCsRatings(page = 1) {
  void r.fetchAdminCsRatings?.(page, adminCsRatingAgentFilter.value, adminCsRatingScoreFilter.value)
}

function setAdminCsScoreFilter(v: AdminCsScoreFilter) {
  adminCsRatingScoreFilter.value = v
  selectedCsRatingId.value = null
  refreshAdminCsRatings(1)
}

function formatCsRatingTime(raw?: string) {
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return String(raw)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function scoreStars(score?: number) {
  const n = Math.max(0, Math.min(5, Math.round(Number(score) || 0)))
  return '★'.repeat(n) + '☆'.repeat(5 - n)
}

const selectedCsRating = computed(() => {
  const id = selectedCsRatingId.value
  if (id == null) return null
  return (r.adminCsRatings || []).find((item) => item.id === id) || null
})

watch(
  () => r.adminModule,
  (mod) => {
    if (mod !== 'csRatings') return
    adminCsRatingAgentFilter.value = ''
    adminCsRatingScoreFilter.value = 'all'
    selectedCsRatingId.value = null
    refreshAdminCsRatings(1)
  }
)


function feedbackTypeLabel(type: string) {
  return type === 'bug' ? '故障反馈' : '功能建议'
}

function formatFeedbackTime(iso: string) {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function feedbackContentPreview(text: string, max = 72) {
  const value = (text || '').trim().replace(/\s+/g, ' ')
  if (!value) return '—'
  if (value.length <= max) return value
  return `${value.slice(0, max)}…`
}

function selectAdminFeedback(id: number) {
  selectedFeedbackId.value = id
}

function setAdminFeedbackTypeFilter(type: AdminFeedbackTypeFilter) {
  adminFeedbackTypeFilter.value = type
}

function setAdminFeedbackReadFilter(read: AdminFeedbackReadFilter) {
  adminFeedbackReadFilter.value = read
}

watch(adminFeedbacksFiltered, (list) => {
  if (!list.length) {
    selectedFeedbackId.value = null
    return
  }
  if (selectedFeedbackId.value == null || !list.some((item) => item.id === selectedFeedbackId.value)) {
    selectedFeedbackId.value = list[0].id
  }
})

function accountRoleOf(u: { platformAdmin?: boolean; customerServiceStaff?: boolean; accountRole?: string }) {
  const raw = String(u.accountRole || '').toUpperCase()
  if (raw === 'ADMIN' || raw === 'CUSTOMER_SERVICE' || raw === 'USER') return raw
  if (u.platformAdmin) return 'ADMIN'
  if (u.customerServiceStaff) return 'CUSTOMER_SERVICE'
  return 'USER'
}

function accountRoleLabel(u: { platformAdmin?: boolean; customerServiceStaff?: boolean; accountRole?: string }) {
  const role = accountRoleOf(u)
  if (role === 'ADMIN') return '管理员'
  if (role === 'CUSTOMER_SERVICE') return '客服'
  return '用户'
}

function isCsStaffUser(u: { customerServiceStaff?: boolean; accountRole?: string }) {
  return !!u.customerServiceStaff || String(u.accountRole || '').toUpperCase() === 'CUSTOMER_SERVICE'
}

function isPlatformAdminUser(u: { platformAdmin?: boolean; accountRole?: string }) {
  return !!u.platformAdmin || String(u.accountRole || '').toUpperCase() === 'ADMIN'
}

const adminUserRegularCount = computed(() => r.adminUsers.filter((u) => !isCsStaffUser(u)).length)
const adminUserCsCount = computed(() => r.adminUsers.filter((u) => isCsStaffUser(u)).length)

const adminUserFiltered = computed(() => {
  let list = r.adminUsers
  if (adminUserTypeFilter.value === 'user') {
    list = list.filter((u) => !isCsStaffUser(u))
  } else if (adminUserTypeFilter.value === 'cs') {
    list = list.filter((u) => isCsStaffUser(u))
  }
  const kw = adminUserKeyword.value.trim().toLowerCase()
  if (!kw) return list
  return list.filter((u) => {
    const username = (u.username || '').toLowerCase()
    const phone = (u.phone || '').toLowerCase()
    const id = String(u.id ?? '')
    return username.includes(kw) || phone.includes(kw) || id.includes(kw)
  })
})
const adminUserPageCount = computed(() =>
  Math.max(1, Math.ceil(adminUserFiltered.value.length / adminUserPageSize.value))
)

function formatCsCreatedAt(raw?: string) {
  if (!raw) return '—'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

async function copyCsId(value: string | number) {
  const text = String(value ?? '').trim()
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    alert(`已复制用户 ID：${text}`)
  } catch {
    alert(`用户 ID：${text}`)
  }
}
const adminUsersPaged = computed(() => {
  const start = (adminUserPage.value - 1) * adminUserPageSize.value
  return adminUserFiltered.value.slice(start, start + adminUserPageSize.value)
})
watch([adminUserFiltered, adminUserPageSize], () => {
  if (adminUserPage.value > adminUserPageCount.value) {
    adminUserPage.value = adminUserPageCount.value
  }
  if (adminUserPage.value < 1) {
    adminUserPage.value = 1
  }
})
watch(adminUserTypeFilter, () => {
  adminUserPage.value = 1
})
watch([adminFeedbackTypeFilter, adminFeedbackReadFilter], () => {
  refreshAdminFeedbacks(1)
})
watch(
  () => r.adminModule,
  (mod) => {
    if (mod !== 'feedback') return
    adminFeedbackTypeFilter.value = 'all'
    adminFeedbackReadFilter.value = 'all'
    adminFeedbackKeyword.value = ''
    selectedFeedbackId.value = null
    refreshAdminFeedbacks(1)
  }
)
watch(
  () => r.adminModule,
  (mod) => {
    if (mod !== 'user') return
    adminUserPage.value = 1
    adminUserTypeFilter.value = 'all'
  }
)

type AdminAppealStatusFilter = 'all' | 'pending' | 'approved' | 'rejected' | 'processed'

const adminAppealStatusFilter = ref<AdminAppealStatusFilter>('all')
const selectedAppealKey = ref<string | null>(null)

const adminAppealStats = computed(() => {
  const list = r.adminAppeals
  return {
    total: list.length,
    pending: list.filter((item) => !item.decision && !item.processed).length,
    approved: list.filter((item) => item.decision === 'APPROVED').length,
    rejected: list.filter((item) => item.decision === 'REJECTED').length,
    processed: list.filter((item) => item.processed && !item.decision).length
  }
})

function matchAppealStatusFilter(item: AdminAppealRow, filter: AdminAppealStatusFilter) {
  if (filter === 'all') return true
  if (filter === 'approved') return item.decision === 'APPROVED'
  if (filter === 'rejected') return item.decision === 'REJECTED'
  if (filter === 'processed') return item.processed && !item.decision
  return !item.decision && !item.processed
}

const adminAppealsDisplay = computed(() =>
  r.filteredAdminAppeals.filter((item) => matchAppealStatusFilter(item, adminAppealStatusFilter.value))
)

const selectedAppeal = computed(() => {
  if (!selectedAppealKey.value) return null
  return adminAppealsDisplay.value.find((item) => item.key === selectedAppealKey.value) ?? null
})

function appealStatusLabel(item: AdminAppealRow) {
  if (item.decision === 'APPROVED') return '已通过'
  if (item.decision === 'REJECTED') return '已驳回'
  if (item.processed) return '已处理'
  return '待处理'
}

function appealStatusClass(item: AdminAppealRow) {
  if (item.decision === 'APPROVED') return 'is-approved'
  if (item.decision === 'REJECTED') return 'is-rejected'
  if (item.processed) return 'is-done'
  return 'is-pending'
}

function appealTaskStatusLabel(status?: string) {
  if (!status) return '—'
  return statusLabelMap[status as TaskStatus] ?? status
}

function formatAppealTime(iso: string) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function appealContentPreview(text: string, max = 72) {
  const lines = String(text || '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  const body = lines.filter((line) => !line.startsWith('【')).join(' ') || lines.join(' ')
  if (!body) return '—'
  if (body.length <= max) return body
  return `${body.slice(0, max)}…`
}

function isAppealTaskDecidable(item: AdminAppealRow | null) {
  if (!item) return false
  const taskId = Number(item.taskId)
  return Number.isFinite(taskId) && taskId > 0
}

function isAppealDecisionFinalized(item: AdminAppealRow | null) {
  return item?.decision === 'APPROVED' || item?.decision === 'REJECTED'
}

function canSubmitAppealDecision(item: AdminAppealRow | null) {
  return isAppealTaskDecidable(item) && !isAppealDecisionFinalized(item)
}

function isAppealDeciding(item: AdminAppealRow | null) {
  return !!item && r.appealDecidingKey === item.key
}

async function submitAppealDecision(decision: 'APPROVED' | 'REJECTED') {
  if (!selectedAppeal.value) return
  await r.decideAppeal(selectedAppeal.value, decision)
}

function selectAdminAppeal(key: string) {
  selectedAppealKey.value = key
}

function setAdminAppealStatusFilter(filter: AdminAppealStatusFilter) {
  adminAppealStatusFilter.value = filter
}

watch(adminAppealsDisplay, (list) => {
  if (!list.length) {
    selectedAppealKey.value = null
    return
  }
  if (selectedAppealKey.value == null || !list.some((item) => item.key === selectedAppealKey.value)) {
    selectedAppealKey.value = list[0].key
  }
})

watch(
  () => r.adminModule,
  (mod) => {
    if (mod !== 'appealAudit') return
    adminAppealStatusFilter.value = 'all'
    r.adminAppealKeyword = ''
    selectedAppealKey.value = null
    void r.fetchAdminAppeals()
  }
)

function goAdminUserPrevPage() {
  adminUserPage.value = Math.max(1, adminUserPage.value - 1)
}
function goAdminUserNextPage() {
  adminUserPage.value = Math.min(adminUserPageCount.value, adminUserPage.value + 1)
}

/** 任务管理：六宫格筛选（有效任务 = 未完结未取消的全部） */
type AdminTaskStatFilter = 'effective' | TaskStatus

const adminTaskStatFilter = ref<AdminTaskStatFilter>('effective')

const adminTasksFilteredForList = computed((): TaskItem[] => {
  const tasks = (r.adminVisibleTasks ?? []) as TaskItem[]
  if (adminTaskStatFilter.value === 'effective') return tasks
  if (adminTaskStatFilter.value === 'PENDING_REVIEW') {
    return tasks.filter((t) => isPendingAdminReview(t))
  }
  return tasks.filter((t) => t.status === adminTaskStatFilter.value)
})

const adminTaskFilterLabel = computed(() => {
  const m: Record<AdminTaskStatFilter, string> = {
    effective: '全部有效任务',
    PENDING_REVIEW: '待审核',
    REJECTED: '已驳回',
    OPEN: '待接单',
    CLAIMED: '配送中',
    PENDING_CONFIRMATION: '待确认',
    OVERDUE: '接单员超时',
    COMPLETED: '',
    CANCELLED: ''
  }
  return m[adminTaskStatFilter.value] || '任务'
})

function setAdminTaskStatFilter(key: AdminTaskStatFilter) {
  adminTaskStatFilter.value = key
  nextTick(() => {
    document.getElementById('admin-task-list-anchor')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function clearAdminTaskStatFilter() {
  setAdminTaskStatFilter('effective')
}

watch(
  () => r.adminModule,
  (mod, prev) => {
    if (mod !== 'task') adminTaskStatFilter.value = 'effective'
    // 审核中心的刷新提示仅在审核中心内展示，避免切模块后“串提示条”
    if (prev === 'audit' && mod !== 'audit' && mod !== 'appealAudit' && (r.adminNotice || '').includes('审核中心')) {
      r.adminNotice = ''
    }
  }
)

const ADMIN_ACTION_LABELS: Record<string, string> = {
  CS_STAFF_CREATED: '创建客服账号',
  USER_ENABLED: '启用用户',
  USER_DISABLED: '禁用用户',
  AVATAR_APPROVED: '通过头像审核',
  AVATAR_REJECTED: '驳回头像审核',
  RIDER_APPROVED: '通过接单员申请',
  RIDER_REJECTED: '驳回接单员申请',
  RIDER_CERT_REVOKED: '撤销接单员认证',
  NOTICE_CREATE: '创建公告',
  NOTICE_UPDATE: '更新公告',
  NOTICE_PUBLISH: '上架公告',
  NOTICE_UNPUBLISH: '下架公告',
  NOTICE_PIN: '置顶公告',
  NOTICE_UNPIN: '取消置顶公告',
  NOTICE_DELETE: '删除公告',
  APPEAL_APPROVED: '申诉通过（结算）',
  APPEAL_APPROVED_NO_PAY: '申诉通过（无责）',
  APPEAL_REJECTED: '申诉驳回',
  TASK_APPROVED: '审核通过任务',
  TASK_REJECTED: '驳回任务',
  TASK_RESUBMITTED: '重新提交任务',
  WALLET_WITHDRAW_APPROVED: '通过提现申请',
  WALLET_WITHDRAW_REJECTED: '驳回提现申请',
  WALLET_ADMIN_CREDIT: '用户钱包充值'
}

function adminActionTypeLabel(code: string) {
  return ADMIN_ACTION_LABELS[code] ?? code
}

function formatAdminLogTime(iso: string) {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const y = d.getFullYear()
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  return `${y}-${mo}-${day} ${hh}:${mm}:${ss}`
}

/** 若详情与标题重复（如「禁用用户：张三」），只展示对象部分，避免版面啰嗦 */
function adminLogDetailBody(log: AdminOperationLog) {
  const raw = (log.detail || '').trim()
  if (!raw) return ''
  const label = adminActionTypeLabel(log.actionType)
  if (!label || label === log.actionType) return raw
  const cn = label + '：'
  const en = label + ':'
  if (raw.startsWith(cn)) return raw.slice(cn.length).trim()
  if (raw.startsWith(en)) return raw.slice(en.length).trim()
  return raw
}

/** 函数式 ref：经 appRoot 注入的嵌套 ref 会被解包，必须通过 bindRiderEchartEl 写回真实 ref */
function setRiderEchartEl(el: Element | ComponentPublicInstance | null) {
  r.bindRiderEchartEl(el instanceof HTMLDivElement ? el : null)
}

function setRiderOverviewEchartEl(el: Element | ComponentPublicInstance | null) {
  r.bindRiderOverviewEchartEl(el instanceof HTMLDivElement ? el : null)
}

function setTaskOverviewTrendEchartEl(el: Element | ComponentPublicInstance | null) {
  r.bindTaskOverviewTrendEchartEl(el instanceof HTMLDivElement ? el : null)
}

function setTaskOverviewStatusEchartEl(el: Element | ComponentPublicInstance | null) {
  r.bindTaskOverviewStatusEchartEl(el instanceof HTMLDivElement ? el : null)
}

function taskStatusLabel(code: string) {
  return (statusLabelMap as Record<string, string>)[code] || code
}
</script>


<template>
  <div class="admin-shell">
    <div
      v-if="adminMobileNavOpen"
      class="admin-sidebar-backdrop"
      aria-hidden="true"
      @click="closeAdminMobileNav"
    />
    <aside
      class="admin-sidebar"
      :class="{ 'admin-sidebar--open': adminMobileNavOpen }"
      title="双击侧栏可刷新后台数据"
      @dblclick="r.onAdminSidebarDblClick"
    >
      <div class="admin-brand">
        <img class="admin-brand-logo" src="/logo.png" alt="片刻帮" />
        <span>管理后台</span>
      </div>
      <div class="admin-sidebar-scroll">
        <button class="admin-menu-item" :class="{ active: isTaskGroupActive }" @click="openTaskGroup">任务中心</button>
        <div v-if="isTaskGroupActive" class="admin-submenu">
          <button class="admin-submenu-item" :class="{ active: r.adminModule === 'task' }" @click="goAdminModule('task')">任务管理</button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'taskOverview' }"
            @click="goAdminModule('taskOverview', () => r.fetchAdminTaskStats())"
          >
            任务数据看板
          </button>
        </div>

        <button class="admin-menu-item" :class="{ active: r.adminModule === 'user' }" @click="goAdminModule('user')">用户管理</button>

        <button class="admin-menu-item" :class="{ active: isAuditGroupActive }" @click="openAuditGroup">审核中心</button>
        <div v-if="isAuditGroupActive" class="admin-submenu">
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'audit' && r.auditSubModule === 'task' }"
            @click="goAdminModule('audit', () => { r.auditSubModule = 'task'; r.refreshAuditCenter('task') })"
          >
            审核任务
          </button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'audit' && r.auditSubModule === 'avatar' }"
            @click="goAdminModule('audit', () => { r.auditSubModule = 'avatar'; r.refreshAuditCenter('avatar') })"
          >
            审核头像
          </button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'audit' && r.auditSubModule === 'rider' }"
            @click="goAdminModule('audit', () => { r.auditSubModule = 'rider'; r.refreshAuditCenter('rider') })"
          >
            审核接单员
          </button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'appealAudit' }"
            @click="goAdminModule('appealAudit', () => r.fetchAdminAppeals())"
          >
            审核申诉
          </button>
        </div>

        <button class="admin-menu-item" :class="{ active: isRiderGroupActive }" @click="openRiderGroup">接单员中心</button>
        <div v-if="isRiderGroupActive" class="admin-submenu">
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'riderOverview' }"
            @click="goAdminModule('riderOverview', () => r.loadAdminDashboardData())"
          >
            数据看板
          </button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'riderRank' }"
            @click="goAdminModule('riderRank', () => r.loadAdminDashboardData())"
          >
            排行榜
          </button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'riderCompare' }"
            @click="goAdminModule('riderCompare', () => r.loadAdminDashboardData())"
          >
            接单对比
          </button>
        </div>

        <button class="admin-menu-item" :class="{ active: isWalletGroupActive }" @click="openWalletGroup">钱包管理</button>
        <div v-if="isWalletGroupActive" class="admin-submenu">
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'walletAudit' }"
            @click="goAdminModule('walletAudit', () => r.fetchAdminPendingWithdraws())"
          >
            提现审核
          </button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'walletCredit' }"
            @click="goAdminModule('walletCredit', () => r.fetchAdminUsers())"
          >
            用户充值
          </button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'coupons' && r.adminCouponTab === 'activity' }"
            @click="goAdminModule('coupons', () => { r.adminCouponTab = 'activity'; void r.fetchAdminHallActivities(); void r.fetchAdminCouponTemplates(); void r.fetchAdminUsers() })"
          >
            大厅活动
          </button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'coupons' && r.adminCouponTab !== 'activity' }"
            @click="goAdminModule('coupons', () => { r.adminCouponTab = 'templates'; void r.fetchAdminCouponTemplates(); void r.fetchAdminCouponRedeemCodes(); void r.fetchAdminUsers() })"
          >
            优惠券管理
          </button>
        </div>

        <button class="admin-menu-item" :class="{ active: isSystemGroupActive }" @click="openSystemGroup">系统管理</button>
        <div v-if="isSystemGroupActive" class="admin-submenu">
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'notice' }"
            @click="goAdminModule('notice', () => r.fetchAdminNotices())"
          >
            公告管理
          </button>
          <button class="admin-submenu-item" :class="{ active: r.adminModule === 'logs' }" @click="goAdminModule('logs')">操作日志</button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'feedback' }"
            @click="goAdminModule('feedback', () => refreshAdminFeedbacks(1))"
          >
            用户反馈
            <em v-if="r.adminFeedbacksUnreadCount > 0" class="admin-feedback-menu-badge">{{ r.adminFeedbacksUnreadCount > 99 ? '99+' : r.adminFeedbacksUnreadCount }}</em>
          </button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'csRatings' }"
            @click="goAdminModule('csRatings', () => refreshAdminCsRatings(1))"
          >
            客服评价
          </button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'customerService' }"
            @click="goAdminModule('customerService', () => r.fetchAdminCustomerServiceStaff())"
          >
            人工客服
          </button>
          <button
            class="admin-submenu-item"
            :class="{ active: r.adminModule === 'aiModel' }"
            @click="goAdminModule('aiModel', () => r.fetchAdminAiConfig())"
          >
            AI 模型配置
          </button>
        </div>
      </div>
      <div class="admin-side-bottom">
        <button class="admin-menu-item" @click="r.goHall">返回前台</button>
        <button class="admin-menu-item danger" @click="r.logout">退出登录</button>
      </div>
    </aside>

    <section class="admin-main">
      <header class="admin-topbar">
        <button
          type="button"
          class="admin-mobile-menu-btn"
          aria-label="打开或关闭菜单"
          @click="toggleAdminMobileNav"
        >
          ☰
        </button>
        <h2>管理员工作台</h2>
        <span class="admin-topbar-account">当前账号：{{ r.myName }}</span>
      </header>
      <p
        v-if="r.adminNotice"
        class="profile-top-notice"
        :class="r.adminNoticeTone === 'error' ? 'error' : 'success'"
      >
        {{ r.adminNotice }}
      </p>

      <div v-if="r.adminModule === 'taskOverview'" class="admin-rider-overview">
        <div class="rider-dash-kpis">
          <div class="rider-dash-kpi rider-dash-kpi--blue">
            <div class="rider-dash-kpi-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 8.5 12 4l8 4.5v7L12 20l-8-4.5v-7Z" />
                <path d="M12 12 4 8.5M12 12l8-3.5M12 12v8" />
              </svg>
            </div>
            <div class="rider-dash-kpi-body">
              <strong>{{ r.taskOverviewKpis.total }}</strong>
              <span>任务总数</span>
            </div>
          </div>
          <div class="rider-dash-kpi rider-dash-kpi--green">
            <div class="rider-dash-kpi-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3.5" y="5" width="17" height="15" rx="2.5" />
                <path d="M8 3.5v3M16 3.5v3M3.5 10h17" />
              </svg>
            </div>
            <div class="rider-dash-kpi-body">
              <strong>{{ r.taskOverviewKpis.today }}</strong>
              <span>今日发布</span>
            </div>
          </div>
          <div class="rider-dash-kpi rider-dash-kpi--orange">
            <div class="rider-dash-kpi-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="8.25" />
                <path d="M12 8v4.5l3 1.5" />
              </svg>
            </div>
            <div class="rider-dash-kpi-body">
              <strong>{{ r.taskOverviewKpis.open }}</strong>
              <span>待接单</span>
            </div>
          </div>
          <div class="rider-dash-kpi rider-dash-kpi--red">
            <div class="rider-dash-kpi-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12.5 9.5 17 19 7.5" />
              </svg>
            </div>
            <div class="rider-dash-kpi-body">
              <strong>{{ r.taskOverviewKpis.completed }}</strong>
              <span>已完成</span>
            </div>
          </div>
        </div>

        <section class="card admin-card rider-dash-chart-card">
          <div class="rider-dash-chart-head">
            <h2>任务发布趋势（Elasticsearch）</h2>
            <div class="rider-dash-chart-tools">
              <label class="rider-dash-range">
                <span class="meta">范围</span>
                <select v-model.number="r.taskStatsTrendDays" @change="r.fetchAdminTaskStats()">
                  <option :value="7">最近7天</option>
                  <option :value="14">最近14天</option>
                  <option :value="30">最近30天</option>
                </select>
              </label>
              <button type="button" class="ghost-local" :disabled="r.taskStatsLoading" @click="r.fetchAdminTaskStats">
                {{ r.taskStatsLoading ? '加载中...' : '刷新数据' }}
              </button>
            </div>
          </div>
          <p class="meta rider-dash-chart-hint">数据来自 ES 索引 campus_delivery_task，与 Kibana 统计一致。</p>
          <div v-if="r.taskStatsLoading" class="placeholder">加载中...</div>
          <div v-else-if="!r.taskStatsData" class="placeholder">暂无统计数据，请确认 Elasticsearch 与 task 服务已启动</div>
          <div v-else :ref="setTaskOverviewTrendEchartEl" class="rider-dash-echart"></div>
        </section>

        <section class="card admin-card admin-hot-places">
          <div class="admin-hot-places-head">
            <div>
              <h2>热门地点 TOP10</h2>
              <p class="meta">按任务次数统计；条长表示相对热度</p>
            </div>
          </div>

          <div class="admin-hot-places-grid">
            <div class="admin-hot-place-panel admin-hot-place-panel--pickup">
              <div class="admin-hot-place-panel-head">
                <h3>
                  <span class="admin-hot-place-title-icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M12 21s6.5-5.2 6.5-10.2A6.5 6.5 0 0 0 12 4.3a6.5 6.5 0 0 0-6.5 6.5C5.5 15.8 12 21 12 21Z" />
                      <circle cx="12" cy="10.8" r="2.2" />
                    </svg>
                  </span>
                  取件地点
                </h3>
                <span class="meta">{{ r.taskStatsData?.hotPickupLocations?.length || 0 }} 处</span>
              </div>
              <ol
                v-if="r.taskStatsData?.hotPickupLocations?.length"
                class="admin-hot-place-list"
              >
                <li
                  v-for="(row, idx) in r.taskStatsData.hotPickupLocations"
                  :key="`pickup-${row.name}`"
                  :class="{ 'is-top3': idx < 3 }"
                >
                  <span class="admin-hot-place-rank">{{ String(idx + 1).padStart(2, '0') }}</span>
                  <div class="admin-hot-place-body">
                    <div class="admin-hot-place-line">
                      <span class="admin-hot-place-name" :title="row.name">{{ row.name }}</span>
                      <strong>{{ row.count }}</strong>
                    </div>
                    <div class="admin-hot-place-track" aria-hidden="true">
                      <div
                        class="admin-hot-place-fill"
                        :style="{
                          width: `${hotPlaceBarPct(row.count, hotPlaceMaxCount(r.taskStatsData?.hotPickupLocations))}%`,
                        }"
                      ></div>
                    </div>
                  </div>
                </li>
              </ol>
              <p v-else class="placeholder">暂无数据（需索引含 pickupLocation.keyword 字段）</p>
            </div>

            <div class="admin-hot-place-panel admin-hot-place-panel--dropoff">
              <div class="admin-hot-place-panel-head">
                <h3>
                  <span class="admin-hot-place-title-icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M4 19.5h16" />
                      <path d="M7 19.5V9.8l5-4.3 5 4.3v9.7" />
                      <path d="M10 19.5v-4.2h4v4.2" />
                    </svg>
                  </span>
                  送达地点
                </h3>
                <span class="meta">{{ r.taskStatsData?.hotDropoffLocations?.length || 0 }} 处</span>
              </div>
              <ol
                v-if="r.taskStatsData?.hotDropoffLocations?.length"
                class="admin-hot-place-list"
              >
                <li
                  v-for="(row, idx) in r.taskStatsData.hotDropoffLocations"
                  :key="`dropoff-${row.name}`"
                  :class="{ 'is-top3': idx < 3 }"
                >
                  <span class="admin-hot-place-rank">{{ String(idx + 1).padStart(2, '0') }}</span>
                  <div class="admin-hot-place-body">
                    <div class="admin-hot-place-line">
                      <span class="admin-hot-place-name" :title="row.name">{{ row.name }}</span>
                      <strong>{{ row.count }}</strong>
                    </div>
                    <div class="admin-hot-place-track" aria-hidden="true">
                      <div
                        class="admin-hot-place-fill"
                        :style="{
                          width: `${hotPlaceBarPct(row.count, hotPlaceMaxCount(r.taskStatsData?.hotDropoffLocations))}%`,
                        }"
                      ></div>
                    </div>
                  </div>
                </li>
              </ol>
              <p v-else class="placeholder">暂无数据（需索引含 dropoffLocation.keyword 字段）</p>
            </div>
          </div>
        </section>

        <section v-if="r.taskStatsData" class="card admin-card rider-dash-chart-card">
          <h2 style="margin: 0 0 10px;">任务状态分布</h2>
          <div :ref="setTaskOverviewStatusEchartEl" class="rider-dash-echart" style="height: 300px;"></div>
          <ul class="rider-dash-dist" style="margin-top: 12px;">
            <li v-for="row in r.taskStatsData.statusDistribution" :key="`status-${row.name}`">
              <span>{{ taskStatusLabel(row.name) }}</span>
              <strong>{{ row.count }}</strong>
            </li>
          </ul>
        </section>
      </div>

      <div v-else-if="r.adminModule === 'riderOverview'" class="admin-rider-overview">
        <div class="rider-dash-kpis">
          <div class="rider-dash-kpi rider-dash-kpi--blue">
            <div class="rider-dash-kpi-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="8" r="3.25" />
                <path d="M5.5 18.5c1.6-3 3.9-4.5 6.5-4.5s4.9 1.5 6.5 4.5" />
              </svg>
            </div>
            <div class="rider-dash-kpi-body">
              <strong>{{ r.riderOverviewKpis.certified }}</strong>
              <span>已认证接单员</span>
            </div>
          </div>
          <div class="rider-dash-kpi rider-dash-kpi--orange">
            <div class="rider-dash-kpi-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="8.25" />
                <path d="M12 7.5v5l3.2 1.8" />
              </svg>
            </div>
            <div class="rider-dash-kpi-body">
              <strong>{{ r.riderOverviewKpis.pendingApply }}</strong>
              <span>待审核申请</span>
            </div>
          </div>
          <div class="rider-dash-kpi rider-dash-kpi--green">
            <div class="rider-dash-kpi-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 15.5h8.5l2.2-5.5H20" />
                <circle cx="8" cy="18" r="1.6" />
                <circle cx="16.5" cy="18" r="1.6" />
                <path d="M4 15.5V9.2c0-.7.5-1.2 1.2-1.2H11" />
              </svg>
            </div>
            <div class="rider-dash-kpi-body">
              <strong>{{ r.riderOverviewKpis.delivering }}</strong>
              <span>配送中单量</span>
            </div>
          </div>
          <div class="rider-dash-kpi rider-dash-kpi--red">
            <div class="rider-dash-kpi-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12.5 9.5 17 19 7.5" />
              </svg>
            </div>
            <div class="rider-dash-kpi-body">
              <strong>{{ r.riderOverviewKpis.completedTotal }}</strong>
              <span>累计完成单</span>
            </div>
          </div>
        </div>

        <section class="card admin-card rider-dash-chart-card">
          <div class="rider-dash-chart-head">
            <h2>接单员数据趋势</h2>
            <div class="rider-dash-chart-tools">
              <label class="rider-dash-range">
                <span class="meta">范围</span>
                <select v-model.number="r.riderOverviewTrendDays">
                  <option :value="7">最近7天</option>
                  <option :value="14">最近14天</option>
                  <option :value="30">最近30天</option>
                </select>
              </label>
              <button type="button" class="ghost-local" @click="r.loadAdminDashboardData">刷新数据</button>
            </div>
          </div>
          <p class="meta rider-dash-chart-hint">趋势按任务最后更新日期统计；完成单仅统计已认证接单员完成的订单。</p>
          <div :ref="setRiderOverviewEchartEl" class="rider-dash-echart"></div>
        </section>

        <div class="rider-dash-bottom">
          <section class="card admin-card rider-dash-panel">
            <h2>用户与接单员分布</h2>
            <ul class="rider-dash-dist">
              <li v-for="row in r.riderOverviewDistribution" :key="row.key">
                <span>{{ row.label }}</span>
                <strong>{{ row.count }}</strong>
              </li>
            </ul>
          </section>
          <section class="card admin-card rider-dash-panel">
            <h2>系统信息</h2>
            <ul class="rider-dash-dist">
              <li><span>应用</span><strong>校园代拿</strong></li>
              <li><span>管理模块</span><strong>接单员数据看板</strong></li>
              <li><span>运行状态</span><strong class="rider-dash-ok">正常</strong></li>
            </ul>
          </section>
        </div>
      </div>

      <section v-else-if="r.adminModule === 'appealAudit'" class="card admin-card admin-feedback-page admin-appeal-page">
        <div class="admin-feedback-page-head">
          <div>
            <h2>申诉审核</h2>
            <p class="meta">集中查看用户任务申诉，支持裁决与补充说明（可选）</p>
          </div>
          <button class="ghost-local" type="button" @click="r.fetchAdminAppeals">
            {{ r.adminAppealLoading ? '加载中...' : '刷新列表' }}
          </button>
        </div>

        <div class="admin-feedback-kpis">
          <button
            type="button"
            class="admin-feedback-kpi"
            :class="{ 'is-active': adminAppealStatusFilter === 'all' }"
            @click="setAdminAppealStatusFilter('all')"
          >
            <span class="admin-feedback-kpi-label">全部申诉</span>
            <strong>{{ adminAppealStats.total }}</strong>
          </button>
          <button
            type="button"
            class="admin-feedback-kpi admin-feedback-kpi--unread"
            :class="{ 'is-active': adminAppealStatusFilter === 'pending' }"
            @click="setAdminAppealStatusFilter('pending')"
          >
            <span class="admin-feedback-kpi-label">待处理</span>
            <strong>{{ adminAppealStats.pending }}</strong>
          </button>
          <button
            type="button"
            class="admin-feedback-kpi"
            :class="{ 'is-active': adminAppealStatusFilter === 'approved' }"
            @click="setAdminAppealStatusFilter('approved')"
          >
            <span class="admin-feedback-kpi-label">已通过</span>
            <strong>{{ adminAppealStats.approved }}</strong>
          </button>
          <button
            type="button"
            class="admin-feedback-kpi admin-feedback-kpi--bug"
            :class="{ 'is-active': adminAppealStatusFilter === 'rejected' }"
            @click="setAdminAppealStatusFilter('rejected')"
          >
            <span class="admin-feedback-kpi-label">已驳回</span>
            <strong>{{ adminAppealStats.rejected }}</strong>
          </button>
        </div>

        <div class="admin-feedback-toolbar">
          <div class="admin-feedback-filter-group">
            <span class="admin-feedback-filter-label">处理状态</span>
            <div class="admin-feedback-chip-row">
              <button
                type="button"
                class="admin-feedback-chip"
                :class="{ 'is-active': adminAppealStatusFilter === 'all' }"
                @click="setAdminAppealStatusFilter('all')"
              >
                全部
              </button>
              <button
                type="button"
                class="admin-feedback-chip"
                :class="{ 'is-active': adminAppealStatusFilter === 'pending' }"
                @click="setAdminAppealStatusFilter('pending')"
              >
                待处理
              </button>
              <button
                type="button"
                class="admin-feedback-chip"
                :class="{ 'is-active': adminAppealStatusFilter === 'approved' }"
                @click="setAdminAppealStatusFilter('approved')"
              >
                已通过
              </button>
              <button
                type="button"
                class="admin-feedback-chip"
                :class="{ 'is-active': adminAppealStatusFilter === 'rejected' }"
                @click="setAdminAppealStatusFilter('rejected')"
              >
                已驳回
              </button>
              <button
                type="button"
                class="admin-feedback-chip"
                :class="{ 'is-active': adminAppealStatusFilter === 'processed' }"
                @click="setAdminAppealStatusFilter('processed')"
              >
                已处理
              </button>
            </div>
          </div>
          <input
            v-model="r.adminAppealKeyword"
            class="admin-feedback-search admin-appeal-search"
            placeholder="搜索用户名 / 任务ID / 标题 / 状态"
          />
        </div>

        <div v-if="r.adminAppealLoading" class="placeholder">加载中...</div>
        <div v-else-if="r.adminAppeals.length === 0" class="placeholder">暂无申诉</div>
        <div v-else-if="adminAppealsDisplay.length === 0" class="placeholder">未找到匹配申诉</div>

        <div v-else class="admin-feedback-layout">
          <div class="admin-feedback-table-pane">
            <div class="admin-users-table-wrap admin-feedback-table-wrap">
              <table class="admin-users-table admin-feedback-table admin-appeal-table">
                <thead>
                  <tr>
                    <th>状态</th>
                    <th>申诉人</th>
                    <th>任务</th>
                    <th>标题</th>
                    <th>任务状态</th>
                    <th class="admin-feedback-col-content">申诉内容</th>
                    <th>时间</th>
                    <th class="admin-users-actions-col">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="a in adminAppealsDisplay"
                    :key="a.key"
                    class="admin-feedback-row admin-appeal-row"
                    :class="{
                      'is-active': selectedAppealKey === a.key,
                      'is-unread': !a.decision && !a.processed
                    }"
                    @click="selectAdminAppeal(a.key)"
                  >
                    <td>
                      <span class="admin-feedback-status admin-appeal-status" :class="appealStatusClass(a)">
                        {{ appealStatusLabel(a) }}
                      </span>
                    </td>
                    <td><strong class="admin-feedback-user">{{ a.fromUsername }}</strong></td>
                    <td>#{{ a.taskId || '—' }}</td>
                    <td class="admin-appeal-title-cell">{{ a.title || '—' }}</td>
                    <td>{{ appealTaskStatusLabel(a.taskStatus) }}</td>
                    <td class="admin-feedback-col-content">
                      <p class="admin-feedback-preview">{{ appealContentPreview(a.content) }}</p>
                    </td>
                    <td class="admin-feedback-time">{{ formatAppealTime(a.createdAt) }}</td>
                    <td class="admin-users-actions-col">
                      <button
                        v-if="!isAppealDecisionFinalized(a)"
                        type="button"
                        class="ghost-local admin-feedback-row-action"
                        @click.stop="r.openAdminAppealReply(a)"
                      >
                        补充说明
                      </button>
                      <span v-else class="meta">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <aside class="admin-feedback-detail admin-appeal-detail" :class="{ 'is-empty': !selectedAppeal }">
            <template v-if="selectedAppeal">
              <header class="admin-feedback-detail-head">
                <div>
                  <h3>申诉详情</h3>
                  <p class="meta">
                    {{ selectedAppeal.fromUsername }} · {{ formatAppealTime(selectedAppeal.createdAt) }}
                  </p>
                </div>
                <span class="admin-feedback-status admin-appeal-status" :class="appealStatusClass(selectedAppeal)">
                  {{ appealStatusLabel(selectedAppeal) }}
                </span>
              </header>

              <dl class="admin-feedback-detail-meta">
                <div>
                  <dt>任务 ID</dt>
                  <dd>#{{ selectedAppeal.taskId || '—' }}</dd>
                </div>
                <div>
                  <dt>任务状态</dt>
                  <dd>{{ appealTaskStatusLabel(selectedAppeal.taskStatus) }}</dd>
                </div>
                <div class="admin-appeal-detail-title">
                  <dt>任务标题</dt>
                  <dd>{{ selectedAppeal.title || '—' }}</dd>
                </div>
              </dl>

              <section class="admin-feedback-detail-block">
                <h4>申诉内容</h4>
                <pre class="admin-feedback-detail-content">{{ selectedAppeal.content }}</pre>
              </section>

              <div class="admin-feedback-detail-actions admin-appeal-detail-actions">
                <button
                  v-if="!isAppealDecisionFinalized(selectedAppeal)"
                  type="button"
                  class="ghost-local"
                  @click.stop="r.openAdminAppealReply(selectedAppeal)"
                >
                  补充说明（可选）
                </button>
                <button
                  type="button"
                  class="primary"
                  :disabled="!canSubmitAppealDecision(selectedAppeal) || isAppealDeciding(selectedAppeal)"
                  @click.stop="submitAppealDecision('APPROVED')"
                >
                  {{ isAppealDeciding(selectedAppeal) ? '处理中...' : '通过申诉' }}
                </button>
                <button
                  type="button"
                  class="danger"
                  :disabled="!canSubmitAppealDecision(selectedAppeal) || isAppealDeciding(selectedAppeal)"
                  @click.stop="submitAppealDecision('REJECTED')"
                >
                  {{ isAppealDeciding(selectedAppeal) ? '处理中...' : '驳回申诉' }}
                </button>
                <button type="button" class="ghost-local" @click.stop="r.toggleAppealProcessed(selectedAppeal.key)">
                  {{ selectedAppeal.processed ? '取消已处理' : '标记已处理' }}
                </button>
              </div>
              <p v-if="!isAppealTaskDecidable(selectedAppeal)" class="meta admin-appeal-decide-hint">
                未能解析任务 ID，无法裁决。请确认申诉消息中包含「任务ID：数字」。
              </p>
              <p v-else-if="isAppealDecisionFinalized(selectedAppeal)" class="meta admin-appeal-decide-hint">
                该申诉已裁决为「{{ appealStatusLabel(selectedAppeal) }}」，结果已通过系统通知发送给用户，无需再回复。
              </p>
            </template>
            <p v-else class="admin-feedback-detail-empty">请选择左侧列表中的一条申诉查看详情</p>
          </aside>
        </div>
      </section>

      <section v-else-if="r.adminModule === 'riderRank'" class="card admin-card admin-rider-rank-card">
        <div class="rider-rank-page">
          <div class="rider-rank-hero">
            <div>
              <h2>接单员排行榜</h2>
              <p class="meta">按不同维度查看接单员表现，点击列表可进入对比详情</p>
            </div>
            <button type="button" class="ghost-local rider-rank-refresh" @click="r.loadAdminDashboardData">刷新数据</button>
          </div>

          <div class="rider-rank-tabs">
            <button
              type="button"
              class="rider-rank-tab"
              :class="{ active: r.riderRankMetric === 'completed' }"
              @click="r.riderRankMetric = 'completed'"
            >
              完成单榜
            </button>
            <button
              type="button"
              class="rider-rank-tab"
              :class="{ active: r.riderRankMetric === 'rate' }"
              @click="r.riderRankMetric = 'rate'"
            >
              完成率榜
            </button>
            <button
              type="button"
              class="rider-rank-tab"
              :class="{ active: r.riderRankMetric === 'income' }"
              @click="r.riderRankMetric = 'income'"
            >
              收入榜
            </button>
            <button
              type="button"
              class="rider-rank-tab"
              :class="{ active: r.riderRankMetric === 'claim' }"
              @click="r.riderRankMetric = 'claim'"
            >
              接单榜
            </button>
          </div>

          <div v-if="r.adminRiderCompareRows.length === 0" class="placeholder rider-rank-empty">暂无排行榜数据</div>
          <template v-else>
            <div class="rider-podium">
              <div v-if="r.riderTop3.second" class="podium-card second">
                <img class="podium-rank-icon" src="/admin-rank/rank-2.png" alt="NO.2" width="48" height="48" />
                <div class="podium-avatar-shell">
                  <div class="podium-avatar">
                    <img
                      v-if="r.riderTop3.second.avatarUrl"
                      :src="r.normalizeImageUrl(r.riderTop3.second.avatarUrl)"
                      :alt="r.riderTop3.second.username"
                    />
                    <template v-else>{{ r.riderTop3.second.username.slice(0, 1).toUpperCase() }}</template>
                  </div>
                </div>
                <div class="podium-name">{{ r.riderTop3.second.username }}</div>
                <div class="podium-score">{{ r.riderRankMetricLabel(r.riderTop3.second) }}</div>
                <div class="podium-base" aria-hidden="true"><span class="podium-base-num">2</span></div>
              </div>
              <div v-if="r.riderTop3.first" class="podium-card first">
                <img class="podium-rank-icon podium-rank-icon--first" src="/admin-rank/rank-1.png" alt="NO.1" width="56" height="56" />
                <div class="podium-avatar-shell">
                  <div class="podium-avatar">
                    <img
                      v-if="r.riderTop3.first.avatarUrl"
                      :src="r.normalizeImageUrl(r.riderTop3.first.avatarUrl)"
                      :alt="r.riderTop3.first.username"
                    />
                    <template v-else>{{ r.riderTop3.first.username.slice(0, 1).toUpperCase() }}</template>
                  </div>
                </div>
                <div class="podium-name">{{ r.riderTop3.first.username }}</div>
                <div class="podium-score">{{ r.riderRankMetricLabel(r.riderTop3.first) }}</div>
                <div class="podium-base" aria-hidden="true"><span class="podium-base-num">1</span></div>
              </div>
              <div v-if="r.riderTop3.third" class="podium-card third">
                <img class="podium-rank-icon" src="/admin-rank/rank-3.png" alt="NO.3" width="48" height="48" />
                <div class="podium-avatar-shell">
                  <div class="podium-avatar">
                    <img
                      v-if="r.riderTop3.third.avatarUrl"
                      :src="r.normalizeImageUrl(r.riderTop3.third.avatarUrl)"
                      :alt="r.riderTop3.third.username"
                    />
                    <template v-else>{{ r.riderTop3.third.username.slice(0, 1).toUpperCase() }}</template>
                  </div>
                </div>
                <div class="podium-name">{{ r.riderTop3.third.username }}</div>
                <div class="podium-score">{{ r.riderRankMetricLabel(r.riderTop3.third) }}</div>
                <div class="podium-base" aria-hidden="true"><span class="podium-base-num">3</span></div>
              </div>
            </div>

            <div class="rider-rank-table">
              <div class="rider-rank-table-head">
                <span>排名</span>
                <span>接单员</span>
                <span class="right">成绩</span>
                <span class="right">占比</span>
              </div>

              <ol class="rider-rank-list">
                <li
                  v-for="(row, idx) in r.riderLeaderboardRows.slice(0, 10)"
                  :key="`rider-rank-page-${row.username}`"
                  :class="{ active: r.selectedRiderName === row.username, 'is-top3': idx < 3 }"
                  @click="r.adminModule = 'riderCompare'; r.selectedRiderName = row.username"
                >
                  <div class="rank-no" :class="idx < 3 ? `rank-no--${idx + 1}` : 'rank-no--rest'">
                    <img
                      v-if="idx < 3"
                      class="rank-no-pixel"
                      :src="`/admin-rank/rank-${idx + 1}.png`"
                      :alt="`第${idx + 1}名`"
                      width="36"
                      height="36"
                    />
                    <template v-else>{{ idx + 1 }}</template>
                  </div>
                  <div class="rank-user">
                    <span class="rank-avatar" :class="idx < 3 ? `rank-avatar--${idx + 1}` : ''">
                      <img
                        v-if="row.avatarUrl"
                        :src="r.normalizeImageUrl(row.avatarUrl)"
                        :alt="row.username"
                      />
                      <template v-else>{{ row.username.slice(0, 1).toUpperCase() }}</template>
                    </span>
                    <span class="rank-name">{{ row.username }}</span>
                  </div>
                  <div class="rank-score right">
                    <span class="rank-score-badge">{{ r.riderRankMetricLabel(row) }}</span>
                  </div>
                  <div class="rank-progress right">
                    <div class="rank-bar-track">
                      <div class="rank-bar">
                        <div
                          class="rank-bar-fill"
                          :class="idx < 3 ? `rank-bar-fill--${idx + 1}` : ''"
                          :style="{ width: `${Math.max((r.riderMetricValue(row) / r.riderRankMaxValue) * 100, 4)}%` }"
                        ></div>
                      </div>
                      <span class="rank-bar-label">{{ Math.round((r.riderMetricValue(row) / r.riderRankMaxValue) * 100) }}%</span>
                    </div>
                  </div>
                </li>
              </ol>
            </div>
          </template>
        </div>
      </section>

      <div v-else-if="r.adminModule === 'task'" class="admin-module">
        <section class="card admin-card admin-task-page">
          <div class="admin-task-page-head">
            <div>
              <h2>任务管理</h2>
              <p class="meta">筛选有效任务；待审核可直接通过或驳回</p>
            </div>
          </div>

          <div class="admin-task-filter-strip" role="tablist" aria-label="任务状态筛选">
            <button
              type="button"
              role="tab"
              class="admin-task-chip"
              :class="{ 'is-active': adminTaskStatFilter === 'effective' }"
              :aria-selected="adminTaskStatFilter === 'effective'"
              @click="setAdminTaskStatFilter('effective')"
            >
              全部有效
              <b>{{ r.adminVisibleTasks.length }}</b>
            </button>
            <button
              type="button"
              role="tab"
              class="admin-task-chip"
              :class="{ 'is-active': adminTaskStatFilter === 'PENDING_REVIEW' }"
              :aria-selected="adminTaskStatFilter === 'PENDING_REVIEW'"
              @click="setAdminTaskStatFilter('PENDING_REVIEW')"
            >
              待审核
              <b>{{ r.adminVisibleTasks.filter((t) => isPendingAdminReview(t)).length }}</b>
            </button>
            <button
              type="button"
              role="tab"
              class="admin-task-chip"
              :class="{ 'is-active': adminTaskStatFilter === 'REJECTED' }"
              :aria-selected="adminTaskStatFilter === 'REJECTED'"
              @click="setAdminTaskStatFilter('REJECTED')"
            >
              已驳回
              <b>{{ r.adminVisibleTasks.filter((t) => t.status === 'REJECTED').length }}</b>
            </button>
            <button
              type="button"
              role="tab"
              class="admin-task-chip"
              :class="{ 'is-active': adminTaskStatFilter === 'OPEN' }"
              :aria-selected="adminTaskStatFilter === 'OPEN'"
              @click="setAdminTaskStatFilter('OPEN')"
            >
              待接单
              <b>{{ r.adminVisibleTasks.filter((t) => t.status === 'OPEN').length }}</b>
            </button>
            <button
              type="button"
              role="tab"
              class="admin-task-chip"
              :class="{ 'is-active': adminTaskStatFilter === 'CLAIMED' }"
              :aria-selected="adminTaskStatFilter === 'CLAIMED'"
              @click="setAdminTaskStatFilter('CLAIMED')"
            >
              配送中
              <b>{{ r.adminVisibleTasks.filter((t) => t.status === 'CLAIMED').length }}</b>
            </button>
            <button
              type="button"
              role="tab"
              class="admin-task-chip"
              :class="{ 'is-active': adminTaskStatFilter === 'PENDING_CONFIRMATION' }"
              :aria-selected="adminTaskStatFilter === 'PENDING_CONFIRMATION'"
              @click="setAdminTaskStatFilter('PENDING_CONFIRMATION')"
            >
              待确认
              <b>{{ r.adminVisibleTasks.filter((t) => t.status === 'PENDING_CONFIRMATION').length }}</b>
            </button>
          </div>

          <div id="admin-task-list-anchor" class="admin-task-list-wrap">
            <div class="admin-task-list-toolbar">
              <span class="admin-task-list-count">
                {{ adminTasksFilteredForList.length }} 条
                <template v-if="adminTaskStatFilter !== 'effective'"> · {{ adminTaskFilterLabel }}</template>
              </span>
              <button
                v-if="adminTaskStatFilter !== 'effective'"
                type="button"
                class="ghost-local admin-task-filter-clear"
                @click="clearAdminTaskStatFilter"
              >
                清除筛选
              </button>
            </div>

            <p v-if="adminTasksFilteredForList.length === 0" class="placeholder admin-task-empty">当前筛选下暂无任务</p>
            <ul v-else class="admin-task-table">
              <li
                v-for="task in adminTasksFilteredForList"
                :key="`admin-${task.id}`"
                class="admin-task-tr"
                :class="{ 'needs-action': isPendingAdminReview(task) }"
              >
                <div class="admin-task-col-main">
                  <div class="admin-task-tr-top">
                    <span class="admin-task-id">#{{ task.id }}</span>
                    <h4>{{ task.title }}</h4>
                    <span class="status" :class="statusClass(task.status)">{{ statusLabelMap[task.status] }}</span>
                  </div>
                  <p class="admin-task-route-line">
                    <span>{{ task.pickupLocation || '—' }}</span>
                    <i aria-hidden="true">→</i>
                    <span>{{ task.dropoffLocation || '—' }}</span>
                  </p>
                  <p class="admin-task-people">
                    发布 {{ task.publisherName || '—' }}
                    <span class="sep">·</span>
                    接单 {{ task.claimerName || '无' }}
                    <span class="sep">·</span>
                    {{ task.contact || '无联系方式' }}
                  </p>
                </div>

                <div class="admin-task-col-reward">
                  <em>赏金</em>
                  <strong>¥{{ Number(task.reward).toFixed(2) }}</strong>
                </div>

                <div class="admin-task-col-ops">
                  <template v-if="isPendingAdminReview(task)">
                    <button
                      class="profile-task-action profile-task-action-ghost"
                      type="button"
                      @click="r.approveTask(task.id)"
                    >
                      通过
                    </button>
                    <button
                      class="profile-task-action profile-task-action-danger"
                      type="button"
                      @click="r.rejectTask(task.id)"
                    >
                      驳回
                    </button>
                  </template>
                  <span v-else class="admin-task-ops-idle">—</span>
                </div>
              </li>
            </ul>
          </div>
        </section>
      </div>

      <section v-else-if="r.adminModule === 'logs'" class="card admin-card">
        <div class="list-head">
          <h2>操作日志</h2>
          <button class="ghost-local" type="button" @click="r.fetchAdminLogs()">刷新当前页</button>
        </div>
        <div v-if="r.adminLogs.length === 0" class="placeholder">暂无操作日志</div>
        <ul v-else class="task-list admin-log-list">
          <li v-for="log in r.adminLogs" :key="`log-${log.id}`" class="task-item admin-log-item">
            <div class="task-top">
              <h3 class="admin-log-title">{{ adminActionTypeLabel(log.actionType) }}</h3>
              <span
                v-if="log.targetTaskId != null"
                class="status status-pending_confirmation admin-log-task-tag"
              >
                任务 #{{ log.targetTaskId }}
              </span>
            </div>
            <p class="meta admin-log-meta">{{ log.operatorName }} · {{ formatAdminLogTime(log.createdAt) }}</p>
            <p v-if="adminLogDetailBody(log)" class="desc admin-log-detail">{{ adminLogDetailBody(log) }}</p>
          </li>
        </ul>
        <div v-if="r.adminLogsPaging.totalElements > 0" class="admin-log-pagination">
          <span class="meta">
            共 {{ r.adminLogsPaging.totalElements }} 条 · 每页 {{ r.adminLogsPaging.size }} 条 · 第
            {{ r.adminLogsPaging.page }} / {{ adminLogPageCount }} 页
          </span>
          <div class="admin-log-pager-btns">
            <button class="ghost-local" type="button" :disabled="r.adminLogsPaging.page <= 1" @click="r.goAdminLogsPrev">
              上一页
            </button>
            <button
              class="ghost-local"
              type="button"
              :disabled="r.adminLogsPaging.page >= adminLogPageCount"
              @click="r.goAdminLogsNext"
            >
              下一页
            </button>
          </div>
        </div>
      </section>

      <section v-else-if="r.adminModule === 'feedback'" class="card admin-card admin-feedback-page">
        <div class="admin-feedback-page-head">
          <div>
            <h2>用户反馈</h2>
            <p class="meta">查看用户提交的功能建议与故障反馈，支持筛选、搜索与标记已读</p>
          </div>
          <button class="ghost-local" type="button" @click="refreshAdminFeedbacks(r.adminFeedbacksPaging.page)">
            {{ r.adminFeedbacksLoading ? '加载中...' : '刷新列表' }}
          </button>
        </div>

        <div class="admin-feedback-kpis">
          <button
            type="button"
            class="admin-feedback-kpi"
            :class="{ 'is-active': adminFeedbackReadFilter === 'all' && adminFeedbackTypeFilter === 'all' }"
            @click="setAdminFeedbackTypeFilter('all'); setAdminFeedbackReadFilter('all')"
          >
            <span class="admin-feedback-kpi-label">全部反馈</span>
            <strong>{{ adminFeedbackStats.total }}</strong>
          </button>
          <button
            type="button"
            class="admin-feedback-kpi admin-feedback-kpi--unread"
            :class="{ 'is-active': adminFeedbackReadFilter === 'unread' }"
            @click="setAdminFeedbackReadFilter('unread')"
          >
            <span class="admin-feedback-kpi-label">未读</span>
            <strong>{{ adminFeedbackStats.unread }}</strong>
          </button>
          <button
            type="button"
            class="admin-feedback-kpi"
            :class="{ 'is-active': adminFeedbackTypeFilter === 'suggest' }"
            @click="setAdminFeedbackTypeFilter('suggest')"
          >
            <span class="admin-feedback-kpi-label">功能建议</span>
            <strong>{{ adminFeedbackStats.suggest }}</strong>
          </button>
          <button
            type="button"
            class="admin-feedback-kpi admin-feedback-kpi--bug"
            :class="{ 'is-active': adminFeedbackTypeFilter === 'bug' }"
            @click="setAdminFeedbackTypeFilter('bug')"
          >
            <span class="admin-feedback-kpi-label">故障反馈</span>
            <strong>{{ adminFeedbackStats.bug }}</strong>
          </button>
        </div>

        <div class="admin-feedback-toolbar">
          <div class="admin-feedback-filter-group">
            <span class="admin-feedback-filter-label">类型</span>
            <div class="admin-feedback-chip-row">
              <button
                type="button"
                class="admin-feedback-chip"
                :class="{ 'is-active': adminFeedbackTypeFilter === 'all' }"
                @click="setAdminFeedbackTypeFilter('all')"
              >
                全部
              </button>
              <button
                type="button"
                class="admin-feedback-chip"
                :class="{ 'is-active': adminFeedbackTypeFilter === 'suggest' }"
                @click="setAdminFeedbackTypeFilter('suggest')"
              >
                功能建议
              </button>
              <button
                type="button"
                class="admin-feedback-chip"
                :class="{ 'is-active': adminFeedbackTypeFilter === 'bug' }"
                @click="setAdminFeedbackTypeFilter('bug')"
              >
                故障反馈
              </button>
            </div>
          </div>
          <div class="admin-feedback-filter-group">
            <span class="admin-feedback-filter-label">状态</span>
            <div class="admin-feedback-chip-row">
              <button
                type="button"
                class="admin-feedback-chip"
                :class="{ 'is-active': adminFeedbackReadFilter === 'all' }"
                @click="setAdminFeedbackReadFilter('all')"
              >
                全部
              </button>
              <button
                type="button"
                class="admin-feedback-chip"
                :class="{ 'is-active': adminFeedbackReadFilter === 'unread' }"
                @click="setAdminFeedbackReadFilter('unread')"
              >
                未读
              </button>
              <button
                type="button"
                class="admin-feedback-chip"
                :class="{ 'is-active': adminFeedbackReadFilter === 'read' }"
                @click="setAdminFeedbackReadFilter('read')"
              >
                已读
              </button>
            </div>
          </div>
          <input
            v-model.trim="adminFeedbackKeyword"
            class="admin-feedback-search"
            placeholder="搜索用户名 / 页面 / 内容 / ID"
          />
        </div>

        <div v-if="r.adminFeedbacksLoading" class="placeholder">加载中...</div>
        <div v-else-if="r.adminFeedbacks.length === 0" class="placeholder">暂无用户反馈</div>
        <div v-else-if="adminFeedbacksFiltered.length === 0" class="placeholder">未找到匹配反馈</div>

        <div v-else class="admin-feedback-layout">
          <div class="admin-feedback-table-pane">
            <div class="admin-users-table-wrap admin-feedback-table-wrap">
              <table class="admin-users-table admin-feedback-table">
                <thead>
                  <tr>
                    <th>状态</th>
                    <th>类型</th>
                    <th>用户</th>
                    <th>页面</th>
                    <th class="admin-feedback-col-content">反馈内容</th>
                    <th>截图</th>
                    <th>时间</th>
                    <th class="admin-users-actions-col">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="item in adminFeedbacksFiltered"
                    :key="`feedback-row-${item.id}`"
                    class="admin-feedback-row"
                    :class="{
                      'is-active': selectedFeedbackId === item.id,
                      'is-unread': !item.readFlag
                    }"
                    @click="selectAdminFeedback(item.id)"
                  >
                    <td>
                      <span
                        class="admin-feedback-status"
                        :class="item.readFlag ? 'is-read' : 'is-unread'"
                      >
                        {{ item.readFlag ? '已读' : '未读' }}
                      </span>
                    </td>
                    <td>
                      <span
                        class="admin-feedback-type-tag"
                        :class="item.feedbackType === 'bug' ? 'is-bug' : 'is-suggest'"
                      >
                        {{ feedbackTypeLabel(item.feedbackType) }}
                      </span>
                    </td>
                    <td>
                      <strong class="admin-feedback-user">{{ item.username }}</strong>
                      <span class="meta admin-feedback-id">#{{ item.id }}</span>
                    </td>
                    <td>{{ item.pageName || '其它' }}</td>
                    <td class="admin-feedback-col-content">
                      <p class="admin-feedback-preview">{{ feedbackContentPreview(item.content) }}</p>
                    </td>
                    <td>
                      <span v-if="item.images?.length" class="admin-feedback-shot-count">{{ item.images.length }} 张</span>
                      <span v-else class="meta">—</span>
                    </td>
                    <td class="admin-feedback-time">{{ formatFeedbackTime(item.createdAt) }}</td>
                    <td class="admin-users-actions-col">
                      <button
                        type="button"
                        class="ghost-local admin-feedback-row-action"
                        @click.stop="
                          r.setAdminFeedbackRead(
                            item.id,
                            !item.readFlag,
                            adminFeedbackTypeFilter,
                            adminFeedbackReadFilter
                          )
                        "
                      >
                        {{ item.readFlag ? '标未读' : '标已读' }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-if="r.adminFeedbacksPaging.totalElements > 0" class="admin-log-pagination">
              <span class="meta">
                共 {{ r.adminFeedbacksPaging.totalElements }} 条 · 每页 {{ r.adminFeedbacksPaging.size }} 条 · 第
                {{ r.adminFeedbacksPaging.page }} / {{ adminFeedbackPageCount }} 页
              </span>
              <div class="admin-log-pager-btns">
                <button
                  class="ghost-local"
                  type="button"
                  :disabled="r.adminFeedbacksPaging.page <= 1"
                  @click="r.goAdminFeedbacksPrev(adminFeedbackTypeFilter, adminFeedbackReadFilter)"
                >
                  上一页
                </button>
                <button
                  class="ghost-local"
                  type="button"
                  :disabled="r.adminFeedbacksPaging.page >= adminFeedbackPageCount"
                  @click="r.goAdminFeedbacksNext(adminFeedbackTypeFilter, adminFeedbackReadFilter)"
                >
                  下一页
                </button>
              </div>
            </div>
          </div>

          <aside class="admin-feedback-detail" :class="{ 'is-empty': !selectedFeedback }">
            <template v-if="selectedFeedback">
              <header class="admin-feedback-detail-head">
                <div>
                  <h3>反馈详情 #{{ selectedFeedback.id }}</h3>
                  <p class="meta">
                    {{ selectedFeedback.username }} · {{ formatFeedbackTime(selectedFeedback.createdAt) }}
                  </p>
                </div>
                <div class="admin-feedback-detail-badges">
                  <span
                    class="admin-feedback-type-tag"
                    :class="selectedFeedback.feedbackType === 'bug' ? 'is-bug' : 'is-suggest'"
                  >
                    {{ feedbackTypeLabel(selectedFeedback.feedbackType) }}
                  </span>
                  <span
                    class="admin-feedback-status"
                    :class="selectedFeedback.readFlag ? 'is-read' : 'is-unread'"
                  >
                    {{ selectedFeedback.readFlag ? '已读' : '未读' }}
                  </span>
                </div>
              </header>

              <dl class="admin-feedback-detail-meta">
                <div>
                  <dt>问题页面</dt>
                  <dd>{{ selectedFeedback.pageName || '其它' }}</dd>
                </div>
                <div>
                  <dt>截图数量</dt>
                  <dd>{{ selectedFeedback.images?.length || 0 }} 张</dd>
                </div>
              </dl>

              <section class="admin-feedback-detail-block">
                <h4>反馈内容</h4>
                <pre class="admin-feedback-detail-content">{{ selectedFeedback.content }}</pre>
              </section>

              <section v-if="selectedFeedback.images?.length" class="admin-feedback-detail-block">
                <h4>截图附件</h4>
                <div class="admin-feedback-detail-images">
                  <button
                    v-for="(img, idx) in selectedFeedback.images"
                    :key="`feedback-detail-img-${selectedFeedback.id}-${idx}`"
                    type="button"
                    class="admin-feedback-detail-image-btn"
                    @click="r.openImagePreview(r.normalizeImageUrl(img))"
                  >
                    <img :src="r.normalizeImageUrl(img)" alt="" />
                    <span>查看大图</span>
                  </button>
                </div>
              </section>

              <div class="admin-feedback-detail-actions">
                <button
                  type="button"
                  class="primary"
                  @click="
                    r.setAdminFeedbackRead(
                      selectedFeedback.id,
                      !selectedFeedback.readFlag,
                      adminFeedbackTypeFilter,
                      adminFeedbackReadFilter
                    )
                  "
                >
                  {{ selectedFeedback.readFlag ? '标记为未读' : '标记为已读' }}
                </button>
              </div>
            </template>
            <p v-else class="admin-feedback-detail-empty">请选择左侧列表中的一条反馈查看详情</p>
          </aside>
        </div>
      </section>


      <section v-else-if="r.adminModule === 'csRatings'" class="card admin-card admin-feedback-page">
        <div class="admin-feedback-page-head">
          <div>
            <h2>客服评价</h2>
            <p class="meta">查看用户对人工客服的星级、标签与留言，可按客服账号与星级筛选</p>
          </div>
          <button class="ghost-local" type="button" @click="refreshAdminCsRatings(r.adminCsRatingsPaging.page)">
            {{ r.adminCsRatingsLoading ? '加载中...' : '刷新列表' }}
          </button>
        </div>

        <div class="admin-feedback-kpis">
          <button type="button" class="admin-feedback-kpi" :class="{ 'is-active': adminCsRatingScoreFilter === 'all' }" @click="setAdminCsScoreFilter('all')">
            <span class="admin-feedback-kpi-label">全部评价</span>
            <strong>{{ r.adminCsRatingsTotal }}</strong>
          </button>
          <div class="admin-feedback-kpi">
            <span class="admin-feedback-kpi-label">平均星级</span>
            <strong>{{ Number(r.adminCsRatingsAverage || 0).toFixed(1) }}</strong>
          </div>
          <button type="button" class="admin-feedback-kpi" :class="{ 'is-active': adminCsRatingScoreFilter === '5' }" @click="setAdminCsScoreFilter('5')">
            <span class="admin-feedback-kpi-label">5 星</span>
            <strong>筛选</strong>
          </button>
          <button type="button" class="admin-feedback-kpi admin-feedback-kpi--bug" :class="{ 'is-active': adminCsRatingScoreFilter === 'low' }" @click="setAdminCsScoreFilter('low')">
            <span class="admin-feedback-kpi-label">1～2 星</span>
            <strong>差评</strong>
          </button>
        </div>

        <div class="admin-feedback-toolbar">
          <div class="admin-feedback-filter-group">
            <span class="admin-feedback-filter-label">星级</span>
            <div class="admin-feedback-chip-row">
              <button type="button" class="admin-feedback-chip" :class="{ active: adminCsRatingScoreFilter === 'all' }" @click="setAdminCsScoreFilter('all')">全部</button>
              <button type="button" class="admin-feedback-chip" :class="{ active: adminCsRatingScoreFilter === '5' }" @click="setAdminCsScoreFilter('5')">5星</button>
              <button type="button" class="admin-feedback-chip" :class="{ active: adminCsRatingScoreFilter === '4' }" @click="setAdminCsScoreFilter('4')">4星</button>
              <button type="button" class="admin-feedback-chip" :class="{ active: adminCsRatingScoreFilter === '3' }" @click="setAdminCsScoreFilter('3')">3星</button>
              <button type="button" class="admin-feedback-chip" :class="{ active: adminCsRatingScoreFilter === 'low' }" @click="setAdminCsScoreFilter('low')">差评</button>
            </div>
          </div>
          <input
            v-model.trim="adminCsRatingAgentFilter"
            class="admin-feedback-search"
            type="search"
            placeholder="按客服账号筛选…"
            @keydown.enter.prevent="refreshAdminCsRatings(1)"
          />
          <button type="button" class="ghost-local" @click="refreshAdminCsRatings(1)">搜索</button>
        </div>

        <div v-if="r.adminCsRatingsLoading && !(r.adminCsRatings || []).length" class="meta" style="padding: 24px;">加载中…</div>
        <div v-else class="admin-feedback-layout">
          <div class="admin-feedback-table-pane">
            <div class="admin-users-table-wrap admin-feedback-table-wrap">
              <table class="admin-users-table admin-feedback-table">
                <thead>
                  <tr>
                    <th>星级</th>
                    <th>客服</th>
                    <th>用户</th>
                    <th class="admin-feedback-col-content">标签 / 留言</th>
                    <th>时间</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="item in r.adminCsRatings || []"
                    :key="`cs-rating-${item.id}`"
                    class="admin-feedback-row"
                    :class="{ active: selectedCsRatingId === item.id }"
                    @click="selectedCsRatingId = item.id"
                  >
                    <td><strong style="color:#d97706">{{ scoreStars(item.score) }}</strong></td>
                    <td><strong class="admin-feedback-user">{{ item.agentUsername }}</strong></td>
                    <td>{{ item.fromUsername }}</td>
                    <td class="admin-feedback-col-content">
                      <div v-if="item.tags?.length" class="admin-cs-rating-tags admin-cs-rating-tags--compact">
                        <span
                          v-for="tag in item.tags.slice(0, 3)"
                          :key="`cs-row-tag-${item.id}-${tag}`"
                          class="admin-cs-rating-tag"
                          :class="{ warn: tag === '有待改进' }"
                        >{{ tag }}</span>
                      </div>
                      <p class="admin-feedback-preview">{{ item.comment || '（无留言）' }}</p>
                    </td>
                    <td class="admin-feedback-time">{{ formatCsRatingTime(item.createdAt) }}</td>
                    <td>
                      <button type="button" class="ghost-local admin-feedback-row-action" @click.stop="selectedCsRatingId = item.id">详情</button>
                    </td>
                  </tr>
                  <tr v-if="!(r.adminCsRatings || []).length">
                    <td colspan="6" class="meta" style="text-align:center;padding:28px;">暂无客服评价</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="admin-pager" style="margin-top:12px;display:flex;gap:8px;align-items:center;">
              <button type="button" class="ghost-local" :disabled="r.adminCsRatingsPaging.page <= 1" @click="r.goAdminCsRatingsPrev(adminCsRatingAgentFilter, adminCsRatingScoreFilter)">上一页</button>
              <span class="meta">第 {{ r.adminCsRatingsPaging.page }} / {{ Math.max(1, r.adminCsRatingsPaging.totalPages) }} 页</span>
              <button type="button" class="ghost-local" :disabled="r.adminCsRatingsPaging.page >= Math.max(1, r.adminCsRatingsPaging.totalPages)" @click="r.goAdminCsRatingsNext(adminCsRatingAgentFilter, adminCsRatingScoreFilter)">下一页</button>
            </div>
          </div>

          <aside class="admin-feedback-detail" :class="{ 'is-empty': !selectedCsRating }">
            <template v-if="selectedCsRating">
              <header class="admin-feedback-detail-head">
                <h3>评价详情 #{{ selectedCsRating.id }}</h3>
                <strong style="color:#d97706;font-size:18px">{{ scoreStars(selectedCsRating.score) }} {{ selectedCsRating.score }} 星</strong>
              </header>
              <dl class="admin-feedback-detail-meta">
                <div><dt>客服</dt><dd>{{ selectedCsRating.agentUsername }}</dd></div>
                <div><dt>用户</dt><dd>{{ selectedCsRating.fromUsername }}</dd></div>
                <div><dt>时间</dt><dd>{{ formatCsRatingTime(selectedCsRating.createdAt) }}</dd></div>
              </dl>
              <section class="admin-feedback-detail-block">
                <h4>标签</h4>
                <div v-if="selectedCsRating.tags?.length" class="admin-cs-rating-tags">
                  <span
                    v-for="tag in selectedCsRating.tags"
                    :key="`cs-tag-${selectedCsRating.id}-${tag}`"
                    class="admin-cs-rating-tag"
                    :class="{ warn: tag === '有待改进' }"
                  >{{ tag }}</span>
                </div>
                <p v-else class="meta">无</p>
              </section>
              <section class="admin-feedback-detail-block">
                <h4>留言</h4>
                <pre class="admin-feedback-detail-content">{{ selectedCsRating.comment || '（用户未填写留言）' }}</pre>
              </section>
            </template>
            <p v-else class="admin-feedback-detail-empty">请选择左侧列表中的一条评价查看详情</p>
          </aside>
        </div>
      </section>

      <section v-else-if="r.adminModule === 'user'" class="card admin-card">
        <div class="list-head admin-user-head">
          <div>
            <h2>用户管理</h2>
            <p class="meta admin-user-head-meta">
              普通用户 {{ adminUserRegularCount }} · 客服账号 {{ adminUserCsCount }}
            </p>
          </div>
        </div>
        <div class="admin-user-type-tabs">
          <button
            type="button"
            class="admin-user-type-tab"
            :class="{ 'is-active': adminUserTypeFilter === 'all' }"
            @click="adminUserTypeFilter = 'all'"
          >
            全部 <span class="admin-user-type-count">{{ r.adminUsers.length }}</span>
          </button>
          <button
            type="button"
            class="admin-user-type-tab"
            :class="{ 'is-active': adminUserTypeFilter === 'user' }"
            @click="adminUserTypeFilter = 'user'"
          >
            普通用户 <span class="admin-user-type-count">{{ adminUserRegularCount }}</span>
          </button>
          <button
            type="button"
            class="admin-user-type-tab admin-user-type-tab--cs"
            :class="{ 'is-active': adminUserTypeFilter === 'cs' }"
            @click="adminUserTypeFilter = 'cs'"
          >
            客服账号 <span class="admin-user-type-count">{{ adminUserCsCount }}</span>
          </button>
        </div>
        <div class="admin-user-toolbar">
          <input
            v-model.trim="adminUserKeyword"
            class="admin-user-search"
            placeholder="搜索用户名 / 注册手机号 / ID"
          />
          <button class="ghost-local" @click="r.fetchAdminUsers">刷新用户</button>
        </div>
        <div v-if="r.adminUsers.length === 0" class="placeholder">暂无用户数据</div>
        <div v-else-if="adminUserFiltered.length === 0" class="placeholder">未找到匹配用户</div>
        <div v-else class="admin-users-table-wrap">
          <table class="admin-users-table">
            <thead>
              <tr>
                <th>账号类型</th>
                <th>用户名</th>
                <th class="admin-users-col-phone">注册手机号</th>
                <th class="admin-users-col-id">ID</th>
                <th>在线</th>
                <th>账号</th>
                <th>头像</th>
                <th>接单员</th>
                <th>信誉分</th>
                <th>注册时间</th>
                <th class="admin-users-actions-col">操作</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="u in adminUsersPaged" :key="`u-${u.id}`">
                <tr :class="{ 'admin-users-row--cs': isCsStaffUser(u) }">
                  <td>
                    <span
                      class="admin-user-type-badge"
                      :class="{
                        'admin-user-type-badge--cs': isCsStaffUser(u),
                        'admin-user-type-badge--admin': isPlatformAdminUser(u),
                        'admin-user-type-badge--user': !isCsStaffUser(u) && !isPlatformAdminUser(u)
                      }"
                    >
                      {{ accountRoleLabel(u) }}
                    </span>
                    <select
                      class="admin-users-role-select"
                      :value="accountRoleOf(u)"
                      :disabled="r.actionLoadingMap[u.id]"
                      @change="r.setUserAccountRole(u.id, ($event.target as HTMLSelectElement).value)"
                    >
                      <option value="USER">普通用户</option>
                      <option value="ADMIN">管理员</option>
                      <option value="CUSTOMER_SERVICE">人工客服</option>
                    </select>
                  </td>
                  <td>
                    <strong class="admin-users-name">{{ u.username }}</strong>
                  </td>
                  <td class="meta admin-users-col-phone nowrap">{{ isCsStaffUser(u) ? '—' : (u.phone || '—') }}</td>
                  <td class="meta admin-users-col-id">{{ u.id }}</td>
                  <td>
                    <span class="status status-compact" :class="u.online ? 'status-completed' : 'status-cancelled'">
                      {{ u.online ? '在线' : '离线' }}
                    </span>
                  </td>
                  <td>
                    <span class="status status-compact" :class="u.enabled ? 'status-open' : 'status-cancelled'">
                      {{ u.enabled ? '正常' : '已禁用' }}
                    </span>
                  </td>
                  <td class="meta">{{ u.avatarPending ? '待审' : '正常' }}</td>
                  <td class="admin-users-rider-cell">
                    <template v-if="isCsStaffUser(u)">
                      <span class="meta">—</span>
                    </template>
                    <template v-else>
                      <span>{{ u.riderApproved ? '已认证' : u.riderApplicationPending ? '待审核' : '普通' }}</span>
                      <p v-if="u.riderApplicationPending" class="admin-users-rider-extra">
                        {{ u.riderRealName || '-' }} ｜ {{ u.riderStudentId || '-' }} ｜ {{ u.riderPhone || '-' }}
                        <span v-if="u.riderApplyReason" class="ellipsis" :title="u.riderApplyReason"> · {{ u.riderApplyReason }}</span>
                      </p>
                    </template>
                  </td>
                  <td class="admin-users-credit-cell">
                    <template v-if="isCsStaffUser(u)">
                      <span class="meta">—</span>
                    </template>
                    <template v-else>
                      <div class="admin-credit-chip">
                        <span class="admin-credit-chip-label">信誉分</span>
                        <strong>{{ u.creditScore ?? 0 }}</strong>
                      </div>
                      <input
                        v-model.number="u.creditScore"
                        type="number"
                        min="0"
                        max="100"
                        class="admin-users-credit-input"
                        :class="{ 'is-low': (u.creditScore ?? 0) < 60, 'is-mid': (u.creditScore ?? 0) >= 60 && (u.creditScore ?? 0) < 80, 'is-high': (u.creditScore ?? 0) >= 80 }"
                        aria-label="信誉分"
                      />
                      <p class="admin-users-credit-hint">0 - 100，低于 60 会影响接单</p>
                    </template>
                  </td>
                  <td class="meta nowrap">{{ r.formatDateOnly(u.createdAt) }}</td>
                  <td class="admin-users-actions-cell">
                    <div class="admin-users-actions">
                      <button
                        v-if="u.enabled"
                        class="danger admin-users-btn"
                        type="button"
                        :disabled="isPlatformAdminUser(u) || r.actionLoadingMap[u.id]"
                        @click="r.setUserEnabled(u.id, false)"
                      >
                        禁用
                      </button>
                      <button
                        v-else
                        class="admin-users-btn"
                        type="button"
                        :disabled="r.actionLoadingMap[u.id]"
                        @click="r.setUserEnabled(u.id, true)"
                      >
                        启用
                      </button>
                      <template v-if="!isCsStaffUser(u)">
                        <button
                          v-if="u.riderApplicationPending"
                          class="admin-users-btn"
                          type="button"
                          :disabled="r.actionLoadingMap[u.id]"
                          @click="r.auditRiderApplication(u.id, true)"
                        >
                          通过接单员
                        </button>
                        <button
                          v-if="u.riderApplicationPending"
                          class="danger admin-users-btn"
                          type="button"
                          :disabled="r.actionLoadingMap[u.id]"
                          @click="r.auditRiderApplication(u.id, false)"
                        >
                          驳回
                        </button>
                        <button
                          v-if="u.riderApproved && !u.riderApplicationPending"
                          class="danger admin-users-btn"
                          type="button"
                          :disabled="isPlatformAdminUser(u) || r.actionLoadingMap[u.id]"
                          @click="r.revokeRiderCertification(u.id)"
                        >
                          取消认证
                        </button>
                        <button
                          class="admin-users-btn admin-users-btn--credit"
                          type="button"
                          :disabled="r.actionLoadingMap[u.id]"
                          @click="r.updateUserCreditScore(u.id, Number(u.creditScore ?? 80))"
                        >
                          保存信誉分
                        </button>
                      </template>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
          <div class="admin-log-pagination" style="margin-top: 12px;">
            <span class="meta">
              共 {{ adminUserFiltered.length }} 条 · 每页
              <select v-model.number="adminUserPageSize" style="margin: 0 6px;">
                <option v-for="n in adminUserPageSizeOptions" :key="`admin-user-size-${n}`" :value="n">{{ n }}</option>
              </select>
              条 · 第 {{ adminUserPage }} / {{ adminUserPageCount }} 页
            </span>
            <div class="admin-log-pager-btns">
              <button class="ghost-local" type="button" :disabled="adminUserPage <= 1" @click="goAdminUserPrevPage">
                上一页
              </button>
              <button
                class="ghost-local"
                type="button"
                :disabled="adminUserPage >= adminUserPageCount"
                @click="goAdminUserNextPage"
              >
                下一页
              </button>
            </div>
          </div>
        </div>
      </section>

      <section v-else-if="r.adminModule === 'audit'" class="card admin-card">
        <div class="list-head">
          <h2>审核中心</h2>
          <button class="ghost-local" @click="() => r.refreshAuditCenter()">刷新审核中心</button>
        </div>

        <template v-if="r.auditSubModule === 'task'">
          <div class="admin-stats">
            <div class="mini-stat">
              <strong>{{ r.safeAllTasks.filter((t) => isPendingAdminReview(t)).length }}</strong>
              <span>待审核</span>
            </div>
            <div class="mini-stat">
              <strong>{{ r.safeAllTasks.filter((t) => t.status === 'REJECTED').length }}</strong>
              <span>已驳回</span>
            </div>
            <div class="mini-stat">
              <strong>{{ r.safeAllTasks.filter((t) => t.status === 'OPEN').length }}</strong>
              <span>已通过</span>
            </div>
          </div>

          <div style="display:flex; gap:10px; flex-wrap:wrap; margin-top: 14px; align-items:center;">
            <button
              class="primary"
              type="button"
              :disabled="r.aiAutoAuditSubmitting"
              @click="r.setAiAutoAuditEnabled(!r.aiAutoAuditEnabled)"
            >
              {{ r.aiAutoAuditSubmitting ? 'AI 切换中...' : `AI 自动审核：${r.aiAutoAuditEnabled ? '开启' : '关闭'}` }}
            </button>
            <button class="ghost-local" type="button" :disabled="r.aiAutoAuditSubmitting" @click="r.fetchAiAutoAuditEnabled">
              刷新状态
            </button>
          </div>

          <h3 style="margin-top: 14px">待审核任务</h3>
          <div v-if="r.safeAllTasks.filter((t) => isPendingAdminReview(t)).length === 0" class="placeholder">
            暂无待审核任务
          </div>
          <ul v-else class="task-list admin-audit-list">
            <li v-for="task in r.safeAllTasks.filter((t) => isPendingAdminReview(t))" :key="`audit-p-${task.id}`" class="task-item admin-audit-item">
              <div class="task-top">
                <h3>{{ task.title }}</h3>
                <span class="status" :class="statusClass(task.status)">{{ statusLabelMap[task.status] }}</span>
              </div>
              <p class="meta">发布人：{{ task.publisherName }} ｜ 联系方式：{{ task.contact }}</p>
              <p class="meta">取件：{{ task.pickupLocation }} → 送达：{{ task.dropoffLocation }}</p>
              <p class="meta">赏金：¥ {{ Number(task.reward).toFixed(2) }}</p>
              <p v-if="task.description" class="desc">{{ task.description }}</p>
              <div class="task-bottom">
                <strong>管理员操作</strong>
                <div class="actions">
                  <button class="profile-task-action profile-task-action-ghost" type="button" :disabled="r.actionLoadingMap[task.id]" @click="r.approveTask(task.id)">通过</button>
                  <button class="profile-task-action profile-task-action-danger" type="button" :disabled="r.actionLoadingMap[task.id]" @click="r.rejectTask(task.id)">驳回</button>
                </div>
              </div>
            </li>
          </ul>

          <h3 v-if="r.safeAllTasks.some((t) => isAwaitingBountyPayment(t))" style="margin-top: 18px">待支付赏金草稿</h3>
          <p v-if="r.safeAllTasks.some((t) => isAwaitingBountyPayment(t))" class="meta">
            以下任务已创建但尚未支付赏金，不会出现在「待审核任务」列表中。
          </p>
          <ul v-if="r.safeAllTasks.some((t) => isAwaitingBountyPayment(t))" class="task-list admin-audit-list">
            <li
              v-for="task in r.safeAllTasks.filter((t) => isAwaitingBountyPayment(t))"
              :key="`audit-unpaid-${task.id}`"
              class="task-item admin-audit-item"
            >
              <div class="task-top">
                <h3>{{ task.title }}</h3>
                <span class="status status-pending_review">待支付赏金</span>
              </div>
              <p class="meta">发布人：{{ task.publisherName }} ｜ 赏金：¥ {{ Number(task.reward).toFixed(2) }}</p>
              <p class="meta">取件：{{ task.pickupLocation }} → 送达：{{ task.dropoffLocation }}</p>
            </li>
          </ul>

          <h3 style="margin-top: 18px">最近驳回任务</h3>
          <div v-if="r.safeAllTasks.filter((t) => t.status === 'REJECTED').length === 0" class="placeholder">
            暂无驳回记录
          </div>
          <ul v-else class="task-list admin-audit-list">
            <li v-for="task in r.safeAllTasks.filter((t) => t.status === 'REJECTED').slice(0, 10)" :key="`audit-r-${task.id}`" class="task-item admin-audit-item">
              <div class="task-top">
                <h3>{{ task.title }}</h3>
                <span class="status" :class="statusClass(task.status)">{{ statusLabelMap[task.status] }}</span>
              </div>
              <p class="meta">发布人：{{ task.publisherName }} ｜ 更新时间：{{ task.updatedAt }}</p>
            </li>
          </ul>
        </template>

        <template v-else-if="r.auditSubModule === 'avatar'">
          <h3 style="margin-top: 14px">待审核头像</h3>
          <div v-if="r.pendingAvatarUsers.length === 0" class="placeholder">暂无待审核头像</div>
          <ul v-else class="task-list admin-audit-list">
            <li v-for="u in r.pendingAvatarUsers" :key="`audit-avatar-${u.id}`" class="task-item admin-audit-item">
              <div class="task-top">
                <h3>{{ u.username }}</h3>
                <span class="status status-pending_review">头像待审核</span>
              </div>
              <p class="meta">用户ID：{{ u.id }} ｜ 注册时间：{{ u.createdAt }}</p>
              <div class="avatar-audit-preview">
                <img
                  v-if="u.pendingAvatarUrl"
                  :src="r.normalizeImageUrl(u.pendingAvatarUrl)"
                  alt="待审核头像"
                  class="avatar-clickable"
                  @click.stop="r.openImagePreview(u.pendingAvatarUrl)"
                  @error="r.errorMsg = '头像预览加载失败，请重新提交头像后再审核'"
                />
                <span v-else class="placeholder">无头像预览</span>
              </div>
              <div class="task-bottom">
                <strong>管理员操作</strong>
                <div class="actions">
                  <button v-if="u.pendingAvatarUrl" class="profile-task-action profile-task-action-ghost" type="button" @click="r.openImagePreview(u.pendingAvatarUrl)">查看头像</button>
                  <button class="profile-task-action profile-task-action-primary" type="button" :disabled="r.actionLoadingMap[u.id]" @click="r.auditUserAvatar(u.id, true)">通过头像</button>
                  <button class="profile-task-action profile-task-action-danger" type="button" :disabled="r.actionLoadingMap[u.id]" @click="r.auditUserAvatar(u.id, false)">驳回头像</button>
                </div>
              </div>
            </li>
          </ul>
        </template>

        <template v-else>
          <h3 style="margin-top: 14px">待审核接单员申请</h3>
          <div v-if="r.adminUsers.filter((u) => u.riderApplicationPending).length === 0" class="placeholder">暂无待审核接单员申请</div>
          <ul v-else class="task-list admin-audit-list">
            <li v-for="u in r.adminUsers.filter((u) => u.riderApplicationPending)" :key="`audit-rider-${u.id}`" class="task-item admin-audit-item">
              <div class="task-top">
                <h3>{{ u.username }}</h3>
                <span class="status status-pending_confirmation">接单员待审核</span>
              </div>
              <p class="meta">用户ID：{{ u.id }} ｜ 申请时间：{{ u.riderAppliedAt || '-' }}</p>
              <p class="meta">真实姓名：{{ u.riderRealName || '-' }} ｜ 学号：{{ u.riderStudentId || '-' }}</p>
              <p class="meta">手机号：{{ u.riderPhone || '-' }}</p>
              <p class="desc">申请理由：{{ u.riderApplyReason || '-' }}</p>
              <div class="task-bottom">
                <strong>管理员操作</strong>
                <div class="actions">
                  <button class="profile-task-action profile-task-action-ghost" type="button" :disabled="r.actionLoadingMap[u.id]" @click="r.auditRiderApplication(u.id, true)">通过接单员</button>
                  <button class="profile-task-action profile-task-action-danger" type="button" :disabled="r.actionLoadingMap[u.id]" @click="r.auditRiderApplication(u.id, false)">驳回接单员</button>
                </div>
              </div>
            </li>
          </ul>
        </template>
      </section>

      <section v-else-if="r.adminModule === 'riderCompare'" class="card admin-card">
        <div class="list-head">
          <h2>接单员接单对比</h2>
          <span class="meta">统计全部已认证接单员（无接单记录显示为 0）</span>
        </div>
        <div v-if="r.adminRiderCompareRows.length === 0" class="placeholder">暂无接单员对比数据</div>
        <div v-else class="rider-compare-layout">
          <div class="rider-compare-table-wrap">
            <table class="rider-compare-table">
              <thead>
                <tr>
                  <th>接单员</th>
                  <th>总接单</th>
                  <th>已完成</th>
                  <th>超时单</th>
                  <th>完成率</th>
                  <th>累计收入</th>
                  <th>最近活跃</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in r.adminRiderCompareRows"
                  :key="`rider-compare-${row.username}`"
                  :class="{ active: r.selectedRiderName === row.username }"
                  @click="r.selectedRiderName = row.username"
                >
                  <td>{{ row.username }}</td>
                  <td>{{ row.claimCount }}</td>
                  <td>{{ row.completedCount }}</td>
                  <td>{{ row.timeoutCount }}</td>
                  <td>{{ row.completionRate }}%</td>
                  <td>¥ {{ row.totalIncome.toFixed(2) }}</td>
                  <td>{{ row.lastClaimAt ? r.formatDateOnly(row.lastClaimAt) : '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="r.selectedRiderRow" class="rider-detail-card">
            <h3>{{ r.selectedRiderRow.username }} · 详情统计</h3>
            <p class="meta">点击左侧行可以切换接单员</p>

            <div class="rider-kpi-grid">
              <div class="rider-kpi-item">
                <strong>{{ r.selectedRiderRow.claimCount }}</strong>
                <span>总接单</span>
              </div>
              <div class="rider-kpi-item">
                <strong>{{ r.selectedRiderRow.completedCount }}</strong>
                <span>已完成</span>
              </div>
              <div class="rider-kpi-item">
                <strong>{{ r.selectedRiderRow.completionRate }}%</strong>
                <span>完成率</span>
              </div>
              <div class="rider-kpi-item">
                <strong>{{ r.selectedRiderRow.timeoutCount }}</strong>
                <span>超时单</span>
              </div>
              <div class="rider-kpi-item">
                <strong>¥ {{ r.selectedRiderRow.totalIncome.toFixed(2) }}</strong>
                <span>累计收入</span>
              </div>
              <div class="rider-kpi-item">
                <strong>{{ r.selectedRiderRow.timeoutRate }}%</strong>
                <span>超时率</span>
              </div>
            </div>

            <div class="rider-chart">
              <div v-for="item in r.riderChartItems" :key="item.key" class="rider-bar-row">
                <div class="rider-bar-head">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.text }}</strong>
                </div>
                <div class="rider-bar-track">
                  <div class="rider-bar-fill" :style="{ width: item.width }"></div>
                </div>
              </div>
            </div>
            <div :ref="setRiderEchartEl" class="rider-echart"></div>
          </div>
        </div>
      </section>

      <section v-else-if="r.adminModule === 'walletAudit'" class="card admin-card">
        <div class="list-head">
          <h2>提现审核</h2>
          <div style="display:flex; gap:8px; flex-wrap:wrap;">
            <input v-model="r.adminWithdrawKeyword" placeholder="搜索用户名/收款人/账号" style="min-width:260px;" />
            <button class="ghost-local" @click="r.fetchAdminPendingWithdraws">刷新列表</button>
          </div>
        </div>
        <div v-if="r.filteredAdminPendingWithdraws.length === 0" class="placeholder">暂无待审核提现申请</div>
        <ul v-else class="task-list admin-audit-list">
          <li v-for="w in r.filteredAdminPendingWithdraws" :key="`audit-withdraw-${w.id}`" class="task-item admin-audit-item">
            <div class="task-top">
              <h3>{{ w.username }} 申请提现</h3>
              <span class="status status-pending_confirmation">¥ {{ Number(w.amount).toFixed(2) }}</span>
            </div>
            <p class="meta">收款人：{{ w.accountName || '-' }} ｜ 收款账号：{{ w.accountNo || '-' }}</p>
            <p v-if="String(w.remark || '').includes('ALIPAY_SANDBOX')" class="meta">渠道：支付宝沙箱（通过后自动转账到上述账号）</p>
            <p class="meta">申请时间：{{ w.createdAt }}</p>
            <p class="desc">申请备注：{{ w.remark || '-' }}</p>
            <input v-model="r.adminWithdrawRemarkMap[w.id]" placeholder="审核备注（可选，建议驳回时填写原因）" />
            <div class="task-bottom">
              <strong>管理员操作</strong>
              <div class="actions">
                <button class="profile-task-action profile-task-action-ghost" type="button" :disabled="r.actionLoadingMap[w.id]" @click="r.auditWithdrawRequest(w.id, true)">通过</button>
                <button class="profile-task-action profile-task-action-danger" type="button" :disabled="r.actionLoadingMap[w.id]" @click="r.auditWithdrawRequest(w.id, false)">驳回</button>
              </div>
            </div>
          </li>
        </ul>
      </section>

      <section v-else-if="r.adminModule === 'walletCredit'" class="card admin-card">
        <div class="list-head">
          <h2>用户钱包充值</h2>
          <button type="button" class="ghost-local" @click="r.fetchAdminUsers">刷新用户列表</button>
        </div>
        <p class="meta">
          向用户「当前余额」与「累计收入」同时增加金额；单次最高 100000 元。用户可在前台「我的钱包」流水中查看，类型为「管理员充值」。
        </p>
        <div class="task-form admin-wallet-credit-form" style="max-width: 520px; margin-top: 14px;">
          <label class="form-field-row" style="display: flex; flex-direction: column; gap: 6px;">
            <span>选择用户</span>
            <select v-model="r.adminWalletCreditUserId" class="admin-wallet-credit-select">
              <option value="">请选择用户</option>
              <option v-for="u in r.adminUsers" :key="`wc-user-${u.id}`" :value="String(u.id)">
                {{ u.username }}（ID {{ u.id }}）
              </option>
            </select>
          </label>
          <label class="form-field-row" style="display: flex; flex-direction: column; gap: 6px;">
            <span>充值金额（元）</span>
            <input v-model.number="r.adminWalletCreditAmount" type="number" min="0.01" step="0.01" placeholder="如 50" />
          </label>
          <label class="form-field-row" style="display: flex; flex-direction: column; gap: 6px;">
            <span>备注（可选，写入用户流水）</span>
            <input v-model="r.adminWalletCreditRemark" maxlength="200" placeholder="如：活动奖励、测试入账" />
          </label>
          <div class="actions" style="margin-top: 8px;">
            <button type="button" class="primary" :disabled="r.adminWalletCreditSubmitting" @click="r.submitAdminWalletCredit">
              {{ r.adminWalletCreditSubmitting ? '提交中...' : '确认充值' }}
            </button>
          </div>
        </div>
      </section>

      <section v-else-if="r.adminModule === 'coupons'" class="card admin-card admin-coupon-page">
        <div class="list-head">
          <h2>{{ r.adminCouponTab === 'activity' ? '大厅活动' : '优惠券管理' }}</h2>
          <button
            type="button"
            class="ghost-local"
            @click="() => { void r.fetchAdminHallActivities(); void r.fetchAdminCouponTemplates(); void r.fetchAdminCouponRedeemCodes() }"
          >
            刷新
          </button>
        </div>
        <p class="meta">
          <template v-if="r.adminCouponTab === 'activity'">
            通用大厅轮播：可配置「成为接单员 / 去发布 / 领券 / 钱包」等跳转，不只限于优惠券。上线=轮播展示，下线=隐藏。
          </template>
          <template v-else>
            优惠券模板、定向发券与兑换码。大厅轮播请在「大厅活动」配置；模板「启用可领」只控制用户能否领取该券。
          </template>
        </p>

        <div class="admin-coupon-tabs">
          <button type="button" :class="{ active: r.adminCouponTab === 'activity' }" @click="r.adminCouponTab = 'activity'">
            大厅活动
          </button>
          <button type="button" :class="{ active: r.adminCouponTab === 'templates' }" @click="r.adminCouponTab = 'templates'">
            全部模板
          </button>
          <button type="button" :class="{ active: r.adminCouponTab === 'issue' }" @click="r.adminCouponTab = 'issue'">
            定向发券
          </button>
          <button
            type="button"
            :class="{ active: r.adminCouponTab === 'codes' }"
            @click="r.adminCouponTab = 'codes'; void r.fetchAdminCouponRedeemCodes()"
          >
            兑换码库
          </button>
        </div>

        <div v-if="r.adminCouponTab === 'activity'" class="admin-hall-studio">
          <section class="admin-hall-list-panel">
            <div class="admin-hall-list-head">
              <div>
                <h3>轮播活动</h3>
                <p class="meta">管理当前大厅轮播条目的展示与隐藏</p>
              </div>
              <div class="admin-hall-stats">
                <span class="admin-hall-stat">
                  全部 <strong>{{ r.adminHallActivities.length }}</strong>
                </span>
                <span class="admin-hall-stat is-on">
                  展示中
                  <strong>{{ r.adminHallActivities.filter((a) => a.status === 'ACTIVE').length }}</strong>
                </span>
                <span class="admin-hall-stat is-off">
                  已隐藏
                  <strong>{{ r.adminHallActivities.filter((a) => a.status !== 'ACTIVE').length }}</strong>
                </span>
              </div>
            </div>

            <div v-if="r.adminHallActivitiesLoading" class="placeholder">加载中…</div>
            <div v-else-if="!r.adminHallActivities.length" class="placeholder admin-hall-empty">
              还没有大厅活动，在下方配置并发布即可展示
            </div>
            <ul v-else class="admin-hall-activity-list">
              <li
                v-for="t in r.adminHallActivities"
                :key="`ha-${t.id}`"
                class="admin-hall-activity-row"
                :class="{ 'is-active': t.status === 'ACTIVE', 'is-hidden': t.status !== 'ACTIVE' }"
              >
                <div class="admin-hall-activity-thumb" :class="`theme-${t.bannerTheme || 'warm'}`">
                  <img
                    v-if="t.bannerImageUrl || t.bannerImageUrlWeb"
                    :src="String(t.bannerImageUrl || t.bannerImageUrlWeb)"
                    alt=""
                  />
                  <span v-else class="admin-hall-activity-thumb-fallback">{{ r.hallActionTypeLabel(t.actionType) }}</span>
                </div>
                <div class="admin-hall-activity-main">
                  <div class="admin-hall-activity-title-row">
                    <h4>{{ t.title }}</h4>
                    <span class="admin-hall-status-pill" :class="t.status === 'ACTIVE' ? 'on' : 'off'">
                      {{ t.status === 'ACTIVE' ? '轮播展示中' : '已隐藏' }}
                    </span>
                  </div>
                  <p class="meta admin-hall-activity-meta">
                    {{ r.hallActionTypeLabel(t.actionType) }} · 按钮「{{ t.ctaText || '去看看' }}」
                    · {{ t.subtitle || '无副文案' }}
                  </p>
                  <div class="admin-hall-asset-flags">
                    <span :class="{ ok: !!t.bannerImageUrl }">小程序海报{{ t.bannerImageUrl ? ' ✓' : ' —' }}</span>
                    <span :class="{ ok: !!t.bannerImageUrlWeb }">网页海报{{ t.bannerImageUrlWeb ? ' ✓' : ' —' }}</span>
                  </div>
                </div>
                <div class="admin-hall-activity-ops">
                  <button
                    v-if="t.status !== 'ACTIVE'"
                    type="button"
                    class="primary"
                    @click="r.setAdminHallActivityStatus(t.id, 'ACTIVE')"
                  >
                    显示到轮播
                  </button>
                  <button v-else type="button" class="ghost-local" @click="r.setAdminHallActivityStatus(t.id, 'OFF')">
                    从轮播隐藏
                  </button>
                  <button type="button" class="ghost-local danger-text" @click="r.deleteAdminHallActivity(t.id)">
                    删除
                  </button>
                </div>
              </li>
            </ul>
          </section>

          <section class="admin-hall-compose">
            <div class="admin-hall-compose-head">
              <div>
                <h3>添加活动</h3>
                <p class="meta">先选活动类型，再配标题 / 按钮文案 / 海报，发布后出现在小程序与网页大厅轮播。</p>
              </div>
              <button
                type="button"
                class="primary admin-hall-publish-btn"
                :disabled="r.adminHallActivitySubmitting"
                @click="r.createAdminHallActivity"
              >
                {{ r.adminHallActivitySubmitting ? '发布中…' : '发布到大厅轮播' }}
              </button>
            </div>

            <div class="admin-hall-compose-body">
              <div class="task-form admin-hall-compose-fields">
                <label class="form-field-row admin-hall-span-2">
                  <span>活动类型</span>
                  <select v-model="r.adminHallActivityForm.actionType" @change="r.onHallActionTypeChange">
                    <option value="RIDER_APPLY">成为接单员（跳转认证页）</option>
                    <option value="PUBLISH">去发布任务</option>
                    <option value="COUPON_CLAIM">领取优惠券</option>
                    <option value="COUPON_CENTER">领券中心</option>
                    <option value="WALLET">我的钱包</option>
                    <option value="CUSTOM_PATH">自定义小程序页面</option>
                  </select>
                </label>

                <label class="form-field-row">
                  <span>活动标题</span>
                  <input
                    v-model="r.adminHallActivityForm.title"
                    placeholder="如：点击认证马上接单"
                    maxlength="12"
                  />
                  <p class="meta admin-upload-hint">
                    最多 12 字（{{ r.adminHallActivityForm.title.length }}/12）
                  </p>
                </label>
                <label class="form-field-row">
                  <span>副文案</span>
                  <input
                    v-model="r.adminHallActivityForm.subtitle"
                    placeholder="如：认证后接单"
                    maxlength="8"
                  />
                  <p class="meta admin-upload-hint">
                    最多 8 字（{{ r.adminHallActivityForm.subtitle.length }}/8）
                  </p>
                </label>
                <label class="form-field-row">
                  <span>按钮文案</span>
                  <input
                    v-model="r.adminHallActivityForm.ctaText"
                    :placeholder="r.defaultHallCta(r.adminHallActivityForm.actionType)"
                    maxlength="5"
                  />
                  <p class="meta admin-upload-hint">
                    最多 5 字（{{ r.adminHallActivityForm.ctaText.length }}/5）
                  </p>
                </label>
                <label class="form-field-row">
                  <span>默认主题色（无海报时）</span>
                  <select v-model="r.adminHallActivityForm.bannerTheme">
                    <option value="warm">暖橙</option>
                    <option value="cool">冰蓝</option>
                    <option value="green">清新绿</option>
                    <option value="blue">靛蓝</option>
                  </select>
                </label>
                <label class="form-field-row">
                  <span>排序（越小越靠前）</span>
                  <input v-model.number="r.adminHallActivityForm.sortOrder" type="number" />
                </label>

                <div v-if="r.adminHallActivityForm.actionType === 'CUSTOM_PATH'" class="form-field-row admin-hall-span-2">
                  <span>跳转页面</span>
                  <select v-model="r.adminHallActivityForm.actionValue">
                    <option disabled value="">请选择小程序页面</option>
                    <option
                      v-for="p in r.miniprogramPageOptions"
                      :key="p.path"
                      :value="p.path"
                    >
                      {{ p.name }}（{{ p.path }}）{{ p.needsQuery ? ' · 需参数' : '' }}
                    </option>
                  </select>
                  <p v-if="r.selectedHallCustomPage" class="meta" style="margin:6px 0 0;">
                    <strong>{{ r.selectedHallCustomPage.name }}</strong>：{{ r.selectedHallCustomPage.desc }}
                    <span v-if="r.selectedHallCustomPage.tab">（底部 Tab 页）</span>
                    <span v-if="r.selectedHallCustomPage.needsQuery" style="color:#b45309;">
                      该页通常需要额外参数，轮播点击可能打不开完整内容，建议换其他页。
                    </span>
                  </p>
                </div>

                <template v-if="r.adminHallActivityForm.actionType === 'COUPON_CLAIM'">
                  <label class="form-field-row admin-hall-span-2">
                    <span>关联已有券模板（可选）</span>
                    <select v-model="r.adminHallActivityForm.couponTemplateId">
                      <option value="">不选则按下方金额自动建券</option>
                      <option v-for="t in r.adminCouponTemplates" :key="`ht-${t.id}`" :value="String(t.id)">
                        #{{ t.id }} {{ t.name }}
                      </option>
                    </select>
                  </label>
                  <label v-if="!r.adminHallActivityForm.couponTemplateId" class="form-field-row">
                    <span>自动建券金额（元）</span>
                    <input v-model.number="r.adminHallActivityForm.couponAmount" type="number" min="0.01" step="0.01" />
                  </label>
                  <label v-if="!r.adminHallActivityForm.couponTemplateId" class="form-field-row">
                    <span>优惠券有效期</span>
                    <select v-model.number="r.adminHallActivityForm.couponValidDays">
                      <option :value="1">1 天</option>
                      <option :value="3">3 天</option>
                      <option :value="7">7 天</option>
                      <option :value="15">15 天</option>
                      <option :value="30">30 天</option>
                      <option :value="90">90 天</option>
                      <option :value="365">1 年</option>
                      <option :value="0">不限期限</option>
                    </select>
                    <p class="meta admin-upload-hint">从发布时起算；领取后按此截止时间过期</p>
                  </label>
                </template>

                <div class="form-field-row">
                  <span>小程序海报（可选）</span>
                  <input type="file" accept="image/*" @change="r.onAdminHallActivityBannerSelected($event)" />
                  <p class="meta admin-upload-hint">推荐 750×320（约 2.3:1），jpg/png，建议小于 500KB · 仅小程序大厅轮播使用</p>
                  <div v-if="r.adminHallActivityForm.bannerImageUrl" class="admin-coupon-preview">
                    <img :src="r.adminHallActivityForm.bannerImageUrl" alt="小程序海报预览" />
                    <button type="button" class="ghost-local" @click="r.adminHallActivityForm.bannerImageUrl = ''">清除</button>
                  </div>
                </div>
                <div class="form-field-row">
                  <span>网页海报（可选）</span>
                  <input type="file" accept="image/*" @change="r.onAdminHallActivityWebBannerSelected($event)" />
                  <p class="meta admin-upload-hint">推荐约 1200×280～1400×320 宽图（约 4:1），与小程序尺寸不同 · 仅网页大厅活动区使用</p>
                  <div v-if="r.adminHallActivityForm.bannerImageUrlWeb" class="admin-coupon-preview">
                    <img :src="r.adminHallActivityForm.bannerImageUrlWeb" alt="网页海报预览" />
                    <button type="button" class="ghost-local" @click="r.adminHallActivityForm.bannerImageUrlWeb = ''">清除</button>
                  </div>
                </div>
              </div>

              <aside class="admin-hall-compose-preview">
                <p class="admin-hall-preview-label">小程序预览</p>
                <div class="admin-hall-preview" :class="`theme-${r.adminHallActivityForm.bannerTheme || 'warm'}`">
                  <img
                    v-if="r.adminHallActivityForm.bannerImageUrl"
                    class="admin-hall-preview-bg"
                    :src="r.adminHallActivityForm.bannerImageUrl"
                    alt=""
                  />
                  <div
                    v-if="r.adminHallActivityForm.actionType === 'COUPON_CLAIM' && !r.adminHallActivityForm.bannerImageUrl"
                    class="admin-hall-preview-amount"
                  >
                    ¥{{ r.adminHallActivityForm.couponAmount || 0 }}
                  </div>
                  <div class="admin-hall-preview-copy">
                    <span class="kicker">{{ r.hallActionTypeLabel(r.adminHallActivityForm.actionType) }} · 小程序</span>
                    <strong>{{ r.adminHallActivityForm.title || '活动标题' }}</strong>
                    <em>{{ r.adminHallActivityForm.subtitle || '副文案会显示在这里' }}</em>
                  </div>
                  <span class="admin-hall-preview-cta">
                    {{ r.adminHallActivityForm.ctaText || r.defaultHallCta(r.adminHallActivityForm.actionType) }}
                  </span>
                </div>

                <p class="admin-hall-preview-label">网页预览</p>
                <div
                  class="admin-hall-preview admin-hall-preview--web"
                  :class="`theme-${r.adminHallActivityForm.bannerTheme || 'warm'}`"
                >
                  <img
                    v-if="r.adminHallActivityForm.bannerImageUrlWeb"
                    class="admin-hall-preview-bg"
                    :src="r.adminHallActivityForm.bannerImageUrlWeb"
                    alt=""
                  />
                  <div class="admin-hall-preview-copy">
                    <span class="kicker">{{ r.hallActionTypeLabel(r.adminHallActivityForm.actionType) }} · 网页</span>
                    <strong>{{ r.adminHallActivityForm.title || '活动标题' }}</strong>
                    <em>{{ r.adminHallActivityForm.subtitle || '副文案会显示在这里' }}</em>
                  </div>
                  <span class="admin-hall-preview-cta">
                    {{ r.adminHallActivityForm.ctaText || r.defaultHallCta(r.adminHallActivityForm.actionType) }}
                  </span>
                </div>

                <button
                  type="button"
                  class="primary admin-hall-publish-btn admin-hall-publish-btn--mobile"
                  :disabled="r.adminHallActivitySubmitting"
                  @click="r.createAdminHallActivity"
                >
                  {{ r.adminHallActivitySubmitting ? '发布中…' : '发布到大厅轮播' }}
                </button>
              </aside>
            </div>
          </section>
        </div>

        <div v-else-if="r.adminCouponTab === 'templates'" class="admin-coupon-studio">
          <section class="admin-coupon-list-panel">
            <div class="admin-coupon-studio-head">
              <div>
                <h3>已有模板</h3>
                <p class="meta">管理领取开关、主题色与删除；创建请在下方表单完成</p>
              </div>
              <div class="admin-hall-stats">
                <span class="admin-hall-stat">
                  全部 <strong>{{ r.adminCouponTemplates.length }}</strong>
                </span>
                <span class="admin-hall-stat is-on">
                  可领
                  <strong>{{
                    r.adminCouponTemplates.filter(
                      (t) => isHallClaimableMode(t.claimMode) && t.status === 'ACTIVE',
                    ).length
                  }}</strong>
                </span>
                <span class="admin-hall-stat is-off">
                  仅发放
                  <strong>{{
                    r.adminCouponTemplates.filter((t) => !isHallClaimableMode(t.claimMode)).length
                  }}</strong>
                </span>
              </div>
            </div>

            <div v-if="r.adminCouponTemplatesLoading" class="placeholder">加载中…</div>
            <div v-else-if="!r.adminCouponTemplates.length" class="placeholder admin-hall-empty">
              暂无模板，在下方创建后即可用于发券与兑换码
            </div>
            <ul v-else class="admin-coupon-template-rows">
              <li
                v-for="t in r.adminCouponTemplates"
                :key="`ct-${t.id}`"
                class="admin-coupon-template-row"
                :class="{
                  'is-active': isHallClaimableMode(t.claimMode) && t.status === 'ACTIVE',
                  'is-off': isHallClaimableMode(t.claimMode) && t.status !== 'ACTIVE',
                }"
              >
                <div class="admin-coupon-template-swatch" :class="`theme-${t.bannerTheme || 'warm'}`">
                  <strong>{{ couponThemeLabel(t.bannerTheme) }}</strong>
                  <span>{{ couponTemplateBrief(t) }}</span>
                </div>
                <div class="admin-coupon-template-main">
                  <div class="admin-coupon-template-title-row">
                    <h4>#{{ t.id }} {{ t.name }}</h4>
                    <span class="admin-coupon-chip">{{ couponClaimModeLabel(t.claimMode) }}</span>
                    <span
                      v-if="isHallClaimableMode(t.claimMode)"
                      class="admin-hall-status-pill"
                      :class="t.status === 'ACTIVE' ? 'on' : 'off'"
                    >
                      {{ t.status === 'ACTIVE' ? '可领取' : '已停用' }}
                    </span>
                  </div>
                  <p class="admin-coupon-brief">
                    {{ couponTypeLabel(t.type) }} · {{ couponTemplateBrief(t) }}
                    · 已发 {{ Number(t.issuedCount || 0) }}
                    <template v-if="Number(t.totalStock || 0) > 0"> / 库存 {{ t.totalStock }}</template>
                  </p>
                  <p class="meta">{{ couponValidityText(t) }}</p>
                  <p class="meta admin-coupon-desc">{{ t.description || '暂无说明' }}</p>
                </div>
                <div class="admin-coupon-template-ops">
                  <template v-if="isHallClaimableMode(t.claimMode)">
                    <button
                      v-if="t.status !== 'ACTIVE'"
                      type="button"
                      class="primary"
                      @click="r.setAdminCouponTemplateStatus(t.id, 'ACTIVE')"
                    >
                      启用可领
                    </button>
                    <button
                      v-else
                      type="button"
                      class="ghost-local"
                      @click="r.setAdminCouponTemplateStatus(t.id, 'OFF')"
                    >
                      停用领取
                    </button>
                  </template>
                  <button type="button" class="ghost-local danger-text" @click="r.deleteAdminCouponTemplate(t.id)">
                    删除
                  </button>
                  <select
                    class="admin-coupon-theme-select"
                    :value="String(t.bannerTheme || 'warm')"
                    title="主题色"
                    @change="r.onAdminCouponThemeChange(t.id, $event)"
                  >
                    <option value="warm">暖橙</option>
                    <option value="cool">冰蓝</option>
                    <option value="green">清新绿</option>
                    <option value="blue">靛蓝</option>
                  </select>
                </div>
              </li>
            </ul>
          </section>

          <section class="admin-coupon-compose">
            <div class="admin-coupon-studio-head">
              <div>
                <h3>新建优惠券模板</h3>
                <p class="meta">创建后默认可领取。若只要发券/兑换码、暂不开放用户自领：创建后点「停用领取」。</p>
              </div>
              <button
                type="button"
                class="primary admin-hall-publish-btn"
                :disabled="r.adminCouponCreateSubmitting"
                @click="r.createAdminCouponTemplate"
              >
                {{ r.adminCouponCreateSubmitting ? '创建中…' : '创建模板' }}
              </button>
            </div>

            <div class="task-form admin-coupon-compose-fields">
              <label class="form-field-row admin-hall-span-2">
                <span>名称</span>
                <input v-model="r.adminCouponForm.name" placeholder="如：周末赏金立减券" />
              </label>
              <label class="form-field-row">
                <span>类型</span>
                <select v-model="r.adminCouponForm.type">
                  <option value="FIXED">立减</option>
                  <option value="THRESHOLD">满减</option>
                  <option value="PERCENT">折扣</option>
                </select>
              </label>
              <label v-if="r.adminCouponForm.type !== 'PERCENT'" class="form-field-row">
                <span>减免金额（元）</span>
                <input v-model.number="r.adminCouponForm.discountAmount" type="number" min="0.01" step="0.01" />
              </label>
              <label v-if="r.adminCouponForm.type === 'THRESHOLD'" class="form-field-row">
                <span>门槛（元）</span>
                <input v-model.number="r.adminCouponForm.minSpend" type="number" min="0" step="0.01" />
              </label>
              <label v-if="r.adminCouponForm.type === 'PERCENT'" class="form-field-row">
                <span>折扣</span>
                <select v-model.number="r.adminCouponForm.discountPercent">
                  <option :value="0.1">0.1（一折）</option>
                  <option :value="0.2">0.2（二折）</option>
                  <option :value="0.3">0.3（三折）</option>
                  <option :value="0.4">0.4（四折）</option>
                  <option :value="0.5">0.5（五折）</option>
                  <option :value="0.6">0.6（六折）</option>
                  <option :value="0.7">0.7（七折）</option>
                  <option :value="0.8">0.8（八折）</option>
                  <option :value="0.9">0.9（九折）</option>
                </select>
              </label>
              <label v-if="r.adminCouponForm.type === 'PERCENT'" class="form-field-row">
                <span>最高减免（元，可选）</span>
                <input
                  v-model.number="r.adminCouponForm.maxDiscount"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="0 表示不限制"
                />
              </label>
              <label class="form-field-row admin-hall-span-2">
                <span>领取方式</span>
                <select v-model="r.adminCouponForm.claimMode">
                  <option value="ADMIN_ONLY">仅定向发券 / 兑换码</option>
                  <option value="PUBLIC">公开可领（需大厅活动引导）</option>
                  <option value="NEWCOMER">活动可领（需大厅活动引导）</option>
                </select>
              </label>
              <label class="form-field-row">
                <span>每人限领</span>
                <input v-model.number="r.adminCouponForm.perUserLimit" type="number" min="1" step="1" />
              </label>
              <label class="form-field-row">
                <span>总库存（0=不限）</span>
                <input v-model.number="r.adminCouponForm.totalStock" type="number" min="0" step="1" />
              </label>
              <label class="form-field-row">
                <span>默认主题色</span>
                <select v-model="r.adminCouponForm.bannerTheme">
                  <option value="warm">暖橙</option>
                  <option value="cool">冰蓝</option>
                  <option value="green">清新绿</option>
                  <option value="blue">靛蓝</option>
                </select>
              </label>
              <label class="form-field-row">
                <span>优惠券有效期</span>
                <select v-model.number="r.adminCouponForm.validDays">
                  <option :value="1">1 天</option>
                  <option :value="3">3 天</option>
                  <option :value="7">7 天</option>
                  <option :value="15">15 天</option>
                  <option :value="30">30 天</option>
                  <option :value="90">90 天</option>
                  <option :value="365">1 年</option>
                  <option :value="0">不限期限</option>
                </select>
                <p class="meta admin-upload-hint">从创建时起算；领取后按此截止时间过期</p>
              </label>
              <label class="form-field-row admin-hall-span-2">
                <span>场景码（可选）</span>
                <input v-model="r.adminCouponForm.sceneCode" placeholder="一般留空；系统活动如 NEWCOMER_Y5" />
                <p class="meta admin-upload-hint">
                  给程序识别用的内部标记，不是给用户看的。普通发券/兑换码请留空；系统内置如新人券
                  <code>NEWCOMER_Y5</code>、首发券 <code>FIRST_PUBLISH_Y3</code>。大厅领券活动请在「大厅活动」里关联模板，不必手填场景码。
                </p>
              </label>
              <label class="form-field-row admin-hall-span-2">
                <span>说明</span>
                <input v-model="r.adminCouponForm.description" placeholder="用户端说明文案" />
              </label>
            </div>

            <button
              type="button"
              class="primary admin-hall-publish-btn admin-hall-publish-btn--mobile"
              :disabled="r.adminCouponCreateSubmitting"
              @click="r.createAdminCouponTemplate"
            >
              {{ r.adminCouponCreateSubmitting ? '创建中…' : '创建模板' }}
            </button>
          </section>
        </div>

        <div v-else-if="r.adminCouponTab === 'issue'" class="task-form admin-coupon-panel" style="max-width:560px;">
          <h3>定向发券</h3>
          <p class="meta">启用或停用的模板都可定向发给指定用户。</p>
          <label class="form-field-row">
            <span>选择模板</span>
            <select v-model="r.adminCouponIssue.templateId">
              <option value="">请选择</option>
              <option v-for="t in r.adminCouponTemplates" :key="`issue-${t.id}`" :value="String(t.id)">
                #{{ t.id }} {{ t.name }} · {{ couponTemplateBrief(t) }}
              </option>
            </select>
          </label>
          <label class="form-field-row">
            <span>用户名</span>
            <input v-model="r.adminCouponIssue.username" list="admin-coupon-usernames" placeholder="精确用户名" />
            <datalist id="admin-coupon-usernames">
              <option v-for="u in r.adminUsers" :key="`cu-${u.id}`" :value="u.username" />
            </datalist>
          </label>
          <label class="form-field-row"><span>张数</span><input v-model.number="r.adminCouponIssue.count" type="number" min="1" max="20" /></label>
          <label class="form-field-row"><span>原因</span><input v-model="r.adminCouponIssue.reason" placeholder="活动 / 投诉补偿" /></label>
          <button type="button" class="primary" :disabled="r.adminCouponIssueSubmitting" @click="r.issueAdminCoupon">
            {{ r.adminCouponIssueSubmitting ? '发放中…' : '立即发券' }}
          </button>
        </div>

        <div v-else class="admin-coupon-grid">
          <div class="task-form admin-coupon-panel">
            <h3>生成兑换码</h3>
            <p class="meta">生成后会写入下方「兑换码库」，可随时复制。停用领取的模板也可生成。</p>
            <label class="form-field-row">
              <span>选择模板</span>
              <select v-model="r.adminCouponRedeem.templateId">
                <option value="">请选择</option>
                <option v-for="t in r.adminCouponTemplates" :key="`rc-${t.id}`" :value="String(t.id)">
                  #{{ t.id }} {{ t.name }} · {{ couponTemplateBrief(t) }}
                </option>
              </select>
            </label>
            <label class="form-field-row"><span>生成数量</span><input v-model.number="r.adminCouponRedeem.count" type="number" min="1" max="50" /></label>
            <label class="form-field-row"><span>每码可用次数</span><input v-model.number="r.adminCouponRedeem.maxUses" type="number" min="1" /></label>
            <label class="form-field-row"><span>指定兑换码（可选）</span><input v-model="r.adminCouponRedeem.code" maxlength="32" placeholder="留空则自动生成" /></label>
            <label class="form-field-row"><span>备注</span><input v-model="r.adminCouponRedeem.remark" placeholder="如：推广活动码" /></label>
            <div class="actions" style="display:flex; gap:8px; flex-wrap:wrap;">
              <button type="button" class="primary" :disabled="r.adminCouponRedeemSubmitting" @click="r.generateAdminCouponRedeemCodes">
                {{ r.adminCouponRedeemSubmitting ? '生成中…' : '生成兑换码' }}
              </button>
              <button v-if="r.adminCouponGeneratedCodes.length" type="button" class="ghost-local" @click="r.copyAdminCouponCodes">
                复制本次生成
              </button>
            </div>
            <div v-if="r.adminCouponGeneratedCodes.length" class="admin-coupon-codes-box">
              <strong>本次刚生成（{{ r.adminCouponGeneratedCodes.length }}）</strong>
              <pre>{{ r.adminCouponGeneratedCodes.join('\n') }}</pre>
            </div>
          </div>

          <div class="admin-coupon-panel">
            <div class="list-head" style="margin-bottom:10px;">
              <h3 style="margin:0;">兑换码库</h3>
              <button type="button" class="ghost-local" @click="r.fetchAdminCouponRedeemCodes">刷新列表</button>
            </div>
            <label class="form-field-row">
              <span>按模板筛选</span>
              <select
                v-model="r.adminCouponRedeemFilterTemplateId"
                @change="r.fetchAdminCouponRedeemCodes()"
              >
                <option value="">全部模板</option>
                <option v-for="t in r.adminCouponTemplates" :key="`rf-${t.id}`" :value="String(t.id)">
                  #{{ t.id }} {{ t.name }}
                </option>
              </select>
            </label>
            <div v-if="r.adminCouponRedeemCodesLoading" class="placeholder">加载中…</div>
            <div v-else-if="!r.adminCouponRedeemCodes.length" class="placeholder">暂无兑换码，请先在左侧生成</div>
            <div v-else class="admin-coupon-codes-table-wrap">
              <table class="admin-coupon-codes-table">
                <thead>
                  <tr>
                    <th>兑换码</th>
                    <th>模板</th>
                    <th>使用</th>
                    <th>状态</th>
                    <th>备注</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in r.adminCouponRedeemCodes" :key="`code-${row.id}`">
                    <td><code>{{ row.code }}</code></td>
                    <td>{{ r.adminCouponTemplateName(row.templateId) }}</td>
                    <td>{{ row.usedCount || 0 }}/{{ row.maxUses || 1 }}</td>
                    <td>{{ row.status }}</td>
                    <td>{{ row.remark || '—' }}</td>
                    <td>
                      <button type="button" class="ghost-local" @click="r.copyTextAdmin(String(row.code || ''))">复制</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </section>

      <section v-else-if="r.adminModule === 'customerService'" class="card admin-card">
        <div class="list-head">
          <h2>人工客服账号</h2>
          <span class="meta">共 {{ (r.adminCustomerServiceStaff || []).length }} 个</span>
          <button type="button" class="ghost-local" @click="r.fetchAdminCustomerServiceStaff">刷新列表</button>
        </div>
        <p class="meta">
          在此创建专用客服账号，客服使用 <a href="http://localhost:5174" target="_blank" rel="noopener">5174 工作台</a> 登录接待。
          用户转人工时会自动分配在线客服，不再使用 admin 账号。
        </p>

        <div class="task-form admin-wallet-credit-form" style="max-width: 520px; margin-top: 14px;">
          <label class="form-field-row" style="display: flex; flex-direction: column; gap: 6px;">
            <span>登录账号（5174 工作台用此账号登录）</span>
            <input v-model.trim="r.adminCsForm.username" placeholder="例如 cs_xiaoli 或 kefu01" />
          </label>
          <label class="form-field-row" style="display: flex; flex-direction: column; gap: 6px;">
            <span>初始密码（6-32 位）</span>
            <input v-model="r.adminCsForm.password" type="password" placeholder="设置登录密码" />
          </label>
          <div class="actions" style="margin-top: 8px;">
            <button type="button" class="primary" :disabled="r.adminCsCreating" @click="r.createAdminCustomerServiceStaff">
              {{ r.adminCsCreating ? '创建中…' : '创建客服账号' }}
            </button>
          </div>
        </div>

        <div
          v-if="r.adminCsLastCreated"
          class="admin-cs-created-card"
          style="margin-top: 16px; padding: 14px 16px; border-radius: 12px; background: #f0fdf4; border: 1px solid #bbf7d0;"
        >
          <strong style="color: #166534;">最近创建的客服账号（请保存，用于 5174 登录）</strong>
          <p class="meta" style="margin: 8px 0 0; line-height: 1.7;">
            用户 ID：<code>{{ r.adminCsLastCreated.id }}</code>
            <button type="button" class="ghost-local" style="margin-left: 8px;" @click="copyCsId(r.adminCsLastCreated.id)">
              复制 ID
            </button>
            <br />
            登录账号：<code>{{ r.adminCsLastCreated.username }}</code>
            登录密码：为您创建时填写的密码（系统不再明文显示）<br />
            登录地址：<a href="http://localhost:5174" target="_blank" rel="noopener">http://localhost:5174</a>
          </p>
        </div>

        <div v-if="!r.adminCustomerServiceStaff?.length" class="placeholder" style="margin-top: 18px;">
          暂无客服账号，请先创建。
        </div>
        <div v-else class="admin-users-table-wrap" style="margin-top: 18px;">
          <table class="admin-users-table">
            <thead>
              <tr>
                <th>用户 ID</th>
                <th>登录账号</th>
                <th>状态</th>
                <th>在线</th>
                <th>创建时间</th>
                <th class="admin-users-actions-col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in r.adminCustomerServiceStaff" :key="`cs-${u.id}`">
                <td class="meta">
                  {{ u.id }}
                  <button type="button" class="ghost-local" style="margin-left: 6px; font-size: 12px;" @click="copyCsId(u.id)">
                    复制
                  </button>
                </td>
                <td><strong>{{ u.username }}</strong></td>
                <td>{{ u.enabled ? '启用' : '禁用' }}</td>
                <td>{{ u.online ? '在线' : '离线' }}</td>
                <td class="meta">{{ formatCsCreatedAt(u.createdAt) }}</td>
                <td class="admin-users-actions-cell">
                  <div class="admin-users-actions">
                    <button
                      v-if="u.enabled"
                      type="button"
                      class="danger admin-users-btn"
                      :disabled="r.actionLoadingMap[u.id]"
                      @click="r.setUserEnabled(u.id, false)"
                    >
                      禁用
                    </button>
                    <button
                      v-else
                      type="button"
                      class="admin-users-btn"
                      :disabled="r.actionLoadingMap[u.id]"
                      @click="r.setUserEnabled(u.id, true)"
                    >
                      启用
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="r.adminModule === 'aiModel'" class="card admin-card">
        <div class="list-head">
          <h2>AI 模型配置（云端 API）</h2>
          <button type="button" class="ghost-local" @click="r.fetchAdminAiConfig">刷新</button>
        </div>
        <p class="meta">
          当前对接 DeepSeek：Base URL 填 <code>https://api.deepseek.com</code>，模型
          <code>deepseek-v4-pro</code>。密钥优先用环境变量 <code>DEEPSEEK_API_KEY</code>；后台也可临时覆盖。
        </p>
        <div class="task-form admin-wallet-credit-form" style="max-width: 620px; margin-top: 14px;">
          <label class="form-field-row" style="display: flex; flex-direction: column; gap: 6px;">
            <span>启用 AI 模块</span>
            <select v-model="r.adminAiConfig.enabled">
              <option :value="true">启用</option>
              <option :value="false">禁用</option>
            </select>
          </label>
          <label class="form-field-row" style="display: flex; flex-direction: column; gap: 6px;">
            <span>API Base URL</span>
            <input v-model.trim="r.adminAiConfig.baseUrl" placeholder="https://api.deepseek.com" />
          </label>
          <label class="form-field-row" style="display: flex; flex-direction: column; gap: 6px;">
            <span>API Key</span>
            <input
              v-model.trim="r.adminAiConfig.apiKey"
              type="password"
              autocomplete="off"
              :placeholder="
                r.adminAiConfig.apiKeyConfigured
                  ? `已配置（${r.adminAiConfig.apiKeyMasked || '****'}），留空则保持不变`
                  : 'sk-... 或环境变量 DEEPSEEK_API_KEY'
              "
            />
          </label>
          <label class="form-field-row" style="display: flex; flex-direction: column; gap: 6px;">
            <span>默认模型（审核等）</span>
            <select v-if="r.adminAiConfig.models.length > 0" v-model="r.adminAiConfig.model">
              <option v-for="m in r.adminAiConfig.models" :key="`llm-model-${m}`" :value="m">{{ m }}</option>
            </select>
            <input v-else v-model.trim="r.adminAiConfig.model" placeholder="deepseek-v4-pro" />
          </label>
          <label class="form-field-row" style="display: flex; flex-direction: column; gap: 6px;">
            <span>客服模型</span>
            <input v-model.trim="r.adminAiConfig.supportModel" placeholder="默认与审核模型相同" />
          </label>
          <label class="form-field-row" style="display: flex; flex-direction: column; gap: 6px;">
            <span>超时（秒）</span>
            <input v-model.number="r.adminAiConfig.timeoutSeconds" type="number" min="3" max="300" />
          </label>
          <label class="form-field-row" style="display: flex; flex-direction: column; gap: 6px;">
            <span>测试提示词</span>
            <textarea
              v-model.trim="r.adminAiTestPrompt"
              rows="3"
              maxlength="400"
              placeholder="例如：请用一句话回复：API 连接成功。"
            ></textarea>
          </label>
          <div class="actions" style="margin-top: 8px;">
            <button type="button" class="primary" @click="r.saveAdminAiConfig">保存配置</button>
            <button type="button" class="ghost-local" :disabled="r.adminAiTesting" @click="r.testAdminAiConfig">
              {{ r.adminAiTesting ? '测试中...' : '测试连接' }}
            </button>
          </div>
        </div>
      </section>

      <section v-else-if="r.adminModule === 'notice'" class="card admin-card">
        <div class="list-head">
          <h2>公告管理</h2>
          <div style="display:flex; gap:8px; flex-wrap:wrap;">
            <button class="ghost-local" :disabled="r.adminNoticeLoading" @click="r.fetchAdminNotices">
              {{ r.adminNoticeLoading ? '加载中...' : '刷新' }}
            </button>
            <button class="primary" @click="r.newAdminNotice">新建公告</button>
          </div>
        </div>

        <div class="admin-notice-layout">
          <aside class="admin-notice-list">
            <div v-if="r.adminNotices.length === 0" class="placeholder">暂无公告（可点击右上角“新建公告”）</div>
            <ul v-else class="task-list" style="margin-top: 0;">
              <li
                v-for="n in r.adminNotices"
                :key="`notice-${n.id}`"
                class="task-item admin-notice-item"
                :class="{ active: r.adminNoticeEditorId === n.id }"
                @click="r.selectAdminNotice(n)"
              >
                <div class="task-top">
                  <h3 style="font-size:16px;">{{ n.title }}</h3>
                  <div style="display:flex; gap:6px; flex-wrap:wrap; justify-content:flex-end;">
                    <span v-if="n.pinned" class="status status-pending_confirmation">置顶</span>
                    <span class="status" :class="n.published ? 'status-completed' : 'status-cancelled'">
                      {{ n.published ? '已上架' : '未上架' }}
                    </span>
                  </div>
                </div>
                <p class="meta" style="margin-top:6px;">更新：{{ n.updatedAt }}</p>
              </li>
            </ul>
          </aside>

          <section class="admin-notice-editor">
            <h3 style="margin:0 0 10px;">{{ r.adminNoticeEditorId == null ? '新建公告' : `编辑公告 #${r.adminNoticeEditorId}` }}</h3>
            <input
              v-model="r.adminNoticeEditor.title"
              placeholder="公告标题（最多80字）"
              maxlength="80"
              @input="r.adminNoticeEditorDirty = true"
            />
            <textarea
              v-model="r.adminNoticeEditor.content"
              rows="8"
              maxlength="5000"
              placeholder="公告内容（支持纯文本，最多5000字）"
              @input="r.adminNoticeEditorDirty = true"
            ></textarea>
            <div class="admin-notice-flags">
              <label class="admin-flag-item">
                <input type="checkbox" v-model="r.adminNoticeEditor.pinned" @change="r.adminNoticeEditorDirty = true" />
                <span class="admin-flag-text">
                  <strong>置顶</strong>
                  <small>优先展示在公告列表前面</small>
                </span>
              </label>
              <label class="admin-flag-item">
                <input type="checkbox" v-model="r.adminNoticeEditor.published" @change="r.adminNoticeEditorDirty = true" />
                <span class="admin-flag-text">
                  <strong>上架</strong>
                  <small>前台可见</small>
                </span>
              </label>
            </div>
            <div class="actions" style="justify-content:flex-end;">
              <button class="ghost-local" type="button" @click="r.adminNoticeEditorId != null && r.adminSelectedNotice ? r.resetAdminNoticeEditor(r.adminSelectedNotice) : r.resetAdminNoticeEditor(null)">
                重置
              </button>
              <button class="primary" type="button" :disabled="r.actionLoadingMap[r.adminNoticeEditorId ?? -9999]" @click="r.saveAdminNotice">
                {{ r.actionLoadingMap[r.adminNoticeEditorId ?? -9999] ? '保存中...' : '保存公告' }}
              </button>
            </div>

            <div v-if="r.adminSelectedNotice" class="admin-notice-actions">
              <div class="actions" style="justify-content:space-between;">
                <div style="display:flex; gap:8px; flex-wrap:wrap;">
                  <button class="ghost-local" type="button" :disabled="r.actionLoadingMap[r.adminSelectedNotice.id]" @click="r.toggleAdminNoticePublish(r.adminSelectedNotice)">
                    {{ r.adminSelectedNotice.published ? '下架' : '上架' }}
                  </button>
                  <button class="ghost-local" type="button" :disabled="r.actionLoadingMap[r.adminSelectedNotice.id]" @click="r.toggleAdminNoticePin(r.adminSelectedNotice)">
                    {{ r.adminSelectedNotice.pinned ? '取消置顶' : '置顶' }}
                  </button>
                </div>
                <button class="danger" type="button" :disabled="r.actionLoadingMap[r.adminSelectedNotice.id]" @click="r.deleteAdminNotice(r.adminSelectedNotice)">
                  删除
                </button>
              </div>
              <p class="meta" style="margin-top:8px;">
                最近操作：{{ r.adminSelectedNotice.operator || '-' }} ｜ 创建：{{ r.adminSelectedNotice.createdAt }} ｜ 更新：{{ r.adminSelectedNotice.updatedAt }}
              </p>
            </div>
          </section>
        </div>
      </section>

      <section v-else class="card admin-card">
        <h2>公告管理</h2>
        <p class="meta">公告管理模块已预留，可继续扩展发布/下线公告。</p>
      </section>
    </section>
  </div>
</template>

<style scoped>
.admin-task-btn {
  min-width: 64px;
  height: 36px;
  padding: 0 14px;
  border: 0;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: #64748b;
  box-shadow: 0 6px 14px rgba(100, 116, 139, 0.18);
}
.admin-task-btn.primary {
  background: #fff;
  color: #2563eb;
  border: 1px solid rgba(37, 99, 235, 0.35);
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.10);
}
.admin-task-btn.danger {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: #fff;
  box-shadow: 0 8px 18px rgba(220, 38, 38, 0.2);
}
.admin-task-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
.admin-audit-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.admin-audit-item {
  padding-bottom: 14px;
}
.admin-audit-list {
  gap: 12px;
}
.admin-rider-overview {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.rider-dash-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
@media (max-width: 1100px) {
  .rider-dash-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 520px) {
  .rider-dash-kpis {
    grid-template-columns: 1fr;
  }
}
.rider-dash-kpi {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid var(--border, rgba(0, 0, 0, 0.08));
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.rider-dash-kpi-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.rider-dash-kpi-icon svg {
  width: 22px;
  height: 22px;
  display: block;
}
.rider-dash-kpi--blue .rider-dash-kpi-icon {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.1);
}
.rider-dash-kpi--green .rider-dash-kpi-icon {
  color: #16a34a;
  background: rgba(22, 163, 74, 0.1);
}
.rider-dash-kpi--orange .rider-dash-kpi-icon {
  color: #ea580c;
  background: rgba(234, 88, 12, 0.1);
}
.rider-dash-kpi--red .rider-dash-kpi-icon {
  color: #dc2626;
  background: rgba(220, 38, 38, 0.1);
}
.rider-dash-kpi-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.rider-dash-kpi-body strong {
  font-size: 1.65rem;
  font-weight: 700;
  line-height: 1.1;
  color: #0f172a;
}
.rider-dash-kpi-body span {
  font-size: 0.88rem;
  color: #64748b;
}
.rider-dash-chart-card h2 {
  margin: 0;
}
.rider-dash-chart-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}
.rider-dash-chart-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.rider-dash-range {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
}
.rider-dash-range select {
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--border, rgba(0, 0, 0, 0.12));
  background: #fff;
  font-size: 0.9rem;
}
.rider-dash-chart-hint {
  margin: 0 0 10px;
  font-size: 0.82rem;
}
.rider-dash-echart {
  width: 100%;
  height: 360px;
  min-height: 260px;
}
.rider-dash-bottom {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
@media (max-width: 800px) {
  .rider-dash-bottom {
    grid-template-columns: 1fr;
  }
}
.rider-dash-panel h2 {
  margin: 0 0 12px;
  font-size: 1.05rem;
}
.rider-dash-dist {
  list-style: none;
  margin: 0;
  padding: 0;
}
.rider-dash-dist li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border, rgba(0, 0, 0, 0.06));
  font-size: 0.92rem;
}
.rider-dash-dist li:last-child {
  border-bottom: none;
}
.rider-dash-dist span {
  color: #64748b;
}
.rider-dash-dist strong {
  color: #0f172a;
  font-weight: 600;
}
.rider-dash-ok {
  color: #16a34a !important;
}

.admin-log-title {
  font-weight: 600;
}
.admin-log-meta {
  margin-top: 4px;
}
.admin-log-detail {
  margin-top: 8px;
  line-height: 1.55;
}
.admin-log-task-tag {
  font-size: 0.85rem;
}
.admin-log-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border, rgba(0, 0, 0, 0.08));
}
.admin-log-pager-btns {
  display: flex;
  gap: 8px;
}

.admin-coupon-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 14px 0 16px;
}
.admin-coupon-tabs button {
  border: 1px solid rgba(0, 0, 0, 0.1);
  background: #fff;
  border-radius: 999px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 14px;
}
.admin-coupon-tabs button.active {
  background: #5c3d2e;
  color: #fff;
  border-color: #5c3d2e;
}
.admin-coupon-grid {
  display: grid;
  grid-template-columns: minmax(280px, 380px) 1fr;
  gap: 18px;
  align-items: start;
}
@media (max-width: 960px) {
  .admin-coupon-grid {
    grid-template-columns: 1fr;
  }
}
.admin-coupon-panel {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 14px;
  padding: 16px 18px;
}
.admin-coupon-panel h3 {
  margin: 0 0 12px;
  font-size: 16px;
}
.admin-coupon-panel .form-field-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}
.admin-coupon-preview {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.admin-coupon-preview img {
  width: 160px;
  height: 72px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid #eee;
}
.admin-coupon-template-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
  max-height: 640px;
  overflow: auto;
}
.admin-coupon-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.admin-coupon-count {
  margin-left: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #888;
}
.admin-coupon-template-card {
  display: grid;
  grid-template-columns: 132px 1fr;
  gap: 14px;
  padding: 14px;
  border: 1px solid rgba(0, 0, 0, 0.07);
  border-radius: 14px;
  background: #fff;
}
.admin-coupon-thumb {
  width: 132px;
  height: 80px;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
  border: 1px solid rgba(0, 0, 0, 0.06);
}
.admin-coupon-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.admin-coupon-thumb-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 13px;
  color: #3d3a33;
  font-weight: 650;
}
.admin-coupon-thumb.theme-warm {
  background: linear-gradient(120deg, #fff1e0, #ffc49a);
}
.admin-coupon-thumb.theme-cool {
  background: linear-gradient(120deg, #e8f4ff, #b8d9ff);
}
.admin-coupon-thumb.theme-green {
  background: linear-gradient(120deg, #e8f8ef, #b8efcf);
}
.admin-coupon-thumb.theme-blue {
  background: linear-gradient(120deg, #eef0ff, #c5c9ff);
}
.admin-coupon-template-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}
.admin-coupon-template-head h3 {
  margin: 0;
  font-size: 15px;
  line-height: 1.35;
}
.admin-coupon-chip {
  flex-shrink: 0;
  padding: 3px 8px;
  border-radius: 999px;
  background: #f3f0ea;
  color: #5c5246;
  font-size: 12px;
  font-weight: 600;
}
.admin-coupon-brief {
  margin: 6px 0 4px;
  font-size: 13px;
  color: #3d3a33;
}
.admin-coupon-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 10px;
}
.admin-coupon-upload-btn {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}
.admin-coupon-theme-select {
  height: 32px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  padding: 0 8px;
  background: #fff;
}
@media (max-width: 720px) {
  .admin-coupon-form-grid {
    grid-template-columns: 1fr;
  }
  .admin-coupon-template-card {
    grid-template-columns: 1fr;
  }
  .admin-coupon-thumb {
    width: 100%;
    height: 100px;
  }
}
.admin-coupon-codes-box {
  margin-top: 12px;
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}
.admin-coupon-codes-box pre {
  margin: 8px 0 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
}
.admin-coupon-codes-table-wrap {
  max-height: 480px;
  overflow: auto;
  border: 1px solid #eee;
  border-radius: 10px;
}
.admin-coupon-codes-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.admin-coupon-codes-table th,
.admin-coupon-codes-table td {
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
  text-align: left;
  white-space: nowrap;
}
.admin-coupon-codes-table th {
  background: #fafafa;
  position: sticky;
  top: 0;
}
.admin-coupon-codes-table code {
  font-weight: 600;
  letter-spacing: 0.04em;
}

/* —— 热门地点 TOP10 —— */
.admin-hot-places {
  padding: 16px 18px 18px;
}
.admin-hot-places-head h2 {
  margin: 0 0 4px;
}
.admin-hot-places-head .meta {
  margin: 0 0 14px;
}
.admin-hot-places-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.admin-hot-place-panel {
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 14px;
  background: #faf9f7;
  padding: 12px 14px 14px;
}
.admin-hot-place-panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}
.admin-hot-place-panel-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: #2a2621;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.admin-hot-place-title-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.admin-hot-place-title-icon svg {
  width: 15px;
  height: 15px;
  display: block;
}
.admin-hot-place-panel--pickup .admin-hot-place-title-icon {
  color: #2563eb;
  background: rgba(37, 99, 235, 0.1);
}
.admin-hot-place-panel--dropoff .admin-hot-place-title-icon {
  color: #c9782e;
  background: rgba(201, 120, 46, 0.12);
}
.admin-hot-place-panel-head .meta {
  margin: 0;
  font-size: 12px;
}
.admin-hot-place-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.admin-hot-place-list li {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}
.admin-hot-place-rank {
  width: 28px;
  text-align: center;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.02em;
  color: #9a9288;
  font-variant-numeric: tabular-nums;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.admin-hot-place-list li.is-top3 .admin-hot-place-rank {
  color: #2a2621;
  font-weight: 750;
}
.admin-hot-place-panel--pickup .admin-hot-place-list li.is-top3 .admin-hot-place-rank {
  color: #2563eb;
}
.admin-hot-place-panel--dropoff .admin-hot-place-list li.is-top3 .admin-hot-place-rank {
  color: #c9782e;
}
.admin-hot-place-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.admin-hot-place-line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.admin-hot-place-name {
  min-width: 0;
  font-size: 13px;
  color: #2f2a24;
  font-weight: 550;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.admin-hot-place-line strong {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 700;
  color: #2a2621;
  font-variant-numeric: tabular-nums;
}
.admin-hot-place-track {
  height: 6px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.admin-hot-place-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.35s ease;
}
.admin-hot-place-panel--pickup .admin-hot-place-fill {
  background: linear-gradient(90deg, #5b8def, #3b6fd9);
}
.admin-hot-place-panel--dropoff .admin-hot-place-fill {
  background: linear-gradient(90deg, #e8a05c, #c9782e);
}
.admin-hot-place-list li.is-top3 .admin-hot-place-name {
  font-weight: 650;
}
@media (max-width: 900px) {
  .admin-hot-places-grid {
    grid-template-columns: 1fr;
  }
}

/* —— 任务管理页布局 —— */
.admin-task-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.admin-task-page-head h2 {
  margin: 0 0 4px;
}
.admin-task-page-head .meta {
  margin: 0;
}
.admin-task-filter-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px;
  border-radius: 14px;
  background: #f3f0eb;
  border: 1px solid rgba(0, 0, 0, 0.04);
}
.admin-task-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: #5c554c;
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.admin-task-chip b {
  min-width: 1.25em;
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #2a2621;
}
.admin-task-chip:hover {
  background: rgba(255, 255, 255, 0.72);
}
.admin-task-chip.is-active {
  background: #fff;
  border-color: rgba(92, 61, 46, 0.18);
  color: #2a2621;
  box-shadow: 0 1px 2px rgba(47, 42, 36, 0.06);
}
.admin-task-chip.is-active b {
  color: #5c3d2e;
}
.admin-task-list-wrap {
  scroll-margin-top: 20px;
}
.admin-task-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}
.admin-task-list-count {
  font-size: 13px;
  color: #6b645c;
}
.admin-task-empty {
  padding: 36px 12px;
  border: 1px dashed rgba(0, 0, 0, 0.1);
  border-radius: 12px;
  background: #faf9f7;
}
.admin-task-table {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 14px;
  overflow: hidden;
  background: #fff;
}
.admin-task-tr {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 108px 132px;
  gap: 12px 16px;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: #fff;
}
.admin-task-tr:last-child {
  border-bottom: none;
}
.admin-task-tr:hover {
  background: #fbfaf8;
}
.admin-task-tr.needs-action {
  background: #fffbf5;
}
.admin-task-tr.needs-action:hover {
  background: #fff8ee;
}
.admin-task-col-main {
  min-width: 0;
}
.admin-task-tr-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.admin-task-id {
  font-size: 12px;
  color: #9a9288;
  font-variant-numeric: tabular-nums;
}
.admin-task-tr-top h4 {
  margin: 0;
  font-size: 15px;
  line-height: 1.3;
  color: #2a2621;
}
.admin-task-route-line {
  margin: 0 0 4px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 8px;
  font-size: 13px;
  color: #3d3a33;
}
.admin-task-route-line i {
  font-style: normal;
  color: #b0a89e;
  font-weight: 600;
}
.admin-task-people {
  margin: 0;
  font-size: 12px;
  color: #8a8278;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.admin-task-people .sep {
  margin: 0 2px;
  color: #c4bbb0;
}
.admin-task-col-reward {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-end;
  text-align: right;
}
.admin-task-col-reward em {
  font-style: normal;
  font-size: 11px;
  color: #9a9288;
}
.admin-task-col-reward strong {
  font-size: 16px;
  font-weight: 700;
  color: #5c3d2e;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.01em;
}
.admin-task-col-ops {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  align-items: center;
  min-height: 34px;
}
.admin-task-ops-idle {
  color: #cfc7bc;
  font-size: 14px;
}
@media (max-width: 900px) {
  .admin-task-tr {
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-areas:
      'main reward'
      'main ops';
  }
  .admin-task-col-main {
    grid-area: main;
  }
  .admin-task-col-reward {
    grid-area: reward;
    align-self: start;
  }
  .admin-task-col-ops {
    grid-area: ops;
    justify-content: flex-end;
  }
}
@media (max-width: 640px) {
  .admin-task-tr {
    grid-template-columns: 1fr;
    grid-template-areas:
      'main'
      'reward'
      'ops';
  }
  .admin-task-col-reward {
    align-items: flex-start;
    text-align: left;
    flex-direction: row;
    gap: 8px;
    align-items: baseline;
  }
  .admin-task-col-ops {
    justify-content: flex-start;
  }
  .admin-task-people {
    white-space: normal;
  }
}

/* —— 大厅活动 / 优惠券模板 共用工作室布局 —— */
.admin-hall-studio,
.admin-coupon-studio {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.admin-hall-list-panel,
.admin-hall-compose,
.admin-coupon-list-panel,
.admin-coupon-compose {
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 16px;
  padding: 16px 18px 18px;
}
.admin-hall-list-head,
.admin-hall-compose-head,
.admin-coupon-studio-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}
.admin-hall-list-head h3,
.admin-hall-compose-head h3,
.admin-coupon-studio-head h3 {
  margin: 0 0 4px;
  font-size: 16px;
}
.admin-hall-list-head .meta,
.admin-hall-compose-head .meta,
.admin-coupon-studio-head .meta {
  margin: 0;
}
.admin-hall-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}
.admin-hall-stat {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #f5f2ed;
  color: #6b645c;
  font-size: 12px;
}
.admin-hall-stat strong {
  font-size: 14px;
  color: #2f2a24;
}
.admin-hall-stat.is-on {
  background: rgba(34, 140, 78, 0.1);
  color: #1f7a45;
}
.admin-hall-stat.is-on strong {
  color: #176338;
}
.admin-hall-stat.is-off {
  background: rgba(0, 0, 0, 0.04);
}
.admin-hall-empty {
  padding: 28px 12px;
}
.admin-hall-activity-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 10px;
  max-height: min(420px, 48vh);
  overflow: auto;
}
.admin-hall-activity-row {
  display: grid;
  grid-template-columns: 148px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 12px;
  border: 1px solid rgba(0, 0, 0, 0.07);
  border-radius: 14px;
  background: #faf9f7;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.admin-hall-activity-row.is-active {
  background: #fff;
  border-color: rgba(92, 61, 46, 0.22);
}
.admin-hall-activity-row.is-hidden {
  opacity: 0.78;
}
.admin-hall-activity-thumb {
  width: 148px;
  height: 72px;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
  border: 1px solid rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}
.admin-hall-activity-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.admin-hall-activity-thumb-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  font-weight: 650;
  color: #3d3a33;
  padding: 0 8px;
  text-align: center;
}
.admin-hall-activity-thumb.theme-warm {
  background: linear-gradient(120deg, #fff1e0, #ffc49a);
}
.admin-hall-activity-thumb.theme-cool {
  background: linear-gradient(120deg, #e8f4ff, #b8d9ff);
}
.admin-hall-activity-thumb.theme-green {
  background: linear-gradient(120deg, #e8f8ef, #b8efcf);
}
.admin-hall-activity-thumb.theme-blue {
  background: linear-gradient(120deg, #eef0ff, #c5c9ff);
}
.admin-hall-activity-main {
  min-width: 0;
}
.admin-hall-activity-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.admin-hall-activity-title-row h4 {
  margin: 0;
  font-size: 15px;
  line-height: 1.35;
  color: #2a2621;
}
.admin-hall-status-pill {
  flex-shrink: 0;
  padding: 3px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 650;
}
.admin-hall-status-pill.on {
  background: rgba(34, 140, 78, 0.12);
  color: #176338;
}
.admin-hall-status-pill.off {
  background: #eceae6;
  color: #6b645c;
}
.admin-hall-activity-meta {
  margin: 6px 0 0;
}
.admin-hall-asset-flags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.admin-hall-asset-flags span {
  font-size: 12px;
  color: #8a8278;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.035);
}
.admin-hall-asset-flags span.ok {
  color: #1f7a45;
  background: rgba(34, 140, 78, 0.08);
}
.admin-hall-activity-ops {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: stretch;
  min-width: 118px;
}
.admin-hall-activity-ops .primary,
.admin-hall-activity-ops .ghost-local {
  width: 100%;
  justify-content: center;
}
.admin-hall-activity-ops .danger-text {
  color: #b42318;
}
.admin-hall-activity-ops .danger-text:hover {
  border-color: rgba(180, 35, 24, 0.35);
  background: rgba(180, 35, 24, 0.06);
}
.admin-hall-publish-btn {
  flex-shrink: 0;
  white-space: nowrap;
}
.admin-hall-publish-btn--mobile {
  display: none;
  width: 100%;
  margin-top: 4px;
}
.admin-hall-compose-body {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(260px, 0.9fr);
  gap: 18px;
  align-items: start;
}
.admin-hall-compose-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 14px;
}
.admin-hall-compose-fields .form-field-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}
.admin-hall-compose-fields .admin-hall-span-2,
.admin-coupon-compose-fields .admin-hall-span-2 {
  grid-column: 1 / -1;
}
.admin-coupon-compose-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 14px;
}
.admin-coupon-compose-fields .form-field-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}
.admin-coupon-compose .admin-hall-publish-btn--mobile {
  margin-top: 8px;
}
.admin-coupon-template-rows {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 10px;
  max-height: min(480px, 52vh);
  overflow: auto;
}
.admin-coupon-template-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 12px;
  border: 1px solid rgba(0, 0, 0, 0.07);
  border-radius: 14px;
  background: #faf9f7;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.admin-coupon-template-row.is-active {
  background: #fff;
  border-color: rgba(92, 61, 46, 0.22);
}
.admin-coupon-template-row.is-off {
  opacity: 0.8;
}
.admin-coupon-template-swatch {
  width: 120px;
  height: 88px;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  text-align: center;
  padding: 8px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-sizing: border-box;
}
.admin-coupon-template-swatch strong {
  font-size: 14px;
  color: #2f2a24;
}
.admin-coupon-template-swatch span {
  font-size: 12px;
  color: #5c5246;
  font-weight: 600;
}
.admin-coupon-template-swatch.theme-warm {
  background: linear-gradient(120deg, #fff1e0, #ffc49a);
}
.admin-coupon-template-swatch.theme-cool {
  background: linear-gradient(120deg, #e8f4ff, #b8d9ff);
}
.admin-coupon-template-swatch.theme-green {
  background: linear-gradient(120deg, #e8f8ef, #b8efcf);
}
.admin-coupon-template-swatch.theme-blue {
  background: linear-gradient(120deg, #eef0ff, #c5c9ff);
}
.admin-coupon-template-main {
  min-width: 0;
}
.admin-coupon-template-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.admin-coupon-template-title-row h4 {
  margin: 0;
  font-size: 15px;
  line-height: 1.35;
  color: #2a2621;
}
.admin-coupon-desc {
  margin-top: 4px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.admin-coupon-template-ops {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: stretch;
  min-width: 118px;
}
.admin-coupon-template-ops .primary,
.admin-coupon-template-ops .ghost-local {
  width: 100%;
  justify-content: center;
}
.admin-coupon-template-ops .admin-coupon-theme-select {
  width: 100%;
}
.admin-coupon-template-ops .danger-text {
  color: #b42318;
}
.admin-coupon-template-ops .danger-text:hover {
  border-color: rgba(180, 35, 24, 0.35);
  background: rgba(180, 35, 24, 0.06);
}
.admin-hall-compose-preview {
  position: sticky;
  top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border-radius: 14px;
  background: linear-gradient(180deg, #f7f4ef 0%, #f3efe8 100%);
  border: 1px solid rgba(0, 0, 0, 0.05);
}
.admin-hall-preview-label {
  margin: 4px 0 0;
  font-size: 12px;
  font-weight: 650;
  color: #6b645c;
  letter-spacing: 0.02em;
}
.admin-hall-compose-preview .admin-hall-preview {
  margin-top: 0;
}
.admin-hall-compose-preview .admin-hall-preview--web {
  margin-top: 4px;
}
@media (max-width: 1100px) {
  .admin-hall-compose-body {
    grid-template-columns: 1fr;
  }
  .admin-hall-compose-preview {
    position: static;
  }
  .admin-hall-compose-head .admin-hall-publish-btn,
  .admin-coupon-studio-head .admin-hall-publish-btn {
    display: none;
  }
  .admin-hall-publish-btn--mobile {
    display: inline-flex;
  }
}
@media (max-width: 820px) {
  .admin-hall-activity-row,
  .admin-coupon-template-row {
    grid-template-columns: 1fr;
  }
  .admin-hall-activity-thumb {
    width: 100%;
    height: 96px;
  }
  .admin-coupon-template-swatch {
    width: 100%;
    height: 72px;
  }
  .admin-hall-activity-ops,
  .admin-coupon-template-ops {
    flex-direction: row;
    flex-wrap: wrap;
    min-width: 0;
  }
  .admin-hall-activity-ops .primary,
  .admin-hall-activity-ops .ghost-local,
  .admin-coupon-template-ops .primary,
  .admin-coupon-template-ops .ghost-local {
    width: auto;
    flex: 1;
  }
  .admin-coupon-template-ops .admin-coupon-theme-select {
    width: auto;
    flex: 1;
    min-width: 100px;
  }
  .admin-hall-list-head,
  .admin-hall-compose-head,
  .admin-coupon-studio-head {
    flex-direction: column;
  }
  .admin-hall-compose-fields,
  .admin-coupon-compose-fields {
    grid-template-columns: 1fr;
  }
}

.admin-hall-preview {
  position: relative;
  margin-top: 8px;
  height: 96px;
  border-radius: 14px;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  color: #fff;
  box-sizing: border-box;
}
.admin-hall-preview.theme-warm {
  background: linear-gradient(135deg, #ffb86c, #f26b2b);
}
.admin-hall-preview.theme-cool {
  background: linear-gradient(135deg, #7ec8ff, #2f7fe0);
}
.admin-hall-preview.theme-green {
  background: linear-gradient(135deg, #6ee7a8, #12a855);
}
.admin-hall-preview.theme-blue {
  background: linear-gradient(135deg, #9aa7ff, #4b57d6);
}
.admin-hall-preview--web {
  height: 88px;
  aspect-ratio: 4 / 1;
  width: 100%;
  max-width: 100%;
  height: auto;
  min-height: 72px;
}
.admin-hall-preview-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.85;
}
.admin-hall-preview-amount,
.admin-hall-preview-copy,
.admin-hall-preview-cta {
  position: relative;
  z-index: 1;
}
.admin-hall-preview-amount {
  flex-shrink: 0;
  min-width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  background: rgba(255, 255, 255, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.35);
}
.admin-hall-preview-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.admin-hall-preview-copy .kicker {
  font-size: 11px;
  opacity: 0.9;
}
.admin-hall-preview-copy strong {
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.admin-hall-preview-copy em {
  font-style: normal;
  font-size: 12px;
  opacity: 0.92;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.admin-hall-preview-cta {
  flex-shrink: 0;
  background: #fff;
  color: #e67e22;
  font-size: 12px;
  font-weight: 700;
  padding: 8px 12px;
  border-radius: 999px;
}
</style>
