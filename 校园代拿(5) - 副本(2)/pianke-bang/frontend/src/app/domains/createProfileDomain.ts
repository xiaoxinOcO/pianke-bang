import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { apiBase } from '../../constants/api'
import type { TaskItem, TaskStatus } from '../../types/app'
import { isAwaitingBountyPayment } from '../../utils/taskStatus'
import { usernameMatches } from '../../utils/taskParse'

type ProfileRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type ProfileDomainDeps = {
  isLoggedIn: Ref<boolean>
  myName: Ref<string>
  mineTasks: Ref<TaskItem[]>
  safeAllTasks: ComputedRef<TaskItem[]>
  actionLoadingMap: Ref<Record<number, boolean>>
  errorMsg: Ref<string>
  request: ProfileRequest
  showProfileNotice: (text: string) => void
  fetchTasks: () => Promise<void>
}

/**
 * 个人中心：我的任务列表派生 + 完成后评分弹窗。
 * 取消/确认/完单等动作仍归主状态（与大厅共用）。
 */
export function createProfileDomain(deps: ProfileDomainDeps) {
  const profileSubModule = ref<'rejected' | 'published' | 'claimed' | 'history'>('published')
  const ratingFormMap = ref<Record<number, { score: number; comment: string }>>({})
  const ratingDialogTaskId = ref<number | null>(null)

  const mineTaskIdSet = computed(() => new Set(deps.mineTasks.value.map((t) => t.id)))

  function isMyPublishedTask(task: TaskItem) {
    if (usernameMatches(task.publisherName, deps.myName.value)) return true
    if (!mineTaskIdSet.value.has(task.id)) return false
    const claimer = String(task.claimerName || '').trim()
    if (claimer && usernameMatches(task.claimerName, deps.myName.value)) return false
    return true
  }

  const rejectedTasks = computed(() => {
    if (!deps.myName.value.trim()) return []
    return deps.safeAllTasks.value
      .filter(
        (t) =>
          usernameMatches(t.publisherName, deps.myName.value) &&
          (t.status === 'REJECTED' || (t.status === 'CANCELLED' && Boolean(t.overdueRejected)))
      )
      .slice()
      .sort((a, b) => String(b.updatedAt || b.createdAt || '').localeCompare(String(a.updatedAt || a.createdAt || '')))
  })

  const myPublishedTasks = computed(() => {
    const merged = new Map<number, TaskItem>()
    for (const t of [...deps.mineTasks.value, ...deps.safeAllTasks.value]) {
      if (isMyPublishedTask(t)) {
        merged.set(t.id, t)
      }
    }
    return Array.from(merged.values())
  })

  const myUnpaidPublishDraft = computed(() => {
    if (!deps.myName.value.trim()) return null
    const seen = new Set<number>()
    for (const t of [...deps.mineTasks.value, ...deps.safeAllTasks.value]) {
      if (seen.has(t.id)) continue
      seen.add(t.id)
      if (isAwaitingBountyPayment(t) && isMyPublishedTask(t)) {
        return t
      }
    }
    return null
  })

  const myPublishedActiveTasks = computed(() => {
    const active = myPublishedTasks.value.filter(
      (t) => t.status !== 'COMPLETED' && t.status !== 'CANCELLED' && t.status !== 'REJECTED'
    )
    const seen = new Set(active.map((t) => t.id))
    const draft = myUnpaidPublishDraft.value
    if (draft && !seen.has(draft.id)) {
      active.unshift(draft)
    }
    return active
      .slice()
      .sort((a, b) => String(b.updatedAt || b.createdAt || '').localeCompare(String(a.updatedAt || a.createdAt || '')))
  })

  const myClaimedTasks = computed(() => {
    const mine = deps.safeAllTasks.value.filter(
      (t) =>
        usernameMatches(t.claimerName, deps.myName.value) &&
        t.status !== 'REJECTED' &&
        !(t.status === 'CANCELLED' && Boolean(t.overdueRejected))
    )
    const weight = (s: TaskStatus) => {
      if (s === 'PENDING_CONFIRMATION') return 5
      if (s === 'CLAIMED') return 4
      if (s === 'OPEN') return 3
      if (s === 'PENDING_REVIEW') return 2
      return 1
    }
    return mine.slice().sort((a, b) => {
      const w = weight(b.status) - weight(a.status)
      if (w !== 0) return w
      return String(b.updatedAt || b.createdAt || '').localeCompare(String(a.updatedAt || a.createdAt || ''))
    })
  })

  const myClaimedActiveTasks = computed(() =>
    myClaimedTasks.value.filter((t) => t.status !== 'COMPLETED' && t.status !== 'CANCELLED')
  )

  const myTasksForCustomerService = computed(() => {
    const merged = new Map<number, TaskItem>()
    for (const t of [...myPublishedTasks.value, ...myClaimedTasks.value]) {
      merged.set(t.id, t)
    }
    return Array.from(merged.values())
      .slice()
      .sort((a, b) => String(b.updatedAt || b.createdAt || '').localeCompare(String(a.updatedAt || a.createdAt || '')))
      .slice(0, 20)
  })

  const myHistoryTasks = computed(() => {
    const me = String(deps.myName.value || '').trim()
    if (!me) return []
    const HISTORY_RETENTION_MS = 3 * 24 * 60 * 60 * 1000
    const now = Date.now()
    return deps.safeAllTasks.value
      .filter(
        (t) =>
          (t.publisherName === me || t.claimerName === me) &&
          (t.status === 'COMPLETED' || t.status === 'CANCELLED')
      )
      .filter((t) => {
        const raw = String(t.updatedAt || t.createdAt || '')
        const ts = Date.parse(raw)
        if (Number.isNaN(ts)) return true
        return now - ts <= HISTORY_RETENTION_MS
      })
      .slice()
      .sort((a, b) => String(b.updatedAt || b.createdAt || '').localeCompare(String(a.updatedAt || a.createdAt || '')))
  })

  const ratingDialogTask = computed(() => {
    if (ratingDialogTaskId.value == null) return null
    return deps.safeAllTasks.value.find((t) => t.id === ratingDialogTaskId.value) || null
  })

  function getRatingForm(taskId: number) {
    if (!ratingFormMap.value[taskId]) {
      ratingFormMap.value[taskId] = { score: 5, comment: '' }
    }
    return ratingFormMap.value[taskId]
  }

  function setStarScore(taskId: number, star: number, isHalf: boolean) {
    const score = isHalf ? star - 0.5 : star
    getRatingForm(taskId).score = score
  }

  async function submitTaskRating(task: TaskItem) {
    if (!deps.isLoggedIn.value) return

    const form = getRatingForm(task.id)
    const score = Number(form.score)
    const comment = form.comment?.trim() || ''

    if (score < 1 || score > 5) {
      deps.errorMsg.value = '评分必须在1-5分之间'
      return
    }
    if (Math.abs(score * 2 - Math.round(score * 2)) > 1e-9) {
      deps.errorMsg.value = '评分仅支持 0.5 分步进（如 4.5）'
      return
    }

    deps.actionLoadingMap.value[task.id] = true
    deps.errorMsg.value = ''
    try {
      await deps.request<TaskItem>(`${apiBase}/${task.id}/rate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ rating: score, comment })
      })
      deps.showProfileNotice('评分提交成功，感谢你的反馈')
      ratingDialogTaskId.value = null
      await deps.fetchTasks()
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      deps.actionLoadingMap.value[task.id] = false
    }
  }

  return {
    profileSubModule,
    ratingFormMap,
    ratingDialogTaskId,
    ratingDialogTask,
    isMyPublishedTask,
    rejectedTasks,
    myPublishedTasks,
    myUnpaidPublishDraft,
    myPublishedActiveTasks,
    myClaimedTasks,
    myClaimedActiveTasks,
    myTasksForCustomerService,
    myHistoryTasks,
    getRatingForm,
    setStarScore,
    submitTaskRating
  }
}
