import type { Ref } from 'vue'
import { apiOrigin } from '../../constants/api'
import type { FeedbackSubmitPayload } from '../../context/feedbackViewInject'
import type { PageView } from '../../types/app'

type FeedbackRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type FeedbackDomainDeps = {
  isLoggedIn: Ref<boolean>
  errorMsg: Ref<string>
  currentView: Ref<PageView>
  request: FeedbackRequest
  showProfileNotice: (text: string) => void
  openAuthPage: (mode: 'login' | 'register') => void
  setView: (view: PageView) => void
}

/**
 * 用户反馈页：提交反馈 + 打开/跳转反馈页。
 * 管理台反馈列表仍归 admin 域。
 */
export function createFeedbackDomain(deps: FeedbackDomainDeps) {
  function buildFeedbackPageUrl() {
    return `${window.location.origin}${window.location.pathname}${window.location.search}#/feedback`
  }

  /** 在新标签页打开反馈中心（闲鱼同款交互） */
  function openFeedbackPage() {
    if (!deps.isLoggedIn.value) {
      deps.openAuthPage('login')
      return
    }
    if (deps.currentView.value === 'feedback') return
    // 不用 noopener，以便新标签页通过 opener 同步 sessionStorage 中的登录态
    window.open(buildFeedbackPageUrl(), '_blank')
  }

  function goFeedback() {
    if (!deps.isLoggedIn.value) {
      deps.openAuthPage('login')
      return
    }
    deps.setView('feedback')
  }

  async function submitUserFeedback(payload: FeedbackSubmitPayload) {
    if (!deps.isLoggedIn.value) {
      deps.openAuthPage('login')
      return false
    }
    deps.errorMsg.value = ''
    try {
      const res = await deps.request<{ message?: string }>(`${apiOrigin}/api/feedback`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type: payload.type,
          page: payload.page,
          content: payload.content.trim(),
          images: payload.images
        })
      })
      const message = (res.message || '').trim() || '反馈已提交，感谢你的建议！'
      deps.showProfileNotice(message)
      return true
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
      return false
    }
  }

  return {
    buildFeedbackPageUrl,
    openFeedbackPage,
    goFeedback,
    submitUserFeedback
  }
}
