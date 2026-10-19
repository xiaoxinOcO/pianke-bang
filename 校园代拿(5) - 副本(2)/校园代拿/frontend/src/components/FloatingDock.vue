<script setup lang="ts">
import { computed, nextTick, toRef } from 'vue'
import { useShellStore } from '../stores/useShellStore'

const r = useShellStore()

const isLoggedIn = toRef(r, 'isLoggedIn')
const totalUnread = toRef(r, 'totalUnread')
const currentView = toRef(r, 'currentView')

const goPublish = r.goPublish as () => void
const goChat = r.goChat as () => void
const goHall = r.goHall as () => void
const openFeedbackPage = r.openFeedbackPage as () => void
const openSupportChat = r.openSupportChat as () => void
const openAuthPage = r.openAuthPage as (mode: 'login' | 'register') => void

type RailItem = {
  id: string
  label: string
  badge?: number
  run: () => void | Promise<void>
}

function onPublish() {
  if (isLoggedIn.value) {
    goPublish()
  } else {
    openAuthPage('register')
  }
}

async function onMessages() {
  if (!isLoggedIn.value) {
    openAuthPage('login')
    return
  }
  await goChat()
}

async function onHelp() {
  if (currentView.value !== 'hall') {
    goHall()
    await nextTick()
  }
  document.getElementById('pk-faq-title')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function onFeedback() {
  if (!isLoggedIn.value) {
    openAuthPage('login')
    return
  }
  openFeedbackPage()
}

async function onCustomerService() {
  if (!isLoggedIn.value) {
    openAuthPage('login')
    return
  }
  openSupportChat()
}

const railItems = computed<RailItem[]>(() => [
  { id: 'publish', label: '发布', run: onPublish },
  {
    id: 'chat',
    label: '消息',
    badge: isLoggedIn.value && totalUnread.value > 0 ? totalUnread.value : 0,
    run: onMessages
  },
  { id: 'help', label: '帮助', run: onHelp },
  { id: 'feedback', label: '反馈', run: onFeedback },
  { id: 'service', label: '客服', run: onCustomerService }
])

async function runItem(item: RailItem) {
  await item.run()
}
</script>

<template>
  <aside class="pk-side-rail" aria-label="快捷操作">
    <button
      v-for="item in railItems"
      :key="item.id"
      type="button"
      class="pk-side-rail-item"
      @click="runItem(item)"
    >
      <span class="pk-side-rail-icon" aria-hidden="true">
        <svg v-if="item.id === 'publish'" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8" />
          <path d="M12 8v8M8 12h8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        </svg>
        <svg v-else-if="item.id === 'chat'" viewBox="0 0 24 24" fill="none">
          <path
            d="M5 6.5A2.5 2.5 0 0 1 7.5 4h9A2.5 2.5 0 0 1 19 6.5v7A2.5 2.5 0 0 1 16.5 16H10l-4.5 3.5V16H7.5A2.5 2.5 0 0 1 5 13.5v-7Z"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linejoin="round"
          />
        </svg>
        <svg v-else-if="item.id === 'help'" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8" />
          <path
            d="M9.5 9.2a2.7 2.7 0 1 1 4.8 1.7c-.8.6-1.3 1.2-1.3 2.1v.5"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
          />
          <circle cx="12" cy="16.8" r="1" fill="currentColor" />
        </svg>
        <svg v-else-if="item.id === 'feedback'" viewBox="0 0 24 24" fill="none">
          <path
            d="M6 5.5h12v11H9.2L6 19.5V5.5Z"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linejoin="round"
          />
          <path d="M9 9.5h6M9 12.5h4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none">
          <path
            d="M6.5 14.5a5.5 5.5 0 0 1 11 0v2.2a1.3 1.3 0 0 1-1.3 1.3h-1.1a1.3 1.3 0 0 0-1.2.8l-.3.7a1 1 0 0 1-1.8 0l-.3-.7a1.3 1.3 0 0 0-1.2-.8H7.8a1.3 1.3 0 0 1-1.3-1.3v-2.2Z"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linejoin="round"
          />
          <path d="M9.5 11.5a2.5 2.5 0 0 1 5 0" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
        </svg>
      </span>
      <em v-if="item.badge && item.badge > 0" class="pk-side-rail-badge">{{ item.badge > 99 ? '99+' : item.badge }}</em>
      <span class="pk-side-rail-label">{{ item.label }}</span>
    </button>
  </aside>
</template>
