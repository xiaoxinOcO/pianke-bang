const authService = require('../../services/auth')
const { normalizeAvatarUrl } = require('../../utils/format')

function getInitial(name) {
  const text = String(name || '').trim()
  return text ? text.slice(0, 1).toUpperCase() : '?'
}

function mapAccounts(accounts) {
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

Page({
  data: {
    phone: '',
    code: '',
    newPassword: '',
    confirmPassword: '',
    countdown: 0,
    sending: false,
    verifying: false,
    submitting: false,
    formError: '',
    verified: false,
    pickToken: '',
    accounts: [],
    selectedUserId: null,
    selectedUsername: ''
  },

  _timer: null,

  onUnload() {
    if (this._timer) clearInterval(this._timer)
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    const patch = { [field]: e.detail.value, formError: '' }
    if (field === 'phone' || field === 'code') {
      patch.verified = false
      patch.pickToken = ''
      patch.accounts = []
      patch.selectedUserId = null
      patch.selectedUsername = ''
    }
    this.setData(patch)
  },

  startCountdown() {
    if (this._timer) clearInterval(this._timer)
    this.setData({ countdown: 60 })
    this._timer = setInterval(() => {
      const n = this.data.countdown - 1
      if (n <= 0) {
        clearInterval(this._timer)
        this._timer = null
        this.setData({ countdown: 0 })
      } else {
        this.setData({ countdown: n })
      }
    }, 1000)
  },

  async sendCode() {
    const phone = this.data.phone.replace(/\D/g, '')
    if (!/^1\d{10}$/.test(phone)) {
      const msg = '请输入 11 位手机号'
      this.setData({ formError: msg })
      wx.showToast({ title: msg, icon: 'none' })
      return
    }
    if (this.data.sending || this.data.countdown > 0) return
    this.setData({ sending: true, formError: '' })
    try {
      const res = await authService.sendForgotPasswordSms(phone)
      wx.showToast({ title: res.message || '验证码已发送', icon: 'none' })
      this.startCountdown()
    } catch (e) {
      const msg = e.message || '发送失败'
      this.setData({ formError: msg })
      wx.showToast({ title: msg, icon: 'none', duration: 2800 })
    } finally {
      this.setData({ sending: false })
    }
  },

  async onVerify() {
    const phone = this.data.phone.replace(/\D/g, '')
    const code = this.data.code.trim()
    if (!/^1\d{10}$/.test(phone)) {
      this.setData({ formError: '请输入 11 位手机号' })
      return
    }
    if (!/^\d{4,8}$/.test(code)) {
      this.setData({ formError: '请输入短信验证码' })
      return
    }
    if (this.data.verifying) return
    this.setData({ verifying: true, formError: '' })
    try {
      const res = await authService.verifyForgotPasswordSms(phone, code)
      const accounts = mapAccounts(res.accounts)
      const needChoose = !!res.needChooseAccount || accounts.length > 1
      if (needChoose && accounts.length > 0) {
        this.setData({
          verified: true,
          pickToken: res.pickToken || '',
          accounts,
          selectedUserId: null,
          selectedUsername: '',
          verifying: false
        })
        return
      }
      const userId = res.userId != null ? Number(res.userId) : (accounts[0] && accounts[0].userId)
      const username = String(res.username || (accounts[0] && accounts[0].username) || '').trim()
      this.setData({
        verified: true,
        pickToken: res.pickToken || '',
        accounts: accounts.length ? accounts : [],
        selectedUserId: userId || null,
        selectedUsername: username,
        verifying: false
      })
    } catch (e) {
      const msg = e.message || '验证失败'
      this.setData({ verifying: false, formError: msg })
      wx.showToast({ title: msg, icon: 'none', duration: 2800 })
    }
  },

  onPickAccount(e) {
    const userId = Number(e.currentTarget.dataset.userId)
    const username = String(e.currentTarget.dataset.username || '').trim()
    if (!userId) return
    this.setData({
      selectedUserId: userId,
      selectedUsername: username,
      formError: ''
    })
  },

  clearAccountPick() {
    this.setData({
      selectedUserId: null,
      selectedUsername: '',
      newPassword: '',
      confirmPassword: '',
      formError: ''
    })
  },

  async onSubmit() {
    const { pickToken, selectedUserId, newPassword, confirmPassword, phone, code } = this.data
    if (!pickToken || !selectedUserId) {
      this.setData({ formError: '请先完成短信验证并选择账号' })
      return
    }
    if (!newPassword || newPassword.length < 6 || newPassword.length > 32) {
      this.setData({ formError: '新密码长度需在 6-32 位' })
      return
    }
    if (newPassword !== confirmPassword) {
      this.setData({ formError: '两次输入的新密码不一致' })
      return
    }
    if (this.data.submitting) return
    this.setData({ submitting: true, formError: '' })
    try {
      const res = await authService.resetPasswordBySms({
        phone: phone.replace(/\D/g, ''),
        code: code.trim(),
        pickToken,
        userId: selectedUserId,
        newPassword
      })
      wx.showToast({ title: res.message || '密码已重置', icon: 'success' })
      setTimeout(() => {
        wx.navigateBack({
          fail: () => wx.redirectTo({ url: '/pages/login/login' })
        })
      }, 800)
    } catch (e) {
      const msg = e.message || '重置失败'
      this.setData({ submitting: false, formError: msg })
      wx.showToast({ title: msg, icon: 'none', duration: 2800 })
    }
  },

  goLogin() {
    wx.navigateBack({
      fail: () => wx.redirectTo({ url: '/pages/login/login' })
    })
  }
})
