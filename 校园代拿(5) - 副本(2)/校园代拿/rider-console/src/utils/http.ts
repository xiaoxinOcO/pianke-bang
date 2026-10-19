import { getToken, clearToken } from './token'

export type AuthLostHandler = (message: string) => void

let onAuthLost: AuthLostHandler | null = null

/** 由 App 注册：401 时同步退出工作台，避免界面仍显示已登录 */
export function setAuthLostHandler(handler: AuthLostHandler | null) {
  onAuthLost = handler
}

async function readErrorText(res: Response) {
  try {
    const data = (await res.json()) as { message?: string }
    if (data?.message) return data.message
  } catch {
    // ignore
  }
  return `请求失败 (${res.status})`
}

export async function request<T>(input: RequestInfo | URL, init?: RequestInit & { timeoutMs?: number }): Promise<T> {
  const token = getToken()
  const headers = new Headers(init?.headers ?? {})
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const timeoutMs = init?.timeoutMs ?? 15000
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    const res = await fetch(input, { ...init, headers, signal: controller.signal })
    if (!res.ok) {
      const errText = await readErrorText(res)
      if (res.status === 401) {
        clearToken()
        onAuthLost?.(errText)
      }
      throw new Error(errText)
    }
    return (await res.json()) as T
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      throw new Error('请求超时，请检查网关或后端服务是否已启动')
    }
    throw err
  } finally {
    window.clearTimeout(timer)
  }
}
