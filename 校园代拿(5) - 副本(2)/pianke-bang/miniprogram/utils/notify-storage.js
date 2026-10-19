const SYSTEM_NOTICE_USERNAME = '__system_notice__'

function key(prefix, username) {
  const user = String(username || '').trim()
  return user ? `${prefix}_${user}` : prefix
}

function readMap(storageKey) {
  try {
    const raw = wx.getStorageSync(storageKey)
    return raw && typeof raw === 'object' ? raw : {}
  } catch {
    return {}
  }
}

function writeMap(storageKey, map) {
  wx.setStorageSync(storageKey, map || {})
}

function getReadUpToMap(username) {
  return readMap(key('campus_chat_read', username))
}

function setReadUpTo(username, contact, messageId) {
  const map = getReadUpToMap(username)
  map[contact] = Math.max(map[contact] || 0, messageId || 0)
  writeMap(key('campus_chat_read', username), map)
}

function getSyncMap(username) {
  return readMap(key('campus_chat_sync', username))
}

function setSynced(username, contact, messageId) {
  const map = getSyncMap(username)
  map[contact] = Math.max(map[contact] || 0, messageId || 0)
  writeMap(key('campus_chat_sync', username), map)
}

function getTaskSnapshot(username) {
  return readMap(key('campus_task_snapshot', username))
}

function setTaskSnapshot(username, snapshot) {
  writeMap(key('campus_task_snapshot', username), snapshot)
}

function seedTaskSnapshot(username, taskId, status) {
  if (!username || taskId == null || taskId === '') return
  const map = getTaskSnapshot(username)
  map[String(taskId)] = String(status || '')
  setTaskSnapshot(username, map)
}

function getTaskNotified(username) {
  return readMap(key('campus_task_notified', username))
}

function setTaskNotified(username, map) {
  writeMap(key('campus_task_notified', username), map)
}

function getSystemNoticeLastReadId(username) {
  return Number(wx.getStorageSync(key('campus_system_notice_read', username)) || 0)
}

function setSystemNoticeLastReadId(username, id) {
  wx.setStorageSync(key('campus_system_notice_read', username), Number(id) || 0)
}

function getRiderSnapshot(username) {
  const raw = wx.getStorageSync(key('campus_rider_snapshot', username))
  if (!raw || typeof raw !== 'object') {
    return { riderApproved: false, riderApplicationPending: false }
  }
  return raw
}

function setRiderSnapshot(username, snapshot) {
  wx.setStorageSync(key('campus_rider_snapshot', username), snapshot || {})
}

module.exports = {
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
  getSystemNoticeLastReadId,
  setSystemNoticeLastReadId,
  getRiderSnapshot,
  setRiderSnapshot
}
