/** 开发环境在 .env.development 里设为 http://localhost:8080；生产留空走同源 /api（须 Nginx 反代 /api） */
const envApiOrigin = (import.meta.env.VITE_API_ORIGIN as string | undefined)?.replace(/\/$/, '') ?? ''

/**
 * HTTPS 页面不能请求 http 接口，浏览器会直接拦截（界面只显示 Failed to fetch）。
 * 同域名部署时应用空 origin 走 /api，由 Nginx 反代到本机 8080。
 */
export const apiOrigin =
  typeof window !== 'undefined' &&
  envApiOrigin.startsWith('http:') &&
  window.location.protocol === 'https:'
    ? ''
    : envApiOrigin

export const apiBase = `${apiOrigin}/api/tasks`
export const authBase = `${apiOrigin}/api/auth`