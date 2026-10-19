import { getCampusApp } from '../app/campusAppState'
import type { FeedbackViewInject } from '../context/feedbackViewInject'

export function useFeedbackStore(): FeedbackViewInject {
  return getCampusApp().domains.feedback as unknown as FeedbackViewInject
}
