export type FeedbackType = 'suggest' | 'bug'

export interface FeedbackSubmitPayload {
  type: FeedbackType
  page: string
  content: string
  images: string[]
}

export interface FeedbackViewInject {
  isLoggedIn: boolean
  myName: string
  myAvatarUrl: string
  profileNotice: string
  errorMsg: string
  openAuthPage: (mode: 'login' | 'register') => void
  goHall: () => void
  goProfile: () => void
  normalizeImageUrl: (url?: string) => string
  compressImageForChat: (file: File) => Promise<string>
  submitUserFeedback: (payload: FeedbackSubmitPayload) => Promise<boolean>
  showProfileNotice: (text: string) => void
}
