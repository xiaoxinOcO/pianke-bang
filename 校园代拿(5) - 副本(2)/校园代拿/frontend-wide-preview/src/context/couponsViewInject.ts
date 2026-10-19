export interface UserCouponRow {
  id: number
  templateId?: number
  status?: string
  name?: string
  type?: string
  discountAmount?: number
  minSpend?: number
  discountPercent?: number
  maxDiscount?: number
  expireAt?: string
  obtainedAt?: string
  usedAt?: string
  sceneCode?: string
  bannerTheme?: string
  bannerImageUrl?: string
}

export interface CouponsViewInject {
  myCoupons: UserCouponRow[]
  myCouponsLoading: boolean
  fetchMyCoupons?: (status?: string) => void | Promise<void>
  redeemCouponCode?: (code: string) => void | Promise<void>
  goHall?: () => void
  goPublish?: () => void
  normalizeImageUrl?: (url?: string) => string
  isLoggedIn: boolean
}
