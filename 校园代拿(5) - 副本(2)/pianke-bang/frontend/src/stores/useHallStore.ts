import { getCampusApp } from '../app/campusAppState'
import type { HallViewInject } from '../context/hallViewInject'

export function useHallStore(): HallViewInject {
  return getCampusApp().domains.hall as unknown as HallViewInject
}
