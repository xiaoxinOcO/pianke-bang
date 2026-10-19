import type { TaskItem, TaskStatus } from '../types/app'

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

/** 未付赏金的草稿任务不算「已发布待审核」 */
export function resolveTaskStatusLabel(task: Pick<TaskItem, 'status' | 'bountyPrepaid'>) {
  if (task.status === 'PENDING_REVIEW' && task.bountyPrepaid !== true) {
    return '待支付赏金'
  }
  return statusLabelMap[task.status] ?? task.status
}

export function isAwaitingBountyPayment(task: Pick<TaskItem, 'status' | 'bountyPrepaid'>) {
  return task.status === 'PENDING_REVIEW' && task.bountyPrepaid !== true
}

export function isPendingAdminReview(task: Pick<TaskItem, 'status' | 'bountyPrepaid'>) {
  return task.status === 'PENDING_REVIEW' && task.bountyPrepaid === true
}

export function statusClass(status: TaskStatus) {
  switch (status) {
    case 'OPEN':
      return 'status-open'
    case 'CLAIMED':
      return 'status-claimed'
    case 'COMPLETED':
      return 'status-completed'
    case 'OVERDUE':
      return 'status-rejected'
    case 'PENDING_CONFIRMATION':
      return 'status-pending_confirmation'
    case 'REJECTED':
      return 'status-rejected'
    case 'CANCELLED':
      return 'status-cancelled'
    case 'PENDING_REVIEW':
      return 'status-pending_confirmation'
  }
}
