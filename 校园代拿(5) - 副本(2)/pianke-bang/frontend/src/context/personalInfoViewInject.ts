/**
 * PersonalInfoView 由 stores/use*Store 提供的字段
 */
export interface PersonalInfoViewInject {
  personalInfoTab: 'base' | 'avatar' | 'password' | 'apps' | 'lang'
  myName: string
  personalInfoForm: {
    realName: string
    userId: number | null
    studentId: string
    gender: 'male' | 'female'
    phone: string
  }
  profileForm: { username: string; avatarUrl: string }
  profileNotice?: string
  myAvatarUrl: string
  normalizeImageUrl: (url: string) => string
  triggerAvatarFilePick: () => void
  bindAvatarFileInput: (el: HTMLInputElement | null) => void
  onAvatarFileSelected: (e: Event) => void
  myAvatarPending: boolean
  savePersonalAvatar: () => void | Promise<void>
  savePersonalBaseInfo: () => void | Promise<void>
  errorMsg: string
  passwordForm: { oldPassword: string; newPassword: string; confirmNewPassword: string }
  bindPasswordCaptchaCanvas: (el: HTMLCanvasElement | null) => void
  refreshPasswordCaptcha: () => void
  passwordCaptchaInput: string
  changePassword: () => void | Promise<void>
}
