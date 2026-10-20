const taskService = require('../../services/task')
const noticeService = require('../../services/notice')
const hallActivityService = require('../../services/hallActivity')
const couponService = require('../../services/coupon')
const authService = require('../../services/auth')
const { isLoggedIn, getUser, requireLogin } = require('../../utils/auth')
const { resolveUserAvatar } = require('../../utils/format')
const realtime = require('../../services/realtime')

Page({
  data: {
    keyword: '',
    taskType: '',
    status: 'OPEN',
    tasks: [],
    notices: [],
    topNotice: null,
    banners: [],
    loading: false,
    page: 0,
    totalPages: 1,
    taskTypes: [
      { value: '', label: '全部' },
      { value: 'EXPRESS', label: '快递代取' },
      { value: 'FOOD', label: '外卖代拿' },
      { value: 'PRINT', label: '打印代送' },
      { value: 'QUEUE', label: '排队代办' },
      { value: 'OTHER', label: '其他' }
    ],
    isLoggedIn: false,
    myName: ''
  },

  onLoad() {
    this._avatarCache = {}
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      const app = getApp()
      this.getTabBar().setData({
        selected: 0,
        chatBadge: app.globalData.chatUnread || 0
      })
    }
    const user = getUser()
    this.setData({
      isLoggedIn: isLoggedIn(),
      myName: user ? user.username : ''
    })
    this.loadNotices()
    this.loadBanners()
    this.loadTasks(true)
    if (isLoggedIn()) realtime.refresh()
  },

  async attachPublisherAvatars(records) {
    const list = Array.isArray(records) ? records : []
    if (!this._avatarCache) this._avatarCache = {}
    const names = [...new Set(list.map(t => t.publisherName).filter(Boolean))]
    const missing = names.filter(n => this._avatarCache[n] === undefined)
    await Promise.all(missing.map(async (name) => {
      try {
        const profile = await authService.fetchPublicProfile(name)
        this._avatarCache[name] = resolveUserAvatar(profile) || ''
      } catch (e) {
        this._avatarCache[name] = ''
      }
    }))
    return list.map(t => ({
      ...t,
      publisherAvatarUrl: this._avatarCache[t.publisherName] || ''
    }))
  },

  onPullDownRefresh() {
    Promise.all([this.loadNotices(), this.loadBanners(), this.loadTasks(true)]).finally(() => {
      wx.stopPullDownRefresh()
    })
  },

  async loadNotices() {
    try {
      const notices = await noticeService.fetchNotices()
      const list = Array.isArray(notices) ? notices : []
      const topNotice = list.find(n => n.pinned) || list[0] || null
      this.setData({ notices: list, topNotice })
    } catch (e) {
      // 公告加载失败不阻塞大厅
    }
  },

  normalizeBanner(row) {
    const item = row || {}
    let actionType = String(item.actionType || item.action || '').toUpperCase()
    // 旧优惠券活动接口兼容
    if (!actionType || actionType === 'CLAIM_TEMPLATE' || actionType === 'CLAIM_NEWCOMER') {
      actionType = 'COUPON_CLAIM'
    }
    const templateId = item.templateId || item.couponTemplateId || null
    const ctaText = item.ctaText
      || (actionType === 'RIDER_APPLY' ? '立即认证'
        : actionType === 'PUBLISH' ? '去发布'
          : actionType === 'WALLET' ? '看钱包'
            : actionType === 'COUPON_CENTER' ? '去领券'
              : actionType === 'COUPON_CLAIM' ? '去领取' : '去看看')
    return {
      ...item,
      actionType,
      templateId,
      couponTemplateId: templateId,
      ctaText,
      claimed: !!item.claimed
    }
  },

  async loadBanners() {
    try {
      const rows = await hallActivityService.fetchHallActivities()
      const banners = (Array.isArray(rows) ? rows : []).map((r) => this.normalizeBanner(r))
      this.setData({ banners })
    } catch (e) {
      try {
        const rows = await couponService.fetchActivities()
        const banners = (Array.isArray(rows) ? rows : []).map((r) => this.normalizeBanner(r))
        this.setData({ banners })
      } catch (err) {
        this.setData({ banners: [] })
      }
    }
  },

  onBannerTap(e) {
    // 用下标取完整活动数据，避免 swiper 内 data-* 丢字段导致全进领券页
    const index = Number(e.currentTarget.dataset.index)
    const item = (this.data.banners || [])[index]
    if (!item) return
    const actionType = String(item.actionType || '').toUpperCase()
    const actionValue = String(item.actionValue || '')
    const templateId = item.templateId || item.couponTemplateId
    const claimed = !!item.claimed

    if (actionType === 'RIDER_APPLY') {
      if (!requireLogin()) return
      wx.navigateTo({ url: '/pages/rider-apply/rider-apply' })
      return
    }
    if (actionType === 'PUBLISH') {
      wx.switchTab({ url: '/pages/publish/publish' })
      return
    }
    if (actionType === 'WALLET') {
      if (!requireLogin()) return
      wx.navigateTo({ url: '/pages/wallet/wallet' })
      return
    }
    if (actionType === 'COUPON_CENTER') {
      wx.navigateTo({ url: '/pages/coupon-center/coupon-center' })
      return
    }
    if (actionType === 'CUSTOM_PATH' && actionValue) {
      const path = actionValue.startsWith('/') ? actionValue : `/${actionValue}`
      const pure = path.split('?')[0]
      if (
        pure === '/pages/publish/publish'
        || pure === '/pages/hall/hall'
        || pure === '/pages/chat/chat'
        || pure === '/pages/profile/profile'
      ) {
        wx.switchTab({ url: pure })
      } else {
        wx.navigateTo({ url: path })
      }
      return
    }
    if (actionType === 'COUPON_CLAIM') {
      if (claimed) {
        if (!requireLogin()) return
        wx.navigateTo({ url: '/pages/coupons/coupons' })
        return
      }
      if (!requireLogin()) return
      const qs = templateId ? `?focus=template-${templateId}` : ''
      wx.navigateTo({ url: `/pages/coupon-center/coupon-center${qs}` })
      return
    }
    wx.showToast({ title: '活动暂不可用', icon: 'none' })
  },

  async loadTasks(reset) {
    if (this.data.loading) return
    const page = reset ? 0 : this.data.page
    this.setData({ loading: true })
    try {
      const params = {
        page: page + 1,
        size: 10,
        status: this.data.status,
        taskType: this.data.taskType,
        keyword: this.data.keyword
      }
      const res = await taskService.fetchTasks(params)
      const records = await this.attachPublisherAvatars(res.records || [])
      this.setData({
        tasks: reset ? records : this.data.tasks.concat(records),
        page,
        totalPages: res.totalPages || 1
      })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearch() {
    this.loadTasks(true)
  },

  onTypeTap(e) {
    this.setData({ taskType: e.currentTarget.dataset.value })
    this.loadTasks(true)
  },

  onTaskTap(e) {
    const task = e.detail && e.detail.task
    if (!task || !task.id) return
    wx.navigateTo({ url: `/pages/task-detail/task-detail?id=${task.id}` })
  },

  async onClaim(e) {
    if (!requireLogin()) return
    const id = e.detail.id
    wx.showLoading({ title: '接单中', mask: true })
    try {
      await taskService.claimTask(id)
      wx.hideLoading()
      wx.showToast({ title: '接单成功', icon: 'success', duration: 2000 })
      this.loadTasks(true)
      realtime.refresh()
    } catch (err) {
      wx.hideLoading()
      const msg = (err && err.message) || '接单失败'
      // 长提示用弹窗，避免 wx.showToast 默认 1.5s 闪没且看不全
      if (/接单员|认证|申请/.test(msg) || msg.length > 14) {
        wx.showModal({
          title: '暂时无法接单',
          content: msg,
          confirmText: /接单员|申请/.test(msg) ? '去申请' : '知道了',
          cancelText: '关闭',
          showCancel: /接单员|申请/.test(msg),
          success: (res) => {
            if (res.confirm && /接单员|申请/.test(msg)) {
              wx.navigateTo({ url: '/pages/rider-apply/rider-apply' })
            }
          }
        })
      } else {
        wx.showToast({ title: msg, icon: 'none', duration: 3500 })
      }
    }
  },

  goNotices() {
    const notice = this.data.topNotice
    const linked = !!(notice && notice.linkEnabled && notice.linkPath)
    const target = linked ? String(notice.linkPath) : '/pages/notices/notices'
    if (!requireLogin(target.startsWith('/') ? target : '/pages/notices/notices')) return
    if (linked) {
      const { openMiniprogramPath } = require('../../utils/navigate')
      if (openMiniprogramPath(notice.linkPath)) return
    }
    wx.navigateTo({ url: '/pages/notices/notices' })
  },

  goMine() {
    if (!requireLogin()) return
    wx.navigateTo({ url: '/pages/mine/mine' })
  },

  onReachBottom() {
    if (this.data.page + 1 < this.data.totalPages) {
      this.setData({ page: this.data.page + 1 })
      this.loadTasks(false)
    }
  }
})
