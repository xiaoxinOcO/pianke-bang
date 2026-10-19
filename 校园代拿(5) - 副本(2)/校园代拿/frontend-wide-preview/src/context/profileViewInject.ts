import type { TaskItem } from '../types/app'

/**
 * ProfileView 由 stores/useProfileStore 提供的字段（值为已解包类型）
 */
export interface ProfileViewInject {
  profileNotice: string
  ratingDialogTask: TaskItem | null
  ratingDialogTaskId: number | null
  setStarScore: (taskId: number, star: number, isHalf: boolean) => void
  getRatingForm: (taskId: number) => { score: number; comment: string }
  starFillState: (score: number, star: number) => string
  formatRating: (score: number) => string
  submitTaskRating: (task: TaskItem) => void | Promise<void>
  actionLoadingMap: Record<number, boolean>
  goPersonalInfo: () => void
  myAvatarUrl: string
  myName: string
  creditScore: number
  blockedUntil: string
  pendingPenalty: number
  myPublishedTasks: TaskItem[]
  myClaimedTasks: TaskItem[]
  myAvatarPending: boolean
  profileSubModule: 'rejected' | 'published' | 'claimed' | 'history'
  rejectedTasks: TaskItem[]
  canRevealTaskContact: (task?: Partial<TaskItem> | null) => boolean
  maskContact: (contact: string) => string
  openAppeal: (task: TaskItem) => void
  editRejectedTask: (task: TaskItem) => void
  myPublishedActiveTasks: TaskItem[]
  myUnpaidPublishDraft: TaskItem | null
  getTaskCompletionProof: (taskId: number) => { note: string; images: string[] } | undefined
  openImagePreview: (src: string) => void
  openTaskDetail: (task: TaskItem) => void
  openProofView: (taskId: number) => void
  confirmTask: (taskId: number) => void | Promise<void>
  cancelTask: (taskId: number) => void | Promise<void>
  syncTaskBountyPaymentForTask: (taskId: number) => void | Promise<void>
  resumeTaskBountyPayment: (taskId: number) => void | Promise<void>
  abandonUnpaidPublishDraft: (taskId: number) => void | Promise<void>
  deleteTask: (taskId: number) => void | Promise<void>
  myClaimedActiveTasks: TaskItem[]
  openCompleteDialog: (taskId: number) => void
  isLoggedIn: boolean
  myHistoryTasks: TaskItem[]
  formatDeadlineCountdown: (latestDeliveryAt?: string) => string
  fetchMyTasksForProfile?: () => void | Promise<void>
  goWallet?: (sub?: string) => void
  goCoupons?: () => void
}
