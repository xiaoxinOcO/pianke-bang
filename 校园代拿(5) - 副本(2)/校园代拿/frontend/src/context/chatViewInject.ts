import type { ChatContactItem, ChatMessageItem, NoticeItem, SystemNoticeFeedItem, TaskItem } from '../types/app'

/**
 * ChatView 由 stores/use*Store 提供的字段
 */
export interface ChatViewInject {
  fetchChatContacts: () => void | Promise<void>
  chatContacts: ChatContactItem[]
  chatTarget: string
  openChatWith: (username: string) => void
  isRiderUser: (username?: string, riderApproved?: boolean | string | number | null) => boolean
  openContactProfile: (username: string) => void
  normalizeImageUrl: (url: string) => string
  unreadMap: Record<string, number>
  loadChatHistory: () => void | Promise<void>
  bindChatMessagesEl: (el: unknown) => void
  chatMessages: ChatMessageItem[]
  shouldShowChatDivider: (idx: number) => boolean
  formatChatDateDivider: (createdAt: string) => string
  myName: string
  chatAvatarUrl: (username: string) => string
  chatImageDraft: string
  chatInput: string
  onChatImageSelected: (event: Event) => void
  clearChatImageDraft: () => void
  setChatImageDraft: (src: string) => void
  openImagePreview: (src: string) => void
  sendChatMessage: () => void | Promise<void>
  clearAllChatUnread: () => void | Promise<void>
  pinChatContact: (username: string) => void
  deleteChatContact: (username: string) => void
  isChatContactPinned: (username: string) => boolean
  chatPinnedUsernames: string[]
  goHall: () => void
  isSystemNoticeContact: (username?: string | null) => boolean
  SYSTEM_NOTICE_USERNAME: string
  SYSTEM_NOTICE_CONTACT: ChatContactItem
  openSupportChat?: () => void
  publishedNoticesForChat: NoticeItem[]
  systemNoticeFeed: SystemNoticeFeedItem[]
  fetchUserSystemMessages: () => void | Promise<void>
  formatNoticeChatTime: (raw?: string) => string
  formatChatTime: (createdAt: string) => string
  myTasksForCustomerService: TaskItem[]
  sendTaskToCustomerService: (task: TaskItem) => void | Promise<void>
  sendingTaskToCsId: number | null
  safeAllTasks: TaskItem[]
}
