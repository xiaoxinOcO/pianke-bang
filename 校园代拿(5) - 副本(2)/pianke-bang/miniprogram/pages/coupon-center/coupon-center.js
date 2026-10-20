const couponService = require('../../services/coupon')
const { isLoggedIn, requireLogin } = require('../../utils/auth')

Page({
  data: {
    activities: [],
    redeemCode: '',
    claimingId: '',
    redeeming: false,
    focus: ''
  },

  onLoad(query) {
    if (query && query.focus) {
      this.setData({ focus: String(query.focus) })
    }
  },

  onShow() {
    this.loadActivities()
  },

  onPullDownRefresh() {
    this.loadActivities().finally(() => wx.stopPullDownRefresh())
  },

  async loadActivities() {
    try {
      const rows = await couponService.fetchActivities()
      this.setData({
        activities: Array.isArray(rows) ? rows : []
      })
      if (this.data.focus) {
        wx.nextTick(() => {
          wx.pageScrollTo({ selector: '.act-card.focus', duration: 280 })
        })
      }
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },

  onRedeemInput(e) {
    this.setData({ redeemCode: e.detail.value || '' })
  },

  couponPagePath() {
    const focus = this.data.focus
    return focus
      ? `/pages/coupon-center/coupon-center?focus=${encodeURIComponent(focus)}`
      : '/pages/coupon-center/coupon-center'
  },

  async onClaimTemplate(e) {
    if (!requireLogin(this.couponPagePath())) return
    const id = e.currentTarget.dataset.id
    if (!id || this.data.claimingId) return
    this.setData({ claimingId: String(id) })
    try {
      await couponService.claimTemplate(id)
      wx.showToast({ title: '领取成功', icon: 'success' })
      await this.loadActivities()
    } catch (err) {
      wx.showToast({ title: err.message || '领取失败', icon: 'none' })
      await this.loadActivities()
    } finally {
      this.setData({ claimingId: '' })
    }
  },

  async onRedeem() {
    if (!requireLogin(this.couponPagePath())) return
    const code = (this.data.redeemCode || '').trim()
    if (!code) {
      wx.showToast({ title: '请输入兑换码', icon: 'none' })
      return
    }
    this.setData({ redeeming: true })
    try {
      const res = await couponService.redeem(code)
      wx.showToast({ title: (res && res.message) || '兑换成功', icon: 'success' })
      this.setData({ redeemCode: '' })
      this.loadActivities()
    } catch (e) {
      wx.showToast({ title: e.message || '兑换失败', icon: 'none' })
    } finally {
      this.setData({ redeeming: false })
    }
  },

  goMineCoupons() {
    if (!requireLogin('/pages/coupons/coupons')) return
    wx.navigateTo({ url: '/pages/coupons/coupons' })
  }
})
