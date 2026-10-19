const walletService = require('../../services/wallet')
const authService = require('../../services/auth')
const { useLoginGuard, checkLoginGuard, getUser, setUser } = require('../../utils/auth')
const { formatMoney, formatDateTime } = require('../../utils/format')

function showNeedRiderWithdrawModal() {
  wx.showModal({
    title: '暂时不能提现',
    content: '余额可以正常使用。提现前请先完成接单员认证：我的 → 接单员申请。',
    confirmText: '去认证',
    cancelText: '知道了',
    success: (res) => {
      if (res.confirm) {
        wx.navigateTo({ url: '/pages/rider-apply/rider-apply' })
      }
    }
  })
}

function isNeedRiderWithdrawError(msg) {
  if (!msg) return false
  const text = String(msg)
  if (/NEED_RIDER_FOR_WITHDRAW|接单员|未认证/.test(text)) return true
  // garbled encoding often shows as all question marks
  if (/^\?+$/.test(text.trim())) return true
  return false
}

function isOnlyQuestionMarks(msg) {
  return !!msg && /^\?+$/.test(String(msg).trim())
}

Page({
  data: {
    username: '',
    balance: '--',
    totalIncome: '--',
    transactions: [],
    loading: false,
    syncing: false
  },

  onLoad() {
    useLoginGuard(this, '/pages/wallet/wallet')
  },

  onShow() {
    if (!checkLoginGuard(this)) return
    const user = getUser() || {}
    this.setData({ username: user.username || '' })
    this.loadWallet()
  },

  onPullDownRefresh() {
    this.loadWallet().finally(() => wx.stopPullDownRefresh())
  },

  async loadWallet() {
    this.setData({ loading: true })
    try {
      const res = await walletService.fetchWallet()
      const overview = res.overview || res
      const transactions = (res.transactions || []).map(t => ({
        ...t,
        amountText: formatMoney(t.amount),
        timeText: formatDateTime(t.createdAt)
      }))
      this.setData({
        balance: formatMoney(overview.balance),
        totalIncome: formatMoney(overview.totalIncome),
        transactions
      })
    } catch (e) {
      // 失败时不要继续展示旧余额，避免误以为没充上
      this.setData({
        balance: '--',
        totalIncome: '--',
        transactions: []
      })
      const msg = e.message || '加载失败'
      if (!isOnlyQuestionMarks(msg)) {
        wx.showToast({ title: msg, icon: 'none', duration: 3000 })
      }
    } finally {
      this.setData({ loading: false })
    }
  },

  async onWithdraw() {
    let user = getUser() || {}
    try {
      const me = await authService.fetchMe()
      if (me) {
        setUser(me)
        if (typeof getApp === 'function') {
          const app = getApp()
          if (app && typeof app.setUser === 'function') app.setUser(me)
        }
        user = me
      }
    } catch (e) {
      // keep local user if refresh fails
    }

    if (!user.riderApproved) {
      showNeedRiderWithdrawModal()
      return
    }

    wx.showModal({
      title: '提现',
      editable: true,
      placeholderText: '请输入提现金额',
      success: async (res) => {
        if (!res.confirm || !res.content) return
        const amount = Number(res.content)
        if (!amount || amount <= 0) {
          wx.showToast({ title: '请输入有效金额', icon: 'none' })
          return
        }
        try {
          await walletService.withdraw({ amount })
          wx.showToast({ title: '提现申请已提交', icon: 'success' })
          this.loadWallet()
        } catch (e) {
          const msg = e.message || '提现失败'
          if (isNeedRiderWithdrawError(msg)) {
            showNeedRiderWithdrawModal()
            return
          }
          if (isOnlyQuestionMarks(msg)) {
            wx.showToast({ title: '提现失败', icon: 'none' })
            return
          }
          // toast 过长会显示不全，用弹窗展示完整原因
          if (msg.length > 14) {
            wx.showModal({ title: '提现失败', content: msg, showCancel: false })
          } else {
            wx.showToast({ title: msg, icon: 'none' })
          }
        }
      }
    })
  },

  async onSyncRecharge() {
    if (this.data.syncing) return
    this.setData({ syncing: true })
    wx.showLoading({ title: '同步中', mask: true })
    try {
      const res = await walletService.syncAlipayRecharge()
      const paid = String(res.status || '').toUpperCase() === 'PAID'
      const msg = res.message || (paid ? '充值已到账' : '订单尚未支付，请先在网页完成支付宝沙箱支付')
      wx.showToast({
        title: msg,
        icon: paid ? 'success' : 'none',
        duration: 3000
      })
      if (paid) this.loadWallet()
    } catch (e) {
      const msg = e.message || '同步失败，请确认已在网页端发起并完成充值'
      if (!isOnlyQuestionMarks(msg)) {
        wx.showToast({
          title: msg,
          icon: 'none',
          duration: 3200
        })
      } else {
        wx.showToast({ title: '同步失败', icon: 'none', duration: 3200 })
      }
    } finally {
      wx.hideLoading()
      this.setData({ syncing: false })
    }
  }
})
