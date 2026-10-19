/**
 * PublishView 由 stores/use*Store 提供的字段
 */
export interface PublishViewInject {
  editingRejectedTaskId: number | null
  publishFormHint: { type: 'success' | 'error'; text: string } | null
  isLoggedIn: boolean
  form: {
    taskType: string
    title: string
    pickupLocation: string
    pickupLat?: number | null
    pickupLng?: number | null
    dropoffLocation: string
    dropoffLat?: number | null
    dropoffLng?: number | null
    reward: number
    publisherName: string
    contact: string
    description: string
    deliveryStartAt: string
    deliveryEndAt: string
    latestDeliveryAt: string
    imageUrls: string[]
  }
  latestDeliveryQuickOption: string
  selectedDeliveryDate: string
  selectedDeliverySummary: string
  deliveryDateOptions: Array<{ value: string; label: string }>
  deliveryQuickOptions: Array<{ value: string; label: string }>
  estimateLatestDeliveryText: string
  showDeliveryPicker: boolean
  openDeliveryPicker: () => void
  closeDeliveryPicker: () => void
  setSelectedDeliveryDate: (value: string) => void
  setLatestDeliveryQuickOption: (value: string) => void
  setPickupCascaderRootEl: (el: unknown) => void
  pickupManualInput: string
  focusPickupCascader: () => void
  showPickupCascader: boolean
  pickupDisplayText: string
  buildingOptions: string[]
  pickupBuilding: string
  pickupRoom: string
  isClassroomBuilding: boolean
  roomOptions: string[]
  setDropoffCascaderRootEl: (el: unknown) => void
  dropoffManualInput: string
  focusDropoffCascader: () => void
  showDropoffCascader: boolean
  dropoffDisplayText: string
  dropoffBuilding: string
  dropoffRoom: string
  isDropoffClassroomBuilding: boolean
  dropoffRoomOptions: string[]
  onPublishImagesSelected: (event: Event) => void
  removePublishImage: (idx: number) => void
  myName: string
  taskTypeOptions: Array<{ value: string; label: string }>
  taskTypeLabel: (taskType?: string) => string
  submitting: boolean
  publishPaymentMethod: 'alipay' | 'wechat' | 'wallet'
  publishPayableTotal: number
  publishWalletPayDisabled: boolean
  publishPayCoupons: Array<Record<string, unknown>>
  publishSelectedCouponId: number | null
  publishCouponDiscount: number
  publishCouponLoading: boolean
  publishCouponOptionLabel: (item: Record<string, unknown>) => string
  onPublishCouponSelected: (raw: string) => void | Promise<void>
  urgentEnabled: boolean
  urgentFee: number | string
  urgentFeePresets: readonly number[]
  urgentFeeValid: boolean
  urgentFeeError: string
  urgentRiderBonus: number
  urgentPushFee: number
  urgentRemaining: number
  urgentWeeklyLimit: number
  togglePublishUrgent: () => void
  pickUrgentFee: (fee: number) => void
  walletOverview: { balance: number; totalIncome: number; totalWithdrawn: number; withdrawingAmount: number }
  walletBalanceLoading: boolean
  showPublishPaymentSheet: boolean
  publishPaymentSheetMode: 'create' | 'resubmit'
  closePublishPaymentSheet: () => void
  confirmPublishPayment: () => void | Promise<void>
  pendingAlipayTaskId: number | null
  syncTaskAlipayPayment: (taskId?: number | null) => void | Promise<unknown>
  preparePublishAlipayState: (autoSync?: boolean) => void | Promise<void>
  resubmitRejectedTask: () => void | Promise<void>
  submitTask: () => void | Promise<void>
}
