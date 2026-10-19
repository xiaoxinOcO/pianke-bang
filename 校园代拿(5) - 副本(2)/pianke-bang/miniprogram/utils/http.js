const { apiBase } = require('../config/index')
const {
  getToken,
  clearToken,
  setAuthRedirectLock,
  isAuthRedirectLocked,
  getSessionEpoch
} = require('./auth')

const REQUIRED_BODY_FIELDS = ['paymentMethod']

function cleanParams(data) {
  if (!data || typeof data !== 'object') return data
  const out = {}
  Object.keys(data).forEach((key) => {
    const val = data[key]
    if (REQUIRED_BODY_FIELDS.includes(key)) {
      if (val !== undefined && val !== null) out[key] = val
      return
    }
    if (val === undefined || val === null || val === '') return
    if (typeof val === 'string' && (val === 'undefined' || val === 'null')) return
    out[key] = val
  })
  return out
}

function serializeRequestData(data, method, header) {
  const cleaned = cleanParams(data)
  const verb = String(method || 'GET').toUpperCase()
  const contentType = String(
    (header && (header['Content-Type'] || header['content-type'])) || ''
  ).toLowerCase()
  if (
    ['POST', 'PUT', 'PATCH'].includes(verb)
    && contentType.includes('application/json')
    && cleaned
    && typeof cleaned === 'object'
  ) {
    return JSON.stringify(cleaned)
  }
  return cleaned
}

function readErrorMessage(data, status) {
  if (!data) return `请求失败(${status})`
  if (typeof data === 'string') return data
  return data.message || data.detail || data.error || `请求失败(${status})`
}

function isHardAuthFailure(status, message) {
  if (status !== 401) return false
  const t = String(message || '')
  if (/认证服务不可用|服务不可用|502|503|504/i.test(t)) return false
  return /账号已被禁用|账号已在其他设备|其他设备登录|登录态无效|未登录|凭证缺失|重新登录|Unauthorized/i.test(t)
}

function stopRealtimeQuietly() {
  try {
    const realtime = require('../services/realtime')
    if (realtime && typeof realtime.stop === 'function') realtime.stop()
  } catch { /* ignore */ }
}

/** 多个 401 并发时只跳转一次登录，避免叠多页 */
function redirectToLoginOnce() {
  if (isAuthRedirectLocked()) return
  setAuthRedirectLock(true)

  clearToken()
  stopRealtimeQuietly()
  try {
    const app = getApp()
    if (app && typeof app.clearUser === 'function') {
      app.clearUser()
    } else if (app && app.globalData) {
      app.globalData.user = null
    }
  } catch { /* ignore */ }

  let onLoginPage = false
  try {
    const pages = getCurrentPages()
    const cur = pages && pages.length ? pages[pages.length - 1] : null
    onLoginPage = !!(cur && cur.route === 'pages/login/login')
  } catch { /* ignore */ }
  if (onLoginPage) return

  wx.showToast({ title: '请重新登录', icon: 'none' })
  setTimeout(() => {
    wx.redirectTo({
      url: '/pages/login/login',
      fail() {
        wx.reLaunch({ url: '/pages/login/login' })
      }
    })
  }, 400)
}

function request(options) {
  const token = getToken()
  const epochAtStart = getSessionEpoch()
  const startedWithToken = !!token
  const header = Object.assign({ 'Content-Type': 'application/json' }, options.header || {})
  if (token) header.Authorization = `Bearer ${token}`

  const body = serializeRequestData(options.data, options.method, header)

  return new Promise((resolve, reject) => {
    wx.request({
      url: options.url.startsWith('http') ? options.url : `${apiBase}${options.url}`,
      method: options.method || 'GET',
      data: body,
      header,
      timeout: options.timeout || 30000,
      success(res) {
        const { statusCode, data } = res
        if (statusCode >= 200 && statusCode < 300) {
          resolve(data)
          return
        }
        const message = readErrorMessage(data, statusCode)
        // 主动退出后的尾巴请求 / 本来就没带 token：静默失败，不跳登录
        const sessionChanged = getSessionEpoch() !== epochAtStart
        if (
          statusCode === 401
          && startedWithToken
          && !sessionChanged
          && !isAuthRedirectLocked()
          && isHardAuthFailure(statusCode, message)
        ) {
          redirectToLoginOnce()
        }
        reject(new Error(message))
      },
      fail(err) {
        reject(new Error(err.errMsg || '网络请求失败，请检查后端是否已启动'))
      }
    })
  })
}

function get(url, data) {
  return request({ url, method: 'GET', data })
}

function post(url, data) {
  return request({ url, method: 'POST', data })
}

function put(url, data) {
  return request({ url, method: 'PUT', data })
}

function del(url, data) {
  return request({ url, method: 'DELETE', data })
}

module.exports = { request, get, post, put, del }
