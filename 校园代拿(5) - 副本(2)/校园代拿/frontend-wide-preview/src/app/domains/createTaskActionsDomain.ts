import { ref, type ComputedRef, type Ref } from 'vue'
import { apiBase, apiOrigin } from '../../constants/api'
import type {
  TaskItem,
  TaskPageResponse,
  WalletOverview
} from '../../types/app'
import { isAwaitingBountyPayment } from '../../utils/taskStatus'
import { parseTaskImageUrls, parseTaskListResponse, proofFromTask, usernameMatches } from '../../utils/taskParse'
import { isBenignAlipaySyncError } from '../../utils/alipayErrors'
import { sleep, withRequestTimeout } from '../../utils/asyncHelpers'
import { defaultDeliveryEndAt, defaultDeliveryStartAt } from '../../utils/datetimeLocal'
import { formatNetworkErrorMessage } from '../../utils/http'

type TaskRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type TaskActionsDomainDeps = {
  request: TaskRequest
  errorMsg: Ref<string>
  isLoggedIn: Ref<boolean>
  isAdmin: Ref<boolean>
  myName: Ref<string>
  loading: Ref<boolean>
  submitting: Ref<boolean>
  actionLoadingMap: Ref<Record<number, boolean>>
  tasks: Ref<TaskItem[]>
  allTasks: Ref<TaskItem[]>
  mineTasks: Ref<TaskItem[]>
  safeTasks: ComputedRef<TaskItem[]> | Ref<TaskItem[]>
  safeAllTasks: ComputedRef<TaskItem[]>
  selectedTask: Ref<TaskItem | null>
  currentView: Ref<string>
  setView: (view: any) => void
  setPublishFormHint: (hint: { type: 'success' | 'error'; text: string } | null, ms?: number) => void
  validatePublishFormBeforePay: () => string | null | undefined
  openPublishPaymentSheet: (mode?: any) => void
  closePublishPaymentSheet: () => void
  resetPublishFormAfterSuccess: () => void
  buildPublishTaskBody: (extra?: Record<string, unknown>) => Record<string, unknown>
  publishPaymentMethod: Ref<string>
  publishPaymentSheetMode: Ref<string>
  publishSelectedCouponId: Ref<any>
  publishPayableTotal: ComputedRef<number> | Ref<number>
  publishWalletPayDisabled: ComputedRef<boolean> | Ref<boolean>
  form: Ref<any>
  openAuthPage: (mode: 'login' | 'register') => void
  scrollPublishValidationIntoView: (message: string) => void
  syncDeliverySelectionFromLatestDeliveryAt: () => void
  myUnpaidPublishDraft: ComputedRef<TaskItem | null | undefined> | Ref<TaskItem | null | undefined>
  isMyPublishedTask: (task: TaskItem) => boolean
  rejectedTasks: ComputedRef<TaskItem[]> | Ref<TaskItem[]>
  ratingDialogTaskId: Ref<number | null>
  getRatingForm: (taskId: number) => any
  pendingAlipayOutTradeNo: Ref<string>
  fetchWalletBalance: () => void | Promise<void>
  fetchWalletData: () => void | Promise<void>
  prepareWalletAlipayState: (force?: boolean) => Promise<void> | void
  openAlipayPayForm: (payForm: string) => void
  walletOverview: Ref<WalletOverview>
  shellSideEffects: { fetchMyTasksForProfile: () => void | Promise<void> }
  detectTaskStatusChanges: () => void
  showProfileNotice: (message: string, tone?: 'success' | 'error') => void
  fetchTasks: () => void | Promise<void>
  compressImageForChat: (file: File) => Promise<string>
  isRiderApproved: Ref<boolean>
  riderApplicationPending: Ref<boolean>
  blockedUntil: Ref<string>
  creditScore: Ref<number>
}

export function createTaskActionsDomain(deps: TaskActionsDomainDeps) {
  void apiBase
  void apiOrigin
  const pendingAlipayTaskId = ref<number | null>(null)

  const completeDialogTaskId = ref<number | null>(null)

  const completeProofText = ref('')

  const completeProofImages = ref<string[]>([])

  const completeProofFileInputRef = ref<HTMLInputElement | null>(null)

  const taskCompletionProofMap = ref<Record<number, { note: string; images: string[] }>>({})

  const proofViewTaskId = ref<number | null>(null)

  const editingRejectedTaskId = ref<number | null>(null)

  const taskCompleteProofStoreKey = 'task_complete_proof_map_v1'

  function loadTaskCompleteProofMap() {
    try {
      const raw = localStorage.getItem(taskCompleteProofStoreKey)
      if (!raw) {
        taskCompletionProofMap.value = {}
        return
      }
      const parsed = JSON.parse(raw) as Record<string, { note?: string; images?: string[] }>
      const next: Record<number, { note: string; images: string[] }> = {}
      Object.keys(parsed || {}).forEach((k) => {
        const id = Number(k)
        if (!Number.isFinite(id)) return
        const item = parsed[k] || {}
        next[id] = {
          note: String(item.note || ''),
          images: Array.isArray(item.images) ? item.images.map((x) => String(x || '')).filter(Boolean) : []
        }
      })
      taskCompletionProofMap.value = next
    } catch {
      taskCompletionProofMap.value = {}
    }
  }

  function persistTaskCompleteProofMap() {
    try {
      localStorage.setItem(taskCompleteProofStoreKey, JSON.stringify(taskCompletionProofMap.value))
    } catch {
      // ignore storage errors
    }
  }

  function hydrateCompletionProofFromTasks(list: TaskItem[]) {
    let changed = false
    for (const task of list) {
      const fromServer = proofFromTask(task)
      if (!fromServer) continue
      const prev = taskCompletionProofMap.value[task.id]
      if (
        prev &&
        prev.note === fromServer.note &&
        prev.images.length === fromServer.images.length &&
        prev.images.every((u, i) => u === fromServer.images[i])
      ) {
        continue
      }
      taskCompletionProofMap.value[task.id] = fromServer
      changed = true
    }
    if (changed) persistTaskCompleteProofMap()
  }

  function getTaskCompletionProof(taskId: number) {
    const local = taskCompletionProofMap.value[taskId]
    if (local && (local.note || local.images.length)) return local
    const task =
      deps.mineTasks.value.find((t) => t.id === taskId) ||
      deps.allTasks.value.find((t) => t.id === taskId) ||
      deps.tasks.value.find((t) => t.id === taskId) ||
      (deps.selectedTask.value?.id === taskId ? deps.selectedTask.value : undefined)
    return proofFromTask(task) || local
  }

  function openProofView(taskId: number) {
    proofViewTaskId.value = taskId
  }

  function closeProofView() {
    proofViewTaskId.value = null
  }

  async function fetchAllTasksForProfile() {
    try {
      const size = 500
      const first = await deps.request<TaskPageResponse | TaskItem[]>(`${apiBase}?page=1&size=${size}`)
      if (Array.isArray(first)) {
        deps.allTasks.value = first
      } else {
        const records = Array.isArray(first.records) ? first.records : []
        const totalPages = Math.max(Number(first.totalPages || 1), 1)

        // 最多拉取前 5 页，避免数据量过大拖慢页面
        const pagesToFetch = Math.min(totalPages, 5)
        if (pagesToFetch <= 1) {
          deps.allTasks.value = records
        } else {
          const rest = await Promise.all(
            Array.from({ length: pagesToFetch - 1 }, (_, i) => i + 2).map((p) =>
              deps.request<TaskPageResponse | TaskItem[]>(`${apiBase}?page=${p}&size=${size}`)
            )
          )

          const more: TaskItem[] = []
          for (const d of rest) {
            if (Array.isArray(d)) {
              more.push(...d)
            } else if (Array.isArray(d.records)) {
              more.push(...d.records)
            }
          }
          deps.allTasks.value = [...records, ...more]
        }
      }
      hydrateCompletionProofFromTasks(deps.allTasks.value)
    } catch {
      if (deps.allTasks.value.length === 0) {
        deps.allTasks.value = [...deps.tasks.value]
      }
    }
    await fetchMyTasksForProfile()
    deps.detectTaskStatusChanges()
  }

  function mergeTasksIntoAllTasks(records: TaskItem[]) {
    if (!records.length) return
    const merged = new Map(deps.allTasks.value.map((t) => [t.id, t]))
    for (const task of records) {
      merged.set(task.id, task)
    }
    deps.allTasks.value = Array.from(merged.values())
  }

  function mergeTasksIntoMineTasks(records: TaskItem[]) {
    if (!records.length) return
    const merged = new Map(deps.mineTasks.value.map((t) => [t.id, t]))
    for (const task of records) {
      merged.set(task.id, task)
    }
    deps.mineTasks.value = Array.from(merged.values())
  }

  async function fetchMyTasksForProfile() {
    const me = deps.myName.value.trim()
    if (!deps.isLoggedIn.value || !me) return
    try {
      const mine = await deps.request<TaskItem[] | TaskPageResponse>(`${apiBase}/mine`)
      const records = parseTaskListResponse(mine)
      if (records.length) {
        deps.mineTasks.value = records
        mergeTasksIntoAllTasks(records)
        hydrateCompletionProofFromTasks(records)
      }
    } catch {
      // /mine 不可用时走分页兜底
    }
    await fetchMyUnpaidPublishDrafts()
  }
  deps.shellSideEffects.fetchMyTasksForProfile = fetchMyTasksForProfile

  async function fetchMyUnpaidPublishDrafts() {
    if (!deps.isLoggedIn.value || !deps.myName.value.trim()) return
    try {
      const data = await deps.request<TaskPageResponse | TaskItem[]>(
        `${apiBase}?status=PENDING_REVIEW&page=1&size=50`
      )
      const records = parseTaskListResponse(data)
      const drafts = records.filter((t) => {
        if (!isAwaitingBountyPayment(t)) return false
        return deps.isMyPublishedTask(t) || usernameMatches(t.publisherName, deps.myName.value)
      })
      if (!drafts.length) return
      mergeTasksIntoMineTasks(drafts)
      mergeTasksIntoAllTasks(drafts)
    } catch {
      // ignore
    }
  }

  function clearStalePendingAlipayTask() {
    sessionStorage.removeItem('campus_pending_alipay_task_id')
    sessionStorage.removeItem('campus_pending_alipay_out_trade_no')
    sessionStorage.removeItem('campus_alipay_task_auto_sync')
    pendingAlipayTaskId.value = null
    deps.pendingAlipayOutTradeNo.value = ''
  }

  function readPendingAlipayTaskId() {
    const cachedTaskId = Number(sessionStorage.getItem('campus_pending_alipay_task_id') || 0)
    const taskId =
      pendingAlipayTaskId.value ??
      (Number.isFinite(cachedTaskId) && cachedTaskId > 0 ? cachedTaskId : null)
    if (taskId != null) {
      pendingAlipayTaskId.value = taskId
    }
    return taskId
  }

  function findBlockingUnpaidPublishTask(): TaskItem | null {
    const draft = deps.myUnpaidPublishDraft.value
    if (draft) {
      pendingAlipayTaskId.value = draft.id
      sessionStorage.setItem('campus_pending_alipay_task_id', String(draft.id))
      return draft
    }

    const cachedId = readPendingAlipayTaskId()
    if (cachedId == null) return null

    const cached = [...deps.mineTasks.value, ...deps.safeAllTasks.value].find((t) => t.id === cachedId)
    if (cached && usernameMatches(cached.publisherName, deps.myName.value) && isAwaitingBountyPayment(cached)) {
      return cached
    }

    return null
  }

  async function submitTask() {
    if (!deps.isLoggedIn.value) {
      deps.setPublishFormHint({ type: 'error', text: '请先登录后再发布任务' }, 8000)
      deps.openAuthPage('login')
      return
    }

    const validationError = deps.validatePublishFormBeforePay()
    if (validationError) {
      deps.setPublishFormHint({ type: 'error', text: validationError }, 10000)
      deps.scrollPublishValidationIntoView(validationError)
      return
    }

    deps.setPublishFormHint(null)
    try {
      await fetchMyTasksForProfile()
    } catch {
      // 拉取草稿失败不阻塞打开发布支付弹层
    }
    deps.openPublishPaymentSheet('create')
  }

  async function confirmPublishPayment() {
    if (!deps.isLoggedIn.value) {
      return
    }

    if (deps.publishPaymentMethod.value === 'wechat') {
      deps.setPublishFormHint({ type: 'error', text: '微信支付暂未开发' }, 8000)
      return
    }

    if (deps.publishPaymentMethod.value === 'wallet' && deps.publishWalletPayDisabled.value) {
      deps.setPublishFormHint(
        { type: 'error', text: '钱包余额不足，请先充值或选择支付宝支付' },
        8000
      )
      return
    }

    deps.submitting.value = true
    deps.closePublishPaymentSheet()
    deps.setPublishFormHint(null)
    const publishTimeoutMs = deps.publishPaymentMethod.value === 'wallet' ? 25000 : 45000
    const publishTimeoutMessage =
      deps.publishPaymentMethod.value === 'wallet'
        ? '钱包支付超时：请确认 Seata（8091）、campus-wallet-service 已启动，且钱包余额充足。'
        : '发布请求超时，请稍后重试。'
    try {
      if (deps.publishPaymentMethod.value !== 'wallet') {
        await fetchMyTasksForProfile()
      }

      if (deps.publishPaymentSheetMode.value === 'create') {
        const blocking = findBlockingUnpaidPublishTask()
        if (blocking && deps.publishPaymentMethod.value === 'alipay') {
          await payTaskBountyViaAlipay(blocking.id)
          deps.setPublishFormHint(
            { type: 'success', text: '已打开待支付任务的支付宝页面，请完成付款后点击「同步支付状态」。' },
            8000
          )
          return
        }
      }

      if (deps.publishPaymentSheetMode.value === 'resubmit') {
        if (editingRejectedTaskId.value == null) {
          throw new Error('未找到要重新提交的任务')
        }
        const taskId = editingRejectedTaskId.value
        const updated = await withRequestTimeout(
          deps.request<TaskItem>(`${apiBase}/${taskId}/resubmit`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(
              deps.buildPublishTaskBody({
                paymentMethod: deps.publishPaymentMethod.value.toUpperCase()
              })
            )
          }),
          publishTimeoutMs,
          publishTimeoutMessage
        )
        if (deps.publishPaymentMethod.value === 'alipay') {
          await payTaskBountyViaAlipay(taskId)
          return
        }
        clearStalePendingAlipayTask()
        editingRejectedTaskId.value = null
        deps.resetPublishFormAfterSuccess()
        deps.setPublishFormHint(
          {
            type: 'success',
            text:
              updated.bountyPrepaid === true
                ? '任务已重新提交，赏金已从钱包扣除，请等待管理员审核'
                : '任务已重新提交，请等待管理员审核'
          },
          8000
        )
        await deps.fetchTasks()
        await deps.fetchWalletBalance()
        return
      }

      const created = await withRequestTimeout(
        deps.request<TaskItem>(apiBase, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(
            deps.buildPublishTaskBody({
              paymentMethod: deps.publishPaymentMethod.value.toUpperCase()
            })
          )
        }),
        publishTimeoutMs,
        publishTimeoutMessage
      )

      if (deps.publishPaymentMethod.value === 'alipay') {
        await payTaskBountyViaAlipay(created.id)
        return
      }

      clearStalePendingAlipayTask()
      deps.resetPublishFormAfterSuccess()
      deps.setPublishFormHint(
        {
          type: 'success',
          text:
            created.bountyPrepaid === true
              ? '赏金已从钱包扣除，请等待管理员审核'
              : '任务已提交，请等待管理员审核'
        },
        8000
      )
      await deps.fetchTasks()
      await deps.fetchWalletBalance()
    } catch (err) {
      let text = (err as Error).message
      if (/Seata|Rollbacking|全局事务|can not connect to seata/i.test(text)) {
        text = 'Seata 事务失败：请确认 seata-server（8091）已启动，且已执行 seata-mysql-init.sql。'
      } else if (/钱包余额不足/i.test(text)) {
        text = '钱包余额不足，请先充值或改用支付宝支付。'
      } else if (/Internal Server Error|请求失败\(500\)/i.test(text)) {
        text = deps.publishPaymentMethod.value === 'wallet'
          ? '钱包支付失败：请确认 campus-task-service、campus-wallet-service 与 Seata 已就绪，且钱包余额充足。'
          : '发布失败：请确认 campus-task-service 与 campus-wallet-service 已启动。'
      }
      deps.setPublishFormHint({ type: 'error', text }, 8000)
    } finally {
      deps.submitting.value = false
    }
  }

  async function payTaskBountyViaAlipay(taskId: number) {
    if (deps.actionLoadingMap.value[taskId]) {
      return
    }
    deps.actionLoadingMap.value[taskId] = true
    try {
      const payload: Record<string, unknown> = { taskId }
      if (deps.publishSelectedCouponId.value != null) {
        payload.userCouponId = deps.publishSelectedCouponId.value
      }
      const res = await deps.request<{ outTradeNo: string; payForm: string; taskId: number }>(
        `${apiOrigin}/api/wallet/alipay/task-pay`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        }
      )
      pendingAlipayTaskId.value = taskId
      sessionStorage.setItem('campus_pending_alipay_task_id', String(taskId))
      if (res.outTradeNo) {
        deps.pendingAlipayOutTradeNo.value = res.outTradeNo
        sessionStorage.setItem('campus_pending_alipay_out_trade_no', res.outTradeNo)
      }
      deps.openAlipayPayForm(String(res.payForm || ''))
    } finally {
      deps.actionLoadingMap.value[taskId] = false
    }
  }

  async function syncTaskAlipayPaymentWithRetry(taskId?: number | null, attempts = 5) {
    let last: { message?: string; status?: string } | null = null
    for (let i = 0; i < attempts; i++) {
      last = await syncTaskAlipayPayment(taskId)
      if (last?.status === 'PAID') {
        return last
      }
      if (i < attempts - 1) {
        await sleep(1500)
      }
    }
    return last
  }

  function notifyOpenerAlipayTaskPaid() {
    try {
      if (window.opener && !window.opener.closed) {
        window.opener.postMessage({ type: 'campus-alipay-task-paid' }, window.location.origin)
      }
    } catch {
      // ignore
    }
  }

  function handleAlipayReturnFromPopup(payload: {
    order?: string
    taskId?: string
    orderType?: string
  }) {
    const order = String(payload.order || '').trim()
    const taskIdRaw = String(payload.taskId || '').trim()
    const orderType = String(payload.orderType || '').trim()

    if (order) {
      deps.pendingAlipayOutTradeNo.value = order
      sessionStorage.setItem('campus_pending_alipay_out_trade_no', order)
      sessionStorage.setItem('campus_alipay_auto_sync', '1')
    }
    if (taskIdRaw) {
      const taskId = Number(taskIdRaw)
      if (Number.isFinite(taskId) && taskId > 0) {
        pendingAlipayTaskId.value = taskId
        sessionStorage.setItem('campus_pending_alipay_task_id', taskIdRaw)
        sessionStorage.setItem('campus_alipay_task_auto_sync', '1')
      }
    }

    window.focus()

    if (orderType === 'TASK_BOUNTY' || taskIdRaw) {
      deps.setPublishFormHint(null)
      deps.setView('publish')
      void preparePublishAlipayState(false)
      return
    }

    deps.setView('wallet')
    void Promise.resolve(deps.prepareWalletAlipayState(true)).then(() => deps.fetchWalletData())
  }

  function onAlipayWindowMessage(event: MessageEvent) {
    if (event.origin !== window.location.origin) return
    const data = event.data as { type?: string; order?: string; taskId?: string; orderType?: string }
    if (data?.type === 'campus-alipay-return') {
      handleAlipayReturnFromPopup(data)
      return
    }
    if (data?.type === 'campus-alipay-task-paid') {
      handleAlipayTaskPaidFromPopup()
    }
  }

  function handleAlipayTaskPaidFromPopup() {
    sessionStorage.removeItem('campus_pending_alipay_task_id')
    sessionStorage.removeItem('campus_pending_alipay_out_trade_no')
    pendingAlipayTaskId.value = null
    void deps.fetchTasks()
    deps.showProfileNotice('任务赏金支付成功，请等待管理员审核')
  }

  async function syncTaskBountyPaymentForTask(taskId: number) {
    deps.actionLoadingMap.value[taskId] = true
    try {
      const res = await syncTaskAlipayPaymentWithRetry(taskId, 5)
      await deps.fetchTasks()
      if (res?.status === 'PAID') {
        sessionStorage.removeItem('campus_pending_alipay_task_id')
        if (pendingAlipayTaskId.value === taskId) {
          pendingAlipayTaskId.value = null
        }
        deps.showProfileNotice('赏金支付已确认，请等待管理员审核')
      } else {
        deps.showProfileNotice(res?.message || '暂未查到支付成功，请确认支付宝沙箱是否已扣款')
      }
    } catch (err) {
      deps.showProfileNotice((err as Error).message)
    } finally {
      deps.actionLoadingMap.value[taskId] = false
    }
  }

  async function resumeTaskBountyPayment(taskId: number) {
    try {
      await payTaskBountyViaAlipay(taskId)
    } catch (err) {
      deps.showProfileNotice((err as Error).message)
    }
  }

  async function syncAwaitingBountyPublishedTasks() {
    if (!deps.isLoggedIn.value) return
    if (!deps.myName.value.trim()) return
    const awaiting = deps.safeAllTasks.value.filter(
      (t) => usernameMatches(t.publisherName, deps.myName.value) && isAwaitingBountyPayment(t)
    )
    if (!awaiting.length) return
    let anyPaid = false
    for (const task of awaiting) {
      try {
        const res = await syncTaskAlipayPaymentWithRetry(task.id, 4)
        if (res?.status === 'PAID') {
          anyPaid = true
        }
      } catch {
        // 单条失败不影响其他
      }
    }
    if (anyPaid) {
      sessionStorage.removeItem('campus_pending_alipay_task_id')
      pendingAlipayTaskId.value = null
      await deps.fetchTasks()
      deps.showProfileNotice('任务赏金已同步，请等待管理员审核')
    }
  }

  async function abandonUnpaidPublishDraft(taskId: number) {
    if (!window.confirm('确定放弃该未支付任务？放弃后可重新发布新任务。')) return
    deps.actionLoadingMap.value[taskId] = true
    try {
      await abandonUnpaidPublishTask(taskId)
      clearStalePendingAlipayTask()
      deps.setPublishFormHint(null)
      deps.showProfileNotice('已放弃未支付任务，可以重新发布')
      await deps.fetchTasks()
    } catch (err) {
      deps.showProfileNotice((err as Error).message || '放弃失败')
    } finally {
      deps.actionLoadingMap.value[taskId] = false
    }
  }

  async function abandonUnpaidPublishTask(taskId: number) {
    try {
      await deps.request<TaskItem>(`${apiBase}/${taskId}/cancel`, { method: 'POST' })
    } catch {
      // 任务可能已被取消
    }
    sessionStorage.removeItem('campus_pending_alipay_task_id')
    sessionStorage.removeItem('campus_pending_alipay_out_trade_no')
    if (pendingAlipayTaskId.value === taskId) {
      pendingAlipayTaskId.value = null
    }
    await deps.fetchTasks()
  }

  async function syncTaskAlipayPayment(taskId?: number | null) {
    if (!deps.isLoggedIn.value) return null
    const cachedTaskId = Number(sessionStorage.getItem('campus_pending_alipay_task_id') || 0)
    const targetTaskId =
      taskId ??
      pendingAlipayTaskId.value ??
      (cachedTaskId > 0 ? cachedTaskId : null)

    if (targetTaskId) {
      return deps.request<{ message?: string; status?: string }>(
        `${apiOrigin}/api/wallet/alipay/task-sync/${encodeURIComponent(String(targetTaskId))}`,
        { method: 'POST' }
      )
    }
    return deps.request<{ message?: string; status?: string }>(
      `${apiOrigin}/api/wallet/alipay/task-sync-latest`,
      { method: 'POST' }
    )
  }

  async function preparePublishAlipayState(autoSync = false) {
    if (!deps.isLoggedIn.value) {
      return
    }

    const returnFromAlipay = sessionStorage.getItem('campus_alipay_task_auto_sync') === '1'
    const pendingTaskId = readPendingAlipayTaskId()

    const shouldAutoSync = returnFromAlipay || (autoSync && pendingTaskId != null)
    if (!shouldAutoSync) {
      return
    }

    sessionStorage.removeItem('campus_alipay_task_auto_sync')

    if (pendingTaskId == null) {
      return
    }

    try {
      const res = await syncTaskAlipayPaymentWithRetry(pendingTaskId, 5)
      if (res?.status === 'PAID') {
        clearStalePendingAlipayTask()
        deps.resetPublishFormAfterSuccess()
        deps.setPublishFormHint(
          { type: 'success', text: res.message || '任务赏金支付成功，请等待管理员审核' },
          8000
        )
        notifyOpenerAlipayTaskPaid()
        await deps.fetchTasks()
      } else {
        const detail = res?.message ? `${res.message}。` : ''
        deps.setPublishFormHint(
          {
            type: 'error',
            text: `${detail}若已在沙箱完成付款，请前往「我的 → 我发布的」点击「同步支付状态」。`
          },
          10000
        )
      }
    } catch (err) {
      const message = (err as Error).message
      if (isBenignAlipaySyncError(message)) {
        clearStalePendingAlipayTask()
        return
      }
      deps.setPublishFormHint({ type: 'error', text: message }, 8000)
    }
  }

  async function claimTask(taskId: number) {
    if (!deps.isLoggedIn.value) {
      return
    }

    if (!deps.isRiderApproved.value) {
      deps.errorMsg.value = deps.riderApplicationPending.value
        ? '你的接单员申请正在审核中，暂时不能接单'
        : '你还不是接单员，请先在发布页提交接单员申请'
      return
    }

    if (!deps.myName.value.trim()) {
      deps.errorMsg.value = '请先填写“我的昵称”后再接单'
      return
    }

    const nowTs = Date.now()
    const blockedTs = deps.blockedUntil.value ? Date.parse(deps.blockedUntil.value) : NaN
    if (Number.isFinite(blockedTs)) {
      if (blockedTs <= nowTs) {
        deps.blockedUntil.value = ''
      } else {
        deps.errorMsg.value = `信誉分过低，当前禁止接单至 ${String(deps.blockedUntil.value).replace('T', ' ').slice(0, 16)}`
        return
      }
    }
    if (deps.creditScore.value < 60) {
      deps.errorMsg.value = `信誉分过低（当前 ${deps.creditScore.value}），暂不可接单`
      return
    }

    const currentTask = deps.safeTasks.value.find((t) => t.id === taskId)
    if (currentTask && currentTask.publisherName === deps.myName.value.trim()) {
      deps.errorMsg.value = '不能接取自己发布的任务'
      return
    }

    deps.actionLoadingMap.value[taskId] = true
    deps.errorMsg.value = ''
    try {
      await deps.request<TaskItem>(`${apiBase}/${taskId}/claim`, { method: 'POST' })
      await deps.fetchTasks()
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      deps.actionLoadingMap.value[taskId] = false
    }
  }

  async function completeTask(taskId: number) {
    if (!deps.isLoggedIn.value) {
      return
    }

    deps.actionLoadingMap.value[taskId] = true
    deps.errorMsg.value = ''
    try {
      const note = completeProofText.value.trim()
      const images = [...completeProofImages.value]
      const payload = {
        note: note || undefined,
        proofImages: images.length ? images : undefined
      }

      const saved = await deps.request<TaskItem>(`${apiBase}/${taskId}/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      })
      const fromServer = proofFromTask(saved)
      taskCompletionProofMap.value[taskId] = fromServer || { note, images }
      persistTaskCompleteProofMap()
      closeCompleteDialog()
      await deps.fetchTasks()
      void deps.fetchWalletBalance()
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      deps.actionLoadingMap.value[taskId] = false
    }
  }

  function openCompleteDialog(taskId: number) {
    completeDialogTaskId.value = taskId
    completeProofText.value = ''
    completeProofImages.value = []
  }

  function closeCompleteDialog() {
    completeDialogTaskId.value = null
    completeProofText.value = ''
    completeProofImages.value = []
    if (completeProofFileInputRef.value) {
      completeProofFileInputRef.value.value = ''
    }
  }

  function triggerCompleteProofFilePick() {
    completeProofFileInputRef.value?.click()
  }

  async function onCompleteProofFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement
    const files = Array.from(input.files || [])
    if (!files.length) return

    const imageFiles = files.filter((f) => f.type.startsWith('image/'))
    if (!imageFiles.length) {
      deps.errorMsg.value = '请上传图片文件（jpg/png/webp）'
      input.value = ''
      return
    }

    const maxRemain = Math.max(0, 6 - completeProofImages.value.length)
    if (maxRemain <= 0) {
      deps.errorMsg.value = '最多上传 6 张凭证图片'
      input.value = ''
      return
    }

    const selected = imageFiles.slice(0, maxRemain)
    const urls = await Promise.all(
      selected.map(
        (file) =>
          new Promise<string>((resolve, reject) => {
            const reader = new FileReader()
            reader.onload = () => resolve(String(reader.result || ''))
            reader.onerror = () => reject(new Error('图片读取失败'))
            reader.readAsDataURL(file)
          })
      )
    ).catch(() => {
      deps.errorMsg.value = '图片读取失败，请重试'
      return [] as string[]
    })

    if (urls.length) {
      completeProofImages.value = [...completeProofImages.value, ...urls]
    }
    input.value = ''
  }

  function removeCompleteProofImage(idx: number) {
    completeProofImages.value = completeProofImages.value.filter((_, i) => i !== idx)
  }

  async function cancelTask(taskId: number) {
    if (!deps.isLoggedIn.value) {
      return
    }

    const task =
      deps.safeTasks.value.find((t) => t.id === taskId) ||
      deps.safeAllTasks.value.find((t) => t.id === taskId) ||
      deps.mineTasks.value.find((t) => t.id === taskId)
    if (task && task.status === 'REJECTED' && Boolean(task.overdueRejected)) {
      deps.showProfileNotice('超时驳回任务不可在接单方侧取消，请由发布者修改后重新提交')
      return
    }

    deps.actionLoadingMap.value[taskId] = true
    deps.errorMsg.value = ''
    try {
      await deps.request<TaskItem>(`${apiBase}/${taskId}/cancel`, { method: 'POST' })
      await deps.fetchTasks()
      await fetchMyTasksForProfile()
      deps.showProfileNotice(task ? `任务「${task.title}」已取消` : '任务已取消')
    } catch (err) {
      const msg = (err as Error).message || '取消失败，请稍后重试'
      deps.errorMsg.value = msg
      deps.showProfileNotice(msg)
    } finally {
      deps.actionLoadingMap.value[taskId] = false
    }
  }

  async function deleteTask(taskId: number) {
    if (!deps.isLoggedIn.value) return
    const task =
      deps.safeTasks.value.find((t) => t.id === taskId) ||
      deps.safeAllTasks.value.find((t) => t.id === taskId) ||
      deps.rejectedTasks.value.find((t) => t.id === taskId) ||
      deps.mineTasks.value.find((t) => t.id === taskId)
    if (!task) {
      deps.showProfileNotice('未找到该任务，请刷新后重试')
      return
    }
    if (task.status === 'PENDING_REVIEW' || task.status === 'OPEN' || task.status === 'CLAIMED' || task.status === 'PENDING_CONFIRMATION') {
      deps.showProfileNotice('进行中的任务不可删除')
      return
    }
    if (!window.confirm(`确定要删除任务「${task.title}」吗？此操作不可恢复。`)) {
      return
    }
    deps.actionLoadingMap.value[taskId] = true
    deps.errorMsg.value = ''
    try {
      await deps.request(`${apiBase}/${taskId}`, { method: 'DELETE' })
      deps.errorMsg.value = ''
      deps.tasks.value = deps.tasks.value.filter((t) => t.id !== taskId)
      deps.allTasks.value = deps.allTasks.value.filter((t) => t.id !== taskId)
      deps.mineTasks.value = deps.mineTasks.value.filter((t) => t.id !== taskId)
      deps.showProfileNotice(`任务「${task.title}」已删除`)
      await fetchMyTasksForProfile()
    } catch (err) {
      const msg = (err as Error).message || '删除失败，请稍后重试'
      deps.errorMsg.value = msg
      deps.showProfileNotice(msg)
    } finally {
      deps.actionLoadingMap.value[taskId] = false
    }
  }

  async function confirmTask(taskId: number) {
    if (!deps.isLoggedIn.value) {
      return
    }

    deps.actionLoadingMap.value[taskId] = true
    deps.errorMsg.value = ''
    try {
      await deps.request<TaskItem>(`${apiBase}/${taskId}/confirm`, { method: 'POST' })
      await fetchMyTasksForProfile()
      void deps.fetchWalletBalance()

      const task = deps.safeAllTasks.value.find((t) => t.id === taskId)
      deps.showProfileNotice(task ? `任务「${task.title}」已确认完成` : '已确认完成')
      if (task && task.status === 'COMPLETED' && task.claimerName && task.claimerRating == null) {
        deps.ratingDialogTaskId.value = taskId
        deps.getRatingForm(taskId)
        deps.setView('profile')
      }
    } catch (err) {
      const msg = formatNetworkErrorMessage(err)
      deps.errorMsg.value = msg
      deps.showProfileNotice(msg || '确认失败，请稍后重试')
    } finally {
      deps.actionLoadingMap.value[taskId] = false
    }
  }

  async function approveTask(taskId: number) {
    if (!deps.isAdmin.value) return
    deps.actionLoadingMap.value[taskId] = true
    deps.errorMsg.value = ''
    try {
      await deps.request<TaskItem>(`${apiBase}/${taskId}/approve`, { method: 'POST' })
      await deps.fetchTasks()
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      deps.actionLoadingMap.value[taskId] = false
    }
  }

  async function rejectTask(taskId: number) {
    if (!deps.isAdmin.value) return
    deps.actionLoadingMap.value[taskId] = true
    deps.errorMsg.value = ''
    try {
      await deps.request<TaskItem>(`${apiBase}/${taskId}/reject`, { method: 'POST' })
      await deps.fetchTasks()
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      deps.actionLoadingMap.value[taskId] = false
    }
  }

  function editRejectedTask(task: TaskItem) {
    const isOverdueRejected = task.status === 'REJECTED' && Boolean(task.overdueRejected)
    if (!deps.isLoggedIn.value || task.publisherName !== deps.myName.value || (!isOverdueRejected && task.status !== 'REJECTED')) {
      return
    }
    deps.setPublishFormHint(null)
    editingRejectedTaskId.value = task.id
    deps.form.value = {
      taskType: String(task.taskType || 'EXPRESS').toUpperCase(),
      title: task.title,
      pickupLocation: task.pickupLocation,
      pickupLat: task.pickupLat == null ? null : Number(task.pickupLat),
      pickupLng: task.pickupLng == null ? null : Number(task.pickupLng),
      dropoffLocation: task.dropoffLocation,
      dropoffLat: task.dropoffLat == null ? null : Number(task.dropoffLat),
      dropoffLng: task.dropoffLng == null ? null : Number(task.dropoffLng),
      reward: Number(task.reward),
      publisherName: task.publisherName,
      contact: task.contact,
      description: task.description || '',
      deliveryStartAt: task.deliveryStartAt ? String(task.deliveryStartAt).slice(0, 16) : defaultDeliveryStartAt(),
      deliveryEndAt: task.deliveryEndAt ? String(task.deliveryEndAt).slice(0, 16) : (task.latestDeliveryAt ? String(task.latestDeliveryAt).slice(0, 16) : defaultDeliveryEndAt()),
      imageUrls: parseTaskImageUrls(task),
      latestDeliveryAt: task.latestDeliveryAt ? String(task.latestDeliveryAt).slice(0, 16) : ''
    }
    deps.syncDeliverySelectionFromLatestDeliveryAt()
    deps.setView('publish')
  }

  async function resubmitRejectedTask() {
    if (!deps.isLoggedIn.value || editingRejectedTaskId.value == null) {
      if (!deps.isLoggedIn.value) {
        deps.setPublishFormHint({ type: 'error', text: '请先登录后再提交' }, 8000)
        deps.openAuthPage('login')
      }
      return
    }

    const validationError = deps.validatePublishFormBeforePay()
    if (validationError) {
      deps.setPublishFormHint({ type: 'error', text: validationError }, 8000)
      deps.scrollPublishValidationIntoView(validationError)
      return
    }

    deps.setPublishFormHint(null)
    try {
      await fetchMyTasksForProfile()
    } catch {
      // ignore
    }
    deps.openPublishPaymentSheet('resubmit')
  }

  return {
    pendingAlipayTaskId,
    completeDialogTaskId,
    completeProofText,
    completeProofImages,
    completeProofFileInputRef,
    taskCompletionProofMap,
    proofViewTaskId,
    editingRejectedTaskId,
    taskCompleteProofStoreKey,
    loadTaskCompleteProofMap,
    getTaskCompletionProof,
    openProofView,
    closeProofView,
    fetchAllTasksForProfile,
    fetchMyTasksForProfile,
    submitTask,
    confirmPublishPayment,
    onAlipayWindowMessage,
    syncTaskBountyPaymentForTask,
    resumeTaskBountyPayment,
    syncAwaitingBountyPublishedTasks,
    abandonUnpaidPublishDraft,
    syncTaskAlipayPayment,
    preparePublishAlipayState,
    claimTask,
    completeTask,
    openCompleteDialog,
    closeCompleteDialog,
    triggerCompleteProofFilePick,
    onCompleteProofFilesSelected,
    removeCompleteProofImage,
    cancelTask,
    deleteTask,
    confirmTask,
    approveTask,
    rejectTask,
    editRejectedTask,
    resubmitRejectedTask,
    hydrateCompletionProofFromTasks,
    clearStalePendingAlipayTask,
    findBlockingUnpaidPublishTask,
    handleAlipayReturnFromPopup,
    payTaskBountyViaAlipay,
    persistTaskCompleteProofMap,
    mergeTasksIntoAllTasks,
    mergeTasksIntoMineTasks,
    fetchMyUnpaidPublishDrafts,
    readPendingAlipayTaskId,
    syncTaskAlipayPaymentWithRetry,
    notifyOpenerAlipayTaskPaid,
    handleAlipayTaskPaidFromPopup,
    abandonUnpaidPublishTask
  }
}
