const chatService = require('../../services/chat')
const authService = require('../../services/auth')
const { getUser, useLoginGuard, checkLoginGuard } = require('../../utils/auth')
const { resolveUserAvatar, normalizeAvatarUrl } = require('../../utils/format')
const { parseChatImage, encodeChatImage, prepareChatImageDataUrl } = require('../../utils/chat-image')
const realtime = require('../../services/realtime')

function pad(n) {
  return n < 10 ? '0' + n : String(n)
}

function isSameDay(a, b) {
  return a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate()
}

function formatChatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatDateLabel(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ''
  const now = new Date()
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (isSameDay(d, now)) return '今天'
  if (isSameDay(d, yesterday)) return '昨天'
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

function getInitial(name) {
  const value = String(name || '').trim()
  return value ? value[0] : '?'
}

const TIME_GAP_MS = 5 * 60 * 1000

function enrichMessages(list, myName) {
  const messages = Array.isArray(list) ? list : []
  let lastDayKey = ''

  return messages.map((message, index) => {
    const createdAt = message.createdAt
    const date = createdAt ? new Date(createdAt) : null
    const validDate = date && !isNaN(date.getTime())
    const dayKey = validDate
      ? `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`
      : ''
    const showDateLabel = !!dayKey && dayKey !== lastDayKey
    if (showDateLabel) lastDayKey = dayKey

    let showTimeLabel = false
    let timeLabel = ''
    let dateLabel = formatDateLabel(createdAt)

    if (validDate) {
      if (showDateLabel) {
        dateLabel = `${dateLabel} ${formatChatTime(createdAt)}`
      } else {
        const prev = index > 0 ? messages[index - 1] : null
        const prevDate = prev && prev.createdAt ? new Date(prev.createdAt) : null
        const prevValid = prevDate && !isNaN(prevDate.getTime())
        const gap = prevValid ? date.getTime() - prevDate.getTime() : TIME_GAP_MS
        if (!prevValid || gap >= TIME_GAP_MS) {
          showTimeLabel = true
          timeLabel = formatChatTime(createdAt)
        }
      }
    }

    const imagePayload = parseChatImage(message.content)
    return {
      ...message,
      isMine: message.fromUsername === myName,
      isImage: !!imagePayload,
      imageUrl: imagePayload ? imagePayload.image : '',
      textContent: imagePayload ? imagePayload.text : String(message.content || ''),
      dateLabel,
      showDateLabel,
      showTimeLabel,
      timeLabel
    }
  }).map((message, index, all) => {
    const prev = index > 0 ? all[index - 1] : null
    const next = index < all.length - 1 ? all[index + 1] : null

    const canGroupWith = (a, b) => {
      if (!a || !b) return false
      if (a.isMine !== b.isMine) return false
      if (a.isImage || b.isImage) return false
      if (a.showDateLabel || b.showDateLabel || a.showTimeLabel || b.showTimeLabel) return false
      const dateA = a.createdAt ? new Date(a.createdAt) : null
      const dateB = b.createdAt ? new Date(b.createdAt) : null
      if (!dateA || !dateB || isNaN(dateA.getTime()) || isNaN(dateB.getTime())) return false
      return Math.abs(dateB.getTime() - dateA.getTime()) < TIME_GAP_MS
    }

    const groupedWithPrev = canGroupWith(prev, message)
    const groupedWithNext = canGroupWith(message, next)

    let groupPos = 'single'
    if (groupedWithPrev && groupedWithNext) groupPos = 'middle'
    else if (groupedWithPrev) groupPos = 'last'
    else if (groupedWithNext) groupPos = 'first'

    return {
      ...message,
      groupPos,
      showAvatar: true,
      groupedWithNext,
      showReadReceipt: false
    }
  })
}

/** 在自己最近一条「已被对方读过」的消息下显示「已读」 */
function attachReadReceipt(messages, peerLastReadMessageId) {
  const list = Array.isArray(messages) ? messages : []
  const peerRead = Number(peerLastReadMessageId) || 0
  let lastReadMineIndex = -1
  list.forEach((m, i) => {
    if (m.isMine && Number(m.id) > 0 && Number(m.id) <= peerRead) {
      lastReadMineIndex = i
    }
  })
  return list.map((m, i) => ({
    ...m,
    showReadReceipt: i === lastReadMineIndex
  }))
}

Page({
  data: {
    target: '',
    messages: [],
    content: '',
    myName: '',
    myAvatar: '',
    myInitial: '',
    targetAvatar: '',
    targetInitial: '',
    scrollTo: '',
    keyboardHeight: 0,
    peerLastReadMessageId: 0,
    canSend: false,
    sending: false,
    loading: false
  },

  onLoad(options) {
    this._target = decodeURIComponent(options.target || '')
    useLoginGuard(this, `/pages/chat-room/chat-room?target=${encodeURIComponent(this._target)}`)
  },

  onShow() {
    if (!checkLoginGuard(this)) return
    const user = getUser()
    const app = getApp()
    app.globalData.activeChatTarget = this._target
    const myName = user ? user.username : ''
    this.setData({
      target: this._target,
      myName,
      myAvatar: resolveUserAvatar(user),
      myInitial: getInitial(myName)
    })
    wx.setNavigationBarTitle({ title: this._target || '聊天' })
    this.loadTargetProfile()
    this.loadHistory()
    realtime.markChatRead(this._target)
    this.startReadPoll()
    if (!this._notifyHandler) {
      this._notifyHandler = (payload) => {
        if (!payload || payload.kind !== 'chat') return
        if (payload.chatUser && payload.chatUser !== this._target) return
        this.loadHistory()
        realtime.markChatRead(this._target)
      }
    }
    getApp().registerNotifyListener(this._notifyHandler)
  },

  onHide() {
    getApp().globalData.activeChatTarget = ''
    this.setData({ keyboardHeight: 0 })
    this.stopReadPoll()
    const app = getApp()
    if (this._notifyHandler && app.unregisterNotifyListener) {
      app.unregisterNotifyListener(this._notifyHandler)
    }
  },

  onUnload() {
    getApp().globalData.activeChatTarget = ''
    this.stopReadPoll()
    const app = getApp()
    if (this._notifyHandler && app.unregisterNotifyListener) {
      app.unregisterNotifyListener(this._notifyHandler)
    }
  },

  startReadPoll() {
    this.stopReadPoll()
    this._readPollTimer = setInterval(() => {
      this.refreshPeerRead(false)
      this.reportRead()
    }, 4000)
  },

  stopReadPoll() {
    if (this._readPollTimer) {
      clearInterval(this._readPollTimer)
      this._readPollTimer = null
    }
  },

  async loadTargetProfile() {
    if (!this._target) return
    try {
      const profile = await authService.fetchPublicProfile(this._target)
      const avatar = normalizeAvatarUrl(profile && profile.avatarUrl)
      this.setData({
        targetAvatar: avatar,
        targetInitial: getInitial(this._target)
      })
    } catch {
      this.setData({ targetInitial: getInitial(this._target) })
    }
  },

  scrollToBottom() {
    const len = (this.data.messages || []).length
    const anchor = len ? `msg-${len - 1}` : 'msg-anchor'
    this.setData({ scrollTo: '' })
    setTimeout(() => {
      this.setData({ scrollTo: anchor })
    }, 60)
  },

  onKeyboardHeightChange(e) {
    const keyboardHeight = (e && e.detail && e.detail.height) || 0
    if (keyboardHeight === this.data.keyboardHeight) {
      if (keyboardHeight > 0) this.scrollToBottom()
      return
    }
    this.setData({ keyboardHeight })
    if (keyboardHeight > 0) {
      setTimeout(() => this.scrollToBottom(), 80)
    }
  },

  async reportRead() {
    const target = this.data.target || this._target
    if (!target) return
    try {
      await chatService.markRead(target)
    } catch { /* ignore */ }
  },

  async refreshPeerRead(scroll) {
    const target = this.data.target || this._target
    if (!target) return
    try {
      const res = await chatService.fetchPeerRead(target)
      const peerLastReadMessageId = Number(res && res.peerLastReadMessageId) || 0
      if (peerLastReadMessageId === this.data.peerLastReadMessageId) return
      const messages = attachReadReceipt(this.data.messages, peerLastReadMessageId)
      this.setData({ peerLastReadMessageId, messages }, () => {
        if (scroll) this.scrollToBottom()
      })
    } catch { /* ignore */ }
  },

  async loadHistory() {
    this.setData({ loading: true })
    try {
      const target = this.data.target || this._target
      const [list, peerRes] = await Promise.all([
        chatService.fetchHistory(target),
        chatService.fetchPeerRead(target).catch(() => ({ peerLastReadMessageId: 0 }))
      ])
      const peerLastReadMessageId = Number(peerRes && peerRes.peerLastReadMessageId) || 0
      const messages = attachReadReceipt(
        enrichMessages(list, this.data.myName),
        peerLastReadMessageId
      )
      this.setData({ messages, peerLastReadMessageId }, () => this.scrollToBottom())
      this.reportRead()
      realtime.markChatRead(target)
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  onInput(e) {
    const content = e.detail.value
    this.setData({
      content,
      canSend: !!String(content || '').trim()
    })
  },

  onPickImage() {
    if (this.data.sending) return
    wx.showActionSheet({
      itemList: ['从相册选择', '拍照'],
      success: (res) => {
        const sourceType = res.tapIndex === 1 ? ['camera'] : ['album']
        this.chooseChatImage(sourceType)
      }
    })
  },

  chooseChatImage(sourceType) {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType,
      success: (res) => {
        const file = (res.tempFiles || [])[0]
        if (!file || !file.tempFilePath) return
        if (file.size && file.size > 8 * 1024 * 1024) {
          wx.showToast({ title: '图片不能超过 8MB', icon: 'none' })
          return
        }
        this.sendImage(file.tempFilePath)
      }
    })
  },

  async sendImage(tempFilePath) {
    if (this.data.sending) return
    this.setData({ sending: true })
    wx.showLoading({ title: '发送中', mask: true })
    try {
      const image = await prepareChatImageDataUrl(tempFilePath)
      const caption = String(this.data.content || '').trim()
      const content = encodeChatImage(image, caption)
      await chatService.sendMessage(this.data.target, content)
      this.setData({ content: '', canSend: false })
      await this.loadHistory()
      realtime.refresh()
    } catch (e) {
      wx.showToast({ title: e.message || '发送失败', icon: 'none', duration: 3000 })
    } finally {
      wx.hideLoading()
      this.setData({ sending: false })
    }
  },

  onPreviewImage(e) {
    const current = e.currentTarget.dataset.url
    if (!current) return
    const urls = (this.data.messages || [])
      .filter(item => item.isImage && item.imageUrl)
      .map(item => item.imageUrl)
    wx.previewImage({
      current,
      urls: urls.length ? urls : [current]
    })
  },

  async onSend() {
    const content = this.data.content.trim()
    if (!content || this.data.sending || !this.data.canSend) return
    this.setData({ sending: true })
    try {
      await chatService.sendMessage(this.data.target, content)
      this.setData({ content: '', canSend: false })
      await this.loadHistory()
      realtime.refresh()
    } catch (e) {
      wx.showToast({ title: e.message || '发送失败', icon: 'none' })
    } finally {
      this.setData({ sending: false })
    }
  }
})
