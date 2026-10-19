import { nextTick, ref, watch, type Ref } from 'vue'
import { authBase } from '../../constants/api'
import type { PageView } from '../../types/app'
import { randomCaptchaText } from '../../utils/captchaDraw'

type PersonalInfoRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type PersonalInfoDomainDeps = {
  isLoggedIn: Ref<boolean>
  errorMsg: Ref<string>
  profileNotice: Ref<string>
  showUserDropdown: Ref<boolean>
  profileForm: Ref<{ username: string; avatarUrl: string }>
  myName: Ref<string>
  myAvatarUrl: Ref<string>
  myAvatarPending: Ref<boolean>
  isRiderApproved: Ref<boolean>
  riderApplicationPending: Ref<boolean>
  /** 发布表单（只写 publisherName，避免与完整 form 类型纠缠） */
  form: { value: { publisherName: string } }
  request: PersonalInfoRequest
  setToken: (token: string) => void
  showProfileNotice: (text: string) => void
  showRealtimeToast: (payload: {
    title: string
    message: string
    kind: 'chat' | 'task'
    chatUser?: string
  }) => void
  setView: (view: PageView) => void
}

/**
 * 个人资料页：基本信息、头像、改密与验证码。
 * 昵称/头像等展示态仍由主状态持有，本域负责表单与提交。
 */
export function createPersonalInfoDomain(deps: PersonalInfoDomainDeps) {
  const personalInfoTab = ref<'base' | 'avatar' | 'password' | 'apps' | 'lang'>('base')
  const personalInfoForm = ref({
    realName: '',
    userId: null as number | null,
    studentId: '',
    gender: 'male' as 'male' | 'female',
    phone: ''
  })
  const passwordCaptchaInput = ref('')
  const passwordCaptchaCode = ref('')
  const passwordCaptchaCanvasRef = ref<HTMLCanvasElement | null>(null)
  const passwordForm = ref({
    oldPassword: '',
    newPassword: '',
    confirmNewPassword: ''
  })
  const avatarFileInputRef = ref<HTMLInputElement | null>(null)

  function drawPasswordCaptcha() {
    const canvas = passwordCaptchaCanvasRef.value
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const w = canvas.width
    const h = canvas.height

    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, w, h)

    for (let i = 0; i < 90; i += 1) {
      ctx.fillStyle = `rgba(59,130,246,${0.03 + Math.random() * 0.05})`
      ctx.fillRect(Math.random() * w, Math.random() * h, 1.2, 1.2)
    }

    for (let i = 0; i < passwordCaptchaCode.value.length; i += 1) {
      const ch = passwordCaptchaCode.value[i]
      const x = 12 + i * 24
      const y = 28 + Math.random() * 3
      const angle = (Math.random() - 0.5) * 0.25

      ctx.save()
      ctx.translate(x, y)
      ctx.rotate(angle)
      ctx.font = '700 24px Segoe UI, Arial'
      ctx.fillStyle = '#111827'
      ctx.fillText(ch, 0, 0)
      ctx.restore()
    }
  }

  function refreshPasswordCaptcha() {
    passwordCaptchaCode.value = randomCaptchaText(4)
    passwordCaptchaInput.value = ''
    nextTick(() => drawPasswordCaptcha())
  }

  function ensurePasswordCaptchaReady() {
    if (!passwordCaptchaCode.value || !passwordCaptchaCanvasRef.value) {
      refreshPasswordCaptcha()
    }
  }

  function goPersonalInfo() {
    if (!deps.isLoggedIn.value) {
      return
    }
    deps.showUserDropdown.value = false
    deps.setView('personalInfo')
    nextTick(() => ensurePasswordCaptchaReady())
  }

  function openProfileSettings() {
    goPersonalInfo()
  }

  function triggerAvatarFilePick() {
    avatarFileInputRef.value?.click()
  }

  function onAvatarFileSelected(event: Event) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return

    if (!file.type.startsWith('image/')) {
      deps.errorMsg.value = '请选择图片文件（jpg/png/webp）'
      input.value = ''
      return
    }

    const reader = new FileReader()
    reader.onload = () => {
      deps.profileForm.value.avatarUrl = String(reader.result || '')
      deps.errorMsg.value = ''
      deps.showProfileNotice('已选择新头像，请点击下方「保存头像并提交审核」。')
    }
    reader.readAsDataURL(file)
    input.value = ''
  }

  function bindAvatarFileInput(el: HTMLInputElement | null) {
    avatarFileInputRef.value = el
  }

  function bindPasswordCaptchaCanvas(el: HTMLCanvasElement | null) {
    passwordCaptchaCanvasRef.value = el
    if (el) {
      nextTick(() => {
        if (!passwordCaptchaCode.value) {
          passwordCaptchaCode.value = randomCaptchaText(4)
        }
        drawPasswordCaptcha()
      })
    }
  }

  async function saveProfile(opts?: { fromAvatar?: boolean }) {
    if (!deps.isLoggedIn.value) return
    deps.errorMsg.value = ''
    deps.profileNotice.value = ''

    const username = deps.profileForm.value.username.trim()
    const avatarUrl = deps.profileForm.value.avatarUrl.trim()

    if (!username) {
      deps.errorMsg.value = '用户名不能为空'
      return
    }

    try {
      const res = await deps.request<{
        message: string
        username: string
        avatarUrl?: string
        pendingAvatarUrl?: string
        avatarPending?: boolean
        riderApproved?: boolean
        riderApplicationPending?: boolean
        token?: string
      }>(`${authBase}/profile`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, avatarUrl })
      })

      deps.myName.value = res.username
      deps.myAvatarUrl.value = res.avatarUrl || ''
      deps.myAvatarPending.value = !!res.avatarPending
      deps.isRiderApproved.value = !!res.riderApproved
      deps.riderApplicationPending.value = !!res.riderApplicationPending
      deps.profileForm.value.username = res.username
      if (res.avatarPending) {
        const pend = (res.pendingAvatarUrl || '').trim()
        deps.profileForm.value.avatarUrl = pend || avatarUrl
      } else {
        deps.profileForm.value.avatarUrl = res.avatarUrl || ''
      }
      if (res.token) {
        deps.setToken(res.token)
      }
      deps.form.value.publisherName = res.username

      const notice =
        res.message
        || (deps.myAvatarPending.value
          ? '头像已提交审核，审核通过后自动生效。'
          : opts?.fromAvatar
            ? '头像已更新。'
            : '资料已保存成功。')
      deps.showProfileNotice(notice)
      if (opts?.fromAvatar || deps.myAvatarPending.value) {
        deps.showRealtimeToast({
          title: deps.myAvatarPending.value ? '头像已提交' : '头像已更新',
          message: notice,
          kind: 'task'
        })
      }
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    }
  }

  async function savePersonalAvatar() {
    deps.errorMsg.value = ''
    if (!deps.profileForm.value.avatarUrl.trim()) {
      deps.errorMsg.value = '请先点击头像上传本地图片'
      return
    }
    await saveProfile({ fromAvatar: true })
  }

  async function savePersonalBaseInfo() {
    if (!deps.isLoggedIn.value) return
    deps.errorMsg.value = ''
    deps.profileNotice.value = ''

    const username = deps.profileForm.value.username.trim()
    const realName = personalInfoForm.value.realName.trim()
    const studentId = personalInfoForm.value.studentId.trim()
    const gender = personalInfoForm.value.gender
    const avatarUrl = deps.profileForm.value.avatarUrl.trim()

    if (!username) {
      deps.errorMsg.value = '昵称不能为空'
      return
    }
    if (username.length > 12) {
      deps.errorMsg.value = '昵称最多12个字'
      return
    }
    if (!realName) {
      deps.errorMsg.value = '请填写真实姓名'
      return
    }
    if (realName.length < 2) {
      deps.errorMsg.value = '真实姓名至少2个字'
      return
    }

    try {
      const res = await deps.request<{
        message: string
        username: string
        realName?: string
        studentId?: string
        gender?: string
        avatarUrl?: string
        pendingAvatarUrl?: string
        avatarPending?: boolean
        riderApproved?: boolean
        riderApplicationPending?: boolean
        token?: string
      }>(`${authBase}/profile`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, realName, studentId, gender, avatarUrl })
      })

      deps.myName.value = res.username
      deps.myAvatarUrl.value = res.avatarUrl || ''
      deps.myAvatarPending.value = !!res.avatarPending
      deps.isRiderApproved.value = !!res.riderApproved
      deps.riderApplicationPending.value = !!res.riderApplicationPending
      deps.profileForm.value.username = res.username
      personalInfoForm.value.realName = res.realName || realName
      personalInfoForm.value.studentId = res.studentId ?? studentId
      if (res.gender === 'male' || res.gender === 'female') {
        personalInfoForm.value.gender = res.gender
      }
      if (res.avatarPending) {
        const pend = (res.pendingAvatarUrl || '').trim()
        deps.profileForm.value.avatarUrl = pend || avatarUrl
      } else {
        deps.profileForm.value.avatarUrl = res.avatarUrl || ''
      }
      if (res.token) {
        deps.setToken(res.token)
      }
      deps.form.value.publisherName = res.username
      deps.showProfileNotice(res.message || '基本资料已保存')
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
    }
  }

  async function changePassword() {
    if (!deps.isLoggedIn.value) return
    deps.errorMsg.value = ''

    ensurePasswordCaptchaReady()
    if (!passwordCaptchaCode.value) {
      deps.errorMsg.value = '验证码未就绪，请点击验证码图片刷新后重试'
      return
    }

    const oldPassword = passwordForm.value.oldPassword.trim()
    const newPassword = passwordForm.value.newPassword.trim()
    const confirmNewPassword = passwordForm.value.confirmNewPassword.trim()

    if (!oldPassword || !newPassword || !confirmNewPassword) {
      deps.errorMsg.value = '请填写完整密码信息'
      return
    }
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,16}$/.test(newPassword)) {
      deps.errorMsg.value = '新密码需8-16位，且至少包含字母和数字'
      return
    }
    if (newPassword !== confirmNewPassword) {
      deps.errorMsg.value = '两次新密码不一致'
      return
    }
    if (passwordCaptchaInput.value.trim().toUpperCase() !== passwordCaptchaCode.value) {
      deps.errorMsg.value = '验证码错误，请重试'
      refreshPasswordCaptcha()
      return
    }

    try {
      const res = await deps.request<{ message: string }>(`${authBase}/password`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ oldPassword, newPassword })
      })
      passwordForm.value.oldPassword = ''
      passwordForm.value.newPassword = ''
      passwordForm.value.confirmNewPassword = ''
      passwordCaptchaInput.value = ''
      refreshPasswordCaptcha()
      deps.errorMsg.value = res.message || '密码修改成功'
    } catch (err) {
      deps.errorMsg.value = (err as Error).message
      refreshPasswordCaptcha()
    }
  }

  watch(personalInfoTab, (tab) => {
    if (tab === 'password') {
      refreshPasswordCaptcha()
    }
  })

  return {
    personalInfoTab,
    personalInfoForm,
    passwordCaptchaInput,
    passwordCaptchaCode,
    passwordCaptchaCanvasRef,
    passwordForm,
    avatarFileInputRef,
    drawPasswordCaptcha,
    refreshPasswordCaptcha,
    ensurePasswordCaptchaReady,
    goPersonalInfo,
    openProfileSettings,
    triggerAvatarFilePick,
    onAvatarFileSelected,
    bindAvatarFileInput,
    bindPasswordCaptchaCanvas,
    saveProfile,
    savePersonalAvatar,
    savePersonalBaseInfo,
    changePassword
  }
}
