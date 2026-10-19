/** 浏览器侧 token 仅存 sessionStorage：关闭标签页/浏览器后需重新登录 */
export const TOKEN_STORAGE_KEY = 'campus_delivery_token'

export type TokenPayload = {
  username?: string
  exp?: number
}

/** 清除旧版 localStorage 持久化 token，避免升级后仍自动登录 */
function clearLegacyLocalToken() {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
}

export function getToken() {
  clearLegacyLocalToken()
  return sessionStorage.getItem(TOKEN_STORAGE_KEY)
}

export function setToken(token: string) {
  clearLegacyLocalToken()
  sessionStorage.setItem(TOKEN_STORAGE_KEY, token)
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_STORAGE_KEY)
  clearLegacyLocalToken()
}

function decodeBase64Url(input: string): string {
  const normalized = input.replace(/-/g, '+').replace(/_/g, '/')
  const pad = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4))
  return atob(normalized + pad)
}

export function parseTokenPayload(token: string): TokenPayload | null {
  try {
    const parts = token.split('.')
    if (parts.length < 2) return null
    const json = JSON.parse(decodeBase64Url(parts[1])) as Record<string, unknown>
    return {
      username: typeof json.sub === 'string' ? json.sub : undefined,
      exp: typeof json.exp === 'number' ? json.exp : undefined
    }
  } catch {
    return null
  }
}

export function isTokenExpired(token: string, skewMs = 5000): boolean {
  const payload = parseTokenPayload(token)
  if (!payload?.exp) return false
  return payload.exp * 1000 <= Date.now() + skewMs
}
