const { resolveStatusLabel, statusClass, taskTypeLabel, taskTypeAbbr, taskTypeBadgeClass } = require('../../utils/task-status')
const { formatTimeAgo, formatDateTime, formatMoney, maskName, resolveUserAvatar } = require('../../utils/format')

function isWeakTitle(title) {
  const s = String(title || '').trim()
  if (!s) return true
  if (s.length < 2) return true
  // 纯数字 / 过短无意义标题，大厅看不出任务内容
  if (/^\d{1,6}$/.test(s)) return true
  if (/^[a-zA-Z0-9_\-]{1,4}$/.test(s)) return true
  return false
}

function shortText(text, max = 16) {
  const s = String(text || '').trim()
  if (!s) return ''
  return s.length > max ? `${s.slice(0, max)}…` : s
}

function buildDisplayTitle(task, typeLabel) {
  const raw = String(task.title || '').trim()
  if (!isWeakTitle(raw)) return raw
  const pickup = shortText(task.pickupLocation, 10)
  const dropoff = shortText(task.dropoffLocation, 10)
  if (pickup && dropoff) return `${typeLabel}：${pickup} → ${dropoff}`
  if (pickup) return `${typeLabel}：取 ${pickup}`
  if (dropoff) return `${typeLabel}：送 ${dropoff}`
  return typeLabel || '校园代拿任务'
}

function buildSummary(task, typeLabel) {
  const pickup = shortText(task.pickupLocation, 12) || '未填取件点'
  const dropoff = shortText(task.dropoffLocation, 12) || '未填送达点'
  return `${typeLabel}｜取 ${pickup}｜送 ${dropoff}`
}

function buildDesc(task) {
  const desc = String(task.description || '').trim()
  if (!desc) return ''
  return desc.length > 40 ? `${desc.slice(0, 40)}…` : desc
}

function buildDeadline(task) {
  if (!task.latestDeliveryAt) return ''
  const full = formatDateTime(task.latestDeliveryAt)
  // 同天只显示时间更易读
  return full.length > 11 ? full.slice(5) : full
}

Component({
  properties: {
    task: { type: Object, value: {} },
    showActions: { type: Boolean, value: false },
    myName: { type: String, value: '' },
    compact: { type: Boolean, value: false },
    perspective: { type: String, value: '' }
  },

  data: {
    statusLabel: '',
    statusCls: '',
    typeLabel: '',
    typeAbbr: '',
    typeBadgeCls: '',
    timeAgo: '',
    personLabel: '发布',
    personName: '匿名',
    personInitial: '匿',
    publisherAvatar: '',
    publisherInitial: '匿',
    rewardText: '0.00',
    urgentOn: false,
    urgentTag: '',
    urgentFeeText: '',
    displayTitle: '',
    summaryLine: '',
    descText: '',
    deadlineText: '',
    pickupText: '未填写',
    dropoffText: '未填写'
  },

  observers: {
    'task, perspective'(task) {
      if (!task) return
      const perspective = this.properties.perspective || ''
      let personLabel = '发布'
      let personName = maskName(task.publisherName)

      if (perspective === 'published') {
        personLabel = '接单'
        personName = task.claimerName ? maskName(task.claimerName) : '待接单'
      } else if (perspective === 'claimed') {
        personLabel = '发布'
        personName = maskName(task.publisherName)
      }

      const typeLabel = taskTypeLabel(task.taskType)
      const initial = personName && personName !== '待接单'
        ? personName.slice(0, 1)
        : '待'
      const publisherMasked = maskName(task.publisherName)
      const publisherAvatar = task.publisherAvatarUrl
        || resolveUserAvatar(task.publisherProfile || { avatarUrl: task.avatarUrl })
        || ''
      const urgentOn = task.urgentEnabled === true || task.urgentEnabled === 1 || task.urgentEnabled === 'true'
      // 大厅面向接单员：只展示其可得的加急赏（约 80%），不展示发布方支付总额
      let urgentBonus = Number(task.urgentRiderBonus)
      if (!(Number.isFinite(urgentBonus) && urgentBonus > 0) && urgentOn) {
        const fee = Number(task.urgentFee)
        if (Number.isFinite(fee) && fee > 0) {
          urgentBonus = Math.round(fee * 0.8 * 100) / 100
        }
      }
      const urgentFeeText = urgentOn && Number.isFinite(urgentBonus) && urgentBonus > 0
        ? formatMoney(urgentBonus)
        : ''
      const urgentTag = urgentOn ? '加急' : ''

      this.setData({
        statusLabel: resolveStatusLabel(task),
        statusCls: statusClass(task.status),
        typeLabel,
        typeAbbr: taskTypeAbbr(task.taskType),
        typeBadgeCls: taskTypeBadgeClass(task.taskType),
        timeAgo: formatTimeAgo(task.createdAt),
        personLabel,
        personName,
        personInitial: initial,
        publisherAvatar,
        publisherInitial: publisherMasked.slice(0, 1) || '匿',
        rewardText: formatMoney(task.reward),
        urgentOn,
        urgentTag,
        urgentFeeText,
        displayTitle: buildDisplayTitle(task, typeLabel),
        summaryLine: buildSummary(task, typeLabel),
        descText: buildDesc(task),
        deadlineText: buildDeadline(task),
        pickupText: String(task.pickupLocation || '').trim() || '未填写取件点',
        dropoffText: String(task.dropoffLocation || '').trim() || '未填写送达点'
      })
    }
  },

  methods: {
    onTap() {
      const task = this.properties.task
      if (task && task.id) {
        this.triggerEvent('tap', { task })
      }
    },

    onClaim() {
      this.triggerEvent('claim', { id: this.properties.task.id })
    }
  }
})
