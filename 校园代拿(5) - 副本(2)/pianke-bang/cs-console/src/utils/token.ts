export const TOKEN_STORAGE_KEY = 'campus_cs_token'

export function getToken() {
  return sessionStorage.getItem(TOKEN_STORAGE_KEY)
}

export function setToken(token: string) {
  sessionStorage.setItem(TOKEN_STORAGE_KEY, token)
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_STORAGE_KEY)
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
