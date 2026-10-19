<script setup lang="ts">
import { computed, ref, watch, watchEffect } from 'vue'
import { useWalletStore } from '../stores/useWalletStore'

const r = useWalletStore()

const recordTab = ref<'transactions' | 'withdraws'>('transactions')
const txPage = ref(1)
const txPageSize = 10
const wdPage = ref(1)
const wdPageSize = 8

watch(
  () => r.walletSubModule,
  (sub) => {
    if (sub === 'withdraws' || sub === 'transactions') recordTab.value = sub
  },
  { immediate: true }
)

const txTotalPages = computed(() => Math.max(1, Math.ceil((r.walletTransactions?.length || 0) / txPageSize)))
const wdTotalPages = computed(() => Math.max(1, Math.ceil((r.walletWithdraws?.length || 0) / wdPageSize)))

const pagedWalletTransactions = computed(() => {
  const start = (txPage.value - 1) * txPageSize
  return (r.walletTransactions || []).slice(start, start + txPageSize)
})

const pagedWalletWithdraws = computed(() => {
  const start = (wdPage.value - 1) * wdPageSize
  return (r.walletWithdraws || []).slice(start, start + wdPageSize)
})

function formatMoney(value: number) {
  return Number(value || 0).toFixed(2)
}

function amountClass(value: number) {
  return Number(value) >= 0 ? 'pk-wallet-amount--plus' : 'pk-wallet-amount--minus'
}

function withdrawBadgeModifier(status: string) {
  if (status === 'PENDING') return 'pk-badge--busy'
  if (status === 'APPROVED') return 'pk-badge--open'
  return 'pk-badge--reject'
}

function goTxPrev() {
  txPage.value = Math.max(1, txPage.value - 1)
}
function goTxNext() {
  txPage.value = Math.min(txTotalPages.value, txPage.value + 1)
}
function goWdPrev() {
  wdPage.value = Math.max(1, wdPage.value - 1)
}
function goWdNext() {
  wdPage.value = Math.min(wdTotalPages.value, wdPage.value + 1)
}

watchEffect(() => {
  if (txPage.value > txTotalPages.value) txPage.value = txTotalPages.value
  if (wdPage.value > wdTotalPages.value) wdPage.value = wdTotalPages.value
})
</script>

<template>
  <main class="pk-wallet">
    <section class="pk-wallet-card">
      <header class="pk-wallet-head">
        <div>
          <h1>我的钱包</h1>
          <p class="pk-wallet-lead">查看余额、流水，并申请提现。</p>
          <p class="pk-wallet-account">当前账号：{{ r.myName }}</p>
        </div>
        <div class="pk-wallet-head-actions">
          <button type="button" class="pk-btn pk-btn-ghost" :disabled="r.walletLoading" @click="r.fetchWalletData">
            {{ r.walletLoading ? '刷新中…' : '刷新钱包' }}
          </button>
          <button
            type="button"
            class="pk-btn pk-btn-ghost"
            :disabled="r.walletLoading"
            title="余额与任务收入对不上时再用：扫已完成任务补漏记"
            @click="r.rebuildWalletData"
          >
            重建账本
          </button>
        </div>
      </header>

      <div class="pk-wallet-summary">
        <article class="pk-wallet-stat pk-wallet-stat--primary">
          <span>可用余额</span>
          <strong>¥ {{ formatMoney(r.walletOverview.balance) }}</strong>
        </article>
        <article class="pk-wallet-stat">
          <span>累计收入</span>
          <strong>¥ {{ formatMoney(r.walletOverview.totalIncome) }}</strong>
        </article>
        <article class="pk-wallet-stat">
          <span>已提现</span>
          <strong>¥ {{ formatMoney(r.walletOverview.totalWithdrawn) }}</strong>
        </article>
        <article class="pk-wallet-stat">
          <span>提现中</span>
          <strong>¥ {{ formatMoney(r.walletOverview.withdrawingAmount) }}</strong>
        </article>
      </div>

      <p class="pk-wallet-tip">
        发布任务会冻结赏金，任务完成后记入接单员钱包；所有用户均可查看余额与流水。
        余额不足时可在下方使用支付宝沙箱充值。
        <template v-if="r.isRiderApproved">提现需管理员审核。</template>
        <template v-else>仅已认证接单员可申请提现。</template>
      </p>

      <section class="pk-wallet-block" aria-labelledby="pk-recharge-title">
        <h2 id="pk-recharge-title">支付宝充值（沙箱）</h2>
        <p class="pk-wallet-tip">
          使用支付宝开放平台沙箱账号完成支付。支付成功后会自动跳回钱包并同步余额；若未自动到账，可点击「同步支付结果」。
        </p>
        <form class="pk-wallet-form" @submit.prevent="r.submitAlipayRecharge">
          <p
            v-if="r.alipayRechargeHint"
            class="pk-wallet-hint"
            :class="r.alipayRechargeHint.type === 'success' ? 'pk-wallet-hint--ok' : 'pk-wallet-hint--err'"
          >
            {{ r.alipayRechargeHint.text }}
          </p>
          <div class="pk-wallet-form-grid pk-wallet-form-grid--two">
            <div class="pk-form-field">
              <label for="wallet-recharge-amount">充值金额</label>
              <input
                id="wallet-recharge-amount"
                v-model.number="r.walletRechargeForm.amount"
                type="number"
                min="0.01"
                step="0.01"
                placeholder="请输入金额"
                required
              />
            </div>
            <div class="pk-form-field">
              <label for="wallet-recharge-order">待同步订单</label>
              <input
                id="wallet-recharge-order"
                :value="r.pendingAlipayOutTradeNo || '暂无'"
                readonly
              />
            </div>
          </div>
          <div class="pk-wallet-form-actions">
            <button class="pk-btn pk-btn-accent" type="submit" :disabled="r.alipayRechargeSubmitting">
              {{ r.alipayRechargeSubmitting ? '跳转中…' : '支付宝沙箱充值' }}
            </button>
            <button
              type="button"
              class="pk-btn pk-btn-ghost"
              :disabled="r.alipaySyncSubmitting"
              @click="r.syncAlipayRecharge()"
            >
              {{ r.alipaySyncSubmitting ? '同步中…' : '同步支付结果' }}
            </button>
          </div>
        </form>
      </section>

      <section class="pk-wallet-block" aria-labelledby="pk-withdraw-title">
        <h2 id="pk-withdraw-title">申请提现</h2>

        <form
          v-if="r.isRiderApproved"
          class="pk-wallet-form"
          @submit.prevent="r.submitWithdrawApply"
        >
          <p
            v-if="r.walletWithdrawHint"
            class="pk-wallet-hint"
            :class="r.walletWithdrawHint.type === 'success' ? 'pk-wallet-hint--ok' : 'pk-wallet-hint--err'"
          >
            {{ r.walletWithdrawHint.text }}
          </p>
          <div class="pk-wallet-rules">
            <p><strong>提现规则</strong></p>
            <ul>
              <li>单笔最低 <em>¥1.00</em>，金额按 <em>¥0.1</em> 递增（如 1.0、1.1、1.2）</li>
              <li>
                本次最多可提
                <em>¥{{ Number(r.walletMaxWithdrawable || 0).toFixed(1) }}</em>
                （不超过可用余额，单笔上限 ¥{{ r.walletWithdrawHardMax }}）
              </li>
              <li>提现金额 <em>低于 ¥100</em> 时，每笔收取手续费 <em>¥0.10</em>；≥ ¥100 免手续费</li>
              <li>手续费从提现金额中扣除：实际到账 = 提现金额 − 手续费</li>
            </ul>
          </div>
          <div class="pk-wallet-form-grid pk-wallet-form-grid--two">
            <div class="pk-form-field">
              <div class="pk-wallet-amount-label">
                <label for="wallet-withdraw-amount">提现金额</label>
                <button
                  v-if="Number(r.walletMaxWithdrawable || 0) >= r.walletWithdrawMin"
                  type="button"
                  class="pk-wallet-max-btn"
                  @click="r.fillWalletMaxWithdraw"
                >
                  全部提现 ¥{{ Number(r.walletMaxWithdrawable || 0).toFixed(1) }}
                </button>
              </div>
              <input
                id="wallet-withdraw-amount"
                :value="r.walletWithdrawForm.amountText"
                type="text"
                inputmode="decimal"
                autocomplete="off"
                :placeholder="
                  Number(r.walletMaxWithdrawable || 0) >= r.walletWithdrawMin
                    ? `最多 ${Number(r.walletMaxWithdrawable || 0).toFixed(1)}`
                    : '余额不足最低提现额'
                "
                :disabled="Number(r.walletMaxWithdrawable || 0) < r.walletWithdrawMin"
                required
                @input="r.onWalletWithdrawAmountInput"
                @blur="r.normalizeWalletWithdrawAmount"
              />
              <p class="pk-wallet-fee-line">
                手续费 ¥{{ Number(r.walletWithdrawFeePreview || 0).toFixed(2) }}
                · 预计到账 ¥{{ Number(r.walletWithdrawPayoutPreview || 0).toFixed(2) }}
              </p>
            </div>
            <div class="pk-form-field">
              <label for="wallet-withdraw-name">收款户名</label>
              <input
                id="wallet-withdraw-name"
                v-model="r.walletWithdrawForm.accountName"
                placeholder="收款人姓名"
                required
              />
            </div>
          </div>
          <div class="pk-wallet-form-grid pk-wallet-form-grid--two">
            <div class="pk-form-field">
              <label for="wallet-withdraw-account">收款账号</label>
              <input
                id="wallet-withdraw-account"
                v-model="r.walletWithdrawForm.accountNo"
                placeholder="银行卡 / 支付宝等"
                required
              />
            </div>
            <div class="pk-form-field">
              <label for="wallet-withdraw-remark">备注</label>
              <input
                id="wallet-withdraw-remark"
                v-model="r.walletWithdrawForm.remark"
                placeholder="可选"
              />
            </div>
          </div>
          <div class="pk-wallet-form-actions">
            <button
              class="pk-btn pk-btn-accent"
              type="submit"
              :disabled="r.withdrawSubmitting || Number(r.walletMaxWithdrawable || 0) < r.walletWithdrawMin"
            >
              {{ r.withdrawSubmitting ? '提交中…' : '提交提现申请' }}
            </button>
          </div>
        </form>

        <div v-else class="pk-wallet-locked">
          <p v-if="r.riderApplicationPending">
            你的接单员申请正在审核中，通过后即可在此申请提现。
          </p>
          <p v-else>
            当前账号未认证为接单员，暂不能提现。发布者仍可查看余额与流水；需要提现请先完成接单员认证。
          </p>
          <button type="button" class="pk-btn pk-btn-ghost" @click="r.goRiderApply?.()">
            去申请接单员
          </button>
        </div>
      </section>

      <section class="pk-wallet-block" aria-labelledby="pk-records-title">
        <div class="pk-wallet-records-head">
          <h2 id="pk-records-title">最近流水</h2>
          <div class="pk-tab-group" role="tablist" aria-label="流水类型">
            <button
              type="button"
              class="pk-tab"
              :class="{ active: recordTab === 'transactions' }"
              role="tab"
              :aria-selected="recordTab === 'transactions'"
              @click="recordTab = 'transactions'"
            >
              账单明细
            </button>
            <button
              type="button"
              class="pk-tab"
              :class="{ active: recordTab === 'withdraws' }"
              role="tab"
              :aria-selected="recordTab === 'withdraws'"
              @click="recordTab = 'withdraws'"
            >
              提现记录
            </button>
          </div>
        </div>

        <div v-if="recordTab === 'transactions'" class="pk-wallet-list">
          <p v-if="r.walletLoading" class="pk-wallet-empty">流水加载中…</p>
          <p v-else-if="!r.walletTransactions.length" class="pk-wallet-empty">暂无流水</p>
          <article v-for="tx in pagedWalletTransactions" :key="`tx-${tx.id}`" class="pk-wallet-list-item">
            <div class="pk-wallet-list-head">
              <h3>{{ r.walletTypeLabel(tx.type) }}</h3>
              <strong :class="amountClass(tx.amount)">
                {{ Number(tx.amount) >= 0 ? '+' : '' }}{{ formatMoney(tx.amount) }}
              </strong>
            </div>
            <div class="pk-wallet-list-meta">
              <span v-if="tx.remark">{{ tx.remark }}</span>
              <span>余额 ¥ {{ formatMoney(tx.balanceAfter) }}</span>
              <span>{{ tx.createdAt }}</span>
            </div>
            <div v-if="tx.taskId && tx.type === 'TASK_INCOME'" class="pk-wallet-list-actions">
              <button type="button" class="pk-btn pk-btn-ghost pk-btn--sm" @click="r.openWalletTaskDetail(tx)">
                查看任务
              </button>
            </div>
          </article>
          <div v-if="r.walletTransactions.length > txPageSize" class="pk-wallet-pagination">
            <button type="button" class="pk-btn pk-btn-ghost pk-btn--sm" :disabled="txPage <= 1" @click="goTxPrev">
              上一页
            </button>
            <span>第 {{ txPage }} / {{ txTotalPages }} 页</span>
            <button
              type="button"
              class="pk-btn pk-btn-ghost pk-btn--sm"
              :disabled="txPage >= txTotalPages"
              @click="goTxNext"
            >
              下一页
            </button>
          </div>
        </div>

        <div v-else class="pk-wallet-list">
          <p v-if="r.walletLoading" class="pk-wallet-empty">记录加载中…</p>
          <p v-else-if="!r.walletWithdraws.length" class="pk-wallet-empty">暂无提现记录</p>
          <article v-for="w in pagedWalletWithdraws" :key="`wd-${w.id}`" class="pk-wallet-list-item">
            <div class="pk-wallet-list-head">
              <h3>提现 ¥ {{ formatMoney(w.amount) }}</h3>
              <span class="pk-badge" :class="withdrawBadgeModifier(w.status)">
                {{ r.withdrawStatusLabel(w.status) }}
              </span>
            </div>
            <div class="pk-wallet-list-meta">
              <span>收款人 {{ w.accountName || '—' }}</span>
              <span>账号 {{ w.accountNo || '—' }}</span>
              <span>{{ w.createdAt }}</span>
            </div>
            <p v-if="w.status === 'REJECTED' && w.auditRemark" class="pk-wallet-list-remark">
              驳回原因：{{ w.auditRemark }}
            </p>
          </article>
          <div v-if="r.walletWithdraws.length > wdPageSize" class="pk-wallet-pagination">
            <button type="button" class="pk-btn pk-btn-ghost pk-btn--sm" :disabled="wdPage <= 1" @click="goWdPrev">
              上一页
            </button>
            <span>第 {{ wdPage }} / {{ wdTotalPages }} 页</span>
            <button
              type="button"
              class="pk-btn pk-btn-ghost pk-btn--sm"
              :disabled="wdPage >= wdTotalPages"
              @click="goWdNext"
            >
              下一页
            </button>
          </div>
        </div>
      </section>
    </section>
  </main>
</template>
