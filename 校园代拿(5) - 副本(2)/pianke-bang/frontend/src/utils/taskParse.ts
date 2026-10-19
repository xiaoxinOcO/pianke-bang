import type { TaskItem, TaskPageResponse } from '../types/app'

export function normalizeUsername(value?: string) {
  return String(value || '')
    .trim()
    .toLowerCase()
    .normalize('NFKC')
}

export function usernameMatches(a?: string, b?: string) {
  const left = normalizeUsername(a)
  const right = normalizeUsername(b)
  return left.length > 0 && left === right
}

export function parseTaskListResponse(data: unknown): TaskItem[] {
  if (Array.isArray(data)) return data as TaskItem[]
  if (data && typeof data === 'object' && Array.isArray((data as TaskPageResponse).records)) {
    return (data as TaskPageResponse).records
  }
  return []
}

export function parseTaskImageUrls(task?: TaskItem | null) {
  const raw = task?.imageUrls
  if (Array.isArray(raw)) {
    return raw.filter((u) => typeof u === 'string' && u.trim()).map((u) => u.trim())
  }
  if (typeof raw === 'string') {
    const s = raw.trim()
    if (!s) return []
    try {
      const arr = JSON.parse(s)
      if (Array.isArray(arr)) {
        return arr.filter((u) => typeof u === 'string' && String(u).trim()).map((u) => String(u).trim())
      }
      return []
    } catch {
      return []
    }
  }
  return []
}

export function parseCompletionProofImages(task?: TaskItem | null) {
  const raw = task?.completionProofImages
  if (Array.isArray(raw)) {
    return raw.filter((u) => typeof u === 'string' && u.trim()).map((u) => u.trim())
  }
  if (typeof raw === 'string') {
    const s = raw.trim()
    if (!s) return []
    try {
      const arr = JSON.parse(s)
      if (Array.isArray(arr)) {
        return arr.filter((u) => typeof u === 'string' && String(u).trim()).map((u) => String(u).trim())
      }
      return []
    } catch {
      return []
    }
  }
  return []
}

export function proofFromTask(task?: TaskItem | null) {
  if (!task) return undefined
  const note = String(task.completionNote || '').trim()
  const images = parseCompletionProofImages(task)
  if (!note && !images.length) return undefined
  return { note, images }
}
