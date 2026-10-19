<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRiderApplyStore } from '../stores/useRiderApplyStore'
import type { RiderApplyViewInject } from '../context/riderApplyViewInject'

type RiderApplyRoot = RiderApplyViewInject & {
  goHall?: () => void
  goProfile?: () => void
}

const r = useRiderApplyStore()
const localApplyError = ref('')
const applyErrorText = computed(() => localApplyError.value || r.riderApplyError || '')

const statusLabel = computed(() => {
  if (r.isRiderApproved) return '已认证接单员'
  if (r.riderApplicationPending) return '接单员审核中'
  return '普通用户'
})

const statusBadgeClass = computed(() => {
  if (r.isRiderApproved) return 'pk-badge--open'
  if (r.riderApplicationPending) return 'pk-badge--busy'
  return 'pk-badge--reject'
})

async function submitRiderApply() {
  const payload = {
    realName: String(r.riderApplyForm.realName || '').trim(),
    studentId: String(r.riderApplyForm.studentId || '').trim(),
    phone: String(r.riderApplyForm.phone || '').trim(),
    reason: String(r.riderApplyForm.reason || '').trim()
  }
  if (!payload.realName || !payload.studentId || !payload.phone || !payload.reason) {
    localApplyError.value = '请填写完整接单员申请信息'
    return
  }
  if (payload.realName.length < 2) {
    localApplyError.value = '真实姓名至少2个字'
    return
  }
  if (!/^\d{6,20}$/.test(payload.studentId)) {
    localApplyError.value = '学号格式不正确（6-20位数字）'
    return
  }
  if (!/^1\d{10}$/.test(payload.phone)) {
    localApplyError.value = '手机号格式不正确（需11位手机号）'
    return
  }
  if (payload.reason.length < 5) {
    localApplyError.value = '申请理由至少5个字'
    return
  }
  localApplyError.value = ''
  await r.applyRider()
}
</script>

<template>
  <main class="pk-rider-apply">
    <aside class="pk-rider-aside">
      <p class="pk-profile-kicker">Courier Apply</p>
      <h2>申请说明</h2>
      <ul class="pk-rider-guide">
        <li>接单员资格需管理员审核后生效</li>
        <li>审核通过后你才能在任务大厅接单</li>
        <li>请填写真实信息，便于人工核验</li>
        <li>若被驳回可修改后再次申请</li>
      </ul>
      <p class="pk-rider-aside-tip">审核一般在 1–3 个工作日内完成，请留意消息通知。</p>
    </aside>

    <section class="pk-profile-card pk-rider-main">
      <header class="pk-rider-head">
        <div>
          <p class="pk-profile-kicker">成为接单员</p>
          <h1>接单员申请</h1>
          <p class="pk-profile-lead">认证通过后即可在任务大厅接单赚取赏金。</p>
        </div>
        <button v-if="r.goProfile" type="button" class="pk-btn pk-btn-ghost pk-btn-sm" @click="r.goProfile!()">
          返回我的
        </button>
      </header>

      <div class="pk-rider-status">
        <span>当前状态</span>
        <em class="pk-badge" :class="statusBadgeClass">{{ statusLabel }}</em>
      </div>

      <div v-if="!r.isRiderApproved && !r.riderApplicationPending" class="pk-rider-form">
        <p class="pk-account-tip">填写提醒：姓名至少 2 字；学号 6–20 位数字；手机号 11 位；理由至少 5 字。</p>
        <p v-if="applyErrorText" class="pk-alert pk-alert--error">{{ applyErrorText }}</p>

        <div class="pk-form-field">
          <label>真实姓名</label>
          <input v-model="r.riderApplyForm.realName" placeholder="真实姓名（至少 2 字）" />
        </div>
        <div class="pk-form-field">
          <label>学号</label>
          <input v-model="r.riderApplyForm.studentId" placeholder="学号（6–20 位数字）" />
        </div>
        <div class="pk-form-field">
          <label>手机号</label>
          <input v-model="r.riderApplyForm.phone" placeholder="手机号（11 位）" />
        </div>
        <div class="pk-form-field">
          <label>申请理由</label>
          <textarea
            v-model="r.riderApplyForm.reason"
            rows="4"
            maxlength="300"
            placeholder="申请理由（至少 5 字，最多 300 字）"
          />
        </div>

        <div class="pk-account-actions">
          <button type="button" class="pk-btn pk-btn-primary" @click="submitRiderApply">提交接单员申请</button>
        </div>
      </div>

      <div v-else-if="r.riderApplicationPending" class="pk-rider-state">
        <p>申请已提交，请等待管理员审核。</p>
        <p class="pk-account-tip">审核期间你仍可发布任务，暂不能接单。</p>
      </div>

      <div v-else class="pk-rider-state pk-rider-state--ok">
        <p>你当前已具备接单权限，可直接前往任务大厅接单。</p>
        <div class="pk-account-actions">
          <button v-if="r.goHall" type="button" class="pk-btn pk-btn-primary" @click="r.goHall!()">前往任务大厅</button>
        </div>
      </div>
    </section>
  </main>
</template>
