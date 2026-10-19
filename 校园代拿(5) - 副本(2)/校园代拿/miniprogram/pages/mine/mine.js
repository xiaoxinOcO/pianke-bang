const taskService = require('../../services/task')
const realtime = require('../../services/realtime')
const { getUser, useLoginGuard, checkLoginGuard, requireLogin } = require('../../utils/auth')
const { resolveStatusLabel, statusClass, taskTypeLabel, taskTypeBadgeClass } = require('../../utils/task-status')
const { formatTimeAgo, formatMoney, maskName } = require('../../utils/format')

const STATUS_TABS = [
  { key: 'all', label: '全部' },
  { key: 'active', label: '进行中' },
  { key: 'confirm', label: '待确认' },
  { key: 'done', label: '已完成' },
  { key: 'closed', label: '已关闭' }
]

const ACTIVE = {
  OPEN: true,
  CLAIMED: true,
  PENDING_REVIEW: true,
  OVERDUE: true
}

function shortAddr(text, max = 14) {
  const s = String(text || '').trim()
  if (!s) return '未填写'
  return s.length > max ? s.slice(0, max) + '…' : s
}

function matchStatusFilter(status, filter) {
  if (filter === 'all') return true
  if (filter === 'active') return !!ACTIVE[status]
  if (filter === 'confirm') return status === 'PENDING_CONFIRMATION'
  if (filter === 'done') return status === 'COMPLETED'
  if (filter === 'closed') return status === 'CANCELLED' || status === 'REJECTED'
  return true
}

Page({
  data: {
    tab: 'published',
    statusFilter: 'all',
    statusTabs: STATUS_TABS,
    tasks: [],
    displayTasks: [],
    loading: false,
    myName: ''
  },

  onLoad() {
    useLoginGuard(this, '/pages/mine/mine')
  },

  onShow() {
    if (!checkLoginGuard(this)) return
    const user = getUser()
    this.setData({ myName: user ? user.username : '' })
    this.loadTasks()
  },

  onTabChange(e) {
    this.setData({
      tab: e.currentTarget.dataset.tab,
      statusFilter: 'all'
    })
    this.loadTasks()
  },

  onStatusFilter(e) {
    const key = e.currentTarget.dataset.key
    this.setData({ statusFilter: key })
    this.applyFilter()
  },

  decorateTask(t, tab, myName) {
    const isPublished = tab === 'published'
    const personText = isPublished
      ? (t.claimerName ? `接单 ${maskName(t.claimerName)}` : '待接单')
      : `发布 ${maskName(t.publisherName)}`

    const canConfirm = t.status === 'PENDING_CONFIRMATION' && t.publisherName === myName
    const canComplete = t.status === 'CLAIMED' && t.claimerName === myName
    const canCancel = ['OPEN', 'CLAIMED', 'PENDING_REVIEW'].includes(t.status) &&
      (t.publisherName === myName || t.claimerName === myName)
    const canChat = !!(t.claimerName && t.publisherName) &&
      ['CLAIMED', 'PENDING_CONFIRMATION', 'OVERDUE', 'COMPLETED'].includes(t.status)
    const canRepublish = isPublished && (t.status === 'COMPLETED' || t.status === 'CANCELLED' || t.status === 'REJECTED')
    const urgentOn = t.urgentEnabled === true || t.urgentEnabled === 1 || t.urgentEnabled === 'true'

    return {
      ...t,
      statusLabel: resolveStatusLabel(t),
      statusCls: statusClass(t.status),
      typeLabel: taskTypeLabel(t.taskType),
      typeCls: taskTypeBadgeClass(t.taskType),
      rewardText: formatMoney(t.reward),
      urgentTag: urgentOn ? '加急' : '',
      timeText: formatTimeAgo(t.createdAt),
      personText,
      pickupShort: shortAddr(t.pickupLocation),
      dropoffShort: shortAddr(t.dropoffLocation),
      canConfirm,
      canComplete,
      canCancel,
      canChat,
      canRepublish
    }
  },

  async loadTasks() {
    this.setData({ loading: true })
    try {
      const res = await taskService.fetchMyTasks()
      const all = Array.isArray(res) ? res : (res.records || [])
      const myName = this.data.myName
      const tab = this.data.tab
      const filtered = tab === 'published'
        ? all.filter(t => t.publisherName === myName)
        : all.filter(t => t.claimerName === myName)
      const tasks = filtered.map(t => this.decorateTask(t, tab, myName))
      this.setData({ tasks })
      this.applyFilter()
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  applyFilter() {
    const { tasks, statusFilter } = this.data
    const displayTasks = tasks.filter(t => matchStatusFilter(t.status, statusFilter))
    this.setData({ displayTasks })
  },

  findTask(id) {
    return this.data.tasks.find(t => t.id === Number(id))
  },

  noop() {},

  openDetail(e) {
    const id = e.currentTarget.dataset.id
    if (id) wx.navigateTo({ url: `/pages/task-detail/task-detail?id=${id}` })
  },

  openDetailBtn(e) {
    const id = e.currentTarget.dataset.id
    if (id) wx.navigateTo({ url: `/pages/task-detail/task-detail?id=${id}` })
  },

  async doAction(action, id) {
    if (!requireLogin()) return
    wx.showLoading({ title: '处理中', mask: true })
    try {
      if (action === 'complete') await taskService.completeTask(id)
      else if (action === 'confirm') await taskService.confirmTask(id)
      else if (action === 'cancel') await taskService.cancelTask(id)
      wx.hideLoading()
      if (action === 'confirm') {
        wx.showToast({ title: '确认成功', icon: 'success', duration: 1000 })
        this.loadTasks()
        if (realtime && realtime.refresh) realtime.refresh()
        setTimeout(() => {
          wx.navigateTo({
            url: `/pages/task-detail/task-detail?id=${id}&rate=1`
          })
        }, 400)
        return
      }
      wx.showToast({ title: '操作成功', icon: 'success' })
      this.loadTasks()
      if (realtime && realtime.refresh) realtime.refresh()
    } catch (e) {
      wx.hideLoading()
      wx.showToast({ title: e.message || '操作失败', icon: 'none' })
    }
  },

  onComplete(e) {
    this.doAction('complete', e.currentTarget.dataset.id)
  },

  onConfirm(e) {
    this.doAction('confirm', e.currentTarget.dataset.id)
  },

  onCancel(e) {
    const id = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认取消',
      content: '确定要取消这个任务吗？',
      success: (res) => {
        if (res.confirm) this.doAction('cancel', id)
      }
    })
  },

  onChat(e) {
    if (!requireLogin()) return
    const task = this.findTask(e.currentTarget.dataset.id)
    if (!task) return
    const myName = this.data.myName
    const target = task.publisherName === myName ? task.claimerName : task.publisherName
    if (!target) {
      wx.showToast({ title: '暂无可联系对象', icon: 'none' })
      return
    }
    wx.navigateTo({ url: `/pages/chat-room/chat-room?target=${encodeURIComponent(target)}` })
  },

  onRepublish() {
    wx.switchTab({ url: '/pages/publish/publish' })
  }
})
