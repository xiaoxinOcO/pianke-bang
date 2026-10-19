import type { TaskItem } from '../types'

/** 与用户端 / 小程序一致：判断任务是否加急 */
export function isTaskUrgent(task?: Partial<TaskItem> | null): boolean {
  const v = task?.urgentEnabled as unknown
  return v === true || v === 1 || v === 'true'
}

/**
 * 接单员可见的加急赏（约 80%）。
 * 优先用后端 urgentRiderBonus，否则按 urgentFee * 0.8 估算。
 */
export function taskUrgentRiderBonus(task?: Partial<TaskItem> | null): number {
  if (!isTaskUrgent(task)) return 0
  let bonus = Number(task?.urgentRiderBonus)
  if (!(Number.isFinite(bonus) && bonus > 0)) {
    const fee = Number(task?.urgentFee)
    if (Number.isFinite(fee) && fee > 0) {
      bonus = Math.round(fee * 0.8 * 100) / 100
    }
  }
  return Number.isFinite(bonus) && bonus > 0 ? bonus : 0
}

export function formatUrgentBonusText(task?: Partial<TaskItem> | null): string {
  const bonus = taskUrgentRiderBonus(task)
  return bonus > 0 ? bonus.toFixed(2) : ''
}
