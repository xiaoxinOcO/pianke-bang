/**
 * 本地 Mock 数据层（不连数据库/后端）
 * useMock=true 时，services 走这里
 */
const { demoAccount } = require('../config/index')

const MOCK_TOKEN = 'mock_token_local'
const STORE_KEY = 'campus_mock_store'

const MOCK_USER = {
  id: 1,
  username: 'pianke',
  realName: '测试用户',
  phone: '13800138000',
  studentId: '20240001',
  gender: '男',
  creditScore: 88,
  riderApproved: true,
  riderApplicationPending: false,
  avatarUrl: '',
  pendingAvatarUrl: '',
  avatarPending: false
}

function nowIso() {
  return new Date().toISOString()
}

function defaultStore() {
  const t = nowIso()
  return {
    nextTaskId: 5,
    tasks: [
      {
        id: 1,
        title: '菜鸟驿站代取快递',
        taskType: 'EXPRESS',
        pickupLocation: '菜鸟驿站（南区）',
        dropoffLocation: '竹园3号楼 208',
        reward: 5,
        publisherName: '小李',
        claimerName: null,
        contact: '138****5678',
        description: '一个小件，取件码尾号 881，放楼下货架即可',
        status: 'OPEN',
        bountyPrepaid: true,
        createdAt: t,
        updatedAt: t
      },
      {
        id: 2,
        title: '东门外卖代拿',
        taskType: 'FOOD',
        pickupLocation: '东门外卖柜 A12',
        dropoffLocation: '一教 A 区门口',
        reward: 4,
        publisherName: '阿陈',
        claimerName: null,
        contact: '微信联系',
        description: '奶茶一杯，11:50 前送到',
        status: 'OPEN',
        bountyPrepaid: true,
        createdAt: t,
        updatedAt: t
      },
      {
        id: 3,
        title: '图书馆打印代送',
        taskType: 'PRINT',
        pickupLocation: '图书馆打印中心',
        dropoffLocation: '计算机学院 305',
        reward: 6,
        publisherName: '王同学',
        claimerName: null,
        contact: '139****1234',
        description: '20 页黑白打印，已付款',
        status: 'OPEN',
        bountyPrepaid: true,
        createdAt: t,
        updatedAt: t
      },
      {
        id: 4,
        title: '食堂排队买饭',
        taskType: 'QUEUE',
        pickupLocation: '第二食堂窗口',
        dropoffLocation: '体育馆门口',
        reward: 3.5,
        publisherName: '张同学',
        claimerName: null,
        contact: '13800138001',
        description: '帮忙买一份盖浇饭',
        status: 'OPEN',
        bountyPrepaid: true,
        createdAt: t,
        updatedAt: t
      }
    ],
    notices: [
      {
        id: 1,
        title: '片刻帮正式上线试运行',
        content: '欢迎使用片刻帮校园代拿平台！发布任务、顺路接单，让校园生活更便捷。',
        pinned: true,
        createdAt: t,
        updatedAt: t
      },
      {
        id: 2,
        title: '接单员认证说明',
        content: '通过接单员认证后可在大厅接单。请前往「我的 → 申请接单员」提交资料。',
        pinned: false,
        createdAt: t,
        updatedAt: t
      }
    ],
    wallet: {
      balance: 100,
      totalIncome: 100,
      transactions: [
        {
          id: 1,
          type: '充值',
          amount: 100,
          remark: '新用户体验金',
          createdAt: t
        }
      ]
    },
    contacts: [],
    messages: {}
  }
}

function getStore() {
  const raw = wx.getStorageSync(STORE_KEY)
  if (!raw || !raw.tasks) {
    const store = defaultStore()
    wx.setStorageSync(STORE_KEY, store)
    return store
  }
  return raw
}

function saveStore(store) {
  wx.setStorageSync(STORE_KEY, store)
}

function resetStore() {
  wx.removeStorageSync(STORE_KEY)
  return defaultStore()
}

function delay(result, ms = 200) {
  return new Promise(resolve => setTimeout(() => resolve(result), ms))
}

function rejectDelay(message, ms = 200) {
  return new Promise((_, reject) => setTimeout(() => reject(new Error(message)), ms))
}

function matchDemo(username, password) {
  const account = demoAccount || { username: 'pianke', password: '123456' }
  return username === account.username && password === account.password
}

function filterTasks(tasks, params = {}) {
  let list = tasks.slice()
  const status = params.status
  const taskType = params.taskType
  const keyword = (params.keyword || '').trim().toLowerCase()

  if (status) list = list.filter(t => t.status === status)
  if (taskType) list = list.filter(t => t.taskType === taskType)
  if (keyword) {
    list = list.filter(t =>
      (t.title || '').toLowerCase().includes(keyword) ||
      (t.pickupLocation || '').toLowerCase().includes(keyword) ||
      (t.dropoffLocation || '').toLowerCase().includes(keyword)
    )
  }
  return list
}

const mockApi = {
  login(username, password) {
    if (matchDemo(username, password)) {
      return delay({ token: MOCK_TOKEN, username: MOCK_USER.username, message: '登录成功（本地模式）' })
    }
    return rejectDelay('账号或密码错误')
  },

  fetchMe() {
    return delay({ ...MOCK_USER })
  },

  updateProfile(data) {
    if (data.avatarUrl) {
      MOCK_USER.avatarUrl = data.avatarUrl
      MOCK_USER.pendingAvatarUrl = data.avatarUrl
      MOCK_USER.avatarPending = true
    }
    if (data.username) MOCK_USER.username = data.username
    return delay({
      message: MOCK_USER.avatarPending ? '资料已更新，头像已提交审核' : '个人资料更新成功',
      username: MOCK_USER.username,
      avatarUrl: MOCK_USER.avatarUrl,
      pendingAvatarUrl: MOCK_USER.pendingAvatarUrl,
      avatarPending: MOCK_USER.avatarPending,
      riderApproved: MOCK_USER.riderApproved,
      riderApplicationPending: MOCK_USER.riderApplicationPending,
      token: MOCK_TOKEN
    })
  },

  fetchPublicProfile(username) {
    return delay({
      username,
      claimedTaskCount: username === 'pianke' ? 3 : 1,
      creditScore: 80
    })
  },

  applyRider(data) {
    const realName = String((data && data.realName) || '').trim()
    const studentId = String((data && data.studentId) || '').trim()
    const phone = String((data && data.phone) || '').replace(/\D/g, '')
    const reason = String((data && data.reason) || '').trim()
    if (!realName || !studentId || !phone || !reason) {
      return rejectDelay('请完整填写申请信息')
    }
    if (!/^1\d{10}$/.test(phone)) {
      return rejectDelay('请输入 11 位手机号')
    }
    if (reason.length > 300) {
      return rejectDelay('申请理由不能超过 300 字')
    }
    MOCK_USER.riderApplicationPending = true
    MOCK_USER.riderApproved = false
    return delay({
      message: '接单员申请已提交，等待管理员审核',
      riderApproved: false,
      riderApplicationPending: true
    })
  },

  fetchTasks(params = {}) {
    const store = getStore()
    const records = filterTasks(store.tasks, params)
    return delay({ records, page: params.page || 0, totalPages: 1, total: records.length })
  },

  fetchMyTasks(params = {}) {
    const store = getStore()
    const records = store.tasks.filter(t =>
      t.publisherName === MOCK_USER.username || t.claimerName === MOCK_USER.username
    )
    return delay({ records, page: 0, totalPages: 1 })
  },

  createTask(data) {
    const store = getStore()
    const reward = Number(data.reward) || 0
    if (data.paymentMethod === 'WALLET' && store.wallet.balance < reward) {
      return rejectDelay('钱包余额不足')
    }
    const task = {
      id: store.nextTaskId++,
      title: data.title,
      taskType: data.taskType || 'OTHER',
      pickupLocation: data.pickupLocation,
      dropoffLocation: data.dropoffLocation,
      reward,
      publisherName: MOCK_USER.username,
      claimerName: null,
      contact: data.contact || MOCK_USER.phone,
      description: data.description || '',
      imageUrls: data.imageUrls || [],
      status: 'PENDING_REVIEW',
      bountyPrepaid: true,
      createdAt: nowIso(),
      updatedAt: nowIso()
    }
    if (data.paymentMethod === 'WALLET') {
      store.wallet.balance = Math.round((store.wallet.balance - reward) * 100) / 100
      store.wallet.transactions.unshift({
        id: Date.now(),
        type: '任务赏金',
        amount: -reward,
        remark: `发布任务：${task.title}`,
        createdAt: nowIso()
      })
    }
    store.tasks.unshift(task)
    saveStore(store)
    return delay(task)
  },

  claimTask(id) {
    const store = getStore()
    const task = store.tasks.find(t => t.id === Number(id))
    if (!task) return rejectDelay('任务不存在')
    if (task.status !== 'OPEN') return rejectDelay('该任务已被接单')
    if (task.publisherName === MOCK_USER.username) return rejectDelay('不能接自己发布的任务')
    task.status = 'CLAIMED'
    task.claimerName = MOCK_USER.username
    task.updatedAt = nowIso()
    saveStore(store)
    return delay(task)
  },

  completeTask(id) {
    const store = getStore()
    const task = store.tasks.find(t => t.id === Number(id))
    if (!task) return rejectDelay('任务不存在')
    if (task.claimerName !== MOCK_USER.username) return rejectDelay('无权操作')
    task.status = 'PENDING_CONFIRMATION'
    task.updatedAt = nowIso()
    saveStore(store)
    return delay(task)
  },

  confirmTask(id) {
    const store = getStore()
    const task = store.tasks.find(t => t.id === Number(id))
    if (!task) return rejectDelay('任务不存在')
    task.status = 'COMPLETED'
    task.updatedAt = nowIso()
    if (task.claimerName === MOCK_USER.username) {
      store.wallet.balance = Math.round((store.wallet.balance + task.reward) * 100) / 100
      store.wallet.totalIncome = Math.round((store.wallet.totalIncome + task.reward) * 100) / 100
      store.wallet.transactions.unshift({
        id: Date.now(),
        type: '任务收入',
        amount: task.reward,
        remark: `完成任务：${task.title}`,
        createdAt: nowIso()
      })
    }
    saveStore(store)
    return delay(task)
  },

  cancelTask(id) {
    const store = getStore()
    const task = store.tasks.find(t => t.id === Number(id))
    if (!task) return rejectDelay('任务不存在')
    task.status = 'CANCELLED'
    task.updatedAt = nowIso()
    saveStore(store)
    return delay(task)
  },

  fetchNotices() {
    const store = getStore()
    return delay(store.notices)
  },

  submitFeedback() {
    return delay({ message: '感谢反馈（本地模式）' })
  },

  fetchWallet() {
    const store = getStore()
    return delay({
      overview: {
        balance: store.wallet.balance,
        totalIncome: store.wallet.totalIncome
      },
      transactions: store.wallet.transactions
    })
  },

  withdraw(data) {
    const store = getStore()
    const amount = Number(data.amount) || 0
    if (amount <= 0 || amount > store.wallet.balance) {
      return rejectDelay('提现金额无效')
    }
    store.wallet.balance = Math.round((store.wallet.balance - amount) * 100) / 100
    store.wallet.transactions.unshift({
      id: Date.now(),
      type: '提现',
      amount: -amount,
      remark: '提现申请（本地模拟）',
      createdAt: nowIso()
    })
    saveStore(store)
    return delay({ message: '提现申请已提交' })
  },

  fetchContacts() {
    return delay([])
  },

  fetchSystemMessages() {
    return delay({
      unreadCount: 0,
      messages: [
        {
          id: 1,
          title: '欢迎使用片刻帮',
          content: '任务状态变更、钱包变动等消息会显示在系统通知中',
          readFlag: true,
          createdAt: nowIso()
        }
      ]
    })
  },

  markSystemMessagesRead() {
    return delay({ message: 'ok' })
  },

  fetchHistory() {
    return delay([])
  },

  sendMessage(toUsername, content) {
    return delay({ message: '发送成功（本地模式）', content, toUsername })
  }
}

module.exports = {
  MOCK_TOKEN,
  MOCK_USER,
  mockApi,
  getStore,
  resetStore
}
