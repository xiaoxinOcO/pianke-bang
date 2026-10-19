const TOKEN_KEY = 'campus_token'
const USER_KEY = 'campus_user'

/** 防止 401 / requireLogin 并发时叠多个登录页 */
let authRedirectLocked = false

/**
 * 会话代数：主动退出 / stop 轮询时自增。
 * 进行中的旧请求若再返回 401，可据此静默忽略，避免刷登录页。
 */
let sessionEpoch = 0

function getSessionEpoch() {
  return sessionEpoch
}

function bumpSessionEpoch() {
  sessionEpoch += 1
  return sessionEpoch
}

function isAuthRedirectLocked() {
  return authRedirectLocked
}

function setAuthRedirectLock(locked) {
  authRedirectLocked = !!locked
}

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function setToken(token) {
  authRedirectLocked = false
  wx.setStorageSync(TOKEN_KEY, token || '')
}

function clearToken() {
  wx.removeStorageSync(TOKEN_KEY)
  wx.removeStorageSync(USER_KEY)
}

/**
 * 主动退出：先抬升会话代数并锁住自动跳登录，再清本地凭证。
 * 由 app.clearUser 调用；业务页不要单独先 clearToken 再 stop。
 */
function beginLocalLogout() {
  bumpSessionEpoch()
  authRedirectLocked = true
  clearToken()
}

function getUser() {
  return wx.getStorageSync(USER_KEY) || null
}

function setUser(user) {
  wx.setStorageSync(USER_KEY, user || null)
}

function isLoggedIn() {
  return !!getToken()
}

function buildLoginUrl(redirectUrl) {
  return redirectUrl
    ? `/pages/login/login?redirect=${encodeURIComponent(redirectUrl)}`
    : '/pages/login/login'
}

function goLogin(redirectUrl) {
  if (authRedirectLocked) return
  authRedirectLocked = true
  let onLoginPage = false
  try {
    const pages = getCurrentPages()
    const cur = pages && pages.length ? pages[pages.length - 1] : null
    onLoginPage = !!(cur && cur.route === 'pages/login/login')
  } catch { /* ignore */ }
  if (onLoginPage) return
  wx.navigateTo({
    url: buildLoginUrl(redirectUrl),
    fail() {
      wx.redirectTo({ url: buildLoginUrl(redirectUrl) })
    }
  })
}

function requireLogin(redirectUrl) {
  if (isLoggedIn()) return true
  goLogin(redirectUrl)
  return false
}

/** 子页面 onLoad：未登录时跳转登录，并标记等待用户选择 */
function useLoginGuard(page, redirectUrl) {
  if (isLoggedIn()) return true
  page._loginGuardPending = true
  goLogin(redirectUrl || `/${page.route}`)
  return false
}

/**
 * 子页面 onShow：用户从登录页返回且仍未登录时，自动退回上一页，避免反复弹登录
 * 返回 true 表示已登录，可继续加载页面
 */
function checkLoginGuard(page) {
  if (isLoggedIn()) {
    page._loginGuardPending = false
    authRedirectLocked = false
    return true
  }
  if (page._loginGuardPending) {
    page._loginGuardPending = false
    authRedirectLocked = false
    wx.navigateBack()
    return false
  }
  return false
}

module.exports = {
  TOKEN_KEY,
  USER_KEY,
  getToken,
  setToken,
  clearToken,
  beginLocalLogout,
  getUser,
  setUser,
  isLoggedIn,
  requireLogin,
  useLoginGuard,
  checkLoginGuard,
  isAuthRedirectLocked,
  setAuthRedirectLock,
  getSessionEpoch,
  bumpSessionEpoch
}
