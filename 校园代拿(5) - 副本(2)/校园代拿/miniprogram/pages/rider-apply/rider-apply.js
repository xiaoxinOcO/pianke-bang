const authService = require('../../services/auth')
const { getUser, setUser, useLoginGuard, checkLoginGuard } = require('../../utils/auth')
const { validateRiderApply } = require('../../utils/form-validate')
const realtime = require('../../services/realtime')

Page({
  data: {
    realName: '',
    studentId: '',
    phone: '',
    reason: '',
    user: null,
    submitting: false,
    formError: ''
  },

  onLoad() {
    useLoginGuard(this, '/pages/rider-apply/rider-apply')
  },

  onShow() {
    if (!checkLoginGuard(this)) return
    const user = getUser()
    this.setData({
      user,
      phone: user && user.phone ? user.phone : this.data.phone
    })
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({
      [field]: e.detail.value,
      formError: ''
    })
  },

  async onSubmit() {
    const error = validateRiderApply(this.data)
    if (error) {
      this.setData({ formError: error })
      return
    }

    const { realName, studentId, phone, reason } = this.data
    this.setData({ submitting: true, formError: '' })
    try {
      const res = await authService.applyRider({
        realName: realName.trim(),
        studentId: studentId.trim(),
        phone: phone.replace(/\D/g, ''),
        reason: reason.trim()
      })

      const user = getUser() || {}
      const nextUser = {
        ...user,
        riderApplicationPending: true,
        riderApproved: !!res.riderApproved
      }
      setUser(nextUser)
      getApp().setUser(nextUser)
      realtime.seedProfileSnapshot()

      getApp().emitNotify({
        kind: 'system',
        title: '申请已提交',
        message: res.message || '接单员申请已提交，请等待管理员审核',
        navigate: '/pages/rider-apply/rider-apply'
      })

      wx.showToast({ title: '申请已提交', icon: 'success' })
      this.setData({ user: nextUser })
    } catch (e) {
      const message = e.message || '申请失败'
      this.setData({ formError: message })
      wx.showToast({ title: message, icon: 'none', duration: 2800 })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
