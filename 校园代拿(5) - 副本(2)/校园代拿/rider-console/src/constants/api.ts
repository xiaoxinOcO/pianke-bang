export const apiOrigin =
  (import.meta.env.VITE_API_ORIGIN as string | undefined)?.replace(/\/$/, '') ?? ''

export const authBase = `${apiOrigin}/api/auth`
export const tasksBase = `${apiOrigin}/api/tasks`
export const walletBase = `${apiOrigin}/api/wallet`
export const chatBase = `${apiOrigin}/api/chat`
export const mainAppUrl =
  (import.meta.env.VITE_MAIN_APP_URL as string | undefined)?.replace(/\/$/, '') ||
  'http://localhost:5173'
