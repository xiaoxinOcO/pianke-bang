/** datetime-local 与送达时间相关的纯工具 */

export function formatDateTimeLocal(date: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function toDateKey(date: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function roundUpToStepMinutes(date: Date, step: number) {
  const d = new Date(date)
  d.setSeconds(0, 0)
  const m = d.getMinutes()
  const delta = (step - (m % step)) % step
  d.setMinutes(m + delta)
  return d
}

export function timeText(date: Date) {
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

export function defaultLatestDeliveryAt() {
  return formatDateTimeLocal(new Date(Date.now() + 90 * 60 * 1000))
}

export function defaultDeliveryStartAt() {
  return formatDateTimeLocal(new Date(Date.now() + 30 * 60 * 1000))
}

export function defaultDeliveryEndAt() {
  return formatDateTimeLocal(new Date(Date.now() + 90 * 60 * 1000))
}

export function buildLatestDeliveryAtByMinutes(minutes: number) {
  const safeMinutes = Number.isFinite(minutes) ? Math.max(10, Math.floor(minutes)) : 60
  return formatDateTimeLocal(new Date(Date.now() + safeMinutes * 60 * 1000))
}
