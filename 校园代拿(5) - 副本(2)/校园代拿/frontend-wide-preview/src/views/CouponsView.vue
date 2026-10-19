<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useCouponsStore } from '../stores/useCouponsStore'

type CouponFilter = 'ALL' | 'UNUSED' | 'USED' | 'EXPIRED' | 'LOCKED'

const r = useCouponsStore()
const redeemCode = ref('')
const redeeming = ref(false)
const filter = ref<CouponFilter>('ALL')

onMounted(() => {
  void r.fetchMyCoupons?.()
})

const tabs = [
  { key: 'ALL' as const, label: '全部' },
  { key: 'UNUSED' as const, label: '可用' },
  { key: 'USED' as const, label: '已用' },
  { key: 'EXPIRED' as const, label: '过期' }
]

const allCoupons = computed(() => r.myCoupons || [])

const counts = computed(() => {
  const list = allCoupons.value
  return {
    all: list.length,
    unused: list.filter((c) => String(c.status) === 'UNUSED').length,
    used: list.filter((c) => String(c.status) === 'USED').length,
    expired: list.filter((c) => String(c.status) === 'EXPIRED').length,
    locked: list.filter((c) => String(c.status) === 'LOCKED').length
  }
})

const STATUS_RANK: Record<string, number> = {
  UNUSED: 0,
  LOCKED: 1,
  USED: 2,
  EXPIRED: 3
}

const visibleCoupons = computed(() => {
  let list = allCoupons.value
  if (filter.value !== 'ALL') {
    list = list.filter((c) => String(c.status) === filter.value)
  }
  return [...list].sort((a, b) => {
    const ra = STATUS_RANK[String(a.status)] ?? 9
    const rb = STATUS_RANK[String(b.status)] ?? 9
    if (ra !== rb) return ra - rb
    return Number(b.id || 0) - Number(a.id || 0)
  })
})

function statusText(status?: string) {
  if (status === 'UNUSED') return '可使用'
  if (status === 'LOCKED') return '占用中'
  if (status === 'USED') return '已使用'
  if (status === 'EXPIRED') return '已过期'
  return status || '-'
}

function statusModifier(status?: string) {
  if (status === 'UNUSED') return 'is-unused'
  if (status === 'LOCKED') return 'is-locked'
  if (status === 'USED') return 'is-used'
  if (status === 'EXPIRED') return 'is-expired'
  return ''
}

function amountText(item: Record<string, unknown>) {
  const type = String(item.type || '')
  if (type === 'PERCENT') {
    const zhe = (Number(item.discountPercent || 0) * 10).toFixed(1).replace(/\.0$/, '')
    return { value: zhe, unit: '折', hint: '折扣券' }
  }
  return {
    value: Number(item.discountAmount || 0).toFixed(2).replace(/\.00$/, ''),
    unit: '元',
    hint: type === 'THRESHOLD' ? '满减券' : '立减券'
  }
}

function descText(item: Record<string, unknown>) {
  const type = String(item.type || '')
  if (type === 'FIXED') return `发布赏金立减 ¥${Number(item.discountAmount || 0).toFixed(2)}`
  if (type === 'THRESHOLD') {
    return `满 ¥${Number(item.minSpend || 0).toFixed(2)} 减 ¥${Number(item.discountAmount || 0).toFixed(2)}`
  }
  if (type === 'PERCENT') {
    const zhe = (Number(item.discountPercent || 0) * 10).toFixed(1).replace(/\.0$/, '')
    const cap = Number(item.maxDiscount || 0)
    return cap > 0 ? `${zhe} 折 · 最高减 ¥${cap.toFixed(2)}` : `${zhe} 折优惠`
  }
  return '可用于发布任务时抵扣赏金'
}

function formatExpire(raw: unknown) {
  if (!raw) return ''
  return String(raw).replace('T', ' ').slice(0, 16)
}

function couponName(item: Record<string, unknown>) {
  const name = String(item.name || '').trim()
  if (!name || /^\d+$/.test(name)) {
    const hint = amountText(item).hint
    return hint === '满减券' ? '满减优惠券' : hint === '折扣券' ? '折扣优惠券' : '赏金抵扣券'
  }
  return name
}

async function onRedeem() {
  const code = redeemCode.value.trim()
  if (!code || redeeming.value) return
  redeeming.value = true
  try {
    await r.redeemCouponCode?.(code)
    redeemCode.value = ''
    await r.fetchMyCoupons?.()
  } finally {
    redeeming.value = false
  }
}

function setFilter(key: CouponFilter) {
  filter.value = key
}

function useCoupon() {
  r.goPublish?.()
}

function bannerUrl(item: Record<string, unknown>) {
  const raw = String(item.bannerImageUrl || '').trim()
  if (!raw) return ''
  return r.normalizeImageUrl?.(raw) || raw
}

function couponThemeClass(item: Record<string, unknown>) {
  const theme = String(item.bannerTheme || 'green').trim().toLowerCase()
  if (theme === 'warm' || theme === 'cool' || theme === 'green' || theme === 'blue') {
    return `theme-${theme}`
  }
  return 'theme-green'
}

function couponTicketClass(item: Record<string, unknown>) {
  return [
    statusModifier(String(item.status)),
    couponThemeClass(item),
    { 'has-banner': !!bannerUrl(item) }
  ]
}

function couponBannerStyle(item: Record<string, unknown>) {
  const url = bannerUrl(item)
  if (!url) return undefined
  const safe = url.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
  return { '--coupon-banner': `url("${safe}")` } as Record<string, string>
}
</script>

<template>
  <main class="pk-coupons">
    <header class="pk-coupons-head">
      <div class="pk-coupons-head-copy">
        <h1>我的优惠券</h1>
        <p class="pk-lead">发布任务支付赏金时可选用抵扣</p>
      </div>
      <div class="pk-coupons-head-actions">
        <button type="button" class="pk-btn pk-btn-ghost pk-btn-sm" @click="r.goHall?.()">返回大厅</button>
        <button
          v-if="counts.unused > 0"
          type="button"
          class="pk-btn pk-btn-accent pk-btn-sm"
          @click="useCoupon"
        >
          去发布使用
        </button>
      </div>
    </header>

    <section class="pk-coupons-redeem" aria-label="兑换码">
      <div class="pk-redeem-row">
        <span class="pk-redeem-tag">兑换码</span>
        <input
          v-model="redeemCode"
          maxlength="32"
          placeholder="输入兑换码领取"
          autocomplete="off"
          @keyup.enter="onRedeem()"
        />
        <button
          type="button"
          class="pk-btn pk-btn-accent pk-btn-sm"
          :disabled="redeeming || !redeemCode.trim()"
          @click="onRedeem()"
        >
          {{ redeeming ? '兑换中…' : '兑换' }}
        </button>
      </div>
    </section>

    <div class="pk-coupons-tabs" role="tablist" aria-label="优惠券筛选">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="pk-coupons-tab"
        role="tab"
        :class="{ active: filter === tab.key }"
        :aria-selected="filter === tab.key"
        @click="setFilter(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-if="r.myCouponsLoading" class="pk-coupons-empty">
      <p>优惠券加载中…</p>
    </div>
    <div v-else-if="visibleCoupons.length === 0" class="pk-coupons-empty">
      <p>{{ filter === 'ALL' ? '暂无优惠券' : `暂无${tabs.find((t) => t.key === filter)?.label || ''}优惠券` }}</p>
      <p class="pk-empty-desc">可在大厅领取新人礼包，或使用上方兑换码</p>
      <button type="button" class="pk-btn pk-btn-accent pk-btn-sm" @click="r.goHall?.()">
        去大厅看看
      </button>
    </div>

    <ul v-else class="pk-coupon-list">
      <li
        v-for="item in visibleCoupons"
        :key="String(item.id)"
        class="pk-coupon-ticket"
        :class="couponTicketClass(item)"
        :style="couponBannerStyle(item)"
      >
        <div class="pk-coupon-value">
          <span class="pk-coupon-value-hint">{{ amountText(item).hint }}</span>
          <div class="pk-coupon-value-num">
            <template v-if="String(item.type) === 'PERCENT'">
              <strong>{{ amountText(item).value }}</strong>
              <small>{{ amountText(item).unit }}</small>
            </template>
            <template v-else>
              <small>¥</small>
              <strong>{{ amountText(item).value }}</strong>
            </template>
          </div>
        </div>
        <div class="pk-coupon-divider" aria-hidden="true"></div>
        <div class="pk-coupon-body">
          <div class="pk-coupon-body-top">
            <strong class="pk-coupon-name">{{ couponName(item) }}</strong>
            <span class="pk-coupon-badge">{{ statusText(String(item.status)) }}</span>
          </div>
          <p class="pk-coupon-desc">{{ descText(item) }}</p>
          <div class="pk-coupon-foot">
            <span v-if="item.expireAt">有效期至 {{ formatExpire(item.expireAt) }}</span>
            <span v-else>长期有效</span>
            <button
              v-if="item.status === 'UNUSED'"
              type="button"
              class="pk-coupon-use"
              @click="useCoupon"
            >
              去使用
            </button>
          </div>
        </div>
      </li>
    </ul>
  </main>
</template>

<style scoped>
.pk-coupons {
  width: min(820px, calc(100% - 32px));
  margin: 0 auto;
  padding: 4px 0 72px;
}

.pk-coupons-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
}

.pk-coupons-head h1 {
  margin: 0 0 6px;
  font-family: var(--font-display, Georgia, 'Noto Serif SC', serif);
  font-size: 28px;
  font-weight: 700;
  color: var(--ink, #14120b);
  letter-spacing: -0.02em;
}

.pk-lead {
  margin: 0;
  color: var(--ink-muted, #7a756b);
  font-size: 14px;
  line-height: 1.5;
}

.pk-coupons-head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.pk-coupons-redeem {
  margin-bottom: 14px;
  padding: 10px;
  border-radius: 14px;
  background: var(--surface, #fffcf7);
  border: 1px solid var(--line, rgba(20, 18, 11, 0.1));
}

.pk-redeem-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pk-redeem-tag {
  flex-shrink: 0;
  padding: 0 4px 0 6px;
  font-size: 13px;
  font-weight: 650;
  color: var(--ink-soft, #3d3a33);
  white-space: nowrap;
}

.pk-redeem-row input {
  flex: 1;
  min-width: 0;
  height: 40px;
  border: 1px solid var(--line, rgba(20, 18, 11, 0.12));
  border-radius: 10px;
  padding: 0 12px;
  background: #fff;
  color: var(--ink, #14120b);
  font-size: 14px;
}

.pk-redeem-row input:focus {
  outline: none;
  border-color: rgba(196, 92, 38, 0.45);
  box-shadow: 0 0 0 3px rgba(196, 92, 38, 0.12);
}

.pk-coupons-tabs {
  display: flex;
  gap: 2px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--line, rgba(20, 18, 11, 0.1));
}

.pk-coupons-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 0;
  padding: 10px 14px 12px;
  background: transparent;
  color: var(--ink-muted, #7a756b);
  font-size: 14px;
  cursor: pointer;
}

.pk-coupons-tab.active {
  color: var(--ink, #14120b);
  font-weight: 650;
}

.pk-coupons-tab.active::after {
  content: '';
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--accent, #c45c26);
}

.pk-coupon-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 14px;
}

.pk-coupon-ticket {
  --ticket-accent: var(--mint, #2f6f62);
  --ticket-soft: var(--mint-light, #d8ebe6);
  --ticket-bg: var(--surface, #fffcf7);
  position: relative;
  display: grid;
  grid-template-columns: 118px 18px minmax(0, 1fr);
  min-height: 118px;
  border-radius: 18px;
  background: var(--ticket-bg);
  border: 1px solid rgba(47, 111, 98, 0.18);
  box-shadow: var(--shadow-card, 0 8px 30px rgba(20, 18, 11, 0.06));
  overflow: hidden;
}

/* 活动主题色（无海报时） */
.pk-coupon-ticket.theme-warm {
  --ticket-accent: #c45c26;
  --ticket-soft: #f4dcc8;
  --ticket-bg: linear-gradient(120deg, #fff 52%, #fff4ea 100%);
  border-color: rgba(196, 92, 38, 0.2);
}

.pk-coupon-ticket.theme-cool {
  --ticket-accent: #2f6fb0;
  --ticket-soft: #d7e9fb;
  --ticket-bg: linear-gradient(120deg, #fff 52%, #eef6ff 100%);
  border-color: rgba(47, 111, 176, 0.2);
}

.pk-coupon-ticket.theme-green {
  --ticket-accent: var(--mint, #2f6f62);
  --ticket-soft: var(--mint-light, #d8ebe6);
  --ticket-bg: linear-gradient(120deg, #fff 55%, #f2fff6 100%);
  border-color: rgba(47, 111, 98, 0.18);
}

.pk-coupon-ticket.theme-blue {
  --ticket-accent: #4b57d6;
  --ticket-soft: #dde1ff;
  --ticket-bg: linear-gradient(120deg, #fff 52%, #f0f2ff 100%);
  border-color: rgba(75, 87, 214, 0.2);
}

/* 活动海报作券面背景 */
.pk-coupon-ticket.has-banner {
  background-color: #fff;
  background-image:
    linear-gradient(
      105deg,
      rgba(255, 252, 247, 0.96) 0%,
      rgba(255, 252, 247, 0.9) 42%,
      rgba(255, 252, 247, 0.72) 70%,
      rgba(255, 252, 247, 0.55) 100%
    ),
    var(--coupon-banner);
  background-size: cover;
  background-position: center right;
}

.pk-coupon-ticket::before,
.pk-coupon-ticket::after {
  content: '';
  position: absolute;
  left: 109px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--paper, #f6f2ea);
  z-index: 2;
}

.pk-coupon-ticket::before {
  top: -8px;
  box-shadow: inset 0 1px 0 rgba(20, 18, 11, 0.06);
}

.pk-coupon-ticket::after {
  bottom: -8px;
  box-shadow: inset 0 -1px 0 rgba(20, 18, 11, 0.06);
}

.pk-coupon-value {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 4px;
  padding: 16px 10px;
  background: linear-gradient(165deg, var(--ticket-soft), #fff 70%);
  color: var(--ticket-accent);
}

.pk-coupon-value-hint {
  font-size: 11px;
  letter-spacing: 0.04em;
  opacity: 0.85;
}

.pk-coupon-value-num {
  display: flex;
  align-items: baseline;
  gap: 2px;
  line-height: 1;
}

.pk-coupon-value-num small {
  font-size: 14px;
  font-weight: 700;
}

.pk-coupon-value-num strong {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.pk-coupon-divider {
  position: relative;
  background:
    repeating-linear-gradient(
      180deg,
      transparent 0 7px,
      rgba(20, 18, 11, 0.14) 7px 12px
    );
  mask-image: linear-gradient(180deg, transparent, #000 12%, #000 88%, transparent);
}

.pk-coupon-body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 16px 16px 14px 8px;
  min-width: 0;
}

.pk-coupon-body-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.pk-coupon-name {
  font-size: 16px;
  color: var(--ink, #14120b);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pk-coupon-badge {
  flex-shrink: 0;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(47, 111, 98, 0.12);
  color: var(--mint, #2f6f62);
}

.pk-coupon-desc {
  margin: 0;
  font-size: 13px;
  color: var(--ink-soft, #3d3a33);
  line-height: 1.45;
}

.pk-coupon-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 2px;
  font-size: 12px;
  color: var(--ink-muted, #7a756b);
}

.pk-coupon-use {
  border: 0;
  border-radius: 999px;
  padding: 6px 12px;
  background: var(--accent, #c45c26);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.pk-coupon-use:hover {
  background: var(--accent-dark, #8f3d14);
}

.pk-coupon-ticket.is-used,
.pk-coupon-ticket.is-expired,
.pk-coupon-ticket.is-locked {
  --ticket-accent: #8a8479;
  --ticket-soft: #f0ebe3;
  --ticket-bg: #f7f4ef;
  border-color: rgba(20, 18, 11, 0.08);
}

.pk-coupon-ticket.has-banner.is-used,
.pk-coupon-ticket.has-banner.is-expired,
.pk-coupon-ticket.has-banner.is-locked {
  background-image:
    linear-gradient(
      105deg,
      rgba(247, 244, 239, 0.96) 0%,
      rgba(247, 244, 239, 0.9) 50%,
      rgba(247, 244, 239, 0.78) 100%
    ),
    var(--coupon-banner);
  filter: grayscale(0.35);
}

.pk-coupon-ticket.is-used .pk-coupon-badge,
.pk-coupon-ticket.is-expired .pk-coupon-badge,
.pk-coupon-ticket.is-locked .pk-coupon-badge {
  background: rgba(20, 18, 11, 0.06);
  color: var(--ink-muted, #7a756b);
}

.pk-coupon-ticket.is-used .pk-coupon-value,
.pk-coupon-ticket.is-expired .pk-coupon-value,
.pk-coupon-ticket.is-locked .pk-coupon-value,
.pk-coupon-ticket.is-used .pk-coupon-body,
.pk-coupon-ticket.is-expired .pk-coupon-body,
.pk-coupon-ticket.is-locked .pk-coupon-body {
  opacity: 0.72;
}

.pk-coupons-empty {
  text-align: center;
  padding: 40px 16px;
  border-radius: 16px;
  background: rgba(255, 252, 247, 0.7);
  border: 1px dashed rgba(20, 18, 11, 0.12);
  color: var(--ink-muted, #7a756b);
}

.pk-coupons-empty p {
  margin: 0;
  font-size: 15px;
  color: var(--ink-soft, #3d3a33);
}

.pk-empty-desc {
  margin: 8px 0 16px !important;
  font-size: 13px !important;
  color: var(--ink-muted, #7a756b) !important;
}

@media (max-width: 640px) {
  .pk-coupons-head {
    flex-direction: column;
  }

  .pk-coupons-head-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .pk-coupon-ticket {
    grid-template-columns: 96px 14px minmax(0, 1fr);
  }

  .pk-coupon-ticket::before,
  .pk-coupon-ticket::after {
    left: 89px;
  }

  .pk-coupon-value-num strong {
    font-size: 24px;
  }

  .pk-redeem-row {
    flex-wrap: wrap;
  }

  .pk-redeem-tag {
    width: 100%;
    padding: 0 2px 2px;
  }

  .pk-redeem-row input {
    flex: 1 1 160px;
  }
}
</style>
