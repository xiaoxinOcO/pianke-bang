import { getCampusApp } from '../app/campusAppState'

export function useAuthStore() {
  return getCampusApp().domains.auth
}
