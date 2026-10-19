import { computed, nextTick, ref, watch, type Ref } from 'vue'
import { BUILDING_OPTIONS as buildingOptions, CLASSROOM_BUILDING_SET, buildRoomOptions } from '../../constants/buildings'
import {
  defaultDeliveryEndAt,
  defaultDeliveryStartAt,
  defaultLatestDeliveryAt,
  formatDateTimeLocal,
  roundUpToStepMinutes,
  timeText,
  toDateKey
} from '../../utils/datetimeLocal'
import { apiOrigin } from '../../constants/api'
import { elFromTemplateRef } from '../../utils/domRef'

type PublishRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type PublishDomainDeps = {
  isLoggedIn: Ref<boolean>
  myName: Ref<string>
  submitting: Ref<boolean>
  /** 延迟赋值：request / 钱包余额 / 手机号 / 加急配额 */
  request: PublishRequest
  fetchWalletBalance: () => void | Promise<void>
  walletBalance: () => number
  contactPhone: () => string
  fetchUrgentQuota: () => void | Promise<void>
}

/**
 * 发布页：表单 UI + 支付弹层/券报价。
 * submitTask / confirmPublishPayment / 支付宝待支付草稿仍归主状态。
 */
export function createPublishDomain(deps: PublishDomainDeps) {
  let publishFormHintTimer: number | null = null

  /** 发布页成功/失败提示（大厅不展示 errorMsg，避免与发布页混淆） */
  const publishFormHint = ref<{ type: 'success' | 'error'; text: string } | null>(null)

  const urgentEnabled = ref(false)
  const urgentFee = ref<number | string>(2)
  const urgentFeePresets = [1, 2, 3, 5] as const
  const urgentRemaining = ref(3)
  const urgentWeeklyLimit = ref(3)

  function parseUrgentFee(raw: unknown = urgentFee.value): { ok: boolean; fee: number; message?: string } {
    const fee = Number(raw)
    if (!Number.isFinite(fee)) return { ok: false, fee: 0, message: '请填写加急费' }
    if (fee < 1 || fee > 20) return { ok: false, fee: 0, message: '加急费须在 ¥1～¥20' }
    const steps = fee / 0.5
    if (Math.abs(steps - Math.round(steps)) > 1e-6) {
      return { ok: false, fee: 0, message: '加急费须以 0.5 元为步进' }
    }
    return { ok: true, fee: Math.round(fee * 100) / 100 }
  }

  const urgentFeeParsed = computed(() => parseUrgentFee(urgentFee.value))
  const urgentFeeValid = computed(() => urgentEnabled.value && urgentFeeParsed.value.ok)
  const urgentFeeError = computed(() => {
    if (!urgentEnabled.value || urgentFeeParsed.value.ok) return ''
    return urgentFeeParsed.value.message || '加急费金额无效'
  })
  const urgentRiderBonus = computed(() =>
    urgentFeeValid.value ? Math.round(urgentFeeParsed.value.fee * 0.8 * 100) / 100 : 0
  )
  const urgentPushFee = computed(() =>
    urgentFeeValid.value
      ? Math.round((urgentFeeParsed.value.fee - urgentRiderBonus.value) * 100) / 100
      : 0
  )

  function scrollPublishValidationIntoView(message: string) {
    void nextTick(() => {
      const urgentRelated = /加急/.test(message)
      const el = document.querySelector(
        urgentRelated ? '[data-publish-focus="urgent"]' : '[data-publish-focus="hint"]'
      ) as HTMLElement | null
      el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      if (urgentRelated) {
        const input = document.querySelector('.publish-urgent-fee-input') as HTMLInputElement | null
        input?.focus({ preventScroll: true })
      }
    })
  }

  function togglePublishUrgent() {
    if (!urgentEnabled.value && urgentRemaining.value <= 0) {
      setPublishFormHint({ type: 'error', text: '本周加急次数已用完' }, 5000)
      return
    }
    urgentEnabled.value = !urgentEnabled.value
  }

  function pickUrgentFee(fee: number) {
    if (!Number.isFinite(fee)) return
    urgentFee.value = fee
  }

  function resetPublishUrgent() {
    urgentEnabled.value = false
    urgentFee.value = 2
  }

  function clearPublishFormHintTimer() {
    if (publishFormHintTimer) {
      window.clearTimeout(publishFormHintTimer)
      publishFormHintTimer = null
    }
  }

  /** 设置发布页提示；success 约 5s、error 约 8s 后自动消失 */
  function setPublishFormHint(
    hint: { type: 'success' | 'error'; text: string } | null,
    autoHideMs?: number
  ) {
    clearPublishFormHintTimer()
    publishFormHint.value = hint
    if (hint && autoHideMs != null && autoHideMs > 0) {
      publishFormHintTimer = window.setTimeout(() => {
        publishFormHint.value = null
        publishFormHintTimer = null
      }, autoHideMs)
    }
  }

  const DELIVERY_START_HOUR = 8
  const DELIVERY_END_HOUR = 22
  const DELIVERY_END_MINUTE = 30

  const form = ref({
    taskType: 'EXPRESS',
    title: '',
    pickupLocation: '',
    pickupLat: null as number | null,
    pickupLng: null as number | null,
    dropoffLocation: '',
    dropoffLat: null as number | null,
    dropoffLng: null as number | null,
    reward: 3,
    publisherName: '',
    contact: '',
    description: '',
    deliveryStartAt: defaultDeliveryStartAt(),
    deliveryEndAt: defaultDeliveryEndAt(),
    imageUrls: [] as string[],
    latestDeliveryAt: defaultLatestDeliveryAt()
  })

  const selectedDeliveryDate = ref(toDateKey(new Date()))
  const latestDeliveryQuickOption = ref('')
  const deliveryDateOptions = computed(() => {
    const now = new Date()
    const list: Array<{ value: string; label: string }> = []
    for (let i = 0; i < 3; i += 1) {
      const d = new Date(now)
      d.setDate(now.getDate() + i)
      const key = toDateKey(d)
      list.push({
        value: key,
        label: i === 0 ? '今天' : i === 1 ? '明天' : `后天 (${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')})`
      })
    }
    return list
  })
  const deliveryQuickOptions = computed(() => {
    const now = new Date()
    const todayKey = toDateKey(now)
    const options: Array<{ value: string; label: string }> = []
    if (selectedDeliveryDate.value === todayKey) {
      const end = new Date(now)
      end.setHours(DELIVERY_END_HOUR, DELIVERY_END_MINUTE, 0, 0)

      const immediate = new Date(Date.now() + 30 * 60 * 1000)
      if (immediate <= end) {
        options.push({
          value: timeText(immediate),
          label: `立即送出（约${timeText(immediate)}送达）`
        })
      }

      const floorStart = new Date(now)
      floorStart.setHours(DELIVERY_START_HOUR, 0, 0, 0)
      const startBase = new Date(Math.max(floorStart.getTime(), Date.now() + 40 * 60 * 1000))
      let cursor = roundUpToStepMinutes(startBase, 10)
      while (cursor <= end) {
        const t = timeText(cursor)
        options.push({ value: t, label: t })
        cursor = new Date(cursor.getTime() + 10 * 60 * 1000)
      }
      return options
    }

    const base = new Date(`${selectedDeliveryDate.value}T${String(DELIVERY_START_HOUR).padStart(2, '0')}:00`)
    const end = new Date(`${selectedDeliveryDate.value}T${String(DELIVERY_END_HOUR).padStart(2, '0')}:${String(DELIVERY_END_MINUTE).padStart(2, '0')}`)
    for (let t = new Date(base); t <= end; t = new Date(t.getTime() + 10 * 60 * 1000)) {
      const text = timeText(t)
      options.push({ value: text, label: text })
    }
    const selectedTime = form.value.latestDeliveryAt?.slice(11, 16)
    if (selectedTime && !options.some((opt) => opt.value === selectedTime)) {
      options.unshift({ value: selectedTime, label: `${selectedTime}（自定义）` })
    }
    return options
  })
  const estimateLatestDeliveryText = computed(() => {
    const raw = form.value.latestDeliveryAt
    if (!raw) return ''
    const date = new Date(raw)
    if (Number.isNaN(date.getTime())) return ''
    const hh = String(date.getHours()).padStart(2, '0')
    const mm = String(date.getMinutes()).padStart(2, '0')
    return `预计最晚 ${hh}:${mm} 送达`
  })
  const selectedDeliverySummary = computed(() => {
    const dateLabel =
      deliveryDateOptions.value.find((d) => d.value === selectedDeliveryDate.value)?.label || selectedDeliveryDate.value
    const timeLabel =
      deliveryQuickOptions.value.find((t) => t.value === latestDeliveryQuickOption.value)?.label ||
      latestDeliveryQuickOption.value
    if (!timeLabel) return dateLabel
    return `${dateLabel} · ${timeLabel}`
  })
  const showDeliveryPicker = ref(false)

  function setSelectedDeliveryDate(value: string) {
    selectedDeliveryDate.value = value
    const first = deliveryQuickOptions.value[0]
    if (!first) return
    setLatestDeliveryQuickOption(first.value)
  }

  function setLatestDeliveryQuickOption(value: string) {
    latestDeliveryQuickOption.value = value
    if (!value) return
    const deadline = `${selectedDeliveryDate.value}T${value}`
    form.value.latestDeliveryAt = deadline
    form.value.deliveryEndAt = deadline
    const endTs = Date.parse(deadline)
    if (Number.isFinite(endTs)) {
      form.value.deliveryStartAt = formatDateTimeLocal(new Date(endTs - 60 * 60 * 1000))
    }
  }

  function openDeliveryPicker() {
    if (!deps.isLoggedIn.value) return
    syncDeliverySelectionFromLatestDeliveryAt()
    showDeliveryPicker.value = true
  }

  function closeDeliveryPicker() {
    showDeliveryPicker.value = false
  }

  function resetDeliverySelectionToDefault() {
    selectedDeliveryDate.value = toDateKey(new Date())
    const first = deliveryQuickOptions.value[0]
    if (!first) return
    setLatestDeliveryQuickOption(first.value)
    showDeliveryPicker.value = false
  }

  function syncDeliverySelectionFromLatestDeliveryAt() {
    const raw = form.value.latestDeliveryAt
    const datePart = raw?.slice(0, 10) || toDateKey(new Date())
    selectedDeliveryDate.value = datePart
    const timePart = raw?.slice(11, 16) || deliveryQuickOptions.value[0]?.value || ''
    if (timePart) {
      setLatestDeliveryQuickOption(timePart)
    }
  }

  resetDeliverySelectionToDefault()

  const pickupBuilding = ref('')
  const pickupRoom = ref('')
  const pickupManualInput = ref('')
  const showPickupCascader = ref(false)
  const pickupCascaderRef = ref<HTMLElement | null>(null)

  const dropoffBuilding = ref('')
  const dropoffRoom = ref('')
  const dropoffManualInput = ref('')
  const showDropoffCascader = ref(false)
  const dropoffCascaderRef = ref<HTMLElement | null>(null)
  const publishImageInputRef = ref<HTMLInputElement | null>(null)

  const isClassroomBuilding = computed(() => CLASSROOM_BUILDING_SET.has(pickupBuilding.value))

  const roomOptions = computed(() => buildRoomOptions(pickupBuilding.value))
  const dropoffRoomOptions = computed(() => buildRoomOptions(dropoffBuilding.value))

  const pickupSelectedText = computed(() => {
    if (pickupBuilding.value && pickupRoom.value) {
      return `${pickupBuilding.value} ${pickupRoom.value}`
    }
    return ''
  })

  const pickupDisplayText = computed(() => {
    if (pickupManualInput.value.trim()) {
      return pickupManualInput.value
    }
    if (pickupSelectedText.value) {
      return pickupSelectedText.value
    }
    if (pickupBuilding.value) {
      return `${pickupBuilding.value}（请选择${isClassroomBuilding.value ? '教室' : '宿舍'}号）`
    }
    return ''
  })

  const isDropoffClassroomBuilding = computed(() => CLASSROOM_BUILDING_SET.has(dropoffBuilding.value))
  const dropoffSelectedText = computed(() => {
    if (dropoffBuilding.value && dropoffRoom.value) {
      return `${dropoffBuilding.value} ${dropoffRoom.value}`
    }
    return ''
  })

  const dropoffDisplayText = computed(() => {
    if (dropoffManualInput.value.trim()) {
      return dropoffManualInput.value
    }
    if (dropoffSelectedText.value) {
      return dropoffSelectedText.value
    }
    if (dropoffBuilding.value) {
      return `${dropoffBuilding.value}（请选择${isDropoffClassroomBuilding.value ? '教室' : '宿舍'}号）`
    }
    return ''
  })

  watch(pickupBuilding, () => {
    pickupRoom.value = ''
    if (!pickupManualInput.value.trim()) {
      form.value.pickupLocation = ''
    }
  })

  watch([pickupBuilding, pickupRoom], () => {
    if (pickupBuilding.value && pickupRoom.value) {
      const selected = `${pickupBuilding.value} ${pickupRoom.value}`
      pickupManualInput.value = selected
      form.value.pickupLocation = selected
      showPickupCascader.value = false
    } else if (!pickupManualInput.value.trim()) {
      form.value.pickupLocation = ''
    }
  })

  watch(pickupManualInput, (val) => {
    if (val.trim()) {
      pickupBuilding.value = ''
      pickupRoom.value = ''
      form.value.pickupLocation = val.trim()
    } else if (pickupBuilding.value && pickupRoom.value) {
      form.value.pickupLocation = `${pickupBuilding.value} ${pickupRoom.value}`
    }
  })

  watch(dropoffBuilding, () => {
    dropoffRoom.value = ''
    if (!dropoffManualInput.value.trim()) {
      form.value.dropoffLocation = ''
    }
  })

  watch([dropoffBuilding, dropoffRoom], () => {
    if (dropoffBuilding.value && dropoffRoom.value) {
      const selected = `${dropoffBuilding.value} ${dropoffRoom.value}`
      dropoffManualInput.value = selected
      form.value.dropoffLocation = selected
      showDropoffCascader.value = false
    } else if (!dropoffManualInput.value.trim()) {
      form.value.dropoffLocation = ''
    }
  })

  watch(dropoffManualInput, (val) => {
    if (val.trim()) {
      dropoffBuilding.value = ''
      dropoffRoom.value = ''
      form.value.dropoffLocation = val.trim()
    } else if (dropoffBuilding.value && dropoffRoom.value) {
      form.value.dropoffLocation = `${dropoffBuilding.value} ${dropoffRoom.value}`
    }
  })

  watch(
    () => form.value.deliveryEndAt,
    (v) => {
      if (v) {
        form.value.latestDeliveryAt = v
      }
    }
  )

  function focusPickupCascader() {
    if (!deps.isLoggedIn.value) return
    showPickupCascader.value = true
  }

  function focusDropoffCascader() {
    if (!deps.isLoggedIn.value) return
    showDropoffCascader.value = true
  }

  function onPublishImagesSelected(event: Event) {
    const input = event.target as HTMLInputElement
    const files = Array.from(input.files || [])
    if (files.length === 0) return
    const remain = Math.max(0, 6 - form.value.imageUrls.length)
    const picked = files.slice(0, remain)
    picked.forEach((file) => {
      if (!file.type.startsWith('image/')) return
      const reader = new FileReader()
      reader.onload = () => {
        const url = String(reader.result || '')
        if (url && form.value.imageUrls.length < 6) {
          form.value.imageUrls.push(url)
        }
      }
      reader.readAsDataURL(file)
    })
    input.value = ''
  }

  function removePublishImage(idx: number) {
    form.value.imageUrls.splice(idx, 1)
  }

  /** 发布页根节点 ref：勿用 :ref="r.pickupCascaderRef"，reactive 内 Ref 在子组件模板会被解包，父级 .value 永远接不到 DOM */
  function setPickupCascaderRootEl(el: unknown) {
    pickupCascaderRef.value = elFromTemplateRef(el)
  }

  function setDropoffCascaderRootEl(el: unknown) {
    dropoffCascaderRef.value = elFromTemplateRef(el)
  }


  const publishPaymentMethod = ref<'alipay' | 'wechat' | 'wallet'>('alipay')
  const showPublishPaymentSheet = ref(false)
  const publishPaymentSheetMode = ref<'create' | 'resubmit'>('create')
  const publishPayCoupons = ref<Array<Record<string, unknown>>>([])
  const publishSelectedCouponId = ref<number | null>(null)
  const publishCouponDiscount = ref(0)
  const publishBountyPayable = ref(Number.NaN)
  const publishCouponLoading = ref(false)

  const publishPayableTotal = computed(() => {
    const reward = Number(form.value.reward || 0)
    const bounty = Number.isFinite(reward) && reward > 0 ? reward : 0
    const fee = urgentFeeValid.value ? urgentFeeParsed.value.fee : 0
    const bountyPayable =
      publishSelectedCouponId.value != null && Number.isFinite(publishBountyPayable.value)
        ? Math.max(0, publishBountyPayable.value)
        : bounty
    return Math.round((bountyPayable + fee) * 100) / 100
  })

  const publishWalletPayDisabled = computed(() => {
    const total = publishPayableTotal.value
    if (!Number.isFinite(total) || total <= 0) return true
    if (urgentEnabled.value && !urgentFeeParsed.value.ok) return true
    return Number(deps.walletBalance() || 0) + 1e-9 < total
  })

  function buildPublishTaskBody(extra: Record<string, unknown> = {}) {
    const body: Record<string, unknown> = {
      ...form.value,
      ...extra,
      urgentEnabled: !!urgentEnabled.value
    }
    if (urgentEnabled.value) {
      body.urgentFee = parseUrgentFee().fee
    }
    if (publishSelectedCouponId.value != null) {
      body.userCouponId = publishSelectedCouponId.value
    }
    return body
  }

  function publishCouponOptionLabel(item: Record<string, unknown>) {
    const name = String(item.name || '优惠券')
    const type = String(item.type || '')
    if (type === 'FIXED') return `${name} · 立减¥${Number(item.discountAmount || 0).toFixed(2)}`
    if (type === 'THRESHOLD') {
      return `${name} · 满${Number(item.minSpend || 0).toFixed(0)}减${Number(item.discountAmount || 0).toFixed(0)}`
    }
    if (type === 'PERCENT') {
      const zhe = (Number(item.discountPercent || 0) * 10).toFixed(1).replace(/\.0$/, '')
      return `${name} · ${zhe}折`
    }
    return name
  }

  function resetPublishCouponSelection() {
    publishSelectedCouponId.value = null
    publishCouponDiscount.value = 0
    publishBountyPayable.value = NaN
  }

  async function loadPublishPayCoupons() {
    if (!deps.isLoggedIn.value) {
      publishPayCoupons.value = []
      resetPublishCouponSelection()
      return
    }
    publishCouponLoading.value = true
    try {
      const rows = await deps.request<Array<Record<string, unknown>>>(
        `${apiOrigin}/api/coupons/mine?status=UNUSED`
      )
      publishPayCoupons.value = Array.isArray(rows) ? rows : []
      if (
        publishSelectedCouponId.value != null &&
        !publishPayCoupons.value.some((c) => Number(c.id) === publishSelectedCouponId.value)
      ) {
        resetPublishCouponSelection()
      }
      await refreshPublishCouponQuote()
    } catch {
      publishPayCoupons.value = []
      resetPublishCouponSelection()
    } finally {
      publishCouponLoading.value = false
    }
  }

  async function refreshPublishCouponQuote() {
    const reward = Number(form.value.reward || 0)
    if (!publishSelectedCouponId.value || !Number.isFinite(reward) || reward <= 0) {
      publishCouponDiscount.value = 0
      publishBountyPayable.value = NaN
      return
    }
    try {
      const quote = await deps.request<{
        applicable?: boolean
        discount?: number
        payable?: number
        reason?: string
      }>(`${apiOrigin}/api/coupons/preview`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userCouponId: publishSelectedCouponId.value,
          reward
        })
      })
      if (!quote || quote.applicable === false) {
        resetPublishCouponSelection()
        setPublishFormHint(
          { type: 'error', text: quote?.reason || '该券不可用于当前赏金' },
          6000
        )
        return
      }
      publishCouponDiscount.value = Math.max(0, Number(quote.discount || 0))
      publishBountyPayable.value = Number(
        quote.payable != null ? quote.payable : reward - publishCouponDiscount.value
      )
    } catch (err) {
      resetPublishCouponSelection()
      setPublishFormHint({ type: 'error', text: (err as Error).message || '优惠券不可用' }, 6000)
    }
  }

  async function onPublishCouponSelected(raw: string) {
    const id = Number(raw)
    if (!raw || !Number.isFinite(id) || id <= 0) {
      resetPublishCouponSelection()
      return
    }
    publishSelectedCouponId.value = id
    await refreshPublishCouponQuote()
  }

  function syncPublishFormFields() {
    if (pickupManualInput.value.trim()) {
      form.value.pickupLocation = pickupManualInput.value.trim()
    }
    if (dropoffManualInput.value.trim()) {
      form.value.dropoffLocation = dropoffManualInput.value.trim()
    }
    form.value.publisherName = deps.myName.value.trim()
    // 联系方式固定用注册手机号，不允许手填
    form.value.contact = String(deps.contactPhone() || "").trim()
  }

  function validatePublishFormBeforePay(): string | null {
    syncPublishFormFields()
    if (!form.value.title?.trim()) {
      return '请填写任务标题'
    }
    if (!form.value.pickupLocation?.trim()) {
      return '请填写取货地点'
    }
    if (!form.value.dropoffLocation?.trim()) {
      return '请填写送达地点'
    }
    const reward = Number(form.value.reward)
    if (!Number.isFinite(reward) || reward < 0.1) {
      return '赏金至少 0.1 元'
    }
    if (reward > 9999.9) {
      return '赏金不能超过 9999.9 元'
    }
    const tenths = Math.round(reward * 10)
    if (Math.abs(reward * 10 - tenths) > 1e-8) {
      return '赏金只能精确到 0.1 元（一位小数）'
    }
    form.value.reward = tenths / 10
    if (!form.value.contact?.trim()) {
      return '账号未绑定手机号，请先完善资料'
    }
    if (!form.value.latestDeliveryAt && !form.value.deliveryEndAt) {
      return '请选择配送时间'
    }
    if (urgentEnabled.value) {
      if (urgentRemaining.value <= 0) return '本周加急次数已用完'
      const urgent = parseUrgentFee()
      if (!urgent.ok) return urgent.message || '加急费金额无效'
    }
    return null
  }

  function openPublishPaymentSheet(mode: 'create' | 'resubmit') {
    deps.submitting.value = false
    publishPaymentMethod.value = 'alipay'
    publishPaymentSheetMode.value = mode
    resetPublishCouponSelection()
    showPublishPaymentSheet.value = true
    if (deps.isLoggedIn.value) {
      void deps.fetchWalletBalance()
      void loadPublishPayCoupons()
    }
  }

  function closePublishPaymentSheet() {
    showPublishPaymentSheet.value = false
  }

  function resetPublishFormAfterSuccess() {
    form.value = {
      taskType: 'EXPRESS',
      title: '',
      pickupLocation: '',
      pickupLat: null,
      pickupLng: null,
      dropoffLocation: '',
      dropoffLat: null,
      dropoffLng: null,
      reward: 3,
      publisherName: deps.myName.value.trim(),
      contact: String(deps.contactPhone() || "").trim(),
      description: '',
      deliveryStartAt: defaultDeliveryStartAt(),
      deliveryEndAt: defaultDeliveryEndAt(),
      imageUrls: [],
      latestDeliveryAt: defaultLatestDeliveryAt()
    }
    resetDeliverySelectionToDefault()
    pickupBuilding.value = ''
    pickupRoom.value = ''
    pickupManualInput.value = ''
    dropoffBuilding.value = ''
    dropoffRoom.value = ''
    dropoffManualInput.value = ''
    resetPublishUrgent()
    resetPublishCouponSelection()
    void deps.fetchUrgentQuota()
  }

  return {
    publishFormHint,
    setPublishFormHint,
    clearPublishFormHintTimer,
    urgentEnabled,
    urgentFee,
    urgentFeePresets,
    urgentRemaining,
    urgentWeeklyLimit,
    parseUrgentFee,
    urgentFeeParsed,
    urgentFeeValid,
    urgentFeeError,
    urgentRiderBonus,
    urgentPushFee,
    scrollPublishValidationIntoView,
    togglePublishUrgent,
    pickUrgentFee,
    resetPublishUrgent,
    form,
    selectedDeliveryDate,
    latestDeliveryQuickOption,
    deliveryDateOptions,
    deliveryQuickOptions,
    estimateLatestDeliveryText,
    selectedDeliverySummary,
    showDeliveryPicker,
    setSelectedDeliveryDate,
    setLatestDeliveryQuickOption,
    openDeliveryPicker,
    closeDeliveryPicker,
    resetDeliverySelectionToDefault,
    syncDeliverySelectionFromLatestDeliveryAt,
    pickupBuilding,
    pickupRoom,
    pickupManualInput,
    showPickupCascader,
    pickupCascaderRef,
    dropoffBuilding,
    dropoffRoom,
    dropoffManualInput,
    showDropoffCascader,
    dropoffCascaderRef,
    publishImageInputRef,
    isClassroomBuilding,
    roomOptions,
    dropoffRoomOptions,
    pickupDisplayText,
    isDropoffClassroomBuilding,
    dropoffDisplayText,
    focusPickupCascader,
    focusDropoffCascader,
    onPublishImagesSelected,
    removePublishImage,
    setPickupCascaderRootEl,
    setDropoffCascaderRootEl,
    pickupSelectedText,
    dropoffSelectedText,
    buildingOptions,
    publishPaymentMethod,
    showPublishPaymentSheet,
    publishPaymentSheetMode,
    publishPayCoupons,
    publishSelectedCouponId,
    publishCouponDiscount,
    publishBountyPayable,
    publishCouponLoading,
    publishPayableTotal,
    publishWalletPayDisabled,
    buildPublishTaskBody,
    publishCouponOptionLabel,
    resetPublishCouponSelection,
    loadPublishPayCoupons,
    refreshPublishCouponQuote,
    onPublishCouponSelected,
    syncPublishFormFields,
    validatePublishFormBeforePay,
    openPublishPaymentSheet,
    closePublishPaymentSheet,
    resetPublishFormAfterSuccess
  }
}
