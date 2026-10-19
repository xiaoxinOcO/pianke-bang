import { nextTick, ref } from 'vue'
import { authBase } from '../../constants/api'
import type { ApiError, AuthMode, AuthResponse, SmsSendResponse } from '../../types/app'
import {
  formatNetworkErrorMessage,
  isAuthDisabledMessage,
  isTransientUnauthorizedMessage,
  messageFromParsedBody
} from '../../utils/http'
import { drawCaptchaOnto, randomCaptchaText } from '../../utils/captchaDraw'
import { clearToken, setToken } from '../../utils/token'

type AuthRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type AuthDomainDeps = {
  request: AuthRequest
  buildLoginRainDrops: (count?: number) => void
  openKickedDialog: (text?: string) => void
  /** 登录后编排（hydrate / 副作用），由主状态注入，可延迟赋值 */
  applyAuthLoginSuccess: () => Promise<void>
  clearSupportChatState: () => void
}

/**
 * 登录/注册域：表单态、验证码、发短信、忘记密码、提交登录/注册。
 * applyAuthLoginSuccess 等登录后副作用由主状态注入。
 */
export function createAuthDomain(deps: AuthDomainDeps) {
  const showAuthPage = ref(false)
  const authMode = ref<AuthMode>('login')
  const loginSubMode = ref<'password' | 'sms'>('password')
  const showLoginPassword = ref(false)
  const authLoading = ref(false)
  const authError = ref('')
  const registerSmsHint = ref('')
  const registerSmsHintError = ref(false)
  const registerSmsChannel = ref<'local' | 'aliyun' | null>(null)
  const smsSending = ref(false)
  const authForm = ref({
    phone: '',
    smsCode: '',
    loginSmsPhone: '',
    loginSmsCode: '',
    loginAccount: '',
    username: '',
    password: '',
    confirmPassword: '',
    realName: '',
    studentId: '',
    gender: 'male'
  })
  const smsCountdown = ref(0)
  const imageCaptcha = ref('')
  const imageCaptchaInput = ref('')
  const captchaCanvasRef = ref<HTMLCanvasElement | null>(null)
  const forgotImageCaptcha = ref('')
  const forgotImageCaptchaInput = ref('')
  const forgotCaptchaCanvasRef = ref<HTMLCanvasElement | null>(null)
  let smsTimer: number | null = null

  const smsAccountPickerOpen = ref(false)
  const smsPickToken = ref('')
  const smsPickCandidates = ref<Array<{ userId: number; username: string }>>([])

  const forgotPasswordOpen = ref(false)
  const forgotPasswordForm = ref({
    phone: '',
    code: '',
    newPassword: '',
    confirmPassword: ''
  })
  const forgotPwdPickToken = ref('')
  const forgotPwdCandidates = ref<Array<{ userId: number; username: string }>>([])
  const forgotPwdSelectedUserId = ref<number | null>(null)
  const forgotPwdSelectedUsername = ref('')
  const forgotPwdNeedChooseAccount = ref(false)
  const forgotPwdAccountPickerOpen = ref(false)
  const forgotPwdSmsVerified = ref(false)
  const forgotPwdVerifying = ref(false)
  let forgotPwdVerifyTimer: number | null = null
  const forgotPwdSmsCountdown = ref(0)
  let forgotPwdSmsTimer: number | null = null
  const forgotPwdSubmitting = ref(false)
  const forgotPwdModalMsg = ref('')
  const forgotPwdModalError = ref('')

  function drawCaptcha() {
    drawCaptchaOnto(captchaCanvasRef.value, imageCaptcha.value)
  }

  function refreshImageCaptcha(canvas?: HTMLCanvasElement | null) {
    imageCaptcha.value = randomCaptchaText(4)
    const target = canvas ?? captchaCanvasRef.value
    nextTick(() => {
      drawCaptchaOnto(target, imageCaptcha.value)
      if (!target) {
        window.requestAnimationFrame(() => {
          drawCaptchaOnto(canvas ?? captchaCanvasRef.value, imageCaptcha.value)
        })
      }
    })
  }

  function refreshForgotCaptcha(canvas?: HTMLCanvasElement | null) {
    forgotImageCaptcha.value = randomCaptchaText(4)
    const target = canvas ?? forgotCaptchaCanvasRef.value
    nextTick(() => {
      drawCaptchaOnto(target, forgotImageCaptcha.value)
      if (!target) {
        window.requestAnimationFrame(() => {
          drawCaptchaOnto(canvas ?? forgotCaptchaCanvasRef.value, forgotImageCaptcha.value)
        })
      }
    })
  }

  function selectLoginSmsTab() {
    loginSubMode.value = 'sms'
    refreshImageCaptcha()
  }

  function closeSmsAccountPicker() {
    smsAccountPickerOpen.value = false
    smsPickToken.value = ''
    smsPickCandidates.value = []
  }

  function resetForgotPwdAccountPick() {
    forgotPwdPickToken.value = ''
    forgotPwdCandidates.value = []
    forgotPwdSelectedUserId.value = null
    forgotPwdSelectedUsername.value = ''
    forgotPwdNeedChooseAccount.value = false
    forgotPwdAccountPickerOpen.value = false
    forgotPwdSmsVerified.value = false
  }

  function selectForgotPwdAccount(userId: number, username: string) {
    forgotPwdSelectedUserId.value = userId
    forgotPwdSelectedUsername.value = username
    forgotPwdModalError.value = ''
    forgotPwdAccountPickerOpen.value = false
  }

  function closeForgotPwdAccountPicker() {
    forgotPwdAccountPickerOpen.value = false
  }

  function closeForgotPasswordModal() {
    forgotPasswordOpen.value = false
    forgotPasswordForm.value = {
      phone: '',
      code: '',
      newPassword: '',
      confirmPassword: ''
    }
    forgotImageCaptchaInput.value = ''
    forgotPwdModalMsg.value = ''
    forgotPwdModalError.value = ''
    forgotPwdSubmitting.value = false
    forgotPwdSmsCountdown.value = 0
    resetForgotPwdAccountPick()
    if (forgotPwdVerifyTimer) {
      window.clearTimeout(forgotPwdVerifyTimer)
      forgotPwdVerifyTimer = null
    }
    if (forgotPwdSmsTimer) {
      window.clearInterval(forgotPwdSmsTimer)
      forgotPwdSmsTimer = null
    }
  }

  function openAuthPage(mode: AuthMode) {
    closeSmsAccountPicker()
    closeForgotPasswordModal()
    authMode.value = mode
    loginSubMode.value = 'password'
    showLoginPassword.value = false
    authError.value = ''
    registerSmsHint.value = ''
    registerSmsChannel.value = null
    authForm.value.loginAccount = ''
    authForm.value.loginSmsPhone = ''
    authForm.value.loginSmsCode = ''
    authForm.value.password = ''
    authForm.value.confirmPassword = ''
    imageCaptchaInput.value = ''
    refreshImageCaptcha()
    document.body.classList.add('auth-page-open')
    showAuthPage.value = true
    deps.buildLoginRainDrops()
  }

  function closeAuthPage() {
    showAuthPage.value = false
    document.body.classList.remove('auth-page-open')
    closeForgotPasswordModal()
    authError.value = ''
    registerSmsHint.value = ''
    registerSmsChannel.value = null
  }

  function openForgotPasswordModal() {
    const loginAccount = authForm.value.loginAccount.trim()
    const phoneDigits = loginAccount.replace(/\D/g, '').slice(0, 11)
    closeForgotPasswordModal()
    forgotPasswordOpen.value = true
    if (/^1\d{10}$/.test(phoneDigits)) {
      forgotPasswordForm.value.phone = phoneDigits
    }
    forgotImageCaptchaInput.value = ''
    refreshForgotCaptcha()
  }

  async function sendSmsCode(canvas?: HTMLCanvasElement | null) {
    authError.value = ''
    registerSmsHint.value = ''
    registerSmsHintError.value = false
    registerSmsChannel.value = null
    const phone = authForm.value.phone.trim()
    if (!/^1\d{10}$/.test(phone)) {
      const msg = '请先填写上方 11 位手机号'
      authError.value = msg
      registerSmsHint.value = msg
      registerSmsHintError.value = true
      return
    }

    if (imageCaptchaInput.value.trim().toUpperCase() !== imageCaptcha.value) {
      const msg = '请先填写正确的图形验证码'
      authError.value = msg
      registerSmsHint.value = msg
      registerSmsHintError.value = true
      refreshImageCaptcha(canvas)
      return
    }

    if (smsCountdown.value > 0) {
      return
    }

    smsSending.value = true
    try {
      const smsRes = await deps.request<SmsSendResponse>(`${authBase}/sms/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone })
      })

      authForm.value.smsCode = ''
      authError.value = ''
      registerSmsHintError.value = false
      registerSmsHint.value =
        smsRes.message || '验证码已发送，请填写收到的6位验证码'
      registerSmsChannel.value = smsRes.smsChannel === 'aliyun' ? 'aliyun' : 'local'
      smsCountdown.value = 60
      if (smsTimer) {
        window.clearInterval(smsTimer)
      }
      smsTimer = window.setInterval(() => {
        smsCountdown.value -= 1
        if (smsCountdown.value <= 0 && smsTimer) {
          window.clearInterval(smsTimer)
          smsTimer = null
        }
      }, 1000)
    } catch (err) {
      const msg = formatNetworkErrorMessage(err)
      authError.value = msg
      registerSmsHint.value = msg
      registerSmsHintError.value = true
    } finally {
      smsSending.value = false
    }
  }

  async function sendLoginSmsCode() {
    authError.value = ''
    registerSmsHint.value = ''
    registerSmsChannel.value = null
    const phone = authForm.value.loginSmsPhone.trim()
    if (!/^1\d{10}$/.test(phone)) {
      authError.value = '请输入11位手机号'
      return
    }

    if (imageCaptchaInput.value.trim().toUpperCase() !== imageCaptcha.value) {
      authError.value = '图形验证码错误，请重试'
      refreshImageCaptcha()
      return
    }

    if (smsCountdown.value > 0) {
      return
    }

    try {
      const smsRes = await deps.request<SmsSendResponse>(`${authBase}/sms/send-login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone })
      })

      authForm.value.loginSmsCode = ''
      authError.value = ''
      registerSmsHint.value =
        smsRes.message || '验证码已发送，请填写收到的6位验证码'
      registerSmsChannel.value = smsRes.smsChannel === 'aliyun' ? 'aliyun' : 'local'
      smsCountdown.value = 60
      if (smsTimer) {
        window.clearInterval(smsTimer)
      }
      smsTimer = window.setInterval(() => {
        smsCountdown.value -= 1
        if (smsCountdown.value <= 0 && smsTimer) {
          window.clearInterval(smsTimer)
          smsTimer = null
        }
      }, 1000)
    } catch (err) {
      authError.value = formatNetworkErrorMessage(err)
    }
  }

  async function verifyForgotPasswordSmsCode() {
    const phone = forgotPasswordForm.value.phone.trim()
    const code = forgotPasswordForm.value.code.trim()
    if (!/^1\d{10}$/.test(phone) || code.length < 6) {
      resetForgotPwdAccountPick()
      return
    }
    if (forgotPwdVerifying.value) return

    forgotPwdVerifying.value = true
    forgotPwdModalError.value = ''
    try {
      const data = await deps.request<{
        needChooseAccount?: boolean
        pickToken?: string
        userId?: number
        username?: string
        accounts?: Array<{ userId?: number; username?: string }>
        message?: string
      }>(`${authBase}/forgot-password/verify-sms`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone, code })
      })

      forgotPwdPickToken.value = String(data.pickToken || '')
      const accounts = Array.isArray(data.accounts)
        ? data.accounts.map((a) => ({
            userId: typeof a.userId === 'number' ? a.userId : Number(a.userId),
            username: String(a.username || '')
          }))
        : []
      const needChoose = !!data.needChooseAccount || accounts.length > 1

      if (needChoose && accounts.length > 0) {
        forgotPwdNeedChooseAccount.value = true
        forgotPwdCandidates.value = accounts
        forgotPwdSelectedUserId.value = null
        forgotPwdSelectedUsername.value = ''
        forgotPwdAccountPickerOpen.value = true
      } else {
        forgotPwdNeedChooseAccount.value = false
        forgotPwdCandidates.value = []
        forgotPwdAccountPickerOpen.value = false
        forgotPwdSelectedUserId.value =
          typeof data.userId === 'number' && Number.isFinite(data.userId)
            ? data.userId
            : accounts[0]?.userId ?? null
        forgotPwdSelectedUsername.value =
          String(data.username || '') || accounts[0]?.username || ''
      }
      forgotPwdSmsVerified.value = !!forgotPwdPickToken.value
      forgotPwdModalMsg.value = data.message || (needChoose ? '请选择要重置密码的账号' : '验证成功')
    } catch (err) {
      resetForgotPwdAccountPick()
      forgotPwdModalError.value = formatNetworkErrorMessage(err)
    } finally {
      forgotPwdVerifying.value = false
    }
  }

  function scheduleForgotPwdSmsVerify() {
    if (forgotPwdVerifyTimer) {
      window.clearTimeout(forgotPwdVerifyTimer)
      forgotPwdVerifyTimer = null
    }
    resetForgotPwdAccountPick()
    const phone = forgotPasswordForm.value.phone.trim()
    const code = forgotPasswordForm.value.code.trim()
    if (!/^1\d{10}$/.test(phone) || code.length < 6) {
      return
    }
    forgotPwdVerifyTimer = window.setTimeout(() => {
      forgotPwdVerifyTimer = null
      void verifyForgotPasswordSmsCode()
    }, 400)
  }

  async function sendForgotPasswordSmsCode(canvas?: HTMLCanvasElement | null) {
    forgotPwdModalMsg.value = ''
    forgotPwdModalError.value = ''
    const phone = forgotPasswordForm.value.phone.trim()
    if (!/^1\d{10}$/.test(phone)) {
      forgotPwdModalError.value = '请输入11位注册手机号'
      return
    }
    if (forgotImageCaptchaInput.value.trim().toUpperCase() !== forgotImageCaptcha.value) {
      forgotPwdModalError.value = '图形验证码错误，请重试'
      refreshForgotCaptcha(canvas)
      return
    }
    if (forgotPwdSmsCountdown.value > 0) {
      return
    }
    try {
      const smsRes = await deps.request<SmsSendResponse>(`${authBase}/forgot-password/sms/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone })
      })
      forgotPasswordForm.value.code = ''
      resetForgotPwdAccountPick()
      forgotPwdModalMsg.value = smsRes.message || '验证码已发送'
      forgotPwdSmsCountdown.value = 60
      if (forgotPwdSmsTimer) {
        window.clearInterval(forgotPwdSmsTimer)
      }
      forgotPwdSmsTimer = window.setInterval(() => {
        forgotPwdSmsCountdown.value -= 1
        if (forgotPwdSmsCountdown.value <= 0 && forgotPwdSmsTimer) {
          window.clearInterval(forgotPwdSmsTimer)
          forgotPwdSmsTimer = null
        }
      }, 1000)
    } catch (err) {
      forgotPwdModalError.value = formatNetworkErrorMessage(err)
      refreshForgotCaptcha(canvas)
    }
  }

  async function submitForgotPasswordReset() {
    forgotPwdModalMsg.value = ''
    forgotPwdModalError.value = ''
    const phone = forgotPasswordForm.value.phone.trim()
    const code = forgotPasswordForm.value.code.trim()
    const newPassword = forgotPasswordForm.value.newPassword.trim()
    const confirm = forgotPasswordForm.value.confirmPassword.trim()

    if (!/^1\d{10}$/.test(phone)) {
      forgotPwdModalError.value = '请输入11位注册手机号'
      return
    }
    if (!code) {
      forgotPwdModalError.value = '请输入短信验证码'
      return
    }
    if (!forgotPwdSmsVerified.value || !forgotPwdPickToken.value) {
      if (code.length >= 6) {
        await verifyForgotPasswordSmsCode()
      }
      if (!forgotPwdSmsVerified.value || !forgotPwdPickToken.value) {
        forgotPwdModalError.value = '请先输入正确的短信验证码'
        return
      }
    }
    if (forgotPwdNeedChooseAccount.value && forgotPwdSelectedUserId.value == null) {
      if (forgotPwdCandidates.value.length > 0) {
        forgotPwdAccountPickerOpen.value = true
      }
      forgotPwdModalError.value = '请选择要重置密码的账号'
      return
    }
    if (newPassword.length < 6 || newPassword.length > 32) {
      forgotPwdModalError.value = '新密码长度需在6-32位'
      return
    }
    if (newPassword !== confirm) {
      forgotPwdModalError.value = '两次输入的新密码不一致'
      return
    }

    forgotPwdSubmitting.value = true
    try {
      const res = await deps.request<{ message: string }>(`${authBase}/forgot-password/reset`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          phone,
          newPassword,
          pickToken: forgotPwdPickToken.value,
          userId: forgotPwdSelectedUserId.value
        })
      })
      closeForgotPasswordModal()
      authError.value = ''
      showAuthPage.value = true
      registerSmsHint.value = res.message || '密码已重置，请使用新密码登录'
      registerSmsChannel.value = null
      loginSubMode.value = 'password'
    } catch (err) {
      forgotPwdModalError.value = formatNetworkErrorMessage(err)
    } finally {
      forgotPwdSubmitting.value = false
    }
  }

  function disposeAuthTimers() {
    if (smsTimer) {
      window.clearInterval(smsTimer)
      smsTimer = null
    }
    if (forgotPwdSmsTimer) {
      window.clearInterval(forgotPwdSmsTimer)
      forgotPwdSmsTimer = null
    }
    if (forgotPwdVerifyTimer) {
      window.clearTimeout(forgotPwdVerifyTimer)
      forgotPwdVerifyTimer = null
    }
  }

  async function confirmSmsPickLogin(userId: number) {
    authError.value = ''
    const tok = smsPickToken.value.trim()
    if (!tok) {
      authError.value = '登录凭证已失效，请重新获取验证码'
      closeSmsAccountPicker()
      return
    }
    authLoading.value = true
    try {
      clearToken()
      const res = await fetch(`${authBase}/login-by-sms/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pickToken: tok, userId })
      })
      const data = (await res.json().catch(() => ({}))) as Partial<AuthResponse & ApiError>

      if (!res.ok) {
        const msg = messageFromParsedBody(data as Record<string, unknown>, res.status)
        authError.value = msg
        if (res.status === 400 && /过期|无效|凭证/.test(msg)) {
          closeSmsAccountPicker()
        }
        return
      }

      const ok = data as AuthResponse
      if (ok.token) {
        setToken(ok.token)
      }
      await deps.applyAuthLoginSuccess()
    } catch (err) {
      authError.value = formatNetworkErrorMessage(err)
    } finally {
      authLoading.value = false
    }
  }

  async function completeLoginWithToken(token: string) {
    const t = String(token || '').trim()
    if (!t) {
      authError.value = '登录凭证无效'
      return
    }
    deps.clearSupportChatState()
    clearToken()
    setToken(t)
    await deps.applyAuthLoginSuccess()
  }

  async function submitAuth() {
    authError.value = ''
    registerSmsHint.value = ''
    registerSmsChannel.value = null

    const password = authForm.value.password.trim()

    if (authMode.value === 'login') {
      if (loginSubMode.value === 'sms') {
        const phone = authForm.value.loginSmsPhone.trim()
        const smsCode = authForm.value.loginSmsCode.trim()
        if (!/^1\d{10}$/.test(phone)) {
          authError.value = '请输入11位手机号'
          return
        }
        if (!smsCode) {
          authError.value = '请输入短信验证码'
          return
        }

        authLoading.value = true
        try {
          clearToken()
          const res = await fetch(`${authBase}/login-by-sms`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ phone, code: smsCode })
          })
          const data = (await res.json().catch(() => ({}))) as Partial<
            AuthResponse &
              ApiError & {
                needChooseAccount?: boolean
                pickToken?: string
                accounts?: Array<{ userId?: number; username?: string }>
              }
          >

          if (!res.ok) {
            const msg = messageFromParsedBody(data as Record<string, unknown>, res.status)
            if (res.status === 400) {
              authError.value = msg
              return
            }
            if (res.status >= 500 || isTransientUnauthorizedMessage(msg)) {
              authError.value = msg
              return
            }
            authError.value = ''
            if (isAuthDisabledMessage(msg)) {
              deps.openKickedDialog('该账号已被管理员禁用，请联系管理员后重试。')
            } else {
              deps.openKickedDialog(
                res.status === 401 || res.status === 403 || /请求失败\s*\(\s*401\s*\)/.test(msg)
                  ? '无法完成登录，请稍后再试或联系管理员。'
                  : msg
              )
            }
            return
          }

          if (data.needChooseAccount && data.pickToken && data.accounts?.length) {
            smsPickToken.value = data.pickToken
            smsPickCandidates.value = data.accounts.map((a) => ({
              userId: typeof a.userId === 'number' ? a.userId : Number(a.userId),
              username: String(a.username || '')
            }))
            smsAccountPickerOpen.value = true
            return
          }

          const ok = data as AuthResponse
          if (ok.token) {
            setToken(ok.token)
          }
          await deps.applyAuthLoginSuccess()
        } catch (err) {
          authError.value = formatNetworkErrorMessage(err)
        } finally {
          authLoading.value = false
        }
        return
      }

      const rawAccount = authForm.value.loginAccount.trim()
      const phoneDigits = rawAccount.replace(/\D/g, '').slice(0, 11)
      const account = /^1\d{10}$/.test(phoneDigits) ? phoneDigits : rawAccount
      if (!account || !password) {
        authError.value = '请输入账号名和密码（支持账号ID、注册手机号或管理员用户名）'
        return
      }

      authLoading.value = true
      try {
        clearToken()
        const res = await fetch(`${authBase}/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ account, password })
        })
        const data = (await res.json().catch(() => ({}))) as Partial<AuthResponse & ApiError>

        if (!res.ok) {
          const msg = messageFromParsedBody(data as Record<string, unknown>, res.status)
          if (res.status === 400) {
            authError.value = msg
            return
          }
          if (/人工客服|客服工作台/.test(msg)) {
            authError.value = msg
            return
          }
          if (res.status >= 500 || isTransientUnauthorizedMessage(msg)) {
            authError.value = msg
            return
          }
          authError.value = ''
          if (isAuthDisabledMessage(msg)) {
            deps.openKickedDialog('该账号已被管理员禁用，请联系管理员后重试。')
          } else {
            deps.openKickedDialog(
              res.status === 401 || res.status === 403 || /请求失败\s*\(\s*401\s*\)/.test(msg)
                ? '无法完成登录，请稍后再试或联系管理员。'
                : msg
            )
          }
          return
        }

        const ok = data as AuthResponse
        if (ok.token) {
          setToken(ok.token)
        }

        await deps.applyAuthLoginSuccess()
      } catch (err) {
        authError.value = formatNetworkErrorMessage(err)
      } finally {
        authLoading.value = false
      }
      return
    }

    const username = authForm.value.username.trim()

    if (!username || !password) {
      authError.value = '请填写昵称和密码'
      return
    }

    if (authMode.value === 'register') {
      const phone = authForm.value.phone.trim()
      const smsCode = authForm.value.smsCode.trim()
      const realName = authForm.value.realName.trim()
      const studentId = authForm.value.studentId.trim()
      const gender = authForm.value.gender

      if (!/^1\d{10}$/.test(phone)) {
        authError.value = '请输入11位手机号'
        return
      }
      if (!smsCode) {
        authError.value = '请输入验证码'
        return
      }
      if (!realName || realName.length < 2) {
        authError.value = '请填写真实姓名（至少2字）'
        return
      }
      if (!/^\d{6,20}$/.test(studentId)) {
        authError.value = '请填写正确学号（6-20位数字）'
        return
      }
      if (password !== authForm.value.confirmPassword.trim()) {
        authError.value = '两次输入的密码不一致'
        return
      }

      authLoading.value = true
      const registerAbort = new AbortController()
      const registerAbortTimer = window.setTimeout(() => registerAbort.abort(), 45000)
      try {
        const res = await deps.request<AuthResponse>(`${authBase}/register-by-sms`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ phone, code: smsCode, username, password, realName, studentId, gender }),
          signal: registerAbort.signal
        })

        authForm.value.smsCode = ''
        if (res.token) {
          clearToken()
          setToken(res.token)
          authError.value = ''
          authForm.value.password = ''
          authForm.value.confirmPassword = ''
          await deps.applyAuthLoginSuccess()
          return
        }

        authMode.value = 'login'
        const uid = res.userId != null ? `账号ID：${res.userId}，` : ''
        authError.value = `注册成功，${uid}请使用账号ID或注册手机号登录（昵称：${res.username}）`
        return
      } catch (err) {
        const e = err as Error
        authError.value =
          e?.name === 'AbortError'
            ? '请求超时（常见于服务器访问阿里云卡住）。请稍后重试或检查云服出网/安全组。'
            : e.message
        return
      } finally {
        window.clearTimeout(registerAbortTimer)
        authLoading.value = false
      }
    }
  }

  return {
    showAuthPage,
    authMode,
    loginSubMode,
    showLoginPassword,
    authLoading,
    authError,
    registerSmsHint,
    registerSmsHintError,
    registerSmsChannel,
    smsSending,
    authForm,
    smsCountdown,
    imageCaptcha,
    imageCaptchaInput,
    captchaCanvasRef,
    forgotImageCaptcha,
    forgotImageCaptchaInput,
    forgotCaptchaCanvasRef,
    smsAccountPickerOpen,
    smsPickToken,
    smsPickCandidates,
    forgotPasswordOpen,
    forgotPasswordForm,
    forgotPwdPickToken,
    forgotPwdCandidates,
    forgotPwdSelectedUserId,
    forgotPwdSelectedUsername,
    forgotPwdNeedChooseAccount,
    forgotPwdAccountPickerOpen,
    forgotPwdSmsVerified,
    forgotPwdVerifying,
    forgotPwdSmsCountdown,
    forgotPwdSubmitting,
    forgotPwdModalMsg,
    forgotPwdModalError,
    drawCaptcha,
    refreshImageCaptcha,
    refreshForgotCaptcha,
    selectLoginSmsTab,
    closeSmsAccountPicker,
    openAuthPage,
    closeAuthPage,
    openForgotPasswordModal,
    closeForgotPasswordModal,
    selectForgotPwdAccount,
    closeForgotPwdAccountPicker,
    scheduleForgotPwdSmsVerify,
    verifyForgotPasswordSmsCode,
    sendForgotPasswordSmsCode,
    submitForgotPasswordReset,
    sendSmsCode,
    sendLoginSmsCode,
    submitAuth,
    confirmSmsPickLogin,
    completeLoginWithToken,
    disposeAuthTimers
  }
}
