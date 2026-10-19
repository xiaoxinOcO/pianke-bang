import { computed, nextTick, ref, watch, type ComputedRef, type Ref } from 'vue'
import { apiOrigin } from '../../constants/api'
import type { AdminTaskStatsResponse, AdminUserItem, TaskItem, TaskStatus } from '../../types/app'
import { statusLabelMap } from '../../utils/taskStatus'

type RiderRequest = <T>(url: string, init?: RequestInit) => Promise<T>

export type RiderStatsDomainDeps = {
  adminUsers: Ref<AdminUserItem[]>
  safeAllTasks: ComputedRef<TaskItem[]>
  request: RiderRequest
  currentView: Ref<string>
  adminModule: Ref<string>
  myName: Ref<string>
  isRiderApproved: Ref<boolean>
  isAdmin: Ref<boolean>
  errorMsg: Ref<string>
}

export function createRiderStatsDomain(deps: RiderStatsDomainDeps) {
  void apiOrigin
  const riderEchartRef = ref<HTMLDivElement | null>(null)

  const riderOverviewEchartRef = ref<HTMLDivElement | null>(null)

  const taskOverviewTrendEchartRef = ref<HTMLDivElement | null>(null)

  const taskOverviewStatusEchartRef = ref<HTMLDivElement | null>(null)

  const taskStatsLoading = ref(false)

  const taskStatsData = ref<AdminTaskStatsResponse | null>(null)

  const taskStatsTrendDays = ref<7 | 14 | 30>(7)

  let riderChartInstance: any = null

  let riderOverviewChartInstance: any = null

  let taskOverviewTrendChartInstance: any = null

  let taskOverviewStatusChartInstance: any = null

  let echartsLoader: Promise<any> | null = null

  const adminRiderCompareRows = computed(() => {
    const approvedNameMap = new Map<string, string>()
    const map = new Map<
      string,
      {
        username: string
        avatarUrl: string
        claimCount: number
        completedCount: number
        timeoutCount: number
        totalIncome: number
        lastClaimAt: string
        noFaultCount: number
      }
    >()

    for (const u of deps.adminUsers.value) {
      const name = String(u.username || '').trim()
      if (!name || !u.riderApproved) continue
      approvedNameMap.set(name.toLowerCase(), name)
      // 先把全部已认证接单员放进统计表，保证无接单记录也会展示为 0
      if (!map.has(name)) {
        map.set(name, {
          username: name,
          avatarUrl: String(u.avatarUrl || '').trim(),
          claimCount: 0,
          completedCount: 0,
          timeoutCount: 0,
          totalIncome: 0,
          lastClaimAt: '',
          noFaultCount: 0
        })
      }
    }

    for (const t of deps.safeAllTasks.value) {
      const claimerRaw = String(t.claimerName || '').trim()
      if (!claimerRaw) continue

      const approvedName = approvedNameMap.get(claimerRaw.toLowerCase())
      if (!approvedName) continue

      const row = map.get(approvedName)!

      // 申诉通过且“无责”的异常单，不应拉低完成率（但仍保留在 claimCount 里用于“总接单”展示）
      if ((t.status === 'CANCELLED' || t.status === 'REJECTED') && (t as any).riderNoFault) {
        row.noFaultCount += 1
      }

      row.claimCount += 1

      if (!row.lastClaimAt || String(t.updatedAt || t.createdAt) > row.lastClaimAt) {
        row.lastClaimAt = String(t.updatedAt || t.createdAt || '')
      }

      if (t.status === 'COMPLETED') {
        row.completedCount += 1
        row.totalIncome += Number(t.reward || 0)
      } else if (t.status === 'OVERDUE' || (t.status === 'REJECTED' && Boolean(t.overdueRejected))) {
        row.timeoutCount += 1
      }
    }

    return Array.from(map.values())
      .map((r) => ({
        ...r,
        timeoutRate: r.claimCount > 0 ? Number(((r.timeoutCount / r.claimCount) * 100).toFixed(1)) : 0,
        completionRate:
          Math.max(r.claimCount - (r.noFaultCount || 0), 0) > 0
            ? Number(((r.completedCount / Math.max(r.claimCount - (r.noFaultCount || 0), 0)) * 100).toFixed(1))
            : 0
      }))
      .sort((a, b) => {
        if (b.completedCount !== a.completedCount) return b.completedCount - a.completedCount
        if (b.claimCount !== a.claimCount) return b.claimCount - a.claimCount
        return b.totalIncome - a.totalIncome
      })
  })

  const selectedRiderName = ref('')

  const riderRankMetric = ref<'completed' | 'rate' | 'income' | 'claim'>('completed')

  const selectedRiderRow = computed(() => {
    if (!adminRiderCompareRows.value.length) return null
    const hit = adminRiderCompareRows.value.find((r) => r.username === selectedRiderName.value)
    return hit || adminRiderCompareRows.value[0]
  })

  const riderLeaderboardRows = computed(() => {
    const rows = adminRiderCompareRows.value.slice()
    if (riderRankMetric.value === 'completed') {
      rows.sort((a, b) => b.completedCount - a.completedCount || b.claimCount - a.claimCount || b.totalIncome - a.totalIncome)
    } else if (riderRankMetric.value === 'rate') {
      rows.sort((a, b) => b.completionRate - a.completionRate || b.completedCount - a.completedCount || b.claimCount - a.claimCount)
    } else if (riderRankMetric.value === 'income') {
      rows.sort((a, b) => b.totalIncome - a.totalIncome || b.completedCount - a.completedCount || b.claimCount - a.claimCount)
    } else {
      rows.sort((a, b) => b.claimCount - a.claimCount || b.completedCount - a.completedCount || b.totalIncome - a.totalIncome)
    }
    return rows
  })

  function riderMetricValue(row: { claimCount: number; completedCount: number; completionRate: number; totalIncome: number }) {
    if (riderRankMetric.value === 'completed') return Number(row.completedCount || 0)
    if (riderRankMetric.value === 'rate') return Number(row.completionRate || 0)
    if (riderRankMetric.value === 'income') return Number(row.totalIncome || 0)
    return Number(row.claimCount || 0)
  }

  const riderRankMaxValue = computed(() => {
    if (riderRankMetric.value === 'rate') return 100
    const max = Math.max(...riderLeaderboardRows.value.map((r) => riderMetricValue(r)), 1)
    return max || 1
  })

  const riderTop3 = computed(() => {
    const list = riderLeaderboardRows.value.slice(0, 3)
    return {
      first: list[0] || null,
      second: list[1] || null,
      third: list[2] || null
    }
  })

  // 排行榜会复用 riderLeaderboardRows + riderRankMetric
  function riderRankMetricLabel(row: { claimCount: number; completedCount: number; completionRate: number; totalIncome: number }) {
    if (riderRankMetric.value === 'completed') return `${row.completedCount} 单`
    if (riderRankMetric.value === 'rate') return `${row.completionRate}%`
    if (riderRankMetric.value === 'income') return `¥ ${row.totalIncome.toFixed(2)}`
    return `${row.claimCount} 单`
  }

  const riderChartMax = computed(() => {
    const row = selectedRiderRow.value
    if (!row) return 1
    return Math.max(row.claimCount, row.completedCount, row.timeoutCount, row.totalIncome, 1)
  })

  const riderChartItems = computed(() => {
    const row = selectedRiderRow.value
    if (!row) return []
    const max = riderChartMax.value
    return [
      { key: 'claim', label: '总接单', value: row.claimCount, text: `${row.claimCount} 单`, width: `${Math.max((row.claimCount / max) * 100, 4)}%` },
      { key: 'completed', label: '已完成', value: row.completedCount, text: `${row.completedCount} 单`, width: `${Math.max((row.completedCount / max) * 100, 4)}%` },
      { key: 'timeout', label: '超时单', value: row.timeoutCount, text: `${row.timeoutCount} 单`, width: `${Math.max((row.timeoutCount / max) * 100, 4)}%` },
      { key: 'income', label: '累计收入', value: row.totalIncome, text: `¥ ${row.totalIncome.toFixed(2)}`, width: `${Math.max((row.totalIncome / max) * 100, 4)}%` },
      { key: 'rate', label: '完成率', value: row.completionRate, text: `${row.completionRate}%`, width: `${Math.max(row.completionRate, 4)}%` }
    ]
  })

  const riderOverviewTrendDays = ref<7 | 14 | 30>(7)

  function riderOverviewDateKey(iso: string): string {
    const s = String(iso || '')
    const m = s.match(/^(\d{4}-\d{2}-\d{2})/)
    return m ? m[1] : ''
  }

  const riderOverviewKpis = computed(() => {
    const users = deps.adminUsers.value
    const certified = users.filter((u) => u.riderApproved).length
    const pendingApply = users.filter((u) => u.riderApplicationPending).length
    const delivering = deps.safeAllTasks.value.filter(
      (t) => !!String(t.claimerName || '').trim() && (t.status === 'CLAIMED' || t.status === 'PENDING_CONFIRMATION')
    ).length
    const approvedLower = new Set(
      users
        .filter((u) => u.riderApproved)
        .map((u) => String(u.username || '').trim().toLowerCase())
        .filter(Boolean)
    )
    let completedTotal = 0
    for (const t of deps.safeAllTasks.value) {
      if (t.status !== 'COMPLETED') continue
      const c = String(t.claimerName || '').trim()
      if (!c || !approvedLower.has(c.toLowerCase())) continue
      completedTotal++
    }
    return { certified, pendingApply, delivering, completedTotal }
  })

  const riderOverviewTrend = computed(() => {
    const days = riderOverviewTrendDays.value
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const dateKeys: string[] = []
    const labels: string[] = []
    for (let i = days - 1; i >= 0; i--) {
      const d = new Date(today)
      d.setDate(d.getDate() - i)
      const y = d.getFullYear()
      const mo = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      dateKeys.push(`${y}-${mo}-${day}`)
      labels.push(`${mo}-${day}`)
    }
    const dayIndex = (dk: string) => dateKeys.indexOf(dk)

    const approvedLower = new Set(
      deps.adminUsers.value
        .filter((u) => u.riderApproved)
        .map((u) => String(u.username || '').trim().toLowerCase())
        .filter(Boolean)
    )

    const completed = dateKeys.map(() => 0)
    const delivering = dateKeys.map(() => 0)
    const cancelled = dateKeys.map(() => 0)
    const newApply = dateKeys.map(() => 0)
    const activeRiderKeys: string[][] = dateKeys.map(() => [])

    for (const u of deps.adminUsers.value) {
      if (!u.riderAppliedAt) continue
      const k = riderOverviewDateKey(u.riderAppliedAt)
      const i = dayIndex(k)
      if (i >= 0) newApply[i]++
    }

    for (const t of deps.safeAllTasks.value) {
      const claimer = String(t.claimerName || '').trim()
      if (!claimer || !approvedLower.has(claimer.toLowerCase())) continue
      const key = claimer.toLowerCase()

      if (t.status === 'COMPLETED') {
        const k = riderOverviewDateKey(t.updatedAt || t.createdAt)
        const i = dayIndex(k)
        if (i >= 0) {
          completed[i]++
          activeRiderKeys[i].push(key)
        }
      } else if (t.status === 'CLAIMED' || t.status === 'PENDING_CONFIRMATION') {
        const k = riderOverviewDateKey(t.updatedAt || t.createdAt)
        const i = dayIndex(k)
        if (i >= 0) delivering[i]++
      } else if ((t.status === 'CANCELLED' || t.status === 'REJECTED') && !(t as { riderNoFault?: boolean }).riderNoFault) {
        const k = riderOverviewDateKey(t.updatedAt || t.createdAt)
        const i = dayIndex(k)
        if (i >= 0) cancelled[i]++
      }
    }

    const activeRiders = activeRiderKeys.map((arr) => new Set(arr).size)

    return { labels, completed, delivering, cancelled, newApply, activeRiders }
  })

  const riderOverviewDistribution = computed(() => {
    const users = deps.adminUsers.value
    const certified = users.filter((u) => u.riderApproved).length
    const pending = users.filter((u) => u.riderApplicationPending).length
    const plain = Math.max(users.length - certified - pending, 0)
    return [
      { key: 'cert', label: '已认证接单员', count: certified },
      { key: 'pend', label: '待审核申请', count: pending },
      { key: 'plain', label: '普通用户', count: plain }
    ]
  })

  function ensureEchartsLoaded() {
    if ((window as any).echarts) return Promise.resolve((window as any).echarts)
    if (echartsLoader) return echartsLoader
    echartsLoader = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = 'https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js'
      script.async = true
      script.onload = () => resolve((window as any).echarts)
      script.onerror = () => reject(new Error('ECharts 加载失败'))
      document.head.appendChild(script)
    })
    return echartsLoader
  }

  async function renderRiderEchart() {
    if (deps.currentView.value !== 'admin' || deps.adminModule.value !== 'riderCompare') return
    if (!selectedRiderRow.value || !riderEchartRef.value) return
    const echarts = await ensureEchartsLoaded()
    if (!echarts) return

    // 当切换左侧菜单时，图表容器会被卸载再重建，需要确保实例绑定到当前 DOM
    if (riderChartInstance) {
      try {
        const dom = riderChartInstance.getDom?.()
        if (dom && dom !== riderEchartRef.value) {
          riderChartInstance.dispose()
          riderChartInstance = null
        }
      } catch {
        riderChartInstance = null
      }
    }
    if (!riderChartInstance) riderChartInstance = echarts.init(riderEchartRef.value)

    const row = selectedRiderRow.value
    riderChartInstance.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 46, right: 18, top: 30, bottom: 28 },
      xAxis: {
        type: 'category',
        data: ['总接单', '已完成', '超时单', '完成率%', '累计收入(元)'],
        axisTick: { show: false }
      },
      yAxis: { type: 'value' },
      series: [
        {
          type: 'bar',
          data: [row.claimCount, row.completedCount, row.timeoutCount, row.completionRate, Number(row.totalIncome.toFixed(2))],
          barMaxWidth: 42,
          itemStyle: {
            borderRadius: [6, 6, 0, 0],
            color: '#3b82f6'
          }
        }
      ]
    })
    riderChartInstance.resize?.()
  }

  async function renderRiderOverviewChart() {
    if (deps.currentView.value !== 'admin' || deps.adminModule.value !== 'riderOverview') return
    if (!riderOverviewEchartRef.value) return
    const echarts = await ensureEchartsLoaded()
    if (!echarts) return

    if (riderOverviewChartInstance) {
      try {
        const dom = riderOverviewChartInstance.getDom?.()
        if (dom && dom !== riderOverviewEchartRef.value) {
          riderOverviewChartInstance.dispose()
          riderOverviewChartInstance = null
        }
      } catch {
        riderOverviewChartInstance = null
      }
    }
    if (!riderOverviewChartInstance) riderOverviewChartInstance = echarts.init(riderOverviewEchartRef.value)

    const t = riderOverviewTrend.value
    riderOverviewChartInstance.setOption({
      color: ['#3b82f6', '#22c55e', '#f97316', '#ef4444', '#94a3b8'],
      tooltip: { trigger: 'axis' },
      legend: {
        bottom: 0,
        data: ['完成单', '配送中动态', '异常/取消', '新申请', '活跃接单员']
      },
      grid: { left: 52, right: 28, top: 36, bottom: 72 },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: t.labels,
        axisTick: { show: false }
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '完成单', type: 'line', smooth: true, showSymbol: true, data: t.completed },
        { name: '配送中动态', type: 'line', smooth: true, showSymbol: true, data: t.delivering },
        { name: '异常/取消', type: 'line', smooth: true, showSymbol: true, data: t.cancelled },
        { name: '新申请', type: 'line', smooth: true, showSymbol: true, data: t.newApply },
        { name: '活跃接单员', type: 'line', smooth: true, showSymbol: true, data: t.activeRiders }
      ]
    })
    riderOverviewChartInstance.resize?.()
  }

  async function renderTaskOverviewCharts() {
    if (deps.currentView.value !== 'admin' || deps.adminModule.value !== 'taskOverview') return
    if (!taskStatsData.value) return
    const echarts = await ensureEchartsLoaded()
    if (!echarts) return

    const trend = taskStatsData.value.publishTrend ?? []
    if (taskOverviewTrendEchartRef.value) {
      if (taskOverviewTrendChartInstance) {
        try {
          const dom = taskOverviewTrendChartInstance.getDom?.()
          if (dom && dom !== taskOverviewTrendEchartRef.value) {
            taskOverviewTrendChartInstance.dispose()
            taskOverviewTrendChartInstance = null
          }
        } catch {
          taskOverviewTrendChartInstance = null
        }
      }
      if (!taskOverviewTrendChartInstance) {
        taskOverviewTrendChartInstance = echarts.init(taskOverviewTrendEchartRef.value)
      }
      taskOverviewTrendChartInstance.setOption({
        color: ['#3b82f6'],
        tooltip: { trigger: 'axis' },
        grid: { left: 52, right: 28, top: 36, bottom: 48 },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: trend.map((p) => p.date),
          axisTick: { show: false }
        },
        yAxis: { type: 'value', minInterval: 1 },
        series: [{ name: '发布任务数', type: 'line', smooth: true, showSymbol: true, data: trend.map((p) => p.count) }]
      })
      taskOverviewTrendChartInstance.resize?.()
    }

    const statusRows = taskStatsData.value.statusDistribution ?? []
    if (taskOverviewStatusEchartRef.value && statusRows.length > 0) {
      if (taskOverviewStatusChartInstance) {
        try {
          const dom = taskOverviewStatusChartInstance.getDom?.()
          if (dom && dom !== taskOverviewStatusEchartRef.value) {
            taskOverviewStatusChartInstance.dispose()
            taskOverviewStatusChartInstance = null
          }
        } catch {
          taskOverviewStatusChartInstance = null
        }
      }
      if (!taskOverviewStatusChartInstance) {
        taskOverviewStatusChartInstance = echarts.init(taskOverviewStatusEchartRef.value)
      }
      taskOverviewStatusChartInstance.setOption({
        tooltip: { trigger: 'item' },
        legend: { bottom: 0 },
        series: [
          {
            type: 'pie',
            radius: ['38%', '68%'],
            data: statusRows.map((row) => ({
              name: statusLabelMap[row.name as TaskStatus] || row.name,
              value: row.count
            }))
          }
        ]
      })
      taskOverviewStatusChartInstance.resize?.()
    }
  }



  const taskOverviewKpis = computed(() => {
    const stats = taskStatsData.value
    const status = stats?.statusDistribution ?? []
    const find = (name: string) => status.find((s) => s.name === name)?.count ?? 0
    return {
      total: stats?.totalCount ?? 0,
      today: stats?.todayCount ?? 0,
      open: find('OPEN'),
      completed: find('COMPLETED')
    }
  })








  async function fetchAdminTaskStats() {
    if (!deps.isAdmin.value) {
      taskStatsData.value = null
      return
    }
    taskStatsLoading.value = true
    try {
      taskStatsData.value = await deps.request<AdminTaskStatsResponse>(
        `${apiOrigin}/api/admin/tasks/stats?days=${taskStatsTrendDays.value}`
      )
      await nextTick()
      renderTaskOverviewCharts()
    } catch (err) {
      taskStatsData.value = null
      deps.errorMsg.value = (err as Error).message
    } finally {
      taskStatsLoading.value = false
    }
  }


  watch([taskStatsData, taskStatsTrendDays, deps.currentView, deps.adminModule], async () => {
    await nextTick()
    renderTaskOverviewCharts()
  })

  watch(
    adminRiderCompareRows,
    (rows) => {
      if (!rows.length) {
        selectedRiderName.value = ''
        return
      }
      if (!rows.some((r) => r.username === selectedRiderName.value)) {
        selectedRiderName.value = rows[0].username
      }
    },
    { immediate: true }
  )

  watch([selectedRiderRow, deps.currentView, deps.adminModule], async () => {
    await nextTick()
    renderRiderEchart()
  })

  watch([riderOverviewTrend, riderOverviewTrendDays, deps.currentView, deps.adminModule], async () => {
    await nextTick()
    renderRiderOverviewChart()
  })

  watch(
    riderOverviewEchartRef,
    (el) => {
      if (!el) {
        if (riderOverviewChartInstance) {
          try {
            riderOverviewChartInstance.dispose()
          } catch {
            /* ignore */
          }
          riderOverviewChartInstance = null
        }
        return
      }
      void nextTick().then(() => renderRiderOverviewChart())
    },
    { flush: 'post' }
  )

  watch(
    riderEchartRef,
    (el) => {
      if (!el) {
        if (riderChartInstance) {
          try {
            riderChartInstance.dispose()
          } catch {
            /* ignore */
          }
          riderChartInstance = null
        }
        return
      }
      void nextTick().then(() => renderRiderEchart())
    },
    { flush: 'post' }
  )

  /** 图表容器必须经此写入：reactive(appRoot) 会解包嵌套 ref，子组件里拿不到 Ref 本体 */
  function bindRiderEchartEl(el: HTMLDivElement | null) {
    riderEchartRef.value = el
  }

  function bindRiderOverviewEchartEl(el: HTMLDivElement | null) {
    riderOverviewEchartRef.value = el
  }

  function bindTaskOverviewTrendEchartEl(el: HTMLDivElement | null) {
    taskOverviewTrendEchartRef.value = el
    if (el) nextTick(() => renderTaskOverviewCharts())
  }

  function bindTaskOverviewStatusEchartEl(el: HTMLDivElement | null) {
    taskOverviewStatusEchartRef.value = el
    if (el) nextTick(() => renderTaskOverviewCharts())
  }

  function isRiderUser(username?: string, riderApproved?: unknown) {
    // 后端可能把 riderApproved 返回成 boolean / 'true' / 1 之类，展示逻辑要做兼容。
    if (typeof riderApproved === 'boolean') return riderApproved
    if (typeof riderApproved === 'number') return riderApproved === 1
    if (typeof riderApproved === 'string') {
      const s = riderApproved.trim().toLowerCase()
      if (s === 'true' || s === '1') return true
      if (s === 'false' || s === '0') return false
    }
    if (!username) return false
    if (deps.myName.value && String(username).trim().toLowerCase() === String(deps.myName.value).trim().toLowerCase()) {
      return deps.isRiderApproved.value
    }
    // 对于非自己且 riderApproved 不可识别的情况：不做猜测，避免把普通用户误判为接单员
    return false
  }

  function resizeRiderCharts() {
    if (riderChartInstance) riderChartInstance.resize()
    if (riderOverviewChartInstance) riderOverviewChartInstance.resize()
    if (taskOverviewTrendChartInstance) taskOverviewTrendChartInstance.resize?.()
    if (taskOverviewStatusChartInstance) taskOverviewStatusChartInstance.resize?.()
  }

  function disposeRiderCharts() {
    if (riderChartInstance) {
      riderChartInstance.dispose()
      riderChartInstance = null
    }
    if (riderOverviewChartInstance) {
      riderOverviewChartInstance.dispose()
      riderOverviewChartInstance = null
    }
    if (taskOverviewTrendChartInstance) {
      taskOverviewTrendChartInstance.dispose()
      taskOverviewTrendChartInstance = null
    }
    if (taskOverviewStatusChartInstance) {
      taskOverviewStatusChartInstance.dispose()
      taskOverviewStatusChartInstance = null
    }
  }

  return {
    riderEchartRef,
    riderOverviewEchartRef,
    taskOverviewTrendEchartRef,
    taskOverviewStatusEchartRef,
    taskStatsLoading,
    taskStatsData,
    taskStatsTrendDays,
    adminRiderCompareRows,
    selectedRiderName,
    riderRankMetric,
    selectedRiderRow,
    riderLeaderboardRows,
    riderMetricValue,
    riderRankMaxValue,
    riderTop3,
    riderRankMetricLabel,
    riderChartMax,
    riderChartItems,
    riderOverviewTrendDays,
    riderOverviewKpis,
    riderOverviewTrend,
    riderOverviewDistribution,
    bindRiderEchartEl,
    bindRiderOverviewEchartEl,
    bindTaskOverviewTrendEchartEl,
    bindTaskOverviewStatusEchartEl,
    isRiderUser,
    resizeRiderCharts,
    disposeRiderCharts,
    renderTaskOverviewCharts,
    fetchAdminTaskStats,
    taskOverviewKpis,
    ensureEchartsLoaded,
    renderRiderEchart,
    renderRiderOverviewChart
  }
}
