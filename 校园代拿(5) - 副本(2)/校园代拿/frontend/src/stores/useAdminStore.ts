import { getCampusApp } from '../app/campusAppState'
import type { AdminShellInject } from '../context/adminShellInject'

export function useAdminStore(): AdminShellInject {
  return getCampusApp().domains.admin as unknown as AdminShellInject
}
