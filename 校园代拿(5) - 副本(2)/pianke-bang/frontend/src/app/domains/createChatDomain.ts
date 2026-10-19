import { computed, nextTick, ref, type Ref } from 'vue'
import { apiOrigin, authBase } from '../../constants/api'
import { isCustomerServiceUsername, resolveCustomerServiceAvatar } from '../../constants/brand'
import { isSystemNoticeContact, SYSTEM_NOTICE_USERNAME } from '../../constants/systemNotice'
import type {
  ChatContactItem,
  ChatMessageItem,
  NoticeItem,
  PageView,
  SystemNoticeFeedItem,
  TaskItem,
  TaskStatus,
  UserPublicProfile,
  UserSystemMessageItem
} from '../../types/app'
import {
  chatHiddenStoreKey,
  chatLatestSeenStoreKey,
  chatPinnedStoreKey,
  chatReadStoreKey,
  systemNoticeReadStoreKey
} from '../../utils/chatStoreKeys'
import { statusLabelMap } from '../../utils/taskStatus'
import { serializeTaskInquiryMessage } from '../../utils/taskInquiryMessage'
import { usernameMatches } from '../../utils/taskParse'
import type { SupportChatMessage } from './createSupportDomain'

type ChatRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type ChatDomainDeps = {
  isLoggedIn: Ref<boolean>
  myName: Ref<string>
  myAvatarUrl: Ref<string>
  request: ChatRequest
  errorMsg: Ref<string>
  currentView: Ref<PageView>
  setView: (view: PageView) => void
  showUserDropdown: Ref<boolean>
  showRealtimeToast: (payload: {
    title: string
    message: string
    kind: 'chat' | 'task'
    chatUser?: string
  }) => void
  showProfileNotice: (text: string) => void
  closeTaskDetail: () => void
  fetchTasks: () => void | Promise<void>
  fetchPublicNotices: () => void | Promise<void>
  fetchUserSystemMessages: () => void | Promise<void>
  publicNotices: Ref<NoticeItem[]>
  userSystemMessages: Ref<UserSystemMessageItem[]>
  userSystemMessagesUnread: Ref<number>
  normalizeImageUrl: (url?: string) => string
  realtimeConnected: Ref<boolean>
  supportHumanMode: Ref<boolean>
  supportAssignedAgent: Ref<string>
  supportChatMessages: Ref<SupportChatMessage[]>
  supportIngestedChatIds: Ref<Set<number>>
  supportHumanHistoryAfterId: Ref<number>
}

/**
 * 通讯录、未读与会话：联系人偏好、轮询、系统通知入口、发图与向客服发订单。
 */
export function createChatDomain(deps: ChatDomainDeps) {
  const chatTarget = ref('')
  const chatInput = ref('')
  const chatImageDraft = ref('')
  const chatMessages = ref<ChatMessageItem[]>([])
  const chatContacts = ref<ChatContactItem[]>([])
  const temporaryChatContacts = ref<string[]>([])
  const chatPinnedUsernames = ref<string[]>([])
  const chatHiddenUsernames = ref<string[]>([])
  const unreadMap = ref<Record<string, number>>({})
  const readUpToIncomingIdMap = ref<Record<string, number>>({})
  /** 各联系人已同步到的最新 incoming 消息 id，用于判断「真正新消息」再弹 toast */
  const lastSyncedIncomingIdMap = ref<Record<string, number>>({})
  const chatMessagesRef = ref<HTMLDivElement | null>(null)
  const contactProfileLoading = ref(false)
  const selectedContactProfile = ref<UserPublicProfile | null>(null)
  const activeContactCard = ref('')
  const showContactProfileModal = ref(false)
  const sendingTaskToCsId = ref<number | null>(null)
  let chatPollingTimer: number | null = null
  /** 登录后首轮未读同步完成前，不弹「新消息」toast */
  let chatUnreadInitialized = false
  const CHAT_IMAGE_PREFIX = '[CHAT_IMAGE]'

  const totalUnread = computed(() => {
    let sum = 0
    const systemUnread = unreadMap.value[SYSTEM_NOTICE_USERNAME] || 0
    sum += systemUnread
    for (const c of chatContacts.value) {
      const name = String(c.username || '').trim()
      if (!name || name === SYSTEM_NOTICE_USERNAME) continue
      sum += unreadMap.value[name] || 0
    }
    return sum
  })

  function shouldTrackChatUnread(username?: string) {
    const name = String(username || '').trim()
    if (!name || name === SYSTEM_NOTICE_USERNAME) return false
    if (isChatContactHidden(name)) return false
    if (isCustomerServiceUsername(name)) return false
    return chatContacts.value.some((c) => c.username === name)
  }

  function pruneGhostChatUnread() {
    const visible = new Set(
      chatContacts.value.map((c) => String(c.username || '').trim()).filter(Boolean)
    )
    visible.add(SYSTEM_NOTICE_USERNAME)
    let changed = false
    const next: Record<string, number> = { ...unreadMap.value }
    for (const key of Object.keys(next)) {
      if (visible.has(key)) continue
      if ((next[key] || 0) !== 0) {
        next[key] = 0
        changed = true
      }
    }
    if (changed) unreadMap.value = next
  }

  function getSystemNoticeLastReadId() {
    try {
      const raw = localStorage.getItem(systemNoticeReadStoreKey(deps.myName.value))
      const id = raw ? Number(JSON.parse(raw)) : 0
      return Number.isFinite(id) ? id : 0
    } catch {
      return 0
    }
  }

  function syncSystemNoticeUnread() {
    const lastRead = getSystemNoticeLastReadId()
    const platformUnread = deps.publicNotices.value.filter((n) => n.published && n.id > lastRead).length
    unreadMap.value[SYSTEM_NOTICE_USERNAME] = platformUnread + deps.userSystemMessagesUnread.value
  }

  async function markUserSystemMessagesRead() {
    if (!deps.isLoggedIn.value) return
    try {
      await deps.request(`${apiOrigin}/api/system-messages/read`, { method: 'POST' })
      deps.userSystemMessages.value = deps.userSystemMessages.value.map((m) => ({ ...m, readFlag: true }))
      deps.userSystemMessagesUnread.value = 0
    } catch {
      // ignore
    }
  }

  function markSystemNoticeRead() {
    const maxId = deps.publicNotices.value.reduce((max, n) => Math.max(max, n.id || 0), 0)
    try {
      localStorage.setItem(systemNoticeReadStoreKey(deps.myName.value), JSON.stringify(maxId))
    } catch {
      // ignore
    }
    void markUserSystemMessagesRead()
    unreadMap.value[SYSTEM_NOTICE_USERNAME] = 0
  }

  const publishedNoticesForChat = computed(() =>
    [...deps.publicNotices.value]
      .filter((n) => n.published)
      .sort((a, b) => {
        if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
        const ta = Date.parse(String(a.updatedAt || a.createdAt || '')) || 0
        const tb = Date.parse(String(b.updatedAt || b.createdAt || '')) || 0
        return tb - ta
      })
  )

  const systemNoticeFeed = computed<SystemNoticeFeedItem[]>(() => {
    const timeAsc = (a: SystemNoticeFeedItem, b: SystemNoticeFeedItem) =>
      (Date.parse(a.time) || 0) - (Date.parse(b.time) || 0)

    const platformItems: SystemNoticeFeedItem[] = publishedNoticesForChat.value.map((n) => ({
      key: `platform-${n.id}`,
      kind: 'platform',
      title: n.title,
      content: n.content,
      time: String(n.updatedAt || n.createdAt || ''),
      pinned: n.pinned,
      unread: n.id > getSystemNoticeLastReadId()
    }))
    const personalItems: SystemNoticeFeedItem[] = deps.userSystemMessages.value.map((m) => ({
      key: `personal-${m.id}`,
      kind: 'personal',
      title: m.title,
      content: m.content,
      time: String(m.createdAt || ''),
      category: m.category,
      unread: !m.readFlag
    }))

    const allItems = [...platformItems, ...personalItems]
    const pinned = allItems.filter((item) => item.pinned).sort(timeAsc)
    const normal = allItems.filter((item) => !item.pinned).sort(timeAsc)
    return [...pinned, ...normal]
  })

  function loadChatLocalPrefsForUser(username: string) {
    try {
      const pinnedRaw = localStorage.getItem(chatPinnedStoreKey(username))
      chatPinnedUsernames.value = pinnedRaw ? (JSON.parse(pinnedRaw) as string[]) : []
      if (!Array.isArray(chatPinnedUsernames.value)) chatPinnedUsernames.value = []
    } catch {
      chatPinnedUsernames.value = []
    }
    try {
      const hiddenRaw = localStorage.getItem(chatHiddenStoreKey(username))
      chatHiddenUsernames.value = hiddenRaw ? (JSON.parse(hiddenRaw) as string[]) : []
      if (!Array.isArray(chatHiddenUsernames.value)) chatHiddenUsernames.value = []
    } catch {
      chatHiddenUsernames.value = []
    }
  }

  function persistChatPinnedForUser(username: string) {
    try {
      localStorage.setItem(chatPinnedStoreKey(username), JSON.stringify(chatPinnedUsernames.value))
    } catch {
      // ignore storage errors
    }
  }

  function persistChatHiddenForUser(username: string) {
    try {
      localStorage.setItem(chatHiddenStoreKey(username), JSON.stringify(chatHiddenUsernames.value))
    } catch {
      // ignore storage errors
    }
  }

  function isChatContactHidden(name: string) {
    return chatHiddenUsernames.value.includes(name)
  }

  function unhideChatContact(name: string) {
    const trimmed = name.trim()
    if (!trimmed) return
    if (!chatHiddenUsernames.value.includes(trimmed)) return
    chatHiddenUsernames.value = chatHiddenUsernames.value.filter((u) => u !== trimmed)
    persistChatHiddenForUser(deps.myName.value)
  }

  function isChatContactPinned(name: string) {
    return chatPinnedUsernames.value.includes(name)
  }

  function sortChatContactsList(list: ChatContactItem[]) {
    const pinOrder = chatPinnedUsernames.value
    const pinRank = (username: string) => {
      if (isSystemNoticeContact(username)) return -1
      const idx = pinOrder.indexOf(username)
      return idx >= 0 ? idx : Number.MAX_SAFE_INTEGER
    }
    return [...list]
      .filter((c) => !isSystemNoticeContact(c.username))
      .sort((a, b) => {
        const ar = pinRank(a.username)
        const br = pinRank(b.username)
        if (ar !== br) return ar - br
        return a.username.localeCompare(b.username, 'zh-CN')
      })
  }

  function filterVisibleChatContacts(list: ChatContactItem[]) {
    const hidden = new Set(chatHiddenUsernames.value)
    return sortChatContactsList(
      list.filter((c) => {
        if (!c.username || hidden.has(c.username)) return false
        if (c.customerServiceStaff) return false
        if (isCustomerServiceUsername(c.username)) return false
        return true
      })
    )
  }

  function pinChatContact(username: string) {
    const name = username.trim()
    if (!name || isSystemNoticeContact(name)) return
    if (chatPinnedUsernames.value.includes(name)) {
      chatPinnedUsernames.value = chatPinnedUsernames.value.filter((u) => u !== name)
    } else {
      chatPinnedUsernames.value = [name, ...chatPinnedUsernames.value.filter((u) => u !== name)]
    }
    persistChatPinnedForUser(deps.myName.value)
    chatContacts.value = sortChatContactsList(chatContacts.value)
  }

  function deleteChatContact(username: string) {
    const name = username.trim()
    if (!name || isSystemNoticeContact(name)) return
    if (!chatHiddenUsernames.value.includes(name)) {
      chatHiddenUsernames.value = [...chatHiddenUsernames.value, name]
      persistChatHiddenForUser(deps.myName.value)
    }
    chatPinnedUsernames.value = chatPinnedUsernames.value.filter((u) => u !== name)
    persistChatPinnedForUser(deps.myName.value)
    chatContacts.value = chatContacts.value.filter((c) => c.username !== name)
    if (chatTarget.value.trim() === name) {
      chatTarget.value = chatContacts.value[0]?.username || ''
      chatMessages.value = []
    }
    unreadMap.value[name] = 0
    pruneGhostChatUnread()
  }

  async function clearAllChatUnread() {
    if (!deps.isLoggedIn.value) return
    const names = chatContacts.value.map((c) => c.username).filter(Boolean)
    if (names.length > 0) {
      await Promise.all(
        names.map(async (name) => {
          try {
            const history = await deps.request<ChatMessageItem[]>(
              `${apiOrigin}/api/chat/history?targetUser=${encodeURIComponent(name)}`
            )
            const incoming = history.filter((m) => m.fromUsername === name)
            const latestIncomingId = incoming.length > 0 ? Math.max(...incoming.map((m) => m.id)) : 0
            readUpToIncomingIdMap.value[name] = latestIncomingId
            unreadMap.value[name] = 0
            markIncomingSynced(name, latestIncomingId)
          } catch {
            unreadMap.value[name] = 0
          }
        })
      )
      persistReadUpToMapForUser(deps.myName.value)
    }
    markSystemNoticeRead()
    deps.showProfileNotice('已全部标记为已读')
  }

  function loadReadUpToMapForUser(username: string) {
    try {
      const raw = localStorage.getItem(chatReadStoreKey(username))
      if (!raw) {
        readUpToIncomingIdMap.value = {}
        return
      }
      const parsed = JSON.parse(raw) as Record<string, number>
      readUpToIncomingIdMap.value = parsed && typeof parsed === 'object' ? parsed : {}
    } catch {
      readUpToIncomingIdMap.value = {}
    }
  }

  function persistReadUpToMapForUser(username: string) {
    try {
      localStorage.setItem(chatReadStoreKey(username), JSON.stringify(readUpToIncomingIdMap.value))
    } catch {
      // ignore storage errors
    }
  }

  function loadLatestSeenMapForUser(username: string) {
    try {
      const raw = localStorage.getItem(chatLatestSeenStoreKey(username))
      if (!raw) {
        lastSyncedIncomingIdMap.value = {}
        return
      }
      const parsed = JSON.parse(raw) as Record<string, number>
      lastSyncedIncomingIdMap.value = parsed && typeof parsed === 'object' ? parsed : {}
    } catch {
      lastSyncedIncomingIdMap.value = {}
    }
  }

  function persistLatestSeenMapForUser(username: string) {
    try {
      localStorage.setItem(chatLatestSeenStoreKey(username), JSON.stringify(lastSyncedIncomingIdMap.value))
    } catch {
      // ignore storage errors
    }
  }

  function markIncomingSynced(contact: string, latestIncomingId: number) {
    if (!contact || latestIncomingId <= 0) return
    const prev = lastSyncedIncomingIdMap.value[contact] ?? 0
    const next = Math.max(prev, latestIncomingId)
    if (next === prev) return
    lastSyncedIncomingIdMap.value = { ...lastSyncedIncomingIdMap.value, [contact]: next }
    if (deps.myName.value) {
      persistLatestSeenMapForUser(deps.myName.value)
    }
  }

  function shouldNotifyChatMessage(contact: string, messageId: number) {
    if (!chatUnreadInitialized || !messageId) return false
    const readUpTo = readUpToIncomingIdMap.value[contact] || 0
    if (messageId <= readUpTo) return false
    const latestSeen = lastSyncedIncomingIdMap.value[contact]
    if (latestSeen == null) return false
    return messageId > latestSeen
  }

  function scrollChatToBottom(force = false) {
    const el = chatMessagesRef.value
    if (!el) return

    if (force) {
      el.scrollTop = el.scrollHeight
      requestAnimationFrame(() => {
        const box = chatMessagesRef.value
        if (box) box.scrollTop = box.scrollHeight
      })
      return
    }

    const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    if (distanceToBottom < 80) {
      el.scrollTop = el.scrollHeight
    }
  }

  function bindChatMessagesEl(el: unknown) {
    if (el == null) {
      chatMessagesRef.value = null
      return
    }
    if (el instanceof HTMLDivElement) {
      chatMessagesRef.value = el
      return
    }
    const root = (el as { $el?: unknown }).$el
    chatMessagesRef.value = root instanceof HTMLDivElement ? root : null
  }

  function shouldShowChatDivider(index: number) {
    if (index <= 0) return true
    const current = chatMessages.value[index]
    const prev = chatMessages.value[index - 1]
    if (!current?.createdAt || !prev?.createdAt) return true
    const currentDate = new Date(current.createdAt)
    const prevDate = new Date(prev.createdAt)
    if (Number.isNaN(currentDate.getTime()) || Number.isNaN(prevDate.getTime())) return true
    return currentDate.toDateString() !== prevDate.toDateString()
  }

  function chatAvatarUrl(username: string) {
    if (isSystemNoticeContact(username)) return ''
    if (!username) return ''
    if (username === deps.myName.value) {
      const mine = deps.myAvatarUrl.value || ''
      return mine ? deps.normalizeImageUrl(mine) : ''
    }
    const contact = chatContacts.value.find((c) => c.username === username)
    const profile =
      selectedContactProfile.value?.username === username ? selectedContactProfile.value : null
    const raw = resolveCustomerServiceAvatar(
      username,
      contact?.avatarUrl || profile?.avatarUrl,
      contact?.customerServiceStaff || profile?.customerServiceStaff
    )
    return raw ? deps.normalizeImageUrl(raw) : ''
  }

  function applyContactProfileFields(contact: ChatContactItem, profile: UserPublicProfile): ChatContactItem {
    const avatarUrl = resolveCustomerServiceAvatar(
      contact.username,
      profile.avatarUrl || contact.avatarUrl,
      profile.customerServiceStaff ?? contact.customerServiceStaff
    )
    return {
      ...contact,
      avatarUrl,
      riderApproved: profile.riderApproved ?? contact.riderApproved,
      customerServiceStaff: profile.customerServiceStaff ?? contact.customerServiceStaff
    }
  }

  function normalizeChatContact(contact: ChatContactItem): ChatContactItem {
    return {
      ...contact,
      avatarUrl: resolveCustomerServiceAvatar(contact.username, contact.avatarUrl, contact.customerServiceStaff)
    }
  }

  async function syncChatUnreadForContact(name: string, notify: boolean) {
    if (!shouldTrackChatUnread(name) && name !== SYSTEM_NOTICE_USERNAME) {
      unreadMap.value[name] = 0
      return
    }
    const history = await deps.request<ChatMessageItem[]>(
      `${apiOrigin}/api/chat/history?targetUser=${encodeURIComponent(name)}`
    )
    const incoming = history.filter((m) => m.fromUsername === name)
    const latestIncomingId = incoming.length > 0 ? Math.max(...incoming.map((m) => m.id)) : 0
    const readUpTo = readUpToIncomingIdMap.value[name] || 0

    if (deps.currentView.value === 'chat' && chatTarget.value.trim() === name) {
      unreadMap.value[name] = 0
      readUpToIncomingIdMap.value[name] = latestIncomingId
      persistReadUpToMapForUser(deps.myName.value)
      markIncomingSynced(name, latestIncomingId)
      if (name === chatTarget.value.trim()) {
        const box = chatMessagesRef.value
        const nearBottom = box ? box.scrollHeight - box.scrollTop - box.clientHeight < 80 : true
        chatMessages.value = history
        if (nearBottom) {
          nextTick(() => {
            requestAnimationFrame(() => {
              scrollChatToBottom(true)
              requestAnimationFrame(() => scrollChatToBottom(true))
            })
          })
        }
      }
      return
    }

    const nextUnread = incoming.filter((m) => m.id > readUpTo).length
    unreadMap.value[name] = nextUnread

    if (notify && chatUnreadInitialized && latestIncomingId > 0) {
      const hasNewSinceSync = incoming.some((m) => shouldNotifyChatMessage(name, m.id))
      if (hasNewSinceSync) {
        deps.showRealtimeToast({
          title: '新消息提醒',
          message: `${name} 给你发来了一条消息`,
          kind: 'chat',
          chatUser: name
        })
      }
    }

    markIncomingSynced(name, latestIncomingId)
  }

  async function initializeChatUnreadState() {
    chatUnreadInitialized = false
    if (!deps.isLoggedIn.value) {
      chatUnreadInitialized = true
      return
    }
    try {
      await fetchChatContacts()
      pruneGhostChatUnread()
      for (const c of chatContacts.value) {
        const name = String(c.username || '').trim()
        if (!name) continue
        try {
          await syncChatUnreadForContact(name, false)
        } catch {
          // ignore single-contact sync error
        }
      }
    } finally {
      chatUnreadInitialized = true
    }
  }

  function startChatPolling() {
    if (chatPollingTimer) {
      window.clearInterval(chatPollingTimer)
    }
    chatPollingTimer = window.setInterval(async () => {
      if (!deps.isLoggedIn.value) return
      if (typeof document !== 'undefined' && document.hidden) return
      if (deps.realtimeConnected.value) return
      if (deps.currentView.value !== 'chat') return

      await fetchChatContacts()

      const active = String(chatTarget.value || '').trim()
      if (active && !isSystemNoticeContact(active)) {
        try {
          await syncChatUnreadForContact(active, true)
        } catch {
          // ignore
        }
        return
      }

      const hot = chatContacts.value
        .filter((c) => {
          const name = String(c.username || '').trim()
          if (!name || isSystemNoticeContact(name)) return false
          return Number(unreadMap.value[name] || 0) > 0
        })
        .slice(0, 3)
      for (const c of hot) {
        try {
          await syncChatUnreadForContact(c.username, true)
        } catch {
          // ignore single-contact polling error
        }
      }
    }, 10000)
  }

  function stopChatPolling() {
    if (chatPollingTimer) {
      window.clearInterval(chatPollingTimer)
      chatPollingTimer = null
    }
  }

  function resetChatLocalState() {
    stopChatPolling()
    unreadMap.value = {}
    readUpToIncomingIdMap.value = {}
    lastSyncedIncomingIdMap.value = {}
    chatPinnedUsernames.value = []
    chatHiddenUsernames.value = []
    chatUnreadInitialized = false
  }

  function handleOutgoingEcho(to: string) {
    void fetchChatContacts().finally(() => {
      if (deps.currentView.value === 'chat' && chatTarget.value.trim() === to) {
        void loadChatHistory(true, 'bottom')
      }
    })
  }

  function noteSupportIncoming(other: string, msgId: number) {
    unreadMap.value[other] = 0
    if (msgId > 0) {
      readUpToIncomingIdMap.value[other] = Math.max(readUpToIncomingIdMap.value[other] || 0, msgId)
      markIncomingSynced(other, msgId)
    }
  }

  function handleIncomingChatMessage(other: string, msgId: number) {
    if (isCustomerServiceUsername(other) || isCustomerServiceChatTarget(other)) {
      unreadMap.value[other] = 0
      if (msgId > 0) markIncomingSynced(other, msgId)
      return
    }

    if (isChatContactHidden(other)) {
      unhideChatContact(other)
    }
    void fetchChatContacts()
      .catch(() => {
        // ignore
      })
      .finally(() => {
        if (deps.currentView.value === 'chat' && chatTarget.value.trim() === other) {
          void loadChatHistory(true, 'bottom')
          return
        }

        const readUpTo = readUpToIncomingIdMap.value[other] || 0
        if (msgId > readUpTo) {
          unreadMap.value[other] = (unreadMap.value[other] || 0) + 1
          if (shouldNotifyChatMessage(other, msgId)) {
            deps.showRealtimeToast({
              title: '新消息提醒',
              message: `${other} 给你发来了一条消息`,
              kind: 'chat',
              chatUser: other
            })
          }
        }
        if (msgId > 0) {
          markIncomingSynced(other, msgId)
        }
      })
  }

  function onViewingSystemNotice() {
    if (deps.currentView.value === 'chat' && isSystemNoticeContact(chatTarget.value)) {
      void markSystemNoticeRead()
    }
  }

  async function goChat() {
    if (!deps.isLoggedIn.value) {
      return
    }
    deps.showUserDropdown.value = false
    deps.setView('chat')
    await fetchChatContacts()
    if (chatTarget.value && !isSystemNoticeContact(chatTarget.value) && !chatContacts.value.some((c) => c.username === chatTarget.value)) {
      chatContacts.value.unshift(normalizeChatContact({ username: chatTarget.value, avatarUrl: '' }))
    }
    if (chatContacts.value.length > 0 && !chatTarget.value) {
      chatTarget.value = chatContacts.value[0].username
    }
    if (chatTarget.value && isSystemNoticeContact(chatTarget.value)) {
      await openChatWith(chatTarget.value)
    } else if (chatTarget.value) {
      await nextTick()
      await loadChatHistory(true, 'bottom')
    }
    startChatPolling()
  }

  async function askPublisherFirst(task: TaskItem) {
    if (!deps.isLoggedIn.value) {
      return
    }
    if (!task.publisherName || task.publisherName === deps.myName.value) {
      return
    }

    chatTarget.value = task.publisherName
    deps.closeTaskDetail()
    await goChat()
  }

  async function fetchChatContacts() {
    if (!deps.isLoggedIn.value) return
    try {
      const [contacts, tempContacts] = await Promise.all([
        deps.request<ChatContactItem[]>(`${apiOrigin}/api/chat/contacts`),
        deps.request<string[]>(`${apiOrigin}/api/chat/temporary-contacts`)
      ])

      temporaryChatContacts.value = tempContacts

      const map = new Map<string, ChatContactItem>()
      for (const c of contacts) {
        map.set(c.username, normalizeChatContact(c))
      }
      for (const name of tempContacts) {
        if (!map.has(name)) map.set(name, normalizeChatContact({ username: name, avatarUrl: '' }))
      }

      const current = chatTarget.value.trim()
      if (current && !map.has(current)) {
        map.set(current, normalizeChatContact({ username: current, avatarUrl: '' }))
      }

      chatContacts.value = filterVisibleChatContacts(Array.from(map.values()))

      for (const c of chatContacts.value) {
        if (unreadMap.value[c.username] == null) unreadMap.value[c.username] = 0
      }
      pruneGhostChatUnread()
      if (unreadMap.value[SYSTEM_NOTICE_USERNAME] == null) {
        syncSystemNoticeUnread()
      }

      if (current) {
        const idx = chatContacts.value.findIndex((c) => c.username === current)
        const needProfile =
          idx >= 0 &&
          (!chatContacts.value[idx].avatarUrl ||
            String(chatContacts.value[idx].avatarUrl).trim() === '' ||
            chatContacts.value[idx].riderApproved == null ||
            chatContacts.value[idx].customerServiceStaff == null)
        if (needProfile) {
          try {
            const profile = await deps.request<UserPublicProfile>(
              `${authBase}/users/${encodeURIComponent(current)}/public-profile`
            )
            if (idx >= 0 && profile) {
              chatContacts.value[idx] = applyContactProfileFields(chatContacts.value[idx], profile)
            }
          } catch {
            // ignore: 仅用于补全展示字段
          }
        }
      }
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    }
  }

  async function openChatWith(target: string) {
    const username = target.trim()
    if (isSystemNoticeContact(username)) {
      chatTarget.value = SYSTEM_NOTICE_USERNAME
      chatMessages.value = []
      unreadMap.value[SYSTEM_NOTICE_USERNAME] = 0
      await Promise.all([deps.fetchPublicNotices(), deps.fetchUserSystemMessages()])
      markSystemNoticeRead()
      return
    }
    unhideChatContact(username)
    chatTarget.value = username
    unreadMap.value[username] = 0
    if (isCustomerServiceChatTarget(username)) {
      void deps.fetchTasks()
    }

    const idx = chatContacts.value.findIndex((c) => c.username === username)
    if (idx >= 0) {
      const needProfile =
        !chatContacts.value[idx].avatarUrl ||
        String(chatContacts.value[idx].avatarUrl).trim() === '' ||
        chatContacts.value[idx].riderApproved == null ||
        chatContacts.value[idx].customerServiceStaff == null
      if (needProfile) {
        try {
          const profile = await deps.request<UserPublicProfile>(
            `${authBase}/users/${encodeURIComponent(username)}/public-profile`
          )
          if (profile) {
            chatContacts.value[idx] = applyContactProfileFields(chatContacts.value[idx], profile)
          }
        } catch {
          // ignore
        }
      }
    } else {
      chatContacts.value.unshift(normalizeChatContact({ username, avatarUrl: '' }))
      chatContacts.value = sortChatContactsList(chatContacts.value)
    }
    await nextTick()
    await loadChatHistory(true, 'bottom')
  }

  async function openContactProfile(username: string) {
    if (!username) return

    activeContactCard.value = username
    showContactProfileModal.value = true
    contactProfileLoading.value = true
    selectedContactProfile.value = null
    try {
      const profile = await deps.request<UserPublicProfile>(
        `${authBase}/users/${encodeURIComponent(username)}/public-profile`
      )
      if (profile) {
        selectedContactProfile.value = {
          ...profile,
          avatarUrl: resolveCustomerServiceAvatar(
            profile.username,
            profile.avatarUrl,
            profile.customerServiceStaff
          )
        }
      }
    } catch {
      selectedContactProfile.value = null
    } finally {
      contactProfileLoading.value = false
    }
  }

  function closeContactProfileModal() {
    showContactProfileModal.value = false
    activeContactCard.value = ''
  }

  async function loadChatHistory(markRead = true, scrollPolicy: 'auto' | 'bottom' = 'auto') {
    if (!deps.isLoggedIn.value || !chatTarget.value.trim()) return
    const target = chatTarget.value.trim()
    if (isSystemNoticeContact(target)) {
      await Promise.all([deps.fetchPublicNotices(), deps.fetchUserSystemMessages()])
      if (markRead) markSystemNoticeRead()
      return
    }
    try {
      await nextTick()
      const el = chatMessagesRef.value
      const stickToBottom =
        scrollPolicy === 'bottom' ||
        (el ? el.scrollHeight - el.scrollTop - el.clientHeight < 80 : true)

      chatMessages.value = await deps.request<ChatMessageItem[]>(
        `${apiOrigin}/api/chat/history?targetUser=${encodeURIComponent(target)}`
      )

      const incoming = chatMessages.value.filter((m) => m.fromUsername === target)
      const latestIncomingId = incoming.length > 0 ? Math.max(...incoming.map((m) => m.id)) : 0

      if (markRead) {
        unreadMap.value[target] = 0
        readUpToIncomingIdMap.value[target] = latestIncomingId
        markIncomingSynced(target, latestIncomingId)
        persistReadUpToMapForUser(deps.myName.value)
      }

      if (stickToBottom) {
        await nextTick()
        requestAnimationFrame(() => {
          scrollChatToBottom(true)
          requestAnimationFrame(() => scrollChatToBottom(true))
        })
      }
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    }
  }

  async function sendChatMessage() {
    if (!deps.isLoggedIn.value) return
    if (isSystemNoticeContact(chatTarget.value)) return
    if (!chatTarget.value.trim()) {
      deps.errorMsg.value = '请先输入聊天对象'
      return
    }
    const text = chatInput.value.trim()
    const image = chatImageDraft.value.trim()
    if (!text && !image) {
      deps.errorMsg.value = '消息不能为空'
      return
    }

    try {
      let content = text
      if (image) {
        content = `${CHAT_IMAGE_PREFIX}${JSON.stringify({ image, text })}`
      }
      await deps.request<ChatMessageItem>(`${apiOrigin}/api/chat/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ toUsername: chatTarget.value.trim(), content })
      })
      chatInput.value = ''
      chatImageDraft.value = ''
      await fetchChatContacts()
      await loadChatHistory(true, 'bottom')
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    }
  }

  function isCustomerServiceChatTarget(username?: string) {
    const name = String(username || chatTarget.value || '').trim()
    if (!name) return false
    if (isCustomerServiceUsername(name)) return true
    const contact = chatContacts.value.find((c) => c.username === name)
    if (contact?.customerServiceStaff) return true
    const profile =
      selectedContactProfile.value?.username === name ? selectedContactProfile.value : null
    return !!profile?.customerServiceStaff
  }

  function buildTaskInquiryMessage(task: TaskItem) {
    const role = usernameMatches(task.publisherName, deps.myName.value)
      ? '发布者'
      : usernameMatches(task.claimerName, deps.myName.value)
        ? '接单员'
        : '相关用户'
    const reward =
      task.reward == null || !Number.isFinite(Number(task.reward))
        ? '-'
        : `¥${Number(task.reward).toFixed(2)}`
    return serializeTaskInquiryMessage({
      taskId: String(task.id),
      title: task.title || '任务订单',
      status: statusLabelMap[task.status as TaskStatus] || String(task.status || '-'),
      role,
      pickup: task.pickupLocation || '-',
      dropoff: task.dropoffLocation || '-',
      reward,
      publisher: task.publisherName || '-',
      claimer: task.claimerName || '待接单'
    })
  }

  async function sendTaskToCustomerService(task: TaskItem) {
    if (!deps.isLoggedIn.value) return
    const supportAgent = deps.supportHumanMode.value ? deps.supportAssignedAgent.value.trim() : ''
    const target = supportAgent || chatTarget.value.trim()
    if (!target) {
      deps.errorMsg.value = '请先选择人工客服'
      return
    }
    if (!supportAgent && !isCustomerServiceChatTarget(target)) {
      deps.errorMsg.value = '仅可向人工客服发送订单'
      return
    }
    if (sendingTaskToCsId.value != null) return

    sendingTaskToCsId.value = task.id
    try {
      const content = buildTaskInquiryMessage(task)
      const sent = await deps.request<ChatMessageItem>(`${apiOrigin}/api/chat/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ toUsername: target, content })
      })
      const sentId = Number(sent?.id || 0)
      if (sentId > 0) {
        deps.supportIngestedChatIds.value.add(sentId)
        if (sentId > deps.supportHumanHistoryAfterId.value) {
          deps.supportHumanHistoryAfterId.value = sentId
        }
      }
      if (supportAgent) {
        deps.supportChatMessages.value.push({
          role: 'user',
          content: `已发送订单给客服：${task.title || `#${task.id}`}`
        })
        unreadMap.value[target] = 0
      } else {
        await fetchChatContacts()
        await loadChatHistory(true, 'bottom')
      }
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    } finally {
      sendingTaskToCsId.value = null
    }
  }

  function onChatImageSelected(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return
    if (!file.type.startsWith('image/')) {
      deps.errorMsg.value = '仅支持图片文件'
      input.value = ''
      return
    }
    if (file.size > 8 * 1024 * 1024) {
      deps.errorMsg.value = '图片不能超过 8MB'
      input.value = ''
      return
    }
    void (async () => {
      try {
        const url = await compressImageForChat(file)
        chatImageDraft.value = url
        deps.errorMsg.value = ''
      } catch {
        deps.errorMsg.value = '图片处理失败，请换一张重试'
      }
    })()
    input.value = ''
  }

  function clearChatImageDraft() {
    chatImageDraft.value = ''
  }

  function setChatImageDraft(value: string) {
    chatImageDraft.value = String(value || '').trim()
  }

  function dataUrlByteSize(dataUrl: string) {
    const i = dataUrl.indexOf(',')
    if (i < 0) return 0
    const b64 = dataUrl.slice(i + 1)
    const pad = b64.endsWith('==') ? 2 : b64.endsWith('=') ? 1 : 0
    return Math.floor((b64.length * 3) / 4) - pad
  }

  async function readFileAsDataUrl(file: File) {
    return await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result || ''))
      reader.onerror = () => reject(new Error('read-failed'))
      reader.readAsDataURL(file)
    })
  }

  async function loadImageElement(src: string) {
    return await new Promise<HTMLImageElement>((resolve, reject) => {
      const img = new Image()
      img.onload = () => resolve(img)
      img.onerror = () => reject(new Error('image-load-failed'))
      img.src = src
    })
  }

  async function compressImageForChat(file: File) {
    const rawDataUrl = await readFileAsDataUrl(file)
    const img = await loadImageElement(rawDataUrl)
    const maxSide = 1280
    const scale = Math.min(1, maxSide / Math.max(img.width, img.height))
    const w = Math.max(1, Math.round(img.width * scale))
    const h = Math.max(1, Math.round(img.height * scale))
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    if (!ctx) return rawDataUrl
    ctx.drawImage(img, 0, 0, w, h)
    let quality = 0.86
    let out = canvas.toDataURL('image/jpeg', quality)
    while (dataUrlByteSize(out) > 900 * 1024 && quality > 0.45) {
      quality -= 0.08
      out = canvas.toDataURL('image/jpeg', quality)
    }
    if (dataUrlByteSize(out) > 900 * 1024) {
      throw new Error('image-too-large-after-compress')
    }
    return out
  }

  return {
    chatTarget,
    chatInput,
    chatImageDraft,
    chatMessages,
    chatContacts,
    temporaryChatContacts,
    chatPinnedUsernames,
    chatHiddenUsernames,
    unreadMap,
    readUpToIncomingIdMap,
    lastSyncedIncomingIdMap,
    contactProfileLoading,
    selectedContactProfile,
    activeContactCard,
    showContactProfileModal,
    sendingTaskToCsId,
    totalUnread,
    publishedNoticesForChat,
    systemNoticeFeed,
    loadChatLocalPrefsForUser,
    isChatContactPinned,
    pinChatContact,
    deleteChatContact,
    clearAllChatUnread,
    loadReadUpToMapForUser,
    persistReadUpToMapForUser,
    loadLatestSeenMapForUser,
    syncSystemNoticeUnread,
    markSystemNoticeRead,
    scrollChatToBottom,
    bindChatMessagesEl,
    shouldShowChatDivider,
    chatAvatarUrl,
    initializeChatUnreadState,
    startChatPolling,
    stopChatPolling,
    resetChatLocalState,
    handleOutgoingEcho,
    noteSupportIncoming,
    handleIncomingChatMessage,
    onViewingSystemNotice,
    goChat,
    askPublisherFirst,
    fetchChatContacts,
    openChatWith,
    openContactProfile,
    closeContactProfileModal,
    loadChatHistory,
    sendChatMessage,
    isCustomerServiceChatTarget,
    sendTaskToCustomerService,
    onChatImageSelected,
    clearChatImageDraft,
    setChatImageDraft,
    compressImageForChat
  }
}
