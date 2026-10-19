import { getCampusApp } from '../app/campusAppState'
import type { ChatViewInject } from '../context/chatViewInject'

export function useChatStore(): ChatViewInject {
  return getCampusApp().domains.chat as unknown as ChatViewInject
}
