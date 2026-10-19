<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { usePublishStore } from '../stores/usePublishStore'

const r = usePublishStore()

const REWARD_MAX = 9999.9
const rewardDraft = ref('')
const rewardHint = ref('')
let rewardHintTimer: number | null = null

function formatRewardOneDecimal(n: unknown) {
  const v = Number(n)
  if (!Number.isFinite(v) || v <= 0) return ''
  const rounded = Math.round(v * 10) / 10
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1)
}

function showRewardHint(text: string) {
  rewardHint.value = text
  if (rewardHintTimer != null) window.clearTimeout(rewardHintTimer)
  rewardHintTimer = window.setTimeout(() => {
    rewardHint.value = ''
    rewardHintTimer = null
  }, 2800)
}

function sanitizeRewardDraft(raw: string): { value: string; tip: string } {
  const original = String(raw || '')
  let tip = ''
  if (/[^\d.]/.test(original)) tip = '赏金只能输入数字，最多一位小数（如 0.1、3.5）'
  let s = original.replace(/[^\d.]/g, '')
  const firstDot = s.indexOf('.')
  if (firstDot >= 0) {
    const afterDot = s.slice(firstDot + 1)
    if (afterDot.includes('.')) tip = tip || '小数点只能有一个'
    if (afterDot.replace(/\./g, '').length > 1) tip = tip || '赏金只能精确到 0.1 元，不能输入两位小数'
    s = `${s.slice(0, firstDot + 1)}${afterDot.replace(/\./g, '')}`
    const [intPart = '', decPart = ''] = s.split('.')
    if (intPart.length > 4) tip = tip || '赏金整数部分最多 4 位，上限 9999.9 元'
    s = `${intPart.slice(0, 4)}.${decPart.slice(0, 1)}`
  } else if (s.length > 4) {
    tip = tip || '赏金整数部分最多 4 位，上限 9999.9 元'
    s = s.slice(0, 4)
  }
  if (s.startsWith('.')) s = `0${s}`
  const n = Number(s)
  if (s !== '' && s !== '.' && Number.isFinite(n) && n > REWARD_MAX) {
    tip = tip || '赏金不能超过 9999.9 元'
    s = String(REWARD_MAX)
  }
  return { value: s, tip }
}

rewardDraft.value = formatRewardOneDecimal(r.form.reward)

watch(
  () => r.form.reward,
  (v) => {
    if (typeof document !== 'undefined' && document.activeElement?.id === 'publish-reward') return
    rewardDraft.value = formatRewardOneDecimal(v)
  }
)

function onRewardInput(event: Event) {
  const el = event.target as HTMLInputElement
  const { value: s, tip } = sanitizeRewardDraft(el.value)
  if (tip) showRewardHint(tip)
  rewardDraft.value = s
  el.value = s
  if (s === '' || s === '.') {
    r.form.reward = 0
    return
  }
  const n = Number(s)
  if (!Number.isFinite(n)) {
    r.form.reward = 0
    return
  }
  r.form.reward = Math.min(n, REWARD_MAX)
}

function onRewardBlur() {
  const n = Number(r.form.reward)
  if (!Number.isFinite(n) || n <= 0) {
    if (rewardDraft.value !== '') showRewardHint('请填写有效赏金，至少 0.1 元')
    r.form.reward = 0
    rewardDraft.value = ''
    return
  }
  if (n < 0.1) {
    showRewardHint('赏金至少 0.1 元')
    r.form.reward = 0
    rewardDraft.value = ''
    return
  }
  const rounded = Math.min(REWARD_MAX, Math.round(n * 10) / 10)
  r.form.reward = rounded
  rewardDraft.value = formatRewardOneDecimal(rounded)
}

const previewTitle = computed(() => String(r.form.title || '').trim() || '填写任务标题')
const previewPickup = computed(
  () => String(r.pickupManualInput || r.form.pickupLocation || '').trim() || '填写取货地点'
)
const previewDropoff = computed(
  () => String(r.dropoffManualInput || r.form.dropoffLocation || '').trim() || '填写送达地点'
)
const previewReward = computed(() => {
  const n = Number(r.form.reward)
  if (!Number.isFinite(n) || n <= 0) return '0.0'
  return (Math.round(n * 10) / 10).toFixed(1)
})

function formatMoney(n: unknown) {
  const v = Number(n)
  if (!Number.isFinite(v)) return '0.00'
  return v.toFixed(2)
}

const previewPayable = computed(() => formatMoney(r.publishPayableTotal))
const urgentFeeText = computed(() => {
  if (r.urgentFeeValid) {
    return formatMoney(Number(r.urgentRiderBonus) + Number(r.urgentPushFee))
  }
  return formatMoney(r.urgentFee)
})
const urgentRiderText = computed(() => formatMoney(r.urgentRiderBonus))
const urgentPushText = computed(() => formatMoney(r.urgentPushFee))

function onUrgentFeeInput(event: Event) {
  const el = event.target as HTMLInputElement
  r.urgentFee = el.value
}
</script>


<template>
    <main class="publish-layout">
      <div class="publish-side">
        <aside class="publish-aside">
          <p class="publish-aside-kicker">Publishing Tips</p>
          <h2>发布建议</h2>
          <ul class="guide-list">
            <li>标题尽量具体，便于快速接单</li>
            <li>地点请写到楼栋/门口，减少沟通</li>
            <li>赏金合理会更快被领取；发布时需先支付任务赏金</li>
            <li>备注里补充时间要求和注意事项</li>
          </ul>
          <p class="meta">提示：任务一旦被接取，会自动从任务大厅移除。</p>
        </aside>

        <aside class="publish-preview" aria-label="订单预览">
          <p class="publish-preview-kicker">订单预览</p>
          <h3 class="publish-preview-title">{{ previewTitle }}</h3>

          <div class="publish-preview-route">
            <div class="publish-preview-rail" aria-hidden="true">
              <span class="publish-preview-dot publish-preview-dot--pickup"></span>
              <span class="publish-preview-line"></span>
              <span class="publish-preview-dot publish-preview-dot--dropoff"></span>
            </div>
            <div class="publish-preview-stops">
              <div class="publish-preview-stop publish-preview-stop--pickup">
                <span class="publish-preview-role">取</span>
                <strong>{{ previewPickup }}</strong>
              </div>
              <div class="publish-preview-stop publish-preview-stop--dropoff">
                <span class="publish-preview-role">送</span>
                <strong>{{ previewDropoff }}</strong>
              </div>
            </div>
          </div>

          <div class="publish-preview-pay">
            <span>{{ r.urgentEnabled && r.urgentFeeValid ? '预计实付' : '应付赏金' }}</span>
            <strong>¥{{ r.urgentEnabled && r.urgentFeeValid ? previewPayable : previewReward }}</strong>
          </div>
          <p v-if="r.urgentEnabled && r.urgentFeeValid" class="publish-preview-urgent">
            含加急费 ¥{{ urgentFeeText }}
          </p>
        </aside>
      </div>

      <section class="publish-main">
        <div class="publish-main-head">
          <p class="publish-main-kicker">Create Task</p>
          <h2>{{ r.editingRejectedTaskId ? '修改驳回任务并重新提交' : '创建任务' }}</h2>
        </div>
        <p v-if="r.isLoggedIn" class="meta publish-bounty-tip">
          填写任务信息后点击发布，再选择支付方式并完成赏金支付。支付成功后进入待审核。
        </p>
        <p
          v-if="r.publishFormHint && r.publishFormHint.type === 'success'"
          role="status"
          class="publish-form-hint publish-form-hint--ok"
        >
          {{ r.publishFormHint.text }}
        </p>
        <div v-if="r.myUnpaidPublishDraft" class="publish-draft-card">
          <div class="publish-draft-copy">
            <strong>有一笔任务赏金尚未支付</strong>
            <p>
              「{{ r.myUnpaidPublishDraft.title }}」· ¥{{ Number(r.myUnpaidPublishDraft.reward).toFixed(2) }}
              · {{ r.myUnpaidPublishDraft.pickupLocation }} → {{ r.myUnpaidPublishDraft.dropoffLocation }}
            </p>
            <small>完成支付后才会进入管理员审核；也可放弃该草稿后重新发布。</small>
          </div>
          <div class="publish-draft-actions">
            <button
              type="button"
              class="pk-btn pk-btn-primary pk-btn-sm"
              :disabled="r.actionLoadingMap[r.myUnpaidPublishDraft.id]"
              @click="r.resumeTaskBountyPayment(r.myUnpaidPublishDraft.id)"
            >
              继续支付
            </button>
            <button
              type="button"
              class="pk-btn pk-btn-ghost pk-btn-sm"
              :disabled="r.actionLoadingMap[r.myUnpaidPublishDraft.id]"
              @click="r.syncTaskBountyPaymentForTask(r.myUnpaidPublishDraft.id)"
            >
              同步支付状态
            </button>
            <button
              type="button"
              class="pk-btn pk-btn-danger pk-btn-sm"
              :disabled="r.actionLoadingMap[r.myUnpaidPublishDraft.id]"
              @click="r.abandonUnpaidPublishDraft(r.myUnpaidPublishDraft.id)"
            >
              放弃任务
            </button>
          </div>
        </div>
        <form class="task-form task-form--labeled" @submit.prevent>
          <div class="form-field-row">
            <label class="form-field-label" for="publish-title">任务标题</label>
            <div class="form-field-control">
              <input
                id="publish-title"
                v-model="r.form.title"
                :disabled="!r.isLoggedIn"
                placeholder="如：代取快递"
                required
              />
            </div>
          </div>
          <div class="form-field-row">
            <label class="form-field-label" for="publish-task-type">任务类型</label>
            <div class="form-field-control">
              <select id="publish-task-type" v-model="r.form.taskType" :disabled="!r.isLoggedIn" required>
                <option v-for="opt in r.taskTypeOptions" :key="`publish-type-${opt.value}`" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>
            </div>
          </div>
          <div class="field-grid-2">
            <div class="form-field-row" :ref="r.setPickupCascaderRootEl">
              <label class="form-field-label" for="publish-pickup">取货地点</label>
              <div class="form-field-control">
                <div class="pickup-cascader-wrap">
                  <input
                    id="publish-pickup"
                    v-model="r.pickupManualInput"
                    :disabled="!r.isLoggedIn"
                    placeholder="可输入或点选（楼栋 + 宿舍/教室号）"
                    @focus="r.focusPickupCascader()"
                    required
                  />
                  <div v-if="r.showPickupCascader && r.isLoggedIn" class="pickup-cascader-panel">
                    <div class="pickup-selected-row" v-if="r.pickupDisplayText">
                      当前：{{ r.pickupDisplayText }}
                    </div>
                    <div class="pickup-col">
                      <p class="pickup-col-title">楼栋</p>
                      <button
                        v-for="b in r.buildingOptions"
                        :key="`pickup-${b}`"
                        type="button"
                        class="pickup-item"
                        :class="{ active: r.pickupBuilding === b }"
                        @click="r.pickupBuilding = b"
                      >
                        {{ b }}
                      </button>
                    </div>
                    <div class="pickup-col">
                      <p class="pickup-col-title">{{ r.isClassroomBuilding ? '教室号' : '宿舍号' }}</p>
                      <p v-if="!r.pickupBuilding" class="pickup-hint">请先选择左侧楼栋</p>
                      <button
                        v-for="rm in r.roomOptions"
                        :key="`room-${rm}`"
                        type="button"
                        class="pickup-item"
                        :class="{ active: r.pickupRoom === rm }"
                        :disabled="!r.pickupBuilding"
                        @click="r.pickupRoom = rm"
                      >
                        {{ rm }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="form-field-row" :ref="r.setDropoffCascaderRootEl">
              <label class="form-field-label" for="publish-dropoff">送达地点</label>
              <div class="form-field-control">
                <div class="pickup-cascader-wrap">
                  <input
                    id="publish-dropoff"
                    v-model="r.dropoffManualInput"
                    :disabled="!r.isLoggedIn"
                    placeholder="可输入或点选（楼栋 + 宿舍/教室号）"
                    @focus="r.focusDropoffCascader()"
                    required
                  />
                  <div v-if="r.showDropoffCascader && r.isLoggedIn" class="pickup-cascader-panel">
                    <div class="pickup-selected-row" v-if="r.dropoffDisplayText">
                      当前：{{ r.dropoffDisplayText }}
                    </div>
                    <div class="pickup-col">
                      <p class="pickup-col-title">楼栋</p>
                      <button
                        v-for="b in r.buildingOptions"
                        :key="`dropoff-${b}`"
                        type="button"
                        class="pickup-item"
                        :class="{ active: r.dropoffBuilding === b }"
                        @click="r.dropoffBuilding = b"
                      >
                        {{ b }}
                      </button>
                    </div>
                    <div class="pickup-col">
                      <p class="pickup-col-title">{{ r.isDropoffClassroomBuilding ? '教室号' : '宿舍号' }}</p>
                      <p v-if="!r.dropoffBuilding" class="pickup-hint">请先选择左侧楼栋</p>
                      <button
                        v-for="rm in r.dropoffRoomOptions"
                        :key="`dropoff-room-${rm}`"
                        type="button"
                        class="pickup-item"
                        :class="{ active: r.dropoffRoom === rm }"
                        :disabled="!r.dropoffBuilding"
                        @click="r.dropoffRoom = rm"
                      >
                        {{ rm }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="field-grid-2">
            <div class="form-field-row">
              <label class="form-field-label" for="publish-reward">赏金</label>
              <div class="form-field-control">
                <input
                  id="publish-reward"
                  :value="rewardDraft"
                  :disabled="!r.isLoggedIn"
                  type="text"
                  inputmode="decimal"
                  autocomplete="off"
                  placeholder="如 3 或 0.5"
                  required
                  :class="{ 'is-reward-warn': !!rewardHint }"
                  @input="onRewardInput"
                  @blur="onRewardBlur"
                />
                <p class="publish-reward-tip" :class="{ 'is-warn': !!rewardHint }">
                  {{ rewardHint || '仅支持一位小数（0.1、0.2…），上限 9999.9 元' }}
                </p>
              </div>
            </div>
            <div class="form-field-row">
              <label class="form-field-label" for="publish-publisher">发布人</label>
              <div class="form-field-control">
                <input id="publish-publisher" :value="r.myName" disabled placeholder="登录账号" required />
              </div>
            </div>
          </div>
          <div class="form-field-row">
            <label class="form-field-label">配送时间</label>
            <div class="form-field-control">
              <button class="delivery-picker-trigger" type="button" :disabled="!r.isLoggedIn" @click="r.openDeliveryPicker()">
                <span class="delivery-picker-trigger-main">{{ r.selectedDeliverySummary }}</span>
                <span class="delivery-picker-trigger-sub">{{ r.estimateLatestDeliveryText }}</span>
                <span class="delivery-picker-trigger-arrow">›</span>
              </button>
            </div>
          </div>
          <input v-model="r.form.deliveryStartAt" type="hidden" />
          <input v-model="r.form.deliveryEndAt" type="hidden" />
          <input v-model="r.form.latestDeliveryAt" type="hidden" />
          <div class="form-field-row publish-urgent-row" data-publish-focus="urgent">
            <div class="form-field-control publish-urgent-block">
              <button
                type="button"
                class="publish-urgent-head"
                :disabled="!r.isLoggedIn"
                @click="r.togglePublishUrgent()"
              >
                <span class="publish-urgent-head-main">
                  <span class="form-field-label publish-urgent-label">
                    加急（本周剩余 {{ r.urgentRemaining }}/{{ r.urgentWeeklyLimit }}）
                  </span>
                  <span class="publish-urgent-desc">提高曝光与优先被看到，不承诺准时送达</span>
                </span>
                <span
                  class="publish-urgent-switch"
                  :class="{ on: r.urgentEnabled, disabled: r.urgentRemaining <= 0 }"
                  aria-hidden="true"
                >
                  <span class="publish-urgent-knob"></span>
                </span>
              </button>
              <div v-if="r.urgentEnabled" class="publish-urgent-body">
                <p class="publish-urgent-tip">
                  约 80% 给接单员，约 20% 为平台推送费 · ¥1～¥20，0.5 元步进
                </p>
                <div class="publish-urgent-chips">
                  <button
                    v-for="fee in r.urgentFeePresets"
                    :key="`urgent-fee-${fee}`"
                    type="button"
                    class="publish-urgent-chip"
                    :class="{ active: Number(r.urgentFee) === fee }"
                    :disabled="!r.isLoggedIn"
                    @click="r.pickUrgentFee(fee)"
                  >
                    ¥{{ fee }}
                  </button>
                </div>
                <div class="publish-urgent-custom-row">
                  <span class="publish-urgent-custom-label">自定义</span>
                  <input
                    class="publish-urgent-fee-input"
                    :class="{ 'is-invalid': !!r.urgentFeeError }"
                    type="text"
                    inputmode="decimal"
                    placeholder="如 2.5"
                    :disabled="!r.isLoggedIn"
                    :value="r.urgentFee"
                    @input="onUrgentFeeInput"
                  />
                </div>
                <p v-if="r.urgentFeeError" class="publish-urgent-error" role="alert">
                  {{ r.urgentFeeError }}
                </p>
                <div v-if="r.urgentFeeValid" class="publish-urgent-split">
                  <span>加急费 ¥{{ urgentFeeText }}</span>
                  <span>接单员 +¥{{ urgentRiderText }} · 推送费 ¥{{ urgentPushText }}</span>
                </div>
                <div v-if="r.urgentFeeValid" class="publish-urgent-split publish-urgent-split--total">
                  <span>预计实付</span>
                  <strong>¥{{ previewPayable }}</strong>
                </div>
              </div>
            </div>
          </div>
          <div class="form-field-row">
            <label class="form-field-label" for="publish-contact">联系方式</label>
            <div class="form-field-control">
              <input
                id="publish-contact"
                :value="r.form.contact"
                type="tel"
                readonly
                :disabled="!r.isLoggedIn"
                placeholder="自动使用注册手机号"
                title="联系方式固定为注册手机号"
                required
              />
              <p class="publish-contact-hint">自动使用账号绑定的注册手机号，不可修改</p>
            </div>
          </div>
          <div class="form-field-row form-field-row--textarea">
            <label class="form-field-label" for="publish-desc">备注</label>
            <div class="form-field-control">
              <textarea id="publish-desc" v-model="r.form.description" :disabled="!r.isLoggedIn" rows="5" placeholder="选填，可写时间要求等" />
            </div>
          </div>
          <p
            v-if="r.publishFormHint && r.publishFormHint.type === 'error'"
            data-publish-focus="hint"
            role="alert"
            class="publish-form-hint publish-form-hint--err error publish-form-hint--near-submit"
          >
            {{ r.publishFormHint.text }}
          </p>
          <button
            class="primary publish-submit-btn"
            type="button"
            :disabled="r.submitting || !r.isLoggedIn"
            @click="r.editingRejectedTaskId ? r.resubmitRejectedTask() : r.submitTask()"
          >
            {{ r.submitting ? '提交中...' : r.editingRejectedTaskId ? '重新提交' : '发布任务' }}
          </button>
          <p v-if="r.pendingAlipayTaskId" class="meta publish-bounty-tip">
            待同步任务 ID：{{ r.pendingAlipayTaskId }}。
            <button type="button" class="pk-btn pk-btn-ghost pk-btn--sm" @click="r.syncTaskAlipayPayment(r.pendingAlipayTaskId)">
              同步支付结果
            </button>
          </p>
        </form>
      </section>

      <div v-if="r.showPublishPaymentSheet" class="publish-pay-mask" @click.self="r.closePublishPaymentSheet()">
        <section class="publish-pay-sheet" role="dialog" aria-labelledby="publish-pay-title">
          <div class="publish-pay-handle" aria-hidden="true"></div>

          <div class="publish-pay-header">
            <button class="publish-pay-close" type="button" aria-label="关闭" @click="r.closePublishPaymentSheet()">
              ×
            </button>
            <p id="publish-pay-title" class="publish-pay-kicker">
              {{ r.publishPaymentSheetMode === 'resubmit' ? '重新提交并支付' : '确认支付赏金' }}
            </p>
            <p class="publish-pay-amount">
              <span class="publish-pay-currency">¥</span>{{ previewPayable }}
            </p>
          </div>

          <div class="publish-pay-order">
            <div class="publish-pay-order-row">
              <span class="publish-pay-order-label">任务</span>
              <strong class="publish-pay-order-value">{{ r.form.title || '任务赏金' }}</strong>
            </div>
            <div class="publish-pay-order-row">
              <span class="publish-pay-order-label">赏金</span>
              <strong class="publish-pay-order-value">¥{{ previewReward }}</strong>
            </div>
            <div v-if="r.urgentEnabled && r.urgentFeeValid" class="publish-pay-order-row">
              <span class="publish-pay-order-label">加急费</span>
              <strong class="publish-pay-order-value">¥{{ urgentFeeText }}</strong>
            </div>
            <div class="publish-pay-order-row">
              <span class="publish-pay-order-label">路线</span>
              <strong class="publish-pay-order-value publish-pay-order-value--route">
                {{ r.form.pickupLocation || r.pickupManualInput }} → {{ r.form.dropoffLocation || r.dropoffManualInput }}
              </strong>
            </div>
            <div v-if="r.publishCouponDiscount > 0" class="publish-pay-order-row">
              <span class="publish-pay-order-label">优惠券</span>
              <strong class="publish-pay-order-value publish-pay-order-value--discount">
                -¥{{ formatMoney(r.publishCouponDiscount) }}
              </strong>
            </div>
          </div>

          <p class="publish-pay-section-title">优惠券</p>
          <label class="publish-pay-coupon">
            <select
              class="publish-pay-coupon-select"
              :value="r.publishSelectedCouponId == null ? '' : String(r.publishSelectedCouponId)"
              :disabled="r.publishCouponLoading || r.submitting"
              @change="r.onPublishCouponSelected(($event.target as HTMLSelectElement).value)"
            >
              <option value="">不使用优惠券</option>
              <option
                v-for="c in r.publishPayCoupons"
                :key="`pay-coupon-${c.id}`"
                :value="String(c.id)"
              >
                {{ r.publishCouponOptionLabel(c) }}
              </option>
            </select>
            <span v-if="r.publishCouponLoading" class="publish-pay-coupon-hint">加载中…</span>
            <span v-else-if="!r.publishPayCoupons.length" class="publish-pay-coupon-hint">暂无可用优惠券</span>
          </label>

          <p class="publish-pay-section-title">选择支付方式</p>
          <div class="publish-pay-list">
            <label
              class="publish-pay-item"
              :class="{ 'is-active': r.publishPaymentMethod === 'alipay' }"
            >
              <input v-model="r.publishPaymentMethod" class="publish-pay-radio" type="radio" value="alipay" />
              <span class="publish-pay-icon-wrap">
                <img src="/pay-alipay.png" alt="" />
              </span>
              <span class="publish-pay-item-main">
                <span class="publish-pay-item-name">支付宝</span>
                <span class="publish-pay-item-desc">沙箱环境 · 推荐使用</span>
              </span>
              <span class="publish-pay-check" aria-hidden="true"></span>
            </label>

            <label
              class="publish-pay-item"
              :class="{
                'is-active': r.publishPaymentMethod === 'wallet',
                'is-disabled': r.publishWalletPayDisabled
              }"
              :title="r.publishWalletPayDisabled ? '钱包余额不足' : ''"
            >
              <input
                v-model="r.publishPaymentMethod"
                class="publish-pay-radio"
                type="radio"
                value="wallet"
                :disabled="r.publishWalletPayDisabled"
              />
              <span class="publish-pay-icon-wrap">
                <img src="/pay-wallet.png" alt="" />
              </span>
              <span class="publish-pay-item-main">
                <span class="publish-pay-item-name">钱包支付</span>
                <span class="publish-pay-item-desc">
                  可用余额 ¥{{ Number(r.walletOverview?.balance || 0).toFixed(2) }}
                  <template v-if="r.publishWalletPayDisabled"> · 余额不足</template>
                </span>
              </span>
              <span class="publish-pay-check" aria-hidden="true"></span>
            </label>

            <label class="publish-pay-item is-disabled" title="微信支付暂未开发">
              <input class="publish-pay-radio" type="radio" value="wechat" disabled />
              <span class="publish-pay-icon-wrap">
                <img src="/pay-wechat.png" alt="" />
              </span>
              <span class="publish-pay-item-main">
                <span class="publish-pay-item-name">微信支付</span>
                <span class="publish-pay-item-desc">功能开发中</span>
              </span>
              <span class="publish-pay-badge">暂未开放</span>
            </label>
          </div>

          <div class="publish-pay-actions">
            <button
              class="publish-pay-submit"
              type="button"
              :disabled="r.submitting"
              @click="r.confirmPublishPayment()"
            >
              {{
                r.submitting
                  ? r.publishPaymentMethod === 'alipay'
                    ? '正在跳转支付…'
                    : '正在支付…'
                  : `确认支付 ¥${previewPayable}`
              }}
            </button>
            <button class="publish-pay-cancel" type="button" @click="r.closePublishPaymentSheet()">暂不支付</button>
          </div>
        </section>
      </div>

      <div v-if="r.showDeliveryPicker" class="delivery-sheet-mask" @click.self="r.closeDeliveryPicker()">
        <section class="delivery-sheet-panel">
          <div class="delivery-sheet-head">
            <h3>选择配送时间</h3>
            <button class="ghost-local" type="button" @click="r.closeDeliveryPicker()">关闭</button>
          </div>
          <div class="delivery-sheet-body">
            <div class="delivery-sheet-date-col">
              <button
                v-for="d in r.deliveryDateOptions"
                :key="`delivery-date-${d.value}`"
                type="button"
                class="delivery-sheet-date-item"
                :class="{ active: r.selectedDeliveryDate === d.value }"
                @click="r.setSelectedDeliveryDate(d.value)"
              >
                {{ d.label }}
              </button>
            </div>
            <div class="delivery-sheet-time-col">
              <p class="delivery-sheet-col-title">可选时间</p>
              <div class="delivery-sheet-time-list">
                <button
                  v-for="opt in r.deliveryQuickOptions"
                  :key="`delivery-time-${opt.value}`"
                  type="button"
                  class="delivery-sheet-time-item"
                  :class="{ active: r.latestDeliveryQuickOption === opt.value }"
                  @click="r.setLatestDeliveryQuickOption(opt.value)"
                >
                  {{ opt.label }}
                </button>
              </div>
            </div>
          </div>
          <div class="delivery-sheet-foot">
            <button class="ghost-local" type="button" @click="r.closeDeliveryPicker()">完成</button>
          </div>
        </section>
      </div>
    </main>
</template>
