const { useMock } = require('../config/index')
const { mockApi } = require('../utils/mock')
const { get, post, put } = require('../utils/http')

function login(username, password) {
  if (useMock) return mockApi.login(username, password)
  return post('/auth/login', { username, password, clientChannel: 'mp' })
}

function register(data) {
  return post('/auth/register', data)
}

function sendSms(phone) {
  if (useMock) return Promise.resolve({ message: '验证码：123456（本地模式）' })
  return post('/auth/sms/send', { phone })
}

function sendLoginSms(phone) {
  if (useMock) return Promise.resolve({ message: '验证码：123456（本地模式）' })
  return post('/auth/sms/send-login', { phone })
}

function loginBySms(phone, code) {
  if (useMock) return mockApi.login('pianke', '123456')
  return post('/auth/login-by-sms', { phone, code, clientChannel: 'mp' })
}

function confirmLoginBySms(pickToken, userId) {
  return post('/auth/login-by-sms/confirm', { pickToken, userId, clientChannel: 'mp' })
}

function registerBySms(data) {
  if (useMock) return mockApi.login('pianke', '123456')
  return post('/auth/register-by-sms', Object.assign({}, data, { clientChannel: 'mp' }))
}

function fetchMe() {
  if (useMock) return mockApi.fetchMe()
  return get('/auth/me')
}

function updateProfile(data) {
  if (useMock) return mockApi.updateProfile(data)
  return put('/auth/profile', data)
}

function applyRider(data) {
  if (useMock) return mockApi.applyRider(data)
  return post('/auth/rider/apply', data)
}

function sendForgotPasswordSms(phone) {
  if (useMock) return Promise.resolve({ message: '验证码：123456（本地模式）' })
  return post('/auth/forgot-password/sms/send', { phone })
}

function verifyForgotPasswordSms(phone, code) {
  if (useMock) {
    return Promise.resolve({
      needChooseAccount: false,
      userId: 1,
      username: 'pianke',
      pickToken: 'mock-pick-token',
      message: '验证成功（本地模式）'
    })
  }
  return post('/auth/forgot-password/verify-sms', { phone, code })
}

function resetPasswordBySms(data) {
  if (useMock) return Promise.resolve({ message: '密码已重置（本地模式）' })
  return post('/auth/forgot-password/reset', data)
}

function fetchPublicProfile(username) {
  if (useMock) return mockApi.fetchPublicProfile(username)
  return get(`/auth/users/${encodeURIComponent(username)}/public-profile`)
}

function fetchClaimerRatings(username) {
  if (useMock) return Promise.resolve([])
  return get(`/auth/users/${encodeURIComponent(username)}/claimer-ratings`)
}

module.exports = {
  login,
  register,
  sendSms,
  sendLoginSms,
  loginBySms,
  confirmLoginBySms,
  registerBySms,
  fetchMe,
  updateProfile,
  applyRider,
  sendForgotPasswordSms,
  verifyForgotPasswordSms,
  resetPasswordBySms,
  fetchPublicProfile,
  fetchClaimerRatings
}
