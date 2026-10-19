import { getCampusApp } from '../app/campusAppState'

export function useShellStore() {
  return getCampusApp().domains.shell
}
