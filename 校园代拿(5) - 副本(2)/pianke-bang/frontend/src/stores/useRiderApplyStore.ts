import { getCampusApp } from '../app/campusAppState'
import type { RiderApplyViewInject } from '../context/riderApplyViewInject'

export type RiderApplyStore = RiderApplyViewInject & {
  goHall?: () => void
  goProfile?: () => void
}

export function useRiderApplyStore(): RiderApplyStore {
  return getCampusApp().domains.riderApply as unknown as RiderApplyStore
}
