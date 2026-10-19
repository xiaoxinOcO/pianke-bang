const { post } = require('../../utils/http')
const { isLoggedIn, getUser, getToken, setToken, setUser } = require('../../utils/auth')

Page({
  data: {
    ticket: '',
    submitting: false,
    done: false,
    needLogin: false,
    error: '',
    info: '',
    username: ''
  },

  onLoad(options) {
    let ticket = ''
    if (options && options.ticket) {
      ticket = String(options.ticket).trim()
    } else if (options && options.scene) {
      try {
        ticket = decodeURIComponent(String(options.scene)).trim()
      } catch (e) {
        ticket = String(options.scene).trim()
      }
    }
    if (!ticket) {
      this.setData({ error: '缺少扫码凭证，请回到电脑端刷新二维码后重试' })
      return
    }
    this.setData({ ticket })
    this.refreshLoginState()
  },

  onShow() {
    if (this.data.ticket) {
      this.refreshLoginState()
    }
  },

  refreshLoginState() {
    if (!this.data.ticket) return
    if (isLoggedIn()) {
      const user = getUser() || {}
      this.setData({
        needLogin: false,
        username: String(user.username || '').trim(),
        info: '请确认是否本人在电脑上登录片刻帮',
        error: ''
      })
    } else {
      this.setData({
        needLogin: true,
        username: '',
        info: '请先登录小程序，再确认电脑端登录'
      })
    }
  },

  goLogin() {
    const redirect = `/pages/scan-login/scan-login?ticket=${encodeURIComponent(this.data.ticket)}`
    wx.navigateTo({
      url: `/pages/login/login?redirect=${encodeURIComponent(redirect)}`
    })
  },

  async onConfirmLogin() {
    if (this.data.submitting || !this.data.ticket) return
    if (!isLoggedIn() || !getToken()) {
      this.setData({ needLogin: true, error: '请先登录小程序' })
      return
    }
    this.setData({ submitting: true, error: '' })
    try {
      const res = await post('/auth/wechat/web/confirm-login', {
        sessionId: this.data.ticket
      })
      if (res.status === 'SUCCESS') {
        // 后端会换发小程序专用会话，必须立刻保存，否则会被网页登录挤掉
        const mpToken = res.mpToken || ''
        if (mpToken) {
          setToken(mpToken)
          const user = getUser() || {}
          if (res.username) {
            user.username = res.username
            setUser(user)
          }
          try {
            const app = getApp()
            if (app && app.globalData) {
              app.globalData.token = mpToken
              if (res.username) {
                app.globalData.user = Object.assign({}, app.globalData.user || {}, { username: res.username })
              }
            }
          } catch (e) { /* ignore */ }
        }
        this.setData({
          done: true,
          submitting: false,
          info: '电脑端已登录成功，可返回微信'
        })
        return
      }
      throw new Error(res.message || '确认失败')
    } catch (err) {
      this.setData({
        submitting: false,
        error: (err && err.message) || '确认登录失败'
      })
    }
  },

  onCancel() {
    wx.navigateBack({
      fail: () => wx.switchTab({ url: '/pages/hall/hall' })
    })
  }
})
