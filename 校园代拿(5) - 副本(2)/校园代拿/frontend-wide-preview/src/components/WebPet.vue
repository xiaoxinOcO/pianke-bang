<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, toRef, watch } from 'vue'
import { useShellStore } from '../stores/useShellStore'

type PetAnim = {
  files: string[]
  fps?: number
  loop?: boolean
}

type PetConfig = {
  name?: string
  spritesDir?: string
  spriteSize?: number
  displayScale?: number
  walkSpeed?: number
  runSpeed?: number
  /** 贴边探头时直接待机（无爪抓过渡），适合没有对应贴边素材的猫 */
  peekImmediate?: boolean
  /** 贴边时露出占精灵高度的比例（底部对齐素材需更大，否则只露空白） */
  peekVisible?: number
  animations: Record<string, PetAnim>
}

type PetOption = { id: string; base: string; label: string }

const PET_OPTIONS: PetOption[] = [
  { id: 'classic-neko', base: '/web-pet/classic-neko', label: 'Neko' },
  { id: 'pet-cat-1', base: '/web-pet/pet-cat-1', label: '棕猫' },
  { id: 'cozy-cat', base: '/web-pet/cozy-cat', label: '暖喵' },
  { id: 'bunny', base: '/web-pet/Bunny', label: '灰兔' }
]
const PET_STORAGE_KEY = 'web-pet-active-id'

const petId = ref(PET_OPTIONS[0].id)
const PET_BASE = computed(() => PET_OPTIONS.find((p) => p.id === petId.value)?.base || PET_OPTIONS[0].base)

const MARGIN = 12
const SPEED = 42
const EDGE_ZONE = 56
const DRAG_THRESHOLD = 5
/** 探头时露出约占精灵高度的比例 */
const PEEK_VISIBLE = 0.45

type EdgeSide = 'bottom'

const r = useShellStore()
const isLoggedIn = toRef(r, 'isLoggedIn') as { value: boolean }
const currentView = toRef(r, 'currentView') as { value: string }
const showAuthPage = toRef(r, 'showAuthPage') as { value: boolean }
const supportChatSending = toRef(r, 'supportChatSending') as { value: boolean }
const supportChatMessages = toRef(r, 'supportChatMessages') as {
  value: Array<{ role: string; content: string }>
}
const sendSupportChatMessage = r.sendSupportChatMessage as (message: string) => Promise<void>
const openSupportChat = r.openSupportChat as () => void
const openAuthPage = r.openAuthPage as (mode: 'login' | 'register') => void

const visible = computed(() => {
  if (showAuthPage.value) return false
  const v = currentView.value
  return v !== 'admin' && v !== 'chat' && v !== 'feedback'
})

const pet = ref<PetConfig | null>(null)
const animName = ref('idle')
const frameIndex = ref(0)
const x = ref(0)
const y = ref(0)
const chatOpen = ref(false)
const draft = ref('')
const bubbleEl = ref<HTMLElement | null>(null)
const listEl = ref<HTMLElement | null>(null)
const viewportW = ref(typeof window !== 'undefined' ? window.innerWidth : 1200)
const peekEdge = ref<EdgeSide | null>(null)

let raf = 0
let lastTs = 0
let frameAcc = 0
let walkTimer = 0
let vx = 0
let vy = 0
let mode: 'idle' | 'walk' | 'run' | 'emote' | 'happy' | 'thinking' | 'drag' | 'peek' = 'idle'
let happyUntil = 0
let peekSettleAt = 0
let moveSpeed = SPEED

const dragging = ref(false)
let dragActive = false
let dragMoved = false
let dragPointerId: number | null = null
let dragOffsetX = 0
let dragOffsetY = 0
let dragStartClientX = 0
let dragStartClientY = 0

const scale = computed(() => Math.max(2, Number(pet.value?.displayScale || 2)))
const spriteSize = computed(() => Math.max(24, Number(pet.value?.spriteSize || 32)))
const displayPx = computed(() => spriteSize.value * scale.value)

const chatLeft = computed(() => {
  const preferred = x.value - 210 + displayPx.value / 2
  return Math.min(Math.max(8, preferred), Math.max(8, viewportW.value - 292))
})
const chatTop = computed(() => Math.max(8, y.value - 268))

const currentAnim = computed(() => {
  const animations = pet.value?.animations || {}
  return animations[animName.value] || animations.idle || { files: ['awake.png'], fps: 2, loop: true }
})

const frameSrc = computed(() => {
  const files = currentAnim.value.files || []
  const base = PET_BASE.value
  if (!files.length) return `${base}/sprites/awake.png`
  const file = files[Math.min(frameIndex.value, files.length - 1)]
  return `${base}/sprites/${file}`
})

const recentMessages = computed(() => {
  const list = supportChatMessages.value || []
  return list.filter((m) => m.role === 'user' || m.role === 'assistant' || m.role === 'human').slice(-8)
})

function setAnim(name: string) {
  if (!pet.value?.animations?.[name] && name !== 'idle') {
    animName.value = 'idle'
  } else {
    animName.value = name
  }
  frameIndex.value = 0
  frameAcc = 0
}

function hasAnim(name: string) {
  return !!pet.value?.animations?.[name]?.files?.length
}

function petWalkSpeed() {
  return Math.max(12, Number(pet.value?.walkSpeed || SPEED))
}

function petRunSpeed() {
  return Math.max(petWalkSpeed(), Number(pet.value?.runSpeed || SPEED * 2.15))
}

function pickWalkDirection() {
  const dirs = [
    { name: 'walk_right', vx: 1, vy: 0 },
    { name: 'walk_left', vx: -1, vy: 0 },
    { name: 'walk_up', vx: 0, vy: -1 },
    { name: 'walk_down', vx: 0, vy: 1 },
    { name: 'walk_up_right', vx: 0.72, vy: -0.72 },
    { name: 'walk_up_left', vx: -0.72, vy: -0.72 },
    { name: 'walk_down_right', vx: 0.72, vy: 0.72 },
    { name: 'walk_down_left', vx: -0.72, vy: 0.72 }
  ]
  const pick = dirs[Math.floor(Math.random() * dirs.length)]
  vx = pick.vx
  vy = pick.vy
  moveSpeed = petWalkSpeed()
  setAnim(pick.name)
  mode = 'walk'
  walkTimer = 1.6 + Math.random() * 2.4
}

function pickRunDirection() {
  const left = Math.random() < 0.5
  vx = left ? -1 : 1
  vy = 0
  moveSpeed = petRunSpeed()
  setAnim(left ? (hasAnim('run_left') ? 'run_left' : 'walk_left') : hasAnim('run_right') ? 'run_right' : 'walk_right')
  mode = 'run'
  walkTimer = 0.9 + Math.random() * 0.55
}

/** 随机播素材里的其它动作：坐 / 睡 / 舔 / 伸懒腰 / 挠痒 / 喵 */
function pickEmote() {
  const once = ['meow', 'yawn', 'happy'].filter(hasAnim)
  // 舔 / 伸懒腰 / 挠痒：持续循环，不要播完卡在最后一帧
  const hold = ['sit', 'sleep', 'laying', 'itch', 'lick', 'stretch'].filter(hasAnim)
  const roll = Math.random()
  vx = 0
  vy = 0
  moveSpeed = petWalkSpeed()

  if (roll < 0.35 && once.length) {
    const name = once[Math.floor(Math.random() * once.length)]
    mode = 'emote'
    setAnim(name)
    walkTimer = 2.8 + Math.random() * 1.5
    return
  }
  if (hold.length) {
    const name = hold[Math.floor(Math.random() * hold.length)]
    mode = 'emote'
    if (name === 'laying' && hasAnim('laying_down')) setAnim('laying_down')
    else setAnim(name)
    if (name === 'stretch') walkTimer = 1.7 + Math.random() * 0.4
    else if (name === 'lick') walkTimer = 3.5 + Math.random() * 1.5
    else if (name === 'itch') walkTimer = 3.2 + Math.random() * 2.2
    else if (name === 'sleep' || name === 'laying') walkTimer = 4 + Math.random() * 4
    else walkTimer = 2.2 + Math.random() * 2.8
    return
  }
  goIdle()
}

function pickNextBehavior() {
  const r = Math.random()
  if (r < 0.32) pickWalkDirection()
  else if (r < 0.48 && (hasAnim('run_right') || hasAnim('run_left'))) pickRunDirection()
  else if (r < 0.88) pickEmote()
  else goIdle()
}

function goIdle() {
  vx = 0
  vy = 0
  moveSpeed = petWalkSpeed()
  peekEdge.value = null
  mode = 'idle'
  setAnim(hasAnim('idle') ? 'idle' : 'awaken')
  walkTimer = 1.4 + Math.random() * 2.4
}

function finishNonLoopAnim() {
  if (mode === 'emote') {
    // 趴下过渡播完后切到趴着保持
    if (animName.value === 'laying_down' && hasAnim('laying')) {
      setAnim('laying')
      return
    }
    // 舔 / 伸懒腰等：停在最后一帧，等 walkTimer 到点再切，不要播完立刻换动作
    return
  }
  if (mode === 'happy' && performance.now() >= happyUntil) {
    if (peekEdge.value) {
      mode = 'peek'
      setAnim('peek_idle')
    } else {
      goIdle()
    }
  }
}

async function loadPet(id: string) {
  const opt = PET_OPTIONS.find((p) => p.id === id) || PET_OPTIONS[0]
  petId.value = opt.id
  try {
    localStorage.setItem(PET_STORAGE_KEY, opt.id)
  } catch {
    /* ignore */
  }
  try {
    const res = await fetch(`${opt.base}/pet.json`)
    if (!res.ok) throw new Error(`pet.json ${res.status}`)
    const data = (await res.json()) as PetConfig
    if (!data?.animations) throw new Error('invalid pet.json')
    pet.value = data
  } catch {
    pet.value = {
      name: opt.label,
      spriteSize: 32,
      displayScale: 2,
      animations: {
        idle: { files: ['awake.png'], fps: 2, loop: true },
        walk_right: { files: ['right1.png', 'right2.png'], fps: 4, loop: true },
        happy: { files: ['scratch1.png', 'scratch2.png'], fps: 8, loop: false }
      }
    }
  }
  goIdle()
  clampToViewport()
}

async function cyclePet() {
  if (PET_OPTIONS.length <= 1) {
    // 暂无一只猫，保留入口；之后加新猫即可轮换
    mode = 'happy'
    happyUntil = performance.now() + 700
    setAnim('happy')
    return
  }
  const idx = PET_OPTIONS.findIndex((p) => p.id === petId.value)
  const next = PET_OPTIONS[(idx + 1) % PET_OPTIONS.length]
  await loadPet(next.id)
  mode = 'happy'
  happyUntil = performance.now() + 700
  setAnim('happy')
}

function bounds() {
  const w = window.innerWidth
  const h = window.innerHeight
  const size = displayPx.value
  return {
    w,
    h,
    size,
    maxX: Math.max(MARGIN, w - size - MARGIN),
    maxY: Math.max(MARGIN, h - size - MARGIN - 24)
  }
}

function clampAxis(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function clampToViewport() {
  const b = bounds()
  x.value = clampAxis(x.value, MARGIN, b.maxX)
  y.value = clampAxis(y.value, MARGIN, b.maxY)
}

/** 拖动时允许贴近下边，方便松手吸附探头；其他边保持在视口内 */
function softClampWhileDrag() {
  const b = bounds()
  const over = b.size * 0.35
  x.value = clampAxis(x.value, MARGIN, b.maxX)
  y.value = clampAxis(y.value, MARGIN, b.h - b.size + over)
}

/** 仅下边界可探头待机 */
function detectEdgeNear(): EdgeSide | null {
  const b = bounds()
  const bottomDist = b.h - (y.value + b.size)
  if (bottomDist > EDGE_ZONE) return null
  return 'bottom'
}

function dockToPeek(_edge: EdgeSide = 'bottom') {
  const b = bounds()
  const configured = Number(pet.value?.peekVisible)
  const ratio =
    Number.isFinite(configured) && configured > 0 ? configured : PEEK_VISIBLE
  const show = b.size * Math.min(0.98, Math.max(0.35, ratio))
  const midX = clampAxis(x.value, MARGIN, b.maxX)
  x.value = midX
  y.value = b.h - show

  vx = 0
  vy = 0
  peekEdge.value = 'bottom'
  mode = 'peek'
  // 橙猫等无贴边爪抓素材：直接正面待机，不要先播过渡再切
  if (pet.value?.peekImmediate && hasAnim('peek_idle')) {
    setAnim('peek_idle')
    peekSettleAt = 0
  } else {
    setAnim(hasAnim('scratch_down') ? 'scratch_down' : 'peek_idle')
    peekSettleAt = performance.now() + 1100
  }
  walkTimer = 9999
}

function placeInitial() {
  const size = displayPx.value
  x.value = Math.max(MARGIN, window.innerWidth - size - 110)
  y.value = Math.max(MARGIN, window.innerHeight - size - 36)
}

function tick(ts: number) {
  if (!lastTs) lastTs = ts
  const dt = Math.min(0.05, (ts - lastTs) / 1000)
  lastTs = ts

  const anim = currentAnim.value
  const fps = Math.max(1, Number(anim.fps || 4))
  frameAcc += dt
  const frameDur = 1 / fps
  while (frameAcc >= frameDur) {
    frameAcc -= frameDur
    const len = Math.max(1, anim.files?.length || 1)
    if (anim.loop === false && frameIndex.value >= len - 1) {
      finishNonLoopAnim()
      break
    }
    frameIndex.value = (frameIndex.value + 1) % len
  }

  if (mode === 'peek' && peekSettleAt && performance.now() >= peekSettleAt) {
    peekSettleAt = 0
    setAnim('peek_idle')
  }

  if (!chatOpen.value && !dragActive) {
    if (mode === 'peek') {
      // 贴边待机：不再乱跑
    } else if (mode === 'happy') {
      if (performance.now() >= happyUntil) {
        if (peekEdge.value) {
          mode = 'peek'
          setAnim('peek_idle')
        } else {
          goIdle()
        }
      }
    } else if (mode === 'emote') {
      walkTimer -= dt
      const looping = currentAnim.value.loop !== false
      if (looping && walkTimer <= 0) goIdle()
      else if (!looping && walkTimer <= 0) goIdle()
    } else if (mode === 'thinking') {
      // 发消息中：保持思考姿势
    } else {
      walkTimer -= dt
      if (walkTimer <= 0) {
        if (mode === 'walk' || mode === 'run') goIdle()
        else pickNextBehavior()
      }
      if (mode === 'walk' || mode === 'run') {
        x.value += vx * moveSpeed * dt
        y.value += vy * moveSpeed * dt
        const b = bounds()
        if (x.value <= MARGIN || x.value >= b.maxX) vx *= -1
        if (y.value <= MARGIN || y.value >= b.maxY) vy *= -1
        x.value = clampAxis(x.value, MARGIN, b.maxX)
        y.value = clampAxis(y.value, MARGIN, b.maxY)
        if (mode === 'run') {
          if (vx < 0) setAnimKeepFrame(hasAnim('run_left') ? 'run_left' : 'walk_left')
          else setAnimKeepFrame(hasAnim('run_right') ? 'run_right' : 'walk_right')
        } else if (vx > 0.4 && Math.abs(vy) < 0.3) setAnimKeepFrame('walk_right')
        else if (vx < -0.4 && Math.abs(vy) < 0.3) setAnimKeepFrame('walk_left')
        else if (vy < -0.4 && Math.abs(vx) < 0.3) setAnimKeepFrame('walk_up')
        else if (vy > 0.4 && Math.abs(vx) < 0.3) setAnimKeepFrame('walk_down')
      }
    }
  }

  if (!visible.value) {
    raf = 0
    lastTs = 0
    return
  }
  raf = requestAnimationFrame(tick)
}

function setAnimKeepFrame(name: string) {
  if (animName.value === name) return
  const prev = frameIndex.value
  setAnim(name)
  const len = currentAnim.value.files?.length || 1
  frameIndex.value = prev % len
}

function toggleChat() {
  mode = 'happy'
  happyUntil = performance.now() + 900
  setAnim('happy')
  chatOpen.value = !chatOpen.value
  if (chatOpen.value) {
    void nextTick(() => {
      scrollChat()
      bubbleEl.value?.querySelector('input')?.focus()
    })
  } else if (peekEdge.value) {
    mode = 'peek'
    setAnim('peek_idle')
  } else {
    goIdle()
  }
}

function onPetPointerDown(e: PointerEvent) {
  if (e.button !== 0 && e.pointerType === 'mouse') return
  e.preventDefault()
  dragPointerId = e.pointerId
  dragMoved = false
  dragActive = false
  dragging.value = false
  dragStartClientX = e.clientX
  dragStartClientY = e.clientY
  dragOffsetX = e.clientX - x.value
  dragOffsetY = e.clientY - y.value
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function onPetPointerMove(e: PointerEvent) {
  if (dragPointerId !== e.pointerId) return
  const dx = e.clientX - dragStartClientX
  const dy = e.clientY - dragStartClientY
  if (!dragActive && Math.hypot(dx, dy) >= DRAG_THRESHOLD) {
    dragActive = true
    dragMoved = true
    dragging.value = true
    vx = 0
    vy = 0
    peekEdge.value = null
    peekSettleAt = 0
    mode = 'drag'
    setAnim('awaken')
  }
  if (!dragActive) return
  x.value = e.clientX - dragOffsetX
  y.value = e.clientY - dragOffsetY
  softClampWhileDrag()
}

function onPetPointerUp(e: PointerEvent) {
  if (dragPointerId !== e.pointerId) return
  try {
    ;(e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId)
  } catch {
    /* ignore */
  }
  const wasDrag = dragMoved
  dragPointerId = null
  dragActive = false
  dragging.value = false
  if (wasDrag) {
    const edge = detectEdgeNear()
    if (edge) {
      dockToPeek(edge)
      return
    }
    clampToViewport()
    mode = 'happy'
    happyUntil = performance.now() + 700
    setAnim('happy')
    walkTimer = 1.5
    return
  }
  toggleChat()
}

function onPetPointerCancel(e: PointerEvent) {
  if (dragPointerId !== e.pointerId) return
  dragPointerId = null
  dragActive = false
  dragging.value = false
  if (peekEdge.value) {
    mode = 'peek'
    setAnim('peek_idle')
  } else {
    goIdle()
  }
}

function scrollChat() {
  const el = listEl.value
  if (el) el.scrollTop = el.scrollHeight
}

async function onSend() {
  const text = draft.value.trim()
  if (!text || supportChatSending.value) return
  if (!isLoggedIn.value) {
    openAuthPage('login')
    return
  }
  draft.value = ''
  setAnim('thinking')
  mode = 'thinking'
  try {
    await sendSupportChatMessage(text)
  } finally {
    if (peekEdge.value) {
      mode = 'peek'
      setAnim('peek_idle')
    } else {
      mode = 'happy'
      happyUntil = performance.now() + 1000
      setAnim('happy')
    }
    await nextTick()
    scrollChat()
  }
}

function openFullSupport() {
  if (!isLoggedIn.value) {
    openAuthPage('login')
    return
  }
  openSupportChat()
}

function onResize() {
  viewportW.value = window.innerWidth
  if (peekEdge.value) {
    dockToPeek(peekEdge.value)
  } else {
    clampToViewport()
  }
}

watch(visible, (ok) => {
  if (!ok) {
    chatOpen.value = false
    if (raf) {
      cancelAnimationFrame(raf)
      raf = 0
      lastTs = 0
    }
    return
  }
  if (!raf && pet.value) {
    raf = requestAnimationFrame(tick)
  }
})

watch(
  () => supportChatMessages.value.length,
  async () => {
    if (chatOpen.value) {
      await nextTick()
      scrollChat()
    }
  }
)

onMounted(async () => {
  let saved = PET_OPTIONS[0].id
  try {
    const fromStore = localStorage.getItem(PET_STORAGE_KEY)
    if (fromStore && PET_OPTIONS.some((p) => p.id === fromStore)) saved = fromStore
  } catch {
    /* ignore */
  }
  await loadPet(saved)
  placeInitial()
  goIdle()
  window.addEventListener('resize', onResize)
  if (visible.value) {
    raf = requestAnimationFrame(tick)
  }
})

onUnmounted(() => {
  cancelAnimationFrame(raf)
  raf = 0
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <div v-if="visible && pet" class="web-pet-root" aria-live="polite">
    <div
      v-if="chatOpen"
      ref="bubbleEl"
      class="web-pet-chat"
      :style="{ left: `${chatLeft}px`, top: `${chatTop}px` }"
      @pointerdown.stop
    >
      <div class="web-pet-chat-head">
        <strong>{{ pet.name || '桌宠' }} · 智能客服</strong>
        <div class="web-pet-chat-actions">
          <button type="button" class="ghost" @click="cyclePet">换宠</button>
          <button type="button" class="ghost" @click="openFullSupport">展开</button>
          <button type="button" class="ghost" @click="chatOpen = false; goIdle()">收起</button>
        </div>
      </div>
      <div ref="listEl" class="web-pet-chat-list">
        <p v-if="!recentMessages.length" class="hint">点我聊天，或问钱包 / 任务问题～</p>
        <div
          v-for="(m, i) in recentMessages"
          :key="`pet-msg-${i}-${m.role}`"
          class="msg"
          :class="m.role"
        >
          {{ m.content }}
        </div>
      </div>
      <form class="web-pet-chat-form" @submit.prevent="onSend">
        <input
          v-model="draft"
          type="text"
          maxlength="200"
          :placeholder="isLoggedIn ? `跟 ${pet.name || '桌宠'} 说点什么…` : '登录后可对话'"
          :disabled="supportChatSending"
        />
        <button type="submit" :disabled="supportChatSending || !draft.trim()">发送</button>
      </form>
    </div>

    <button
      type="button"
      class="web-pet-sprite"
      :class="{ 'is-dragging': dragging, 'is-peek': !!peekEdge }"
      :style="{
        left: `${x}px`,
        top: `${y}px`,
        width: `${displayPx}px`,
        height: `${displayPx}px`,
      }"
      :title="`${pet.name || '桌宠'}（拖到下边可探头 / 点击对话；气泡内可换宠）`"
      @pointerdown="onPetPointerDown"
      @pointermove="onPetPointerMove"
      @pointerup="onPetPointerUp"
      @pointercancel="onPetPointerCancel"
    >
      <img :src="frameSrc" alt="" draggable="false" />
    </button>
  </div>
</template>

<style scoped>
.web-pet-root {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 1200;
}
.web-pet-sprite {
  position: fixed;
  pointer-events: auto;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: grab;
  touch-action: none;
  user-select: none;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  filter: drop-shadow(0 6px 10px rgba(15, 23, 42, 0.18));
}
.web-pet-sprite.is-dragging {
  cursor: grabbing;
  filter: drop-shadow(0 10px 16px rgba(15, 23, 42, 0.28));
  z-index: 1210;
}
.web-pet-sprite.is-peek {
  filter: drop-shadow(0 4px 8px rgba(15, 23, 42, 0.2));
}
.web-pet-sprite img {
  width: 100%;
  height: 100%;
  display: block;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  pointer-events: none;
  user-select: none;
}
.web-pet-chat {
  position: fixed;
  width: 280px;
  pointer-events: auto;
  border-radius: 14px;
  background: rgba(255, 252, 248, 0.96);
  border: 1px solid rgba(92, 61, 46, 0.12);
  box-shadow: 0 16px 40px rgba(47, 42, 36, 0.16);
  overflow: hidden;
  backdrop-filter: blur(8px);
}
.web-pet-chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: #f7f3ee;
}
.web-pet-chat-head strong {
  font-size: 13px;
  color: #2a2621;
}
.web-pet-chat-actions {
  display: flex;
  gap: 4px;
}
.web-pet-chat-actions .ghost {
  border: 0;
  background: transparent;
  color: #6b645c;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 6px;
}
.web-pet-chat-actions .ghost:hover {
  background: rgba(0, 0, 0, 0.05);
  color: #2a2621;
}
.web-pet-chat-list {
  height: 168px;
  overflow: auto;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #fff;
}
.web-pet-chat-list .hint {
  margin: 0;
  font-size: 12px;
  color: #9a9288;
}
.web-pet-chat-list .msg {
  max-width: 92%;
  padding: 7px 10px;
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}
.web-pet-chat-list .msg.user {
  align-self: flex-end;
  background: #5c3d2e;
  color: #fff;
}
.web-pet-chat-list .msg.assistant,
.web-pet-chat-list .msg.human {
  align-self: flex-start;
  background: #f3efe8;
  color: #2f2a24;
}
.web-pet-chat-form {
  display: flex;
  gap: 8px;
  padding: 10px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  background: #faf8f5;
}
.web-pet-chat-form input {
  flex: 1;
  min-width: 0;
  height: 34px;
  border-radius: 9px;
  border: 1px solid rgba(0, 0, 0, 0.1);
  padding: 0 10px;
  font: inherit;
  font-size: 13px;
  background: #fff;
}
.web-pet-chat-form button {
  height: 34px;
  padding: 0 12px;
  border: 0;
  border-radius: 9px;
  background: #5c3d2e;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.web-pet-chat-form button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
@media (max-width: 640px) {
  .web-pet-chat {
    width: min(280px, calc(100vw - 16px));
  }
}
</style>
