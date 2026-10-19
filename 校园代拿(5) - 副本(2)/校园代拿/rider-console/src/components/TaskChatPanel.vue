<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { chatBase } from '../constants/api'
import type { ChatMessageItem } from '../types'
import {
  chatMessageImage,
  chatMessageText,
  encodeChatImage,
  parseChatImage,
  prepareChatImageDataUrl
} from '../utils/chatImage'
import { request } from '../utils/http'

const props = withDefaults(
  defineProps<{
    targetUser: string
    myName: string
    taskTitle?: string
    peerAvatarUrl?: string
    myAvatarUrl?: string
    expanded?: boolean
    immersive?: boolean
  }>(),
  { expanded: true, immersive: false, peerAvatarUrl: '', myAvatarUrl: '' }
)

const messages = ref<ChatMessageItem[]>([])
const input = ref('')
const loading = ref(false)
const sending = ref(false)
const error = ref('')
const panelEl = ref<HTMLElement | null>(null)
const imageInputRef = ref<HTMLInputElement | null>(null)
const previewUrl = ref('')

let pollTimer: number | null = null

const rootClass = computed(() => ({
  'task-chat': true,
  'task-chat--expanded': props.expanded,
  'task-chat--immersive': props.immersive
}))

function formatTime(raw?: string) {
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw.replace('T', ' ').slice(11, 16)
  return d.toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function avatarLetter(name: string) {
  return String(name || '?').trim().slice(0, 1).toUpperCase()
}

function messageImage(msg: ChatMessageItem) {
  return chatMessageImage(msg.content)
}

function messageText(msg: ChatMessageItem) {
  return chatMessageText(msg.content)
}

function isImageMessage(msg: ChatMessageItem) {
  return !!parseChatImage(msg.content)
}

function openPreview(url: string) {
  if (!url) return
  previewUrl.value = url
}

function closePreview() {
  previewUrl.value = ''
}

function shouldShowDateDivider(index: number) {
  const current = messages.value[index]
  const prev = messages.value[index - 1]
  if (!current?.createdAt) return false
  if (!prev?.createdAt) return index === 0
  return current.createdAt.slice(0, 10) !== prev.createdAt.slice(0, 10)
}

function formatDateDivider(raw?: string) {
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw.slice(0, 10)
  const today = new Date()
  if (
    d.getFullYear() === today.getFullYear() &&
    d.getMonth() === today.getMonth() &&
    d.getDate() === today.getDate()
  ) {
    return '今天'
  }
  return d.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })
}

function scrollToBottom() {
  const el = panelEl.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

async function loadHistory(scroll = true) {
  const target = props.targetUser.trim()
  if (!target) {
    messages.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    const el = panelEl.value
    const stickToBottom = scroll && (el ? el.scrollHeight - el.scrollTop - el.clientHeight < 120 : true)
    messages.value = await request<ChatMessageItem[]>(
      `${chatBase}/history?targetUser=${encodeURIComponent(target)}`
    )
    if (stickToBottom) {
      await nextTick()
      scrollToBottom()
    }
  } catch (err) {
    error.value = (err as Error).message || '加载消息失败'
  } finally {
    loading.value = false
  }
}

async function sendRawContent(content: string) {
  const target = props.targetUser.trim()
  if (!target || !content || sending.value) return
  sending.value = true
  error.value = ''
  try {
    await request<ChatMessageItem>(`${chatBase}/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ toUsername: target, content })
    })
    await loadHistory(true)
  } catch (err) {
    error.value = (err as Error).message || '发送失败'
  } finally {
    sending.value = false
  }
}

async function sendMessage() {
  const text = input.value.trim()
  if (!text) return
  input.value = ''
  await sendRawContent(text)
}

function pickImage() {
  imageInputRef.value?.click()
}

async function onImageSelected(e: Event) {
  const inputEl = e.target as HTMLInputElement
  const file = inputEl.files?.[0]
  inputEl.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    error.value = '请选择图片文件'
    return
  }
  if (sending.value) return
  sending.value = true
  error.value = ''
  try {
    const dataUrl = await prepareChatImageDataUrl(file)
    const caption = input.value.trim()
    input.value = ''
    const target = props.targetUser.trim()
    if (!target) throw new Error('对方用户无效')
    await request<ChatMessageItem>(`${chatBase}/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ toUsername: target, content: encodeChatImage(dataUrl, caption) })
    })
    await loadHistory(true)
  } catch (err) {
    error.value = (err as Error).message || '图片发送失败'
  } finally {
    sending.value = false
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Enter' || e.shiftKey) return
  e.preventDefault()
  void sendMessage()
}

function startPolling() {
  stopPolling()
  pollTimer = window.setInterval(() => {
    void loadHistory(false)
  }, 5000)
}

function stopPolling() {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

watch(
  () => props.targetUser,
  () => {
    void loadHistory(true)
  }
)

onMounted(() => {
  void loadHistory(true)
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div :class="rootClass">
    <header v-if="!immersive" class="task-chat-head">
      <div class="task-chat-peer">
        <div class="task-chat-avatar">
          <img v-if="peerAvatarUrl" :src="peerAvatarUrl" alt="" />
          <template v-else>{{ avatarLetter(targetUser) }}</template>
        </div>
        <div>
          <h3>{{ targetUser }}</h3>
          <p>发布者<span v-if="taskTitle"> · {{ taskTitle }}</span></p>
        </div>
      </div>
      <button type="button" class="task-chat-refresh" :disabled="loading" @click="loadHistory(true)">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </header>

    <div ref="panelEl" class="task-chat-messages">
      <div class="task-chat-stream">
        <p v-if="loading && messages.length === 0" class="task-chat-empty">加载中…</p>
        <p v-else-if="messages.length === 0" class="task-chat-empty">
          和发布者打个招呼，确认取送细节吧
        </p>

        <template v-for="(msg, index) in messages" :key="msg.id">
          <div v-if="shouldShowDateDivider(index)" class="task-chat-date">
            {{ formatDateDivider(msg.createdAt) }}
          </div>
          <div class="task-chat-row" :class="msg.fromUsername === myName ? 'me' : 'other'">
            <div v-if="msg.fromUsername !== myName" class="task-chat-avatar sm">
              <img v-if="peerAvatarUrl" :src="peerAvatarUrl" alt="" />
              <template v-else>{{ avatarLetter(msg.fromUsername) }}</template>
            </div>
            <div class="task-chat-bubble-wrap">
              <div class="task-chat-bubble" :class="{ 'task-chat-bubble--media': isImageMessage(msg) }">
                <template v-if="isImageMessage(msg)">
                  <img
                    class="task-chat-inline-image"
                    :src="messageImage(msg)"
                    alt="聊天图片"
                    @click="openPreview(messageImage(msg))"
                  />
                  <p v-if="messageText(msg)" class="task-chat-caption">{{ messageText(msg) }}</p>
                </template>
                <template v-else>{{ msg.content }}</template>
              </div>
              <time>{{ formatTime(msg.createdAt) }}</time>
            </div>
            <div v-if="msg.fromUsername === myName" class="task-chat-avatar sm me">
              <img v-if="myAvatarUrl" :src="myAvatarUrl" alt="" />
              <template v-else>{{ avatarLetter(myName) }}</template>
            </div>
          </div>
        </template>
      </div>
    </div>

    <p v-if="error" class="task-chat-error">{{ error }}</p>

    <footer class="task-chat-composer">
      <div class="task-chat-input-row">
        <button
          type="button"
          class="task-chat-image-btn"
          :disabled="sending"
          title="发送图片"
          @click="pickImage"
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <rect x="3" y="5" width="18" height="14" rx="2" stroke="currentColor" stroke-width="1.8" />
            <circle cx="9" cy="10" r="1.6" fill="currentColor" />
            <path
              d="M3 16l5-4 3 2.5L16 9l5 7"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
        <input
          ref="imageInputRef"
          type="file"
          accept="image/*"
          class="task-chat-file-input"
          @change="onImageSelected"
        />
        <textarea
          v-model="input"
          rows="1"
          maxlength="500"
          placeholder="发消息…"
          :disabled="sending"
          @keydown="onKeydown"
        />
        <button
          type="button"
          class="task-chat-send"
          :disabled="sending || !input.trim()"
          title="发送"
          @click="sendMessage"
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M5 12h12m0 0-4-4m4 4-4 4"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
      </div>
      <p v-if="!immersive" class="task-chat-hint">Enter 发送 · Shift+Enter 换行 · 可发图片</p>
    </footer>

    <div v-if="previewUrl" class="task-chat-preview-mask" @click.self="closePreview">
      <img :src="previewUrl" alt="预览" class="task-chat-preview-image" />
    </div>
  </div>
</template>

<style scoped>
.task-chat {
  display: flex;
  flex-direction: column;
  background: #fff;
  overflow: hidden;
  position: relative;
}

.task-chat--expanded {
  height: 100%;
  min-height: 0;
}

.task-chat--immersive {
  background: #ebebeb;
}

.task-chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid #e7e5e4;
  background: #fff;
  flex-shrink: 0;
}

.task-chat-peer {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.task-chat-avatar {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: #10b981;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  flex-shrink: 0;
  overflow: hidden;
}

.task-chat-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.task-chat-avatar.sm {
  width: 38px;
  height: 38px;
  border-radius: 6px;
  font-size: 14px;
}

.task-chat-avatar.me {
  background: #3b82f6;
}

.task-chat-head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #111;
}

.task-chat-head p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #888;
}

.task-chat-refresh {
  border: none;
  background: #f5f5f4;
  color: #57534e;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.task-chat-messages {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
}

.task-chat--immersive .task-chat-messages {
  background: #ebebeb;
}

.task-chat-stream {
  max-width: 920px;
  margin: 0 auto;
}

.task-chat-empty {
  margin: 0;
  text-align: center;
  color: #999;
  font-size: 14px;
  padding: 60px 24px;
}

.task-chat-date {
  text-align: center;
  margin: 12px 0 16px;
  font-size: 12px;
  color: #999;
}

.task-chat-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
}

.task-chat-row.me {
  flex-direction: row;
  justify-content: flex-end;
}

.task-chat-bubble-wrap {
  max-width: min(78%, 720px);
  min-width: 0;
}

.task-chat-row.me .task-chat-bubble-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.task-chat-bubble {
  padding: 10px 14px;
  border-radius: 6px;
  background: #fff;
  font-size: 15px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: #111;
  box-shadow: 0 1px 1px rgba(0, 0, 0, 0.04);
}

.task-chat-bubble--media {
  padding: 6px;
  white-space: normal;
}

.task-chat-inline-image {
  display: block;
  max-width: min(260px, 70vw);
  max-height: 320px;
  border-radius: 8px;
  object-fit: cover;
  cursor: zoom-in;
  background: #f5f5f4;
}

.task-chat-caption {
  margin: 6px 4px 2px;
  font-size: 14px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

.task-chat-row.other .task-chat-bubble {
  border-radius: 4px 12px 12px 12px;
}

.task-chat-row.me .task-chat-bubble {
  background: #95ec69;
  border-radius: 12px 4px 12px 12px;
  box-shadow: none;
}

.task-chat-bubble-wrap time {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: #999;
}

.task-chat-row.me .task-chat-bubble-wrap time {
  text-align: right;
}

.task-chat-error {
  margin: 0;
  padding: 8px 16px;
  font-size: 12px;
  color: #dc2626;
  background: #fef2f2;
  flex-shrink: 0;
}

.task-chat-composer {
  flex-shrink: 0;
  padding: 10px 16px 14px;
  background: #f7f7f7;
  border-top: 1px solid #e0e0e0;
}

.task-chat--immersive .task-chat-composer {
  background: #f7f7f7;
}

.task-chat-input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.task-chat-file-input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.task-chat-image-btn {
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 8px;
  background: #fff;
  color: #57534e;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  cursor: pointer;
  box-shadow: inset 0 0 0 1px #e5e5e5;
}

.task-chat-image-btn svg {
  width: 20px;
  height: 20px;
}

.task-chat-image-btn:hover:not(:disabled) {
  color: #059669;
  box-shadow: inset 0 0 0 1px #6ee7b7;
}

.task-chat-image-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.task-chat-input-row textarea {
  flex: 1;
  min-width: 0;
  resize: none;
  border: none;
  border-radius: 8px;
  padding: 10px 14px;
  font: inherit;
  font-size: 15px;
  line-height: 1.5;
  outline: none;
  min-height: 42px;
  max-height: 120px;
  background: #fff;
  box-shadow: inset 0 0 0 1px #e5e5e5;
}

.task-chat-input-row textarea:focus {
  box-shadow: inset 0 0 0 1px #10b981;
}

.task-chat-send {
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 8px;
  background: #10b981;
  color: #fff;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  cursor: pointer;
}

.task-chat-send svg {
  width: 20px;
  height: 20px;
}

.task-chat-send:hover:not(:disabled) {
  background: #059669;
}

.task-chat-send:disabled,
.task-chat-input-row textarea:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.task-chat-hint {
  margin: 6px 0 0;
  font-size: 11px;
  color: #aaa;
  text-align: right;
}

.task-chat-preview-mask {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(15, 23, 42, 0.72);
}

.task-chat-preview-image {
  max-width: 100%;
  max-height: 100%;
  border-radius: 10px;
  object-fit: contain;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.35);
}
</style>
