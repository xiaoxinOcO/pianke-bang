import type { TaskItem, TaskStatus } from '../types'

export const statusLabelMap: Record<TaskStatus, string> = {
  OPEN: '待接单',
  CLAIMED: '配送中',
  OVERDUE: '接单员超时',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  PENDING_CONFIRMATION: '待发布方确认',
  REJECTED: '审核驳回',
  PENDING_REVIEW: '待管理员审核'
}

export function resolveTaskStatusLabel(task: Pick<TaskItem, 'status' | 'bountyPrepaid'>) {
  if (task.status === 'PENDING_REVIEW' && task.bountyPrepaid !== true) {
    return '待支付赏金'
  }
  return statusLabelMap[task.status] ?? task.status
}

export function statusBadgeClass(status: TaskStatus) {
  switch (status) {
    case 'OPEN':
      return 'badge-open'
    case 'CLAIMED':
      return 'badge-claimed'
    case 'PENDING_CONFIRMATION':
      return 'badge-pending'
    case 'COMPLETED':
      return 'badge-done'
    case 'CANCELLED':
    case 'REJECTED':
    case 'OVERDUE':
      return 'badge-muted'
    default:
      return 'badge-muted'
  }
}
