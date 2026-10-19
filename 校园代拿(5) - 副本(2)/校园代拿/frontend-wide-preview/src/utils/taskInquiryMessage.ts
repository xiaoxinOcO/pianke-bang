export interface TaskInquiryData {
  taskId: string
  title: string
  status: string
  role: string
  pickup: string
  dropoff: string
  reward: string
  publisher?: string
  claimer?: string
}

export const TASK_INQUIRY_PREFIX = '[TASK_INQUIRY]'

function normalizeTaskInquiry(raw: Record<string, unknown>): TaskInquiryData | null {
  const taskId = String(raw.taskId ?? '').trim()
  if (!taskId) return null
  return {
    taskId,
    title: String(raw.title ?? '').trim(),
    status: String(raw.status ?? '').trim(),
    role: String(raw.role ?? '').trim(),
    pickup: String(raw.pickup ?? '').trim(),
    dropoff: String(raw.dropoff ?? '').trim(),
    reward: String(raw.reward ?? '').trim(),
    publisher: String(raw.publisher ?? '').trim() || undefined,
    claimer: String(raw.claimer ?? '').trim() || undefined
  }
}

function parseLegacyTaskInquiry(raw: string): TaskInquiryData | null {
  const lines = raw
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  if (!lines[0]?.startsWith('【订单咨询】')) return null

  const fields: Record<string, string> = {}
  for (let i = 1; i < lines.length; i++) {
    const match = lines[i].match(/^([^：:]+)[：:]\s*(.*)$/)
    if (match) fields[match[1].trim()] = match[2].trim()
  }

  return normalizeTaskInquiry({
    taskId: fields['任务ID'],
    title: fields['标题'],
    status: fields['状态'],
    role: fields['我的角色'],
    pickup: fields['取件'],
    dropoff: fields['送达'],
    reward: fields['赏金'],
    publisher: fields['发布者'],
    claimer: fields['接单员']
  })
}

export function serializeTaskInquiryMessage(data: TaskInquiryData): string {
  return TASK_INQUIRY_PREFIX + JSON.stringify(data)
}

export function parseTaskInquiryMessage(content?: string | null): TaskInquiryData | null {
  const raw = String(content ?? '').trim()
  if (!raw) return null

  if (raw.startsWith(TASK_INQUIRY_PREFIX)) {
    try {
      const parsed = JSON.parse(raw.slice(TASK_INQUIRY_PREFIX.length)) as Record<string, unknown>
      return normalizeTaskInquiry(parsed)
    } catch {
      return null
    }
  }

  if (raw.startsWith('【订单咨询】')) {
    return parseLegacyTaskInquiry(raw)
  }

  return null
}

export function isTaskInquiryMessage(content?: string | null): boolean {
  return parseTaskInquiryMessage(content) != null
}
