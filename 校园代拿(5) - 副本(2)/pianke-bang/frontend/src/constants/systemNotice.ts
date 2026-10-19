import type { ChatContactItem } from '../types/app'

export const SYSTEM_NOTICE_USERNAME = '__system_notice__'

export const SYSTEM_NOTICE_CONTACT: ChatContactItem = {
  username: SYSTEM_NOTICE_USERNAME,
  displayName: '系统通知',
  contactPreview: '任务、申诉、钱包与平台公告',
  isSystemNotice: true
}

export function isSystemNoticeContact(username?: string | null) {
  return String(username || '').trim() === SYSTEM_NOTICE_USERNAME
}
