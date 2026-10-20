const TAB_PATHS = [
  '/pages/hall/hall',
  '/pages/publish/publish',
  '/pages/chat/chat',
  '/pages/profile/profile'
]

/**
 * 打开小程序内页面路径（支持 tabBar / 普通页）
 * @param {string} rawPath 如 /pages/wallet/wallet
 * @returns {boolean} 是否已发起跳转
 */
function openMiniprogramPath(rawPath) {
  const path = String(rawPath || '').trim()
  if (!path || !path.startsWith('/')) return false
  const pure = path.split('?')[0]
  if (TAB_PATHS.includes(pure)) {
    wx.switchTab({ url: pure })
  } else {
    wx.navigateTo({
      url: path,
      fail: () => {
        wx.showToast({ title: '页面打不开', icon: 'none' })
      }
    })
  }
  return true
}

module.exports = {
  TAB_PATHS,
  openMiniprogramPath
}
