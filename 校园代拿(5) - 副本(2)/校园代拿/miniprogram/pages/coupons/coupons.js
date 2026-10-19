const couponService = require('../../services/coupon')
const { isLoggedIn, requireLogin } = require('../../utils/auth')

const STATUS_TEXT = {
  UNUSED: '未使用',
  LOCKED: '占用中',
  USED: '已使用',
  EXPIRED: '已过期'
}

function formatCouponDesc(item) {
  const type = item.type
  if (type === 'FIXED') {
    return `立减 ¥${Number(item.discountAmount || 0).toFixed(2)}`
  }
  if (type === 'THRESHOLD') {
    return `满 ¥${Number(item.minSpend || 0).toFixed(2)} 减 ¥${Number(item.discountAmount || 0).toFixed(2)}`
  }
  if (type === 'PERCENT') {
    const p = Number(item.discountPercent || 0)
    const zhe = (p * 10).toFixed(1).replace(/\.0$/, '')
    return `${zhe} 折优惠`
  }
  return '赏金抵扣券'
}

function formatExpire(expireAt) {
  if (!expireAt) return ''
  const s = String(expireAt).replace('T', ' ')
  return s.length >= 16 ? s.slice(0, 16) : s
}

Page({
  data: {
    tabs: [
      { value: '', label: '全部' },
      { value: 'UNUSED', label: '可用' },
      { value: 'USED', label: '已用' },
      { value: 'EXPIRED', label: '过期' }
    ],
    statusFilter: 'UNUSED',
    list: [],
    loading: false
  },

  onShow() {
    if (!isLoggedIn()) {
      requireLogin()
      return
    }
    this.loadList()
  },

  onPullDownRefresh() {
    this.loadList().finally(() => wx.stopPullDownRefresh())
  },

  goCenter() {
    wx.navigateTo({ url: '/pages/coupon-center/coupon-center' })
  },

  onTab(e) {
    const value = e.currentTarget.dataset.value
    if (value === this.data.statusFilter) return
    this.setData({ statusFilter: value })
    this.loadList()
  },

  async loadList() {
    if (!isLoggedIn()) return
    this.setData({ loading: true })
    try {
      const rows = await couponService.fetchMine(this.data.statusFilter || undefined)
      const list = (rows || []).map((item) => ({
        ...item,
        descText: formatCouponDesc(item),
        expireText: formatExpire(item.expireAt),
        statusText: STATUS_TEXT[item.status] || item.status
      }))
      this.setData({ list })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  }
})
