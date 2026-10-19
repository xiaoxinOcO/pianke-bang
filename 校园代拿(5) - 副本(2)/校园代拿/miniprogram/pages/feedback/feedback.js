const noticeService = require('../../services/notice')
const { useLoginGuard, checkLoginGuard } = require('../../utils/auth')

Page({
  data: {
    feedbackType: 'suggest',
    content: '',
    submitting: false
  },

  onLoad() {
    useLoginGuard(this, '/pages/feedback/feedback')
  },

  onShow() {
    checkLoginGuard(this)
  },

  onTypeChange(e) {
    this.setData({ feedbackType: e.detail.value })
  },

  onInput(e) {
    this.setData({ content: e.detail.value })
  },

  async onSubmit() {
    if (!this.data.content.trim()) {
      wx.showToast({ title: '请填写反馈内容', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      await noticeService.submitFeedback({
        feedbackType: this.data.feedbackType,
        pageName: 'miniprogram-feedback',
        content: this.data.content.trim(),
        images: []
      })
      wx.showToast({ title: '感谢反馈', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 600)
    } catch (e) {
      wx.showToast({ title: e.message || '提交失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
