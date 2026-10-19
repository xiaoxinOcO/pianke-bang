/** 平台 logo，同时作为人工客服默认头像 */
export const BRAND_LOGO_URL = '/logo.png'
/** 片刻帮品牌字标（反馈页顶栏等） */
export const BRAND_WORDMARK_URL = '/pianke-wordmark.png'

export function isCustomerServiceUsername(username?: string) {
  const name = String(username || '').trim()
  return name.includes('客服')
}

export function resolveCustomerServiceAvatar(username?: string, avatarUrl?: string, customerServiceStaff?: boolean) {
  if (customerServiceStaff || isCustomerServiceUsername(username)) {
    return BRAND_LOGO_URL
  }
  return String(avatarUrl || '').trim()
}
