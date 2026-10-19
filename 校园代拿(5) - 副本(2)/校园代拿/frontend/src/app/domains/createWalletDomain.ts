import { computed, ref, type Ref } from 'vue'
import { apiOrigin } from '../../constants/api'
import type { PageView, WalletOverview, WalletTransactionItem, WalletWithdrawItem } from '../../types/app'
import { isBenignAlipaySyncError } from '../../utils/alipayErrors'
import {
  calcWalletWithdrawFee,
  calcWalletWithdrawPayout,
  floorToWalletWithdrawStep,
  isValidWalletWithdrawStep,
  sanitizeWalletWithdrawAmountInput,
  WITHDRAW_HARD_MAX,
  WITHDRAW_MIN,
  WITHDRAW_STEP
} from '../../utils/walletMath'

type WalletRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type WalletDomainDeps = {
  isLoggedIn: Ref<boolean>
  isRiderApproved: Ref<boolean>
  errorMsg: Ref<string>
  showUserDropdown: Ref<boolean>
  request: WalletRequest
  setView: (view: PageView) => void
}

/**
 * 用户钱包：余额、流水、提现、支付宝充值同步。
 * 发布任务支付宝支付仍归 publish；本域只持有充值用的 pending outTradeNo。
 */
export function createWalletDomain(deps: WalletDomainDeps) {
  const walletOverview = ref<WalletOverview>({
    balance: 0,
    totalIncome: 0,
    totalWithdrawn: 0,
    withdrawingAmount: 0
  })
  const walletTransactions = ref<WalletTransactionItem[]>([])
  const walletWithdraws = ref<WalletWithdrawItem[]>([])
  const walletLoading = ref(false)
  const walletBalanceLoading = ref(false)
  const walletOverviewLoaded = ref(false)
  const walletSubModule = ref<'overview' | 'withdraws' | 'transactions'>('overview')

  const withdrawSubmitting = ref(false)
  const walletWithdrawForm = ref({
    amount: WITHDRAW_MIN,
    amountText: WITHDRAW_MIN.toFixed(1),
    accountName: '',
    accountNo: '',
    remark: ''
  })
  const walletWithdrawHint = ref<{ type: 'error' | 'success'; text: string } | null>(null)

  const walletMaxWithdrawable = computed(() => {
    const bal = Number(walletOverview.value.balance || 0)
    return floorToWalletWithdrawStep(Math.min(bal, WITHDRAW_HARD_MAX))
  })

  function onWalletWithdrawAmountInput(e: Event) {
    const el = e.target as HTMLInputElement
    const next = sanitizeWalletWithdrawAmountInput(el.value, walletMaxWithdrawable.value)
    el.value = next
    walletWithdrawForm.value.amountText = next
    if (next === '' || next === '.') {
      walletWithdrawForm.value.amount = Number.NaN
      return
    }
    walletWithdrawForm.value.amount = Number(next)
  }

  function normalizeWalletWithdrawAmount() {
    const max = walletMaxWithdrawable.value
    let a = Number(walletWithdrawForm.value.amount)
    if (!Number.isFinite(a)) {
      a = max >= WITHDRAW_MIN ? WITHDRAW_MIN : 0
    } else {
      a = Math.round(a * 10) / 10
      if (max >= WITHDRAW_MIN) {
        if (a < WITHDRAW_MIN) a = WITHDRAW_MIN
        if (a > max) a = max
      } else {
        a = 0
      }
    }
    walletWithdrawForm.value.amount = a
    walletWithdrawForm.value.amountText = a > 0 ? a.toFixed(1) : ''
  }

  function fillWalletMaxWithdraw() {
    const max = walletMaxWithdrawable.value
    if (max < WITHDRAW_MIN) return
    walletWithdrawForm.value.amount = max
    walletWithdrawForm.value.amountText = max.toFixed(1)
  }

  const walletWithdrawFeePreview = computed(() => {
    const a = Number(walletWithdrawForm.value.amount)
    if (!Number.isFinite(a) || a <= 0) return 0
    return calcWalletWithdrawFee(a)
  })
  const walletWithdrawPayoutPreview = computed(() => {
    const a = Number(walletWithdrawForm.value.amount)
    if (!Number.isFinite(a) || a <= 0) return 0
    return calcWalletWithdrawPayout(a)
  })

  const walletRechargeForm = ref({ amount: 0 })
  const alipayRechargeSubmitting = ref(false)
  const alipaySyncSubmitting = ref(false)
  const alipayRechargeHint = ref<{ type: 'error' | 'success'; text: string } | null>(null)
  const pendingAlipayOutTradeNo = ref('')

  function clearPendingAlipayRechargeOrder() {
    sessionStorage.removeItem('campus_pending_alipay_out_trade_no')
    sessionStorage.removeItem('campus_alipay_auto_sync')
    pendingAlipayOutTradeNo.value = ''
  }

  function openAlipayPayForm(payForm: string) {
    const html = payForm.trim()
    if (!html) {
      throw new Error('支付宝支付表单为空')
    }
    document.open()
    document.write(html)
    document.close()
  }

  async function fetchWalletBalance() {
    if (!deps.isLoggedIn.value) return
    walletBalanceLoading.value = true
    try {
      const data = await deps.request<{ overview: WalletOverview }>(`${apiOrigin}/api/wallet/me`)
      const ov = data.overview || { balance: 0, totalIncome: 0, totalWithdrawn: 0, withdrawingAmount: 0 }
      walletOverview.value = {
        balance: Number(ov.balance || 0),
        totalIncome: Number(ov.totalIncome || 0),
        totalWithdrawn: Number(ov.totalWithdrawn || 0),
        withdrawingAmount: Number(ov.withdrawingAmount || 0)
      }
      walletOverviewLoaded.value = true
    } catch {
      if (!walletOverviewLoaded.value) {
        walletOverview.value = { balance: 0, totalIncome: 0, totalWithdrawn: 0, withdrawingAmount: 0 }
      }
    } finally {
      walletBalanceLoading.value = false
    }
  }

  async function fetchWalletData() {
    if (!deps.isLoggedIn.value) return
    walletLoading.value = true
    try {
      // 日常进入钱包只读账本；补漏收入见 rebuildWalletData
      const data = await deps.request<{
        overview: WalletOverview
        transactions: WalletTransactionItem[]
        withdrawRequests: WalletWithdrawItem[]
      }>(`${apiOrigin}/api/wallet/me`)

      const ov = data.overview || { balance: 0, totalIncome: 0, totalWithdrawn: 0, withdrawingAmount: 0 }
      walletOverview.value = {
        balance: Number(ov.balance || 0),
        totalIncome: Number(ov.totalIncome || 0),
        totalWithdrawn: Number(ov.totalWithdrawn || 0),
        withdrawingAmount: Number(ov.withdrawingAmount || 0)
      }
      walletTransactions.value = Array.isArray(data.transactions) ? data.transactions : []
      walletWithdraws.value = Array.isArray(data.withdrawRequests) ? data.withdrawRequests : []
      walletOverviewLoaded.value = true
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
      if (!walletOverviewLoaded.value) {
        walletOverview.value = { balance: 0, totalIncome: 0, totalWithdrawn: 0, withdrawingAmount: 0 }
      }
      // 已有数据时保留列表，避免网络抖动把页面刷成空账本
    } finally {
      walletLoading.value = false
    }
  }

  /** 扫任务补漏记收入；仅对账异常时调用，不要挂在日常打开钱包路径上 */
  async function rebuildWalletData() {
    if (!deps.isLoggedIn.value) return
    walletLoading.value = true
    try {
      await deps.request(`${apiOrigin}/api/wallet/rebuild`, { method: 'POST' })
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
      walletLoading.value = false
      return
    }
    await fetchWalletData()
  }

  async function refreshPendingAlipayOrder() {
    if (!deps.isLoggedIn.value) return
    try {
      const pending = await deps.request<{ outTradeNo?: string; status?: string; message?: string }>(
        `${apiOrigin}/api/wallet/alipay/pending`
      )
      const outTradeNo = String(pending.outTradeNo || '').trim()
      if (outTradeNo) {
        pendingAlipayOutTradeNo.value = outTradeNo
        sessionStorage.setItem('campus_pending_alipay_out_trade_no', outTradeNo)
        return
      }
      clearPendingAlipayRechargeOrder()
    } catch {
      // ignore
    }
  }

  async function quietlyReconcilePendingAlipayRecharge() {
    const outTradeNo = pendingAlipayOutTradeNo.value.trim()
    if (!outTradeNo) return
    try {
      const res = await deps.request<{ message?: string; status?: string; outTradeNo?: string }>(
        `${apiOrigin}/api/wallet/alipay/sync/${encodeURIComponent(outTradeNo)}`,
        { method: 'POST' }
      )
      if (res.status === 'PAID') {
        clearPendingAlipayRechargeOrder()
        walletRechargeForm.value.amount = 0
        await fetchWalletData()
        alipayRechargeHint.value = {
          type: 'success',
          text: res.message || '充值成功，余额已更新'
        }
        return
      }
      if (res.status === 'CLOSED' || isBenignAlipaySyncError(String(res.message || ''))) {
        clearPendingAlipayRechargeOrder()
        alipayRechargeHint.value = null
      }
    } catch (err) {
      if (isBenignAlipaySyncError((err as Error).message || '')) {
        clearPendingAlipayRechargeOrder()
        alipayRechargeHint.value = null
      }
    }
  }

  /** 进入钱包：对齐服务端待同步单；仅从支付宝回跳时才自动同步并提示 */
  async function prepareWalletAlipayState(_autoSync = false) {
    await refreshPendingAlipayOrder()
    if (!deps.isLoggedIn.value) return

    const fromPayReturn = sessionStorage.getItem('campus_alipay_auto_sync') === '1'
    if (fromPayReturn) {
      sessionStorage.removeItem('campus_alipay_auto_sync')
      if (pendingAlipayOutTradeNo.value) {
        await syncAlipayRecharge(false)
      }
      return
    }

    if (pendingAlipayOutTradeNo.value) {
      await quietlyReconcilePendingAlipayRecharge()
    }
  }

  async function goWallet(subModule?: 'overview' | 'withdraws' | 'transactions') {
    if (!deps.isLoggedIn.value) {
      return
    }
    walletSubModule.value = subModule ?? 'overview'
    deps.showUserDropdown.value = false
    deps.setView('wallet')
    await prepareWalletAlipayState(true)
    await fetchWalletData()
  }

  async function submitWithdrawApply() {
    if (!deps.isLoggedIn.value) return
    if (!deps.isRiderApproved.value) {
      walletWithdrawHint.value = { type: 'error', text: '仅已认证接单员可申请提现，请先完成接单员认证' }
      return
    }

    walletWithdrawHint.value = null

    normalizeWalletWithdrawAmount()
    const max = walletMaxWithdrawable.value
    if (max < WITHDRAW_MIN) {
      walletWithdrawHint.value = {
        type: 'error',
        text: `当前最多可提 ¥${max.toFixed(1)}，未达最低提现额 ¥${WITHDRAW_MIN.toFixed(2)}`
      }
      return
    }
    const amount = Math.round(Number(walletWithdrawForm.value.amount) * 100) / 100
    if (!Number.isFinite(amount) || amount < WITHDRAW_MIN) {
      walletWithdrawHint.value = { type: 'error', text: `单笔提现最低 ¥${WITHDRAW_MIN.toFixed(2)}` }
      return
    }
    if (!isValidWalletWithdrawStep(amount)) {
      walletWithdrawHint.value = { type: 'error', text: '提现金额须为 0.1 的整数倍（如 1.0、1.1、1.2）' }
      return
    }
    if (amount > max) {
      walletWithdrawHint.value = { type: 'error', text: `提现金额不能超过当前最多可提 ¥${max.toFixed(1)}` }
      return
    }

    const accountName = walletWithdrawForm.value.accountName.trim()
    const accountNo = walletWithdrawForm.value.accountNo.trim()
    if (!accountName) {
      walletWithdrawHint.value = { type: 'error', text: '请填写收款人姓名' }
      return
    }
    if (!accountNo) {
      walletWithdrawHint.value = { type: 'error', text: '请填写收款账号（银行卡、支付宝等）' }
      return
    }

    withdrawSubmitting.value = true
    try {
      await deps.request(`${apiOrigin}/api/wallet/withdraw`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          amount,
          accountName,
          accountNo,
          remark: walletWithdrawForm.value.remark.trim()
        })
      })

      const fee = calcWalletWithdrawFee(amount)
      const payout = calcWalletWithdrawPayout(amount)
      walletWithdrawForm.value.amount = WITHDRAW_MIN
      walletWithdrawForm.value.amountText = WITHDRAW_MIN.toFixed(1)
      walletWithdrawForm.value.accountName = ''
      walletWithdrawForm.value.accountNo = ''
      walletWithdrawForm.value.remark = ''

      await fetchWalletData()
      walletWithdrawHint.value = {
        type: 'success',
        text: `提现申请已提交（手续费 ¥${fee.toFixed(2)}，预计到账 ¥${payout.toFixed(2)}），等待管理员审核`
      }
    } catch (err) {
      walletWithdrawHint.value = { type: 'error', text: (err as Error).message }
    } finally {
      withdrawSubmitting.value = false
    }
  }

  async function submitAlipayRecharge() {
    if (!deps.isLoggedIn.value) return

    alipayRechargeHint.value = null
    const amount = Number(walletRechargeForm.value.amount)
    if (!Number.isFinite(amount) || amount <= 0) {
      alipayRechargeHint.value = { type: 'error', text: '请输入大于 0 的充值金额' }
      return
    }

    alipayRechargeSubmitting.value = true
    try {
      const res = await deps.request<{ outTradeNo: string; amount: number; payForm: string }>(
        `${apiOrigin}/api/wallet/alipay/recharge`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ amount })
        }
      )

      pendingAlipayOutTradeNo.value = String(res.outTradeNo || '')
      if (pendingAlipayOutTradeNo.value) {
        sessionStorage.setItem('campus_pending_alipay_out_trade_no', pendingAlipayOutTradeNo.value)
      }

      openAlipayPayForm(String(res.payForm || ''))
    } catch (err) {
      alipayRechargeHint.value = { type: 'error', text: (err as Error).message }
    } finally {
      alipayRechargeSubmitting.value = false
    }
  }

  async function syncAlipayRecharge(preferLatest = false) {
    if (!deps.isLoggedIn.value) return

    alipaySyncSubmitting.value = true
    alipayRechargeHint.value = null
    try {
      let res: { message?: string; status?: string; outTradeNo?: string }

      if (preferLatest) {
        res = await deps.request<{ message?: string; status?: string; outTradeNo?: string }>(
          `${apiOrigin}/api/wallet/alipay/sync-latest`,
          { method: 'POST' }
        )
      } else {
        await refreshPendingAlipayOrder()
        const outTradeNo =
          pendingAlipayOutTradeNo.value.trim() ||
          sessionStorage.getItem('campus_pending_alipay_out_trade_no')?.trim() ||
          ''

        if (!outTradeNo) {
          clearPendingAlipayRechargeOrder()
          alipayRechargeHint.value = null
          return
        }
        res = await deps.request<{ message?: string; status?: string; outTradeNo?: string }>(
          `${apiOrigin}/api/wallet/alipay/sync/${encodeURIComponent(outTradeNo)}`,
          { method: 'POST' }
        )
      }

      if (res.status === 'PAID' || res.status === 'CLOSED') {
        clearPendingAlipayRechargeOrder()
        if (res.status === 'PAID') {
          walletRechargeForm.value.amount = 0
        }
      } else if (res.outTradeNo) {
        pendingAlipayOutTradeNo.value = String(res.outTradeNo)
        sessionStorage.setItem('campus_pending_alipay_out_trade_no', pendingAlipayOutTradeNo.value)
      }

      await fetchWalletData()
      const msg = String(res.message || '')
      if (res.status === 'CLOSED' || isBenignAlipaySyncError(msg)) {
        clearPendingAlipayRechargeOrder()
        alipayRechargeHint.value = null
        return
      }
      alipayRechargeHint.value = {
        type: res.status === 'PAID' ? 'success' : 'error',
        text: msg || (res.status === 'PAID' ? '充值成功' : '订单尚未支付')
      }
    } catch (err) {
      const msg = (err as Error).message || ''
      if (isBenignAlipaySyncError(msg)) {
        clearPendingAlipayRechargeOrder()
        alipayRechargeHint.value = null
        return
      }
      alipayRechargeHint.value = { type: 'error', text: msg }
    } finally {
      alipaySyncSubmitting.value = false
    }
  }

  return {
    walletOverview,
    walletTransactions,
    walletWithdraws,
    walletLoading,
    walletBalanceLoading,
    walletOverviewLoaded,
    walletSubModule,
    withdrawSubmitting,
    walletWithdrawForm,
    walletWithdrawHint,
    walletMaxWithdrawable,
    onWalletWithdrawAmountInput,
    normalizeWalletWithdrawAmount,
    fillWalletMaxWithdraw,
    walletWithdrawFeePreview,
    walletWithdrawPayoutPreview,
    walletWithdrawMin: WITHDRAW_MIN,
    walletWithdrawStep: WITHDRAW_STEP,
    walletWithdrawHardMax: WITHDRAW_HARD_MAX,
    walletRechargeForm,
    alipayRechargeSubmitting,
    alipaySyncSubmitting,
    alipayRechargeHint,
    pendingAlipayOutTradeNo,
    clearPendingAlipayRechargeOrder,
    openAlipayPayForm,
    fetchWalletBalance,
    fetchWalletData,
    rebuildWalletData,
    refreshPendingAlipayOrder,
    prepareWalletAlipayState,
    quietlyReconcilePendingAlipayRecharge,
    goWallet,
    submitWithdrawApply,
    submitAlipayRecharge,
    syncAlipayRecharge
  }
}
