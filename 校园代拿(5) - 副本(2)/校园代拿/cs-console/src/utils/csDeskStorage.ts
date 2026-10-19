import {
  DEFAULT_QUICK_REPLY_GROUPS,
  type QuickReplyGroup
} from '../constants/defaultQuickReplies'

const QR_KEY = 'cs_quick_replies_v1'
const NOTES_KEY = 'cs_internal_notes_v1'
const NOTIFY_KEY = 'cs_notify_prefs_v1'
const SESSION_KEY = 'cs_session_meta_v1'
const TODAY_KEY = 'cs_today_stats_v1'

export type SessionStatus = 'waiting' | 'serving' | 'ended'

export type SessionMeta = {
  firstSeenAt?: number
  acceptedAt?: number
  lastAgentAt?: number
  lastUserAt?: number
  endedAt?: number
  waitingSince?: number
}

export type NotifyPrefs = {
  sound: boolean
  desktop: boolean
}

export type TodayStats = {
  day: string
  handled: string[]
  firstReplyMs: number[]
}

function todayKey() {
  const d = new Date()
  return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`
}

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return fallback
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

function writeJson(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // ignore quota
  }
}

export function loadQuickReplyGroups(): QuickReplyGroup[] {
  const data = readJson<QuickReplyGroup[] | null>(QR_KEY, null)
  if (!Array.isArray(data) || data.length === 0) return structuredClone(DEFAULT_QUICK_REPLY_GROUPS)
  return data
}

export function saveQuickReplyGroups(groups: QuickReplyGroup[]) {
  writeJson(QR_KEY, groups)
}

export function resetQuickReplyGroups(): QuickReplyGroup[] {
  const next = structuredClone(DEFAULT_QUICK_REPLY_GROUPS)
  saveQuickReplyGroups(next)
  return next
}

export function loadNotes(): Record<string, string> {
  return readJson<Record<string, string>>(NOTES_KEY, {})
}

export function saveNote(username: string, note: string) {
  const map = loadNotes()
  const name = username.trim()
  if (!name) return
  const text = note.trim()
  if (!text) delete map[name]
  else map[name] = text
  writeJson(NOTES_KEY, map)
}

export function loadNotifyPrefs(): NotifyPrefs {
  return readJson<NotifyPrefs>(NOTIFY_KEY, { sound: true, desktop: false })
}

export function saveNotifyPrefs(prefs: NotifyPrefs) {
  writeJson(NOTIFY_KEY, prefs)
}

export function loadSessionMetaMap(): Record<string, SessionMeta> {
  return readJson<Record<string, SessionMeta>>(SESSION_KEY, {})
}

export function saveSessionMetaMap(map: Record<string, SessionMeta>) {
  writeJson(SESSION_KEY, map)
}

export function touchSessionMeta(
  map: Record<string, SessionMeta>,
  username: string,
  patch: Partial<SessionMeta>
): Record<string, SessionMeta> {
  const name = username.trim()
  if (!name) return map
  const next: SessionMeta = { ...(map[name] || {}), ...patch }
  // undefined 表示清除字段（JSON 持久化时也不会留下脏值）
  ;(Object.keys(patch) as (keyof SessionMeta)[]).forEach((key) => {
    if (patch[key] === undefined) delete next[key]
  })
  return { ...map, [name]: next }
}

export function deriveSessionStatus(
  meta: SessionMeta | undefined,
  _unread: number
): SessionStatus {
  if (meta?.endedAt) return 'ended'
  if (meta?.acceptedAt) return 'serving'
  // 仅用户转人工后进入等待；无进行中轮次的历史联系人归为已结束
  if (meta?.waitingSince) return 'waiting'
  return 'ended'
}

export function loadTodayStats(): TodayStats {
  const empty: TodayStats = { day: todayKey(), handled: [], firstReplyMs: [] }
  const data = readJson<TodayStats>(TODAY_KEY, empty)
  if (data.day !== todayKey()) return empty
  return {
    day: data.day,
    handled: Array.isArray(data.handled) ? data.handled : [],
    firstReplyMs: Array.isArray(data.firstReplyMs) ? data.firstReplyMs : []
  }
}

export function saveTodayStats(stats: TodayStats) {
  writeJson(TODAY_KEY, { ...stats, day: todayKey() })
}

export function markHandledToday(username: string) {
  const stats = loadTodayStats()
  const name = username.trim()
  if (!name) return stats
  if (!stats.handled.includes(name)) stats.handled.push(name)
  saveTodayStats(stats)
  return stats
}

export function recordFirstReplyMs(ms: number) {
  const stats = loadTodayStats()
  if (Number.isFinite(ms) && ms >= 0) {
    stats.firstReplyMs.push(Math.round(ms))
    if (stats.firstReplyMs.length > 200) stats.firstReplyMs = stats.firstReplyMs.slice(-200)
    saveTodayStats(stats)
  }
  return stats
}

export function formatDuration(ms: number | null | undefined) {
  if (ms == null || !Number.isFinite(ms) || ms < 0) return '—'
  const sec = Math.floor(ms / 1000)
  if (sec < 60) return `${sec}秒`
  const min = Math.floor(sec / 60)
  const rem = sec % 60
  if (min < 60) return rem ? `${min}分${rem}秒` : `${min}分`
  const hour = Math.floor(min / 60)
  const m = min % 60
  return m ? `${hour}时${m}分` : `${hour}时`
}

export function playNotifyBeep() {
  try {
    const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
    if (!Ctx) return
    const ctx = new Ctx()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'sine'
    osc.frequency.value = 880
    gain.gain.value = 0.04
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.start()
    osc.stop(ctx.currentTime + 0.12)
    window.setTimeout(() => void ctx.close(), 300)
  } catch {
    // ignore
  }
}

export async function requestDesktopNotify(title: string, body: string) {
  if (typeof Notification === 'undefined') return
  if (Notification.permission === 'denied') return
  if (Notification.permission === 'default') {
    const perm = await Notification.requestPermission()
    if (perm !== 'granted') return
  }
  try {
    new Notification(title, { body, silent: true })
  } catch {
    // ignore
  }
}
