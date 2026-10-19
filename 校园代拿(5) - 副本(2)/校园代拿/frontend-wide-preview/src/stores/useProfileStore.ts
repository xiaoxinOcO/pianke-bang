import { getCampusApp } from '../app/campusAppState'
import type { ProfileViewInject } from '../context/profileViewInject'

export type ProfileStore = ProfileViewInject & {
  goWallet?: (sub?: string) => void
  goCoupons?: () => void
}

export function useProfileStore(): ProfileStore {
  return getCampusApp().domains.profile as unknown as ProfileStore
}
