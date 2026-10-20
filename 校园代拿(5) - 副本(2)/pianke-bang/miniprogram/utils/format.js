const { apiOrigin } = require('../config/index')

function pad(n) {
  return n < 10 ? '0' + n : String(n)
}

function formatDateTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatTimeAgo(iso) {
  if (!iso) return ''
  const diff = Date.now() - new Date(iso).getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)} 天前`
  return formatDateTime(iso)
}

function formatMoney(amount) {
  const n = Number(amount)
  if (isNaN(n)) return '0.00'
  return n.toFixed(2)
}

function maskName(name) {
  const s = String(name || '').trim()
  if (!s) return '匿名'
  if (s.length <= 1) return s
  if (s.length === 2) return s[0] + '*'
  return s[0] + '*'.repeat(s.length - 2) + s[s.length - 1]
}

function maskContact(contact) {
  const s = String(contact || '').trim()
  if (!s) return ''
  if (/^1\d{10}$/.test(s)) return s.slice(0, 3) + '****' + s.slice(7)
  return s.length > 4 ? s.slice(0, 2) + '***' + s.slice(-2) : s
}

function parseImageUrls(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function pad2(n) {
  return n < 10 ? '0' + n : String(n)
}

function formatLocalDateTime(date) {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}T${pad2(date.getHours())}:${pad2(date.getMinutes())}:00`
}

function buildDefaultDeliveryRange() {
  const now = Date.now()
  const start = new Date(now + 30 * 60 * 1000)
  const end = new Date(now + 90 * 60 * 1000)
  const endStr = formatLocalDateTime(end)
  return {
    deliveryStartAt: formatLocalDateTime(start),
    deliveryEndAt: endStr,
    latestDeliveryAt: endStr
  }
}

function combineDateTime(date, time) {
  if (!date || !time) return ''
  return `${date}T${time}:00`
}

function splitDateTime(iso) {
  if (!iso) return { date: '', time: '' }
  const normalized = String(iso).replace(' ', 'T')
  const [date, timePart = ''] = normalized.split('T')
  return {
    date,
    time: timePart.slice(0, 5)
  }
}

function normalizeAvatarUrl(url) {
  const raw = String(url || '').trim()
  if (!raw) return ''
  if (raw.startsWith('data:image/')) return raw
  if (raw.startsWith('http://') || raw.startsWith('https://')) return raw
  // 人工客服固定头像是网页的 /logo.png，小程序资源在 /images/logo.png
  if (raw === '/logo.png' || raw.endsWith('/logo.png')) return '/images/logo.png'
  if (raw.startsWith('/9j/')) return `data:image/jpeg;base64,${raw}`
  if (raw.startsWith('iVBOR')) return `data:image/png;base64,${raw}`
  if (raw.startsWith('UklGR')) return `data:image/webp;base64,${raw}`
  if (!raw.startsWith('/') && apiOrigin) {
    const looksLikeAssetPath =
      raw.startsWith('uploads/') ||
      raw.startsWith('images/') ||
      raw.startsWith('avatar/') ||
      raw.startsWith('avatars/') ||
      raw.startsWith('files/')
    if (looksLikeAssetPath) return `${apiOrigin}/${raw}`
  }
  return raw
}

function resolveUserAvatar(user) {
  if (!user) return ''
  const raw = user.avatarPending && user.pendingAvatarUrl
    ? user.pendingAvatarUrl
    : (user.avatarUrl || '')
  return normalizeAvatarUrl(raw)
}

function walletTypeLabel(type) {
  switch (String(type || '')) {
    case 'TASK_INCOME':
      return '任务入账'
    case 'TASK_BOUNTY_LOCK':
      return '发布任务冻结赏金'
    case 'TASK_BOUNTY_REFUND':
      return '任务赏金退还'
    case 'MANUAL_BONUS':
      return '其他收入'
    case 'ADMIN_CREDIT':
      return '管理员充值'
    case 'ALIPAY_RECHARGE':
      return '支付宝充值'
    case 'ALIPAY_WITHDRAW':
      return '支付宝提现'
    case 'WITHDRAW_APPLY':
      return '提现申请'
    case 'WITHDRAW_APPROVE':
      return '提现通过'
    case 'WITHDRAW_REJECT':
      return '提现驳回'
    case 'TIMEOUT_COMPENSATION_DEBIT':
      return '超时补偿扣款'
    case 'TIMEOUT_COMPENSATION_CREDIT':
      return '超时补偿入账'
    case 'TIMEOUT_COMPENSATION_DEBT':
      return '超时补偿欠款'
    case 'TIMEOUT_COMPENSATION_DEBT_REPAY':
      return '欠款抵扣'
    case 'COUPON_SUBSIDY':
      return '优惠券补贴'
    default:
      return type || '流水'
  }
}

module.exports = {
  formatDateTime,
  formatTimeAgo,
  formatMoney,
  maskName,
  maskContact,
  parseImageUrls,
  buildDefaultDeliveryRange,
  combineDateTime,
  splitDateTime,
  normalizeAvatarUrl,
  resolveUserAvatar,
  walletTypeLabel
}
