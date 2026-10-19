import { getCampusApp } from '../app/campusAppState'
import type { PublishViewInject } from '../context/publishViewInject'

export function usePublishStore(): PublishViewInject {
  return getCampusApp().domains.publish as unknown as PublishViewInject
}
