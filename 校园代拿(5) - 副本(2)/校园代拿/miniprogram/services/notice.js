const { useMock } = require('../config/index')
const { mockApi } = require('../utils/mock')
const { get, post } = require('../utils/http')

function fetchNotices() {
  if (useMock) return mockApi.fetchNotices()
  return get('/notices')
}

function fetchSystemMessages() {
  if (useMock) return mockApi.fetchSystemMessages()
  return get('/system-messages').then((res) => {
    if (Array.isArray(res)) {
      return { messages: res, unreadCount: res.filter(item => !item.readFlag).length }
    }
    return {
      messages: res.messages || res.records || [],
      unreadCount: Number(res.unreadCount || 0)
    }
  })
}

function markSystemMessagesRead() {
  if (useMock) return mockApi.markSystemMessagesRead()
  return post('/system-messages/read')
}

function submitFeedback(data) {
  if (useMock) return mockApi.submitFeedback(data)
  return post('/feedback', data)
}

function supportChat(content) {
  if (useMock) return Promise.resolve({ message: '客服回复（本地模式）' })
  return post('/support/chat', { content })
}

module.exports = {
  fetchNotices,
  fetchSystemMessages,
  markSystemMessagesRead,
  submitFeedback,
  supportChat
}
