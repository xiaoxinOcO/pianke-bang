export function chatReadStoreKey(username: string) {
  return `chat_read_upto_${username}`
}

export function chatLatestSeenStoreKey(username: string) {
  return `chat_latest_seen_${username}`
}

export function chatPinnedStoreKey(username: string) {
  return `chat_pinned_${username}`
}

export function chatHiddenStoreKey(username: string) {
  return `chat_hidden_${username}`
}

export function systemNoticeReadStoreKey(username: string) {
  return `system_notice_read_${username}`
}
