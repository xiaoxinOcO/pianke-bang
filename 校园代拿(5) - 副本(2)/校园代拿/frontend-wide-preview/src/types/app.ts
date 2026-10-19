export type TaskStatus =
  | 'PENDING_REVIEW'
  | 'REJECTED'
  | 'OPEN'
  | 'CLAIMED'
  | 'OVERDUE'
  | 'PENDING_CONFIRMATION'
  | 'COMPLETED'
  | 'CANCELLED'

export type AuthMode = 'login' | 'register'
export type PageView =
  | 'hall'
  | 'profile'
  | 'publish'
  | 'chat'
  | 'admin'
  | 'riderApply'
  | 'personalInfo'
  | 'wallet'
  | 'feedback'
  | 'coupons'

export interface TaskItem {
  id: number
  taskType?: string
  title: string
  pickupLocation: string
  pickupLat?: number
  pickupLng?: number
  dropoffLocation: string
  dropoffLat?: number
  dropoffLng?: number
  reward: number
  publisherName: string
  contact: string
  description: string
  status: TaskStatus
  deliveryStartAt?: string
  deliveryEndAt?: string
  latestDeliveryAt?: string
  imageUrls?: string[] | string
  /** 接单员完成留言 */
  completionNote?: string
  /** 完成凭证图片（JSON 数组字符串或数组） */
  completionProofImages?: string[] | string
  claimerName?: string
  claimerRating?: number
  claimerRatingComment?: string
  claimerRatedAt?: string
  riderNoFault?: boolean
  appealApproved?: boolean
  appealServiceCompleted?: boolean
  overdueRejected?: boolean
  appealAuditedBy?: string
  appealRemark?: string
  /** 管理员驳回原因（展示给发布者） */
  adminRejectRemark?: string
  appealAuditedAt?: string
  /** 赏金是否已支付/锁定 */
  bountyPrepaid?: boolean
  /** 是否加急 */
  urgentEnabled?: boolean
  /** 发布方支付的加急费总额 */
  urgentFee?: number
  /** 接单员可得加急赏（约 80%） */
  urgentRiderBonus?: number
  /** 平台推送费（约 20%） */
  urgentPushFee?: number
  /** 加急生效时间（赏金预付成功后写入） */
  urgentAt?: string
  createdAt: string
  updatedAt: string
}

export interface UserPublicProfile {
  username: string
  avatarUrl?: string
  customerServiceStaff?: boolean
  publisherAverageRating?: number
  publisherRatingCount?: number
  claimerAverageRating?: number
  claimerRatingCount?: number
  claimedTaskCount?: number
  riderApproved?: boolean
  gender?: string
  phone?: string
}

export interface ApiError {
  message?: string
}

export interface AuthResponse {
  message: string
  username: string
  /** 注册成功后返回，用于登录（与注册手机号二选一） */
  userId?: number
  token?: string
}

export interface MeResponse {
  username: string
  avatarUrl?: string
  /** 审核中的头像（仅 avatarPending 时有值） */
  pendingAvatarUrl?: string
  avatarPending?: boolean
  riderApproved?: boolean
  riderApplicationPending?: boolean
  /** 登录用账号主键，与学号不同 */
  userId?: number
  realName?: string
  studentId?: string
  phone?: string
  gender?: string
  creditScore?: number
  blockedUntil?: string
  pendingPenalty?: number
  customerServiceStaff?: boolean
  /** 平台管理员（库内角色） */
  platformAdmin?: boolean
  /** USER / ADMIN / CUSTOMER_SERVICE */
  accountRole?: string
}

export interface SmsSendResponse {
  message: string
  /** local=仅写入库不发短信；aliyun=调用阿里云发送 */
  smsChannel?: 'local' | 'aliyun'
}

export interface TaskPageResponse {
  records: TaskItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminOperationLog {
  id: number
  operatorName: string
  actionType: string
  targetTaskId?: number
  detail?: string
  createdAt: string
}

export interface AdminLogPageResponse {
  records: AdminOperationLog[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface UserFeedbackItem {
  id: number
  username: string
  feedbackType: 'suggest' | 'bug'
  pageName: string
  content: string
  images: string[]
  readFlag: boolean
  createdAt: string
}

export interface UserFeedbackPageResponse {
  records: UserFeedbackItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  unreadCount: number
}

export interface CsRatingItem {
  id: number
  fromUsername: string
  agentUsername: string
  score: number
  tags: string[]
  comment: string
  createdAt: string
}

export interface CsRatingPageResponse {
  records: CsRatingItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  averageScore: number
  totalCount: number
}

export interface AdminUserItem {
  id: number
  username: string
  /** 短信注册绑定手机号 */
  phone?: string
  online?: boolean
  lastSeenAt?: string
  enabled: boolean
  avatarUrl?: string
  avatarPending?: boolean
  pendingAvatarUrl?: string
  riderApproved?: boolean
  riderApplicationPending?: boolean
  creditScore?: number
  blockedUntil?: string
  pendingPenalty?: number
  riderRealName?: string
  riderStudentId?: string
  riderPhone?: string
  riderApplyReason?: string
  riderAppliedAt?: string
  createdAt: string
  customerServiceStaff?: boolean
  platformAdmin?: boolean
  accountRole?: string
}

export interface ChatMessageItem {
  id: number
  fromUsername: string
  toUsername: string
  content: string
  createdAt: string
}

export interface ChatContactItem {
  username: string
  avatarUrl?: string
  riderApproved?: boolean
  customerServiceStaff?: boolean
  /** 聊天列表虚拟条目：系统通知 */
  isSystemNotice?: boolean
  displayName?: string
  contactPreview?: string
}

export interface WalletOverview {
  balance: number
  totalIncome: number
  totalWithdrawn: number
  withdrawingAmount: number
}

export interface WalletTransactionItem {
  id: number
  type: string
  amount: number
  balanceAfter: number
  remark?: string
  taskId?: number
  createdAt: string
}

export interface WalletWithdrawItem {
  id: number
  amount: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  accountName?: string
  accountNo?: string
  remark?: string
  auditRemark?: string
  createdAt: string
}

export interface AdminWalletWithdrawItem extends WalletWithdrawItem {
  username: string
  auditRemark?: string
}

export interface NoticeItem {
  id: number
  title: string
  content: string
  published: boolean
  pinned: boolean
  operator?: string
  createdAt: string
  updatedAt: string
}

export interface UserSystemMessageItem {
  id: number
  username: string
  title: string
  content: string
  category: string
  eventType: string
  refId?: number | null
  readFlag: boolean
  createdAt: string
}

export interface SystemMessageListResponse {
  messages: UserSystemMessageItem[]
  unreadCount: number
}

export interface SystemNoticeFeedItem {
  key: string
  kind: 'platform' | 'personal'
  title: string
  content: string
  time: string
  pinned?: boolean
  unread?: boolean
  category?: string
}

export interface AdminAiConfig {
  enabled: boolean
  baseUrl: string
  apiKey: string
  apiKeyConfigured?: boolean
  apiKeyMasked?: string
  model: string
  supportModel?: string
  timeoutSeconds: number
  models: string[]
  provider?: string
  message?: string
}

export interface AdminAiTestResponse {
  message?: string
  model: string
  baseUrl: string
  reply: string
}

export interface AdminAiTaskAuditResult {
  taskId: number
  decision: 'APPROVE' | 'REJECT' | string
  reason: string
  violations?: string[]
  applied?: boolean
  error?: string
}

export interface AdminAiAutoAuditResponse {
  apply: boolean
  pendingCount: number
  approvedCount: number
  rejectedCount: number
  errorCount: number
  results: AdminAiTaskAuditResult[]
  message?: string
}

export interface AdminTaskStatsItem {
  name: string
  count: number
}

export interface AdminTaskStatsTrendPoint {
  date: string
  count: number
}

export interface AdminTaskStatsResponse {
  totalCount: number
  todayCount: number
  trendDays: number
  publishTrend: AdminTaskStatsTrendPoint[]
  statusDistribution: AdminTaskStatsItem[]
  hotPickupLocations: AdminTaskStatsItem[]
  hotDropoffLocations: AdminTaskStatsItem[]
  source?: string
}
