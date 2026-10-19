const authService = require('../../services/auth')
const { setToken, setUser, isLoggedIn } = require('../../utils/auth')
const { demoAccount } = require('../../config/index')
const {
  validateLoginPassword,
  validateRegister,
  validateSmsLogin
} = require('../../utils/form-validate')
const { normalizeAvatarUrl } = require('../../utils/format')
const realtime = require('../../services/realtime')

function getInitial(name) {
  const text = String(name || '').trim()
  return text ? text.slice(0, 1).toUpperCase() : '?'
}

function mapPickAccounts(accounts) {
  if (!Array.isArray(accounts)) return []
  return accounts.map((item) => {
    const username = String((item && item.username) || '').trim()
    return {
      userId: item && item.userId,
      username,
      avatarUrl: normalizeAvatarUrl(item && item.avatarUrl),
      avatarInitial: getInitial(username)
    }
  })
}

const TAB_PATHS = [
  '/pages/hall/hall',
  '/pages/publish/publish',
  '/pages/chat/chat',
  '/pages/profile/profile'
]

function navigateAfterLogin(redirect) {
  const target = redirect || '/pages/hall/hall'
  const path = target.split('?')[0]
  if (TAB_PATHS.includes(path)) {
    wx.switchTab({ url: path })
    return
  }
  wx.redirectTo({
    url: target,
    fail: () => wx.switchTab({ url: '/pages/hall/hall' })
  })
}

Page({
  data: {
    mode: 'login',
    loginType: 'password',
    username: '',
    password: '',
    phone: '',
    code: '',
    regUsername: '',
    regPassword: '',
    regRealName: '',
    regStudentId: '',
    regGender: '男',
    countdown: 0,
    submitting: false,
    pickAccounts: [],
    pickToken: '',
    redirect: '',
    demoAccount: null,
    formError: ''
  },

  onLoad(options) {
    this._navigating = false
    if (options.redirect) {
      this.setData({ redirect: decodeURIComponent(options.redirect) })
    }
    if (options.mode === 'register') {
      this.setData({ mode: 'register' })
    }
    if (demoAccount) {
      this.setData({
        demoAccount,
        username: demoAccount.username,
        password: demoAccount.password
      })
    }
    if (isLoggedIn()) {
      this._navigating = true
      navigateAfterLogin(this.data.redirect)
    }
  },

  switchMode(e) {
    this.setData({ mode: e.currentTarget.dataset.mode, formError: '' })
  },

  switchLoginType(e) {
    this.setData({ loginType: e.currentTarget.dataset.type, formError: '' })
  },

  goForgotPassword() {
    wx.navigateTo({ url: '/pages/forgot-password/forgot-password' })
  },

  fillDemoAccount() {
    const account = this.data.demoAccount
    if (!account) return
    this.setData({
      mode: 'login',
      loginType: 'password',
      username: account.username,
      password: account.password,
      formError: ''
    })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [field]: e.detail.value, formError: '' })
  },

  async sendCode() {
    const phone = this.data.phone.replace(/\D/g, '')
    if (!/^1\d{10}$/.test(phone)) {
      const msg = '请输入 11 位手机号'
      this.setData({ formError: msg })
      wx.showToast({ title: msg, icon: 'none', duration: 2800 })
      return
    }
    try {
      const api = this.data.mode === 'register' ? authService.sendSms : authService.sendLoginSms
      const res = await api(phone)
      wx.showToast({ title: res.message || '验证码已发送', icon: 'none' })
      this.startCountdown()
    } catch (e) {
      const msg = e.message || '发送失败'
      this.setData({ formError: msg })
      wx.showToast({ title: msg, icon: 'none', duration: 2800 })
    }
  },

  startCountdown() {
    this.setData({ countdown: 60 })
    const timer = setInterval(() => {
      const n = this.data.countdown - 1
      if (n <= 0) {
        clearInterval(timer)
        this.setData({ countdown: 0 })
      } else {
        this.setData({ countdown: n })
      }
    }, 1000)
  },

  async onLogin() {
    if (this.data.submitting || this._navigating) return

    let error = ''
    if (this.data.loginType === 'password') {
      error = validateLoginPassword(this.data)
    } else {
      error = validateSmsLogin(this.data)
    }
    if (error) {
      this.setData({ formError: error })
      return
    }

    this.setData({ submitting: true, formError: '' })
    try {
      let res
      if (this.data.loginType === 'password') {
        res = await authService.login(this.data.username.trim(), this.data.password)
      } else {
        const phone = this.data.phone.replace(/\D/g, '')
        res = await authService.loginBySms(phone, this.data.code.trim())
        if (res.pickToken && res.accounts) {
          this.setData({
            pickToken: res.pickToken,
            pickAccounts: mapPickAccounts(res.accounts),
            submitting: false
          })
          return
        }
      }
      await this.handleLoginSuccess(res)
    } catch (e) {
      this._navigating = false
      const msg = e.message || '登录失败'
      this.setData({ submitting: false, formError: msg })
      wx.showToast({ title: msg, icon: 'none', duration: 2800 })
    }
  },

  async onPickAccount(e) {
    const userId = Number(e.currentTarget.dataset.userId)
    if (!userId || !this.data.pickToken) {
      this.setData({ formError: '请重新获取验证码后再选择账号' })
      return
    }
    this.setData({ submitting: true, formError: '' })
    try {
      const res = await authService.confirmLoginBySms(this.data.pickToken, userId)
      await this.handleLoginSuccess(res)
    } catch (e) {
      this._navigating = false
      const msg = e.message || '登录失败'
      this.setData({ submitting: false, formError: msg })
      wx.showToast({ title: msg, icon: 'none', duration: 2800 })
    }
  },

  async onRegister() {
    if (this.data.submitting || this._navigating) return

    const error = validateRegister(this.data)
    if (error) {
      this.setData({ formError: error })
      return
    }

    const phone = this.data.phone.replace(/\D/g, '')
    this.setData({ submitting: true, formError: '' })
    try {
      const res = await authService.registerBySms({
        username: this.data.regUsername.trim(),
        password: this.data.regPassword,
        phone,
        code: this.data.code.trim(),
        realName: this.data.regRealName.trim(),
        studentId: this.data.regStudentId.trim(),
        gender: this.data.regGender
      })
      await this.handleLoginSuccess(res)
    } catch (e) {
      this._navigating = false
      const msg = e.message || '注册失败'
      this.setData({ submitting: false, formError: msg })
      wx.showToast({ title: msg, icon: 'none', duration: 2800 })
    }
  },

  async handleLoginSuccess(res) {
    if (this._navigating) return
    if (!res.token) throw new Error('登录成功但未返回 token')

    this._navigating = true
    setToken(res.token)

    const me = await authService.fetchMe()
    setUser(me)
    getApp().setUser(me)
    realtime.seedProfileSnapshot()

    navigateAfterLogin(this.data.redirect)
  },

  onSkip() {
    wx.navigateBack({
      fail: () => wx.switchTab({ url: '/pages/hall/hall' })
    })
  }
})
