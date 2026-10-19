<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CsUserContextPayload } from '../types/csContext'
import type { TaskInquiryData } from '../utils/taskInquiryMessage'

const props = defineProps<{
  username: string
  loading: boolean
  error: string
  context: CsUserContextPayload | null
  handoffReason: string
  note: string
  waitLabel: string
  serveLabel: string
  overdue: boolean
  chatOrders: TaskInquiryData[]
}>()

const emit = defineEmits<{
  'update:note': [value: string]
  refresh: []
}>()

const localNote = ref(props.note)
watch(
  () => props.note,
  (v) => {
    localNote.value = v
  }
)

const profile = computed(() => props.context?.profile)
const wallet = computed(() => props.context?.wallet)
const tasks = computed(() => props.context?.tasks || [])

function money(v: unknown) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return `¥${n.toFixed(2)}`
}

function onNoteBlur() {
  emit('update:note', localNote.value)
}
</script>

<template>
  <aside class="ctx-panel" aria-label="用户上下文">
    <header class="ctx-head">
      <div>
        <strong>用户上下文</strong>
        <p v-if="username">{{ username }}</p>
        <p v-else>选择左侧用户后显示</p>
      </div>
      <button v-if="username" type="button" class="ctx-refresh" :disabled="loading" @click="emit('refresh')">
        {{ loading ? '…' : '刷新' }}
      </button>
    </header>

    <div v-if="!username" class="ctx-empty">选中会话后，这里会显示订单、钱包与备注。</div>
    <div v-else class="ctx-body">
      <section class="ctx-card" :class="{ overdue }">
        <h3>接待计时</h3>
        <dl class="ctx-dl">
          <div><dt>等待</dt><dd>{{ waitLabel }}</dd></div>
          <div><dt>接待中</dt><dd>{{ serveLabel }}</dd></div>
        </dl>
        <p v-if="overdue" class="ctx-warn">超时未回复，请尽快跟进</p>
      </section>

      <section class="ctx-card">
        <h3>资料</h3>
        <dl class="ctx-dl">
          <div>
            <dt>身份</dt>
            <dd>{{ profile?.riderApproved ? '认证接单员' : '普通用户' }}</dd>
          </div>
          <div>
            <dt>手机</dt>
            <dd>{{ profile?.phoneMasked || '—' }}</dd>
          </div>
          <div>
            <dt>信用分</dt>
            <dd>{{ profile?.creditScore ?? '—' }}</dd>
          </div>
        </dl>
      </section>

      <section class="ctx-card">
        <h3>转人工原因</h3>
        <p class="ctx-text">{{ handoffReason || '暂无记录（多为「用户请求人工客服协助」）' }}</p>
      </section>

      <section class="ctx-card">
        <h3>钱包概况</h3>
        <p v-if="loading" class="ctx-muted">加载中…</p>
        <p v-else-if="error" class="ctx-warn">{{ error }}</p>
        <p v-else-if="wallet && wallet.available === false" class="ctx-warn">{{ wallet.error || '暂不可用' }}</p>
        <dl v-else class="ctx-dl">
          <div><dt>余额</dt><dd>{{ money(wallet?.balance) }}</dd></div>
          <div><dt>累计收入</dt><dd>{{ money(wallet?.totalIncome) }}</dd></div>
          <div><dt>提现中</dt><dd>{{ money(wallet?.withdrawingAmount) }}</dd></div>
          <div><dt>已提现</dt><dd>{{ money(wallet?.totalWithdrawn) }}</dd></div>
        </dl>
      </section>

      <section class="ctx-card">
        <h3>最近订单</h3>
        <p v-if="loading" class="ctx-muted">加载中…</p>
        <ul v-else-if="tasks.length" class="ctx-list">
          <li v-for="t in tasks" :key="`task-${t.id}`">
            <em>#{{ t.id }} {{ t.title || '任务' }}</em>
            <small>{{ t.statusLabel || t.status || '—' }} · {{ t.roleLabel || t.role || '—' }} · {{ money(t.reward) }}</small>
          </li>
        </ul>
        <p v-else class="ctx-muted">接口暂无订单，可看下方会话内订单卡</p>
        <ul v-if="chatOrders.length" class="ctx-list ctx-list--chat">
          <li v-for="o in chatOrders" :key="`chat-ord-${o.taskId}`">
            <em>会话 #{{ o.taskId }} {{ o.title }}</em>
            <small>{{ o.status || '—' }} · {{ o.reward || '—' }}</small>
          </li>
        </ul>
      </section>

      <section class="ctx-card">
        <h3>内部备注 <small>仅客服可见</small></h3>
        <textarea
          v-model="localNote"
          rows="4"
          placeholder="如：已核实退款中、用户要求今晚回电…"
          @blur="onNoteBlur"
        />
      </section>
    </div>
  </aside>
</template>

<style scoped>
.ctx-panel {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-height: 0;
  background: #fff;
  border-left: 1px solid #eceae8;
  overflow: hidden;
}

.ctx-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 16px 14px 12px;
  border-bottom: 1px solid #f0eeec;
}

.ctx-head strong {
  display: block;
  font-size: 14px;
  color: #1c1917;
}

.ctx-head p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #78716c;
  word-break: break-all;
}

.ctx-refresh {
  border: 1px solid #fed7aa;
  background: #fff7ed;
  color: #c2410c;
  border-radius: 8px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.ctx-refresh:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ctx-empty {
  margin: 24px 16px;
  padding: 16px;
  border-radius: 12px;
  background: #fafaf9;
  color: #78716c;
  font-size: 13px;
  line-height: 1.55;
}

.ctx-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px;
  display: grid;
  gap: 10px;
  align-content: start;
}

.ctx-card {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid #f0eeec;
  background: #fafaf9;
}

.ctx-card.overdue {
  border-color: #fecaca;
  background: #fef2f2;
}

.ctx-card h3 {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 800;
  color: #9a3412;
  letter-spacing: 0.04em;
}

.ctx-card h3 small {
  margin-left: 6px;
  font-weight: 500;
  color: #a8a29e;
}

.ctx-dl {
  margin: 0;
  display: grid;
  gap: 6px;
}

.ctx-dl > div {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
}

.ctx-dl dt {
  color: #a8a29e;
}

.ctx-dl dd {
  margin: 0;
  color: #1c1917;
  font-weight: 600;
  text-align: right;
  word-break: break-all;
}

.ctx-text,
.ctx-muted,
.ctx-warn {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

.ctx-text {
  color: #44403c;
}

.ctx-muted {
  color: #a8a29e;
}

.ctx-warn {
  color: #b91c1c;
  font-weight: 600;
}

.ctx-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 8px;
}

.ctx-list li {
  display: grid;
  gap: 2px;
}

.ctx-list em {
  font-style: normal;
  font-size: 12px;
  font-weight: 700;
  color: #1c1917;
}

.ctx-list small {
  font-size: 11px;
  color: #78716c;
}

.ctx-list--chat {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e7e5e4;
}

.ctx-card textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #e7e5e4;
  border-radius: 10px;
  padding: 8px 10px;
  font: inherit;
  font-size: 12px;
  line-height: 1.5;
  resize: vertical;
  background: #fff;
  color: #1c1917;
}

.ctx-card textarea:focus {
  outline: none;
  border-color: #fb923c;
  box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.15);
}
</style>
