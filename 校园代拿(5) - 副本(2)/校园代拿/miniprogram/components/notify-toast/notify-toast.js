Component({
  data: {
    visible: false,
    title: '',
    message: '',
    kind: '',
    chatUser: '',
    taskId: 0,
    navigate: ''
  },

  lifetimes: {
    attached() {
      this._timer = null
      this._handler = (payload) => this.show(payload)
      const app = getApp()
      if (app && typeof app.registerNotifyListener === 'function') {
        app.registerNotifyListener(this._handler)
      }
      if (app && app.globalData && app.globalData.pendingNotify) {
        this.show(app.globalData.pendingNotify)
        app.globalData.pendingNotify = null
      }
    },

    detached() {
      const app = getApp()
      if (app && typeof app.unregisterNotifyListener === 'function') {
        app.unregisterNotifyListener(this._handler)
      }
      if (this._timer) clearTimeout(this._timer)
    }
  },

  methods: {
    show(payload) {
      if (!payload) return
      if (this._timer) clearTimeout(this._timer)
      this.setData({
        visible: true,
        title: payload.title || '提醒',
        message: payload.message || '',
        kind: payload.kind || '',
        chatUser: payload.chatUser || '',
        taskId: payload.taskId || 0,
        navigate: payload.navigate || ''
      })
      this._timer = setTimeout(() => {
        this.setData({ visible: false })
      }, 9000)
    },

    onTap() {
      const { kind, chatUser, taskId, navigate } = this.data
      this.setData({ visible: false })
      if (navigate) {
        wx.navigateTo({
          url: navigate,
          fail: () => wx.redirectTo({ url: navigate })
        })
        return
      }
      if (kind === 'chat' && chatUser) {
        wx.navigateTo({ url: `/pages/chat-room/chat-room?target=${encodeURIComponent(chatUser)}` })
        return
      }
      if ((kind === 'system' || kind === 'task') && taskId) {
        wx.navigateTo({ url: `/pages/task-detail/task-detail?id=${taskId}` })
        return
      }
      if (kind === 'system') {
        wx.navigateTo({ url: '/pages/system-notice/system-notice' })
        return
      }
      if (kind === 'task') {
        wx.switchTab({ url: '/pages/hall/hall' })
      }
    },

    onClose() {
      this.setData({ visible: false })
    }
  }
})
