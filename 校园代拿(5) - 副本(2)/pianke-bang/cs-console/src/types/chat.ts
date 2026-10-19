export interface AuthResponse {
  message: string
  username: string
  token?: string
}

export interface MeResponse {
  username: string
  avatarUrl?: string
  customerServiceStaff?: boolean
}

export interface UserPublicProfile {
  username: string
  avatarUrl?: string
  riderApproved?: boolean
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
}

export interface RealtimeEvent {
  type: string
  data?: {
    id?: number
    fromUsername?: string
    toUsername?: string
    content?: string
    createdAt?: string
  }
}
