const taskService = require('../../services/task')
const authService = require('../../services/auth')
const realtime = require('../../services/realtime')
const { getUser, requireLogin, isLoggedIn } = require('../../utils/auth')
const { resolveStatusLabel, statusClass, taskTypeLabel, taskTypeAbbr, taskTypeBadgeClass } = require('../../utils/task-status')
const {
  formatDateTime,
  formatMoney,
  parseImageUrls,
  maskName,
  normalizeAvatarUrl,
  resolveUserAvatar
} = require('../../utils/format')

Page({
  data: {
    task: null,
    statusLabel: '',
    statusCls: '',
    typeLabel: '',
    typeAbbr: '',
    typeBadgeCls: '',
    rewardText: '0.00',
    urgentTag: '',
    urgentBonusText: '',
    createdText: '',
    deadlineText: '',
    images: [],
    publisherProfile: null,
    publisherMasked: '',
    publisherInitial: '',
    publisherAvatar: '',
    claimerMasked: '',
    claimerInitial: '',
    claimerAvatar: '',
    claimerProfile: null,
    claimerStat: '还没有人接',
    showClaimerSheet: false,
    claimerSheet: {
      riderApproved: false,
      ratingText: '暂无',
      ratingCount: '0',
      claimedCount: '0',
      hasOrderRating: false,
      orderRatingText: '',
      orderComment: ''
    },
    showClaimerRatings: false,
    claimerRatingsLoading: false,
    claimerRatings: [],
    myName: '',
    isLoggedIn: false,
    canClaim: false,
    canComplete: false,
    canConfirm: false,
    canCancel: false,
    canChat: false,
    canCallClaimer: false,
    canRate: false,
    showRateModal: false,
    rateScore: 5,
    rateComment: '',
    rateSubmitting: false,
    rateStars: [1, 2, 3, 4, 5],
    steps: [],
    loading: true
  },

  buildSteps(status) {
    const order = [
      { key: 'open', label: '发布' },
      { key: 'claimed', label: '接单' },
      { key: 'deliver', label: '配送' },
      { key: 'done', label: '完成' }
    ]
    let active = 0
    if (status === 'OPEN' || status === 'PENDING_REVIEW') active = 0
    else if (status === 'CLAIMED' || status === 'OVERDUE') active = 1
    else if (status === 'PENDING_CONFIRMATION') active = 2
    else if (status === 'COMPLETED') active = 3
    else if (status === 'CANCELLED' || status === 'REJECTED') active = 0

    return order.map((s, i) => ({
      ...s,
      done: status === 'COMPLETED' ? true : i < active,
      current: status === 'COMPLETED' ? i === 3 : i === active
    }))
  },

  onLoad(options) {
    if (options.id) {
      this.taskId = Number(options.id)
      this._openRateAfterLoad = options.rate === '1' || options.rate === 'true'
      this.loadTask()
    }
  },

  async resolveTask() {
    const id = this.taskId
    try {
      const mine = await taskService.fetchMyTasks()
      const hit = (Array.isArray(mine) ? mine : []).find(t => Number(t.id) === id)
      if (hit) return hit
    } catch { /* ignore */ }
    try {
      const res = await taskService.fetchTasks({ page: 1, size: 50 })
      const hit = (res.records || []).find(t => Number(t.id) === id)
      if (hit) return hit
    } catch { /* ignore */ }
    return null
  },

  async loadTask() {
    this.setData({ loading: true })
    try {
      const task = await this.resolveTask()
      if (!task) throw new Error('任务不存在或已不可见')
      const user = getUser()
      const myName = user ? user.username : ''
      const images = parseImageUrls(task.imageUrls)
      const publisherMasked = maskName(task.publisherName)
      const claimerMasked = task.claimerName ? maskName(task.claimerName) : ''
      const involvedStatuses = ['CLAIMED', 'PENDING_CONFIRMATION', 'OVERDUE', 'COMPLETED']
      const canChat = !!(task.claimerName && task.publisherName) &&
        involvedStatuses.includes(task.status) &&
        isLoggedIn()
      const canCallClaimer = !!(myName && task.claimerName && task.publisherName === myName) &&
        involvedStatuses.includes(task.status)
      const canRate = !!(
        myName
        && task.publisherName === myName
        && task.claimerName
        && task.status === 'COMPLETED'
        && task.claimerRating == null
      )
      let claimerStat = '还没有人接'
      if (claimerMasked) {
        if (task.status === 'COMPLETED') claimerStat = '已完成本单'
        else if (task.status === 'PENDING_CONFIRMATION') claimerStat = '待发布方确认'
        else if (task.status === 'OVERDUE') claimerStat = '已超时'
        else claimerStat = '正在配送'
      }

      const urgentOn = task.urgentEnabled === true || task.urgentEnabled === 1 || task.urgentEnabled === 'true'
      // 详情面向接单：统一展示加急赏（约 80%）；发布支付总额只在发布页拆账里体现
      let urgentBonus = Number(task.urgentRiderBonus)
      if (!(Number.isFinite(urgentBonus) && urgentBonus > 0) && urgentOn) {
        const fee = Number(task.urgentFee)
        if (Number.isFinite(fee) && fee > 0) {
          urgentBonus = Math.round(fee * 0.8 * 100) / 100
        }
      }
      const urgentBonusText = urgentOn && Number.isFinite(urgentBonus) && urgentBonus > 0
        ? formatMoney(urgentBonus)
        : ''
      const urgentTag = urgentOn ? '加急' : ''

      this.setData({
        task,
        images,
        statusLabel: resolveStatusLabel(task),
        statusCls: statusClass(task.status),
        typeLabel: taskTypeLabel(task.taskType),
        typeAbbr: taskTypeAbbr(task.taskType),
        typeBadgeCls: taskTypeBadgeClass(task.taskType),
        rewardText: formatMoney(task.reward),
        urgentTag,
        urgentBonusText,
        createdText: formatDateTime(task.createdAt),
        deadlineText: task.latestDeliveryAt ? formatDateTime(task.latestDeliveryAt) : '',
        publisherMasked,
        publisherInitial: publisherMasked.slice(0, 1) || '匿',
        publisherAvatar: '',
        claimerMasked,
        claimerInitial: claimerMasked ? claimerMasked.slice(0, 1) : '',
        claimerAvatar: '',
        claimerStat,
        myName,
        isLoggedIn: isLoggedIn(),
        canClaim: task.status === 'OPEN' && myName && task.publisherName !== myName,
        canComplete: task.status === 'CLAIMED' && task.claimerName === myName,
        canConfirm: task.status === 'PENDING_CONFIRMATION' && task.publisherName === myName,
        canCancel: ['OPEN', 'CLAIMED', 'PENDING_REVIEW'].includes(task.status) &&
          (task.publisherName === myName || task.claimerName === myName),
        canChat,
        canCallClaimer,
        canRate,
        steps: this.buildSteps(task.status)
      })
      await this.loadPeopleAvatars(task, user, myName)
      if (this._openRateAfterLoad || this._pendingRate) {
        this._openRateAfterLoad = false
        this._pendingRate = false
        this.openRateModal()
      }
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
      this.setData({ task: null })
    } finally {
      this.setData({ loading: false })
    }
  },

  async loadPeopleAvatars(task, user, myName) {
    const patch = {}
    const names = []
    if (task.publisherName) names.push(['publisher', task.publisherName])
    if (task.claimerName) names.push(['claimer', task.claimerName])

    await Promise.all(names.map(async ([role, username]) => {
      if (myName && username === myName) {
        const selfAvatar = resolveUserAvatar(user)
        if (selfAvatar) {
          if (role === 'publisher') patch.publisherAvatar = selfAvatar
          else patch.claimerAvatar = selfAvatar
        }
      }
      try {
        const profile = await authService.fetchPublicProfile(username)
        const avatar = normalizeAvatarUrl(profile && profile.avatarUrl)
        if (role === 'publisher') {
          patch.publisherProfile = profile
          if (avatar) patch.publisherAvatar = avatar
        } else {
          patch.claimerProfile = profile
          if (avatar) patch.claimerAvatar = avatar
        }
      } catch { /* ignore */ }
    }))

    if (Object.keys(patch).length) this.setData(patch)
  },

  openRateModal() {
    const task = this.data.task
    if (!task) return
    if (!(
      task.status === 'COMPLETED'
      && this.data.myName
      && task.publisherName === this.data.myName
      && task.claimerName
      && task.claimerRating == null
    )) {
      return
    }
    this.setData({
      showRateModal: true,
      rateScore: 5,
      rateComment: '',
      rateSubmitting: false
    })
  },

  buildClaimerSheet() {
    const task = this.data.task || {}
    const profile = this.data.claimerProfile || {}
    const avg = profile.claimerAverageRating
    const count = Number(profile.claimerRatingCount || 0)
    const claimed = Number(profile.claimedTaskCount || 0)
    const hasOrderRating = task.claimerRating != null && task.claimerRating !== ''
    return {
      riderApproved: !!profile.riderApproved,
      ratingText: avg == null || avg === '' ? '暂无' : Number(avg).toFixed(1),
      ratingCount: String(count),
      claimedCount: String(claimed),
      hasOrderRating,
      orderRatingText: hasOrderRating ? Number(task.claimerRating).toFixed(1) : '',
      orderComment: String(task.claimerRatingComment || '').trim()
    }
  },

  async onOpenClaimerProfile() {
    if (!this.data.claimerMasked || !(this.data.task && this.data.task.claimerName)) return
    if (!this.data.claimerProfile) {
      try {
        const profile = await authService.fetchPublicProfile(this.data.task.claimerName)
        const avatar = normalizeAvatarUrl(profile && profile.avatarUrl)
        this.setData({
          claimerProfile: profile,
          claimerAvatar: avatar || this.data.claimerAvatar
        })
      } catch { /* ignore */ }
    }
    this.setData({
      showClaimerSheet: true,
      claimerSheet: this.buildClaimerSheet()
    })
  },

  closeClaimerSheet() {
    this.setData({ showClaimerSheet: false, showClaimerRatings: false })
  },

  async onOpenClaimerRatings() {
    const name = this.data.task && this.data.task.claimerName
    if (!name) return
    this.setData({ showClaimerRatings: true, claimerRatingsLoading: true })
    try {
      const list = await authService.fetchClaimerRatings(name)
      const rows = Array.isArray(list) ? list : []
      this.setData({
        claimerRatings: rows.map((item) => {
          const score = item && item.rating != null ? Number(item.rating) : 0
          const at = item && item.ratedAt ? formatDateTime(item.ratedAt) : ''
          return {
            taskId: item.taskId,
            title: item.title || '任务',
            ratingText: Number.isFinite(score) ? score.toFixed(1) : '-',
            comment: item.comment || '',
            publisherName: item.publisherName || '***',
            ratedAtText: at
          }
        }),
        claimerRatingsLoading: false
      })
    } catch (err) {
      this.setData({ claimerRatingsLoading: false, claimerRatings: [] })
      wx.showToast({ title: (err && err.message) || '加载评价失败', icon: 'none' })
    }
  },

  closeClaimerRatings() {
    this.setData({ showClaimerRatings: false })
  },

  onChatFromClaimerSheet() {
    this.setData({ showClaimerSheet: false })
    this.onChat()
  },

  closeRateModal() {
    if (this.data.rateSubmitting) return
    this.setData({ showRateModal: false })
  },

  onPickStar(e) {
    const score = Number(e.currentTarget.dataset.score)
    if (!score || score < 1 || score > 5) return
    this.setData({ rateScore: score })
  },

  onRateCommentInput(e) {
    this.setData({ rateComment: e.detail.value || '' })
  },

  async submitRate() {
    if (this.data.rateSubmitting || !this.taskId) return
    const score = Number(this.data.rateScore)
    if (score < 1 || score > 5) {
      wx.showToast({ title: '请选择 1-5 分', icon: 'none' })
      return
    }
    const comment = String(this.data.rateComment || '').trim()
    if (comment.length > 300) {
      wx.showToast({ title: '评价最多 300 字', icon: 'none' })
      return
    }
    this.setData({ rateSubmitting: true })
    wx.showLoading({ title: '提交中', mask: true })
    try {
      await taskService.rateTask(this.taskId, { rating: score, comment })
      wx.hideLoading()
      this.setData({ showRateModal: false, rateSubmitting: false, canRate: false })
      wx.showToast({ title: '评价成功', icon: 'success' })
      this.loadTask()
      realtime.refresh()
    } catch (e) {
      wx.hideLoading()
      this.setData({ rateSubmitting: false })
      wx.showToast({ title: e.message || '评价失败', icon: 'none', duration: 3000 })
    }
  },

  async doAction(action) {
    if (!requireLogin()) return
    const id = this.taskId
    wx.showLoading({ title: '处理中', mask: true })
    try {
      if (action === 'claim') await taskService.claimTask(id)
      else if (action === 'complete') await taskService.completeTask(id)
      else if (action === 'confirm') await taskService.confirmTask(id)
      else if (action === 'cancel') await taskService.cancelTask(id)
      wx.hideLoading()
      if (action === 'confirm') {
        this._pendingRate = true
        wx.showToast({ title: '确认成功', icon: 'success', duration: 1200 })
        await this.loadTask()
        realtime.refresh()
        return
      }
      wx.showToast({ title: '操作成功', icon: 'success', duration: 2000 })
      this.loadTask()
      realtime.refresh()
    } catch (e) {
      wx.hideLoading()
      const msg = (e && e.message) || '操作失败'
      if (action === 'claim' && (/接单员|认证|申请/.test(msg) || msg.length > 14)) {
        wx.showModal({
          title: '暂时无法接单',
          content: msg,
          confirmText: /接单员|申请/.test(msg) ? '去申请' : '知道了',
          cancelText: '关闭',
          showCancel: /接单员|申请/.test(msg),
          success: (res) => {
            if (res.confirm && /接单员|申请/.test(msg)) {
              wx.navigateTo({ url: '/pages/rider-apply/rider-apply' })
            }
          }
        })
      } else {
        wx.showToast({ title: msg, icon: 'none', duration: 3500 })
      }
    }
  },

  onClaim() { this.doAction('claim') },
  onComplete() { this.doAction('complete') },
  onConfirm() { this.doAction('confirm') },
  onRate() { this.openRateModal() },
  onCancel() {
    wx.showModal({
      title: '确认取消',
      content: '确定要取消这个任务吗？',
      success: (res) => { if (res.confirm) this.doAction('cancel') }
    })
  },

  onChat() {
    const task = this.data.task
    if (!task || !requireLogin()) return
    const target = task.publisherName === this.data.myName ? task.claimerName : task.publisherName
    if (!target) {
      wx.showToast({ title: '暂无可联系对象', icon: 'none' })
      return
    }
    wx.navigateTo({ url: `/pages/chat-room/chat-room?target=${encodeURIComponent(target)}` })
  },

  async onCallClaimer() {
    if (!requireLogin()) return
    if (!this.data.canCallClaimer || !this.taskId) {
      wx.showToast({ title: '当前不可拨打', icon: 'none' })
      return
    }
    wx.showLoading({ title: '获取号码', mask: true })
    try {
      const res = await taskService.fetchClaimerPhone(this.taskId)
      const phone = String((res && res.phone) || '').replace(/\D/g, '')
      if (!/^1\d{10}$/.test(phone)) {
        throw new Error('接单员未填写可用手机号')
      }
      wx.hideLoading()
      wx.makePhoneCall({
        phoneNumber: phone,
        fail: (err) => {
          const msg = (err && err.errMsg) || ''
          if (/cancel|取消/i.test(msg)) return
          wx.showToast({ title: '无法拨打电话', icon: 'none' })
        }
      })
    } catch (e) {
      wx.hideLoading()
      wx.showToast({ title: e.message || '获取电话失败', icon: 'none', duration: 3000 })
    }
  },

  noop() {},

  onBack() {
    wx.navigateBack({ fail: () => wx.redirectTo({ url: '/pages/mine/mine' }) })
  },

  previewImage(e) {
    const url = e.currentTarget.dataset.url
    wx.previewImage({ current: url, urls: this.data.images })
  }
})
