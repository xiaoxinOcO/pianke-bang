const fs = require('fs')
const path = require('path')

const base = __dirname
const types = fs.readFileSync(path.join(base, '_raw_types_state.txt'), 'utf8')
let funcs = fs.readFileSync(path.join(base, '_raw_funcs.txt'), 'utf8')

funcs = funcs.replace(/\r?\nauthSideEffects\.clearSupportChatState = clearSupportChatState\r?\n/, '\n')

const depNames = [
  'isLoggedIn',
  'myName',
  'request',
  'errorMsg',
  'showRealtimeToast',
  'goWallet',
  'goProfile',
  'goRiderApply',
  'goCoupons',
  'openTaskDetail',
  'fetchMyTasksForProfile',
  'fetchWalletData',
  'walletOverview',
  'walletTransactions',
  'walletWithdraws',
  'mineTasks',
  'allTasks',
  'creditScore',
  'blockedUntil',
  'isRiderApproved',
  'riderApplicationPending',
  'openAuthPage',
  'hallFaqItems',
  'unreadMap'
]

function smartReplace(code, names) {
  const strings = []
  let protectedCode = code.replace(/(['"`])(?:\\.|(?!\1).)*\1/g, (m) => {
    strings.push(m)
    return `__STR${strings.length - 1}__`
  })
  for (const name of names) {
    const re = new RegExp(`(?<![.\\w])${name}(?!\\w|\\s*:)`, 'g')
    protectedCode = protectedCode.replace(re, 'deps.' + name)
  }
  return protectedCode.replace(/__STR(\d+)__/g, (_, i) => strings[Number(i)])
}

funcs = smartReplace(funcs, depNames)

let typesBody = types
  .replace(/^type SupportChatMessage/, 'export type SupportChatMessage')
  .replace(/^type SupportToolCard/, 'export type SupportToolCard')

const stateIdx = typesBody.indexOf('const showSupportChat')
const typeDecls = typesBody.slice(0, stateIdx).trimEnd()
const stateDecls = typesBody.slice(stateIdx).trimEnd()

const indent = (text) =>
  text
    .split('\n')
    .map((l) => (l.length ? '  ' + l : ''))
    .join('\n')

const header = `import { computed, ref, type Ref } from 'vue'
import { apiOrigin, authBase } from '../../constants/api'
import type {
  ChatMessageItem,
  TaskItem,
  TaskStatus,
  UserPublicProfile,
  WalletOverview,
  WalletTransactionItem,
  WalletWithdrawItem
} from '../../types/app'
import { resolveTaskStatusLabel, statusLabelMap } from '../../utils/taskStatus'
import { usernameMatches } from '../../utils/taskParse'

type SupportRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type SupportDomainDeps = {
  isLoggedIn: Ref<boolean>
  myName: Ref<string>
  request: SupportRequest
  errorMsg: Ref<string>
  showRealtimeToast: (payload: {
    title: string
    message: string
    kind: 'chat' | 'task'
    chatUser?: string
  }) => void
  goWallet: (subModule?: 'overview' | 'withdraws' | 'transactions') => void | Promise<void>
  goProfile: () => void
  goRiderApply: () => void
  goCoupons: () => void | Promise<void>
  openTaskDetail: (task: TaskItem) => void | Promise<void>
  fetchMyTasksForProfile: () => void | Promise<void>
  fetchWalletData: () => void | Promise<void>
  walletOverview: Ref<WalletOverview>
  walletTransactions: Ref<WalletTransactionItem[]>
  walletWithdraws: Ref<WalletWithdrawItem[]>
  mineTasks: Ref<TaskItem[]>
  allTasks: Ref<TaskItem[]>
  tasks: Ref<TaskItem[]>
  creditScore: Ref<number>
  blockedUntil: Ref<string>
  isRiderApproved: Ref<boolean>
  riderApplicationPending: Ref<boolean>
  openAuthPage: (mode: 'login' | 'register') => void
  hallFaqItems: ReadonlyArray<{ question: string; answer: string }>
  unreadMap: Ref<Record<string, number>>
}

${typeDecls}

/**
 * 智能客服 / 人工转接：聊天窗状态、工具卡片、人工轮询与评价。
 */
export function createSupportDomain(deps: SupportDomainDeps) {
  const safeAllTasks = computed(() => (Array.isArray(deps.allTasks.value) ? deps.allTasks.value : []))

${indent(stateDecls)}

${indent(funcs)}

  return {
    showSupportChat,
    supportChatSending,
    supportTransferLoading,
    supportChatMessages,
    supportAgentOnline,
    supportAgentStatusText,
    supportHumanMode,
    supportAssignedAgent,
    supportAssignedAgentAvatar,
    supportHandoffJoined,
    supportHumanHistoryAfterId,
    supportIngestedChatIds,
    SUPPORT_CS_RATING_TAGS,
    supportCsRatingVisible,
    supportCsRatingAgent,
    supportCsRatingScore,
    supportCsRatingTags,
    supportCsRatingComment,
    supportCsRatingSubmitting,
    supportCsRatingDone,
    openSupportChat,
    closeSupportChat,
    clearSupportChatState,
    stopSupportHumanPolling,
    refreshSupportAgentAvailability,
    openSupportWallet,
    openSupportMyTasks,
    openSupportRiderApply,
    openSupportCoupons,
    openSupportTaskById,
    sendSupportChatMessage,
    transferToHumanSupport,
    endSupportHumanSession,
    ingestSupportHumanIncoming,
    supportCsRatingScoreLabel,
    toggleSupportCsRatingTag,
    skipSupportCsRating,
    submitSupportCsRating
  }
}
`

fs.writeFileSync(path.join(base, 'createSupportDomain.ts'), header, 'utf8')
console.log('wrote createSupportDomain.ts lines', header.split('\n').length)
