<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, toRef, watch, type Ref } from 'vue'
import { useAuthStore } from '../stores/useAuthStore'
import { usePiankeLogin } from '../composables/usePiankeLogin'

/** 登录页样式按需加载，不进首屏主包 */
void import('../styles/piankebang-login.css')

const {
  mode,
  passwordVisible,
  mascotMood,
  mascotFlashClass,
  mascotSay,
  speechUpdating,
  showMascotSweat,
  rememberMe,
  phoneValid,
  strengthLevel,
  showStrengthBar,
  successVisible,
  cardExit,
  soundEnabled,
  account,
  nickname,
  password,
  confirm,
  progressPercent,
  progressLabel,
  trustOn,
  handoffReady,
  handoffEnter,
  authLoading,
  heading,
  subheading,
  phoneLabel,
  phonePlaceholder,
  phoneInputType,
  submitLabel,
  successTitle,
  formError,
  fieldError,
  fieldErrorFor,
  isFieldInvalid,
  clearInvalidField,
  particleCanvas,
  mascotEl,
  closeAuthPage,
  switchMode,
  handleSubmit,
  togglePassword,
  onMascotTap,
  onPhoneInput,
  onNicknameInput,
  updateProgress,
  updateStrength,
  syncMoodFromFocus,
  toggleSound,
  authFormEl,
  pupilLeftStyle,
  pupilRightStyle,
  loginSceneEl,
  handlePointerMove
} = usePiankeLogin()

const r = useAuthStore()
const authForm = toRef(r, 'authForm') as Ref<Record<string, string>>
const smsCountdown = toRef(r, 'smsCountdown') as Ref<number>
const imageCaptchaInput = toRef(r, 'imageCaptchaInput') as Ref<string>
const registerSmsHint = toRef(r, 'registerSmsHint') as Ref<string>
const registerSmsHintError = toRef(r, 'registerSmsHintError') as Ref<boolean>
const registerSmsChannel = toRef(r, 'registerSmsChannel') as Ref<'local' | 'aliyun' | null>
const smsSending = toRef(r, 'smsSending') as Ref<boolean>
const sendSmsCode = r.sendSmsCode as (canvas?: HTMLCanvasElement | null) => void | Promise<void>
const refreshImageCaptcha = r.refreshImageCaptcha as (canvas?: HTMLCanvasElement | null) => void
const refreshForgotCaptcha = r.refreshForgotCaptcha as (canvas?: HTMLCanvasElement | null) => void

const smsAccountPickerOpen = toRef(r, 'smsAccountPickerOpen') as Ref<boolean>
const smsPickCandidates = toRef(r, 'smsPickCandidates') as Ref<Array<{ userId: number; username: string }>>
const confirmSmsPickLogin = r.confirmSmsPickLogin as (userId: number) => void | Promise<void>
const closeSmsAccountPicker = r.closeSmsAccountPicker as () => void

const forgotPasswordOpen = toRef(r, 'forgotPasswordOpen') as Ref<boolean>
const forgotPasswordForm = toRef(r, 'forgotPasswordForm') as Ref<Record<string, string>>
const forgotImageCaptchaInput = toRef(r, 'forgotImageCaptchaInput') as Ref<string>
const forgotPwdSmsCountdown = toRef(r, 'forgotPwdSmsCountdown') as Ref<number>
const forgotPwdSubmitting = toRef(r, 'forgotPwdSubmitting') as Ref<boolean>
const forgotPwdModalMsg = toRef(r, 'forgotPwdModalMsg') as Ref<string>
const forgotPwdModalError = toRef(r, 'forgotPwdModalError') as Ref<string>
const forgotPwdCandidates = toRef(r, 'forgotPwdCandidates') as Ref<Array<{ userId: number; username: string }>>
const forgotPwdSelectedUserId = toRef(r, 'forgotPwdSelectedUserId') as Ref<number | null>
const forgotPwdNeedChooseAccount = toRef(r, 'forgotPwdNeedChooseAccount') as Ref<boolean>
const forgotPwdAccountPickerOpen = toRef(r, 'forgotPwdAccountPickerOpen') as Ref<boolean>
const forgotPwdSmsVerified = toRef(r, 'forgotPwdSmsVerified') as Ref<boolean>
const forgotPwdVerifying = toRef(r, 'forgotPwdVerifying') as Ref<boolean>
const forgotPwdSelectedUsername = toRef(r, 'forgotPwdSelectedUsername') as Ref<string>
const openForgotPasswordModal = r.openForgotPasswordModal as () => void
const closeForgotPasswordModal = r.closeForgotPasswordModal as () => void
const sendForgotPasswordSmsCode = r.sendForgotPasswordSmsCode as (
  canvas?: HTMLCanvasElement | null
) => void | Promise<void>
const scheduleForgotPwdSmsVerify = r.scheduleForgotPwdSmsVerify as () => void
const selectForgotPwdAccount = r.selectForgotPwdAccount as (userId: number, username: string) => void
const closeForgotPwdAccountPicker = r.closeForgotPwdAccountPicker as () => void
const submitForgotPasswordReset = r.submitForgotPasswordReset as () => void | Promise<void>

const registerCaptchaCanvasRef = ref<HTMLCanvasElement | null>(null)
const forgotCaptchaCanvasLocalRef = ref<HTMLCanvasElement | null>(null)

type LegalDocKey = 'terms' | 'privacy'
const legalDoc = ref<LegalDocKey | null>(null)

const LEGAL_DOCS: Record<
  LegalDocKey,
  { title: string; updated: string; paragraphs: string[] }
> = {
  terms: {
    title: '用户协议',
    updated: '2026-08-01',
    paragraphs: [
      '欢迎使用「片刻帮」校园互助平台。注册、登录或使用本服务，即表示你已阅读、理解并同意本协议全部内容。若不同意，请停止使用。',
      '一、服务说明：平台提供校园代取快递、代买、代打印、排队等互助任务的信息发布、撮合、订单管理与沟通工具，不直接从事线下配送，亦不对互助双方的线下行为作担保。',
      '二、账号注册：你应使用真实、有效信息注册，妥善保管账号与密码，不得将账号出借、转让或与他人共用。因个人保管不善导致的损失，由账号使用人自行承担。',
      '三、发布规则：发布任务时请如实填写取送地点、时限、赏金及必要说明；可按页面提示选择加急等附加服务。禁止发布违法违规、危险物品、虚假信息，或与校园互助无关的内容。',
      '四、接单与履约：接单前请确认地点、时限与赏金；接单后应及时与对方沟通并按约定完成。取件码、手机号等隐私信息仅可用于完成本单，不得外传或用于其他用途。',
      '五、取消与争议：未接单前可按规则取消；已接单后应优先协商。因超时、虚假完成、恶意取消等情形，平台可依据规则调整订单状态、信誉或限制部分功能。',
      '六、赏金与钱包：赏金预付、退款、提现、手续费等以页面展示及钱包流水为准。请勿进行套现、刷单或其他扰乱交易秩序的行为。',
      '七、沟通与评价：请文明使用站内私聊与客服；不得辱骂、骚扰或发送违法内容。订单完成后可进行真实评价，禁止恶意刷评或打击报复。',
      '八、违规处理：对违反本协议或影响平台安全的行为，平台有权采取警告、限制功能、冻结交易、注销账号等措施，并保留依法追究责任的权利。',
      '九、免责声明：因不可抗力、网络故障、第三方服务异常，或用户自身原因导致的服务中断、延误或损失，平台在法律允许范围内不承担责任。',
      '十、协议更新：我们可能根据运营与法规要求更新本协议；重大变更将通过站内提示等方式告知。你继续使用即视为接受更新后的内容。'
    ]
  },
  privacy: {
    title: '隐私政策',
    updated: '2026-08-01',
    paragraphs: [
      '「片刻帮」重视你的个人信息与隐私安全。本政策说明我们如何收集、使用、存储与保护你的信息。使用本服务即表示你理解并同意本政策。',
      '一、收集的信息：账号与手机号、登录凭证；昵称、头像等资料；任务与订单内容（含取送地点、备注）；钱包与交易记录；客服与私聊沟通记录；设备、日志与安全风控所需的技术信息。',
      '二、使用目的：用于注册登录与身份核验、任务匹配与履约、支付结算与提现、客服支持、风险控制、纠纷处理，以及在匿名或聚合后用于改进产品体验。',
      '三、共享与披露：我们不会出售你的个人信息。仅在完成订单所必需时向相对方展示必要信息（如联系方式、取送地点）；在法律法规要求、或保护用户与平台合法权益所必需时，可能依法披露。',
      '四、第三方服务：支付、短信验证、地图或统计分析等能力可能由合作方提供，我们仅在必要范围内向其提供信息，并要求其依法保护。',
      '五、存储与期限：信息保存在实现服务目的所必需的期限内；超出期限或账号注销完成后，我们将删除或匿名化处理，法律法规另有规定的除外。',
      '六、你的权利：可在个人中心查看与更新部分资料；可通过站内智能客服或人工客服申请更正、删除或账号注销，我们将在合理期限内核实并处理。',
      '七、未成年人：本服务面向校园用户。若你为未成年人，请在监护人指导下阅读本政策并使用服务；我们发现未经同意收集未成年人信息时，将尽快删除。',
      '八、安全措施：我们采取合理的技术与管理措施保护数据，但互联网环境无法保证绝对安全。请勿向他人泄露验证码、密码及取件码等敏感信息。',
      '九、政策更新：本政策如有更新，我们将及时公示。你继续使用服务，即表示理解并同意更新后的隐私政策。如有疑问，可通过站内客服联系我们。'
    ]
  }
}

const legalDocView = computed(() => (legalDoc.value ? LEGAL_DOCS[legalDoc.value] : null))

function openLegalDoc(key: LegalDocKey) {
  legalDoc.value = key
}

function closeLegalDoc() {
  legalDoc.value = null
}

const agreeLegal = ref(false)
const agreeHint = ref('')
let agreeHintTimer: number | null = null

const wechatQrOpen = ref(false)
const wechatQrLoading = ref(false)
const wechatQrHint = ref('')
const wechatQrError = ref('')
const wechatQrImageUrl = ref('')
const wechatQrSessionId = ref('')
let wechatPollTimer: number | null = null

const completeLoginWithToken = r.completeLoginWithToken as (token: string) => void | Promise<void>

function stopWechatPoll() {
  if (wechatPollTimer != null) {
    window.clearInterval(wechatPollTimer)
    wechatPollTimer = null
  }
}

function closeWechatQr() {
  stopWechatPoll()
  wechatQrOpen.value = false
  wechatQrLoading.value = false
  wechatQrHint.value = ''
  wechatQrError.value = ''
  wechatQrImageUrl.value = ''
  wechatQrSessionId.value = ''
}

async function pollWechatQrOnce() {
  const sid = wechatQrSessionId.value
  if (!sid) return
  try {
    const { authBase } = await import('../constants/api')
    const res = await fetch(`${authBase}/wechat/web/qr-session/${encodeURIComponent(sid)}`)
    const data = (await res.json().catch(() => ({}))) as {
      status?: string
      message?: string
      token?: string
    }
    if (!res.ok) {
      wechatQrError.value = data.message || '扫码会话失效'
      stopWechatPoll()
      return
    }
    if (data.status === 'SUCCESS' && data.token) {
      const token = data.token
      // 先关二维码层，避免挡住后续「欢迎」成功动画
      closeWechatQr()
      await completeLoginWithToken(token)
      return
    }
    if (data.status === 'NEED_BIND') {
      wechatQrHint.value = data.message || '请在手机上完成手机号绑定'
    } else if (data.status === 'NEED_CHOOSE') {
      wechatQrHint.value = data.message || '请在手机上选择要登录的账号'
    } else if (data.status === 'EXPIRED' || data.status === 'FAILED') {
      wechatQrError.value = data.message || '二维码已失效，请刷新'
      stopWechatPoll()
    }
  } catch {
    wechatQrError.value = '轮询失败，请检查网络后刷新二维码'
  }
}

async function openWechatQr() {
  if (!agreeLegal.value) {
    flashAgreeHint('请先勾选同意用户协议与隐私政策')
    return
  }
  wechatQrOpen.value = true
  wechatQrLoading.value = true
  wechatQrError.value = ''
  wechatQrHint.value = '正在生成二维码…'
  stopWechatPoll()

  const origin = window.location.origin
  const isLocalHost = /^(https?:\/\/)?(localhost|127\.0\.0\.1)(:\d+)?$/i.test(origin.replace(/\/$/, ''))
    || origin.includes('localhost')
    || origin.includes('127.0.0.1')

  if (isLocalHost) {
    wechatQrLoading.value = false
    wechatQrImageUrl.value = ''
    wechatQrSessionId.value = ''
    wechatQrHint.value = '当前是本地开发环境'
    wechatQrError.value =
      '手机扫码打不开电脑上的 localhost。请把前端部署到公网 HTTPS（如正式站）后再扫；或同一 WiFi 下用电脑局域网 IP 访问页面再生成二维码（微信对 HTTP 局域网支持不稳定）。'
    return
  }

  try {
    const { authBase } = await import('../constants/api')
    const res = await fetch(`${authBase}/wechat/web/qr-session`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ frontendBaseUrl: origin })
    })
    const data = (await res.json().catch(() => ({}))) as {
      sessionId?: string
      qrContent?: string
      qrImageBase64?: string
      hint?: string
      message?: string
      mode?: string
    }
    if (!res.ok) {
      wechatQrError.value = data.message || '无法创建扫码会话'
      wechatQrLoading.value = false
      return
    }
    wechatQrSessionId.value = data.sessionId || ''
    if (data.qrImageBase64) {
      wechatQrImageUrl.value = data.qrImageBase64
    } else {
      const content = data.qrContent || ''
      wechatQrImageUrl.value = content
        ? `https://api.qrserver.com/v1/create-qr-code/?size=220x220&data=${encodeURIComponent(content)}`
        : ''
    }
    wechatQrHint.value = data.hint || '扫码后在小程序内点「确认登录」即可'
    wechatQrLoading.value = false
    wechatPollTimer = window.setInterval(() => {
      void pollWechatQrOnce()
    }, 2000)
    void pollWechatQrOnce()
  } catch {
    wechatQrError.value = '创建扫码会话失败'
    wechatQrLoading.value = false
  }
}

watch(wechatQrOpen, (open) => {
  if (!open) stopWechatPoll()
})

onUnmounted(() => {
  stopWechatPoll()
  clearAgreeHint()
})

function clearAgreeHint() {
  agreeHint.value = ''
  if (agreeHintTimer != null) {
    window.clearTimeout(agreeHintTimer)
    agreeHintTimer = null
  }
}

function flashAgreeHint(text: string) {
  clearAgreeHint()
  requestAnimationFrame(() => {
    agreeHint.value = text
    agreeHintTimer = window.setTimeout(() => {
      agreeHint.value = ''
      agreeHintTimer = null
    }, 3000)
  })
}

watch(mode, () => {
  clearAgreeHint()
})

async function onAuthSubmit(e: Event) {
  if (!agreeLegal.value) {
    flashAgreeHint('请先勾选同意用户协议与隐私政策')
    return
  }
  clearAgreeHint()
  await handleSubmit(e)
}

function paintRegisterCaptcha() {
  refreshImageCaptcha(registerCaptchaCanvasRef.value)
}

function paintForgotCaptcha() {
  refreshForgotCaptcha(forgotCaptchaCanvasLocalRef.value)
}

function openForgotPwdAccountPicker() {
  forgotPwdAccountPickerOpen.value = true
}

onMounted(async () => {
  await nextTick()
  paintRegisterCaptcha()
})

watch(mode, async (m) => {
  if (m === 'register') {
    authForm.value.phone = account.value.replace(/\D/g, '').slice(0, 11)
    await nextTick()
    paintRegisterCaptcha()
  }
})

watch(registerCaptchaCanvasRef, async (canvas) => {
  if (canvas) {
    await nextTick()
    paintRegisterCaptcha()
  }
})

watch(forgotPasswordOpen, async (open) => {
  if (open) {
    await nextTick()
    paintForgotCaptcha()
  }
})

watch(forgotCaptchaCanvasLocalRef, async (canvas) => {
  if (canvas && forgotPasswordOpen.value) {
    await nextTick()
    paintForgotCaptcha()
  }
})

async function onSendRegisterSms() {
  authForm.value.phone = account.value.replace(/\D/g, '').slice(0, 11)
  await sendSmsCode(registerCaptchaCanvasRef.value)
}
</script>

<template>
  <div ref="loginSceneEl" class="login-scene" id="login-scene" @pointermove="handlePointerMove">
    <canvas ref="particleCanvas" id="particle-canvas" aria-hidden="true"></canvas>
    <div class="scene-glow" id="scene-glow" aria-hidden="true"></div>

    <header class="scene-top">
      <button type="button" class="brand" @click="closeAuthPage()">
        <img class="brand-mark" src="/logo.png" alt="片刻帮" />
        <div class="brand-copy">
          <strong>片刻帮</strong>
          <span>顺路帮忙，刚好值得</span>
        </div>
      </button>
      <button type="button" class="back-link" @click="closeAuthPage()">← 返回首页</button>
    </header>

    <main class="login-shell" :class="{ 'card-exit': cardExit }">
      <article class="login-card" id="login-card" :class="{ 'card-exit': cardExit }">
        <aside class="login-aside" id="mascot-stage" aria-label="小帮帮互动区">
          <div class="aside-intro">
            <span class="aside-tag">小帮帮在线</span>
            <h2 class="aside-title">准备好了，<br />就一起出发</h2>
            <p class="aside-desc">我会根据你的填写进度，给出不同反应</p>
          </div>

          <div class="aside-character">
            <div class="speech-bubble" :class="{ updating: speechUpdating }" id="speech-bubble">
              <p id="mascot-say">{{ mascotSay }}</p>
            </div>

            <div
              ref="mascotEl"
              class="mascot"
              :class="mascotFlashClass"
              id="mascot"
              :data-mood="mascotMood"
              role="button"
              tabindex="0"
              aria-label="小帮帮，连点有惊喜"
              @click="onMascotTap()"
              @keydown.enter.prevent="onMascotTap()"
              @keydown.space.prevent="onMascotTap()"
            >
              <div v-show="showMascotSweat" class="mascot-sweat" id="mascot-sweat">💧</div>
              <div class="mascot-ear ear-left"></div>
              <div class="mascot-ear ear-right"></div>
              <div class="mascot-body">
                <div class="mascot-badge">帮</div>
                <div class="mascot-face">
                  <div class="eye eye-left"><div class="pupil" :style="pupilLeftStyle"></div><div class="eye-shine"></div></div>
                  <div class="eye eye-right"><div class="pupil" :style="pupilRightStyle"></div><div class="eye-shine"></div></div>
                  <div class="blush blush-left"></div>
                  <div class="blush blush-right"></div>
                  <div class="mouth" id="mascot-mouth"></div>
                </div>
              </div>
              <div class="mascot-arm arm-left"></div>
              <div class="mascot-arm arm-right"></div>
            </div>
          </div>

          <div class="aside-footer">
            <div class="trust-block">
              <span class="trust-label">信任值</span>
              <div class="trust-meter" id="trust-meter" aria-label="填写进度">
                <span v-for="(_, i) in trustOn" :key="`heart-${i}`" class="trust-heart" :class="{ on: trustOn[i] }"></span>
              </div>
            </div>
            <div class="aside-progress">
              <div class="progress-track">
                <div class="progress-fill" id="progress-fill" :style="{ width: `${progressPercent}%` }"></div>
              </div>
              <span class="progress-label" id="progress-label">{{ progressLabel }}</span>
            </div>
          </div>
        </aside>

        <section class="login-form-side" :class="{ 'auth-panel--register': mode === 'register' }" id="auth-panel">
          <div class="form-header">
            <div class="form-header-copy">
              <h1 id="login-heading">{{ heading }}</h1>
              <p id="login-subheading">{{ subheading }}</p>
            </div>
            <div class="auth-switch" :class="{ register: mode === 'register' }" role="tablist">
              <button
                class="auth-switch-btn"
                :class="{ active: mode === 'login' }"
                type="button"
                role="tab"
                id="tab-login"
                :aria-selected="mode === 'login'"
                @click="switchMode('login')"
              >
                登录
              </button>
              <button
                class="auth-switch-btn"
                :class="{ active: mode === 'register' }"
                type="button"
                role="tab"
                id="tab-register"
                :aria-selected="mode === 'register'"
                @click="switchMode('register')"
              >
                注册
              </button>
              <span class="auth-switch-thumb" id="auth-switch-thumb"></span>
            </div>
          </div>

          <form ref="authFormEl" class="auth-form" id="auth-form" novalidate @submit.prevent="onAuthSubmit">
            <div class="form-stack">
              <div class="form-field" data-step="1">
                <label for="phone" id="phone-label">{{ phoneLabel }}</label>
                <div class="input-shell">
                  <span class="input-icon">📱</span>
                  <input
                    id="phone"
                    v-model="account"
                    name="phone"
                    :type="phoneInputType"
                    autocomplete="username"
                    :placeholder="phonePlaceholder"
                    maxlength="20"
                    required
                    :class="{ valid: phoneValid, invalid: isFieldInvalid('phone') }"
                    @input="clearInvalidField('phone'); onPhoneInput(); syncMoodFromFocus()"
                    @focus="syncMoodFromFocus()"
                  />
                  <span v-if="phoneValid" class="field-status show" id="phone-status" aria-hidden="true">✓</span>
                </div>
                <p v-if="fieldErrorFor('phone')" class="field-inline-error">{{ fieldErrorFor('phone') }}</p>
              </div>

              <div v-show="mode === 'register'" class="form-field" id="nickname-field" data-step="2">
                <label for="nickname">昵称</label>
                <div class="input-shell">
                  <span class="input-icon">✨</span>
                  <input
                    id="nickname"
                    v-model="nickname"
                    name="nickname"
                    type="text"
                    autocomplete="nickname"
                    placeholder="怎么称呼你（登录后展示）"
                    maxlength="16"
                    :class="{ invalid: isFieldInvalid('nickname') }"
                    @input="clearInvalidField('nickname'); onNicknameInput(); updateProgress()"
                    @focus="syncMoodFromFocus()"
                  />
                </div>
                <p v-if="fieldErrorFor('nickname')" class="field-inline-error">{{ fieldErrorFor('nickname') }}</p>
              </div>

              <div v-show="mode === 'register'" class="form-field">
                <label for="realName">真实姓名</label>
                <div class="input-shell">
                  <span class="input-icon">🪪</span>
                  <input
                    id="realName"
                    v-model="authForm.realName"
                    type="text"
                    autocomplete="name"
                    placeholder="至少 2 个字"
                    :class="{ invalid: isFieldInvalid('realName') }"
                    @input="clearInvalidField('realName')"
                  />
                </div>
                <p v-if="fieldErrorFor('realName')" class="field-inline-error">{{ fieldErrorFor('realName') }}</p>
              </div>

              <div v-show="mode === 'register'" class="form-field">
                <label for="studentId">学号</label>
                <div class="input-shell">
                  <span class="input-icon">#</span>
                  <input
                    id="studentId"
                    v-model="authForm.studentId"
                    type="text"
                    inputmode="numeric"
                    placeholder="6-20 位数字"
                    maxlength="20"
                    :class="{ invalid: isFieldInvalid('studentId') }"
                    @input="clearInvalidField('studentId')"
                  />
                </div>
                <p v-if="fieldErrorFor('studentId')" class="field-inline-error">{{ fieldErrorFor('studentId') }}</p>
              </div>

              <div v-show="mode === 'register'" class="form-field">
                <label>性别</label>
                <div class="register-gender-row">
                  <label class="register-gender-option" :class="{ active: authForm.gender === 'male' }">
                    <input v-model="authForm.gender" type="radio" value="male" />
                    <span>男</span>
                  </label>
                  <label class="register-gender-option" :class="{ active: authForm.gender === 'female' }">
                    <input v-model="authForm.gender" type="radio" value="female" />
                    <span>女</span>
                  </label>
                </div>
              </div>

              <div v-show="mode === 'register'" class="form-field">
                <label>图形验证码</label>
                <div class="register-extra-row">
                  <div class="input-shell input-plain">
                    <input v-model="imageCaptchaInput" id="image-captcha" placeholder="输入图形码" autocomplete="off" :class="{ invalid: isFieldInvalid('image-captcha') }" @input="clearInvalidField('image-captcha')" />
                  </div>
                  <canvas
                    ref="registerCaptchaCanvasRef"
                    class="register-captcha-canvas"
                    width="130"
                    height="42"
                    title="点击刷新"
                    @click="paintRegisterCaptcha()"
                  ></canvas>
                </div>
                <p v-if="fieldErrorFor('image-captcha')" class="field-inline-error">{{ fieldErrorFor('image-captcha') }}</p>
              </div>

              <div v-show="mode === 'register'" class="form-field">
                <label for="reg-sms">短信验证码</label>
                <div class="input-shell input-plain register-extra-row">
                  <input
                    id="reg-sms"
                    v-model="authForm.smsCode"
                    placeholder="6 位验证码"
                    autocomplete="one-time-code"
                    maxlength="6"
                    :class="{ invalid: isFieldInvalid('reg-sms') }"
                    @input="clearInvalidField('reg-sms')"
                  />
                  <button type="button" class="register-sms-btn" :disabled="smsCountdown > 0 || smsSending" @click="onSendRegisterSms()">
                    {{ smsSending ? '发送中…' : smsCountdown > 0 ? `${smsCountdown}s` : '发送验证码' }}
                  </button>
                </div>
                <p v-if="fieldErrorFor('reg-sms')" class="field-inline-error">{{ fieldErrorFor('reg-sms') }}</p>
                <p v-else-if="registerSmsHint" class="register-hint" :class="{ 'register-hint--error': registerSmsHintError, 'register-hint--success': !registerSmsHintError }">
                  {{ registerSmsHint }}
                </p>
              </div>

              <div class="form-field" data-step="2">
                <label for="password">密码</label>
                <div class="input-shell">
                  <span class="input-icon">🔒</span>
                  <input
                    id="password"
                    v-model="password"
                    name="password"
                    :type="passwordVisible ? 'text' : 'password'"
                    :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
                    placeholder="至少 6 位"
                    minlength="6"
                    required
                    :class="{ invalid: isFieldInvalid('password') }"
                    @input="clearInvalidField('password'); updateStrength(); updateProgress()"
                    @focus="syncMoodFromFocus()"
                  />
                  <button class="toggle-password" type="button" id="toggle-password" aria-label="显示密码" @click="togglePassword()">
                    {{ passwordVisible ? '🙈' : '👁' }}
                  </button>
                </div>
                <p v-if="fieldErrorFor('password')" class="field-inline-error">{{ fieldErrorFor('password') }}</p>
                <div v-show="mode === 'register' && showStrengthBar" class="strength-bar" id="strength-bar" :data-level="String(strengthLevel)">
                  <span></span><span></span><span></span>
                </div>
              </div>

              <div v-show="mode === 'register'" class="form-field" id="confirm-field" data-step="3">
                <label for="confirm">确认密码</label>
                <div class="input-shell">
                  <span class="input-icon">✓</span>
                  <input
                    id="confirm"
                    v-model="confirm"
                    name="confirm"
                    type="password"
                    autocomplete="new-password"
                    placeholder="再次输入密码"
                    minlength="6"
                    :class="{ invalid: isFieldInvalid('confirm') }"
                    @input="clearInvalidField('confirm'); updateProgress()"
                    @focus="syncMoodFromFocus()"
                  />
                </div>
                <p v-if="fieldErrorFor('confirm')" class="field-inline-error">{{ fieldErrorFor('confirm') }}</p>
              </div>
            </div>

            <div class="form-actions">
              <div v-if="mode === 'login'" class="form-actions-meta login-only">
                <label class="remember-row" id="remember-row">
                  <input v-model="rememberMe" type="checkbox" id="remember" />
                  <span>保持登录状态</span>
                </label>
                <button type="button" class="forgot-link" @click="openForgotPasswordModal()">忘记密码？</button>
              </div>

              <p v-if="formError && !fieldError" class="form-error" id="form-error">{{ formError }}</p>

              <button
                class="btn btn-accent btn-block"
                type="submit"
                id="submit-btn"
                :class="{ loading: authLoading, 'handoff-enter': handoffEnter, 'handoff-ready': handoffReady }"
                :disabled="authLoading"
              >
                <span class="btn-text">{{ submitLabel }}</span>
                <span class="btn-loader" :hidden="!authLoading"></span>
              </button>
            </div>
          </form>

          <div v-if="mode === 'login'" class="auth-alt-login" aria-label="其他登录方式">
            <div class="auth-alt-login__rule" aria-hidden="true">
              <span>快捷登录</span>
            </div>
            <button
              type="button"
              class="auth-alt-login__wechat"
              :disabled="authLoading"
              @click="openWechatQr"
            >
              <span class="auth-alt-login__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
                  <path d="M9.5 4.2c-3.7 0-6.7 2.5-6.7 5.6 0 1.8 1 3.3 2.6 4.4l-.6 2.2 2.4-1.3c.7.2 1.4.3 2.1.3.2 0 .4 0 .6-.1-.1-.4-.2-.8-.2-1.2 0-3.1 2.9-5.6 6.4-5.6.2 0 .5 0 .7.1C16.1 5.9 13.1 4.2 9.5 4.2zm-2.3 3.2c.5 0 .8.4.8.8s-.4.8-.8.8-.8-.4-.8-.8.4-.8.8-.8zm4.6 0c.5 0 .8.4.8.8s-.4.8-.8.8-.8-.4-.8-.8.4-.8.8-.8z"/>
                  <path d="M17.8 10.6c-3.1 0-5.6 2.1-5.6 4.7s2.5 4.7 5.6 4.7c.5 0 1.1-.1 1.6-.2l1.9 1-.5-1.8c1.3-.9 2.1-2.2 2.1-3.7 0-2.6-2.5-4.7-5.1-4.7zm-2 3.2c.3 0 .6.3.6.6s-.3.6-.6.6-.6-.3-.6-.6.3-.6.6-.6zm3.9 0c.3 0 .6.3.6.6s-.3.6-.6.6-.6-.3-.6-.6.3-.6.6-.6z"/>
                </svg>
              </span>
              <span class="auth-alt-login__copy">
                <strong>微信扫码</strong>
                <em>扫码后在小程序确认登录电脑端</em>
              </span>
              <span class="auth-alt-login__arrow" aria-hidden="true">→</span>
            </button>
          </div>

          <label
            class="login-agree-row"
            :class="{ warn: !!agreeHint && !agreeLegal, shake: !!agreeHint && !agreeLegal }"
          >
            <input
              v-model="agreeLegal"
              type="checkbox"
              id="agree-legal"
              @change="clearAgreeHint()"
            />
            <span>
              我已阅读并同意
              <button type="button" class="login-foot-link" @click.stop="openLegalDoc('terms')">用户协议</button>
              与
              <button type="button" class="login-foot-link" @click.stop="openLegalDoc('privacy')">隐私政策</button>
            </span>
          </label>
          <p v-if="agreeHint && !agreeLegal" class="login-agree-hint" id="agree-error">{{ agreeHint }}</p>
        </section>
      </article>
    </main>

    <div
      v-if="legalDocView"
      class="legal-mask"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="'legal-doc-title'"
      @click.self="closeLegalDoc"
    >
      <section class="legal-sheet">
        <header class="legal-sheet-head">
          <div>
            <h2 id="legal-doc-title">{{ legalDocView.title }}</h2>
            <p>更新日期 {{ legalDocView.updated }}</p>
          </div>
          <button type="button" class="legal-close" aria-label="关闭" @click="closeLegalDoc">
            <svg viewBox="0 0 14 14" width="14" height="14" fill="none" aria-hidden="true">
              <path d="M3.5 3.5l7 7M10.5 3.5l-7 7" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
          </button>
        </header>
        <div class="legal-sheet-body">
          <p v-for="(para, idx) in legalDocView.paragraphs" :key="`legal-p-${idx}`">{{ para }}</p>
        </div>
        <footer class="legal-sheet-foot">
          <button type="button" class="btn btn-accent" @click="closeLegalDoc">我知道了</button>
        </footer>
      </section>
    </div>

    <button
      type="button"
      class="sound-toggle"
      id="sound-toggle"
      :aria-label="soundEnabled ? '关闭音效' : '开启音效'"
      :aria-pressed="soundEnabled"
      :title="soundEnabled ? '关闭音效' : '音效（默认关闭）'"
      @click="toggleSound()"
    >
      {{ soundEnabled ? '🔊' : '🔇' }}
    </button>

    <div v-if="smsAccountPickerOpen" class="success-overlay" @click.self="closeSmsAccountPicker()">
      <div class="success-card" style="text-align: left; max-width: 420px;">
        <h3>选择登录账号</h3>
        <p style="font-size: 13px; color: var(--ink-muted);">该手机号已通过短信验证，请选择要登录的账号</p>
        <ul style="list-style: none; padding: 0; margin: 16px 0; display: grid; gap: 8px;">
          <li v-for="a in smsPickCandidates" :key="a.userId">
            <button type="button" class="demo-fill" style="width: 100%; text-align: left;" :disabled="authLoading" @click="confirmSmsPickLogin(a.userId)">
              {{ a.username }} · ID {{ a.userId }}
            </button>
          </li>
        </ul>
        <button type="button" class="demo-fill" style="width: 100%;" @click="closeSmsAccountPicker()">取消</button>
      </div>
    </div>

    <div v-if="wechatQrOpen" class="success-overlay wx-qr-mask" @click.self="closeWechatQr()">
      <div class="wx-qr-sheet" role="dialog" aria-modal="true" aria-labelledby="wx-qr-title">
        <header class="wx-qr-sheet__head">
          <div>
            <p class="wx-qr-sheet__eyebrow">片刻帮 · 扫码登录</p>
            <h3 id="wx-qr-title">微信扫码登录</h3>
          </div>
          <button type="button" class="wx-qr-sheet__close" aria-label="关闭" @click="closeWechatQr">×</button>
        </header>

        <div class="wx-qr-sheet__frame" :class="{ loading: wechatQrLoading }">
          <div v-if="wechatQrLoading" class="wx-qr-sheet__skeleton">正在生成小程序码…</div>
          <img
            v-else-if="wechatQrImageUrl"
            class="wx-qr-sheet__img"
            :src="wechatQrImageUrl"
            alt="微信登录小程序码"
            width="220"
            height="220"
          />
          <div v-else class="wx-qr-sheet__skeleton">暂无二维码</div>
        </div>

        <p class="wx-qr-sheet__hint">{{ wechatQrHint || '扫码后在小程序内点「确认登录」即可' }}</p>
        <p v-if="wechatQrError" class="form-error wx-qr-sheet__error">{{ wechatQrError }}</p>

        <div class="wx-qr-sheet__actions">
          <button type="button" class="wx-qr-sheet__ghost" :disabled="wechatQrLoading" @click="openWechatQr">
            刷新二维码
          </button>
          <button type="button" class="btn btn-accent wx-qr-sheet__done" @click="closeWechatQr">完成</button>
        </div>
      </div>
    </div>

    <div v-if="forgotPasswordOpen" class="success-overlay" @click.self="closeForgotPasswordModal()">
      <div class="success-card" style="text-align: left; max-width: 480px;">
        <h3>重置密码</h3>
        <p class="register-hint">请填写注册时绑定的手机号，验证通过后可设置新密码。</p>
        <form class="auth-form" @submit.prevent="submitForgotPasswordReset()">
          <div class="form-stack">
            <div class="form-field">
              <label>手机号</label>
              <div class="input-shell">
                <input
                  v-model="forgotPasswordForm.phone"
                  type="tel"
                  inputmode="numeric"
                  autocomplete="tel"
                  maxlength="11"
                  placeholder="11 位注册手机号"
                  @input="scheduleForgotPwdSmsVerify()"
                />
              </div>
            </div>
            <div class="form-field">
              <label>图形码</label>
              <div class="register-extra-row">
                <div class="input-shell">
                  <input
                    v-model="forgotImageCaptchaInput"
                    autocomplete="off"
                    maxlength="4"
                    placeholder="输入右侧字符"
                  />
                </div>
                <canvas
                  ref="forgotCaptchaCanvasLocalRef"
                  class="register-captcha-canvas"
                  width="130"
                  height="42"
                  title="点击刷新图形验证码"
                  @click="paintForgotCaptcha()"
                ></canvas>
              </div>
            </div>
            <div class="form-field">
              <label>短信码</label>
              <div class="input-shell input-plain register-extra-row">
                <input
                  v-model="forgotPasswordForm.code"
                  inputmode="numeric"
                  autocomplete="one-time-code"
                  maxlength="6"
                  placeholder="6 位验证码"
                  @input="scheduleForgotPwdSmsVerify()"
                />
                <button
                  type="button"
                  class="register-sms-btn"
                  :disabled="forgotPwdSmsCountdown > 0"
                  @click="sendForgotPasswordSmsCode(forgotCaptchaCanvasLocalRef)"
                >
                  {{ forgotPwdSmsCountdown > 0 ? `${forgotPwdSmsCountdown}s` : '发送验证码' }}
                </button>
              </div>
            </div>
            <p v-if="forgotPwdModalMsg" class="register-hint">{{ forgotPwdModalMsg }}</p>
            <div v-if="forgotPwdVerifying" class="form-field">
              <p class="register-hint">正在验证短信码…</p>
            </div>
            <div v-else-if="forgotPwdNeedChooseAccount" class="form-field">
              <label>重置账号</label>
              <p v-if="forgotPwdSelectedUsername" class="forgot-account-selected">
                已选：{{ forgotPwdSelectedUsername }} · ID {{ forgotPwdSelectedUserId }}
              </p>
              <button type="button" class="forgot-account-repick" @click="openForgotPwdAccountPicker()">
                {{ forgotPwdSelectedUsername ? '重新选择账号' : '点击选择要重置的账号' }}
              </button>
            </div>
            <div v-else-if="forgotPwdSmsVerified && forgotPwdSelectedUsername" class="form-field">
              <label>重置账号</label>
              <p class="forgot-account-selected">{{ forgotPwdSelectedUsername }} · ID {{ forgotPwdSelectedUserId }}</p>
            </div>
            <div class="form-field">
              <label>新密码</label>
              <div class="input-shell">
                <input
                  v-model="forgotPasswordForm.newPassword"
                  type="password"
                  autocomplete="new-password"
                  minlength="6"
                  maxlength="32"
                  placeholder="6-32 位新密码"
                />
              </div>
            </div>
            <div class="form-field">
              <label>确认密码</label>
              <div class="input-shell">
                <input
                  v-model="forgotPasswordForm.confirmPassword"
                  type="password"
                  autocomplete="new-password"
                  minlength="6"
                  maxlength="32"
                  placeholder="再次输入新密码"
                />
              </div>
            </div>
          </div>
          <p v-if="forgotPwdModalError" class="form-error">{{ forgotPwdModalError }}</p>
          <button type="submit" class="btn btn-accent btn-block" :disabled="forgotPwdSubmitting">{{ forgotPwdSubmitting ? '提交中…' : '确认重置' }}</button>
        </form>
        <button type="button" class="demo-fill" style="width: 100%; margin-top: 10px;" @click="closeForgotPasswordModal()">关闭</button>
      </div>
    </div>

    <div v-if="forgotPwdAccountPickerOpen" class="success-overlay" style="z-index: 10001;" @click.self="closeForgotPwdAccountPicker()">
      <div class="success-card" style="text-align: left; max-width: 420px;">
        <h3>选择要重置的账号</h3>
        <p class="register-hint">该手机号绑定了多个账号，请选择其中一个重置密码。</p>
        <ul class="forgot-account-picker">
          <li v-for="a in forgotPwdCandidates" :key="a.userId">
            <button
              type="button"
              class="forgot-account-option"
              :class="{ selected: forgotPwdSelectedUserId === a.userId }"
              @click="selectForgotPwdAccount(a.userId, a.username)"
            >
              {{ a.username }} · ID {{ a.userId }}
            </button>
          </li>
        </ul>
        <button type="button" class="demo-fill" style="width: 100%; margin-top: 10px;" @click="closeForgotPwdAccountPicker()">取消</button>
      </div>
    </div>

    <!-- 成功欢迎层放最后，避免被二维码等弹层挡住 -->
    <div v-if="successVisible" class="success-overlay" id="success-overlay" style="z-index: 10002;">
      <div class="success-burst" aria-hidden="true"></div>
      <div class="success-card">
        <div class="success-icon">✓</div>
        <h3 id="success-title">{{ successTitle }}</h3>
        <p id="success-desc">正在带你回到首页…</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.brand,
.back-link {
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
}

.forgot-account-picker {
  list-style: none;
  padding: 0;
  margin: 8px 0 0;
  display: grid;
  gap: 8px;
}

.forgot-account-option {
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 10px;
  background: #fff;
  color: inherit;
  font: inherit;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.forgot-account-option.selected {
  border-color: rgba(196, 92, 38, 0.55);
  background: rgba(244, 220, 200, 0.45);
}

.forgot-account-selected {
  margin: 8px 0 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(244, 220, 200, 0.35);
  font-size: 14px;
}

.forgot-account-repick {
  width: 100%;
  margin-top: 8px;
  padding: 10px 12px;
  border: 1px dashed rgba(196, 92, 38, 0.45);
  border-radius: 10px;
  background: rgba(244, 220, 200, 0.2);
  color: var(--accent-dark, #9a3412);
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
</style>
