export const TOKEN_STORAGE_KEY = 'campus_rider_token'

function storage(): Storage {
  try {
    return localStorage
  } catch {
    return sessionStorage
  }
}

export function getToken() {
  const local = localStorage.getItem(TOKEN_STORAGE_KEY)
  if (local) return local
  // 兼容旧版 sessionStorage
  const session = sessionStorage.getItem(TOKEN_STORAGE_KEY)
  if (session) {
    localStorage.setItem(TOKEN_STORAGE_KEY, session)
    sessionStorage.removeItem(TOKEN_STORAGE_KEY)
    return session
  }
  return null
}

export function setToken(token: string) {
  storage().setItem(TOKEN_STORAGE_KEY, token)
  try {
    sessionStorage.removeItem(TOKEN_STORAGE_KEY)
  } catch {
    /* ignore */
  }
}

export function clearToken() {
  try {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
  } catch {
    /* ignore */
  }
  try {
    sessionStorage.removeItem(TOKEN_STORAGE_KEY)
  } catch {
    /* ignore */
  }
}

export function parseUsername(token: string): string {
  try {
    const part = token.split('.')[1]
    if (!part) return ''
    const json = JSON.parse(atob(part.replace(/-/g, '+').replace(/_/g, '/'))) as { sub?: string }
    return String(json.sub || '').trim()
  } catch {
    return ''
  }
}
