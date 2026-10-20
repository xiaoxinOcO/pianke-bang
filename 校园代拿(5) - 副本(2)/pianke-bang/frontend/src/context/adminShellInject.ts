import type {
  AdminOperationLog,
  AdminUserItem,
  NoticeItem,
  TaskItem,
  AdminWalletWithdrawItem,
  AdminAiConfig,
  AdminTaskStatsResponse,
  UserFeedbackItem,
  CsRatingItem
} from '../types/app'

export interface AdminAppealRow {
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
}

export interface AdminRiderCompareRow {
  username: string
  avatarUrl?: string
  claimCount: number
  completedCount: number
  timeoutCount: number
  timeoutRate: number
  totalIncome: number
  lastClaimAt: string
  completionRate: number
}

export interface RiderChartBarItem {
  key: string
  label: string
  value: number
  text: string
  width: string
}

/**
 * AdminShell 由 stores/use*Store 提供的字段
 */
export interface AdminShellInject {
  onAdminSidebarDblClick: () => void | Promise<void>
  adminModule:
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
  taskStatsLoading: boolean
  taskStatsData: AdminTaskStatsResponse | null
  taskStatsTrendDays: 7 | 14 | 30
  taskOverviewKpis: { total: number; today: number; open: number; completed: number }
  fetchAdminTaskStats: () => void | Promise<void>
  bindTaskOverviewTrendEchartEl: (el: HTMLDivElement | null) => void
  bindTaskOverviewStatusEchartEl: (el: HTMLDivElement | null) => void
  auditSubModule: 'task' | 'avatar' | 'rider'
  refreshAuditCenter: (type?: 'task' | 'avatar' | 'rider') => void | Promise<void>
  fetchAdminAppeals: () => void | Promise<void>
  goHall: () => void
  logout: () => void
  myName: string
  adminNotice: string
  adminNoticeTone: 'success' | 'error'
  adminAppealKeyword: string
  adminAppealLoading: boolean
  adminAppeals: AdminAppealRow[]
  filteredAdminAppeals: AdminAppealRow[]
  appealDecidingKey: string | null
  formatDateOnly: (iso: string) => string
  openAdminAppealReply: (a: AdminAppealRow) => void
  decideAppeal: (a: AdminAppealRow, decision: 'APPROVED' | 'REJECTED') => void | Promise<void>
  toggleAppealProcessed: (key: string) => void
  riderRankMetric: 'completed' | 'rate' | 'income' | 'claim'
  riderOverviewTrendDays: 7 | 14 | 30
  riderOverviewKpis: { certified: number; pendingApply: number; delivering: number; completedTotal: number }
  riderOverviewTrend: {
    labels: string[]
    completed: number[]
    delivering: number[]
    cancelled: number[]
    newApply: number[]
    activeRiders: number[]
  }
  riderOverviewDistribution: Array<{ key: string; label: string; count: number }>
  bindRiderOverviewEchartEl: (el: HTMLDivElement | null) => void
  loadAdminDashboardData: () => void | Promise<void>
  adminRiderCompareRows: AdminRiderCompareRow[]
  riderTop3: {
    first: AdminRiderCompareRow | null
    second: AdminRiderCompareRow | null
    third: AdminRiderCompareRow | null
  }
  riderRankMetricLabel: (row: AdminRiderCompareRow) => string
  riderLeaderboardRows: AdminRiderCompareRow[]
  selectedRiderName: string
  riderMetricValue: (row: AdminRiderCompareRow) => number
  riderRankMaxValue: number
  adminVisibleTasks: TaskItem[]
  approveTask: (id: number) => void | Promise<void>
  rejectTask: (id: number) => void | Promise<void>
  fetchAdminLogs: (page?: number) => void | Promise<void>
  goAdminLogsPrev: () => void
  goAdminLogsNext: () => void
  fetchAdminFeedbacks: (
    page?: number,
    typeFilter?: 'all' | 'suggest' | 'bug',
    readFilter?: 'all' | 'unread' | 'read'
  ) => void | Promise<void>
  goAdminFeedbacksPrev: (
    typeFilter?: 'all' | 'suggest' | 'bug',
    readFilter?: 'all' | 'unread' | 'read'
  ) => void
  goAdminFeedbacksNext: (
    typeFilter?: 'all' | 'suggest' | 'bug',
    readFilter?: 'all' | 'unread' | 'read'
  ) => void
  setAdminFeedbackRead: (
    id: number,
    read: boolean,
    typeFilter?: 'all' | 'suggest' | 'bug',
    readFilter?: 'all' | 'unread' | 'read'
  ) => void | Promise<void>
  adminFeedbacksLoading: boolean
  adminFeedbacksUnreadCount: number
  adminFeedbacksPaging: { page: number; size: number; totalPages: number; totalElements: number }
  adminFeedbacks: UserFeedbackItem[]
  adminCsRatings: CsRatingItem[]
  adminCsRatingsPaging: { page: number; size: number; totalPages: number; totalElements: number }
  adminCsRatingsLoading: boolean
  adminCsRatingsAverage: number
  adminCsRatingsTotal: number
  fetchAdminCsRatings: (
    page?: number,
    agentFilter?: string,
    scoreFilter?: 'all' | '5' | '4' | '3' | 'low'
  ) => void | Promise<void>
  goAdminCsRatingsPrev: (
    agentFilter?: string,
    scoreFilter?: 'all' | '5' | '4' | '3' | 'low'
  ) => void
  goAdminCsRatingsNext: (
    agentFilter?: string,
    scoreFilter?: 'all' | '5' | '4' | '3' | 'low'
  ) => void
  adminLogsPaging: { page: number; size: number; totalPages: number; totalElements: number }
  adminLogs: AdminOperationLog[]
  fetchAdminUsers: () => void | Promise<void>
  adminUsers: AdminUserItem[]
  actionLoadingMap: Record<number, boolean>
  setUserEnabled: (id: number, enabled: boolean) => void | Promise<void>
  updateUserCreditScore: (userId: number, creditScore: number) => void | Promise<void>
  setUserAccountRole: (userId: number, role: string) => void | Promise<void>
  auditRiderApplication: (userId: number, approve: boolean) => void | Promise<void>
  revokeRiderCertification: (userId: number) => void | Promise<void>
  safeAllTasks: TaskItem[]
  pendingAvatarUsers: AdminUserItem[]
  normalizeImageUrl: (url: string) => string
  openImagePreview: (src: string) => void
  auditUserAvatar: (userId: number, approve: boolean) => void | Promise<void>
  errorMsg: string
  selectedRiderRow: AdminRiderCompareRow | null
  riderChartItems: RiderChartBarItem[]
  /** 绑定 ECharts 容器 DOM（勿用 inject 上的 ref：reactive 会解包成元素或 null） */
  bindRiderEchartEl: (el: HTMLDivElement | null) => void
  adminWithdrawKeyword: string
  fetchAdminPendingWithdraws: () => void | Promise<void>
  filteredAdminPendingWithdraws: AdminWalletWithdrawItem[]
  adminWithdrawRemarkMap: Record<number, string>
  auditWithdrawRequest: (id: number, approve: boolean) => void | Promise<void>
  adminWalletCreditUserId: string
  adminWalletCreditAmount: number
  adminWalletCreditRemark: string
  adminWalletCreditSubmitting: boolean
  submitAdminWalletCredit: () => void | Promise<void>
  adminCouponTab: 'activity' | 'templates' | 'issue' | 'codes'
  adminHallActivityForm: {
    title: string
    subtitle: string
    ctaText: string
    actionType: string
    actionValue: string
    couponTemplateId: string
    couponAmount: number
    couponValidDays: number
    bannerTheme: string
    bannerImageUrl: string
    bannerImageUrlWeb: string
    sortOrder: number
  }
  adminHallActivitySubmitting: boolean
  adminHallActivityEditingId: number | null
  adminHallActivityFormError: string
  adminHallActivities: Array<Record<string, unknown>>
  adminHallActivitiesLoading: boolean
  fetchAdminHallActivities: () => void | Promise<void>
  createAdminHallActivity: () => void | Promise<void>
  editAdminHallActivity: (row: Record<string, unknown>) => void
  cancelAdminHallActivityEdit: () => void
  setAdminHallActivityStatus: (id: number | string, status: 'ACTIVE' | 'OFF') => void | Promise<void>
  deleteAdminHallActivity: (id: number | string) => void | Promise<void>
  hallActionTypeLabel: (type: unknown) => string
  defaultHallCta: (actionType: string) => string
  miniprogramPageOptions: Array<{
    path: string
    name: string
    desc: string
    tab?: boolean
    needsQuery?: boolean
  }>
  selectedHallCustomPage: {
    path: string
    name: string
    desc: string
    tab?: boolean
    needsQuery?: boolean
  } | null
  onHallActionTypeChange: () => void
  onAdminHallActivityBannerSelected: (event: Event) => void | Promise<void>
  onAdminHallActivityWebBannerSelected: (event: Event) => void | Promise<void>
  openAdminHallActivityModule: () => void
  adminCouponTemplates: Array<Record<string, unknown>>
  adminCouponTemplatesLoading: boolean
  adminCouponForm: {
    name: string
    type: string
    discountAmount: number
    minSpend: number
    discountPercent: number
    maxDiscount: number
    perUserLimit: number
    totalStock: number
    claimMode: string
    sceneCode: string
    description: string
    bannerTheme: string
    bannerImageUrl: string
    /** 0=不限期限；其余为天数 */
    validDays: number
  }
  adminCouponCreateSubmitting: boolean
  adminCouponIssue: { templateId: string; username: string; count: number; reason: string }
  adminCouponIssueSubmitting: boolean
  adminCouponRedeem: { templateId: string; count: number; maxUses: number; code: string; remark: string }
  adminCouponRedeemSubmitting: boolean
  adminCouponGeneratedCodes: string[]
  adminCouponRedeemCodes: Array<Record<string, unknown>>
  adminCouponRedeemCodesLoading: boolean
  adminCouponRedeemFilterTemplateId: string
  fetchAdminCouponTemplates: () => void | Promise<void>
  createAdminCouponTemplate: () => void | Promise<void>
  issueAdminCoupon: () => void | Promise<void>
  generateAdminCouponRedeemCodes: () => void | Promise<void>
  copyAdminCouponCodes: () => void
  fetchAdminCouponRedeemCodes: () => void | Promise<void>
  adminCouponTemplateName: (id: unknown) => string
  onAdminCouponThemeChange: (templateId: number | string, event: Event) => void
  onAdminCouponBannerSelected: (event: Event) => void | Promise<void>
  onAdminCouponTemplateBannerSelected: (templateId: number | string, event: Event) => void | Promise<void>
  updateAdminCouponTemplateBanner: (templateId: number | string, bannerImageUrl: string | null) => void | Promise<void>
  copyTextAdmin: (text: string) => void
  setAdminCouponTemplateStatus: (templateId: number | string, status: 'ACTIVE' | 'OFF') => void | Promise<void>
  deleteAdminCouponTemplate: (templateId: number | string) => void | Promise<void>
  adminAiConfig: AdminAiConfig
  adminAiTestPrompt: string
  adminAiTesting: boolean
  fetchAdminAiConfig: () => void | Promise<void>
  saveAdminAiConfig: () => void | Promise<void>
  testAdminAiConfig: () => void | Promise<void>
  aiAutoAuditSubmitting: boolean
  autoAuditPendingTasks: (apply: boolean) => void | Promise<void>
  aiAutoAuditEnabled: boolean
  fetchAiAutoAuditEnabled: () => void | Promise<void>
  setAiAutoAuditEnabled: (enabled: boolean) => void | Promise<void>
  adminNoticeLoading: boolean
  fetchAdminNotices: () => void | Promise<void>
  newAdminNotice: () => void
  adminNotices: NoticeItem[]
  adminNoticeEditorId: number | null
  selectAdminNotice: (n: NoticeItem) => void
  adminNoticeEditor: {
    title: string
    content: string
    published: boolean
    pinned: boolean
    linkEnabled: boolean
    linkPath: string
  }
  adminNoticeEditorDirty: boolean
  resetAdminNoticeEditor: (n: NoticeItem | null) => void
  saveAdminNotice: () => void | Promise<void>
  adminSelectedNotice: NoticeItem | null
  toggleAdminNoticePublish: (n: NoticeItem) => void | Promise<void>
  toggleAdminNoticePin: (n: NoticeItem) => void | Promise<void>
  deleteAdminNotice: (n: NoticeItem) => void | Promise<void>
}
