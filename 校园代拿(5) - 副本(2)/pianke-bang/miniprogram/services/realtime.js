const taskService = require('./task')
const chatService = require('./chat')
const noticeService = require('./notice')
const authService = require('./auth')
const { getUser, setUser, isLoggedIn, getSessionEpoch, bumpSessionEpoch } = require('../utils/auth')
const {
  formatTaskStatusNoticeMessage,
  taskStatusToastTitle,
  inferTaskEvent
} = require('../utils/task-notify')
const {
  SYSTEM_NOTICE_USERNAME,
  getReadUpToMap,
  setReadUpTo,
  getSyncMap,
  setSynced,
  getTaskSnapshot,
  setTaskSnapshot,
  seedTaskSnapshot,
  getTaskNotified,
  setTaskNotified,
  getRiderSnapshot,
  setRiderSnapshot
} = require('../utils/notify-storage')

const CHAT_INTERVAL = 3000
const TASK_INTERVAL = 15000

let chatTimer = null
let taskTimer = null
let chatReady = false
let taskReady = false
let profileReady = false
let lastSystemUnread = 0
let lastSystemMessageId = 0
let systemBaselineReady = false
let running = false

function getAppSafe() {
  try {
    return getApp()
  } catch {
    return null
  }
}

function emitNotify(payload) {
  const app = getAppSafe()
  if (app && typeof app.emitNotify === 'function') {
    app.emitNotify(payload)
  }
}

function updateUnreadBadge(total) {
  const app = getAppSafe()
  if (!app) return
  app.globalData.chatUnread = total
  if (typeof app.updateTabBarBadge === 'function') {
    app.updateTabBarBadge()
  }
}

function getMyName() {
  const user = getUser()
  return user && user.username ? user.username : ''
}

function isMyTask(task, myName) {
  return task && (task.publisherName === myName || task.claimerName === myName)
}

function shouldNotifyChat(contact, messageId, myName) {
  if (!chatReady || !messageId) return false
  const readMap = getReadUpToMap(myName)
  const syncMap = getSyncMap(myName)
  const readUpTo = readMap[contact] || 0
  if (messageId <= readUpTo) return false
  const latestSeen = syncMap[contact]
  if (latestSeen == null) return false
  return messageId > latestSeen
}

function markChatRead(contact) {
  const myName = getMyName()
  if (!contact || !myName) return
  chatService.fetchHistory(contact).then((history) => {
    const list = Array.isArray(history) ? history : []
    const incoming = list.filter(m => m.fromUsername === contact)
    const latestId = incoming.length ? Math.max(...incoming.map(m => m.id || 0)) : 0
    setReadUpTo(myName, contact, latestId)
    setSynced(myName, contact, latestId)
    pollChat(false)
  }).catch(() => {})
}

async function pollChat(notify) {
  const epoch = getSessionEpoch()
  if (!isLoggedIn() || !running) return
  const myName = getMyName()
  if (!myName) return

  const app = getAppSafe()
  const activeChat = app ? (app.globalData.activeChatTarget || '') : ''
  const readMap = getReadUpToMap(myName)
  const syncMap = getSyncMap(myName)

  let contacts = []
  try {
    contacts = await chatService.fetchContacts()
  } catch {
    return
  }
  if (!running || getSessionEpoch() !== epoch || !isLoggedIn()) return
  if (!Array.isArray(contacts)) contacts = []

  let chatUnread = 0

  for (const contact of contacts) {
    if (!running || getSessionEpoch() !== epoch || !isLoggedIn()) return
    const name = contact.username
    if (!name || name === SYSTEM_NOTICE_USERNAME) continue

    let history = []
    try {
      history = await chatService.fetchHistory(name)
    } catch {
      continue
    }
    if (!running || getSessionEpoch() !== epoch || !isLoggedIn()) return
    if (!Array.isArray(history)) history = []

    const incoming = history.filter(m => m.fromUsername === name)
    const latestId = incoming.length ? Math.max(...incoming.map(m => m.id || 0)) : 0

    if (activeChat === name) {
      setReadUpTo(myName, name, latestId)
      setSynced(myName, name, latestId)
      continue
    }

    const readUpTo = readMap[name] || 0
    const unread = incoming.filter(m => (m.id || 0) > readUpTo).length
    chatUnread += unread

    if (notify && chatReady && latestId > 0) {
      const newOnes = incoming.filter(m => shouldNotifyChat(name, m.id, myName))
      if (newOnes.length > 0) {
        emitNotify({
          kind: 'chat',
          title: '新消息提醒',
          message: `${contact.displayName || name} 给你发来了一条消息`,
          chatUser: name
        })
      }
    }

    if (syncMap[name] == null && latestId > 0) {
      setSynced(myName, name, latestId)
    } else if (latestId > 0) {
      setSynced(myName, name, latestId)
    }
  }

  if (!running || getSessionEpoch() !== epoch || !isLoggedIn()) return

  let systemUnread = 0
  try {
    const res = await noticeService.fetchSystemMessages()
    if (!running || getSessionEpoch() !== epoch || !isLoggedIn()) return
    const messages = res.messages || []
    systemUnread = Number(res.unreadCount || 0)
    const maxId = messages.reduce((max, item) => Math.max(max, Number(item.id) || 0), 0)

    if (!systemBaselineReady) {
      lastSystemMessageId = maxId
      systemBaselineReady = true
    } else if (notify && chatReady) {
      const fresh = messages
        .filter(item => !item.readFlag && (Number(item.id) || 0) > lastSystemMessageId)
        .sort((a, b) => (Number(a.id) || 0) - (Number(b.id) || 0))
      fresh.forEach((latest) => {
        const latestId = Number(latest.id || 0)
        emitNotify({
          kind: 'system',
          title: latest.title || '系统通知',
          message: latest.content || '你有新的系统消息',
          systemMessageId: latestId,
          eventType: latest.eventType || '',
          taskId: latest.refId || null
        })
        lastSystemMessageId = Math.max(lastSystemMessageId, latestId)
      })
    }
    lastSystemUnread = systemUnread
  } catch {
    // ignore
  }

  if (!running || getSessionEpoch() !== epoch || !isLoggedIn()) return
  updateUnreadBadge(chatUnread + systemUnread)

  if (!chatReady) {
    chatReady = true
  }
}

async function pollTasks(notify) {
  if (!isLoggedIn()) return
  const myName = getMyName()
  if (!myName) return

  let tasks = []
  try {
    const res = await taskService.fetchMyTasks()
    tasks = Array.isArray(res) ? res : (res.records || [])
  } catch {
    return
  }

  const prevSnapshot = getTaskSnapshot(myName)
  const nextSnapshot = {}
  const notifiedMap = getTaskNotified(myName)
  const nextNotified = { ...notifiedMap }

  for (const task of tasks) {
    if (!task || !task.id) continue
    const status = String(task.status || '')
    nextSnapshot[task.id] = status

    if (!taskReady || !isMyTask(task, myName)) continue

    const prevStatus = prevSnapshot[task.id]
    if (!prevStatus || prevStatus === status) continue

    const event = inferTaskEvent(prevStatus, status)
    const message = formatTaskStatusNoticeMessage({
      title: task.title,
      status,
      event
    })
    const title = taskStatusToastTitle(event, status)
    const notifyKey = `${task.id}:${status}`

    if (notify && message && nextNotified[task.id] !== status) {
      nextNotified[task.id] = status
      emitNotify({
        kind: 'task',
        title,
        message,
        taskId: task.id
      })
    }
  }

  setTaskSnapshot(myName, nextSnapshot)
  setTaskNotified(myName, nextNotified)

  if (!taskReady) {
    taskReady = true
  }
}

async function pollProfile(notify) {
  if (!isLoggedIn()) return
  const myName = getMyName()
  if (!myName) return

  try {
    const me = await authService.fetchMe()
    const prev = getRiderSnapshot(myName)
    const prevApproved = !!prev.riderApproved
    const prevPending = !!prev.riderApplicationPending
    const nextApproved = !!me.riderApproved
    const nextPending = !!me.riderApplicationPending

    const current = getUser() || {}
    const nextUser = { ...current, ...me }
    setUser(nextUser)
    const app = getAppSafe()
    if (app && typeof app.setUser === 'function') {
      app.globalData.user = nextUser
    }

    if (profileReady && notify) {
      if (!prevApproved && nextApproved) {
        emitNotify({
          kind: 'system',
          title: '接单员认证通过',
          message: '恭喜！你已通过接单员审核，可以前往大厅接单了',
          navigate: '/pages/rider-apply/rider-apply'
        })
      } else if (prevPending && !nextPending && !nextApproved) {
        emitNotify({
          kind: 'system',
          title: '接单员申请未通过',
          message: '你的接单员申请未通过审核，可修改后重新提交',
          navigate: '/pages/rider-apply/rider-apply'
        })
      }
    }

    setRiderSnapshot(myName, {
      riderApproved: nextApproved,
      riderApplicationPending: nextPending
    })
  } catch {
    // ignore
  }

  if (!profileReady) {
    profileReady = true
  }
}

function seedProfileSnapshot() {
  const myName = getMyName()
  if (!myName) return
  const user = getUser()
  if (!user) return
  setRiderSnapshot(myName, {
    riderApproved: !!user.riderApproved,
    riderApplicationPending: !!user.riderApplicationPending
  })
}

function seedTaskSnapshotForUser(taskId, status) {
  const myName = getMyName()
  if (!myName || taskId == null) return
  seedTaskSnapshot(myName, taskId, status)
}

function start() {
  if (!isLoggedIn() || running) return
  running = true
  chatReady = false
  taskReady = false
  profileReady = false
  lastSystemUnread = 0
  lastSystemMessageId = 0
  systemBaselineReady = false

  seedProfileSnapshot()
  pollChat(false)
  pollTasks(false)
  pollProfile(false)

  if (chatTimer) clearInterval(chatTimer)
  if (taskTimer) clearInterval(taskTimer)

  chatTimer = setInterval(() => pollChat(true), CHAT_INTERVAL)
  taskTimer = setInterval(() => {
    pollChat(true)
    pollTasks(true)
    pollProfile(true)
  }, TASK_INTERVAL)
}

function stop() {
  bumpSessionEpoch()
  running = false
  chatReady = false
  taskReady = false
  profileReady = false
  lastSystemUnread = 0
  lastSystemMessageId = 0
  systemBaselineReady = false
  if (chatTimer) {
    clearInterval(chatTimer)
    chatTimer = null
  }
  if (taskTimer) {
    clearInterval(taskTimer)
    taskTimer = null
  }
  updateUnreadBadge(0)
}

function refresh() {
  if (!isLoggedIn()) return
  pollChat(false)
  pollTasks(false)
  pollProfile(false)
}

function checkTasksNow() {
  return pollTasks(true)
}

module.exports = {
  start,
  stop,
  refresh,
  markChatRead,
  seedProfileSnapshot,
  seedTaskSnapshot: seedTaskSnapshotForUser,
  checkTasksNow
}
