const fs = require('fs')
const path = require('path')

const appPath = path.join(__dirname, '..', 'campusAppState.ts')
let src = fs.readFileSync(appPath, 'utf8')
const nl = src.includes('\r\n') ? '\r\n' : '\n'

// 1. Add import after createPublishDomain
if (!src.includes('createSupportDomain')) {
  src = src.replace(
    `import { createPublishDomain } from './domains/createPublishDomain'${nl}`,
    `import { createPublishDomain } from './domains/createPublishDomain'${nl}` +
      `import { createSupportDomain } from './domains/createSupportDomain'${nl}`
  )
}

// 2. Delete types/state block
const typeStart = src.indexOf(`${nl}type SupportChatMessage = {`)
const typeEndMarker = `${nl}const kickedDialogOpen = ref(false)`
const typeEnd = src.indexOf(typeEndMarker)
if (typeStart < 0 || typeEnd < 0) {
  throw new Error(`Could not find SupportChatMessage / kickedDialogOpen markers ${typeStart} ${typeEnd}`)
}
src = src.slice(0, typeStart) + src.slice(typeEnd)

// 3. Delete function block openSupportChat ... transferToHumanSupport (keep openChatWith)
const fnStart = src.indexOf(`${nl}function openSupportChat()`)
const fnEnd = src.indexOf(`${nl}async function openChatWith(`)
if (fnStart < 0 || fnEnd < 0) {
  throw new Error(`Could not find openSupportChat / openChatWith markers ${fnStart} ${fnEnd}`)
}
src = src.slice(0, fnStart) + src.slice(fnEnd)

// 4. Add supportSideEffects stub before authSideEffects
const authStub = `const authSideEffects = {${nl}  applyAuthLoginSuccess: (async () => {}) as () => Promise<void>,${nl}  clearSupportChatState: (() => {}) as () => void${nl}}`
const supportStub = `const supportSideEffects = {${nl}  handleHumanAgentChat: ((_o: string, _c: string, _m: number) => false) as (other: string, content: string, msgId: number) => boolean,${nl}  clearSupportChatState: (() => {}) as () => void${nl}}${nl}${nl}const authSideEffects = {${nl}  applyAuthLoginSuccess: (async () => {}) as () => Promise<void>${nl}}`
if (!src.includes('const supportSideEffects = {')) {
  if (!src.includes(authStub)) {
    throw new Error('authSideEffects stub not found in expected form')
  }
  src = src.replace(authStub, supportStub)
}

// 5. Change createAuthDomain clearSupportChatState wiring
src = src.replace(
  'clearSupportChatState: () => authSideEffects.clearSupportChatState()',
  'clearSupportChatState: () => supportSideEffects.clearSupportChatState()'
)

// 6. Replace handleRealtimeEvent support block
const oldRt = `    // 客服消息：接入智能客服窗，不进通讯录未读${nl}    if (${nl}      supportHumanMode.value &&${nl}      supportAssignedAgent.value &&${nl}      usernameMatches(other, supportAssignedAgent.value)${nl}    ) {${nl}      ingestSupportHumanIncoming(content, msgId)${nl}      unreadMap.value[other] = 0${nl}      if (msgId > 0) {${nl}        readUpToIncomingIdMap.value[other] = Math.max(readUpToIncomingIdMap.value[other] || 0, msgId)${nl}        markIncomingSynced(other, msgId)${nl}      }${nl}      if (!showSupportChat.value) {${nl}        showRealtimeToast({${nl}          title: '人工客服回复',${nl}          message: content.slice(0, 40) || '客服发来一条消息',${nl}          kind: 'chat'${nl}        })${nl}      }${nl}      return${nl}    }`

const newRt = `    // 客服消息：接入智能客服窗，不进通讯录未读${nl}    if (supportSideEffects.handleHumanAgentChat(other, content, msgId)) {${nl}      unreadMap.value[other] = 0${nl}      if (msgId > 0) {${nl}        readUpToIncomingIdMap.value[other] = Math.max(readUpToIncomingIdMap.value[other] || 0, msgId)${nl}        markIncomingSynced(other, msgId)${nl}      }${nl}      return${nl}    }`

if (!src.includes(oldRt)) {
  if (!src.includes('supportSideEffects.handleHumanAgentChat')) {
    throw new Error('Realtime support block not found')
  }
} else {
  src = src.replace(oldRt, newRt)
}

// 7. Insert createSupportDomain after goRiderApply
const goRiderBlock = `function goRiderApply() {${nl}  if (!isLoggedIn.value) {${nl}    return${nl}  }${nl}  showUserDropdown.value = false${nl}  riderApplyError.value = ''${nl}  setView('riderApply')${nl}}${nl}${nl}`

const supportCreate = `function goRiderApply() {${nl}  if (!isLoggedIn.value) {${nl}    return${nl}  }${nl}  showUserDropdown.value = false${nl}  riderApplyError.value = ''${nl}  setView('riderApply')${nl}}${nl}${nl}const {${nl}  showSupportChat,${nl}  supportChatSending,${nl}  supportTransferLoading,${nl}  supportChatMessages,${nl}  supportAgentOnline,${nl}  supportAgentStatusText,${nl}  supportHumanMode,${nl}  supportAssignedAgent,${nl}  supportAssignedAgentAvatar,${nl}  supportHandoffJoined,${nl}  supportHumanHistoryAfterId,${nl}  supportIngestedChatIds,${nl}  SUPPORT_CS_RATING_TAGS,${nl}  supportCsRatingVisible,${nl}  supportCsRatingAgent,${nl}  supportCsRatingScore,${nl}  supportCsRatingTags,${nl}  supportCsRatingComment,${nl}  supportCsRatingSubmitting,${nl}  supportCsRatingDone,${nl}  openSupportChat,${nl}  closeSupportChat,${nl}  clearSupportChatState,${nl}  stopSupportHumanPolling,${nl}  refreshSupportAgentAvailability,${nl}  openSupportWallet,${nl}  openSupportMyTasks,${nl}  openSupportRiderApply,${nl}  openSupportCoupons,${nl}  openSupportTaskById,${nl}  sendSupportChatMessage,${nl}  transferToHumanSupport,${nl}  endSupportHumanSession,${nl}  ingestSupportHumanIncoming,${nl}  supportCsRatingScoreLabel,${nl}  toggleSupportCsRatingTag,${nl}  skipSupportCsRating,${nl}  submitSupportCsRating${nl}} = createSupportDomain({${nl}  isLoggedIn,${nl}  myName,${nl}  request,${nl}  errorMsg,${nl}  showRealtimeToast,${nl}  goWallet,${nl}  goProfile,${nl}  goRiderApply,${nl}  goCoupons,${nl}  openTaskDetail,${nl}  fetchMyTasksForProfile,${nl}  fetchWalletData,${nl}  walletOverview,${nl}  walletTransactions,${nl}  walletWithdraws,${nl}  mineTasks,${nl}  allTasks,${nl}  tasks,${nl}  creditScore,${nl}  blockedUntil,${nl}  isRiderApproved,${nl}  riderApplicationPending,${nl}  openAuthPage,${nl}  hallFaqItems,${nl}  unreadMap${nl}})${nl}${nl}supportSideEffects.clearSupportChatState = clearSupportChatState${nl}supportSideEffects.handleHumanAgentChat = (other, content, msgId) => {${nl}  if (!supportHumanMode.value || !supportAssignedAgent.value || !usernameMatches(other, supportAssignedAgent.value)) return false${nl}  ingestSupportHumanIncoming(content, msgId)${nl}  if (!showSupportChat.value) {${nl}    showRealtimeToast({ title: '人工客服回复', message: content.slice(0, 40) || '客服发来一条消息', kind: 'chat' })${nl}  }${nl}  return true${nl}}${nl}${nl}`

if (!src.includes('= createSupportDomain({')) {
  if (!src.includes(goRiderBlock)) {
    throw new Error('goRiderApply block not found for insertion')
  }
  src = src.replace(goRiderBlock, supportCreate)
}

fs.writeFileSync(appPath, src, 'utf8')
console.log('patched campusAppState.ts lines', src.split(/\r?\n/).length)

// 8. Update index.ts
const indexPath = path.join(__dirname, 'index.ts')
let indexSrc = fs.readFileSync(indexPath, 'utf8')
const indexNl = indexSrc.includes('\r\n') ? '\r\n' : '\n'
if (!indexSrc.includes('createSupportDomain')) {
  indexSrc = indexSrc.replace(
    `export { createPublishDomain } from './createPublishDomain'${indexNl}`,
    `export { createPublishDomain } from './createPublishDomain'${indexNl}` +
      `export { createSupportDomain } from './createSupportDomain'${indexNl}`
  )
  fs.writeFileSync(indexPath, indexSrc, 'utf8')
  console.log('updated index.ts')
}

// 9. Delete temp raw/extract files
for (const f of fs.readdirSync(__dirname)) {
  if (f.startsWith('_raw_') || f.startsWith('_extract_')) {
    fs.unlinkSync(path.join(__dirname, f))
    console.log('deleted', f)
  }
}
