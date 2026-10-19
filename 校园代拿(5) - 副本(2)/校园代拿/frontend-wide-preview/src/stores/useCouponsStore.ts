import { getCampusApp } from '../app/campusAppState'
import type { CouponsViewInject } from '../context/couponsViewInject'

export function useCouponsStore(): CouponsViewInject {
  return getCampusApp().domains.coupons as unknown as CouponsViewInject
}
