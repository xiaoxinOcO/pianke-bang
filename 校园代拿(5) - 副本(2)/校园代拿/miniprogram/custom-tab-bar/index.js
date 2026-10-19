Component({
  data: {
    selected: 0,
    chatBadge: 0,
    list: [
      { pagePath: '/pages/hall/hall', text: '大厅', icon: 'hall' },
      { pagePath: '/pages/publish/publish', text: '发布', icon: 'publish' },
      { pagePath: '/pages/chat/chat', text: '消息', icon: 'chat' },
      { pagePath: '/pages/profile/profile', text: '我的', icon: 'profile' }
    ]
  },

  methods: {
    switchTab(e) {
      const index = e.currentTarget.dataset.index
      const item = this.data.list[index]
      wx.switchTab({ url: item.pagePath })
      this.setData({ selected: index })
    }
  }
})
