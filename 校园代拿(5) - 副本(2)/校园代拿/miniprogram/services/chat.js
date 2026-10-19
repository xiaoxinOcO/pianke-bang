const { useMock } = require('../config/index')
const { mockApi } = require('../utils/mock')
const { get, post, request } = require('../utils/http')

function fetchContacts() {
  if (useMock) return mockApi.fetchContacts()
  return get('/chat/contacts')
}

function fetchHistory(targetUser) {
  if (useMock) return mockApi.fetchHistory(targetUser)
  return get('/chat/history', { targetUser })
}

function sendMessage(toUsername, content) {
  if (useMock) return mockApi.sendMessage(toUsername, content)
  const big = String(content || '').length > 80000
  return request({
    url: '/chat/send',
    method: 'POST',
    data: { toUsername, content },
    timeout: big ? 60000 : 30000
  })
}

/** 上报当前用户已读该会话 */
function markRead(targetUser, lastReadMessageId) {
  if (useMock) return Promise.resolve({ lastReadMessageId: lastReadMessageId || 0 })
  const data = { targetUser }
  if (lastReadMessageId != null) data.lastReadMessageId = lastReadMessageId
  return post('/chat/read', data)
}

/** 查询对方已读游标 */
function fetchPeerRead(targetUser) {
  if (useMock) return Promise.resolve({ peerLastReadMessageId: 0 })
  return get('/chat/peer-read', { targetUser })
}

module.exports = {
  fetchContacts,
  fetchHistory,
  sendMessage,
  markRead,
  fetchPeerRead
}
