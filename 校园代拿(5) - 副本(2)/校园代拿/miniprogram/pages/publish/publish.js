const taskService = require('../../services/task')
const walletService = require('../../services/wallet')
const couponService = require('../../services/coupon')
const authService = require('../../services/auth')
const realtime = require('../../services/realtime')
const { getUser, setUser, isLoggedIn } = require('../../utils/auth')
const { buildDefaultDeliveryRange, combineDateTime, splitDateTime, formatMoney } = require('../../utils/format')
const {
  formatTaskStatusNoticeMessage,
  taskStatusToastTitle
} = require('../../utils/task-notify')

function couponLabel(item) {
  const name = item.name || '优惠券'
  if (item.type === 'FIXED') return `${name} · 立减¥${Number(item.discountAmount || 0).toFixed(2)}`
  if (item.type === 'THRESHOLD') {
    return `${name} · 满${Number(item.minSpend || 0).toFixed(0)}减${Number(item.discountAmount || 0).toFixed(0)}`
  }
  if (item.type === 'PERCENT') {
    const zhe = (Number(item.discountPercent || 0) * 10).toFixed(1).replace(/\.0$/, '')
    return `${name} · ${zhe}折`
  }
  return name
}

Page({
  data: {
    taskTypes: [
      { value: 'EXPRESS', label: '快递代取' },
      { value: 'FOOD', label: '外卖代拿' },
      { value: 'PRINT', label: '打印代送' },
      { value: 'QUEUE', label: '排队代办' },
      { value: 'OTHER', label: '其他' }
    ],
    taskType: 'EXPRESS',
    taskTypeLabel: '快递代取',
    title: '',
    pickupLocation: '',
    pickupLat: null,
    pickupLng: null,
    dropoffLocation: '',
    dropoffLat: null,
    dropoffLng: null,
    reward: '',
    contact: '',
    description: '',
    deliveryDate: '',
    deliveryTime: '',
    paymentMethod: 'WALLET',
    walletBalance: '0.00',
    imageUrls: [],
    submitting: false,
    needLogin: false,
    formError: '',
    coupons: [],
    couponLabels: ['不使用优惠券'],
    couponIndex: 0,
    userCouponId: null,
    couponDiscount: 0,
    couponDiscountText: '0.00',
    payableText: '0.00',
    bountyPayableText: '0.00',
    urgentEnabled: false,
    urgentFeePresets: [1, 2, 3, 5],
    urgentFee: 2,
    urgentFeeValid: false,
    urgentFeeText: '2.00',
    urgentRiderText: '1.60',
    urgentPushText: '0.40',
    urgentRemaining: 3,
    urgentWeeklyLimit: 3
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      const app = getApp()
      this.getTabBar().setData({
        selected: 1,
        chatBadge: app.globalData.chatUnread || 0
      })
    }
    const loggedIn = isLoggedIn()
    this.setData({ needLogin: !loggedIn })
    if (!loggedIn) return
    this.initDeliveryDefaults()
    const user = getUser()
    const phone = user && user.phone ? String(user.phone).trim() : ''
    if (phone) {
      this.setData({ contact: phone })
    } else {
      // 本地 user 可能没有 phone，拉一次 /me
      this.refreshContactFromMe()
    }
    this.loadWalletBalance()
    this.loadCoupons()
    this.loadUrgentQuota()
  },

  async refreshContactFromMe() {
    try {
      const me = await authService.fetchMe()
      const phone = me && me.phone ? String(me.phone).trim() : ''
      if (!phone) return
      const user = Object.assign({}, getUser() || {}, me)
      setUser(user)
      try {
        getApp().setUser(user)
      } catch (e) { /* ignore */ }
      this.setData({ contact: phone })
    } catch (e) { /* ignore */ }
  },

  async loadUrgentQuota() {
    try {
      const q = await taskService.fetchUrgentQuota()
      const remaining = Number(q && q.remaining)
      const weeklyLimit = Number(q && q.weeklyLimit)
      const patch = {}
      if (Number.isFinite(remaining)) patch.urgentRemaining = remaining
      if (Number.isFinite(weeklyLimit) && weeklyLimit > 0) patch.urgentWeeklyLimit = weeklyLimit
      if (Number.isFinite(remaining) && remaining <= 0 && this.data.urgentEnabled) {
        patch.urgentEnabled = false
      }
      if (Object.keys(patch).length) this.setData(patch)
      this.refreshPayable()
    } catch {
      // 配额失败不阻断发布，提交时后端会再校验
    }
  },

  parseUrgentFee() {
    const fee = Number(this.data.urgentFee)
    if (!Number.isFinite(fee)) return { ok: false, fee: 0, message: '请填写加急费' }
    if (fee < 1 || fee > 20) return { ok: false, fee: 0, message: '加急费须在 ¥1～¥20' }
    const steps = fee / 0.5
    if (Math.abs(steps - Math.round(steps)) > 1e-6) {
      return { ok: false, fee: 0, message: '加急费须以 0.5 元为步进' }
    }
    return { ok: true, fee: Math.round(fee * 100) / 100 }
  },

  onToggleUrgent() {
    if (this.data.urgentRemaining <= 0 && !this.data.urgentEnabled) {
      wx.showToast({ title: '本周加急次数已用完', icon: 'none' })
      return
    }
    const urgentEnabled = !this.data.urgentEnabled
    this.setData({ urgentEnabled, formError: '' })
    this.refreshPayable()
  },

  onPickUrgentFee(e) {
    const fee = Number(e.currentTarget.dataset.fee)
    if (!Number.isFinite(fee)) return
    this.setData({ urgentFee: fee, formError: '' })
    this.refreshPayable()
  },

  onUrgentFeeInput(e) {
    this.setData({ urgentFee: e.detail.value, formError: '' })
    this.refreshPayable()
  },

  initDeliveryDefaults() {
    if (this.data.deliveryDate && this.data.deliveryTime) return
    const defaults = buildDefaultDeliveryRange()
    const parts = splitDateTime(defaults.latestDeliveryAt)
    this.setData({
      deliveryDate: parts.date,
      deliveryTime: parts.time
    })
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/publish/publish') })
  },

  async readWalletBalance() {
    const wallet = await walletService.fetchWallet()
    const overview = wallet && (wallet.overview || wallet)
    const balance = overview && overview.balance != null ? Number(overview.balance) : NaN
    return Number.isFinite(balance) ? balance : null
  },

  async loadWalletBalance() {
    try {
      const balance = await this.readWalletBalance()
      this.setData({ walletBalance: balance == null ? '--' : formatMoney(balance) })
    } catch {
      this.setData({ walletBalance: '--' })
    }
  },

  async loadCoupons() {
    try {
      const rows = await couponService.fetchMine('UNUSED')
      const coupons = rows || []
      const couponLabels = ['不使用优惠券'].concat(coupons.map(couponLabel))
      let couponIndex = 0
      if (this.data.userCouponId) {
        const idx = coupons.findIndex((c) => c.id === this.data.userCouponId)
        couponIndex = idx >= 0 ? idx + 1 : 0
      }
      this.setData({ coupons, couponLabels, couponIndex })
      if (couponIndex === 0) {
        this.setData({ userCouponId: null })
      }
      await this.refreshPayable()
    } catch {
      this.setData({
        coupons: [],
        couponLabels: ['不使用优惠券'],
        couponIndex: 0,
        userCouponId: null,
        couponDiscount: 0,
        couponDiscountText: '0.00'
      })
      await this.refreshPayable()
    }
  },

  onCouponChange(e) {
    const couponIndex = Number(e.detail.value) || 0
    const coupon = couponIndex > 0 ? this.data.coupons[couponIndex - 1] : null
    this.setData({
      couponIndex,
      userCouponId: coupon ? coupon.id : null,
      formError: ''
    })
    this.refreshPayable()
  },

  async refreshPayable() {
    const reward = Number(this.data.reward)
    const urgent = this.parseUrgentFee()
    const urgentFee = urgent.ok ? urgent.fee : 0
    const urgentRider = Math.round(urgentFee * 0.8 * 100) / 100
    const urgentPush = Math.round((urgentFee - urgentRider) * 100) / 100

    const applyUrgentDisplay = (bountyPayable) => {
      const bounty = Number(bountyPayable) || 0
      const total = bounty + (this.data.urgentEnabled && urgent.ok ? urgentFee : 0)
      this.setData({
        bountyPayableText: formatMoney(bounty),
        payableText: formatMoney(total),
        urgentFeeValid: !!(this.data.urgentEnabled && urgent.ok),
        urgentFeeText: formatMoney(urgentFee),
        urgentRiderText: formatMoney(urgentRider),
        urgentPushText: formatMoney(urgentPush)
      })
    }

    if (!Number.isFinite(reward) || reward <= 0) {
      this.setData({
        couponDiscount: 0,
        couponDiscountText: '0.00',
        bountyPayableText: '0.00',
        payableText: formatMoney(this.data.urgentEnabled && urgent.ok ? urgentFee : 0),
        urgentFeeValid: !!(this.data.urgentEnabled && urgent.ok),
        urgentFeeText: formatMoney(urgentFee),
        urgentRiderText: formatMoney(urgentRider),
        urgentPushText: formatMoney(urgentPush)
      })
      return
    }
    const userCouponId = this.data.userCouponId
    if (!userCouponId) {
      applyUrgentDisplay(reward)
      return
    }
    try {
      const quote = await couponService.preview(userCouponId, reward)
      if (!quote || quote.applicable === false) {
        this.setData({
          couponIndex: 0,
          userCouponId: null,
          couponDiscount: 0,
          couponDiscountText: '0.00',
          formError: (quote && quote.reason) || '该券不可用于当前赏金'
        })
        applyUrgentDisplay(reward)
        return
      }
      const discount = Number(quote.discount || 0)
      const payable = Number(quote.payable != null ? quote.payable : reward - discount)
      this.setData({
        couponDiscount: discount,
        couponDiscountText: formatMoney(discount)
      })
      applyUrgentDisplay(payable)
    } catch (e) {
      this.setData({
        couponIndex: 0,
        userCouponId: null,
        couponDiscount: 0,
        couponDiscountText: '0.00',
        formError: e.message || '优惠券不可用'
      })
      applyUrgentDisplay(reward)
    }
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [field]: e.detail.value, formError: '' })
    if (field === 'reward') {
      this.refreshPayable()
    }
  },

  onDeliveryDateChange(e) {
    this.setData({ deliveryDate: e.detail.value })
  },

  onDeliveryTimeChange(e) {
    this.setData({ deliveryTime: e.detail.value })
  },

  onTypeChange(e) {
    const idx = Number(e.detail.value)
    const item = this.data.taskTypes[idx]
    this.setData({ taskType: item.value, taskTypeLabel: item.label, formError: '' })
  },

  chooseLocation(e) {
    const field = e.currentTarget.dataset.field
    wx.chooseLocation({
      success: (res) => {
        const update = {}
        if (field === 'pickup') {
          update.pickupLocation = res.name || res.address
          update.pickupLat = res.latitude
          update.pickupLng = res.longitude
        } else {
          update.dropoffLocation = res.name || res.address
          update.dropoffLat = res.latitude
          update.dropoffLng = res.longitude
        }
        this.setData({ ...update, formError: '' })
      },
      fail: () => {
        wx.showToast({ title: '可手动输入地点', icon: 'none' })
      }
    })
  },

  removeImage(e) {
    const index = Number(e.currentTarget.dataset.index)
    const imageUrls = this.data.imageUrls.filter((_, i) => i !== index)
    this.setData({ imageUrls })
  },

  chooseImage() {
    wx.chooseMedia({
      count: 3 - this.data.imageUrls.length,
      mediaType: ['image'],
      success: (res) => {
        const files = res.tempFiles || []
        const tasks = files.map(f => this.fileToBase64(f.tempFilePath))
        Promise.all(tasks).then(urls => {
          this.setData({ imageUrls: this.data.imageUrls.concat(urls) })
        })
      }
    })
  },

  fileToBase64(path) {
    return new Promise((resolve, reject) => {
      wx.getFileSystemManager().readFile({
        filePath: path,
        encoding: 'base64',
        success: (res) => resolve('data:image/jpeg;base64,' + res.data),
        fail: reject
      })
    })
  },

  async ensureUser() {
    let user = getUser()
    if (user) return user
    if (!isLoggedIn()) return null
    try {
      const me = await authService.fetchMe()
      setUser(me)
      getApp().setUser(me)
      return me
    } catch (e) {
      return null
    }
  },

  validateForm() {
    const title = (this.data.title || '').trim()
    const pickupLocation = (this.data.pickupLocation || '').trim()
    const dropoffLocation = (this.data.dropoffLocation || '').trim()
    const reward = Number(this.data.reward)
    const contact = (this.data.contact || '').trim()
    const latestDeliveryAt = combineDateTime(this.data.deliveryDate, this.data.deliveryTime)

    if (!title) return '请填写任务标题'
    if (title.length < 4) return '标题太短，请写清楚要做什么，如「东门快递点代取包裹」'
    if (/^\d{1,8}$/.test(title)) return '标题不能只是数字，请写清楚任务内容'
    if (!pickupLocation) return '请填写取件地点'
    if (pickupLocation.length < 2) return '取件地点请写具体一点，如「东门菜鸟驿站」'
    if (!dropoffLocation) return '请填写送达地点'
    if (dropoffLocation.length < 2) return '送达地点请写具体一点，如「3栋104宿舍」'
    if (!Number.isFinite(reward) || reward <= 0) return '赏金必须大于 0'
    if (!contact) return '账号未绑定手机号，请先完善资料'
    if (!latestDeliveryAt) return '请选择最晚送达时间'
    if (Date.parse(latestDeliveryAt.replace(' ', 'T')) <= Date.now()) {
      return '送达时间必须晚于当前时间'
    }
    if (this.data.urgentEnabled) {
      if (this.data.urgentRemaining <= 0) return '本周加急次数已用完'
      const urgent = this.parseUrgentFee()
      if (!urgent.ok) return urgent.message || '加急费金额无效'
    }
    return ''
  },

  buildDeliveryPayload(latestDeliveryAt) {
    const endTs = Date.parse(latestDeliveryAt.replace(' ', 'T'))
    const start = new Date(endTs - 60 * 60 * 1000)
    const end = new Date(endTs)
    const pad = (n) => (n < 10 ? '0' + n : String(n))
    const fmt = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
    const endStr = fmt(end)
    return {
      deliveryStartAt: fmt(start),
      deliveryEndAt: endStr,
      latestDeliveryAt: endStr
    }
  },

  async onSubmit() {
    if (this.data.submitting) return

    if (!isLoggedIn()) {
      wx.showToast({ title: '请先登录', icon: 'none' })
      this.goLogin()
      return
    }

    const error = this.validateForm()
    if (error) {
      this.setData({ formError: error })
      wx.showToast({ title: error, icon: 'none', duration: 2500 })
      return
    }

    const reward = Number(this.data.reward)
    await this.refreshPayable()
    const payable = Number(this.data.payableText)
    const payAmount = Number.isFinite(payable) && payable > 0 ? payable : reward
    const balance = Number(this.data.walletBalance)
    if (Number.isFinite(balance) && payAmount > balance) {
      const msg = `钱包余额不足（当前 ¥${this.data.walletBalance}，实付 ¥${formatMoney(payAmount)}）`
      this.setData({ formError: msg })
      wx.showToast({ title: msg, icon: 'none', duration: 2800 })
      return
    }

    const user = await this.ensureUser()
    if (!user) {
      wx.showToast({ title: '登录信息失效，请重新登录', icon: 'none' })
      this.goLogin()
      return
    }

    const latestDeliveryAt = combineDateTime(this.data.deliveryDate, this.data.deliveryTime)
    const delivery = this.buildDeliveryPayload(latestDeliveryAt)

    this.setData({ submitting: true, formError: '' })
    wx.showLoading({ title: '发布中', mask: true })

    let balanceBefore = null
    try {
      balanceBefore = await this.readWalletBalance()
    } catch {
      balanceBefore = null
    }

    try {
      const payload = {
        paymentMethod: 'WALLET',
        taskType: this.data.taskType,
        title: this.data.title.trim(),
        pickupLocation: this.data.pickupLocation.trim(),
        pickupLat: this.data.pickupLat,
        pickupLng: this.data.pickupLng,
        dropoffLocation: this.data.dropoffLocation.trim(),
        dropoffLat: this.data.dropoffLat,
        dropoffLng: this.data.dropoffLng,
        reward,
        publisherName: user.username,
        contact: (user.phone || this.data.contact || '').trim(),
        description: (this.data.description || '').trim(),
        imageUrls: this.data.imageUrls,
        urgentEnabled: !!this.data.urgentEnabled,
        ...delivery
      }
      if (payload.urgentEnabled) {
        payload.urgentFee = this.parseUrgentFee().fee
      }
      if (this.data.userCouponId) {
        payload.userCouponId = this.data.userCouponId
      }
      const task = await taskService.createTask(payload)

      if (task.bountyPrepaid !== true) {
        if (task.id) {
          try {
            await taskService.cancelTask(task.id)
          } catch {
            // ignore
          }
        }
        throw new Error('钱包扣款未成功，任务已取消。请重新发布并确认余额充足')
      }

      let balanceAfter = null
      try {
        balanceAfter = await this.readWalletBalance()
      } catch {
        balanceAfter = null
      }
      if (
        balanceBefore != null
        && balanceAfter != null
        && Math.abs(balanceBefore - balanceAfter - payAmount) > 0.01
      ) {
        throw new Error('钱包余额未变化，扣款可能失败，请前往钱包页确认后再试')
      }

      const title = (payload.title || '').trim()
      const taskStatus = String(task.status || 'PENDING_REVIEW')
      const notifyMessage = title
        ? `《${title}》已提交，赏金已从钱包扣除，请等待管理员审核`
        : '任务已提交，赏金已从钱包扣除，请等待管理员审核'

      if (taskStatus === 'OPEN') {
        const approvedMessage = formatTaskStatusNoticeMessage({
          title,
          status: 'OPEN',
          event: 'TASK_APPROVED'
        })
        getApp().emitNotify({
          kind: 'task',
          title: taskStatusToastTitle('TASK_APPROVED', 'OPEN'),
          message: approvedMessage,
          taskId: task.id
        })
        realtime.seedTaskSnapshot(task.id, 'OPEN')
      } else {
        getApp().emitNotify({
          kind: 'task',
          title: '任务已提交',
          message: notifyMessage,
          taskId: task && task.id
        })
        realtime.seedTaskSnapshot(task.id, 'PENDING_REVIEW')
      }

      wx.showToast({
        title: taskStatus === 'OPEN' ? '审核通过，已上架' : '已提交，等待管理员审核',
        icon: 'success',
        duration: 2800
      })
      this.resetForm()
      this.loadWalletBalance()
      realtime.refresh()
      realtime.checkTasksNow()
      setTimeout(() => wx.switchTab({ url: '/pages/hall/hall' }), 800)
    } catch (e) {
      let msg = e.message || '发布失败'
      if (/钱包余额不足，无法支付赏金/.test(msg)) {
        msg = `服务器扣款失败：页面显示余额 ¥${this.data.walletBalance}，但发布服务读到的余额不足。请确认赏金未超过余额；若仍失败，需管理员检查服务器 wallet 配置`
      }
      this.setData({ formError: msg })
      wx.showToast({ title: msg, icon: 'none', duration: 3500 })
    } finally {
      wx.hideLoading()
      this.setData({ submitting: false })
    }
  },

  resetForm() {
    const defaults = buildDefaultDeliveryRange()
    const parts = splitDateTime(defaults.latestDeliveryAt)
    this.setData({
      title: '',
      pickupLocation: '',
      pickupLat: null,
      pickupLng: null,
      dropoffLocation: '',
      dropoffLat: null,
      dropoffLng: null,
      reward: '',
      description: '',
      imageUrls: [],
      deliveryDate: parts.date,
      deliveryTime: parts.time,
      couponIndex: 0,
      userCouponId: null,
      couponDiscount: 0,
      couponDiscountText: '0.00',
      payableText: '0.00'
    })
    this.loadCoupons()
  }
})
