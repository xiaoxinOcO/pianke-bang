/**
 * 小程序 API 配置
 *
 * 【上传体验版 / 真机】必须保持 ENV = 'release'
 *   apiBase = https://api.xiaoxin1.xyz/api
 *   DNS：api.xiaoxin1.xyz → 47.86.165.82（香港，无需大陆备案）
 *   微信公众平台 → 服务器域名 → request: https://api.xiaoxin1.xyz
 *
 * 【仅开发者工具本地调试】可临时改为 ENV = 'server'，并勾选「不校验合法域名」
 *
 * ENV 说明：
 *   develop - 本机后端 localhost:8080（可开 Mock）
 *   server  - 香港服务器 47.86.165.82（HTTP，仅调试）
 *   release - 正式/体验版 HTTPS 域名（微信合法域名须与此一致）
 */
// 临时真机局域网：develop 指向本机 WLAN。上传体验版前改回 release（域名配置未删除）
const ENV = 'develop' // develop | server | release

const API_ORIGINS = {
  develop: 'http://10.150.199.72:8080',
  server: 'http://47.86.165.82',
  release: 'https://api.xiaoxin1.xyz'
}

const apiOrigin = (API_ORIGINS[ENV] || API_ORIGINS.release).replace(/\/$/, '')

module.exports = {
  ENV,
  apiOrigin,
  apiBase: `${apiOrigin}/api`,
  wsBase: apiOrigin.replace(/^http/, 'ws') + '/ws',
  brandName: '片刻帮',
  brandSlogan: '校园顺路代拿，省时又省心',
  // 临时连本机真实接口。改回纯假数据时设为 true；上传体验版前把 ENV 改回 release
  useMock: false,
  demoAccount: ENV === 'develop' ? {
    username: 'pianke',
    password: '123456',
    label: '测试账号'
  } : null
}
