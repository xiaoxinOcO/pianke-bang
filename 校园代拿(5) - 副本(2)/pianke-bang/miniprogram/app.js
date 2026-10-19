const { brandName, brandSlogan } = require('./config/index')
const realtime = require('./services/realtime')
const { isLoggedIn, beginLocalLogout } = require('./utils/auth')

App({
  globalData: {
    user: null,
    brandName,
    brandSlogan,
    chatUnread: 0,
    activeChatTarget: '',
    pendingNotify: null,
    notifyListeners: []
  },

  onLaunch() {
    const token = wx.getStorageSync('campus_token')
    const user = wx.getStorageSync('campus_user')
    if (token && user) {
      this.globalData.user = user
      realtime.start()
    }
  },

  onShow() {
    if (isLoggedIn()) {
      realtime.start()
      realtime.refresh()
    } else {
      realtime.stop()
    }
  },

  setUser(user) {
    this.globalData.user = user
    wx.setStorageSync('campus_user', user)
    realtime.start()
  },

  clearUser() {
    // 先停轮询并抬升会话代数，避免退出后尾巴请求刷 401
    realtime.stop()
    beginLocalLogout()
    this.globalData.user = null
    this.globalData.activeChatTarget = ''
    this.globalData.chatUnread = 0
  },

  registerNotifyListener(fn) {
    if (typeof fn !== 'function') return
    if (!this.globalData.notifyListeners.includes(fn)) {
      this.globalData.notifyListeners.push(fn)
    }
  },

  unregisterNotifyListener(fn) {
    this.globalData.notifyListeners = (this.globalData.notifyListeners || [])
      .filter(item => item !== fn)
  },

  emitNotify(payload) {
    this.globalData.pendingNotify = payload
    const listeners = this.globalData.notifyListeners || []
    listeners.forEach(fn => {
      try { fn(payload) } catch (e) { /* ignore */ }
    })
  },

  updateTabBarBadge() {
    const pages = getCurrentPages()
    const page = pages[pages.length - 1]
    if (page && typeof page.getTabBar === 'function') {
      const tabBar = page.getTabBar()
      if (tabBar && typeof tabBar.setData === 'function') {
        tabBar.setData({ chatBadge: this.globalData.chatUnread || 0 })
      }
    }
  }
})
