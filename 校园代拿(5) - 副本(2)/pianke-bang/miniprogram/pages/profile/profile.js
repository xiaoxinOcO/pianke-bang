const authService = require('../../services/auth')
const { getUser, isLoggedIn, requireLogin, setToken, setUser } = require('../../utils/auth')
const { resolveUserAvatar } = require('../../utils/format')

Page({
  data: {
    user: null,
    isLoggedIn: false,
    avatarUrl: '',
    avatarPending: false,
    uploadingAvatar: false
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      const app = getApp()
      this.getTabBar().setData({
        selected: 3,
        chatBadge: app.globalData.chatUnread || 0
      })
    }
    const loggedIn = isLoggedIn()
    const user = getUser()
    this.applyUserState(user, loggedIn)
    if (loggedIn) this.refreshMe()
  },

  applyUserState(user, loggedIn) {
    this.setData({
      isLoggedIn: loggedIn,
      user,
      avatarUrl: resolveUserAvatar(user),
      avatarPending: !!(user && user.avatarPending)
    })
  },

  async refreshMe() {
    try {
      const me = await authService.fetchMe()
      getApp().setUser(me)
      this.applyUserState(me, true)
    } catch (e) { /* ignore */ }
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },

  onChangeAvatar() {
    if (!isLoggedIn() || this.data.uploadingAvatar) return
    wx.showActionSheet({
      itemList: ['从相册选择', '拍照'],
      success: (res) => {
        const sourceType = res.tapIndex === 1 ? ['camera'] : ['album']
        this.pickAvatar(sourceType)
      }
    })
  },

  pickAvatar(sourceType) {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType,
      success: (res) => {
        const file = (res.tempFiles || [])[0]
        if (!file) return
        this.prepareAvatar(file.tempFilePath)
      }
    })
  },

  prepareAvatar(path) {
    wx.compressImage({
      src: path,
      quality: 70,
      success: (res) => this.uploadAvatar(res.tempFilePath),
      fail: () => this.uploadAvatar(path)
    })
  },

  fileToBase64(path) {
    return new Promise((resolve, reject) => {
      wx.getFileSystemManager().readFile({
        filePath: path,
        encoding: 'base64',
        success: (res) => resolve('data:image/jpeg;base64,' + res.data),
        fail: reject
      })
    })
  },

  async uploadAvatar(path) {
    const user = getUser()
    if (!user || !user.username) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      return
    }

    this.setData({ uploadingAvatar: true })
    wx.showLoading({ title: '上传中', mask: true })
    try {
      const avatarUrl = await this.fileToBase64(path)
      const res = await authService.updateProfile({
        username: user.username,
        avatarUrl
      })
      this.applyProfileResponse(res)
      wx.showToast({
        title: res.avatarPending ? '头像已提交审核' : '头像更新成功',
        icon: 'none'
      })
    } catch (e) {
      wx.showToast({ title: e.message || '头像上传失败', icon: 'none' })
    } finally {
      wx.hideLoading()
      this.setData({ uploadingAvatar: false })
    }
  },

  applyProfileResponse(res) {
    if (res.token) setToken(res.token)
    const current = getUser() || {}
    const nextUser = {
      ...current,
      username: res.username || current.username,
      avatarUrl: res.avatarUrl || current.avatarUrl,
      pendingAvatarUrl: res.pendingAvatarUrl || '',
      avatarPending: !!res.avatarPending,
      riderApproved: res.riderApproved !== undefined ? res.riderApproved : current.riderApproved,
      riderApplicationPending: res.riderApplicationPending !== undefined
        ? res.riderApplicationPending
        : current.riderApplicationPending
    }
    setUser(nextUser)
    getApp().setUser(nextUser)
    this.applyUserState(nextUser, true)
  },

  goWallet() {
    if (!requireLogin('/pages/wallet/wallet')) return
    wx.navigateTo({ url: '/pages/wallet/wallet' })
  },

  goCoupons() {
    if (!requireLogin('/pages/coupons/coupons')) return
    wx.navigateTo({ url: '/pages/coupons/coupons' })
  },

  goMine() {
    if (!requireLogin('/pages/mine/mine')) return
    wx.navigateTo({ url: '/pages/mine/mine' })
  },

  goRiderApply() {
    if (!requireLogin('/pages/rider-apply/rider-apply')) return
    wx.navigateTo({ url: '/pages/rider-apply/rider-apply' })
  },

  goFeedback() {
    if (!requireLogin('/pages/feedback/feedback')) return
    wx.navigateTo({ url: '/pages/feedback/feedback' })
  },

  goSupport() {
    if (!requireLogin('/pages/support/support')) return
    wx.navigateTo({ url: '/pages/support/support' })
  },

  goNotices() {
    if (!requireLogin('/pages/notices/notices')) return
    wx.navigateTo({ url: '/pages/notices/notices' })
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出吗？',
      success: (res) => {
        if (res.confirm) {
          getApp().clearUser()
          this.applyUserState(null, false)
          if (typeof this.getTabBar === 'function' && this.getTabBar()) {
            this.getTabBar().setData({ chatBadge: 0 })
          }
          wx.showToast({ title: '已退出', icon: 'none' })
        }
      }
    })
  }
})
