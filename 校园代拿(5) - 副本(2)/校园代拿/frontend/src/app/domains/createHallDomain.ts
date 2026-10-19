import { computed, nextTick, ref, watch, type Ref } from 'vue'
import { apiBase, apiOrigin, authBase } from '../../constants/api'
import type {
  NoticeItem,
  PageView,
  TaskItem,
  TaskPageResponse,
  TaskStatus,
  UserPublicProfile
} from '../../types/app'
import { formatNetworkErrorMessage } from '../../utils/http'
import { formatTaskTimeAgo, maskName } from '../../utils/displayFormat'
import {
  hallStatusLabel,
  hallTaskCategoryLabel,
  hallTaskDistance,
  hallTaskEmoji,
  TASK_TYPE_OPTIONS,
  taskTypeLabel
} from '../../utils/hallDisplay'
import { isTaskUrgent, taskUrgentRiderBonus } from '../../utils/taskUrgent'
import { fetchCampusWeather } from '../../utils/campusWeather'

type HallRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type HallDomainDeps = {
  publicNotices: Ref<NoticeItem[]>
  currentView: Ref<PageView>
  isLoggedIn: Ref<boolean>
  setView: (view: PageView) => void
  tasks: Ref<TaskItem[]>
  allTasks: Ref<TaskItem[]>
  loading: Ref<boolean>
  errorMsg: Ref<string>
  myName: Ref<string>
  myAvatarUrl: Ref<string>
  /** 以下可延迟赋值（request / 登录页 / 钱包券入口） */
  request: HallRequest
  openAuthPage: (mode: 'login' | 'register') => void
  showProfileNotice: (text: string) => void
  goWallet: () => void | Promise<void>
  goCoupons: () => void | Promise<void>
}

type HeroPromoSlide = {
  key: string
  eyebrow: string
  title: string
  subtitle: string
  isPromo: boolean
  ctaText?: string
  actionType?: string
  claimed?: boolean
  templateId?: number
  bannerTheme?: string
  bannerImageUrl?: string
  softBanner?: boolean
  raw?: Record<string, unknown>
}

/**
 * 大厅 UI：公告条 + hero 活动轮播 + 筛选 / 客户端列表整形。
 * fetchTasks / claimTask 等仍归主状态（可后续注入）。
 */
export function createHallDomain(deps: HallDomainDeps) {
  const hallNoticeExpanded = ref(false)
  const hallNoticeActiveId = ref<number | null>(null)
  const noticeBarVisible = ref(true)
  const noticeTrackRef = ref<HTMLElement | null>(null)
  const noticeMeasureRef = ref<HTMLElement | null>(null)
  const noticeMarqueeActive = ref(false)
  const hallNoticeCarouselIndex = ref(0)
  let noticeTrackResizeObserver: ResizeObserver | null = null
  let noticeCarouselTimer: number | null = null
  let noticeCarouselPaused = false
  const NOTICE_CAROUSEL_INTERVAL = 6000

  function formatNoticeBannerText(n: NoticeItem) {
    const content = String(n.content || '').replace(/\s+/g, ' ').slice(0, 60)
    return `${n.title}${content ? ` · ${content}` : ''}`
  }

  function updateNoticeMarquee() {
    const track = noticeTrackRef.value
    const measure = noticeMeasureRef.value
    if (!track || !measure || !noticeBarVisible.value || deps.currentView.value !== 'hall') {
      noticeMarqueeActive.value = false
      return
    }
    noticeMarqueeActive.value = measure.scrollWidth > track.clientWidth + 2
  }

  const hasMultipleHallNotices = computed(() => deps.publicNotices.value.length > 1)

  const hallNoticeBannerText = computed(() => {
    const list = deps.publicNotices.value
    if (list.length === 0) return ''
    const idx = Math.min(hallNoticeCarouselIndex.value, list.length - 1)
    return formatNoticeBannerText(list[idx])
  })

  const activeHallNoticeId = computed(() => {
    const list = deps.publicNotices.value
    if (list.length === 0) return 0
    const idx = Math.min(hallNoticeCarouselIndex.value, list.length - 1)
    return list[idx]?.id ?? 0
  })

  const hallTopNotice = computed(() => {
    const list = deps.publicNotices.value || []
    if (list.length === 0) return null
    const idx = Math.min(hallNoticeCarouselIndex.value, list.length - 1)
    return list[idx] || null
  })

  function syncHallNoticeCarouselIndex() {
    const list = deps.publicNotices.value
    if (list.length === 0) {
      hallNoticeCarouselIndex.value = 0
      hallNoticeActiveId.value = null
      return
    }
    if (hallNoticeCarouselIndex.value >= list.length) {
      hallNoticeCarouselIndex.value = 0
    }
    hallNoticeActiveId.value = list[hallNoticeCarouselIndex.value]?.id ?? null
  }

  function stopNoticeCarousel() {
    if (noticeCarouselTimer) {
      window.clearInterval(noticeCarouselTimer)
      noticeCarouselTimer = null
    }
  }

  function startNoticeCarousel() {
    stopNoticeCarousel()
    if (!hasMultipleHallNotices.value || !noticeBarVisible.value || deps.currentView.value !== 'hall') {
      return
    }
    noticeCarouselTimer = window.setInterval(() => {
      if (noticeCarouselPaused) return
      goToHallNotice(hallNoticeCarouselIndex.value + 1)
    }, NOTICE_CAROUSEL_INTERVAL)
  }

  function restartNoticeCarousel() {
    startNoticeCarousel()
  }

  function pauseNoticeCarousel() {
    noticeCarouselPaused = true
  }

  function resumeNoticeCarousel() {
    noticeCarouselPaused = false
  }

  function goToHallNotice(index: number) {
    const list = deps.publicNotices.value
    if (list.length === 0) return
    hallNoticeCarouselIndex.value = ((index % list.length) + list.length) % list.length
    hallNoticeActiveId.value = list[hallNoticeCarouselIndex.value]?.id ?? null
    restartNoticeCarousel()
  }

  function toggleHallNoticeExpanded() {
    hallNoticeExpanded.value = !hallNoticeExpanded.value
  }

  function activateHallNotice(n: NoticeItem) {
    const idx = deps.publicNotices.value.findIndex((item) => item.id === n.id)
    if (idx >= 0) {
      goToHallNotice(idx)
    } else {
      hallNoticeActiveId.value = n.id
    }
    hallNoticeExpanded.value = true
  }

  function onPublicNoticesLoaded() {
    if (deps.publicNotices.value.length === 0) {
      hallNoticeActiveId.value = null
      hallNoticeExpanded.value = false
      hallNoticeCarouselIndex.value = 0
      stopNoticeCarousel()
      return
    }
    syncHallNoticeCarouselIndex()
    startNoticeCarousel()
  }

  function setupNoticeMarqueeObserver() {
    nextTick(() => {
      updateNoticeMarquee()
      if (typeof ResizeObserver === 'undefined') return
      noticeTrackResizeObserver?.disconnect()
      noticeTrackResizeObserver = null
      if (noticeTrackRef.value) {
        noticeTrackResizeObserver = new ResizeObserver(() => updateNoticeMarquee())
        noticeTrackResizeObserver.observe(noticeTrackRef.value)
      }
      if (deps.currentView.value === 'hall' && noticeBarVisible.value && hasMultipleHallNotices.value) {
        startNoticeCarousel()
      } else {
        stopNoticeCarousel()
      }
    })
  }

  watch([hallNoticeBannerText, hallNoticeCarouselIndex, noticeBarVisible, deps.currentView], () => {
    setupNoticeMarqueeObserver()
  })

  // —— hero 活动轮播 ——
  const promoActivities = ref<Array<Record<string, unknown>>>([])
  const heroPromoIndex = ref(0)
  const claimingHeroPromoId = ref<string | number | null>(null)
  let heroPromoCarouselTimer: number | null = null
  let heroPromoCarouselPaused = false
  const HERO_PROMO_INTERVAL = 4500

  const HERO_PROMO_FALLBACK: HeroPromoSlide = {
    key: 'fallback',
    eyebrow: 'Campus Micro Tasks',
    title: '把零碎时间，换成一点小收入',
    subtitle: '「刚好顺路、刚好有空」的小请求——搬东西、代买、打印、占座。',
    isPromo: false,
    bannerTheme: 'warm',
    bannerImageUrl: '/hall-hero-fallback.png',
    softBanner: true
  }

  const heroPromoSlides = computed<HeroPromoSlide[]>(() => {
    const list = promoActivities.value
    if (!Array.isArray(list) || list.length === 0) return [HERO_PROMO_FALLBACK]
    return list.map((act, idx) => {
      const id = act.id ?? act.templateId ?? idx
      const webImg = String(act.bannerImageUrlWeb || '').trim()
      return {
        key: String(id),
        eyebrow: '活动推荐',
        title: String(act.title || '校园互助活动'),
        subtitle: String(act.subtitle || '点击参与活动'),
        isPromo: true,
        ctaText: act.ctaText != null ? String(act.ctaText) : undefined,
        actionType: act.actionType != null ? String(act.actionType) : undefined,
        claimed: !!act.claimed,
        templateId: Number(act.templateId || act.couponTemplateId) || undefined,
        bannerTheme: String(act.bannerTheme || act.theme || 'warm'),
        bannerImageUrl: webImg || undefined,
        raw: act
      }
    })
  })

  const activeHeroPromo = computed(() => {
    const slides = heroPromoSlides.value
    if (slides.length === 0) return HERO_PROMO_FALLBACK
    const idx = ((heroPromoIndex.value % slides.length) + slides.length) % slides.length
    return slides[idx] || HERO_PROMO_FALLBACK
  })

  const hasMultipleHeroPromos = computed(() => heroPromoSlides.value.length > 1)

  function stopHeroPromoCarousel() {
    if (heroPromoCarouselTimer) {
      window.clearInterval(heroPromoCarouselTimer)
      heroPromoCarouselTimer = null
    }
  }

  function startHeroPromoCarousel() {
    stopHeroPromoCarousel()
    if (!hasMultipleHeroPromos.value || deps.currentView.value !== 'hall') return
    heroPromoCarouselTimer = window.setInterval(() => {
      if (heroPromoCarouselPaused) return
      goToHeroPromo(heroPromoIndex.value + 1)
    }, HERO_PROMO_INTERVAL)
  }

  function pauseHeroPromoCarousel() {
    heroPromoCarouselPaused = true
  }

  function resumeHeroPromoCarousel() {
    heroPromoCarouselPaused = false
  }

  function goToHeroPromo(index: number) {
    const len = heroPromoSlides.value.length
    if (len === 0) return
    heroPromoIndex.value = ((index % len) + len) % len
    startHeroPromoCarousel()
  }

  function heroPromoCtaText(slide: HeroPromoSlide) {
    const type = String(slide.actionType || '')
    if (type === 'COUPON_CLAIM' && slide.claimed) return '已领取'
    if (claimingHeroPromoId.value != null) return '打开中…'
    if (slide.ctaText) return slide.ctaText
    if (type === 'RIDER_APPLY') return '立即认证'
    if (type === 'PUBLISH') return '去发布'
    if (type === 'WALLET') return '看钱包'
    if (type === 'COUPON_CENTER') return '去领券'
    return deps.isLoggedIn.value ? '去看看' : '登录后查看'
  }

  function hallActivityNavigateWeb(act: Record<string, unknown>) {
    const type = String(act.actionType || '')
    if (type === 'RIDER_APPLY') {
      deps.setView('riderApply')
      return
    }
    if (type === 'PUBLISH') {
      deps.setView('publish')
      return
    }
    if (type === 'WALLET') {
      void deps.goWallet()
      return
    }
    if (type === 'COUPON_CENTER' || type === 'COUPON_CLAIM') {
      void deps.goCoupons()
      return
    }
    deps.showProfileNotice('请在小程序中打开该活动')
  }

  async function fetchPromoActivities() {
    try {
      const rows = await deps.request<Array<Record<string, unknown>>>(`${apiOrigin}/api/hall-activities`)
      promoActivities.value = Array.isArray(rows) ? rows : []
      if (heroPromoIndex.value >= promoActivities.value.length) {
        heroPromoIndex.value = 0
      }
      if (deps.currentView.value === 'hall') startHeroPromoCarousel()
    } catch {
      promoActivities.value = []
      heroPromoIndex.value = 0
      stopHeroPromoCarousel()
    }
  }

  async function claimPromoActivity(act: {
    id?: string | number
    templateId?: number
    couponTemplateId?: number
    claimed?: boolean
    actionType?: string
    action?: string
  }) {
    const type = String(act?.actionType || act?.action || '')
    if (type && type !== 'COUPON_CLAIM') {
      if (!deps.isLoggedIn.value && (type === 'RIDER_APPLY' || type === 'WALLET' || type === 'PUBLISH')) {
        deps.openAuthPage('login')
        return
      }
      hallActivityNavigateWeb(act as Record<string, unknown>)
      return
    }
    if (!deps.isLoggedIn.value) {
      deps.openAuthPage('login')
      return
    }
    if (act?.claimed) return
    const templateId = Number(act?.templateId || act?.couponTemplateId)
    try {
      if (Number.isFinite(templateId) && templateId > 0) {
        const res = await deps.request<{ message?: string }>(`${apiOrigin}/api/coupons/claim/${templateId}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({})
        })
        deps.showProfileNotice(res?.message || '领取成功，可在发布任务时使用')
      } else {
        deps.showProfileNotice('该活动未关联优惠券')
      }
      await fetchPromoActivities()
    } catch (err) {
      deps.showProfileNotice((err as Error).message || '领取失败')
      await fetchPromoActivities()
    }
  }

  async function claimHeroPromo() {
    const slide = activeHeroPromo.value
    if (!slide.isPromo || !slide.raw) return
    const type = String(slide.actionType || '')
    if (type === 'COUPON_CLAIM' && slide.claimed) return
    if (claimingHeroPromoId.value != null) return
    claimingHeroPromoId.value = slide.key
    try {
      await claimPromoActivity(slide.raw as {
        id?: string | number
        templateId?: number
        couponTemplateId?: number
        claimed?: boolean
        actionType?: string
        action?: string
      })
    } finally {
      claimingHeroPromoId.value = null
    }
  }

  async function claimNewcomerPack() {
    await claimPromoActivity({ action: 'CLAIM_NEWCOMER' })
  }

  watch(deps.currentView, (view) => {
    if (view === 'hall') {
      void fetchPromoActivities()
      startHeroPromoCarousel()
    } else {
      stopHeroPromoCarousel()
    }
  })

  watch(hasMultipleHeroPromos, (multi) => {
    if (multi && deps.currentView.value === 'hall') startHeroPromoCarousel()
    else if (!multi) stopHeroPromoCarousel()
  })

  watch(deps.isLoggedIn, (loggedIn) => {
    if (loggedIn) void fetchPromoActivities()
  })


  const HALL_ZONES = [
    { value: '', label: '全校' },
    { value: '南门', label: '南门快递区' },
    { value: '图书馆', label: '图书馆' },
    { value: '食堂', label: '一食堂' },
    { value: '商业街', label: '校外商业街' },
    { value: '宿舍', label: '学生宿舍' },
    { value: '教学楼', label: '教学楼群' }
  ] as const

  const filters = ref({
    keyword: '',
    status: 'OPEN' as '' | TaskStatus,
    taskType: '',
    zone: '',
    rewardRange: '' as '' | 'low' | 'mid' | 'high',
    sortMode: 'default' as 'default' | 'new' | 'reward'
  })

  const paging = ref({
    page: 1,
    size: 8,
    totalPages: 1,
    totalElements: 0
  })

  const safeTasks = computed(() => (Array.isArray(deps.tasks.value) ? deps.tasks.value : []))
  const safeAllTasks = computed(() => (Array.isArray(deps.allTasks.value) ? deps.allTasks.value : []))

  function matchHallReward(reward: number) {
    const range = filters.value.rewardRange
    if (!range) return true
    if (range === 'low') return reward < 10
    if (range === 'mid') return reward >= 10 && reward <= 20
    return reward > 20
  }

  function matchHallZone(task: TaskItem) {
    const zone = filters.value.zone.trim()
    if (!zone) return true
    const text = `${task.pickupLocation || ''} ${task.dropoffLocation || ''} ${task.title || ''}`
    return text.includes(zone)
  }

  function sortHallTasks(list: TaskItem[]) {
    const mode = filters.value.sortMode
    const copy = list.slice()
    if (mode === 'new') {
      copy.sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
    } else if (mode === 'reward') {
      copy.sort((a, b) => {
        const totalA = Number(a.reward || 0) + taskUrgentRiderBonus(a)
        const totalB = Number(b.reward || 0) + taskUrgentRiderBonus(b)
        return totalB - totalA
      })
    } else {
      // 推荐：与后端大厅排序一致——加急优先，再按加急费，再按时间
      copy.sort((a, b) => {
        const ua = isTaskUrgent(a) ? 1 : 0
        const ub = isTaskUrgent(b) ? 1 : 0
        if (ub !== ua) return ub - ua
        const fa = isTaskUrgent(a) ? Number(a.urgentFee || 0) : 0
        const fb = isTaskUrgent(b) ? Number(b.urgentFee || 0) : 0
        if (fb !== fa) return fb - fa
        return String(b.createdAt || '').localeCompare(String(a.createdAt || ''))
      })
    }
    return copy
  }

  const visibleTasks = computed(() =>
    sortHallTasks(
      safeTasks.value.filter(
        (t) => t.status === 'OPEN' && matchHallZone(t) && matchHallReward(Number(t.reward || 0))
      )
    )
  )

  const hallStats = computed(() => {
    const open = safeAllTasks.value.filter((t) => t.status === 'OPEN')
    const completed = safeAllTasks.value.filter((t) => t.status === 'COMPLETED')
    const avgReward =
      open.length > 0 ? open.reduce((sum, t) => sum + Number(t.reward || 0), 0) / open.length : 0
    return [
      { label: '待接任务', value: String(open.length), note: '当前大厅可接' },
      { label: '已完成', value: String(completed.length), note: '平台累计' },
      {
        label: '平均赏金',
        value: open.length ? `¥${avgReward.toFixed(1)}` : '—',
        note: '待接任务均值'
      }
    ]
  })

  const taskTypeOptions = computed(() => [...TASK_TYPE_OPTIONS])

  const hotTaskTypes = computed(() => {
    const list = Array.isArray(safeAllTasks.value) && safeAllTasks.value.length > 0 ? safeAllTasks.value : safeTasks.value
    const activeStatuses = new Set<TaskStatus>(['OPEN', 'CLAIMED', 'PENDING_CONFIRMATION', 'OVERDUE'])
    const scoped = list.filter((t) => {
      const st = String(t.status || '').toUpperCase() as TaskStatus
      if (filters.value.status) {
        return st === filters.value.status
      }
      return activeStatuses.has(st)
    })
    const counter = new Map<string, number>()
    for (const t of scoped) {
      const key = String(t.taskType || 'EXPRESS').trim().toUpperCase()
      counter.set(key, (counter.get(key) || 0) + 1)
    }
    return Array.from(counter.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, 4)
      .map(([type, count]) => ({
        type,
        label: taskTypeLabel(type),
        count
      }))
  })

  function selectZoneFilter(zone: string) {
    filters.value.zone = String(zone || '').trim()
  }

  function selectSortMode(mode: 'default' | 'new' | 'reward') {
    filters.value.sortMode = mode
  }

  function applyFilters() {
    paging.value.page = 1
    void fetchTasks()
  }

  function resetFilters() {
    filters.value.keyword = ''
    filters.value.status = 'OPEN'
    filters.value.taskType = ''
    filters.value.zone = ''
    filters.value.rewardRange = ''
    filters.value.sortMode = 'default'
    paging.value.page = 1
    void fetchTasks()
  }

  function goPrevPage() {
    if (paging.value.page <= 1) return
    paging.value.page -= 1
    void fetchTasks()
  }

  function goNextPage() {
    if (paging.value.page >= paging.value.totalPages) return
    paging.value.page += 1
    void fetchTasks()
  }

  function selectTaskTypeFilter(taskType: string) {
    filters.value.taskType = String(taskType || '').trim().toUpperCase()
    applyFilters()
  }

  async function fetchTasks() {
    deps.loading.value = true
    deps.errorMsg.value = ''
    try {
      const params = new URLSearchParams({
        page: String(paging.value.page),
        size: String(paging.value.size)
      })

      if (filters.value.keyword.trim()) {
        params.set('keyword', filters.value.keyword.trim())
      }
      // 大厅只展示正在发布、待接单的任务
      if (deps.currentView.value === 'hall') {
        params.set('status', 'OPEN')
      } else if (filters.value.status) {
        params.set('status', filters.value.status)
      }
      if (filters.value.taskType) {
        params.set('taskType', filters.value.taskType)
      }

      const data = await deps.request<TaskPageResponse | TaskItem[]>(`${apiBase}?${params.toString()}`)

      if (Array.isArray(data)) {
        deps.tasks.value = data
        paging.value.page = 1
        paging.value.size = data.length || paging.value.size
        paging.value.totalPages = 1
        paging.value.totalElements = data.length
      } else {
        deps.tasks.value = Array.isArray(data.records) ? data.records : []
        paging.value.page = data.page ?? 1
        paging.value.size = data.size ?? paging.value.size
        paging.value.totalPages = Math.max(data.totalPages ?? 1, 1)
        paging.value.totalElements = data.totalElements ?? deps.tasks.value.length
      }

      // 大厅只保留当前页；个人中心任务由进页时再拉
    } catch (err) {
      deps.errorMsg.value = formatNetworkErrorMessage(err)
    } finally {
      deps.loading.value = false
    }
  }

  const HERO_CATEGORY_TAGS = [
    { label: '全部', value: '' },
    { label: '跑腿', value: 'EXPRESS' },
    { label: '代买', value: 'FOOD' },
    { label: '打印', value: 'PRINT' },
    { label: '搬运', value: 'OTHER' },
    { label: '排队', value: 'QUEUE' },
    { label: '其他', value: 'OTHER' }
  ] as const

  function scrollToHallTaskList() {
    const el = document.getElementById('hall-task-list')
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }

  async function selectHeroCategoryTag(value: string) {
    if (deps.currentView.value !== 'hall') deps.setView('hall')
    filters.value.taskType = value
    paging.value.page = 1
    await fetchTasks()
    await nextTick()
    scrollToHallTaskList()
  }

  async function applyHeroSearch() {
    if (deps.currentView.value !== 'hall') deps.setView('hall')
    paging.value.page = 1
    await fetchTasks()
    await nextTick()
    scrollToHallTaskList()
  }

  const HALL_FAQ = [
    {
      question: '酬劳什么时候到账？',
      answer: '发布者确认完成后，酬劳会在短时间内进入接单员平台余额，可在「我的钱包」查看与提现。'
    },
    {
      question: '可以取消已接的任务吗？',
      answer:
        '可以，但按阶段不同：已完成不可取消；「待发布方确认」时发布者不能单方取消（有争议请私聊或申诉）；配送中接单员取消会把任务退回大厅；待接单等阶段发布者可取消并按规则退还赏金。普通取消不会自动扣信誉分。'
    },
    {
      question: '如何成为接单员？',
      answer: '登录后点击头像菜单中的「接单员申请」，填写实名信息并等待管理员审核通过。'
    },
    {
      question: '发布任务需要手续费吗？',
      answer: '平台内互助任务无额外手续费，发布时需从钱包预付赏金，任务取消或驳回后会按规则退还。'
    }
  ]

  const hallActivities = computed(() => {
    const source = Array.isArray(safeAllTasks.value) && safeAllTasks.value.length > 0 ? safeAllTasks.value : safeTasks.value
    const items: Array<{ text: string; time: string }> = []
    for (const task of source) {
      if (task.status === 'COMPLETED' && task.claimerName) {
        items.push({
          text: `${task.claimerName} 完成了「${task.title}」`,
          time: formatTaskTimeAgo(task.updatedAt || task.createdAt)
        })
      } else if (task.status === 'OPEN') {
        items.push({
          text: `新任务：${task.title}`,
          time: formatTaskTimeAgo(task.createdAt)
        })
      } else if (task.status === 'CLAIMED' && task.claimerName) {
        items.push({
          text: `${task.claimerName} 已接「${task.title}」`,
          time: formatTaskTimeAgo(task.updatedAt || task.createdAt)
        })
      }
    }
    if (items.length === 0) {
      return [
        { text: '欢迎使用片刻帮校园代拿', time: '刚刚' },
        { text: '登录后可接单、发布与提现', time: '提示' }
      ]
    }
    return items.slice(0, 8)
  })

  const hallLeaderboardAvatarMap = ref<Record<string, string>>({})

  const hallLeaderboardRaw = computed(() => {
    const source = Array.isArray(safeAllTasks.value) && safeAllTasks.value.length > 0 ? safeAllTasks.value : safeTasks.value
    const counter = new Map<string, number>()
    for (const task of source) {
      if (task.status === 'COMPLETED' && task.claimerName) {
        counter.set(task.claimerName, (counter.get(task.claimerName) || 0) + 1)
      }
    }
    const ranked = [...counter.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([name, score]) => ({ name, score, seed: name }))
    if (ranked.length > 0) return ranked
    return [
      { name: '接单达人', score: 48, seed: 'hall-rank-a' },
      { name: '顺路侠', score: 41, seed: 'hall-rank-b' },
      { name: '校园帮', score: 36, seed: 'hall-rank-c' },
      { name: '跑腿王', score: 29, seed: 'hall-rank-d' },
      { name: '热心同学', score: 22, seed: 'hall-rank-e' }
    ]
  })

  const hallLeaderboard = computed(() =>
    hallLeaderboardRaw.value.map((item) => {
      const cached = hallLeaderboardAvatarMap.value[item.name]
      const avatarUrl =
        item.name === deps.myName.value
          ? String(deps.myAvatarUrl.value || cached || '').trim()
          : String(cached || '').trim()
      return { ...item, avatarUrl }
    })
  )

  async function syncHallLeaderboardAvatars(names: string[]) {
    const pending = names.filter((name) => name && !(name in hallLeaderboardAvatarMap.value))
    if (!pending.length) return
    for (const name of pending) {
      hallLeaderboardAvatarMap.value[name] = ''
    }
    await Promise.all(
      pending.map(async (name) => {
        try {
          const profile = await deps.request<UserPublicProfile>(
            `${authBase}/users/${encodeURIComponent(name)}/public-profile`
          )
          hallLeaderboardAvatarMap.value[name] = String(profile.avatarUrl || '').trim()
        } catch {
          hallLeaderboardAvatarMap.value[name] = ''
        }
      })
    )
  }

  function publisherAvatarUrl(name?: string) {
    const key = String(name || '').trim()
    if (!key) return ''
    if (key === deps.myName.value) {
      return String(deps.myAvatarUrl.value || hallLeaderboardAvatarMap.value[key] || '').trim()
    }
    return String(hallLeaderboardAvatarMap.value[key] || '').trim()
  }

  function publisherNameInitial(name?: string) {
    const raw = String(name || '').trim()
    const display = deps.isLoggedIn.value ? raw : maskName(raw)
    const ch = display.replace(/[^\u4e00-\u9fffA-Za-z0-9]/g, '').slice(0, 1)
    return ch || (display || '?').slice(0, 1) || '?'
  }

  watch(
    hallLeaderboardRaw,
    (list) => {
      void syncHallLeaderboardAvatars(list.map((item) => item.name))
    },
    { immediate: true }
  )

  watch(
    visibleTasks,
    (list) => {
      const names = list.map((t) => String(t.publisherName || '').trim()).filter(Boolean)
      void syncHallLeaderboardAvatars(names)
    },
    { immediate: true }
  )

  const hallWeatherValue = ref('🌤️ 加载中…')
  const hallWeatherNote = ref('正在获取实时天气')
  const hallCompletionRateText = ref('—')
  const hallCompletionRateNote = ref('近 30 天履约')
  const hallWeatherLoading = ref(false)
  let hallWeatherTimer: number | null = null

  async function fetchHallCompletionStats() {
    try {
      const data = await deps.request<{
        days?: number
        completionRate?: number | null
        completedCount?: number
        cancelledCount?: number
        settledCount?: number
      }>(`${apiOrigin}/api/tasks/hall-stats?days=30`)
      const rate = data?.completionRate
      const settled = Number(data?.settledCount || 0)
      if (rate == null || !Number.isFinite(Number(rate)) || settled <= 0) {
        hallCompletionRateText.value = '—'
        hallCompletionRateNote.value = '近 30 天暂无完结单'
        return
      }
      const n = Number(rate)
      hallCompletionRateText.value = `${Number.isInteger(n) ? String(n) : n.toFixed(1)}%`
      const completed = Number(data?.completedCount || 0)
      const cancelled = Number(data?.cancelledCount || 0)
      hallCompletionRateNote.value = `完成 ${completed} · 接单后取消 ${cancelled}`
    } catch {
      hallCompletionRateText.value = '—'
      hallCompletionRateNote.value = '统计暂时不可用'
    }
  }

  const heroSideStats = computed(() => {
    const open = safeAllTasks.value.filter((t) => t.status === 'OPEN').length
    return [
      { label: '今日新任务', value: String(open), note: open > 0 ? '比昨日活跃' : '暂无待接', variant: 'mint' },
      { label: '平均完成时长', value: '42', unit: '分', note: '轻量任务为主', variant: 'peach' },
      {
        label: '完成率',
        value: hallCompletionRateText.value,
        note: hallCompletionRateNote.value,
        variant: 'gold'
      },
      { label: '校园天气', value: hallWeatherValue.value, note: hallWeatherNote.value, variant: 'sky' }
    ]
  })

  async function refreshCampusWeather(force = false) {
    if (hallWeatherLoading.value && !force) return
    hallWeatherLoading.value = true
    try {
      const snap = await fetchCampusWeather()
      hallWeatherValue.value = `${snap.emoji} ${snap.temperature}°C ${snap.label}`
      hallWeatherNote.value = `${snap.note} · ${snap.placeHint}`
    } catch {
      if (hallWeatherValue.value.includes('加载中')) {
        hallWeatherValue.value = '🌤️ 暂不可用'
        hallWeatherNote.value = '稍后自动重试'
      }
    } finally {
      hallWeatherLoading.value = false
    }
  }

  function startCampusWeatherPolling() {
    stopCampusWeatherPolling()
    void refreshCampusWeather(true)
    hallWeatherTimer = window.setInterval(() => {
      void refreshCampusWeather()
    }, 30 * 60 * 1000)
  }

  function stopCampusWeatherPolling() {
    if (hallWeatherTimer != null) {
      window.clearInterval(hallWeatherTimer)
      hallWeatherTimer = null
    }
  }

  function disposeHallNoticeUi() {
    stopNoticeCarousel()
    stopHeroPromoCarousel()
    stopCampusWeatherPolling()
    noticeTrackResizeObserver?.disconnect()
    noticeTrackResizeObserver = null
  }

  return {
    hallNoticeExpanded,
    hallNoticeActiveId,
    noticeBarVisible,
    noticeTrackRef,
    noticeMeasureRef,
    noticeMarqueeActive,
    hallNoticeCarouselIndex,
    hasMultipleHallNotices,
    hallNoticeBannerText,
    activeHallNoticeId,
    hallTopNotice,
    formatNoticeBannerText,
    updateNoticeMarquee,
    syncHallNoticeCarouselIndex,
    stopNoticeCarousel,
    startNoticeCarousel,
    restartNoticeCarousel,
    pauseNoticeCarousel,
    resumeNoticeCarousel,
    goToHallNotice,
    toggleHallNoticeExpanded,
    activateHallNotice,
    onPublicNoticesLoaded,
    setupNoticeMarqueeObserver,
    disposeHallNoticeUi,
    promoActivities,
    heroPromoIndex,
    claimingHeroPromoId,
    heroPromoSlides,
    activeHeroPromo,
    hasMultipleHeroPromos,
    stopHeroPromoCarousel,
    startHeroPromoCarousel,
    pauseHeroPromoCarousel,
    resumeHeroPromoCarousel,
    goToHeroPromo,
    heroPromoCtaText,
    fetchPromoActivities,
    claimPromoActivity,
    claimHeroPromo,
    claimNewcomerPack,
    hallZones: HALL_ZONES,
    filters,
    paging,
    visibleTasks,
    hallStats,
    taskTypeOptions,
    hotTaskTypes,
    selectZoneFilter,
    selectSortMode,
    selectTaskTypeFilter,
    applyFilters,
    resetFilters,
    goPrevPage,
    goNextPage,
    heroCategoryTags: HERO_CATEGORY_TAGS,
    selectHeroCategoryTag,
    applyHeroSearch,
    scrollToHallTaskList,
    heroSideStats,
    fetchHallCompletionStats,
    startCampusWeatherPolling,
    stopCampusWeatherPolling,
    refreshCampusWeather,
    hallFaqItems: HALL_FAQ,
    hallActivities,
    hallLeaderboard,
    publisherAvatarUrl,
    publisherNameInitial,
    fetchTasks,
    hallStatusLabel,
    hallTaskCategoryLabel,
    hallTaskDistance,
    hallTaskEmoji,
    formatTaskTimeAgo,
    taskTypeLabel
  }
}
