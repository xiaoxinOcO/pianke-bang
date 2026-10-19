import fs from 'node:fs'

const appPath = 'src/app/campusAppState.ts'
const src = fs.readFileSync(appPath, 'utf8')
const nl = src.includes('\r\n') ? '\r\n' : '\n'
const lines = src.split(/\r?\n/)

function slice(a, b) {
  const text = lines.slice(a - 1, b).join('\n')
  const start = lines[a - 1]
  const end = lines[b - 1]
  if (!start || !end) throw new Error(`bad range ${a}-${b}`)
  return text
}

function assertIncludes(rangeText, needle, label) {
  if (!rangeText.includes(needle)) {
    throw new Error(`${label} missing ${needle}`)
  }
}

const blocks = [
  ['totalUnread', 959, 1206, 'const totalUnread'],
  ['readMaps', 1281, 1323, 'function loadReadUpToMapForUser'],
  ['avatar', 3312, 3360, 'function shouldShowChatDivider'],
  ['scroll', 3790, 3821, 'function scrollChatToBottom'],
  ['poll', 4105, 4244, 'function markIncomingSynced'],
  ['goChat', 4286, 4319, 'async function goChat'],
  ['contacts', 5036, 5174, 'async function fetchChatContacts'],
  ['send', 5527, 5766, 'async function loadChatHistory']
]

let body = ''
for (const [name, a, b, needle] of blocks) {
  const text = slice(a, b)
  assertIncludes(text, needle, name)
  body += `\n${text}\n`
}

const replacers = [
  'fetchUserSystemMessages',
  'userSystemMessagesUnread',
  'userSystemMessages',
  'fetchPublicNotices',
  'supportHumanHistoryAfterId',
  'supportIngestedChatIds',
  'supportAssignedAgent',
  'supportChatMessages',
  'supportHumanMode',
  'showUserDropdown',
  'showRealtimeToast',
  'showProfileNotice',
  'realtimeConnected',
  'normalizeImageUrl',
  'closeTaskDetail',
  'publicNotices',
  'currentView',
  'isLoggedIn',
  'myAvatarUrl',
  'errorMsg',
  'fetchTasks',
  'unreadMap',
  'myName',
  'setView',
  'request'
]

for (const name of replacers) {
  const re = new RegExp(`(?<![\\w.])${name}\\b`, 'g')
  body = body.replace(re, `deps.${name}`)
}

if (body.includes('deps.deps.')) throw new Error('double deps')
if (body.includes('deps.requestAnimationFrame')) throw new Error('requestAnimationFrame rewritten')

const header = `import { computed, nextTick, ref, type Ref } from 'vue'
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
  showRealtimeToast: (payload: {
    title: string
    message: string
    kind: 'chat' | 'task'
    chatUser?: string
  }) => void
  showProfileNotice: (text: string) => void
  showUserDropdown: Ref<boolean>
  closeTaskDetail: () => void
  normalizeImageUrl: (url?: string) => string
  fetchTasks: () => void | Promise<void>
  fetchPublicNotices: () => void | Promise<void>
  fetchUserSystemMessages: () => void | Promise<void>
  publicNotices: Ref<NoticeItem[]>
  userSystemMessages: Ref<UserSystemMessageItem[]>
  userSystemMessagesUnread: Ref<number>
  realtimeConnected: Ref<boolean>
  /** Shared with support domain so agent replies can clear the same badge map. */
  unreadMap: Ref<Record<string, number>>
  supportHumanMode: Ref<boolean>
  supportAssignedAgent: Ref<string>
  supportChatMessages: Ref<SupportChatMessage[]>
  supportIngestedChatIds: Ref<Set<number>>
  supportHumanHistoryAfterId: Ref<number>
}

/**
 * 通讯录 / 私聊 / 未读：联系人、会话、轮询与系统通知在聊天页的展示。
 * 实时连接与登出仍留在 campusAppState，通过 side effects 调进来。
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
  const unreadMap = deps.unreadMap
  const readUpToIncomingIdMap = ref<Record<string, number>>({})
  /** 各联系人已同步到的最新 incoming 消息 id，用于判断「真正新消息」再弹 toast */
  const lastSyncedIncomingIdMap = ref<Record<string, number>>({})
  let chatPollingTimer: number | null = null
  /** 登录后首轮未读同步完成前，不弹「新消息」toast */
  let chatUnreadInitialized = false
  const CHAT_IMAGE_PREFIX = '[CHAT_IMAGE]'
  const chatMessagesRef = ref<HTMLDivElement | null>(null)
  const contactProfileLoading = ref(false)
  const selectedContactProfile = ref<UserPublicProfile | null>(null)
  const activeContactCard = ref('')
  const showContactProfileModal = ref(false)
  const sendingTaskToCsId = ref<number | null>(null)
`

const footer = `
  function handleOutgoingEcho(to: string) {
    void fetchChatContacts().finally(() => {
      if (deps.currentView.value === 'chat' && chatTarget.value.trim() === to) {
        void loadChatHistory(true, 'bottom')
      }
    })
  }

  /** 人工客服窗吃掉的消息：清未读并抬高已读水位，不进通讯录。 */
  function acknowledgeSupportChat(other: string, msgId: number) {
    unreadMap.value[other] = 0
    if (msgId > 0) {
      readUpToIncomingIdMap.value[other] = Math.max(readUpToIncomingIdMap.value[other] || 0, msgId)
      markIncomingSynced(other, msgId)
    }
  }

  function handleIncomingChatMessage(from: string, _to: string, msgId: number, _content: string) {
    if (!from || from === deps.myName.value) return
    const other = from
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
              message: \`\${other} 给你发来了一条消息\`,
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

  function onSystemRealtimeMessage() {
    void deps.fetchUserSystemMessages()
    if (deps.currentView.value === 'chat' && isSystemNoticeContact(chatTarget.value)) {
      void markSystemNoticeRead()
    }
  }

  function resetChatLocalState() {
    unreadMap.value = {}
    readUpToIncomingIdMap.value = {}
    lastSyncedIncomingIdMap.value = {}
    chatPinnedUsernames.value = []
    chatHiddenUsernames.value = []
    chatUnreadInitialized = false
    stopChatPolling()
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
    shouldTrackChatUnread,
    pruneGhostChatUnread,
    loadChatLocalPrefsForUser,
    persistChatPinnedForUser,
    persistChatHiddenForUser,
    isChatContactHidden,
    unhideChatContact,
    isChatContactPinned,
    sortChatContactsList,
    filterVisibleChatContacts,
    pinChatContact,
    deleteChatContact,
    clearAllChatUnread,
    loadReadUpToMapForUser,
    persistReadUpToMapForUser,
    loadLatestSeenMapForUser,
    persistLatestSeenMapForUser,
    getSystemNoticeLastReadId,
    syncSystemNoticeUnread,
    markUserSystemMessagesRead,
    markSystemNoticeRead,
    markIncomingSynced,
    shouldNotifyChatMessage,
    syncChatUnreadForContact,
    initializeChatUnreadState,
    startChatPolling,
    stopChatPolling,
    scrollChatToBottom,
    bindChatMessagesEl,
    shouldShowChatDivider,
    chatAvatarUrl,
    applyContactProfileFields,
    normalizeChatContact,
    fetchChatContacts,
    openChatWith,
    openContactProfile,
    closeContactProfileModal,
    loadChatHistory,
    sendChatMessage,
    isCustomerServiceChatTarget,
    buildTaskInquiryMessage,
    sendTaskToCustomerService,
    onChatImageSelected,
    clearChatImageDraft,
    setChatImageDraft,
    dataUrlByteSize,
    readFileAsDataUrl,
    loadImageElement,
    compressImageForChat,
    goChat,
    askPublisherFirst,
    handleOutgoingEcho,
    acknowledgeSupportChat,
    handleIncomingChatMessage,
    onSystemRealtimeMessage,
    resetChatLocalState
  }
}
`

// The extracted body still assigns unreadMap via deps.unreadMap because of rewrite,
// but we also aliased const unreadMap = deps.unreadMap. Undo unreadMap rewrite
// so the local alias is used (same ref).
body = body.replace(/deps\.unreadMap/g, 'unreadMap')

const out = header + body + footer
fs.writeFileSync('src/app/domains/createChatDomain.ts', out.replace(/\n/g, nl))
console.log('wrote domain', out.split('\n').length, 'lines')

// --- patch campusAppState ---
function must(lineNo, includes) {
  const line = lines[lineNo - 1]
  if (!line || !line.includes(includes)) {
    throw new Error(`line ${lineNo} expected to include ${includes} but was: ${line}`)
  }
}

must(412, 'chatMessagesRef')
must(833, 'contactProfileLoading')
must(836, 'showContactProfileModal')
must(881, 'const chatTarget')
must(896, 'historyTickTimer')
must(898, 'CHAT_IMAGE_PREFIX')
must(959, 'const totalUnread')
must(1205, 'showProfileNotice')
must(1206, '}')
must(1281, 'function loadReadUpToMapForUser')
must(1317, 'function persistLatestSeenMapForUser')
must(1323, '}')
must(3312, 'function shouldShowChatDivider')
must(3360, 'function normalizeChatContact')
must(3790, 'function scrollChatToBottom')
must(3821, 'function bindChatMessagesEl')
must(4105, 'function markIncomingSynced')
must(4244, '}')
must(4246, 'function startAuthHeartbeat')
must(4286, 'async function goChat')
must(4319, 'await goChat()')
must(5036, 'async function fetchChatContacts')
must(5174, 'function closeContactProfileModal')
must(5527, 'async function loadChatHistory')
must(5765, 'return out')
must(5766, '}')

const ranges = [
  [5527, 5766],
  [5036, 5174],
  [4286, 4319],
  [4105, 4244],
  [3790, 3821],
  [3312, 3360],
  [1281, 1323],
  [959, 1206]
]
// delete bottom-up, plus small replacements
let next = lines.slice()
for (const [a, b] of ranges) {
  next.splice(a - 1, b - a + 1)
}

// After large deletes, do content replacements on joined text.
let text = next.join('\n')

const chatRefBlock = `const chatTarget = ref('')
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
let chatPollingTimer: number | null = null
/** 登录后首轮未读同步完成前，不弹「新消息」toast */
let chatUnreadInitialized = false
let historyTickTimer: number | null = null
let authHeartbeatTimer: number | null = null
const CHAT_IMAGE_PREFIX = '[CHAT_IMAGE]'`

if (!text.includes(chatRefBlock)) throw new Error('chat ref block missing')
text = text.replace(
  chatRefBlock,
  `let historyTickTimer: number | null = null
let authHeartbeatTimer: number | null = null`
)

const profileBlock = `const contactProfileLoading = ref(false)
const selectedContactProfile = ref<UserPublicProfile | null>(null)
const activeContactCard = ref('')
const showContactProfileModal = ref(false)
`
if (!text.includes(profileBlock)) throw new Error('profile block missing')
text = text.replace(profileBlock, '')

text = text.replace(`const chatMessagesRef = ref<HTMLDivElement | null>(null)\n`, '')

const sendingBlock = `const sendingTaskToCsId = ref<number | null>(null)\n`
if (!text.includes(sendingBlock)) throw new Error('sendingTaskToCsId missing')
text = text.replace(sendingBlock, '')

if (!text.includes("import { createSupportDomain } from './domains/createSupportDomain'")) {
  throw new Error('support import missing')
}
text = text.replace(
  "import { createSupportDomain } from './domains/createSupportDomain'",
  "import { createSupportDomain } from './domains/createSupportDomain'\nimport { createChatDomain } from './domains/createChatDomain'"
)

text = text.replace(
  `  chatHiddenStoreKey,
  chatLatestSeenStoreKey,
  chatPinnedStoreKey,
  chatReadStoreKey,
  systemNoticeReadStoreKey
`,
  `  chatReadStoreKey
`
)

// SystemNoticeFeedItem may become unused
text = text.replace('  SystemNoticeFeedItem,\n', '')
text = text.replace('  ChatContactItem,\n', '')

const realtimeOld = `  if (type === 'CHAT_MESSAGE') {
    const from = String(data?.fromUsername || '').trim()
    const to = String(data?.toUsername || '').trim()
    if (!from || !to) return
    const msgId = Number(data?.id || 0)
    const content = String(data?.content || '')

    if (from === myName.value) {
      void fetchChatContacts().finally(() => {
        if (currentView.value === 'chat' && chatTarget.value.trim() === to) {
          void loadChatHistory(true, 'bottom')
        }
      })
      return
    }

    const other = from

    // 客服消息：接入智能客服窗，不进通讯录未读
    if (supportSideEffects.handleHumanAgentChat(other, content, msgId)) {
      unreadMap.value[other] = 0
      if (msgId > 0) {
        readUpToIncomingIdMap.value[other] = Math.max(readUpToIncomingIdMap.value[other] || 0, msgId)
        markIncomingSynced(other, msgId)
      }
      return
    }

    if (isCustomerServiceUsername(other) || isCustomerServiceChatTarget(other)) {
      // 未处于人工模式时，客服私信也不推通讯录；引导回智能客服
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
        if (currentView.value === 'chat' && chatTarget.value.trim() === other) {
          void loadChatHistory(true, 'bottom')
          return
        }

        const readUpTo = readUpToIncomingIdMap.value[other] || 0
        if (msgId > readUpTo) {
          unreadMap.value[other] = (unreadMap.value[other] || 0) + 1
          if (shouldNotifyChatMessage(other, msgId)) {
            showRealtimeToast({
              title: '新消息提醒',
              message: \`\${other} 给你发来了一条消息\`,
              kind: 'chat',
              chatUser: other
            })
          }
        }
        if (msgId > 0) {
          markIncomingSynced(other, msgId)
        }
      })
    return
  }

  if (type === 'SYSTEM_MESSAGE') {
    void fetchUserSystemMessages()
    if (currentView.value === 'chat' && isSystemNoticeContact(chatTarget.value)) {
      void markSystemNoticeRead()
    }
    return
  }`

const realtimeNew = `  if (type === 'CHAT_MESSAGE') {
    const from = String(data?.fromUsername || '').trim()
    const to = String(data?.toUsername || '').trim()
    if (!from || !to) return
    const msgId = Number(data?.id || 0)
    const content = String(data?.content || '')

    if (from === myName.value) {
      chatSideEffects.handleOutgoingEcho(to)
      return
    }

    // 客服消息：接入智能客服窗，不进通讯录未读
    if (supportSideEffects.handleHumanAgentChat(from, content, msgId)) {
      chatSideEffects.acknowledgeSupportChat(from, msgId)
      return
    }

    chatSideEffects.handleIncomingChatMessage(from, to, msgId, content)
    return
  }

  if (type === 'SYSTEM_MESSAGE') {
    chatSideEffects.onSystemRealtimeMessage()
    return
  }`

if (!text.includes(realtimeOld)) throw new Error('realtime block missing')
text = text.replace(realtimeOld, realtimeNew)

const stub = `const realtimeConnected = ref(false)
let realtimeClient: { deactivate: () => void | Promise<void>; activate: () => void } | null = null
let realtimeSubscribed = false`

const stubNew = `const realtimeConnected = ref(false)
let realtimeClient: { deactivate: () => void | Promise<void>; activate: () => void } | null = null
let realtimeSubscribed = false

const chatSideEffects = {
  handleIncomingChatMessage: ((_from: string, _to: string, _msgId: number, _content: string) => {}) as (
    from: string,
    to: string,
    msgId: number,
    content: string
  ) => void,
  handleOutgoingEcho: ((_to: string) => {}) as (to: string) => void,
  acknowledgeSupportChat: ((_other: string, _msgId: number) => {}) as (other: string, msgId: number) => void,
  onSystemRealtimeMessage: (() => {}) as () => void,
  stopChatPolling: (() => {}) as () => void,
  startChatPolling: (() => {}) as () => void,
  resetChatLocalState: (() => {}) as () => void
}`

if (!text.includes(stub)) throw new Error('realtime stub anchor missing')
text = text.replace(stub, stubNew)

// startRealtime polling calls go through side effects so they exist before createChatDomain
text = text.replaceAll('stopChatPolling()', 'chatSideEffects.stopChatPolling()')
text = text.replaceAll('startChatPolling()', 'chatSideEffects.startChatPolling()')

const supportAssignEnd = `supportSideEffects.handleHumanAgentChat = (other, content, msgId) => {
  if (!supportHumanMode.value || !supportAssignedAgent.value || !usernameMatches(other, supportAssignedAgent.value)) return false
  ingestSupportHumanIncoming(content, msgId)
  if (!showSupportChat.value) {
    showRealtimeToast({ title: '人工客服回复', message: content.slice(0, 40) || '客服发来一条消息', kind: 'chat' })
  }
  return true
}`

const unreadDeclAndChat = `/** 与客服域、聊天域共用的未读表（客服回复会把对应联系人未读清零）。 */
const unreadMap = ref<Record<string, number>>({})

${supportAssignEnd}

const {
  chatTarget,
  chatInput,
  chatImageDraft,
  chatMessages,
  chatContacts,
  temporaryChatContacts,
  chatPinnedUsernames,
  chatHiddenUsernames,
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
  loadLatestSeenMapForUser,
  syncSystemNoticeUnread,
  markSystemNoticeRead,
  initializeChatUnreadState,
  startChatPolling,
  stopChatPolling,
  bindChatMessagesEl,
  shouldShowChatDivider,
  chatAvatarUrl,
  fetchChatContacts,
  openChatWith,
  openContactProfile,
  closeContactProfileModal,
  loadChatHistory,
  sendChatMessage,
  sendTaskToCustomerService,
  onChatImageSelected,
  clearChatImageDraft,
  setChatImageDraft,
  compressImageForChat,
  goChat,
  askPublisherFirst,
  handleOutgoingEcho,
  acknowledgeSupportChat,
  handleIncomingChatMessage,
  onSystemRealtimeMessage,
  resetChatLocalState
} = createChatDomain({
  isLoggedIn,
  myName,
  myAvatarUrl,
  request,
  errorMsg,
  currentView,
  setView,
  showRealtimeToast,
  showProfileNotice,
  showUserDropdown,
  closeTaskDetail,
  normalizeImageUrl,
  fetchTasks,
  fetchPublicNotices,
  fetchUserSystemMessages,
  publicNotices,
  userSystemMessages,
  userSystemMessagesUnread,
  realtimeConnected,
  unreadMap,
  supportHumanMode,
  supportAssignedAgent,
  supportChatMessages,
  supportIngestedChatIds,
  supportHumanHistoryAfterId
})

chatSideEffects.handleIncomingChatMessage = handleIncomingChatMessage
chatSideEffects.handleOutgoingEcho = handleOutgoingEcho
chatSideEffects.acknowledgeSupportChat = acknowledgeSupportChat
chatSideEffects.onSystemRealtimeMessage = onSystemRealtimeMessage
chatSideEffects.stopChatPolling = stopChatPolling
chatSideEffects.startChatPolling = startChatPolling
chatSideEffects.resetChatLocalState = resetChatLocalState`

if (!text.includes(supportAssignEnd)) throw new Error('support assign missing')
text = text.replace(supportAssignEnd, unreadDeclAndChat)

// createSupportDomain is BEFORE this insertion, and it references unreadMap.
// unreadMap must be declared BEFORE createSupportDomain call.
// We inserted unreadMap after support assign, which is AFTER createSupportDomain. Move it.

const unreadDecl = `/** 与客服域、聊天域共用的未读表（客服回复会把对应联系人未读清零）。 */
const unreadMap = ref<Record<string, number>>({})

`
if (!text.includes(unreadDecl)) throw new Error('unread decl missing after splice')
text = text.replace(unreadDecl, '')

const supportCallAnchor = `} = createSupportDomain({
  isLoggedIn,
  myName,
  request,`
if (!text.includes(supportCallAnchor)) throw new Error('support call anchor missing')
text = text.replace(
  supportCallAnchor,
  `} = createSupportDomain({
  isLoggedIn,
  myName,
  request,`
)

const beforeSupport = `const {
  showSupportChat,`
if (!text.includes(beforeSupport)) throw new Error('before support missing')
text = text.replace(
  beforeSupport,
  `${unreadDecl}const {
  showSupportChat,`
)

// logout map reset
const logoutReset = `  unreadMap.value = {}
  readUpToIncomingIdMap.value = {}
  lastSyncedIncomingIdMap.value = {}
  chatPinnedUsernames.value = []
  chatHiddenUsernames.value = []
  chatUnreadInitialized = false
  resetTaskStatusTracking()`
if (!text.includes(logoutReset)) throw new Error('logout reset missing')
text = text.replace(logoutReset, `  chatSideEffects.resetChatLocalState()
  resetTaskStatusTracking()`)

// logout also calls stopChatPolling via side effect already replaced — reset also stops polling.
// That would double-call stop which is fine. But we replaced stopChatPolling() globally including logout.
// resetChatLocalState also stops. Double stop is OK.

// fetchUserSystemMessages calls syncSystemNoticeUnread — function is hoisted? 
// syncSystemNoticeUnread is now const from createChatDomain which runs AFTER fetchUserSystemMessages definition.
// Call-time is OK.

text = text.replace(/\n/g, nl)
fs.writeFileSync(appPath, text)
console.log('patched app', text.split(/\r?\n/).length)
