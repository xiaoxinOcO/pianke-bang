import { clearToken, getToken } from './token'

export type UnauthorizedContext = {
  hadToken: boolean
  errText: string
}

/** 读取错误响应正文（Spring JSON / Problem+JSON / 纯文本） */
export async function readErrorText(res: Response): Promise<string> {
  const status = res.status
  const fallback = `请求失败(${status})`
  const text = await res.text()
  const trimmed = text.trim()
  // Nginx 502/504 常为 HTML 错误页，避免用户只看到「请求失败」
  if (status === 504 && (!trimmed || trimmed.startsWith('<'))) {
    return '网关超时(504)：后端未及时响应。请 SSH 执行 systemctl status campus、journalctl -u campus -n 80，确认 MySQL 与 Java 正常；并检查 Nginx proxy_read_timeout、proxy_pass 是否指向 127.0.0.1:8080。'
  }
  if (status === 502 && (!trimmed || trimmed.startsWith('<'))) {
    return '网关错误(502)：连不上后端。请确认 campus 服务已启动且端口与 Nginx proxy_pass 一致。'
  }
  if (status === 503 && (!trimmed || /^service unavailable$/i.test(trimmed) || trimmed.startsWith('<'))) {
    return '后端服务不可用(503)：请先启动 campus-cloud 网关（默认端口 8080），再刷新页面。'
  }
  if (!trimmed) return fallback
  if (trimmed.startsWith('<')) return fallback
  try {
    const data = JSON.parse(trimmed) as Record<string, unknown>
    const msg =
      (typeof data.message === 'string' && data.message) ||
      (typeof data.detail === 'string' && data.detail) ||
      (typeof data.error === 'string' && data.error && data.error !== 'Unauthorized' ? data.error : '')
    if (msg) return String(msg)
  } catch {
    if (trimmed.length < 500) return trimmed
  }
  return fallback
}

/** 已从 res.json() 解析出的错误体中提取可读文案，避免再次读取 Response 流 */
export function messageFromParsedBody(
  data: Record<string, unknown> | null | undefined,
  status: number
): string {
  const fallback = `请求失败(${status})`
  if (!data) return fallback
  const msg =
    (typeof data.message === 'string' && data.message) ||
    (typeof data.detail === 'string' && data.detail) ||
    (typeof data.error === 'string' && data.error && data.error !== 'Unauthorized' ? data.error : '')
  return msg ? String(msg) : fallback
}

/** 仅当服务端明确返回「禁用」语义时判定，避免与「账号已在其他设备登录」等混淆 */
export function isAuthDisabledMessage(t: string): boolean {
  if (/账号已被禁用|已被管理员禁用|管理员已禁用/.test(t)) return true
  if (/禁用/.test(t) && /联系管理员/.test(t)) return true
  return false
}

export function isAuthKickedElsewhereMessage(t: string): boolean {
  return /账号已在其他设备|其他设备登录|别处登录/.test(t)
}

/** 网关/网络抖动导致的 401，不应清 token */
export function isTransientUnauthorizedMessage(t: string): boolean {
  return /认证服务不可用|服务不可用|网关|502|503|504|无法连接后端|Failed to fetch|NetworkError|Load failed/i.test(t)
}

export function isHardAuthFailure(status: number, message: string): boolean {
  if (status !== 401) return false
  if (isTransientUnauthorizedMessage(message)) return false
  return (
    isAuthDisabledMessage(message) ||
    isAuthKickedElsewhereMessage(message) ||
    /登录态无效|未登录|凭证缺失|重新登录/.test(message)
  )
}

/** fetch 级网络错误（无响应体）转可读说明 */
export function formatNetworkErrorMessage(err: unknown): string {
  const msg = err instanceof Error ? err.message : String(err)
  if (
    msg === 'Failed to fetch' ||
    msg === 'NetworkError when attempting to fetch resource.' ||
    /Load failed/i.test(msg)
  ) {
    return '无法连接后端：请先启动 campus-cloud 网关（默认端口 8080），再刷新页面。开发环境需在本目录执行 npm run dev，接口经 Vite 代理到 /api。'
  }
  return msg
}

export type AuthMeResult<T> =
  | { ok: true; data: T }
  | { ok: false; status: number; message: string }

/** 页面刷新恢复登录态：直连 /me，不触发全局 401 登出逻辑 */
export async function fetchAuthMe<T>(authBase: string, token: string): Promise<AuthMeResult<T>> {
  try {
    const res = await fetch(`${authBase}/me`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (res.ok) {
      return { ok: true, data: (await res.json()) as T }
    }
    return { ok: false, status: res.status, message: await readErrorText(res) }
  } catch (err) {
    return { ok: false, status: 0, message: formatNetworkErrorMessage(err) }
  }
}

export async function fetchAuthMeWithRetry<T>(
  authBase: string,
  token: string,
  attempts = 3,
  delayMs = 350
): Promise<AuthMeResult<T>> {
  let last: AuthMeResult<T> = { ok: false, status: 0, message: '未知错误' }
  for (let i = 0; i < attempts; i++) {
    last = await fetchAuthMe<T>(authBase, token)
    if (last.ok) return last
    if (isHardAuthFailure(last.status, last.message)) return last
    if (i < attempts - 1) {
      await new Promise((resolve) => window.setTimeout(resolve, delayMs))
    }
  }
  return last
}

export function createAuthenticatedRequest(
  onUnauthorized: (ctx: UnauthorizedContext) => void | Promise<void>,
  shouldSuppressUnauthorized?: () => boolean
) {
  return async function request<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
    const token = getToken()
    const hadToken = !!token
    const headers = new Headers(init?.headers ?? {})
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }

    const res = await fetch(input, {
      ...init,
      headers
    })

    if (!res.ok) {
      const errText = await readErrorText(res)
      if (res.status === 401) {
        const suppressed = shouldSuppressUnauthorized?.() === true
        const transient = isTransientUnauthorizedMessage(errText)
        const hardFailure = isHardAuthFailure(res.status, errText)
        if (!suppressed && hardFailure) {
          clearToken()
          await onUnauthorized({ hadToken, errText })
        }
        if (hadToken) {
          const t = String(errText || '').trim()
          if (transient) {
            throw new Error(t || '认证服务暂时不可用，请稍后重试')
          }
          const generic401 = /^请求失败\s*\(\s*401\s*\)$/.test(t)
          if (isAuthDisabledMessage(t)) {
            throw new Error('该账号已被管理员禁用，请联系管理员后重试。')
          }
          if (t && !generic401) {
            throw new Error(
              isAuthKickedElsewhereMessage(t) ? '你的账号在别处登录，请重新登录' : t
            )
          }
          throw new Error('你的账号在别处登录，请重新登录')
        }
        throw new Error(errText)
      }
      throw new Error(errText)
    }

    const text = await res.text()
    const trimmed = text.trim()
    if (!trimmed) {
      return undefined as T
    }
    try {
      return JSON.parse(trimmed) as T
    } catch {
      return trimmed as unknown as T
    }
  }
}
