import type { TaskItem, TaskStatus } from '../types/app'
import { statusLabelMap } from './taskStatus'

export const TASK_TYPE_OPTIONS = [
  { value: 'EXPRESS', label: '代取快递' },
  { value: 'FOOD', label: '代买饭' },
  { value: 'PRINT', label: '代打印' },
  { value: 'QUEUE', label: '代排队' },
  { value: 'OTHER', label: '其他' }
] as const

export function taskTypeLabel(taskType?: string) {
  const key = String(taskType || 'EXPRESS').trim().toUpperCase()
  const hit = TASK_TYPE_OPTIONS.find((opt) => opt.value === key)
  return hit?.label || '其他'
}

export function hallTaskEmoji(title: string) {
  const t = String(title || '').toLowerCase()
  if (t.includes('外卖') || t.includes('餐')) return '🍜'
  if (t.includes('快递') || t.includes('包裹') || t.includes('取件')) return '📦'
  if (t.includes('打印')) return '🖨'
  if (t.includes('搬') || t.includes('吉他') || t.includes('重物')) return '🎸'
  if (t.includes('排队') || t.includes('占座')) return '⏳'
  if (t.includes('文件') || t.includes('资料')) return '📄'
  if (t.includes('早餐')) return '🥐'
  if (t.includes('药')) return '💊'
  if (t.includes('水果')) return '🍎'
  if (t.includes('超市') || t.includes('买')) return '🛒'
  return '📋'
}

const HALL_STATUS_LABEL: Partial<Record<TaskStatus, string>> = {
  OPEN: '可接',
  CLAIMED: '进行中',
  PENDING_CONFIRMATION: '进行中',
  PENDING_REVIEW: '进行中',
  OVERDUE: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REJECTED: '已驳回'
}

export function hallStatusLabel(status: TaskStatus) {
  return HALL_STATUS_LABEL[status] || statusLabelMap[status] || status
}

export function hallTaskDistance(task: TaskItem) {
  const km = ((task.id % 15) + 3) / 10
  return `${km.toFixed(1)}km`
}

export function hallTaskCategoryLabel(task: TaskItem) {
  const key = String(task.taskType || '').trim().toUpperCase()
  const fromType = taskTypeLabel(task.taskType)
  const title = String(task.title || '')
  if (key && key !== 'EXPRESS') return fromType
  if (/外卖|饭|早餐|食堂|代买|买/.test(title)) return '代买'
  if (/打印/.test(title)) return '代打印'
  if (/排队|占座/.test(title)) return '代排队'
  if (/搬|吉他|重物|搬运/.test(title)) return '搬运'
  if (/快递|驿站|取件|代取|文件/.test(title)) return '代取快递'
  return fromType
}

export function heroTaskCardTitle(task: TaskItem) {
  const type = taskTypeLabel(task.taskType)
  const spot = task.pickupLocation?.trim() || task.title
  return `${type} · ${spot}`
}
