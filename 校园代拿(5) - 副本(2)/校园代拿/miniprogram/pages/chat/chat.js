const chatService = require('../../services/chat')
const noticeService = require('../../services/notice')
const authService = require('../../services/auth')
const { requireLogin, isLoggedIn, getUser } = require('../../utils/auth')
const {
  SYSTEM_NOTICE_USERNAME,
  getReadUpToMap,
  setReadUpTo,
  setSynced
} = require('../../utils/notify-storage')
const { normalizeAvatarUrl } = require('../../utils/format')
const { chatContentPreview } = require('../../utils/chat-image')
const realtime = require('../../services/realtime')

const SYSTEM_NOTICE_ITEM = {
  username: SYSTEM_NOTICE_USERNAME,
  displayName: '系统通知',
  contactPreview: '任务、钱包与平台消息',
  isSystemNotice: true
}

function buildContactPreview(messages, myName) {
  const list = Array.isArray(messages) ? messages : []
  if (!list.length) return ''
  const last = list[list.length - 1]
  const content = chatContentPreview(last.content, 36)
  if (!content) return ''
  if (last.fromUsername === myName) return `我：${content}`
  return content
}

function truncateText(text, max) {
  const value = String(text || '').trim()
  if (!value) return ''
  return value.length > max ? `${value.slice(0, max)}…` : value
}

function getInitial(name) {
  const value = String(name || '').trim()
  return value ? value[0] : '?'
}

function pad(n) {
  return n < 10 ? '0' + n : String(n)
}

function isSameDay(a, b) {
  return a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate()
}

function formatListTime(iso) {
  if (!iso) return ''
  const date = new Date(iso)
  if (isNaN(date.getTime())) return ''
  const now = new Date()
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (isSameDay(date, now)) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
  if (isSameDay(date, yesterday)) return '昨天'
  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}月${date.getDate()}日`
  }
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
}

async function enrichContact(item, myName, readMap) {
  if (!item.username || item.username === SYSTEM_NOTICE_USERNAME) return item

  let avatarUrl = normalizeAvatarUrl(item.avatarUrl)
  if (!avatarUrl) {
    try {
      const profile = await authService.fetchPublicProfile(item.username)
      avatarUrl = normalizeAvatarUrl(profile && profile.avatarUrl)
    } catch { /* ignore */ }
  }

  const displayName = item.displayName || item.username
  const base = {
    ...item,
    displayName,
    avatarUrl,
    avatarInitial: getInitial(displayName)
  }

  try {
    const history = await chatService.fetchHistory(item.username)
    const messages = Array.isArray(history) ? history : []
    const incoming = messages.filter(m => m.fromUsername === item.username)
    const readUpTo = readMap[item.username] || 0
    return {
      ...base,
      unread: incoming.filter(m => (m.id || 0) > readUpTo).length,
      contactPreview: buildContactPreview(messages, myName),
      timeText: formatListTime(messages.length ? messages[messages.length - 1].createdAt : '')
    }
  } catch {
    return base
  }
}

Page({
  data: {
    contacts: [],
    systemNotice: SYSTEM_NOTICE_ITEM,
    systemUnread: 0,
    hasUnread: false,
    markingRead: false,
    loading: false,
    isLoggedIn: false
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      const app = getApp()
      this.getTabBar().setData({
        selected: 2,
        chatBadge: app.globalData.chatUnread || 0
      })
    }
    const loggedIn = isLoggedIn()
    this.setData({ isLoggedIn: loggedIn })
    if (loggedIn) {
      this.loadContacts()
      realtime.refresh()
    }
    if (!this._notifyHandler) {
      this._notifyHandler = (payload) => {
        if (payload && payload.kind === 'chat') {
          this.scheduleReload()
        }
      }
    }
    getApp().registerNotifyListener(this._notifyHandler)
  },

  onHide() {
    const app = getApp()
    if (this._notifyHandler && app.unregisterNotifyListener) {
      app.unregisterNotifyListener(this._notifyHandler)
    }
    if (this._reloadTimer) {
      clearTimeout(this._reloadTimer)
      this._reloadTimer = null
    }
  },

  scheduleReload() {
    if (this._reloadTimer) clearTimeout(this._reloadTimer)
    this._reloadTimer = setTimeout(() => {
      this._reloadTimer = null
      if (isLoggedIn()) this.loadContacts()
    }, 500)
  },

  async loadContacts() {
    this.setData({ loading: true })
    try {
      const myName = getUser()?.username || ''
      const readMap = getReadUpToMap(myName)
      const [contacts, systemRes] = await Promise.all([
        chatService.fetchContacts(),
        noticeService.fetchSystemMessages().catch(() => ({ messages: [], unreadCount: 0 }))
      ])

      const baseList = (Array.isArray(contacts) ? contacts : []).map(item => ({
        ...item,
        unread: 0,
        contactPreview: '',
        avatarUrl: '',
        avatarInitial: getInitial(item.displayName || item.username)
      }))
      const list = await Promise.all(
        baseList.map(item => enrichContact(item, myName, readMap))
      )

      const systemUnread = Number(systemRes.unreadCount || 0)
      const latest = (systemRes.messages || [])[0]
      const systemNotice = {
        ...SYSTEM_NOTICE_ITEM,
        contactPreview: latest
          ? truncateText(latest.title || latest.content || SYSTEM_NOTICE_ITEM.contactPreview, 40)
          : SYSTEM_NOTICE_ITEM.contactPreview,
        unread: systemUnread,
        timeText: latest ? formatListTime(latest.createdAt) : ''
      }

      this.setData({
        contacts: list,
        systemNotice,
        systemUnread,
        hasUnread: systemUnread > 0 || list.some(item => Number(item.unread || 0) > 0)
      })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async onMarkAllRead() {
    if (!requireLogin()) return
    if (this.data.markingRead) return

    const contacts = this.data.contacts || []
    const hasUnread = this.data.systemUnread > 0
      || contacts.some(item => Number(item.unread || 0) > 0)
    if (!hasUnread) {
      wx.showToast({ title: '暂无未读消息', icon: 'none' })
      return
    }

    this.setData({ markingRead: true })
    const myName = getUser()?.username || ''
    try {
      await noticeService.markSystemMessagesRead().catch(() => {})

      await Promise.all(contacts.map(async (item) => {
        const name = item && item.username
        if (!name || name === SYSTEM_NOTICE_USERNAME) return
        try {
          const history = await chatService.fetchHistory(name)
          const messages = Array.isArray(history) ? history : []
          const incoming = messages.filter(m => m.fromUsername === name)
          const latestId = incoming.length
            ? Math.max(...incoming.map(m => Number(m.id) || 0))
            : 0
          if (myName && latestId > 0) {
            setReadUpTo(myName, name, latestId)
            setSynced(myName, name, latestId)
          }
          await chatService.markRead(name, latestId > 0 ? latestId : undefined).catch(() => {})
        } catch { /* ignore single contact failure */ }
      }))

      const clearedContacts = contacts.map(item => ({ ...item, unread: 0 }))
      const systemNotice = {
        ...this.data.systemNotice,
        unread: 0
      }
      this.setData({
        contacts: clearedContacts,
        systemNotice,
        systemUnread: 0,
        hasUnread: false
      })

      const app = getApp()
      if (app && app.globalData) app.globalData.chatUnread = 0
      if (typeof this.getTabBar === 'function' && this.getTabBar()) {
        this.getTabBar().setData({ chatBadge: 0 })
      }
      if (typeof realtime.refresh === 'function') realtime.refresh()

      wx.showToast({ title: '已全部标为已读', icon: 'success' })
    } catch (e) {
      wx.showToast({ title: (e && e.message) || '操作失败', icon: 'none' })
    } finally {
      this.setData({ markingRead: false })
    }
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },

  openSystemNotice() {
    if (!requireLogin()) return
    wx.navigateTo({ url: '/pages/system-notice/system-notice' })
  },

  openChat(e) {
    const username = e.currentTarget.dataset.username
    if (!username) return
    wx.navigateTo({ url: `/pages/chat-room/chat-room?target=${encodeURIComponent(username)}` })
  }
})
