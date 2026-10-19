function taskStatusToCn(status) {
  const map = {
    PENDING_REVIEW: '待审核',
    REJECTED: '已驳回',
    OPEN: '待接单',
    CLAIMED: '已被接单',
    PENDING_CONFIRMATION: '待发布者确认',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    OVERDUE: '超时'
  }
  return map[String(status || '').toUpperCase()] || status || '状态已更新'
}

function formatTaskStatusNoticeMessage(payload) {
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
  if (status === 'COMPLETED' || event === 'TASK_COMPLETED' || event === 'TASK_CONFIRMED' || event === 'TASK_AUTO_CONFIRMED') {
    return bookTitle ? `任务${bookTitle}已完成` : '任务已完成'
  }
  if (event === 'TASK_CANCELLED' || status === 'CANCELLED') {
    return bookTitle ? `任务${bookTitle}已取消` : '任务已取消'
  }
  const cnStatus = taskStatusToCn(status)
  return bookTitle ? `任务${bookTitle}状态更新为：${cnStatus}` : `任务状态更新为：${cnStatus}`
}

function taskStatusToastTitle(event, status) {
  const evt = String(event || '').toUpperCase()
  const st = String(status || '').toUpperCase()
  if (evt === 'TASK_APPROVED') return '任务审核通过'
  if (evt === 'TASK_REJECTED' || st === 'REJECTED') return '任务审核未通过'
  if (evt === 'TASK_CLAIMED') return '任务已被接单'
  if (st === 'PENDING_CONFIRMATION' || evt === 'TASK_PENDING_CONFIRMATION') return '待确认完成'
  if (st === 'COMPLETED' || evt === 'TASK_COMPLETED') return '任务已完成'
  return '任务状态更新'
}

function inferTaskEvent(prevStatus, nextStatus) {
  const prev = String(prevStatus || '').toUpperCase()
  const next = String(nextStatus || '').toUpperCase()
  if (prev === 'PENDING_REVIEW' && next === 'OPEN') return 'TASK_APPROVED'
  if (prev === 'PENDING_REVIEW' && next === 'REJECTED') return 'TASK_REJECTED'
  if (prev === 'OPEN' && next === 'CLAIMED') return 'TASK_CLAIMED'
  if (next === 'PENDING_CONFIRMATION') return 'TASK_PENDING_CONFIRMATION'
  if (next === 'COMPLETED') return 'TASK_COMPLETED'
  if (next === 'CANCELLED') return 'TASK_CANCELLED'
  return ''
}

module.exports = {
  taskStatusToCn,
  formatTaskStatusNoticeMessage,
  taskStatusToastTitle,
  inferTaskEvent
}
