<script setup lang="ts">
import { usePersonalInfoStore } from '../stores/usePersonalInfoStore'
import type { PersonalInfoViewInject } from '../context/personalInfoViewInject'

type AccountRoot = PersonalInfoViewInject & {
  goProfile?: () => void
}

const r = usePersonalInfoStore()

const ACCOUNT_TABS = [
  { key: 'base' as const, label: '基本资料' },
  { key: 'avatar' as const, label: '我的头像' },
  { key: 'password' as const, label: '密码管理' },
  { key: 'apps' as const, label: '应用管理' },
  { key: 'lang' as const, label: '语言' }
]

function maskPhone(p: string) {
  const s = String(p || '').trim()
  if (s.length >= 11) return `${s.slice(0, 3)}****${s.slice(-4)}`
  if (s.length >= 7) return `${s.slice(0, 2)}****${s.slice(-2)}`
  return s || '—'
}

function setTab(key: PersonalInfoViewInject['personalInfoTab']) {
  r.personalInfoTab = key
}
</script>

<template>
  <main class="pk-account">
    <section class="pk-profile-card pk-account-card">
      <header class="pk-account-head">
        <div>
          <p class="pk-profile-kicker">账号设置</p>
          <h1>账号管理</h1>
          <p class="pk-profile-lead">管理你的基本资料与账号安全设置</p>
        </div>
        <button v-if="r.goProfile" type="button" class="pk-btn pk-btn-ghost pk-btn-sm" @click="r.goProfile!()">
          返回我的
        </button>
      </header>

      <div class="pk-account-tabs" role="tablist" aria-label="账号设置分类">
        <div class="pk-tab-group pk-tab-group--scroll">
          <button
            v-for="tab in ACCOUNT_TABS"
            :key="tab.key"
            type="button"
            class="pk-tab"
            role="tab"
            :class="{ active: r.personalInfoTab === tab.key }"
            :aria-selected="r.personalInfoTab === tab.key"
            @click="setTab(tab.key)"
          >
            {{ tab.label }}
          </button>
        </div>
      </div>

      <p
        v-if="r.errorMsg && (r.personalInfoTab === 'base' || r.personalInfoTab === 'avatar' || r.personalInfoTab === 'password')"
        class="pk-alert pk-alert--error"
      >
        {{ r.errorMsg }}
      </p>
      <p
        v-if="r.profileNotice && (r.personalInfoTab === 'base' || r.personalInfoTab === 'avatar')"
        class="pk-profile-notice"
        role="status"
      >
        {{ r.profileNotice }}
      </p>

      <div v-if="r.personalInfoTab === 'base'" class="pk-account-body">
        <div class="pk-account-fields">
          <div class="pk-account-field">
            <span class="pk-account-label">昵称</span>
            <input
              v-model="r.profileForm.username"
              class="pk-account-input"
              maxlength="12"
              placeholder="登录后展示的名称"
            />
          </div>
          <div class="pk-account-field">
            <span class="pk-account-label">真实姓名</span>
            <input
              v-model="r.personalInfoForm.realName"
              class="pk-account-input"
              maxlength="20"
              placeholder="至少2个字"
            />
          </div>
          <div class="pk-account-field">
            <span class="pk-account-label">登录账号 ID</span>
            <span class="pk-account-value">{{ r.personalInfoForm.userId != null ? r.personalInfoForm.userId : '—' }}</span>
          </div>
          <div class="pk-account-field">
            <span class="pk-account-label">学号</span>
            <input
              v-model="r.personalInfoForm.studentId"
              class="pk-account-input"
              maxlength="32"
              placeholder="选填"
            />
          </div>
          <div class="pk-account-field">
            <span class="pk-account-label">性别</span>
            <div class="pk-account-radio">
              <label>
                <input type="radio" value="male" v-model="r.personalInfoForm.gender" />
                男
              </label>
              <label>
                <input type="radio" value="female" v-model="r.personalInfoForm.gender" />
                女
              </label>
            </div>
          </div>
          <div class="pk-account-field">
            <span class="pk-account-label">手机号</span>
            <span class="pk-account-value">{{ r.personalInfoForm.phone ? maskPhone(r.personalInfoForm.phone) : '—' }}</span>
            <button type="button" class="pk-account-link">修改</button>
          </div>
        </div>

        <div class="pk-account-actions">
          <button type="button" class="pk-btn pk-btn-primary" @click="r.savePersonalBaseInfo()">保存基本资料</button>
        </div>
      </div>

      <div v-else-if="r.personalInfoTab === 'avatar'" class="pk-account-body">
        <div class="pk-account-avatar-block">
          <span class="pk-account-label">当前头像</span>
          <div class="pk-account-avatar-picker">
            <img
              v-if="r.profileForm.avatarUrl || r.myAvatarUrl"
              :src="r.normalizeImageUrl(r.profileForm.avatarUrl || r.myAvatarUrl)"
              class="pk-profile-avatar pk-account-avatar-preview"
              alt="avatar"
              title="点击更换头像"
              role="button"
              tabindex="0"
              @click="r.triggerAvatarFilePick"
              @keydown.enter.prevent="r.triggerAvatarFilePick"
              @keydown.space.prevent="r.triggerAvatarFilePick"
            />
            <div
              v-else
              class="pk-profile-avatar pk-profile-avatar-fallback pk-account-avatar-preview"
              title="点击上传头像"
              role="button"
              tabindex="0"
              @click="r.triggerAvatarFilePick"
              @keydown.enter.prevent="r.triggerAvatarFilePick"
              @keydown.space.prevent="r.triggerAvatarFilePick"
            >
              {{ (r.myName || 'U').slice(0, 1).toUpperCase() }}
            </div>
            <p class="pk-account-avatar-hint">点击头像上传本地图片</p>
          </div>
        </div>

        <input
          :ref="r.bindAvatarFileInput"
          type="file"
          accept="image/*"
          class="visually-hidden-file-input"
          @change="r.onAvatarFileSelected"
        />

        <p class="pk-account-tip">头像需管理员审核后才会生效。</p>
        <p class="pk-account-tip">
          当前状态：{{ r.myAvatarPending ? '审核中（通过后自动替换）' : '已通过' }}
        </p>

        <div class="pk-account-actions">
          <button type="button" class="pk-btn pk-btn-primary" @click="r.savePersonalAvatar">
            {{ r.myAvatarPending ? '重新提交头像审核' : '保存头像并提交审核' }}
          </button>
        </div>
      </div>

      <div v-else-if="r.personalInfoTab === 'password'" class="pk-account-body">
        <div class="pk-account-form">
          <div class="pk-form-field">
            <label>旧密码</label>
            <input v-model="r.passwordForm.oldPassword" type="password" autocomplete="current-password" />
          </div>

          <div class="pk-form-field">
            <label>新的密码</label>
            <input v-model="r.passwordForm.newPassword" type="password" autocomplete="new-password" />
            <p class="pk-account-tip">密码要求 8–16 位，至少包含数字、字母两种元素</p>
          </div>

          <div class="pk-form-field">
            <label>确认新密码</label>
            <input v-model="r.passwordForm.confirmNewPassword" type="password" autocomplete="new-password" />
          </div>

          <div class="pk-form-field pk-account-captcha">
            <label>验证码</label>
            <div class="pk-account-captcha-row">
              <canvas
                :ref="r.bindPasswordCaptchaCanvas"
                class="pk-account-captcha-canvas"
                width="130"
                height="42"
                @click="r.refreshPasswordCaptcha"
              />
              <input v-model="r.passwordCaptchaInput" placeholder="请输入验证码" />
            </div>
          </div>

          <div class="pk-account-actions">
            <button type="button" class="pk-btn pk-btn-primary" @click="r.changePassword">保存密码</button>
          </div>
        </div>
      </div>

      <div v-else class="pk-account-placeholder">
        <p>该功能后续开放</p>
      </div>
    </section>
  </main>
</template>
