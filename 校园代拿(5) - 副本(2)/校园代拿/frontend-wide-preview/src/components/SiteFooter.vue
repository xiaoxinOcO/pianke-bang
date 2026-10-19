<script setup lang="ts">
import { useShellStore } from '../stores/useShellStore'

const r = useShellStore()

const goHall = r.goHall as () => void
const goPublish = r.goPublish as () => void
const goProfile = r.goProfile as () => void
const goWallet = r.goWallet as (sub?: string) => void
const goChat = r.goChat as () => void
const goAdmin = r.goAdmin as () => void
const goRiderApply = r.goRiderApply as () => void
const openAuthPage = r.openAuthPage as (mode: 'login' | 'register') => void

const profileSubModule = r.profileSubModule as { value: string }
</script>

<template>
  <footer class="site-footer">
    <div class="site-footer-inner">
      <div>
        <div class="site-footer-brand">校园代拿 · Campus Co.</div>
        <p class="site-footer-desc">
          一个为大学生而生的校园互助跑腿空间。让顺路接单有温度，让校园生活更轻松。
        </p>
      </div>
      <div class="site-footer-col">
        <h4>探索</h4>
        <button type="button" @click="goHall()">任务大厅</button>
        <button type="button" @click="goPublish()">发布任务</button>
        <button type="button" @click="goRiderApply()">成为接单员</button>
      </div>
      <div class="site-footer-col">
        <h4>服务</h4>
        <button type="button" @click="goWallet()">我的钱包</button>
        <button type="button" @click="goChat()">私聊消息</button>
        <button type="button" @click="goProfile(); profileSubModule = 'history'">历史记录</button>
      </div>
      <div class="site-footer-col">
        <h4>关于</h4>
        <button type="button" @click="openAuthPage('register')">注册账号</button>
        <button type="button" @click="openAuthPage('login')">登录</button>
        <button type="button" @click="goAdmin()">管理后台</button>
      </div>
    </div>
    <div class="site-footer-bottom">
      <span>© 2026 校园代拿 · 校园互助跑腿</span>
      <span>Campus delivery marketplace</span>
    </div>
  </footer>
</template>
