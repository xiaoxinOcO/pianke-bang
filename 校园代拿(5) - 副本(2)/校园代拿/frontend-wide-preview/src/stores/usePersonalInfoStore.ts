import { getCampusApp } from '../app/campusAppState'
import type { PersonalInfoViewInject } from '../context/personalInfoViewInject'

export type PersonalInfoStore = PersonalInfoViewInject & {
  goProfile?: () => void
}

export function usePersonalInfoStore(): PersonalInfoStore {
  return getCampusApp().domains.personalInfo as unknown as PersonalInfoStore
}
