import { getToken, clearToken } from './token'

async function readErrorText(res: Response) {
  try {
    const data = (await res.json()) as { message?: string }
    if (data?.message) return data.message
  } catch {
    // ignore
  }
  return `请求失败 (${res.status})`
}

export async function request<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  const token = getToken()
  const headers = new Headers(init?.headers ?? {})
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(input, { ...init, headers })
  if (!res.ok) {
    const errText = await readErrorText(res)
    if (res.status === 401) {
      clearToken()
    }
    throw new Error(errText)
  }
  return (await res.json()) as T
}
