import { computed, nextTick, onMounted, onUnmounted, ref, toRef, watch, type Ref } from 'vue'
import { getCampusApp } from '../app/campusAppState'

const MOOD_SAY: Record<string, string> = {
  idle: '你好呀，填完信息我就带你进去～',
  phone: '手机号是多少？我帮你记着 📱',
  phoneOk: '号码格式对了！这看起来靠谱 ✓',
  nickname: '我可以怎么称呼你？✨',
  password: '密码我不看，你放心输 🤭',
  confirm: '再输一次确认，我照样不看',
  peek: '诶…你真要我偷看明文？👀',
  ready: '信息齐了！接好登录按钮～ 🚀',
  error: '好像哪里不对，再检查一下？',
  verify: '让我核对一下你的信息… 🔍',
  loading: '稍等，马上就好…',
  happy: '欢迎回来！🎉',
  curious: '新同学？欢迎加入片刻帮 ✨',
  familiar: '又见面啦，欢迎回来 👋',
  phoneBad: '这号码好像不太对 📱',
  pwdWrong: '密码不对，我不偷看，你自己改～',
  pwdWeak: '这密码…让我有点紧张 😰',
  pwdMid: '嗯，还行，再稳一点 👌',
  pwdStrong: '这密码看起来很有安全感！💪',
  loginRetryHint: '连续错了两次…再核对一下账号和密码，或试试「忘记密码」～',
  wave: '进去吧，待会儿见～ 👋',
  ticklish: '别戳啦，我痒！',
  morning: '早呀，今天也要顺路帮忙吗？☀️',
  lateNight: '这么晚还在忙？辛苦了 🌙',
  nicknameLong: '昵称太长了，我记不下来啦～',
  pwdDanger: '这个组合有点危险哦 ⚠️'
}

const NO_TRACK_MOODS = new Set([
  'password', 'confirm', 'loading', 'happy', 'wave',
  'pwd-weak', 'pwd-mid', 'pwd-strong', 'pwd-danger', 'ticklish'
])

function validateUsername(name: string) {
  return /^[a-zA-Z0-9_\u4e00-\u9fa5]{2,20}$/.test(name.trim())
}

function validatePhone(phone: string) {
  return /^1\d{10}$/.test(phone.replace(/\D/g, ''))
}

function formatPhone(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, 11)
  if (digits.length <= 3) return digits
  if (digits.length <= 7) return `${digits.slice(0, 3)} ${digits.slice(3)}`
  return `${digits.slice(0, 3)} ${digits.slice(3, 7)} ${digits.slice(7)}`
}

function normalizePhoneDigits(value: string) {
  let digits = value.replace(/\D/g, '')
  if (digits.length > 11 && digits.startsWith('86')) digits = digits.slice(2)
  return digits.slice(0, 11)
}

function getPasswordLevel(pwd: string) {
  if (!pwd) return -1
  if (pwd.length < 6) return 0
  if (pwd.length >= 8 && /\d/.test(pwd) && /[a-zA-Z]/.test(pwd)) {
    if (pwd.length >= 10 && /[^a-zA-Z0-9]/.test(pwd)) return 2
    return 1
  }
  return 0
}

const SoundFX = {
  STORAGE_KEY: 'piankebang_login_sound',
  enabled: false,
  ctx: null as AudioContext | null,

  load() {
    this.enabled = localStorage.getItem(this.STORAGE_KEY) === '1'
  },

  save() {
    localStorage.setItem(this.STORAGE_KEY, this.enabled ? '1' : '0')
  },

  async ensureCtx() {
    if (!this.ctx) {
      const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
      if (!Ctx) return false
      this.ctx = new Ctx()
    }
    if (this.ctx.state === 'suspended') await this.ctx.resume()
    return true
  },

  tone(freq: number, { type = 'sine', dur = 0.08, vol = 0.04, delay = 0 } = {}) {
    if (!this.enabled || !this.ctx) return
    const t = this.ctx.currentTime + delay
    const osc = this.ctx.createOscillator()
    const gain = this.ctx.createGain()
    osc.type = type as OscillatorType
    osc.frequency.setValueAtTime(freq, t)
    gain.gain.setValueAtTime(vol, t)
    gain.gain.exponentialRampToValueAtTime(0.001, t + dur)
    osc.connect(gain)
    gain.connect(this.ctx.destination)
    osc.start(t)
    osc.stop(t + dur)
  },

  play(name: string) {
    if (!this.enabled) return
    void this.ensureCtx().then((ok) => {
      if (!ok) return
      if (name === 'cover') this.tone(520, { type: 'sine', dur: 0.09, vol: 0.035 })
      if (name === 'wink') this.tone(880, { type: 'triangle', dur: 0.06, vol: 0.03 })
      if (name === 'poke') this.tone(740, { type: 'square', dur: 0.05, vol: 0.022 })
      if (name === 'warn') this.tone(220, { type: 'sine', dur: 0.14, vol: 0.035 })
      if (name === 'toggle') this.tone(600, { type: 'sine', dur: 0.05, vol: 0.028 })
      if (name === 'success') {
        ;[523, 659, 784].forEach((freq, i) => {
          this.tone(freq, { dur: 0.12, vol: 0.032, delay: i * 0.07 })
        })
      }
    })
  },

  toggle() {
    this.enabled = !this.enabled
    this.save()
    if (this.enabled) void this.ensureCtx().then(() => this.play('toggle'))
  }
}

export function usePiankeLogin() {
  const r = getCampusApp().domains.auth
  // reactive(appRoot) 会解包嵌套 ref，须用 toRef 才能正确读写
  const authForm = toRef(r, 'authForm') as Ref<Record<string, string>>
  const authMode = toRef(r, 'authMode') as Ref<'login' | 'register'>
  const authError = toRef(r, 'authError') as Ref<string>
  const authLoading = toRef(r, 'authLoading') as Ref<boolean>
  const loginSubMode = toRef(r, 'loginSubMode') as Ref<'password' | 'sms'>
  const isLoggedIn = toRef(r, 'isLoggedIn') as Ref<boolean>
  const showAuthPage = toRef(r, 'showAuthPage') as Ref<boolean>
  const myName = toRef(r, 'myName') as Ref<string>
  const submitAuth = r.submitAuth as () => Promise<void>
  const closeAuthPage = r.closeAuthPage as () => void

  const mode = ref<'login' | 'register'>('login')
  const passwordVisible = ref(false)
  const mascotMood = ref('idle')
  const mascotFlashClass = ref('')
  const mascotSay = ref(MOOD_SAY.idle)
  const speechUpdating = ref(false)
  const showMascotSweat = ref(false)
  const phoneWasValid = ref(false)
  const failCount = ref(0)
  const handoffReady = ref(false)
  const handoffEnter = ref(false)
  const rememberMe = ref(true)
  const phoneValid = ref(false)
  const strengthLevel = ref(0)
  const showStrengthBar = ref(false)
  const successVisible = ref(false)
  const cardExit = ref(false)
  const soundEnabled = ref(false)
  const invalidField = ref('')
  const fieldError = ref('')
  const pendingErrorField = ref<string | null>(null)
  const farewellPlaying = ref(false)
  let farewellTimers: number[] = []

  const account = ref('')
  const nickname = ref('')
  const password = ref('')
  const confirm = ref('')

  const progressPercent = ref(0)
  const progressLabel = ref('填写 0 / 2 项')
  const trustOn = ref([false, false, false, false])

  const particleCanvas = ref<HTMLCanvasElement | null>(null)
  const mascotEl = ref<HTMLElement | null>(null)
  const loginSceneEl = ref<HTMLElement | null>(null)
  const authFormEl = ref<HTMLFormElement | null>(null)
  const pupilLeft = ref({ x: 0, y: 0 })
  const pupilRight = ref({ x: 0, y: 0 })
  let mousePos = { x: window.innerWidth / 2, y: window.innerHeight / 2 }
  let lastPhoneNodAt = 0
  let lastPwdLevel = -1
  let pwdDangerActive = false
  let mascotTapCount = 0
  let mascotTapTimer: number | null = null
  let easterEggActive = false
  let particleStop: (() => void) | null = null

  const heading = computed(() => (mode.value === 'login' ? '欢迎回来' : '加入片刻帮'))
  const subheading = computed(() =>
    mode.value === 'login' ? '登录后继续你的校园互助之旅' : '注册后即可接单、发布与提现'
  )
  const phoneLabel = computed(() => '手机号')
  const phonePlaceholder = computed(() => (mode.value === 'login' ? '138 0013 8000' : '11 位手机号'))
  const phoneInputType = computed(() => 'tel')
  const submitLabel = computed(() => (mode.value === 'login' ? '登录' : '注册并登录'))
  const successTitle = computed(() => (mode.value === 'register' ? '注册成功' : '登录成功'))
  const formError = computed(() => authError.value)

  const pupilLeftStyle = computed(() => {
    if (!shouldTrackSide('left')) return undefined
    const y = mascotMood.value === 'late-night' ? pupilLeft.value.y + 2 : pupilLeft.value.y
    return { transform: `translate(${pupilLeft.value.x}px, ${y}px)` }
  })

  const pupilRightStyle = computed(() => {
    if (!shouldTrackSide('right')) return undefined
    const y = mascotMood.value === 'late-night' ? pupilRight.value.y + 2 : pupilRight.value.y
    return { transform: `translate(${pupilRight.value.x}px, ${y}px)` }
  })

  function shouldTrackSide(side: 'left' | 'right') {
    const mood = mascotMood.value
    if (NO_TRACK_MOODS.has(mood)) return false
    if (mood === 'peek' && side === 'left') return false
    if (mascotFlashClass.value === 'wink' && side === 'right') return false
    return true
  }

  function sayKey(key: string) {
    sayRaw(MOOD_SAY[key] || MOOD_SAY.idle)
  }

  function sayRaw(text: string) {
    speechUpdating.value = true
    window.setTimeout(() => {
      mascotSay.value = text
      speechUpdating.value = false
    }, 120)
  }

  function setMood(mood: string, options: { sayKey?: string; sayText?: string; silent?: boolean } = {}) {
    const prev = mascotMood.value
    mascotMood.value = mood
    showMascotSweat.value = ['error', 'phone-bad', 'pwd-danger'].includes(mood)
    if (!options.silent) {
      if (options.sayText) sayRaw(options.sayText)
      else if (options.sayKey) sayKey(options.sayKey)
    }
    if ((mood === 'password' || mood === 'confirm') && prev !== mood) SoundFX.play('cover')
    if (mood === 'peek' && prev !== 'peek') SoundFX.play('wink')
    updateEyeTracking()
  }

  function getMascotRoot() {
    return mascotEl.value ?? document.getElementById('mascot')
  }

  function calcPupilOffset(eye: HTMLElement) {
    const rect = eye.getBoundingClientRect()
    if (rect.width === 0 || rect.height === 0) return { x: 0, y: 0 }
    const cx = rect.left + rect.width / 2
    const cy = rect.top + rect.height / 2
    const angle = Math.atan2(mousePos.y - cy, mousePos.x - cx)
    const dist = Math.min(7, Math.hypot(mousePos.x - cx, mousePos.y - cy) / 24)
    return { x: Math.cos(angle) * dist, y: Math.sin(angle) * dist }
  }

  function updateEyeTracking() {
    const root = getMascotRoot()
    if (!root) return
    const leftEye = root.querySelector<HTMLElement>('.eye-left')
    const rightEye = root.querySelector<HTMLElement>('.eye-right')
    pupilLeft.value = leftEye && shouldTrackSide('left') ? calcPupilOffset(leftEye) : { x: 0, y: 0 }
    pupilRight.value = rightEye && shouldTrackSide('right') ? calcPupilOffset(rightEye) : { x: 0, y: 0 }
  }

  function handlePointerMove(e: MouseEvent | PointerEvent) {
    mousePos = { x: e.clientX, y: e.clientY }
    cancelAnimationFrame(eyeTrackRaf)
    eyeTrackRaf = requestAnimationFrame(updateEyeTracking)
  }

  function seedMousePos() {
    mousePos = { x: window.innerWidth / 2, y: window.innerHeight / 2 }
    const root = getMascotRoot()
    if (root) {
      const rect = root.getBoundingClientRect()
      mousePos = { x: rect.left + rect.width / 2, y: rect.top + rect.height / 3 }
    }
  }

  function flashClass(className: string, ms = 500) {
    mascotFlashClass.value = className
    if (className === 'wink') SoundFX.play('wink')
    if (className === 'squirm') SoundFX.play('poke')
    updateEyeTracking()
    window.setTimeout(() => {
      if (mascotFlashClass.value === className) mascotFlashClass.value = ''
      updateEyeTracking()
    }, ms)
  }

  function getTimeGreeting() {
    const hour = new Date().getHours()
    if (hour >= 22 || hour < 5) return { mood: 'late-night', sayKey: 'lateNight' }
    if (hour >= 6 && hour < 8) return { mood: 'morning', sayKey: 'morning' }
    return null
  }

  function onNicknameInput() {
    const raw = nickname.value
    const name = raw.trim()
    if (document.activeElement?.id !== 'nickname') return
    if (raw.length >= 13) {
      setMood('nickname-long', { sayKey: 'nicknameLong' })
      return
    }
    if (name) sayRaw(`以后叫你 ${name} 啦？`)
    else setMood('nickname', { sayKey: 'nickname', silent: !name })
  }

  function getRequiredFields() {
    if (mode.value === 'login') return ['account', 'password'] as const
    return ['account', 'nickname', 'password', 'confirm'] as const
  }

  function isAccountValid(value: string) {
    const trimmed = value.trim()
    const digits = normalizePhoneDigits(trimmed)
    if (mode.value === 'login') {
      if (digits.length >= 11) return validatePhone(digits)
      return validateUsername(trimmed)
    }
    return validatePhone(digits)
  }

  function isFieldComplete(field: string) {
    if (field === 'account') return isAccountValid(account.value)
    if (field === 'nickname') return nickname.value.trim().length > 0
    if (field === 'password') return password.value.length >= 6
    if (field === 'confirm') return confirm.value.length >= 6 && confirm.value === password.value
    return false
  }

  function updateTrustHearts() {
    const fields = getRequiredFields()
    const completed = fields.filter(isFieldComplete).length
    trustOn.value = trustOn.value.map((_, i) => {
      const visible = mode.value === 'login' ? i < 2 : i < 4
      if (!visible) return false
      return i < completed
    })
    const allDone = completed === fields.length
    const active = document.activeElement
    const onSecret = active?.id === 'password' || active?.id === 'confirm'
    if (allDone && !onSecret && !['loading', 'verify', 'error', 'wave', 'ticklish'].includes(mascotMood.value)) {
      setMood('ready', { sayKey: 'ready' })
      triggerSubmitHandoff()
    } else if (!allDone) {
      handoffReady.value = false
      handoffEnter.value = false
    }
  }

  function triggerSubmitHandoff() {
    if (handoffReady.value) return
    handoffReady.value = true
    handoffEnter.value = false
    requestAnimationFrame(() => {
      handoffEnter.value = true
    })
  }

  function updateProgress() {
    const fields = getRequiredFields()
    const filled = fields.filter((f) => {
      if (f === 'account') return account.value.trim().length > 0
      if (f === 'nickname') return nickname.value.trim().length > 0
      if (f === 'password') return password.value.length > 0
      return confirm.value.length > 0
    }).length
    progressPercent.value = Math.round((filled / fields.length) * 100)
    progressLabel.value = `填写 ${filled} / ${fields.length} 项`
    updateTrustHearts()
  }

  function updatePhoneStatus() {
    const valid = isAccountValid(account.value)
    phoneValid.value = valid
    if (valid && !phoneWasValid.value) {
      phoneWasValid.value = true
      flashClass('wink', 600)
      sayKey('phoneOk')
    }
    if (!valid) phoneWasValid.value = false
  }

  function onPhoneInput() {
    const raw = account.value.trim()
    if (mode.value === 'register' || /^[\d\s]+$/.test(raw)) {
      const digits = normalizePhoneDigits(account.value)
      account.value = formatPhone(digits)
      if (mode.value === 'register') {
        authForm.value.phone = digits
      }
    }
    const digitLen = account.value.replace(/\D/g, '').length
    const nodAt = Math.floor(digitLen / 3) * 3
    if (nodAt >= 3 && nodAt <= 9 && nodAt !== lastPhoneNodAt) {
      lastPhoneNodAt = nodAt
      flashClass('nod', 420)
    }
    updatePhoneStatus()
    updateProgress()
  }

  function updatePasswordMood() {
    if (document.activeElement?.id !== 'password' || passwordVisible.value) return
    const pwd = password.value
    const phoneDigits = normalizePhoneDigits(account.value)
    if (phoneDigits.length >= 11 && pwd.length >= 6 && pwd === phoneDigits) {
      if (!pwdDangerActive) {
        pwdDangerActive = true
        lastPwdLevel = -1
        setMood('pwd-danger', { sayKey: 'pwdDanger' })
        SoundFX.play('warn')
      }
      return
    }
    pwdDangerActive = false
    const level = getPasswordLevel(pwd)
    if (level < 0) {
      setMood('password', { sayKey: 'password', silent: true })
      lastPwdLevel = -1
      return
    }
    if (level === lastPwdLevel) return
    lastPwdLevel = level
    if (level === 0) setMood('pwd-weak', { sayKey: 'pwdWeak' })
    else if (level === 1) setMood('pwd-mid', { sayKey: 'pwdMid' })
    else setMood('pwd-strong', { sayKey: 'pwdStrong' })
  }

  function updateStrength() {
    if (mode.value === 'register') {
      showStrengthBar.value = password.value.length > 0
      const level = getPasswordLevel(password.value)
      strengthLevel.value = level >= 0 ? level + 1 : 0
    }
    updatePasswordMood()
  }

  function syncMoodFromFocus() {
    if (easterEggActive) return
    const active = document.activeElement as HTMLElement | null
    if (active?.id === 'password' || active?.id === 'confirm') {
      if (passwordVisible.value && active.id === 'password') setMood('peek', { sayKey: 'peek' })
      else if (active.id === 'confirm') setMood('confirm', { sayKey: 'confirm' })
      else {
        updatePasswordMood()
        if (!password.value) setMood('password', { sayKey: 'password' })
      }
      return
    }
    if (active?.id === 'phone') {
      setMood('phone', { sayKey: 'phone' })
      return
    }
    if (active?.id === 'nickname') {
      onNicknameInput()
      if (!nickname.value.trim()) setMood('nickname', { sayKey: 'nickname' })
      return
    }
    const fields = getRequiredFields()
    if (fields.every(isFieldComplete)) setMood('ready', { sayKey: 'ready' })
    else setMood('idle', { sayKey: 'idle' })
  }

  function switchMode(next: 'login' | 'register') {
    mode.value = next
    authMode.value = next
    document.body.classList.toggle('mode-register', next === 'register')
    document.body.classList.toggle('mode-login', next === 'login')
    if (next === 'register') {
      authForm.value.phone = normalizePhoneDigits(account.value)
    }
    phoneWasValid.value = false
    lastPhoneNodAt = 0
    lastPwdLevel = -1
    pwdDangerActive = false
    handoffReady.value = false
    handoffEnter.value = false
    failCount.value = 0
    authError.value = ''
    invalidField.value = ''
    fieldError.value = ''
    pendingErrorField.value = null
    updateProgress()
    if (next === 'login') setMood('familiar', { sayKey: 'familiar' })
    else setMood('curious', { sayKey: 'curious' })
  }

  function syncRegisterFormToAuth() {
    authForm.value.phone = normalizePhoneDigits(account.value)
    authForm.value.username = nickname.value.trim()
    authForm.value.password = password.value
    authForm.value.confirmPassword = confirm.value
  }

  function focusRegisterField(field: string) {
    window.requestAnimationFrame(() => {
      const el = document.getElementById(field)
      if (!el) return
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      el.focus()
    })
  }

  function resolveRegisterErrorField(message: string, explicitField?: string) {
    if (explicitField) return explicitField
    if (message.includes('图形验证码') || message.includes('图形码')) return 'image-captcha'
    if (message.includes('手机号')) return 'phone'
    if (message.includes('短信')) return 'reg-sms'
    if (message.includes('验证码')) return 'reg-sms'
    if (message.includes('昵称')) return 'nickname'
    if (message.includes('真实姓名') || message.includes('姓名')) return 'realName'
    if (message.includes('学号')) return 'studentId'
    if (message.includes('不一致') || message.includes('确认')) return 'confirm'
    if (message.includes('密码')) return 'password'
    return 'phone'
  }

  async function markInvalidField(field: string) {
    invalidField.value = ''
    await nextTick()
    invalidField.value = field
    focusRegisterField(field)
  }

  function clearInvalidField(field?: string) {
    if (!field || invalidField.value === field) {
      invalidField.value = ''
      fieldError.value = ''
    }
  }

  function isFieldInvalid(field: string) {
    return invalidField.value === field
  }

  function fieldErrorFor(field: string) {
    return isFieldInvalid(field) ? fieldError.value : ''
  }

  function validateRegisterForm(): { message: string; field: string } | null {
    syncRegisterFormToAuth()

    const phone = authForm.value.phone
    if (!phone) {
      return { message: '请填写手机号', field: 'phone' }
    }
    if (!/^1\d{10}$/.test(phone)) {
      return { message: '请输入11位手机号', field: 'phone' }
    }

    if (!authForm.value.username.trim()) {
      return { message: '请填写昵称', field: 'nickname' }
    }

    const realName = authForm.value.realName.trim()
    if (!realName) {
      return { message: '请填写真实姓名', field: 'realName' }
    }
    if (realName.length < 2) {
      return { message: '真实姓名至少2个字', field: 'realName' }
    }

    const studentId = authForm.value.studentId.trim()
    if (!studentId) {
      return { message: '请填写学号', field: 'studentId' }
    }
    if (!/^\d{6,20}$/.test(studentId)) {
      return { message: '学号需为6-20位数字', field: 'studentId' }
    }

    if (!authForm.value.smsCode.trim()) {
      return { message: '请填写短信验证码', field: 'reg-sms' }
    }

    const pwd = password.value.trim()
    if (!pwd) {
      return { message: '请填写密码', field: 'password' }
    }
    if (pwd.length < 6) {
      return { message: '密码至少需要6位', field: 'password' }
    }

    if (!confirm.value.trim()) {
      return { message: '请填写确认密码', field: 'confirm' }
    }
    if (pwd !== confirm.value.trim()) {
      return { message: '两次输入的密码不一致', field: 'confirm' }
    }

    return null
  }

  function validateLoginForm(): { message: string; field: string } | null {
    if (!account.value.trim()) {
      return { message: '请填写账号或手机号', field: 'phone' }
    }
    if (!password.value.trim()) {
      return { message: '请填写密码', field: 'password' }
    }
    if (password.value.trim().length < 6) {
      return { message: '密码至少需要6位', field: 'password' }
    }
    return null
  }

  function showFormValidationError(message: string, field: string) {
    pendingErrorField.value = field
    fieldError.value = message
    // 框内提示，不再把错误堆在底部（聚焦后底部看不见）
    authError.value = ''
    void markInvalidField(field)
    if (field === 'phone') {
      setMood('phone-bad', { sayKey: 'phoneBad' })
      flashClass('tilt', 580)
    } else {
      setMood('error', { sayKey: 'error' })
    }
  }

  function handleAuthError(message: string, field?: string) {
    failCount.value += 1
    authError.value = message
    if (field === 'phone' || field === 'account') {
      setMood('phone-bad', { sayKey: 'phoneBad' })
      flashClass('tilt', 580)
    } else if (field === 'password') {
      setMood('password', { sayKey: 'pwdWrong' })
    } else {
      setMood('error', { sayKey: 'error' })
    }
    if (failCount.value >= 2) {
      sayKey('loginRetryHint')
    }
  }

  function clearFarewellTimers() {
    farewellTimers.forEach((t) => window.clearTimeout(t))
    farewellTimers = []
  }

  function playAuthFarewell() {
    if (farewellPlaying.value) return
    farewellPlaying.value = true
    failCount.value = 0
    SoundFX.play('success')
    const name = myName.value.trim() || nickname.value.trim() || account.value.trim() || '同学'
    sayRaw(`${name}，欢迎！挥手送你进去～`)
    setMood('wave', { silent: true })
    cardExit.value = false
    successVisible.value = false

    clearFarewellTimers()
    farewellTimers.push(
      window.setTimeout(() => {
        cardExit.value = true
        successVisible.value = true
      }, 700)
    )
    farewellTimers.push(
      window.setTimeout(() => {
        closeAuthPage()
        successVisible.value = false
        cardExit.value = false
        farewellPlaying.value = false
      }, 1650)
    )
  }

  function maybePlayAuthFarewell() {
    if (!isLoggedIn.value || !showAuthPage.value || authError.value || farewellPlaying.value) return
    playAuthFarewell()
  }

  async function handleSubmit(e: Event) {
    e.preventDefault()
    if (authLoading.value) return
    authError.value = ''
    invalidField.value = ''
    fieldError.value = ''
    pendingErrorField.value = null

    if (mode.value === 'register') {
      const invalid = validateRegisterForm()
      if (invalid) {
        showFormValidationError(invalid.message, invalid.field)
        return
      }
    } else {
      const invalid = validateLoginForm()
      if (invalid) {
        showFormValidationError(invalid.message, invalid.field)
        return
      }
    }

    setMood('verify', { sayKey: 'verify' })
    flashClass('nod', 480)

    await new Promise((resolve) => window.setTimeout(resolve, 520))
    setMood('loading', { sayKey: 'loading' })

    authMode.value = mode.value
    loginSubMode.value = 'password'

    if (mode.value === 'login') {
      const trimmed = account.value.trim()
      const phoneDigits = normalizePhoneDigits(trimmed)
      authForm.value.loginAccount = /^1\d{10}$/.test(phoneDigits) ? phoneDigits : trimmed
      authForm.value.password = password.value
    } else {
      authForm.value.phone = normalizePhoneDigits(account.value)
      authForm.value.username = nickname.value.trim()
      authForm.value.password = password.value
      authForm.value.confirmPassword = confirm.value
    }

    await submitAuth()

    if (authError.value) {
      if (mode.value === 'register' && authError.value.includes('注册成功')) {
        setMood('happy', { sayKey: 'happy' })
      } else {
        handleAuthError(authError.value, mode.value === 'login' ? 'password' : undefined)
      }
      if (mode.value === 'register' && authMode.value === 'login') {
        mode.value = 'login'
        document.body.classList.remove('mode-register')
        document.body.classList.add('mode-login')
      }
      return
    }

    maybePlayAuthFarewell()
  }

  function togglePassword() {
    passwordVisible.value = !passwordVisible.value
    lastPwdLevel = -1
    if (document.activeElement?.id === 'password') syncMoodFromFocus()
  }

  function onMascotTap() {
    const mood = mascotMood.value
    if (['loading', 'verify', 'wave'].includes(mood)) return
    mascotTapCount += 1
    if (mascotTapTimer) window.clearTimeout(mascotTapTimer)
    mascotTapTimer = window.setTimeout(() => {
      mascotTapCount = 0
    }, 2500)
    flashClass('squirm', 320)
    if (mascotTapCount >= 5) {
      mascotTapCount = 0
      easterEggActive = true
      setMood('ticklish', { sayKey: 'ticklish' })
      flashClass('squirm', 900)
      window.setTimeout(() => {
        easterEggActive = false
        const form = authFormEl.value
        if (form?.contains(document.activeElement)) syncMoodFromFocus()
        else {
          const greeting = getTimeGreeting()
          if (greeting) setMood(greeting.mood, { sayKey: greeting.sayKey })
          else setMood('idle', { sayKey: 'idle' })
        }
      }, 3200)
    }
  }

  function initParticles() {
    const canvas = particleCanvas.value
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    let w = 0
    let h = 0
    let raf = 0
    const dots = Array.from({ length: 45 }, () => ({
      x: Math.random() * window.innerWidth,
      y: Math.random() * window.innerHeight,
      r: Math.random() * 2 + 0.5,
      vx: (Math.random() - 0.5) * 0.25,
      vy: (Math.random() - 0.5) * 0.25
    }))

    function resize() {
      w = canvas!.width = window.innerWidth
      h = canvas!.height = window.innerHeight
    }

    function draw() {
      ctx!.clearRect(0, 0, w, h)
      dots.forEach((d) => {
        d.x += d.vx
        d.y += d.vy
        if (d.x < 0) d.x = w
        if (d.x > w) d.x = 0
        if (d.y < 0) d.y = h
        if (d.y > h) d.y = 0
        ctx!.beginPath()
        ctx!.arc(d.x, d.y, d.r, 0, Math.PI * 2)
        ctx!.fillStyle = 'rgba(248, 244, 236, 0.3)'
        ctx!.fill()
      })
      raf = requestAnimationFrame(draw)
    }

    window.addEventListener('resize', resize)
    resize()
    draw()
    particleStop = () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('resize', resize)
    }
  }

  function toggleSound() {
    SoundFX.toggle()
    soundEnabled.value = SoundFX.enabled
  }

  let removeMouseMove: (() => void) | null = null
  let removeFormFocusOut: (() => void) | null = null
  let eyeTrackRaf = 0

  function bindEyeTracking() {
    removeMouseMove?.()
    seedMousePos()

    window.addEventListener('mousemove', handlePointerMove, { passive: true })
    window.addEventListener('pointermove', handlePointerMove, { passive: true })
    document.addEventListener('mousemove', handlePointerMove, { passive: true })
    removeMouseMove = () => {
      window.removeEventListener('mousemove', handlePointerMove)
      window.removeEventListener('pointermove', handlePointerMove)
      document.removeEventListener('mousemove', handlePointerMove)
    }

    updateEyeTracking()
  }

  function initMascotEvents() {
    removeFormFocusOut?.()
    const form = authFormEl.value
    if (!form) return

    const onFormFocusOut = () => {
      window.setTimeout(() => {
        if (form.contains(document.activeElement)) return
        passwordVisible.value = false
        syncMoodFromFocus()
      }, 30)
    }
    form.addEventListener('focusout', onFormFocusOut)
    removeFormFocusOut = () => form.removeEventListener('focusout', onFormFocusOut)
  }

  onMounted(async () => {
    SoundFX.load()
    soundEnabled.value = SoundFX.enabled
    document.body.classList.add('auth-page-open')
    document.body.classList.add('mode-login')
    mode.value = authMode.value === 'register' ? 'register' : 'login'
    if (mode.value === 'register') {
      document.body.classList.add('mode-register')
      document.body.classList.remove('mode-login')
    }
    account.value = authForm.value.loginAccount || ''
    password.value = authForm.value.password || ''

    await nextTick()
    initParticles()
    initMascotEvents()
    updateProgress()

    const greeting = getTimeGreeting()
    if (greeting) setMood(greeting.mood, { sayKey: greeting.sayKey })
    else setMood('idle', { sayKey: 'idle' })

    bindEyeTracking()
    if (!greeting) document.getElementById('phone')?.focus()
  })

  onUnmounted(() => {
    removeMouseMove?.()
    removeFormFocusOut?.()
    clearFarewellTimers()
    cancelAnimationFrame(eyeTrackRaf)
    document.body.classList.remove('mode-login', 'mode-register', 'auth-page-open')
    particleStop?.()
    if (mascotTapTimer) window.clearTimeout(mascotTapTimer)
  })

  watch(mascotEl, () => {
    void nextTick(() => {
      seedMousePos()
      updateEyeTracking()
    })
  })

  watch([mascotMood, mascotFlashClass], () => {
    updateEyeTracking()
  })

  watch(authFormEl, (form) => {
    if (form) initMascotEvents()
  })

  watch(authMode, (m) => {
    mode.value = m
    document.body.classList.toggle('mode-register', m === 'register')
    document.body.classList.toggle('mode-login', m === 'login')
  })

  watch(authError, (msg) => {
    if (!msg) return
    if (mode.value !== 'register') {
      pendingErrorField.value = null
      return
    }
    if (
      msg.includes('注册成功') ||
      msg.includes('验证码已发送') ||
      msg.includes('密码已重置')
    ) {
      return
    }
    const field = pendingErrorField.value ?? resolveRegisterErrorField(msg)
    pendingErrorField.value = null
    fieldError.value = msg
    void markInvalidField(field)
  })

  watch([isLoggedIn, showAuthPage], () => {
    void nextTick(() => {
      maybePlayAuthFarewell()
    })
  })

  return {
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
    authFormEl,
    pupilLeftStyle,
    pupilRightStyle,
    loginSceneEl,
    handlePointerMove,
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
    toggleSound
  }
}
