<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, toRef } from 'vue'
import { useFeedbackStore } from '../stores/useFeedbackStore'
import { BRAND_LOGO_URL, BRAND_WORDMARK_URL } from '../constants/brand'
import type { FeedbackType } from '../context/feedbackViewInject'

const r = useFeedbackStore()

const isLoggedIn = toRef(r, 'isLoggedIn')
const myName = toRef(r, 'myName')
const myAvatarUrl = toRef(r, 'myAvatarUrl')
const profileNotice = toRef(r, 'profileNotice')
const errorMsg = toRef(r, 'errorMsg')

const FEEDBACK_MAX = 500
const IMAGE_MAX = 5
const ACCEPT_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/apng', 'image/gif', 'image/bmp', 'image/webp']

const PAGE_OPTIONS = ['大厅', '发布任务', '消息', '钱包', '个人中心', '其它'] as const

const feedbackType = ref<FeedbackType>('suggest')
const feedbackPage = ref<string>('其它')
const feedbackContent = ref('')
const feedbackImages = ref<string[]>([])
const submitting = ref(false)
const uploadBusy = ref(false)
const formError = ref('')
const uploadZoneRef = ref<HTMLElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const contentLength = computed(() => feedbackContent.value.length)
const canSubmit = computed(
  () => feedbackContent.value.trim().length > 0 && !submitting.value && !uploadBusy.value
)

function selectType(type: FeedbackType) {
  feedbackType.value = type
}

function selectPage(page: string) {
  feedbackPage.value = page
}

function openFilePicker() {
  fileInputRef.value?.click()
}

async function addImageFile(file: File) {
  if (!ACCEPT_TYPES.includes(file.type)) {
    formError.value = '仅支持 JPG、JPEG、PNG、APNG、GIF、BMP 格式'
    return
  }
  if (feedbackImages.value.length >= IMAGE_MAX) {
    formError.value = `最多上传 ${IMAGE_MAX} 张截图`
    return
  }
  uploadBusy.value = true
  formError.value = ''
  try {
    const url = await r.compressImageForChat(file)
    feedbackImages.value.push(url)
  } catch {
    formError.value = '图片处理失败，请换一张重试'
  } finally {
    uploadBusy.value = false
  }
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  for (const file of files) {
    if (feedbackImages.value.length >= IMAGE_MAX) break
    await addImageFile(file)
  }
}

function removeImage(index: number) {
  feedbackImages.value.splice(index, 1)
}

async function onPaste(event: ClipboardEvent) {
  const items = event.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.kind !== 'file') continue
    const file = item.getAsFile()
    if (!file || !file.type.startsWith('image/')) continue
    event.preventDefault()
    await addImageFile(file)
    break
  }
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
}

async function onDrop(event: DragEvent) {
  event.preventDefault()
  const files = event.dataTransfer?.files ? Array.from(event.dataTransfer.files) : []
  for (const file of files) {
    if (feedbackImages.value.length >= IMAGE_MAX) break
    if (!file.type.startsWith('image/')) continue
    await addImageFile(file)
  }
}

async function onSubmit() {
  if (!isLoggedIn.value) {
    r.openAuthPage('login')
    return
  }
  const content = feedbackContent.value.trim()
  if (!content) {
    formError.value = '请填写反馈内容'
    return
  }
  submitting.value = true
  formError.value = ''
  const ok = await r.submitUserFeedback({
    type: feedbackType.value,
    page: feedbackPage.value,
    content,
    images: feedbackImages.value.slice()
  })
  submitting.value = false
  if (ok) {
    feedbackContent.value = ''
    feedbackImages.value = []
    feedbackType.value = 'suggest'
    feedbackPage.value = '其它'
  } else {
    formError.value = errorMsg.value || '提交失败，请稍后重试'
  }
}

function onDocPaste(event: ClipboardEvent) {
  if (!uploadZoneRef.value) return
  const active = document.activeElement
  if (active instanceof HTMLTextAreaElement || active instanceof HTMLInputElement) return
  void onPaste(event)
}

onMounted(() => {
  document.title = '我要反馈_片刻帮'
  document.documentElement.classList.add('pk-feedback-route')
  document.body.classList.add('pk-feedback-route')
  document.addEventListener('paste', onDocPaste)
})

onUnmounted(() => {
  document.title = '片刻帮'
  document.documentElement.classList.remove('pk-feedback-route')
  document.body.classList.remove('pk-feedback-route')
  document.removeEventListener('paste', onDocPaste)
})
</script>

<template>
  <div class="pk-feedback-standalone">
    <header class="pk-feedback-topbar">
      <div class="pk-feedback-topbar-inner">
        <button type="button" class="pk-feedback-brand" @click="r.goHall()">
          <img class="pk-feedback-brand-mark" :src="BRAND_LOGO_URL" alt="" />
          <img class="pk-feedback-brand-wordmark" :src="BRAND_WORDMARK_URL" alt="片刻帮" />
        </button>

        <div class="pk-feedback-topbar-right">
          <button type="button" class="pk-feedback-home-link" @click="r.goHall()">返回首页</button>
          <button
            v-if="isLoggedIn"
            type="button"
            class="pk-feedback-user"
            @click="r.goProfile()"
          >
            <img
              v-if="myAvatarUrl"
              :src="r.normalizeImageUrl(myAvatarUrl)"
              alt=""
              class="pk-feedback-user-avatar"
            />
            <span v-else class="pk-feedback-user-avatar pk-feedback-user-avatar-fallback">
              {{ (myName || 'U').slice(0, 1).toUpperCase() }}
            </span>
            <span class="pk-feedback-user-name">{{ myName || '用户' }}</span>
          </button>
          <button v-else type="button" class="pk-feedback-login-link" @click="r.openAuthPage('login')">
            登录
          </button>
        </div>
      </div>
    </header>

    <main class="pk-feedback-body" aria-labelledby="pk-feedback-title">
      <article class="pk-feedback-shell">
        <h1 id="pk-feedback-title" class="pk-feedback-title">片刻帮用户反馈中心</h1>

        <section class="pk-feedback-section">
          <h2 class="pk-feedback-section-title">
            <span class="pk-feedback-num">1.</span>
            请输入您对
            <em class="pk-feedback-highlight">片刻帮</em>
            的反馈或建议吧~
            <span class="pk-feedback-required">*</span>
          </h2>

          <div class="pk-feedback-type-row" role="tablist" aria-label="反馈类型">
            <button
              type="button"
              class="pk-feedback-type-btn"
              :class="{ active: feedbackType === 'suggest' }"
              role="tab"
              :aria-selected="feedbackType === 'suggest'"
              @click="selectType('suggest')"
            >
              我要提功能/体验建议
            </button>
            <button
              type="button"
              class="pk-feedback-type-btn"
              :class="{ active: feedbackType === 'bug' }"
              role="tab"
              :aria-selected="feedbackType === 'bug'"
              @click="selectType('bug')"
            >
              我要反馈故障
            </button>
          </div>

          <div class="pk-feedback-textarea-wrap">
            <textarea
              v-model="feedbackContent"
              class="pk-feedback-textarea"
              :maxlength="FEEDBACK_MAX"
              :placeholder="
                feedbackType === 'bug'
                  ? '请描述片刻帮使用过程中遇到的故障，如某个功能无法使用、页面报错、流程受阻、页面卡顿等'
                  : '请描述片刻帮使用过程中遇到的问题，如某个功能无法使用/不好用、流程受阻、页面卡顿，或其他产品建议'
              "
              aria-label="反馈内容"
            />
            <span class="pk-feedback-counter">{{ contentLength }} / {{ FEEDBACK_MAX }}</span>
          </div>
        </section>

        <section class="pk-feedback-section">
          <h2 class="pk-feedback-section-title">
            <span class="pk-feedback-num">2.</span>
            问题页面
          </h2>
          <div class="pk-feedback-page-row" role="group" aria-label="问题页面">
            <button
              v-for="page in PAGE_OPTIONS"
              :key="page"
              type="button"
              class="pk-feedback-page-btn"
              :class="{ active: feedbackPage === page }"
              @click="selectPage(page)"
            >
              {{ page }}
            </button>
          </div>
        </section>

        <section class="pk-feedback-section">
          <h2 class="pk-feedback-section-title">
            <span class="pk-feedback-num">3.</span>
            上传有效截图，可以让问题
            <em class="pk-feedback-highlight">更快被发现</em>
            哦！（最多可以上传 {{ IMAGE_MAX }} 张截图）
          </h2>

          <div class="pk-feedback-upload-grid">
            <div
              v-for="(img, idx) in feedbackImages"
              :key="`fb-img-${idx}`"
              class="pk-feedback-thumb"
            >
              <img :src="img" alt="" />
              <button type="button" class="pk-feedback-thumb-remove" aria-label="删除截图" @click="removeImage(idx)">
                ×
              </button>
            </div>

            <div
              v-if="feedbackImages.length < IMAGE_MAX"
              ref="uploadZoneRef"
              class="pk-feedback-upload-zone"
              tabindex="0"
              role="button"
              aria-label="上传截图"
              @click="openFilePicker"
              @dragover="onDragOver"
              @drop="onDrop"
              @keydown.enter.prevent="openFilePicker"
              @keydown.space.prevent="openFilePicker"
            >
              <span class="pk-feedback-upload-icon" aria-hidden="true">
                <svg viewBox="0 0 48 48" fill="none">
                  <rect x="6" y="10" width="36" height="28" rx="3" stroke="currentColor" stroke-width="2" />
                  <circle cx="17" cy="21" r="3.5" stroke="currentColor" stroke-width="2" />
                  <path d="M6 32l10-8 8 6 8-10 10 12" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
                  <path d="M30 14h8v8" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
                </svg>
              </span>
              <p v-if="uploadBusy" class="pk-feedback-upload-busy">处理中…</p>
            </div>
          </div>
          <p class="pk-feedback-upload-hint">可粘贴、拖拽或点击上传 · JPG / PNG / GIF 等</p>

          <input
            ref="fileInputRef"
            type="file"
            accept="image/jpeg,image/jpg,image/png,image/apng,image/gif,image/bmp,image/webp"
            multiple
            hidden
            @change="onFileChange"
          />
        </section>

        <p v-if="profileNotice" class="pk-feedback-success" role="status">{{ profileNotice }}</p>
        <p v-if="formError" class="pk-feedback-error">{{ formError }}</p>

        <div class="pk-feedback-actions">
          <button type="button" class="pk-feedback-submit" :disabled="!canSubmit" @click="onSubmit">
            {{ submitting ? '提交中…' : '提交反馈' }}
          </button>
        </div>
      </article>
    </main>
  </div>
</template>
