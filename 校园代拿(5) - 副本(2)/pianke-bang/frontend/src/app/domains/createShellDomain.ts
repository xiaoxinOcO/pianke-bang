import { ref, type Ref } from 'vue'
import type { PageView } from '../../types/app'

export type ShellDomainDeps = {
  isLoggedIn: Ref<boolean>
  isAdmin: Ref<boolean>
  fetchMyTasksForProfile: () => void | Promise<void>
  loadAdminDashboardData: () => void
}

function parseViewFromHash(): PageView {
  const hash = window.location.hash.replace('#', '')
  if (hash === '/publish') return 'publish'
  if (hash === '/profile') return 'profile'
  if (hash === '/personal-info') return 'personalInfo'
  if (hash === '/chat') return 'chat'
  if (hash === '/wallet') return 'wallet'
  if (hash === '/coupons') return 'coupons'
  if (hash === '/rider-apply') return 'riderApply'
  if (hash === '/feedback') return 'feedback'
  if (hash === '/admin') return 'admin'
  return 'hall'
}

/**
 * 应用壳导航：当前页、顶栏下拉、侧栏分组、页面跳转。
 * 客服浮窗 / 大厅 hero 仍归 chat、hall。
 */
export function createShellDomain(deps: ShellDomainDeps) {
  const currentView = ref<PageView>(parseViewFromHash())
  const showUserDropdown = ref(false)
  const navGroupOpen = ref({
    task: false,
    profile: false,
    chat: false,
    wallet: false,
    admin: false
  })

  function setView(view: PageView) {
    currentView.value = view
    const path =
      view === 'hall'
        ? '/hall'
        : view === 'publish'
          ? '/publish'
          : view === 'profile'
            ? '/profile'
            : view === 'personalInfo'
              ? '/personal-info'
              : view === 'chat'
                ? '/chat'
                : view === 'wallet'
                  ? '/wallet'
                  : view === 'coupons'
                    ? '/coupons'
                    : view === 'riderApply'
                      ? '/rider-apply'
                      : view === 'feedback'
                        ? '/feedback'
                        : '/admin'
    window.location.hash = path
  }

  function goHall() {
    setView('hall')
  }

  function goProfile() {
    if (!deps.isLoggedIn.value) {
      return
    }
    showUserDropdown.value = false
    setView('profile')
    void deps.fetchMyTasksForProfile()
  }

  function goAdmin() {
    if (!deps.isLoggedIn.value || !deps.isAdmin.value) {
      return
    }
    setView('admin')
    deps.loadAdminDashboardData()
  }

  function toggleNavGroup(group: keyof typeof navGroupOpen.value) {
    navGroupOpen.value[group] = !navGroupOpen.value[group]
  }

  return {
    currentView,
    showUserDropdown,
    navGroupOpen,
    setView,
    goHall,
    goProfile,
    goAdmin,
    toggleNavGroup,
    parseViewFromHash
  }
}
