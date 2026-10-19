export type TaskStatus =
  | 'PENDING_REVIEW'
  | 'REJECTED'
  | 'OPEN'
  | 'CLAIMED'
  | 'OVERDUE'
  | 'PENDING_CONFIRMATION'
  | 'COMPLETED'
  | 'CANCELLED'

export interface TaskItem {
  id: number
  taskType?: string
  title: string
  pickupLocation: string
  dropoffLocation: string
  reward: number
  publisherName: string
  contact: string
  description: string
  status: TaskStatus
  deliveryStartAt?: string
  deliveryEndAt?: string
  latestDeliveryAt?: string
  completionNote?: string
  completionProofImages?: string[] | string
  claimerName?: string
  bountyPrepaid?: boolean
  urgentEnabled?: boolean | number | string
  urgentFee?: number
  urgentRiderBonus?: number
  urgentPushFee?: number
  urgentAt?: string
  createdAt: string
  updatedAt: string
}

export interface TaskPageResponse {
  records: TaskItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AuthResponse {
  message: string
  username: string
  token?: string
}

export interface MeResponse {
  username: string
  avatarUrl?: string
  riderApproved?: boolean
  riderApplicationPending?: boolean
  creditScore?: number
  blockedUntil?: string
}

export interface WalletOverview {
  balance: number
  totalIncome: number
  totalWithdrawn: number
  withdrawingAmount: number
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

export interface WalletMeResponse {
  overview: WalletOverview
  withdrawRequests?: WalletWithdrawItem[]
}

export interface ChatMessageItem {
  id: number
  fromUsername: string
  toUsername: string
  content: string
  createdAt: string
}
