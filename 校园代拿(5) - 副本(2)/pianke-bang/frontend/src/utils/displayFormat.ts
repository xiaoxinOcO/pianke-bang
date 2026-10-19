/** 展示文案 / 脱敏 / 状态中文 — 无共享状态 */

export function maskName(name: string) {
  if (!name) return '***'
  if (name.length <= 1) return '*'
  return `${name[0]}**`
}

export function maskContact(contact: string) {
  if (!contact) return '登录后可见'
  if (contact.length <= 4) return '****'
  return `${contact.slice(0, 2)}****${contact.slice(-2)}`
}

export function starFillState(score: number, star: number) {
  if (score >= star) return 'full'
  if (score >= star - 0.5) return 'half'
  return 'empty'
}

export function formatRating(score?: number) {
  if (score == null) return ''
  return Number.isInteger(score) ? `${score}` : score.toFixed(1)
}

export function formatDateOnly(value?: string) {
  if (!value) return '-'
  const datePart = String(value).split('T')[0]
  return datePart || String(value)
}

/** @param nowTs 当前时间戳（个人中心倒计时用 historyNowTs） */
export function formatDeadlineCountdown(latestDeliveryAt?: string, nowTs: number = Date.now()) {
  if (!latestDeliveryAt) return ''
  const deadlineTs = Date.parse(String(latestDeliveryAt))
  if (Number.isNaN(deadlineTs)) return ''
  const diffMs = deadlineTs - nowTs
  const absSec = Math.floor(Math.abs(diffMs) / 1000)
  const hours = Math.floor(absSec / 3600)
  const mins = Math.floor((absSec % 3600) / 60)
  const secs = absSec % 60
  const text =
    hours > 0
      ? `${hours}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
      : `${mins}:${String(secs).padStart(2, '0')}`
  return diffMs >= 0 ? `剩余 ${text}` : `超时 ${text}`
}

export function formatTaskTimeAgo(iso?: string) {
  if (!iso) return '刚刚'
  const diff = Date.now() - new Date(iso).getTime()
  if (!Number.isFinite(diff) || diff < 0) return '刚刚'
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  return `${days} 天前`
}

export function formatChatTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

export function formatChatDateDivider(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const now = new Date()
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  if (sameDay) return '今天'
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  const isYesterday =
    date.getFullYear() === yesterday.getFullYear() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getDate() === yesterday.getDate()
  if (isYesterday) return '昨天'
  const y = date.getFullYear()
  const m = date.getMonth() + 1
  const d = date.getDate()
  return `${y}年${m}月${d}日`
}

export function formatNoticeChatTime(raw?: string) {
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return String(raw).replace('T', ' ').slice(0, 16)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

export function taskStatusToCn(status?: string) {
  const key = String(status || '').toUpperCase()
  const map: Record<string, string> = {
    PENDING_REVIEW: '待审核',
    REJECTED: '已驳回',
    OPEN: '待接单',
    CLAIMED: '已被接单',
    PENDING_CONFIRMATION: '待发布者确认',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    OVERDUE: '超时'
  }
  return map[key] || (status || '状态已更新')
}

export function formatTaskStatusNoticeMessage(payload: { title?: string; status?: string; event?: string }) {
  const title = String(payload.title || '').trim()
  const status = String(payload.status || '').trim().toUpperCase()
  const event = String(payload.event || '').trim().toUpperCase()
  const bookTitle = title ? `《${title}》` : ''

  if (event === 'TASK_APPROVED') {
    return bookTitle
      ? `你的任务${bookTitle}已审核通过，现已出现在任务大厅`
      : '你的任务已审核通过，现已出现在任务大厅'
  }
  if (event === 'TASK_REJECTED' || status === 'REJECTED') {
    return bookTitle ? `你的任务${bookTitle}未通过审核` : '你的任务未通过审核'
  }
  if (event === 'TASK_CLAIMED' || (status === 'CLAIMED' && event !== 'TASK_PENDING_CONFIRMATION')) {
    return bookTitle ? `任务${bookTitle}已被接单` : '你的任务已被接单'
  }
  if (status === 'PENDING_CONFIRMATION' || event === 'TASK_PENDING_CONFIRMATION') {
    return bookTitle ? `任务${bookTitle}待你确认完成` : '有任务待你确认完成'
  }
  if (status === 'COMPLETED' || event === 'TASK_COMPLETED') {
    return bookTitle ? `任务${bookTitle}已完成` : '任务已完成'
  }
  const cnStatus = taskStatusToCn(status)
  return bookTitle ? `任务${bookTitle}状态更新为：${cnStatus}` : `任务状态更新为：${cnStatus}`
}

export function walletTypeLabel(type: string) {
  switch (type) {
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
      return '支付宝沙箱提现'
    case 'WITHDRAW_APPLY':
      return '提现申请'
    case 'WITHDRAW_APPROVE':
      return '提现通过'
    case 'WITHDRAW_REJECT':
      return '提现驳回'
    case 'TIMEOUT_COMPENSATION_DEBIT':
      return '超时补偿扣款（接单员）'
    case 'TIMEOUT_COMPENSATION_CREDIT':
      return '超时补偿入账（发布者）'
    case 'TIMEOUT_COMPENSATION_DEBT':
      return '超时补偿欠款登记'
    case 'TIMEOUT_COMPENSATION_DEBT_REPAY':
      return '超时补偿欠款抵扣'
    default:
      return type
  }
}

export function withdrawStatusLabel(status: 'PENDING' | 'APPROVED' | 'REJECTED') {
  if (status === 'PENDING') return '审核中'
  if (status === 'APPROVED') return '已通过'
  return '已驳回'
}

export function withdrawStatusClass(status: 'PENDING' | 'APPROVED' | 'REJECTED') {
  if (status === 'PENDING') return 'status-pending_confirmation'
  if (status === 'APPROVED') return 'status-open'
  return 'status-cancelled'
}
