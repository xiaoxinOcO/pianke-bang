const STATUS_LABEL = {
  OPEN: '待接单',
  CLAIMED: '配送中',
  OVERDUE: '接单员超时',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  PENDING_CONFIRMATION: '待发布方确认',
  REJECTED: '审核驳回',
  PENDING_REVIEW: '待管理员审核'
}

const STATUS_CLASS = {
  OPEN: 'status-open',
  CLAIMED: 'status-claimed',
  COMPLETED: 'status-completed',
  OVERDUE: 'status-danger',
  PENDING_CONFIRMATION: 'status-pending',
  REJECTED: 'status-danger',
  CANCELLED: 'status-muted',
  PENDING_REVIEW: 'status-pending'
}

const TASK_TYPE_LABEL = {
  EXPRESS: '快递代取',
  FOOD: '外卖代拿',
  PRINT: '打印代送',
  QUEUE: '排队代办',
  OTHER: '其他'
}

function resolveStatusLabel(task) {
  if (task.status === 'PENDING_REVIEW' && task.bountyPrepaid !== true) {
    return '待支付赏金'
  }
  return STATUS_LABEL[task.status] || task.status
}

function statusClass(status) {
  return STATUS_CLASS[status] || 'status-muted'
}

function taskTypeLabel(type) {
  return TASK_TYPE_LABEL[type] || type || '其他'
}

function taskTypeAbbr(type) {
  const map = {
    EXPRESS: '递',
    FOOD: '餐',
    PRINT: '印',
    QUEUE: '排',
    OTHER: '他'
  }
  return map[type] || '他'
}

function taskTypeBadgeClass(type) {
  const map = {
    EXPRESS: 'express',
    FOOD: 'food',
    PRINT: 'print',
    QUEUE: 'queue',
    OTHER: 'other'
  }
  return map[type] || 'other'
}

module.exports = {
  STATUS_LABEL,
  resolveStatusLabel,
  statusClass,
  taskTypeLabel,
  taskTypeAbbr,
  taskTypeBadgeClass
}
