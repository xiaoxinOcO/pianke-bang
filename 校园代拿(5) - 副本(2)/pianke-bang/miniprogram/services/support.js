const { get, post } = require('../utils/http')

function supportChat(message, history) {
  return post('/support/chat', {
    message,
    history: Array.isArray(history) ? history : []
  })
}

function fetchAgentAvailability() {
  return get('/support/agent-availability')
}

function assignAgent() {
  return get('/support/assign-agent')
}

function submitRating(payload) {
  return post('/support/ratings', {
    agentUsername: (payload && payload.agentUsername) || '',
    score: payload && payload.score,
    tags: (payload && Array.isArray(payload.tags)) ? payload.tags : [],
    comment: (payload && payload.comment) || ''
  })
}

module.exports = {
  supportChat,
  fetchAgentAvailability,
  assignAgent,
  submitRating
}
