import type { NoticeItem, TaskItem, TaskStatus } from '../types/app'

export type HallRewardRange = '' | 'low' | 'mid' | 'high'
export type HallSortMode = 'default' | 'new' | 'reward'

export interface HallFilters {
  keyword: string
  status: '' | TaskStatus
  taskType: string
  zone: string
  rewardRange: HallRewardRange
  sortMode: HallSortMode
}

export interface HallZoneOption {
  value: string
  label: string
}

export interface HallActivityItem {
  text: string
  time: string
}

export interface HallLeaderboardItem {
  name: string
  score: number
  seed: string
  avatarUrl?: string
}

export interface HallStatCard {
  label: string
  value: string
  note: string
}

export interface HallPaging {
  page: number
  size: number
  totalPages: number
  totalElements: number
}

export interface ClaimerRatingEntry {
  avg: number
  count: number
}

/**
 * HallView 由 stores/use*Store 提供的字段
 */
export interface HallViewInject {
  publicNotices: NoticeItem[]
  publicNoticesLoading: boolean
  fetchPublicNotices: () => void | Promise<void>
  hallTopNotice: NoticeItem | null
  hallNoticeExpanded: boolean
  toggleHallNoticeExpanded: () => void
  activateHallNotice: (n: NoticeItem) => void
  isLoggedIn: boolean
  filters: HallFilters
  applyFilters: () => void
  resetFilters: () => void
  errorMsg: string
  loading: boolean
  visibleTasks: TaskItem[]
  taskTypeOptions: Array<{ value: string; label: string }>
  hotTaskTypes: Array<{ type: string; label: string; count: number }>
  taskTypeLabel: (taskType?: string) => string
  selectTaskTypeFilter: (taskType: string) => void
  hallZones: HallZoneOption[]
  selectZoneFilter: (zone: string) => void
  selectSortMode: (mode: HallSortMode) => void
  hallStats: HallStatCard[]
  hallActivities: HallActivityItem[]
  hallLeaderboard: HallLeaderboardItem[]
  hallFaqItems: Array<{ question: string; answer: string }>
  hallTaskEmoji: (title: string) => string
  hallStatusLabel: (status: TaskStatus) => string
  hallTaskCategoryLabel: (task: TaskItem) => string
  hallTaskDistance: (task: TaskItem) => string
  formatTaskTimeAgo: (iso?: string) => string
  openAuthPage?: (mode: 'login' | 'register') => void
  showProfileNotice?: (text: string) => void
  promoActivities?: Array<{
    id?: string | number
    title?: string
    subtitle?: string
    claimed?: boolean
    templateId?: number
    action?: string
    sceneCode?: string
  }>
  fetchPromoActivities?: () => void | Promise<void>
  claimPromoActivity?: (act: {
    id?: string | number
    templateId?: number
    claimed?: boolean
    action?: string
  }) => void | Promise<void>
  canRevealTaskContact: (task?: Partial<TaskItem> | null) => boolean
  maskName: (name: string) => string
  publisherAvatarUrl: (name?: string) => string
  publisherNameInitial: (name?: string) => string
  normalizeImageUrl: (url?: string) => string
  maskContact: (contact: string) => string
  claimerRatingStatsMap: Record<string, ClaimerRatingEntry>
  openTaskDetail: (task: TaskItem) => void
  isRiderApproved: boolean
  myName: string
  askPublisherFirst: (task: TaskItem) => void
  claimTask: (id: number) => void | Promise<void>
  actionLoadingMap: Record<number, boolean>
  openCompleteDialog: (taskId: number) => void
  confirmTask: (taskId: number) => void | Promise<void>
  cancelTask: (taskId: number) => void | Promise<void>
  formatDeadlineCountdown: (latestDeliveryAt?: string) => string
  paging: HallPaging
  goPrevPage: () => void
  goNextPage: () => void
}
