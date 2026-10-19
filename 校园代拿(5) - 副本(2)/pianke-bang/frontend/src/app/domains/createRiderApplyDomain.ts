import { ref, type Ref } from 'vue'
import { authBase } from '../../constants/api'

type RiderApplyRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type RiderApplyDomainDeps = {
  isLoggedIn: Ref<boolean>
  isRiderApproved: Ref<boolean>
  riderApplicationPending: Ref<boolean>
  request: RiderApplyRequest
  showProfileNotice: (text: string) => void
}

/**
 * 接单员申请域：表单状态 + 提交逻辑。
 * 依赖登录态 / 通知由主状态注入，避免再造一份全局树。
 */
export function createRiderApplyDomain(deps: RiderApplyDomainDeps) {
  const riderApplyForm = ref({
    realName: '',
    studentId: '',
    phone: '',
    reason: ''
  })
  const riderApplyError = ref('')

  async function applyRider() {
    if (!deps.isLoggedIn.value) return
    if (deps.isRiderApproved.value) {
      deps.showProfileNotice('你已经是接单员')
      return
    }
    if (deps.riderApplicationPending.value) {
      deps.showProfileNotice('接单员申请已提交，请等待管理员审核')
      return
    }

    const payload = {
      realName: riderApplyForm.value.realName.trim(),
      studentId: riderApplyForm.value.studentId.trim(),
      phone: riderApplyForm.value.phone.trim(),
      reason: riderApplyForm.value.reason.trim()
    }
    riderApplyError.value = ''

    if (!payload.realName || !payload.studentId || !payload.phone || !payload.reason) {
      riderApplyError.value = '请填写完整接单员申请信息'
      return
    }
    if (payload.realName.length < 2) {
      riderApplyError.value = '真实姓名至少2个字'
      return
    }
    if (!/^\d{6,20}$/.test(payload.studentId)) {
      riderApplyError.value = '学号格式不正确（6-20位数字）'
      return
    }
    if (!/^1\d{10}$/.test(payload.phone)) {
      riderApplyError.value = '手机号格式不正确（需11位手机号）'
      return
    }
    if (payload.reason.length < 5) {
      riderApplyError.value = '申请理由至少5个字'
      return
    }

    try {
      const res = await deps.request<{
        message: string
        riderApproved: boolean
        riderApplicationPending: boolean
      }>(`${authBase}/rider/apply`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      })
      deps.isRiderApproved.value = !!res.riderApproved
      deps.riderApplicationPending.value = !!res.riderApplicationPending
      riderApplyError.value = ''
      deps.showProfileNotice(res.message || '接单员申请已提交')
    } catch (err) {
      riderApplyError.value = (err as Error).message
    }
  }

  return {
    riderApplyForm,
    riderApplyError,
    applyRider
  }
}
