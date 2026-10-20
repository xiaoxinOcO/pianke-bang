const noticeService = require('../../services/notice')
const { formatDateTime } = require('../../utils/format')
const { openMiniprogramPath } = require('../../utils/navigate')
const { useLoginGuard, checkLoginGuard, requireLogin } = require('../../utils/auth')

Page({
  data: {
    notices: []
  },

  onLoad() {
    useLoginGuard(this, '/pages/notices/notices')
  },

  onShow() {
    if (!checkLoginGuard(this)) return
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
  },

  onNoticeTap(e) {
    const id = Number(e.currentTarget.dataset.id)
    const notice = (this.data.notices || []).find((n) => Number(n.id) === id)
    if (!notice || !notice.linkEnabled || !notice.linkPath) return
    if (!requireLogin(notice.linkPath)) return
    openMiniprogramPath(notice.linkPath)
  }
})
