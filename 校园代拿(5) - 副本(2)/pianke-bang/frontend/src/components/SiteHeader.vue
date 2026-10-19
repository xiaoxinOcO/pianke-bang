<script setup lang="ts">
import { computed, ref, toRef } from 'vue'
import { useShellStore } from '../stores/useShellStore'
import { BRAND_LOGO_URL, BRAND_WORDMARK_URL } from '../constants/brand'
const r = useShellStore()

const showUserDropdown = toRef(r, 'showUserDropdown')
const currentView = toRef(r, 'currentView')
const isLoggedIn = toRef(r, 'isLoggedIn')
const isAdmin = toRef(r, 'isAdmin')
const isRiderApproved = toRef(r, 'isRiderApproved')
const myName = toRef(r, 'myName')
const myAvatarUrl = toRef(r, 'myAvatarUrl')
const totalUnread = toRef(r, 'totalUnread')
const walletOverview = toRef(r, 'walletOverview')
const walletBalanceLoading = toRef(r, 'walletBalanceLoading')
const creditScore = toRef(r, 'creditScore')
const filters = toRef(r as { filters: { keyword: string; taskType: string } }, 'filters')

const goHall = r.goHall as () => void
const goPublish = r.goPublish as () => void
const goProfile = r.goProfile as () => void
const goWallet = r.goWallet as (sub?: string) => void
const goChat = r.goChat as () => void
const goAdmin = r.goAdmin as () => void
const goPersonalInfo = r.goPersonalInfo as () => void
const goRiderApply = r.goRiderApply as () => void
const logout = r.logout as () => void
const openAuthPage = r.openAuthPage as (mode: 'login' | 'register') => void
const normalizeImageUrl = r.normalizeImageUrl as (url: string) => string
const applyHeroSearch = r.applyHeroSearch as () => void | Promise<void>
const selectHeroCategoryTag = r.selectHeroCategoryTag as (value: string) => void | Promise<void>
const heroCategoryTags = (r.heroCategoryTags || []) as Array<{ label: string; value: string }>

const profileSubModule = toRef(r, 'profileSubModule')

let userMenuCloseTimer: number | null = null
const userMenuPanelStyle = ref<Record<string, string>>({})

const roleLabel = computed(() => {
  if (isRiderApproved.value) return '已认证接单员'
  return '校园用户'
})

/** 仅大厅需要顶栏搜索；发布/我的等页面用简洁顶栏 */
const showHallSearch = computed(() => currentView.value === 'hall')

function formatBalance(value: unknown) {
  return Number(value || 0).toFixed(2)
}

function navActive(view: string) {
  return currentView.value === view
}

function clearUserMenuCloseTimer() {
  if (userMenuCloseTimer != null) {
    window.clearTimeout(userMenuCloseTimer)
    userMenuCloseTimer = null
  }
}

function syncUserMenuPanelPosition() {
  const el = document.querySelector('.app-shell--piankebang .pk-account-hub-trigger') as HTMLElement | null
  if (!el) return
  const rect = el.getBoundingClientRect()
  userMenuPanelStyle.value = {
    top: `${Math.round(rect.bottom + 10)}px`,
    right: `${Math.max(12, Math.round(window.innerWidth - rect.right))}px`,
    left: 'auto'
  }
}

function closeMenu() {
  clearUserMenuCloseTimer()
  showUserDropdown.value = false
}

function openUserMenu() {
  clearUserMenuCloseTimer()
  syncUserMenuPanelPosition()
  showUserDropdown.value = true
}

function scheduleCloseUserMenu() {
  clearUserMenuCloseTimer()
  userMenuCloseTimer = window.setTimeout(() => {
    showUserDropdown.value = false
    userMenuCloseTimer = null
  }, 180)
}

function toggleUserMenu() {
  clearUserMenuCloseTimer()
  if (showUserDropdown.value) {
    showUserDropdown.value = false
    return
  }
  syncUserMenuPanelPosition()
  showUserDropdown.value = true
}

function runMenuAction(action: () => void) {
  action()
  closeMenu()
}

function goMine() {
  if (isLoggedIn.value) {
    goProfile()
    profileSubModule.value = 'published'
  } else {
    openAuthPage('login')
  }
}
</script>

<template>
  <header class="pk-topbar" :class="{ 'pk-topbar--compact': !showHallSearch }">
    <button type="button" class="pk-brand" @click="goHall()">
      <img class="pk-brand-mark" :src="BRAND_LOGO_URL" alt="" />
      <div class="pk-brand-copy">
        <img class="pk-brand-wordmark" :src="BRAND_WORDMARK_URL" alt="片刻帮" />
        <span>顺路帮忙，刚好值得</span>
      </div>
    </button>

    <div v-if="showHallSearch" class="pk-top-search">
      <input
        v-model="filters.keyword"
        type="search"
        placeholder="搜索任务、地点或类型…"
        aria-label="搜索任务"
        @keyup.enter="applyHeroSearch()"
      />
      <button class="pk-btn pk-btn-accent pk-top-search-btn" type="button" @click="applyHeroSearch()">搜索</button>
    </div>

    <div class="pk-topbar-right">
      <nav class="pk-topnav" aria-label="主导航">
        <button type="button" class="pk-nav-link" :class="{ active: navActive('hall') }" @click="goHall()">发现</button>
        <button
          type="button"
          class="pk-nav-link"
          :class="{ active: navActive('publish') }"
          @click="isLoggedIn ? goPublish() : openAuthPage('register')"
        >
          发布
        </button>
        <button type="button" class="pk-nav-link" :class="{ active: navActive('profile') }" @click="goMine()">我的</button>
        <button
          type="button"
          class="pk-nav-link pk-nav-badge-wrap"
          :class="{ active: navActive('chat') }"
          @click="isLoggedIn ? goChat() : openAuthPage('login')"
        >
          消息
          <em v-if="isLoggedIn && totalUnread > 0">{{ totalUnread > 99 ? '99+' : totalUnread }}</em>
        </button>
        <button v-if="isAdmin" type="button" class="pk-nav-link" :class="{ active: navActive('admin') }" @click="goAdmin()">
          管理
        </button>
      </nav>

      <div class="pk-topbar-actions">
        <button v-if="!isLoggedIn" type="button" class="pk-btn pk-btn-ghost" @click="openAuthPage('login')">登录</button>

        <div
          v-if="isLoggedIn"
          class="pk-account-hub"
          :class="{ 'is-open': showUserDropdown }"
          @mouseenter="openUserMenu()"
          @mouseleave="scheduleCloseUserMenu()"
        >
          <button
            type="button"
            class="pk-account-hub-trigger"
            :title="myName"
            :aria-expanded="showUserDropdown"
            aria-haspopup="menu"
            @click.stop="toggleUserMenu()"
          >
            <img v-if="myAvatarUrl" :src="normalizeImageUrl(myAvatarUrl)" alt="" class="pk-account-hub-avatar" />
            <span v-else class="pk-account-hub-avatar-fallback">{{ (myName || 'U').slice(0, 1).toUpperCase() }}</span>
            <span class="pk-account-hub-name">{{ myName }}</span>
            <span class="pk-account-hub-chevron" aria-hidden="true">{{ showUserDropdown ? '▴' : '▾' }}</span>
          </button>

          <Teleport to="body">
            <aside
              v-if="showUserDropdown"
              class="pk-user-menu pk-user-menu--panel"
              role="menu"
              :style="userMenuPanelStyle"
              @click.stop
              @mouseenter="openUserMenu()"
              @mouseleave="scheduleCloseUserMenu()"
            >
              <header class="pk-user-menu-drawer-head">
                <div class="pk-user-menu-head">
                  <img
                    v-if="myAvatarUrl"
                    :src="normalizeImageUrl(myAvatarUrl)"
                    alt=""
                    class="pk-user-menu-avatar"
                  />
                  <div v-else class="pk-user-menu-avatar pk-user-menu-avatar-fallback">
                    {{ (myName || 'U').slice(0, 1).toUpperCase() }}
                  </div>
                  <div class="pk-user-menu-meta">
                    <strong>{{ myName }}</strong>
                    <span>{{ roleLabel }} · 信誉 {{ creditScore }}</span>
                  </div>
                </div>
                <button type="button" class="pk-user-menu-close" aria-label="关闭菜单" @click="closeMenu()">
                  <span aria-hidden="true">×</span>
                </button>
              </header>

              <div class="pk-user-menu-body">
                <div class="pk-user-menu-balance" role="button" tabindex="0" @click="runMenuAction(() => goWallet())">
                  <span>可用余额</span>
                  <strong v-if="walletBalanceLoading">同步中…</strong>
                  <strong v-else>¥ {{ formatBalance(walletOverview?.balance) }}</strong>
                </div>

                <div class="pk-user-menu-group">
                  <button type="button" class="pk-user-menu-item" role="menuitem" @click="runMenuAction(() => goPersonalInfo())">
                    <span class="pk-user-menu-icon" aria-hidden="true">👤</span>
                    <span>个人资料</span>
                  </button>
                  <button
                    type="button"
                    class="pk-user-menu-item"
                    role="menuitem"
                    @click="
                      runMenuAction(() => {
                        goProfile()
                        profileSubModule = 'published'
                      })
                    "
                  >
                    <span class="pk-user-menu-icon" aria-hidden="true">📋</span>
                    <span>我的任务</span>
                  </button>
                  <button type="button" class="pk-user-menu-item" role="menuitem" @click="runMenuAction(() => goRiderApply())">
                    <span class="pk-user-menu-icon" aria-hidden="true">🛵</span>
                    <span>接单员申请</span>
                  </button>
                  <button type="button" class="pk-user-menu-item" role="menuitem" @click="runMenuAction(() => goWallet())">
                    <span class="pk-user-menu-icon" aria-hidden="true">💰</span>
                    <span>我的钱包</span>
                  </button>
                  <button type="button" class="pk-user-menu-item" role="menuitem" @click="runMenuAction(() => goChat())">
                    <span class="pk-user-menu-icon" aria-hidden="true">💬</span>
                    <span>私聊消息</span>
                    <em v-if="totalUnread > 0" class="pk-user-menu-badge">{{ totalUnread > 99 ? '99+' : totalUnread }}</em>
                  </button>
                  <button v-if="isAdmin" type="button" class="pk-user-menu-item" role="menuitem" @click="runMenuAction(() => goAdmin())">
                    <span class="pk-user-menu-icon" aria-hidden="true">⚙️</span>
                    <span>管理后台</span>
                  </button>
                </div>
              </div>

              <div class="pk-user-menu-foot">
                <button type="button" class="pk-user-menu-logout" role="menuitem" @click="runMenuAction(() => logout())">
                  退出登录
                </button>
              </div>
            </aside>
          </Teleport>
        </div>
      </div>
    </div>

    <div v-if="showHallSearch" class="pk-top-hot" aria-label="快捷分类">
      <button
        v-for="tag in heroCategoryTags"
        :key="`top-tag-${tag.value || 'all'}`"
        type="button"
        class="pk-top-hot-link"
        :class="{ active: filters.taskType === tag.value }"
        @click="selectHeroCategoryTag(tag.value)"
      >
        {{ tag.label }}
      </button>
    </div>
  </header>
</template>
