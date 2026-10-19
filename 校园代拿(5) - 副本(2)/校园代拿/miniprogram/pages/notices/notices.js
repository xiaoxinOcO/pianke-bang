const noticeService = require('../../services/notice')
const { formatDateTime } = require('../../utils/format')

Page({
  data: {
    notices: []
  },

  onLoad() {
    this.loadNotices()
  },

  async loadNotices() {
    try {
      const list = await noticeService.fetchNotices()
      const notices = (Array.isArray(list) ? list : []).map(n => ({
        ...n,
        timeText: formatDateTime(n.updatedAt || n.createdAt)
      }))
      this.setData({ notices })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  }
})
