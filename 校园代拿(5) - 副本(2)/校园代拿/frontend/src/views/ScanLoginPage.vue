<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { authBase } from '../constants/api'

type AccountRow = { userId: number; username: string; avatarUrl?: string }

type PollResult = {
  status?: string
  message?: string
  needChooseAccount?: boolean
  needBindPhone?: boolean
  accounts?: AccountRow[]
  pickToken?: string
  token?: string
  username?: string
}

const ticket = ref('')
const phone = ref('')
const code = ref('')
const countdown = ref(0)
const submitting = ref(false)
const sending = ref(false)
const error = ref('')
const info = ref('')
const done = ref(false)
const pickAccounts = ref<AccountRow[]>([])
const pickToken = ref('')
const needBind = ref(false)

let countdownTimer: number | null = null

function parseHashQuery() {
  const raw = window.location.hash.replace(/^#\/?/, '')
  const qIndex = raw.indexOf('?')
  const qs = qIndex >= 0 ? raw.slice(qIndex + 1) : ''
  const params = new URLSearchParams(qs)
  ticket.value = (params.get('ticket') || '').trim()
  needBind.value = params.get('bind') === '1'
  if (params.get('done') === '1') {
    done.value = true
    info.value = '授权成功，请回到电脑端查看登录状态'
  }
  const err = params.get('error')
  if (err) {
    error.value = decodeURIComponent(err)
  }
}

async function refreshSessionHint() {
  if (!ticket.value || done.value) return
  try {
    const res = await fetch(`${authBase}/wechat/web/qr-session/${encodeURIComponent(ticket.value)}`)
    const data = (await res.json().catch(() => ({}))) as PollResult
    if (!res.ok) {
      error.value = (data as { message?: string }).message || '会话无效，请重新扫码'
      return
    }
    if (data.status === 'SUCCESS') {
      done.value = true
      info.value = '电脑端已登录成功，可以关闭本页'
      return
    }
    if (data.status === 'NEED_BIND' || data.needBindPhone) {
      needBind.value = true
      info.value = data.message || '首次微信登录，请用手机号短信验证绑定'
    }
    if (data.status === 'NEED_CHOOSE' && data.accounts?.length) {
      pickAccounts.value = data.accounts
      pickToken.value = data.pickToken || ''
      info.value = data.message || '请选择要登录的账号'
    }
    if (data.status === 'FAILED' || data.status === 'EXPIRED') {
      error.value = data.message || '二维码已失效，请回电脑端刷新'
    }
  } catch {
    error.value = '网络异常，请检查后重试'
  }
}

async function sendSms() {
  error.value = ''
  const p = phone.value.replace(/\D/g, '')
  if (!/^1\d{10}$/.test(p)) {
    error.value = '请输入 11 位手机号'
    return
  }
  if (countdown.value > 0 || sending.value) return
  sending.value = true
  try {
    const res = await fetch(`${authBase}/sms/send-login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: p })
    })
    const data = (await res.json().catch(() => ({}))) as { message?: string }
    if (!res.ok) {
      error.value = data.message || '发送失败'
      return
    }
    info.value = data.message || '验证码已发送'
    countdown.value = 60
    if (countdownTimer) window.clearInterval(countdownTimer)
    countdownTimer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && countdownTimer) {
        window.clearInterval(countdownTimer)
        countdownTimer = null
      }
    }, 1000)
  } catch {
    error.value = '发送验证码失败，请稍后重试'
  } finally {
    sending.value = false
  }
}

async function confirmSms() {
  error.value = ''
  if (!ticket.value) {
    error.value = '缺少扫码凭证，请重新扫码'
    return
  }
  const p = phone.value.replace(/\D/g, '')
  const c = code.value.trim()
  if (!/^1\d{10}$/.test(p) || !c) {
    error.value = '请填写手机号和验证码'
    return
  }
  submitting.value = true
  try {
    const res = await fetch(`${authBase}/wechat/web/confirm-sms`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId: ticket.value, phone: p, code: c })
    })
    const data = (await res.json().catch(() => ({}))) as PollResult & { message?: string }
    if (!res.ok) {
      error.value = data.message || '验证失败'
      return
    }
    if (data.status === 'NEED_CHOOSE' || data.needChooseAccount) {
      pickAccounts.value = data.accounts || []
      pickToken.value = data.pickToken || ''
      info.value = data.message || '请选择要登录的账号'
      return
    }
    if (data.status === 'SUCCESS') {
      done.value = true
      info.value = '登录成功，请回到电脑端继续使用'
      return
    }
    error.value = data.message || '确认失败，请重试'
  } catch {
    error.value = '网络异常，请稍后重试'
  } finally {
    submitting.value = false
  }
}

async function pickAccount(userId: number) {
  error.value = ''
  if (!ticket.value || !pickToken.value) {
    error.value = '请重新获取验证码后再选择'
    return
  }
  submitting.value = true
  try {
    const res = await fetch(`${authBase}/wechat/web/confirm-pick`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId: ticket.value, pickToken: pickToken.value, userId })
    })
    const data = (await res.json().catch(() => ({}))) as PollResult & { message?: string }
    if (!res.ok) {
      error.value = data.message || '选择失败'
      return
    }
    if (data.status === 'SUCCESS') {
      done.value = true
      info.value = '登录成功，请回到电脑端继续使用'
      return
    }
    error.value = data.message || '选择失败'
  } catch {
    error.value = '网络异常，请稍后重试'
  } finally {
    submitting.value = false
  }
}

const title = computed(() => {
  if (done.value) return '登录成功'
  if (pickAccounts.value.length) return '选择账号'
  if (needBind.value) return '绑定手机号'
  return '扫码确认登录'
})

onMounted(() => {
  parseHashQuery()
  void refreshSessionHint()
})

onUnmounted(() => {
  if (countdownTimer) window.clearInterval(countdownTimer)
})
</script>

<template>
  <div class="scan-login">
    <header class="scan-login__brand">
      <img class="scan-login__logo" src="/logo.png" alt="片刻帮" />
      <div>
        <strong>片刻帮</strong>
        <span>手机确认登录</span>
      </div>
    </header>

    <main class="scan-login__card">
      <h1>{{ title }}</h1>
      <p v-if="info" class="scan-login__info">{{ info }}</p>
      <p v-if="error" class="scan-login__error">{{ error }}</p>

      <template v-if="!done && pickAccounts.length">
        <button
          v-for="a in pickAccounts"
          :key="a.userId"
          type="button"
          class="scan-login__pick"
          :disabled="submitting"
          @click="pickAccount(a.userId)"
        >
          {{ a.username }} · ID {{ a.userId }}
        </button>
      </template>

      <template v-else-if="!done">
        <p class="scan-login__hint">
          用已注册手机号完成短信验证后，电脑端将自动登录。若配置了微信开放平台扫码，首次也需在此绑定手机号。
        </p>
        <label class="scan-login__field">
          <span>手机号</span>
          <input v-model="phone" type="tel" maxlength="11" placeholder="11 位手机号" />
        </label>
        <label class="scan-login__field">
          <span>验证码</span>
          <div class="scan-login__code-row">
            <input v-model="code" type="text" maxlength="6" placeholder="6 位验证码" />
            <button type="button" :disabled="countdown > 0 || sending" @click="sendSms">
              {{ sending ? '发送中' : countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </button>
          </div>
        </label>
        <button type="button" class="scan-login__submit" :disabled="submitting || !ticket" @click="confirmSms">
          {{ submitting ? '确认中…' : '确认登录电脑端' }}
        </button>
        <p v-if="!ticket" class="scan-login__error">链接缺少 ticket，请从电脑端二维码重新扫入</p>
      </template>
    </main>
  </div>
</template>

<style scoped>
.scan-login {
  min-height: 100vh;
  padding: 28px 18px 48px;
  background:
    radial-gradient(circle at 20% 10%, rgba(46, 125, 90, 0.18), transparent 42%),
    linear-gradient(160deg, #f3f7f4 0%, #e8f0ea 48%, #f7f4ee 100%);
  color: #1d2a22;
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}
.scan-login__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 22px;
}
.scan-login__logo {
  width: 44px;
  height: 44px;
  border-radius: 12px;
}
.scan-login__brand strong {
  display: block;
  font-size: 18px;
}
.scan-login__brand span {
  font-size: 13px;
  color: #5b6b61;
}
.scan-login__card {
  max-width: 420px;
  margin: 0 auto;
  padding: 22px 18px 24px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 16px 40px rgba(28, 52, 38, 0.08);
  backdrop-filter: blur(8px);
}
.scan-login__card h1 {
  margin: 0 0 10px;
  font-size: 22px;
}
.scan-login__hint,
.scan-login__info {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.55;
  color: #4d5f54;
}
.scan-login__error {
  margin: 0 0 12px;
  font-size: 13px;
  color: #b42318;
}
.scan-login__field {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;
  font-size: 13px;
}
.scan-login__field input {
  width: 100%;
  height: 44px;
  border: 1px solid #cfdad3;
  border-radius: 12px;
  padding: 0 12px;
  font-size: 16px;
  box-sizing: border-box;
}
.scan-login__code-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}
.scan-login__code-row button,
.scan-login__submit,
.scan-login__pick {
  border: 0;
  border-radius: 12px;
  background: #2f6b4f;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
.scan-login__code-row button {
  padding: 0 12px;
  white-space: nowrap;
}
.scan-login__code-row button:disabled,
.scan-login__submit:disabled,
.scan-login__pick:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.scan-login__submit {
  width: 100%;
  height: 46px;
  margin-top: 8px;
  font-weight: 600;
}
.scan-login__pick {
  width: 100%;
  text-align: left;
  padding: 12px 14px;
  margin-bottom: 8px;
  background: #eff6f1;
  color: #1d2a22;
}
</style>
