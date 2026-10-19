import { ref, type Ref } from 'vue'
import { apiOrigin } from '../../constants/api'
import type { PageView } from '../../types/app'

type CouponsRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type CouponsDomainDeps = {
  isLoggedIn: Ref<boolean>
  showUserDropdown: Ref<boolean>
  request: CouponsRequest
  showProfileNotice: (text: string) => void
  openAuthPage: (mode: 'login' | 'register') => void
  setView: (view: PageView) => void
}

/**
 * 用户优惠券中心：我的券列表、兑换码、进入优惠券页。
 * 发布页选券 / 管理台券模板仍分别归 publish、admin。
 */
export function createCouponsDomain(deps: CouponsDomainDeps) {
  const myCoupons = ref<Array<Record<string, unknown>>>([])
  const myCouponsLoading = ref(false)

  async function fetchMyCoupons(status?: string) {
    if (!deps.isLoggedIn.value) {
      myCoupons.value = []
      return
    }
    myCouponsLoading.value = true
    try {
      const qs = status ? `?status=${encodeURIComponent(status)}` : ''
      myCoupons.value = await deps.request<Array<Record<string, unknown>>>(
        `${apiOrigin}/api/coupons/mine${qs}`
      )
    } catch (err) {
      myCoupons.value = []
      deps.showProfileNotice((err as Error).message || '加载优惠券失败')
    } finally {
      myCouponsLoading.value = false
    }
  }

  async function redeemCouponCode(code: string) {
    if (!deps.isLoggedIn.value) {
      deps.openAuthPage('login')
      return
    }
    const trimmed = (code || '').trim()
    if (!trimmed) {
      deps.showProfileNotice('请输入兑换码')
      return
    }
    try {
      const res = await deps.request<{ message?: string }>(`${apiOrigin}/api/coupons/redeem`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: trimmed })
      })
      deps.showProfileNotice(res?.message || '兑换成功')
      await fetchMyCoupons()
    } catch (err) {
      deps.showProfileNotice((err as Error).message || '兑换失败')
    }
  }

  async function goCoupons() {
    if (!deps.isLoggedIn.value) {
      deps.openAuthPage('login')
      return
    }
    deps.showUserDropdown.value = false
    deps.setView('coupons')
    await fetchMyCoupons()
  }

  return {
    myCoupons,
    myCouponsLoading,
    fetchMyCoupons,
    redeemCouponCode,
    goCoupons
  }
}
