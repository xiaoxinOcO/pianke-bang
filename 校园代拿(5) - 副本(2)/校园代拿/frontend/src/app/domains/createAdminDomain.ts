import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { apiOrigin } from '../../constants/api'
import { findMiniprogramPage } from '../../constants/miniprogramPages'
import type {
  AdminAiAutoAuditResponse,
  AdminAiConfig,
  AdminAiTestResponse,
  AdminLogPageResponse,
  AdminOperationLog,
  AdminUserItem,
  AdminWalletWithdrawItem,
  ChatMessageItem,
  NoticeItem,
  TaskItem,
  TaskPageResponse,
  UserFeedbackItem,
  UserFeedbackPageResponse,
  CsRatingItem,
  CsRatingPageResponse
} from '../../types/app'
import { isAwaitingBountyPayment, isPendingAdminReview, statusLabelMap } from '../../utils/taskStatus'

type AdminRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type AdminDomainDeps = {
  request: AdminRequest
  errorMsg: Ref<string>
  isLoggedIn: Ref<boolean>
  isAdmin: Ref<boolean>
  myName: Ref<string>
  currentView: Ref<string>
  actionLoadingMap: Ref<Record<number, boolean>>
  tasks: Ref<TaskItem[]>
  safeAllTasks: ComputedRef<TaskItem[]>
  chatContacts: Ref<any[]>
  temporaryChatContacts: Ref<any[]>
  fetchChatContacts: () => Promise<void>
  fetchTasks: () => Promise<void>
  compressImageForChat: (file: File) => Promise<string>
  shellSideEffects: { loadAdminDashboardData?: () => void | Promise<void>; goProfile?: () => void }
  showProfileNotice: (message: string, tone?: 'success' | 'error') => void
  adminModule: Ref<
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
  >
  auditSubModule: Ref<'task' | 'avatar' | 'rider'>
}

export function createAdminDomain(deps: AdminDomainDeps) {
  void apiOrigin
  const adminLogs = ref<AdminOperationLog[]>([])

  const adminLogsPaging = ref({ page: 1, size: 20, totalPages: 0, totalElements: 0 })

  const adminFeedbacks = ref<UserFeedbackItem[]>([])

  const adminFeedbacksPaging = ref({ page: 1, size: 20, totalPages: 0, totalElements: 0 })

  const adminFeedbacksUnreadCount = ref(0)

  const adminFeedbacksLoading = ref(false)

  const adminCsRatings = ref<CsRatingItem[]>([])

  const adminCsRatingsPaging = ref({ page: 1, size: 20, totalPages: 0, totalElements: 0 })

  const adminCsRatingsLoading = ref(false)

  const adminCsRatingsAverage = ref(0)

  const adminCsRatingsTotal = ref(0)

  const adminUsers = ref<AdminUserItem[]>([])

  const adminCustomerServiceStaff = ref<AdminUserItem[]>([])

  const adminCsForm = ref({ username: '', password: '' })

  const adminCsCreating = ref(false)

  const adminCsLastCreated = ref<{ id: number; username: string } | null>(null)

  const adminPendingWithdraws = ref<AdminWalletWithdrawItem[]>([])

  const adminNotices = ref<NoticeItem[]>([])

  const adminNoticeLoading = ref(false)

  const adminNoticeEditorId = ref<number | null>(null)

  const adminNoticeEditor = ref({ title: '', content: '', published: false, pinned: false })

  const adminNoticeEditorDirty = ref(false)

  const adminWithdrawKeyword = ref('')

  const adminWithdrawRemarkMap = ref<Record<number, string>>({})
  /** 下拉框绑定：空字符串表示未选 */

  /** 下拉框绑定：空字符串表示未选 */
  const adminWalletCreditUserId = ref('')

  const adminWalletCreditAmount = ref(0)

  const adminWalletCreditRemark = ref('')

  const adminWalletCreditSubmitting = ref(false)

  const adminCouponTemplates = ref<Array<Record<string, unknown>>>([])

  const adminCouponTemplatesLoading = ref(false)

  const adminCouponTab = ref<'activity' | 'templates' | 'issue' | 'codes'>('activity')

  const adminHallActivityForm = ref({
    title: '',
    subtitle: '',
    ctaText: '',
    actionType: 'RIDER_APPLY',
    actionValue: '/pages/rider-apply/rider-apply',
    couponTemplateId: '',
    couponAmount: 5,
    couponValidDays: 7,
    bannerTheme: 'warm',
    bannerImageUrl: '',
    bannerImageUrlWeb: '',
    sortOrder: 0
  })

  const adminHallActivitySubmitting = ref(false)

  const adminHallActivities = ref<Array<Record<string, unknown>>>([])

  const adminHallActivitiesLoading = ref(false)

  const adminCouponForm = ref({
    name: '',
    type: 'FIXED',
    discountAmount: 5,
    minSpend: 0,
    discountPercent: 0.9,
    maxDiscount: 0,
    perUserLimit: 1,
    totalStock: 0,
    claimMode: 'ADMIN_ONLY',
    sceneCode: '',
    description: '',
    bannerTheme: 'warm',
    /** 0=不限期限；其余为天数（与大厅活动建券一致） */
    validDays: 7
  })

  const adminCouponCreateSubmitting = ref(false)

  const adminCouponIssue = ref({
    templateId: '',
    username: '',
    count: 1,
    reason: '活动发放'
  })

  const adminCouponIssueSubmitting = ref(false)

  const adminCouponRedeem = ref({
    templateId: '',
    count: 5,
    maxUses: 1,
    code: '',
    remark: ''
  })

  const adminCouponRedeemSubmitting = ref(false)

  const adminCouponGeneratedCodes = ref<string[]>([])

  const adminCouponRedeemCodes = ref<Array<Record<string, unknown>>>([])

  const adminCouponRedeemCodesLoading = ref(false)

  const adminCouponRedeemFilterTemplateId = ref('')

  const adminAiConfig = ref<AdminAiConfig>({
    enabled: false,
    baseUrl: 'https://api.deepseek.com',
    apiKey: '',
    model: 'deepseek-v4-pro',
    supportModel: 'deepseek-v4-pro',
    timeoutSeconds: 120,
    models: []
  })

  const adminAiTestPrompt = ref('请用一句话回复：API 连接成功。')

  const adminAiTesting = ref(false)

  const aiAutoAuditSubmitting = ref(false)

  const aiAutoAuditEnabled = ref(false)


  const adminAppealLoading = ref(false)

  const adminAppealKeyword = ref('')

  const adminAppeals = ref<
    Array<{
      key: string
      messageId: number
      fromUsername: string
      createdAt: string
      taskId?: number
      title?: string
      taskStatus?: string
      content: string
      processed: boolean
      decision?: 'APPROVED' | 'REJECTED'
    }>
  >([])

  const adminAppealReply = ref<{ toUsername: string; taskId?: number; title?: string } | null>(null)

  const adminAppealReplyText = ref('')

  const adminAppealReplySubmitting = ref(false)

  const appealDecidingKey = ref<string | null>(null)

  const adminNotice = ref('')

  const adminNoticeTone = ref<'success' | 'error'>('success')

  let adminNoticeTimer: number | null = null

  async function fetchAdminLogs(page?: number) {
    if (!deps.isAdmin.value) {
      adminLogs.value = []
      return
    }

    const targetPage =
      typeof page === 'number' && Number.isFinite(page) && page >= 1
        ? Math.floor(page)
        : adminLogsPaging.value.page
    try {
      const data = await deps.request<AdminLogPageResponse>(
        `${apiOrigin}/api/admin/logs?page=${targetPage}&size=${adminLogsPaging.value.size}`
      )
      adminLogs.value = data.records
      adminLogsPaging.value = {
        page: data.page,
        size: data.size,
        totalPages: data.totalPages,
        totalElements: data.totalElements
      }
    } catch (err) {
      if (deps.currentView.value === 'chat') {
        deps.errorMsg.value = (err as Error).message
      }
    }
  }

  function goAdminLogsPrev() {
    if (adminLogsPaging.value.page <= 1) return
    void fetchAdminLogs(adminLogsPaging.value.page - 1)
  }

  function goAdminLogsNext() {
    const total = Math.max(1, adminLogsPaging.value.totalPages)
    if (adminLogsPaging.value.page >= total) return
    void fetchAdminLogs(adminLogsPaging.value.page + 1)
  }

  type AdminFeedbackTypeFilter = 'all' | 'suggest' | 'bug'
  type AdminFeedbackReadFilter = 'all' | 'unread' | 'read'

  async function fetchAdminFeedbacks(
    page?: number,
    typeFilter: AdminFeedbackTypeFilter = 'all',
    readFilter: AdminFeedbackReadFilter = 'all'
  ) {
    if (!deps.isAdmin.value) {
      adminFeedbacks.value = []
      adminFeedbacksUnreadCount.value = 0
      return
    }

    const targetPage =
      typeof page === 'number' && Number.isFinite(page) && page >= 1
        ? Math.floor(page)
        : adminFeedbacksPaging.value.page

    const params = new URLSearchParams()
    params.set('page', String(targetPage))
    params.set('size', String(adminFeedbacksPaging.value.size))
    if (typeFilter !== 'all') params.set('type', typeFilter)
    if (readFilter === 'unread') params.set('read', 'false')
    if (readFilter === 'read') params.set('read', 'true')

    adminFeedbacksLoading.value = true
    try {
      const data = await deps.request<UserFeedbackPageResponse>(
        `${apiOrigin}/api/admin/feedback?${params.toString()}`
      )
      adminFeedbacks.value = data.records
      adminFeedbacksPaging.value = {
        page: data.page,
        size: data.size,
        totalPages: data.totalPages,
        totalElements: data.totalElements
      }
      adminFeedbacksUnreadCount.value = data.unreadCount
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      adminFeedbacksLoading.value = false
    }
  }

  function goAdminFeedbacksPrev(
    typeFilter: AdminFeedbackTypeFilter = 'all',
    readFilter: AdminFeedbackReadFilter = 'all'
  ) {
    if (adminFeedbacksPaging.value.page <= 1) return
    void fetchAdminFeedbacks(adminFeedbacksPaging.value.page - 1, typeFilter, readFilter)
  }

  function goAdminFeedbacksNext(
    typeFilter: AdminFeedbackTypeFilter = 'all',
    readFilter: AdminFeedbackReadFilter = 'all'
  ) {
    const total = Math.max(1, adminFeedbacksPaging.value.totalPages)
    if (adminFeedbacksPaging.value.page >= total) return
    void fetchAdminFeedbacks(adminFeedbacksPaging.value.page + 1, typeFilter, readFilter)
  }

  async function fetchAdminCsRatings(
    page?: number,
    agentFilter = '',
    scoreFilter: 'all' | '5' | '4' | '3' | 'low' = 'all'
  ) {
    if (!deps.isAdmin.value) {
      adminCsRatings.value = []
      adminCsRatingsAverage.value = 0
      adminCsRatingsTotal.value = 0
      return
    }
    const targetPage =
      typeof page === 'number' && Number.isFinite(page) && page >= 1
        ? Math.floor(page)
        : adminCsRatingsPaging.value.page
    const params = new URLSearchParams()
    params.set('page', String(targetPage))
    params.set('size', String(adminCsRatingsPaging.value.size))
    const agent = String(agentFilter || '').trim()
    if (agent) params.set('agent', agent)
    if (scoreFilter === '5') {
      params.set('scoreMin', '5')
      params.set('scoreMax', '5')
    } else if (scoreFilter === '4') {
      params.set('scoreMin', '4')
      params.set('scoreMax', '4')
    } else if (scoreFilter === '3') {
      params.set('scoreMin', '3')
      params.set('scoreMax', '3')
    } else if (scoreFilter === 'low') {
      params.set('scoreMin', '1')
      params.set('scoreMax', '2')
    }
    adminCsRatingsLoading.value = true
    try {
      const data = await deps.request<CsRatingPageResponse>(
        `${apiOrigin}/api/admin/cs-ratings?${params.toString()}`
      )
      adminCsRatings.value = data.records || []
      adminCsRatingsPaging.value = {
        page: data.page,
        size: data.size,
        totalPages: data.totalPages,
        totalElements: data.totalElements
      }
      adminCsRatingsAverage.value = Number(data.averageScore || 0)
      adminCsRatingsTotal.value = Number(data.totalCount || 0)
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      adminCsRatingsLoading.value = false
    }
  }

  function goAdminCsRatingsPrev(agentFilter = '', scoreFilter: 'all' | '5' | '4' | '3' | 'low' = 'all') {
    if (adminCsRatingsPaging.value.page <= 1) return
    void fetchAdminCsRatings(adminCsRatingsPaging.value.page - 1, agentFilter, scoreFilter)
  }

  function goAdminCsRatingsNext(agentFilter = '', scoreFilter: 'all' | '5' | '4' | '3' | 'low' = 'all') {
    const total = Math.max(1, adminCsRatingsPaging.value.totalPages)
    if (adminCsRatingsPaging.value.page >= total) return
    void fetchAdminCsRatings(adminCsRatingsPaging.value.page + 1, agentFilter, scoreFilter)
  }

  async function setAdminFeedbackRead(
    id: number,
    read: boolean,
    typeFilter: AdminFeedbackTypeFilter = 'all',
    readFilter: AdminFeedbackReadFilter = 'all'
  ) {
    if (!deps.isAdmin.value) return
    try {
      await deps.request(`${apiOrigin}/api/admin/feedback/${id}/read?value=${read ? 'true' : 'false'}`, {
        method: 'POST'
      })
      await fetchAdminFeedbacks(adminFeedbacksPaging.value.page, typeFilter, readFilter)
      showAdminNotice(read ? '已标记为已读' : '已标记为未读')
    } catch (err) {
      showAdminNotice((err as Error).message)
    }
  }

  async function fetchAdminUsers() {
    if (!deps.isAdmin.value) {
      adminUsers.value = []
      return
    }

    try {
      adminUsers.value = await deps.request<AdminUserItem[]>(`${apiOrigin}/api/admin/users`)
    } catch (err) {
      const msg = (err as Error).message
      deps.errorMsg.value = msg.includes('400') ? '申请提交失败：请检查姓名/学号/手机号/理由格式' : msg
    }
  }

  async function fetchAdminCustomerServiceStaff() {
    if (!deps.isAdmin.value) {
      adminCustomerServiceStaff.value = []
      return
    }
    try {
      adminCustomerServiceStaff.value = await deps.request<AdminUserItem[]>(`${apiOrigin}/api/admin/customer-service`)
    } catch (err) {
      showAdminNotice((err as Error).message)
    }
  }

  async function createAdminCustomerServiceStaff() {
    if (!deps.isAdmin.value || adminCsCreating.value) return
    const username = adminCsForm.value.username.trim()
    const password = adminCsForm.value.password.trim()
    if (!username || !password) {
      showAdminNotice('请填写客服账号和密码')
      return
    }
    if (password.length < 6 || password.length > 32) {
      showAdminNotice('密码长度需在 6-32 位')
      return
    }
    adminCsCreating.value = true
    try {
      const created = await deps.request<AdminUserItem>(`${apiOrigin}/api/admin/customer-service`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      })
      adminCsLastCreated.value = {
        id: created.id,
        username: created.username || username
      }
      adminCsForm.value = { username: '', password: '' }
      await fetchAdminCustomerServiceStaff()
      await fetchAdminUsers()
      showAdminNotice(
        `创建成功！登录账号：${adminCsLastCreated.value.username}，用户 ID：${adminCsLastCreated.value.id}。请用该账号在 5174 工作台登录（密码为您刚设置的密码）。`
      )
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      adminCsCreating.value = false
    }
  }

  async function updateUserCreditScore(userId: number, creditScore: number) {
    if (!deps.isAdmin.value) return
    const score = Math.max(0, Math.min(100, Math.floor(Number(creditScore))))
    deps.actionLoadingMap.value[userId] = true
    try {
      await deps.request(`${apiOrigin}/api/admin/users/${userId}/credit-score`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ creditScore: score })
      })
      await fetchAdminUsers()
      await fetchAdminLogs(1)
      showAdminNotice(`已将用户信誉分调整为 ${score}`)
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      deps.actionLoadingMap.value[userId] = false
    }
  }

  async function setUserAccountRole(userId: number, role: string) {
    if (!deps.isAdmin.value) return
    const next = String(role || '').trim().toUpperCase()
    if (!['USER', 'ADMIN', 'CUSTOMER_SERVICE'].includes(next)) {
      showAdminNotice('角色无效')
      return
    }
    deps.actionLoadingMap.value[userId] = true
    try {
      await deps.request<AdminUserItem>(`${apiOrigin}/api/admin/users/${userId}/account-role`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ role: next })
      })
      await fetchAdminUsers()
      await fetchAdminCustomerServiceStaff()
      await fetchAdminLogs(1)
      const label = next === 'ADMIN' ? '管理员' : next === 'CUSTOMER_SERVICE' ? '人工客服' : '普通用户'
      showAdminNotice(`已将账号角色调整为：${label}`)
    } catch (err) {
      showAdminNotice((err as Error).message)
      await fetchAdminUsers()
    } finally {
      deps.actionLoadingMap.value[userId] = false
    }
  }

  async function setUserEnabled(userId: number, enabled: boolean) {
    if (!deps.isAdmin.value) return

    deps.actionLoadingMap.value[userId] = true
    deps.errorMsg.value = ''
    try {
      await deps.request<AdminUserItem>(`${apiOrigin}/api/admin/users/${userId}/${enabled ? 'enable' : 'disable'}`, {
        method: 'POST'
      })
      await fetchAdminUsers()
      await fetchAdminLogs(1)
      if (deps.adminModule.value === 'customerService') {
        await fetchAdminCustomerServiceStaff()
      }
      showAdminNotice(enabled ? '已启用该用户' : '已禁用该用户')
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      deps.actionLoadingMap.value[userId] = false
    }
  }

  async function auditRiderApplication(userId: number, approve: boolean) {
    if (!deps.isAdmin.value) return

    deps.actionLoadingMap.value[userId] = true
    deps.errorMsg.value = ''
    try {
      await deps.request<AdminUserItem>(`${apiOrigin}/api/admin/users/${userId}/rider/${approve ? 'approve' : 'reject'}`, {
        method: 'POST'
      })
      await fetchAdminUsers()
      await fetchAdminLogs(1)
      showAdminNotice(approve ? '已通过接单员申请' : '已驳回接单员申请')
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      deps.actionLoadingMap.value[userId] = false
    }
  }

  async function revokeRiderCertification(userId: number) {
    if (!deps.isAdmin.value) return
    if (
      !window.confirm('确定取消该用户的接单员认证？取消后将变为普通用户，历史申请资料会清空，需要重新提交申请才能再次接单。')
    ) {
      return
    }

    deps.actionLoadingMap.value[userId] = true
    deps.errorMsg.value = ''
    try {
      await deps.request<AdminUserItem>(`${apiOrigin}/api/admin/users/${userId}/rider/revoke`, {
        method: 'POST'
      })
      await fetchAdminUsers()
      await fetchAdminLogs(1)
      showAdminNotice('已撤销该用户的接单员认证')
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      deps.actionLoadingMap.value[userId] = false
    }
  }

  async function auditUserAvatar(userId: number, approve: boolean) {
    if (!deps.isAdmin.value) return

    deps.actionLoadingMap.value[userId] = true
    deps.errorMsg.value = ''
    try {
      await deps.request<AdminUserItem>(`${apiOrigin}/api/admin/users/${userId}/avatar/${approve ? 'approve' : 'reject'}`, {
        method: 'POST'
      })
      await fetchAdminUsers()
      await fetchAdminLogs(1)
      showAdminNotice(approve ? '头像已审核通过' : '头像已驳回')
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      deps.actionLoadingMap.value[userId] = false
    }
  }

  function showAdminNotice(text: string, tone: 'success' | 'error' = 'success') {
    adminNotice.value = text
    adminNoticeTone.value = tone
    if (adminNoticeTimer) {
      window.clearTimeout(adminNoticeTimer)
    }
    adminNoticeTimer = window.setTimeout(() => {
      adminNotice.value = ''
      adminNoticeTone.value = 'success'
      adminNoticeTimer = null
    }, tone === 'error' ? 8000 : 3000)
  }

  const filteredAdminPendingWithdraws = computed(() => {
    const kw = adminWithdrawKeyword.value.trim().toLowerCase()
    if (!kw) return adminPendingWithdraws.value
    return adminPendingWithdraws.value.filter((w) =>
      String(w.username || '').toLowerCase().includes(kw) ||
      String(w.accountName || '').toLowerCase().includes(kw) ||
      String(w.accountNo || '').toLowerCase().includes(kw)
    )
  })

  const adminSelectedNotice = computed(() => {
    if (adminNoticeEditorId.value == null) return null
    return adminNotices.value.find((n) => n.id === adminNoticeEditorId.value) || null
  })

  function resetAdminNoticeEditor(from?: NoticeItem | null) {
    const n = from || null
    adminNoticeEditorId.value = n ? n.id : null
    adminNoticeEditor.value = {
      title: n ? String(n.title || '') : '',
      content: n ? String(n.content || '') : '',
      published: n ? !!n.published : false,
      pinned: n ? !!n.pinned : false
    }
    adminNoticeEditorDirty.value = false
  }

  function selectAdminNotice(n: NoticeItem) {
    resetAdminNoticeEditor(n)
  }

  function newAdminNotice() {
    resetAdminNoticeEditor(null)
  }

  async function fetchAdminNotices() {
    if (!deps.isAdmin.value) {
      adminNotices.value = []
      return
    }
    adminNoticeLoading.value = true
    try {
      adminNotices.value = await deps.request<NoticeItem[]>(`${apiOrigin}/api/admin/notices`)
      if (adminNoticeEditorId.value != null) {
        const latest = adminNotices.value.find((n) => n.id === adminNoticeEditorId.value) || null
        if (latest && !adminNoticeEditorDirty.value) {
          resetAdminNoticeEditor(latest)
        }
      }
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      adminNoticeLoading.value = false
    }
  }

  async function saveAdminNotice() {
    if (!deps.isAdmin.value) return
    const payload = {
      title: String(adminNoticeEditor.value.title || '').trim(),
      content: String(adminNoticeEditor.value.content || '').trim(),
      published: !!adminNoticeEditor.value.published,
      pinned: !!adminNoticeEditor.value.pinned
    }
    if (!payload.title) {
      showAdminNotice('标题不能为空')
      return
    }
    if (!payload.content) {
      showAdminNotice('内容不能为空')
      return
    }
    const editingId = adminNoticeEditorId.value
    const loadingKey = editingId ?? -9999
    deps.actionLoadingMap.value[loadingKey] = true
    try {
      if (editingId == null) {
        const created = await deps.request<NoticeItem>(`${apiOrigin}/api/admin/notices`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        })
        showAdminNotice('公告已创建')
        await fetchAdminNotices()
        resetAdminNoticeEditor(created)
      } else {
        const updated = await deps.request<NoticeItem>(`${apiOrigin}/api/admin/notices/${editingId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        })
        showAdminNotice('公告已保存')
        await fetchAdminNotices()
        resetAdminNoticeEditor(updated)
      }
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      deps.actionLoadingMap.value[loadingKey] = false
    }
  }

  async function toggleAdminNoticePublish(n: NoticeItem) {
    if (!deps.isAdmin.value) return
    deps.actionLoadingMap.value[n.id] = true
    try {
      await deps.request<NoticeItem>(`${apiOrigin}/api/admin/notices/${n.id}/publish?value=${!n.published}`, { method: 'POST' })
      await fetchAdminNotices()
      showAdminNotice(!n.published ? '公告已上架' : '公告已下架')
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      deps.actionLoadingMap.value[n.id] = false
    }
  }

  async function toggleAdminNoticePin(n: NoticeItem) {
    if (!deps.isAdmin.value) return
    deps.actionLoadingMap.value[n.id] = true
    try {
      await deps.request<NoticeItem>(`${apiOrigin}/api/admin/notices/${n.id}/pin?value=${!n.pinned}`, { method: 'POST' })
      await fetchAdminNotices()
      showAdminNotice(!n.pinned ? '已置顶' : '已取消置顶')
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      deps.actionLoadingMap.value[n.id] = false
    }
  }

  async function deleteAdminNotice(n: NoticeItem) {
    if (!deps.isAdmin.value) return
    deps.actionLoadingMap.value[n.id] = true
    try {
      await deps.request<{ message: string }>(`${apiOrigin}/api/admin/notices/${n.id}`, { method: 'DELETE' })
      showAdminNotice('公告已删除')
      if (adminNoticeEditorId.value === n.id) {
        resetAdminNoticeEditor(null)
      }
      await fetchAdminNotices()
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      deps.actionLoadingMap.value[n.id] = false
    }
  }

  async function loadAdminDashboardData() {
    if (!deps.isLoggedIn.value || !deps.isAdmin.value) return
    await Promise.all([
      deps.fetchTasks(),
      fetchAdminUsers(),
      fetchAdminLogs(1),
      fetchAdminFeedbacks(1),
      fetchAdminPendingWithdraws(),
      fetchAdminNotices(),
      fetchAdminAiConfig(),
      fetchAiAutoAuditEnabled()
    ])
  }
  deps.shellSideEffects.loadAdminDashboardData = loadAdminDashboardData

  async function fetchAdminPendingWithdraws() {
    if (!deps.isAdmin.value) {
      adminPendingWithdraws.value = []
      return
    }
    try {
      adminPendingWithdraws.value = await deps.request<AdminWalletWithdrawItem[]>(
        `${apiOrigin}/api/wallet/admin/withdraw/pending`
      )
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    }
  }

  async function auditWithdrawRequest(requestId: number, approve: boolean) {
    if (!deps.isAdmin.value) return
    deps.actionLoadingMap.value[requestId] = true
    try {
      const remark = (adminWithdrawRemarkMap.value[requestId] || '').trim()
      await deps.request(`${apiOrigin}/api/wallet/admin/withdraw/${requestId}/audit`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ approve, remark: remark || (approve ? '审核通过' : '审核驳回') })
      })
      delete adminWithdrawRemarkMap.value[requestId]
      await fetchAdminPendingWithdraws()
      showAdminNotice(approve ? '提现审核通过' : '提现已驳回')
    } catch (err) {
      const msg = String((err as Error)?.message || '').trim() || '提现审核失败，请稍后重试'
      showAdminNotice(msg, 'error')
    } finally {
      deps.actionLoadingMap.value[requestId] = false
    }
  }

  async function submitAdminWalletCredit() {
    if (!deps.isAdmin.value) return
    const userId = Number(adminWalletCreditUserId.value)
    if (!adminWalletCreditUserId.value.trim() || !Number.isFinite(userId) || userId <= 0) {
      showAdminNotice('请选择要充值的用户')
      return
    }
    const amount = Number(adminWalletCreditAmount.value)
    if (!Number.isFinite(amount) || amount <= 0) {
      showAdminNotice('请输入大于 0 的充值金额')
      return
    }
    adminWalletCreditSubmitting.value = true
    try {
      const res = await deps.request<{ message?: string; balanceAfter?: number | string }>(
        `${apiOrigin}/api/admin/users/${userId}/wallet/credit`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            amount,
            remark: adminWalletCreditRemark.value.trim()
          })
        }
      )
      const bal = res.balanceAfter != null ? Number(res.balanceAfter) : NaN
      const extra = Number.isFinite(bal) ? ` 用户当前余额 ¥${bal.toFixed(2)}` : ''
      showAdminNotice((res.message || '充值成功') + extra)
      adminWalletCreditAmount.value = 0
      adminWalletCreditRemark.value = ''
    } catch (err) {
      let text = (err as Error).message
      if (/Not Found|404|请求失败\(404\)/i.test(text)) {
        text = '充值接口未找到：请确认 campus-gateway 已重启，且 campus-wallet-service 已启动并注册到 Nacos。'
      }
      showAdminNotice(text)
    } finally {
      adminWalletCreditSubmitting.value = false
    }
  }

  async function fetchAdminCouponTemplates() {
    if (!deps.isAdmin.value) return
    adminCouponTemplatesLoading.value = true
    try {
      adminCouponTemplates.value = await deps.request<Array<Record<string, unknown>>>(
        `${apiOrigin}/api/admin/coupons/templates`
      )
    } catch (err) {
      adminCouponTemplates.value = []
      showAdminNotice((err as Error).message || '加载优惠券模板失败')
    } finally {
      adminCouponTemplatesLoading.value = false
    }
  }

  function padCouponDt(n: number) {
    return String(n).padStart(2, '0')
  }

  /** 与大厅活动自动建券一致：从现在起算 N 天截止（当天 23:59:59）；0=不限 */


  /** 与大厅活动自动建券一致：从现在起算 N 天截止（当天 23:59:59）；0=不限 */
  function couponValidityRangeFromDays(daysRaw: number): { validFrom: string | null; validTo: string | null } {
    const days = Number(daysRaw)
    if (!Number.isFinite(days) || days <= 0) {
      return { validFrom: null, validTo: null }
    }
    const now = new Date()
    const fmt = (d: Date) =>
      `${d.getFullYear()}-${padCouponDt(d.getMonth() + 1)}-${padCouponDt(d.getDate())}T${padCouponDt(d.getHours())}:${padCouponDt(d.getMinutes())}:${padCouponDt(d.getSeconds())}`
    const to = new Date(now)
    to.setDate(to.getDate() + days)
    to.setHours(23, 59, 59, 0)
    return { validFrom: fmt(now), validTo: fmt(to) }
  }

  async function createAdminCouponTemplate() {
    if (!deps.isAdmin.value) return
    const name = adminCouponForm.value.name.trim()
    if (!name) {
      showAdminNotice('请填写优惠券名称')
      return
    }
    let discountPercent: number | null = null
    let discountAmount: number | null = null
    if (adminCouponForm.value.type === 'PERCENT') {
      const pct = Math.round(Number(adminCouponForm.value.discountPercent) * 10) / 10
      if (![0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9].includes(pct)) {
        showAdminNotice('请选择 0.1～0.9 的折扣（间隔 0.1）')
        adminCouponForm.value.discountPercent = 0.9
        return
      }
      discountPercent = pct
      adminCouponForm.value.discountPercent = pct
    } else {
      const amt = Number(adminCouponForm.value.discountAmount)
      if (!Number.isFinite(amt) || amt <= 0) {
        showAdminNotice('请填写有效的减免金额')
        return
      }
      discountAmount = amt
    }
    const { validFrom, validTo } = couponValidityRangeFromDays(Number(adminCouponForm.value.validDays) || 0)
    adminCouponCreateSubmitting.value = true
    try {
      const body: Record<string, unknown> = {
        name,
        type: adminCouponForm.value.type,
        minSpend: Number(adminCouponForm.value.minSpend) || 0,
        perUserLimit: Math.max(1, Math.floor(Number(adminCouponForm.value.perUserLimit) || 1)),
        totalStock: Math.max(0, Math.floor(Number(adminCouponForm.value.totalStock) || 0)),
        claimMode: adminCouponForm.value.claimMode,
        sceneCode: adminCouponForm.value.sceneCode.trim() || null,
        description: adminCouponForm.value.description.trim() || null,
        bannerTheme: adminCouponForm.value.bannerTheme || 'warm',
        validFrom,
        validTo
      }
      if (adminCouponForm.value.type === 'PERCENT') {
        body.discountPercent = discountPercent
        body.maxDiscount = Number(adminCouponForm.value.maxDiscount) || null
      } else {
        body.discountAmount = discountAmount
      }
      await deps.request(`${apiOrigin}/api/admin/coupons/templates`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      })
      showAdminNotice('优惠券模板已创建')
      adminCouponForm.value.name = ''
      adminCouponForm.value.description = ''
      adminCouponForm.value.sceneCode = ''
      adminCouponForm.value.validDays = 7
      await fetchAdminCouponTemplates()
    } catch (err) {
      showAdminNotice((err as Error).message || '创建失败')
    } finally {
      adminCouponCreateSubmitting.value = false
    }
  }

  async function fetchAdminHallActivities() {
    if (!deps.isAdmin.value) return
    adminHallActivitiesLoading.value = true
    try {
      const rows = await deps.request<Array<Record<string, unknown>>>(`${apiOrigin}/api/admin/hall-activities`)
      adminHallActivities.value = Array.isArray(rows) ? rows : []
    } catch (err) {
      adminHallActivities.value = []
      showAdminNotice((err as Error).message || '加载大厅活动失败')
    } finally {
      adminHallActivitiesLoading.value = false
    }
  }

  function defaultHallCta(actionType: string) {
    if (actionType === 'RIDER_APPLY') return '立即认证'
    if (actionType === 'PUBLISH') return '去发布'
    if (actionType === 'COUPON_CLAIM') return '去领取'
    if (actionType === 'COUPON_CENTER') return '去领券'
    if (actionType === 'WALLET') return '看钱包'
    return '去看看'
  }

  async function createAdminHallActivity() {
    if (!deps.isAdmin.value) return
    const title = adminHallActivityForm.value.title.trim()
    if (!title) {
      showAdminNotice('请填写活动标题（会显示在小程序轮播）')
      return
    }
    if (title.length > 12) {
      showAdminNotice('活动标题最多 12 字')
      return
    }
    const subtitle = adminHallActivityForm.value.subtitle.trim()
    if (subtitle.length > 8) {
      showAdminNotice('副文案最多 8 字')
      return
    }
    const ctaText = adminHallActivityForm.value.ctaText.trim()
    if (ctaText.length > 5) {
      showAdminNotice('按钮文案最多 5 字')
      return
    }
    const actionType = adminHallActivityForm.value.actionType
    adminHallActivitySubmitting.value = true
    try {
      const body: Record<string, unknown> = {
        title,
        subtitle: subtitle || null,
        ctaText: ctaText || defaultHallCta(actionType),
        actionType,
        bannerTheme: adminHallActivityForm.value.bannerTheme || 'warm',
        bannerImageUrl: adminHallActivityForm.value.bannerImageUrl.trim() || null,
        bannerImageUrlWeb: adminHallActivityForm.value.bannerImageUrlWeb.trim() || null,
        sortOrder: Number(adminHallActivityForm.value.sortOrder) || 0,
        status: 'ACTIVE'
      }
      if (actionType === 'CUSTOM_PATH') {
        body.actionValue = adminHallActivityForm.value.actionValue.trim()
      }
      if (actionType === 'COUPON_CLAIM') {
        const tid = Number(adminHallActivityForm.value.couponTemplateId)
        if (Number.isFinite(tid) && tid > 0) {
          body.couponTemplateId = tid
        } else {
          body.couponAmount = Number(adminHallActivityForm.value.couponAmount) || 5
          const days = Number(adminHallActivityForm.value.couponValidDays)
          body.couponValidDays = Number.isFinite(days) ? days : 7
        }
      }
      await deps.request(`${apiOrigin}/api/admin/hall-activities`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      })
      showAdminNotice('活动已上线，小程序与网页大厅轮播中可见')
      adminHallActivityForm.value.title = ''
      adminHallActivityForm.value.subtitle = ''
      adminHallActivityForm.value.ctaText = ''
      adminHallActivityForm.value.actionValue = ''
      adminHallActivityForm.value.bannerImageUrl = ''
      adminHallActivityForm.value.bannerImageUrlWeb = ''
      adminCouponTab.value = 'activity'
      await fetchAdminHallActivities()
    } catch (err) {
      showAdminNotice((err as Error).message || '创建活动失败')
    } finally {
      adminHallActivitySubmitting.value = false
    }
  }

  async function setAdminHallActivityStatus(id: number | string, status: 'ACTIVE' | 'OFF') {
    if (!deps.isAdmin.value) return
    const activityId = Number(id)
    if (!Number.isFinite(activityId) || activityId <= 0) return
    try {
      await deps.request(`${apiOrigin}/api/admin/hall-activities/${activityId}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status })
      })
      showAdminNotice(status === 'ACTIVE' ? '已显示到轮播' : '已从轮播隐藏')
      await fetchAdminHallActivities()
    } catch (err) {
      showAdminNotice((err as Error).message || '更新失败')
    }
  }

  async function deleteAdminHallActivity(id: number | string) {
    if (!deps.isAdmin.value) return
    const activityId = Number(id)
    if (!Number.isFinite(activityId) || activityId <= 0) return
    if (!window.confirm('确定删除该大厅活动？')) return
    try {
      await deps.request(`${apiOrigin}/api/admin/hall-activities/${activityId}`, { method: 'DELETE' })
      showAdminNotice('活动已删除')
      await fetchAdminHallActivities()
    } catch (err) {
      showAdminNotice((err as Error).message || '删除失败')
    }
  }

  async function onAdminHallActivityBannerSelected(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return
    try {
      adminHallActivityForm.value.bannerImageUrl = await deps.compressImageForChat(file)
    } catch (err) {
      showAdminNotice((err as Error).message || '图片处理失败')
    } finally {
      input.value = ''
    }
  }

  async function onAdminHallActivityWebBannerSelected(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return
    try {
      adminHallActivityForm.value.bannerImageUrlWeb = await deps.compressImageForChat(file)
    } catch (err) {
      showAdminNotice((err as Error).message || '网页海报处理失败')
    } finally {
      input.value = ''
    }
  }

  function openAdminHallActivityModule() {
    deps.adminModule.value = 'coupons'
    adminCouponTab.value = 'activity'
    void fetchAdminHallActivities()
    void fetchAdminCouponTemplates()
    void fetchAdminUsers()
  }

  function hallActionTypeLabel(type: unknown) {
    const t = String(type || '')
    if (t === 'RIDER_APPLY') return '成为接单员'
    if (t === 'PUBLISH') return '去发布任务'
    if (t === 'COUPON_CLAIM') return '领取优惠券'
    if (t === 'COUPON_CENTER') return '领券中心'
    if (t === 'WALLET') return '我的钱包'
    if (t === 'CUSTOM_PATH') return '自定义页面'
    return t || '-'
  }

  const selectedHallCustomPage = computed(() =>
    findMiniprogramPage(adminHallActivityForm.value.actionValue)
  )

  function onHallActionTypeChange() {
    const type = adminHallActivityForm.value.actionType
    if (type === 'CUSTOM_PATH' && !adminHallActivityForm.value.actionValue.trim()) {
      adminHallActivityForm.value.actionValue = '/pages/rider-apply/rider-apply'
    }
    if (!adminHallActivityForm.value.ctaText.trim()) {
      // 保持空，占位符会用 defaultHallCta；自定义页用所选页面名
      const page = selectedHallCustomPage.value
      if (type === 'CUSTOM_PATH' && page) {
        adminHallActivityForm.value.ctaText = ''
      }
    }
  }

  async function fetchAdminCouponRedeemCodes() {
    if (!deps.isAdmin.value) return
    adminCouponRedeemCodesLoading.value = true
    try {
      const tid = adminCouponRedeemFilterTemplateId.value.trim()
      const qs = tid ? `?templateId=${encodeURIComponent(tid)}` : ''
      const rows = await deps.request<Array<Record<string, unknown>>>(
        `${apiOrigin}/api/admin/coupons/redeem-codes${qs}`
      )
      adminCouponRedeemCodes.value = Array.isArray(rows) ? rows : []
    } catch (err) {
      adminCouponRedeemCodes.value = []
      showAdminNotice((err as Error).message || '加载兑换码失败')
    } finally {
      adminCouponRedeemCodesLoading.value = false
    }
  }

  function adminCouponTemplateName(id: unknown) {
    const n = Number(id)
    const hit = adminCouponTemplates.value.find((t) => Number(t.id) === n)
    return hit ? String(hit.name || `#${n}`) : `#${n}`
  }

  async function updateAdminCouponTemplateTheme(templateId: number | string, bannerTheme: string) {
    if (!deps.isAdmin.value) return
    const id = Number(templateId)
    if (!Number.isFinite(id) || id <= 0) return
    try {
      await deps.request(`${apiOrigin}/api/admin/coupons/templates/${id}/banner`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ bannerTheme })
      })
      showAdminNotice('主题色已更新')
      await fetchAdminCouponTemplates()
    } catch (err) {
      showAdminNotice((err as Error).message || '更新主题色失败')
    }
  }

  function onAdminCouponThemeChange(templateId: number | string, event: Event) {
    const el = event.target as HTMLSelectElement | null
    if (!el) return
    void updateAdminCouponTemplateTheme(templateId, el.value)
  }

  function copyTextAdmin(text: string) {
    if (!text) return
    void navigator.clipboard.writeText(text).then(
      () => showAdminNotice('已复制'),
      () => showAdminNotice('复制失败，请手动选择')
    )
  }

  async function issueAdminCoupon() {
    if (!deps.isAdmin.value) return
    const templateId = Number(adminCouponIssue.value.templateId)
    const username = adminCouponIssue.value.username.trim()
    if (!Number.isFinite(templateId) || templateId <= 0) {
      showAdminNotice('请选择优惠券模板')
      return
    }
    if (!username) {
      showAdminNotice('请填写用户名')
      return
    }
    adminCouponIssueSubmitting.value = true
    try {
      const res = await deps.request<{ message?: string }>(`${apiOrigin}/api/admin/coupons/issue`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          templateId,
          username,
          count: Math.max(1, Number(adminCouponIssue.value.count) || 1),
          reason: adminCouponIssue.value.reason.trim() || '定向发放'
        })
      })
      showAdminNotice(res.message || '发券成功')
    } catch (err) {
      showAdminNotice((err as Error).message || '发券失败')
    } finally {
      adminCouponIssueSubmitting.value = false
    }
  }

  async function generateAdminCouponRedeemCodes() {
    if (!deps.isAdmin.value) return
    const templateId = Number(adminCouponRedeem.value.templateId)
    if (!Number.isFinite(templateId) || templateId <= 0) {
      showAdminNotice('请选择优惠券模板')
      return
    }
    const count = Math.max(1, Math.min(50, Number(adminCouponRedeem.value.count) || 1))
    adminCouponRedeemSubmitting.value = true
    try {
      const body: Record<string, unknown> = {
        templateId,
        count,
        maxUses: Math.max(1, Number(adminCouponRedeem.value.maxUses) || 1),
        remark: adminCouponRedeem.value.remark.trim() || null
      }
      const fixed = adminCouponRedeem.value.code.trim()
      if (fixed) {
        body.code = fixed
        body.count = 1
      }
      const res = await deps.request<{ codes?: string[]; created?: number; message?: string }>(
        `${apiOrigin}/api/admin/coupons/redeem-codes`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        }
      )
      adminCouponGeneratedCodes.value = Array.isArray(res.codes) ? res.codes.map(String) : []
      showAdminNotice(`已生成 ${res.created ?? adminCouponGeneratedCodes.value.length} 个兑换码，见下方「兑换码库」`)
      adminCouponRedeem.value.code = ''
      adminCouponRedeemFilterTemplateId.value = String(templateId)
      await fetchAdminCouponRedeemCodes()
    } catch (err) {
      showAdminNotice((err as Error).message || '生成兑换码失败')
    } finally {
      adminCouponRedeemSubmitting.value = false
    }
  }

  function copyAdminCouponCodes() {
    const text = adminCouponGeneratedCodes.value.join('\n')
    if (!text) return
    copyTextAdmin(text)
  }

  async function setAdminCouponTemplateStatus(templateId: number | string, status: 'ACTIVE' | 'OFF') {
    if (!deps.isAdmin.value) return
    const id = Number(templateId)
    if (!Number.isFinite(id) || id <= 0) return
    try {
      await deps.request(`${apiOrigin}/api/admin/coupons/templates/${id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status })
      })
      showAdminNotice(status === 'ACTIVE' ? '模板已启用，用户可领取' : '模板已停用领取')
      await fetchAdminCouponTemplates()
    } catch (err) {
      showAdminNotice((err as Error).message || '更新状态失败')
    }
  }

  async function deleteAdminCouponTemplate(templateId: number | string) {
    if (!deps.isAdmin.value) return
    const id = Number(templateId)
    if (!Number.isFinite(id) || id <= 0) return
    if (!window.confirm('确定删除该优惠券模板？已发给用户的券模板无法删除，可改用「停用领取」。')) return
    try {
      await deps.request(`${apiOrigin}/api/admin/coupons/templates/${id}`, { method: 'DELETE' })
      showAdminNotice('模板已删除')
      await fetchAdminCouponTemplates()
    } catch (err) {
      const msg = (err as Error).message || '删除失败'
      showAdminNotice(msg)
      window.alert(msg)
    }
  }

  async function fetchAdminAiConfig() {
    if (!deps.isAdmin.value) return
    try {
      const res = await deps.request<AdminAiConfig>(`${apiOrigin}/api/admin/ai/config`)
      adminAiConfig.value = {
        enabled: !!res.enabled,
        baseUrl: String(res.baseUrl || '').trim() || 'https://api.deepseek.com',
        apiKey: '',
        apiKeyConfigured: !!res.apiKeyConfigured,
        apiKeyMasked: String(res.apiKeyMasked || ''),
        model: String(res.model || '').trim() || 'deepseek-v4-pro',
        supportModel: String(res.supportModel || res.model || '').trim() || 'deepseek-v4-pro',
        timeoutSeconds: Number.isFinite(Number(res.timeoutSeconds)) ? Number(res.timeoutSeconds) : 120,
        models: Array.isArray(res.models) ? res.models.map((x) => String(x)) : [],
        provider: res.provider
      }
    } catch (err) {
      showAdminNotice((err as Error).message)
    }
  }

  async function saveAdminAiConfig() {
    if (!deps.isAdmin.value) return
    const body: Record<string, unknown> = {
      enabled: !!adminAiConfig.value.enabled,
      baseUrl: String(adminAiConfig.value.baseUrl || '').trim(),
      model: String(adminAiConfig.value.model || '').trim(),
      supportModel: String(adminAiConfig.value.supportModel || adminAiConfig.value.model || '').trim(),
      timeoutSeconds: Number.isFinite(Number(adminAiConfig.value.timeoutSeconds))
        ? Number(adminAiConfig.value.timeoutSeconds)
        : 60
    }
    const typedKey = String(adminAiConfig.value.apiKey || '').trim()
    if (typedKey) {
      body.apiKey = typedKey
    }
    if (!body.baseUrl) {
      showAdminNotice('请填写 API Base URL')
      return
    }
    if (!body.model) {
      showAdminNotice('请填写模型名称')
      return
    }
    if (!typedKey && !adminAiConfig.value.apiKeyConfigured) {
      showAdminNotice('请填写 API Key（或先配置环境变量 DEEPSEEK_API_KEY）')
      return
    }
    try {
      const res = await deps.request<AdminAiConfig>(`${apiOrigin}/api/admin/ai/config`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      })
      adminAiConfig.value = {
        enabled: !!res.enabled,
        baseUrl: String(res.baseUrl || '').trim() || String(body.baseUrl),
        apiKey: '',
        apiKeyConfigured: !!res.apiKeyConfigured,
        apiKeyMasked: String(res.apiKeyMasked || ''),
        model: String(res.model || '').trim() || String(body.model),
        supportModel: String(res.supportModel || res.model || '').trim(),
        timeoutSeconds: Number.isFinite(Number(res.timeoutSeconds))
          ? Number(res.timeoutSeconds)
          : Number(body.timeoutSeconds),
        models: Array.isArray(res.models) ? res.models.map((x) => String(x)) : [],
        provider: res.provider
      }
      showAdminNotice(res.message || 'AI 配置已保存')
    } catch (err) {
      showAdminNotice((err as Error).message)
    }
  }

  async function testAdminAiConfig() {
    if (!deps.isAdmin.value) return
    adminAiTesting.value = true
    try {
      const res = await deps.request<AdminAiTestResponse>(`${apiOrigin}/api/admin/ai/test`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt: adminAiTestPrompt.value.trim() })
      })
      const reply = String(res.reply || '').trim()
      showAdminNotice(`测试成功（${res.model}）：${reply || '已收到响应'}`)
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      adminAiTesting.value = false
    }
  }

  async function autoAuditPendingTasks(apply: boolean) {
    if (!deps.isAdmin.value) return
    aiAutoAuditSubmitting.value = true
    try {
      const doApply = !!apply
      if (doApply) {
        const ok = window.confirm('确认让 AI 自动审核并执行通过/驳回所有待审核任务吗？')
        if (!ok) return
      }
      const res = await deps.request<AdminAiAutoAuditResponse>(`${apiOrigin}/api/admin/ai/tasks/auto-audit`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ apply: doApply })
      })
      const msg = doApply
        ? `AI 自动审核已完成：待审核 ${res.pendingCount}，通过 ${res.approvedCount}，驳回 ${res.rejectedCount}，错误 ${res.errorCount}`
        : `AI 预审已完成：待审核 ${res.pendingCount}，通过 ${res.approvedCount}，驳回 ${res.rejectedCount}，错误 ${res.errorCount}`
      showAdminNotice(msg)
      await deps.fetchTasks()
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      aiAutoAuditSubmitting.value = false
    }
  }

  async function fetchAiAutoAuditEnabled() {
    if (!deps.isAdmin.value) return
    try {
      const res = await deps.request<{ enabled: boolean }>(`${apiOrigin}/api/admin/ai/auto-audit/enabled`)
      aiAutoAuditEnabled.value = !!res.enabled
    } catch (err) {
      showAdminNotice((err as Error).message)
    }
  }

  async function setAiAutoAuditEnabled(enabled: boolean) {
    if (!deps.isAdmin.value) return
    aiAutoAuditSubmitting.value = true
    try {
      await deps.request(`${apiOrigin}/api/admin/ai/auto-audit/enabled`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled })
      })
      aiAutoAuditEnabled.value = enabled
      showAdminNotice(enabled ? 'AI 自动审核已开启' : 'AI 自动审核已关闭')
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      aiAutoAuditSubmitting.value = false
    }
  }

  async function refreshAuditCenter(type: 'task' | 'avatar' | 'rider' = deps.auditSubModule.value) {
    await Promise.all([deps.fetchTasks(), fetchAdminUsers()])

    if (type === 'task') {
      const pendingTaskCount = deps.safeAllTasks.value.filter((t) => isPendingAdminReview(t)).length
      const unpaidDraftCount = deps.safeAllTasks.value.filter((t) => isAwaitingBountyPayment(t)).length
      const extra =
        unpaidDraftCount > 0 ? `（另有 ${unpaidDraftCount} 条待支付赏金草稿，不在审核列表中）` : ''
      showAdminNotice(`审核中心已刷新，当前待审任务 ${pendingTaskCount} 条${extra}`)
      return
    }

    if (type === 'avatar') {
      const pendingAvatarCount = adminUsers.value.filter((u) => u.avatarPending).length
      showAdminNotice(`审核中心已刷新，当前待审头像 ${pendingAvatarCount} 条`)
      return
    }

    const pendingRiderCount = adminUsers.value.filter((u) => u.riderApplicationPending).length
    showAdminNotice(`审核中心已刷新，当前待审接单员 ${pendingRiderCount} 条`)
  }

  const appealTask = ref<TaskItem | null>(null)

  const appealText = ref('')

  const appealSubmitting = ref(false)

  function openAppeal(task: TaskItem) {
    if (!deps.isLoggedIn.value) return
    appealTask.value = task
    appealText.value = ''
  }

  function closeAppeal() {
    appealTask.value = null
    appealText.value = ''
    appealSubmitting.value = false
  }

  function appealProcessedStoreKey() {
    return `appeal_processed_${deps.myName.value || 'admin'}`
  }

  function appealDecisionStoreKey() {
    return `appeal_decision_${deps.myName.value || 'admin'}`
  }

  function normalizeAppealDecision(raw: unknown): 'APPROVED' | 'REJECTED' | undefined {
    const value = String(raw ?? '').trim().toUpperCase()
    if (value === 'APPROVED') return 'APPROVED'
    if (value === 'REJECTED') return 'REJECTED'
    return undefined
  }

  function loadAppealDecisionMap() {
    try {
      const raw = localStorage.getItem(appealDecisionStoreKey())
      if (!raw) return {} as Record<string, 'APPROVED' | 'REJECTED'>
      const parsed = JSON.parse(raw) as Record<string, unknown>
      if (!parsed || typeof parsed !== 'object') return {} as Record<string, 'APPROVED' | 'REJECTED'>
      const map: Record<string, 'APPROVED' | 'REJECTED'> = {}
      for (const [key, value] of Object.entries(parsed)) {
        const decision = normalizeAppealDecision(value)
        if (decision) map[key] = decision
      }
      return map
    } catch {
      return {} as Record<string, 'APPROVED' | 'REJECTED'>
    }
  }

  function persistAppealDecisionMap(map: Record<string, 'APPROVED' | 'REJECTED'>) {
    try {
      localStorage.setItem(appealDecisionStoreKey(), JSON.stringify(map))
    } catch {
      // ignore
    }
  }

  function loadAppealProcessedSet() {
    try {
      const raw = localStorage.getItem(appealProcessedStoreKey())
      if (!raw) return new Set<string>()
      const arr = JSON.parse(raw) as string[]
      return new Set(Array.isArray(arr) ? arr : [])
    } catch {
      return new Set<string>()
    }
  }

  function persistAppealProcessedSet(set: Set<string>) {
    try {
      localStorage.setItem(appealProcessedStoreKey(), JSON.stringify(Array.from(set.values())))
    } catch {
      // ignore
    }
  }

  function parseAppealPayload(content: string) {
    // 申诉消息格式由 submitAppeal() 生成；兼容全角/半角冒号与空格
    const normalized = String(content || '').replace(/\r/g, '')
    const taskIdRaw = normalized.match(/任务\s*ID\s*[：:]\s*(\d+)/i)?.[1]
    const titleRaw = normalized.match(/标题\s*[：:]\s*(.+)/i)?.[1]
    const statusRaw = normalized.match(/状态\s*[：:]\s*(.+)/i)?.[1]
    const parsedTaskId = taskIdRaw ? Number(taskIdRaw) : NaN
    return {
      taskId: Number.isFinite(parsedTaskId) && parsedTaskId > 0 ? parsedTaskId : undefined,
      title: titleRaw ? String(titleRaw).trim().split('\n')[0]?.trim() : undefined,
      taskStatus: statusRaw ? String(statusRaw).trim().split('\n')[0]?.trim() : undefined
    }
  }

  async function fetchAppealTaskMap(taskIds: number[]) {
    const uniqueIds = Array.from(new Set(taskIds.filter((id) => Number.isFinite(id) && id > 0)))
    if (!uniqueIds.length) return new Map<number, TaskItem>()
    try {
      const data = await deps.request<TaskPageResponse>(`${apiOrigin}/api/tasks?page=1&size=500`)
      const records = Array.isArray(data.records) ? data.records : []
      const map = new Map<number, TaskItem>()
      for (const id of uniqueIds) {
        const task = records.find((item) => item.id === id)
        if (task) map.set(id, task)
      }
      return map
    } catch {
      return new Map<number, TaskItem>()
    }
  }

  function resolveAppealDecision(
    taskId: number | undefined,
    stored: unknown,
    taskMap: Map<number, TaskItem>
  ): 'APPROVED' | 'REJECTED' | undefined {
    const local = normalizeAppealDecision(stored)
    if (local) return local
    if (!taskId) return undefined
    const task = taskMap.get(taskId)
    if (!task?.appealAuditedAt) return undefined
    return task.appealApproved ? 'APPROVED' : 'REJECTED'
  }

  async function fetchAdminAppeals() {
    if (!deps.isLoggedIn.value || !deps.isAdmin.value) return
    adminAppealLoading.value = true
    try {
      // 复用聊天联系人：这些都是给 admin 发过消息的人
      await deps.fetchChatContacts()
      const contacts = Array.from(
        new Set(
          [
            ...(Array.isArray(deps.chatContacts.value)
              ? deps.chatContacts.value.map((c: any) => (typeof c === 'string' ? c : c?.username))
              : []),
            ...(Array.isArray(deps.temporaryChatContacts.value)
              ? deps.temporaryChatContacts.value.map((c: any) => (typeof c === 'string' ? c : c?.username))
              : [])
          ].filter(Boolean)
        )
      ) as string[]

      const processed = loadAppealProcessedSet()
      const decisionMap = loadAppealDecisionMap()
      const all: typeof adminAppeals.value = []
      const draftRows: typeof adminAppeals.value = []

      for (const name of contacts) {
        try {
          const history = await deps.request<ChatMessageItem[]>(
            `${apiOrigin}/api/chat/history?targetUser=${encodeURIComponent(name)}`
          )
          for (const m of history) {
            if (m.toUsername !== 'admin') continue
            if (!String(m.content || '').includes('【任务申诉】')) continue
            const payload = parseAppealPayload(String(m.content || ''))
            const key = `msg_${m.id}`
            draftRows.push({
              key,
              messageId: m.id,
              fromUsername: m.fromUsername,
              createdAt: m.createdAt,
              taskId: payload.taskId,
              title: payload.title,
              taskStatus: payload.taskStatus,
              content: m.content,
              processed: processed.has(key),
              decision: undefined
            })
          }
        } catch {
          // ignore single-contact fetch error
        }
      }

      const taskMap = await fetchAppealTaskMap(draftRows.map((row) => row.taskId).filter((id): id is number => !!id))
      for (const row of draftRows) {
        all.push({
          ...row,
          decision: resolveAppealDecision(row.taskId, decisionMap[row.key], taskMap)
        })
      }

      all.sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
      adminAppeals.value = all
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      adminAppealLoading.value = false
    }
  }

  function toggleAppealProcessed(itemKey: string) {
    const set = loadAppealProcessedSet()
    if (set.has(itemKey)) set.delete(itemKey)
    else set.add(itemKey)
    persistAppealProcessedSet(set)
    adminAppeals.value = adminAppeals.value.map((a) => (a.key === itemKey ? { ...a, processed: set.has(itemKey) } : a))
  }

  async function decideAppeal(appeal: (typeof adminAppeals.value)[number], decision: 'APPROVED' | 'REJECTED') {
    if (!deps.isLoggedIn.value || !deps.isAdmin.value) return
    const taskId = Number(appeal.taskId)
    if (!Number.isFinite(taskId) || taskId <= 0) {
      showAdminNotice('该申诉未解析到有效任务 ID，无法裁决')
      return
    }
    if (appeal.decision === 'APPROVED' || appeal.decision === 'REJECTED') {
      showAdminNotice('该申诉已裁决，无法再次操作')
      return
    }
    if (appealDecidingKey.value) return

    appealDecidingKey.value = appeal.key
    try {
      // 规则：通过申诉 => 不扣完成率；且“服务确实完成”则给钱（这里默认通过即认定服务完成）
      await deps.request(`${apiOrigin}/api/tasks/${taskId}/appeal/audit`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          approve: decision === 'APPROVED',
          serviceCompleted: decision === 'APPROVED',
          remark: ''
        })
      })

      await deps.fetchTasks()

      const decisionMap = loadAppealDecisionMap()
      decisionMap[appeal.key] = decision
      persistAppealDecisionMap(decisionMap)

      const processedSet = loadAppealProcessedSet()
      processedSet.add(appeal.key)
      persistAppealProcessedSet(processedSet)

      adminAppeals.value = adminAppeals.value.map((a) => {
        if (a.key === appeal.key) return { ...a, decision, processed: true }
        if (a.taskId === taskId) return { ...a, decision }
        return a
      })
      showAdminNotice(decision === 'APPROVED' ? '已通过申诉，系统通知已发送' : '已驳回申诉，系统通知已发送')
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      appealDecidingKey.value = null
    }
  }

  function openAdminAppealReply(appeal: (typeof adminAppeals.value)[number]) {
    adminAppealReply.value = { toUsername: appeal.fromUsername, taskId: appeal.taskId, title: appeal.title }
    adminAppealReplyText.value = ''
  }

  function closeAdminAppealReply() {
    adminAppealReply.value = null
    adminAppealReplyText.value = ''
    adminAppealReplySubmitting.value = false
  }

  async function sendAppealSystemMessage(payload: {
    username: string
    title: string
    content: string
    eventType: string
    refId?: number | null
  }) {
    await deps.request(`${apiOrigin}/api/system-messages/admin/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: payload.username,
        title: payload.title,
        content: payload.content,
        category: 'TASK',
        eventType: payload.eventType,
        refId: payload.refId ?? null
      })
    })
  }

  async function submitAdminAppealReply() {
    if (!deps.isLoggedIn.value || !deps.isAdmin.value) return
    if (!adminAppealReply.value) return
    const text = adminAppealReplyText.value.trim()
    if (!text) {
      showAdminNotice('请输入补充说明')
      return
    }
    adminAppealReplySubmitting.value = true
    try {
      const taskPart = adminAppealReply.value.taskId
        ? `任务 #${adminAppealReply.value.taskId}${adminAppealReply.value.title ? `《${adminAppealReply.value.title}》` : ''}`
        : ''
      const content = taskPart ? `${taskPart}\n${text}` : text
      await sendAppealSystemMessage({
        username: adminAppealReply.value.toUsername,
        title: '申诉补充说明',
        content,
        eventType: 'APPEAL_REPLY'
      })
      showAdminNotice('已通过系统通知发送补充说明')
      closeAdminAppealReply()
    } catch (err) {
      showAdminNotice((err as Error).message)
    } finally {
      adminAppealReplySubmitting.value = false
    }
  }

  const filteredAdminAppeals = computed(() => {
    const kw = adminAppealKeyword.value.trim().toLowerCase()
    if (!kw) return adminAppeals.value
    return adminAppeals.value.filter((a) => {
      return (
        String(a.fromUsername || '').toLowerCase().includes(kw) ||
        String(a.title || '').toLowerCase().includes(kw) ||
        String(a.taskId || '').includes(kw) ||
        String(a.taskStatus || '').toLowerCase().includes(kw)
      )
    })
  })

  async function submitAppeal() {
    if (!deps.isLoggedIn.value) return
    if (!appealTask.value) return
    const text = appealText.value.trim()
    if (!text) {
      deps.errorMsg.value = '请输入申诉内容'
      return
    }

    appealSubmitting.value = true
    try {
      const t = appealTask.value
      const content = [
        '【任务申诉】',
        `任务ID：${t.id}`,
        `标题：${t.title}`,
        `状态：${statusLabelMap[t.status] || t.status}`,
        `发布者：${t.publisherName || '-'}`,
        `接单员：${t.claimerName || '-'}`,
        `申诉人：${deps.myName.value || '-'}`,
        '---',
        text
      ].join('\n')

      await deps.request<ChatMessageItem>(`${apiOrigin}/api/chat/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ toUsername: 'admin', content })
      })

      await deps.fetchChatContacts()
      deps.showProfileNotice('申诉已发送给管理员（admin）')
      closeAppeal()
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      appealSubmitting.value = false
    }
  }

  return {
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
    adminModule: deps.adminModule,
    auditSubModule: deps.auditSubModule,
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
  }
}
