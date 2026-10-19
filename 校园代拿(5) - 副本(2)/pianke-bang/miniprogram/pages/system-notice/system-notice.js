const noticeService = require('../../services/notice')
const { useLoginGuard, checkLoginGuard } = require('../../utils/auth')
const { formatDateTime } = require('../../utils/format')
const realtime = require('../../services/realtime')

/** 订单/任务类通知可进详情；充值等纯钱包消息不跳转 */
function resolveTaskId(item) {
  const refId = Number(item && item.refId)
  if (!refId) return 0
  const category = String((item && item.category) || '').toUpperCase()
  const eventType = String((item && item.eventType) || '').toUpperCase()
  if (category === 'TASK') return refId
  if (['TASK_INCOME', 'TASK_BOUNTY_PAID'].includes(eventType)) return refId
  return 0
}

Page({
  data: {
    messages: [],
    unreadCount: 0,
    loading: false
  },

  onLoad() {
    useLoginGuard(this, '/pages/system-notice/system-notice')
  },

  onShow() {
    if (!checkLoginGuard(this)) return
    this.loadMessages(true)
  },

  async loadMessages(markRead) {
    this.setData({ loading: true })
    try {
      const res = await noticeService.fetchSystemMessages()
      const messages = (res.messages || []).map(item => {
        const taskId = resolveTaskId(item)
        return {
          ...item,
          timeText: formatDateTime(item.createdAt),
          taskId,
          canOpenTask: !!taskId
        }
      })
      this.setData({
        messages,
        unreadCount: Number(res.unreadCount || 0)
      })
      if (markRead && messages.length > 0) {
        await noticeService.markSystemMessagesRead()
        realtime.refresh()
      }
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  onOpenNotice(e) {
    const taskId = Number(e.currentTarget.dataset.taskId || 0)
    if (!taskId) return
    wx.navigateTo({
      url: `/pages/task-detail/task-detail?id=${taskId}`
    })
  }
})
