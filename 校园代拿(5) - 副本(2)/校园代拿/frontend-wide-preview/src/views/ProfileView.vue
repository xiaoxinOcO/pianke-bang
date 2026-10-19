<script setup lang="ts">
import { computed, ref, watch, watchEffect } from 'vue'
import { useProfileStore } from '../stores/useProfileStore'
import type { ProfileViewInject } from '../context/profileViewInject'
import type { TaskItem, TaskStatus } from '../types/app'
import { isAwaitingBountyPayment, resolveTaskStatusLabel, statusLabelMap } from '../utils/taskStatus'
import { formatUrgentBonusText, isTaskUrgent } from '../utils/taskUrgent'

type ProfileRoot = ProfileViewInject & {
  goWallet?: (sub?: string) => void
  goCoupons?: () => void
}

const r = useProfileStore()

const PROFILE_TABS = [
  { key: 'published' as const, label: '我发布的' },
  { key: 'claimed' as const, label: '我接取的' },
  { key: 'rejected' as const, label: '异常任务' },
  { key: 'history' as const, label: '历史记录' }
]

const HISTORY_PAGE_SIZE = 5
const historyPage = ref(1)

const activeBlockedUntil = computed(() => {
  const raw = String(r.blockedUntil || '').trim()
  if (!raw) return ''
  const ts = Date.parse(raw)
  if (!Number.isFinite(ts) || ts <= Date.now()) return ''
  return raw
})

const sectionCopy = computed(() => {
  const sub = r.profileSubModule
  if (sub === 'rejected') {
    return {
      title: '异常任务',
      desc: '被驳回、被取消以及需要重新提交的任务，集中在这里处理。'
    }
  }
  if (sub === 'claimed') {
    return {
      title: '我接取的任务',
      desc: '当前正在处理的接单，跟踪送达时限与提交进度。'
    }
  }
  if (sub === 'history') {
    return {
      title: '历史记录',
      desc: '已完成与已取消的订单，方便回看每一笔任务处理结果。'
    }
  }
  return {
    title: '我发布的任务',
    desc: '查看我发布但尚未完成的任务，并快速处理接单、确认与取消。'
  }
})

function pkStatusBadge(status: TaskStatus) {
  if (status === 'OPEN') return 'pk-badge--open'
  if (status === 'COMPLETED') return 'pk-badge--done'
  if (status === 'REJECTED' || status === 'CANCELLED') return 'pk-badge--reject'
  return 'pk-badge--busy'
}

function setSubModule(key: ProfileViewInject['profileSubModule']) {
  r.profileSubModule = key
  if (key === 'history') historyPage.value = 1
}

function closeRatingDialog() {
  r.ratingDialogTaskId = null
}

const allActiveTasks = computed((): TaskItem[] => {
  const sub = r.profileSubModule
  if (sub === 'rejected') return r.rejectedTasks || []
  if (sub === 'claimed') return r.myClaimedActiveTasks || []
  if (sub === 'history') return r.myHistoryTasks || []
  return Array.isArray(r.myPublishedActiveTasks) ? r.myPublishedActiveTasks : []
})

const historyTotalPages = computed(() =>
  Math.max(1, Math.ceil((r.myHistoryTasks?.length || 0) / HISTORY_PAGE_SIZE))
)

const displayTasks = computed((): TaskItem[] => {
  const list = allActiveTasks.value
  if (r.profileSubModule !== 'history') return list
  const start = (historyPage.value - 1) * HISTORY_PAGE_SIZE
  return list.slice(start, start + HISTORY_PAGE_SIZE)
})

watch(
  () => r.profileSubModule,
  (sub) => {
    if (sub === 'history') historyPage.value = 1
  }
)

watchEffect(() => {
  if (historyPage.value > historyTotalPages.value) {
    historyPage.value = historyTotalPages.value
  }
})

function goHistoryPrev() {
  historyPage.value = Math.max(1, historyPage.value - 1)
}

function goHistoryNext() {
  historyPage.value = Math.min(historyTotalPages.value, historyPage.value + 1)
}

function emptyLabel() {
  const sub = r.profileSubModule
  if (sub === 'rejected') return '暂无异常任务'
  if (sub === 'claimed') return '暂无进行中的订单'
  if (sub === 'history') return '暂无历史记录'
  return '暂无进行中的任务'
}
</script>

<template>
  <main class="pk-profile">
    <div v-if="r.ratingDialogTask" class="detail-mask" @click.self="closeRatingDialog">
      <section class="detail-panel" style="max-width: 520px;">
        <div class="detail-head">
          <h3>完成评价</h3>
          <button class="ghost-local" type="button" @click="closeRatingDialog">关闭</button>
        </div>
        <div class="rating-form">
          <p class="meta">请给接单员 <strong>{{ r.ratingDialogTask!.claimerName }}</strong> 评分</p>
          <label>评分：</label>
          <div class="star-row">
            <div v-for="n in 5" :key="`star-dialog-${r.ratingDialogTask!.id}-${n}`" class="star-half-wrap">
              <button
                type="button"
                class="star-half-btn left"
                @click="r.setStarScore(r.ratingDialogTask!.id, n, true)"
              />
              <button
                type="button"
                class="star-half-btn right"
                @click="r.setStarScore(r.ratingDialogTask!.id, n, false)"
              />
              <span class="star-char" :class="r.starFillState(r.getRatingForm(r.ratingDialogTask!.id).score, n)">★</span>
            </div>
            <span class="star-score">{{ r.formatRating(r.getRatingForm(r.ratingDialogTask!.id).score) }} 分</span>
          </div>
          <textarea
            v-model="r.getRatingForm(r.ratingDialogTask!.id).comment"
            rows="3"
            maxlength="300"
            placeholder="可选评价（最多300字）"
          />
          <div class="actions">
            <button
              :disabled="r.actionLoadingMap[r.ratingDialogTask!.id]"
              type="button"
              @click="r.submitTaskRating(r.ratingDialogTask!)"
            >
              提交评分
            </button>
          </div>
        </div>
      </section>
    </div>

    <section class="pk-profile-card pk-profile-user">
      <div
        class="pk-profile-user-main"
        role="button"
        tabindex="0"
        @click="r.goPersonalInfo"
        @keydown.enter.prevent="r.goPersonalInfo"
      >
        <div class="pk-profile-avatar-wrap">
          <img
            v-if="r.myAvatarUrl"
            :src="r.myAvatarUrl"
            class="pk-profile-avatar"
            alt="avatar"
            @error="r.myAvatarUrl = ''"
          />
          <div v-else class="pk-profile-avatar pk-profile-avatar-fallback">
            {{ (r.myName || 'U').slice(0, 1).toUpperCase() }}
          </div>
        </div>
        <div class="pk-profile-user-copy">
          <p class="pk-profile-kicker">我的账户</p>
          <h1>{{ r.myName || '未登录用户' }}</h1>
          <p class="pk-profile-lead">点击头像或昵称编辑资料</p>
          <div class="pk-profile-tags">
            <span>发布 {{ r.myPublishedTasks.length }}</span>
            <span>接取 {{ r.myClaimedTasks.length }}</span>
            <span>信誉 {{ r.creditScore }}</span>
            <span v-if="r.pendingPenalty > 0">待补偿 ¥{{ Number(r.pendingPenalty).toFixed(2) }}</span>
            <span v-if="activeBlockedUntil" class="pk-profile-tag--warn">
              禁接至 {{ String(activeBlockedUntil).replace('T', ' ').slice(0, 16) }}
            </span>
            <span v-if="r.myAvatarPending">头像审核中</span>
          </div>
        </div>
      </div>
      <div class="pk-profile-user-actions">
        <button type="button" class="pk-btn pk-btn-ghost pk-btn-sm" @click="r.goPersonalInfo">编辑资料</button>
        <button v-if="r.goWallet" type="button" class="pk-btn pk-btn-ghost pk-btn-sm" @click="r.goWallet!()">
          我的钱包
        </button>
        <button v-if="r.goCoupons" type="button" class="pk-btn pk-btn-ghost pk-btn-sm" @click="r.goCoupons!()">
          我的优惠券
        </button>
      </div>
    </section>

    <div class="pk-profile-tabs" role="tablist" aria-label="任务分类">
      <div class="pk-tab-group">
        <button
          v-for="tab in PROFILE_TABS"
          :key="tab.key"
          type="button"
          class="pk-tab"
          role="tab"
          :class="{ active: r.profileSubModule === tab.key }"
          :aria-selected="r.profileSubModule === tab.key"
          @click="setSubModule(tab.key)"
        >
          {{ tab.label }}
          <em v-if="tab.key === 'rejected' && r.rejectedTasks.length > 0">{{ r.rejectedTasks.length }}</em>
        </button>
      </div>
    </div>

    <section class="pk-profile-card pk-profile-panel">
      <p v-if="r.profileNotice" class="pk-profile-notice" role="status">{{ r.profileNotice }}</p>
      <header class="pk-section-head pk-section-head--compact">
        <div>
          <h2>{{ sectionCopy.title }}</h2>
          <p>{{ sectionCopy.desc }}</p>
        </div>
        <button
          v-if="r.profileSubModule === 'published'"
          type="button"
          class="pk-btn pk-btn-ghost pk-btn-sm"
          @click="r.fetchMyTasksForProfile?.()"
        >
          刷新
        </button>
      </header>

      <div
        v-if="r.profileSubModule === 'published' && r.myUnpaidPublishDraft && allActiveTasks.length === 0"
        class="publish-draft-card"
        style="margin-bottom: 16px"
      >
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

      <div
        v-if="allActiveTasks.length === 0 && !(r.profileSubModule === 'published' && r.myUnpaidPublishDraft)"
        class="pk-empty-state"
      >
        {{ emptyLabel() }}
      </div>

      <div v-else class="pk-profile-task-list">
        <article
          v-for="task in displayTasks"
          :key="`${r.profileSubModule}-${task.id}`"
          class="pk-profile-task"
        >
          <div class="pk-profile-task-head">
            <h3 class="pk-profile-task-title-line">
              <span>{{ task.title }}</span>
              <span v-if="isTaskUrgent(task)" class="pk-urgent-mark">加急</span>
            </h3>
            <div class="pk-profile-task-badges">
              <span
                class="pk-badge"
                :class="pkStatusBadge(task.overdueRejected ? 'REJECTED' : task.status)"
              >
                {{ task.overdueRejected ? '接单员超时' : resolveTaskStatusLabel(task) }}
              </span>
              <span
                v-if="
                  task.latestDeliveryAt &&
                  (task.status === 'CLAIMED' || task.status === 'OVERDUE') &&
                  r.profileSubModule === 'claimed'
                "
                class="pk-badge"
                :class="task.status === 'OVERDUE' ? 'pk-badge--reject' : 'pk-badge--busy'"
              >
                {{ r.formatDeadlineCountdown(task.latestDeliveryAt) }}
              </span>
            </div>
          </div>

          <template v-if="r.profileSubModule === 'rejected'">
            <p class="pk-profile-task-meta">取件：{{ task.pickupLocation }} → 送达：{{ task.dropoffLocation }}</p>
            <p class="pk-profile-task-meta">
              联系方式：{{ r.canRevealTaskContact(task) ? task.contact : r.maskContact(task.contact) }}
            </p>
            <p v-if="task.status === 'CANCELLED' && !task.overdueRejected" class="pk-profile-task-desc">
              该任务已被发布者取消，你无需再配送。
            </p>
            <p v-if="task.overdueRejected" class="pk-profile-task-desc">该任务已因超时被驳回，你可以修改后重新提交。</p>
            <p v-if="task.status === 'REJECTED' && task.adminRejectRemark" class="pk-profile-task-desc">
              驳回原因：{{ task.adminRejectRemark }}
            </p>
            <footer class="pk-profile-task-foot">
              <span class="pk-profile-task-role">发布者操作</span>
              <div class="pk-profile-task-actions">
                <button type="button" class="pk-btn pk-btn-ghost pk-btn-sm" @click="r.openAppeal(task)">申诉</button>
                <button
                  v-if="task.status === 'REJECTED'"
                  type="button"
                  class="pk-btn pk-btn-primary pk-btn-sm"
                  @click="r.editRejectedTask(task)"
                >
                  修改并重提
                </button>
                <button
                  v-if="task.status === 'REJECTED' || task.status === 'CANCELLED' || task.overdueRejected"
                  type="button"
                  class="pk-btn pk-btn-danger pk-btn-sm"
                  :disabled="Boolean(r.actionLoadingMap[task.id])"
                  @click="r.deleteTask(task.id)"
                >
                  {{ r.actionLoadingMap[task.id] ? '删除中…' : '删除' }}
                </button>
              </div>
            </footer>
          </template>

          <template v-else-if="r.profileSubModule === 'published'">
            <p class="pk-profile-task-meta">取件：{{ task.pickupLocation }} → 送达：{{ task.dropoffLocation }}</p>
            <p class="pk-profile-task-meta">
              赏金：¥ {{ Number(task.reward).toFixed(2) }}
              <template v-if="formatUrgentBonusText(task)">
                · 加急赏 +¥{{ formatUrgentBonusText(task) }}
              </template>
            </p>
            <p v-if="task.claimerName" class="pk-profile-task-meta">接单员：{{ task.claimerName }}</p>
            <div
              v-if="task.status === 'PENDING_CONFIRMATION'"
              class="complete-proof-card"
            >
              <p class="meta"><strong>接单方提交记录</strong></p>
              <template v-if="r.getTaskCompletionProof(task.id)">
                <p v-if="r.getTaskCompletionProof(task.id)?.note" class="desc">
                  {{ r.getTaskCompletionProof(task.id)?.note }}
                </p>
                <div v-if="r.getTaskCompletionProof(task.id)?.images?.length" class="complete-proof-grid">
                  <div
                    v-for="(img, idx) in r.getTaskCompletionProof(task.id)?.images"
                    :key="`pub-proof-${task.id}-${idx}`"
                    class="complete-proof-item"
                  >
                    <img :src="img" alt="完成凭证" @click="r.openImagePreview(img)" />
                  </div>
                </div>
                <p
                  v-else-if="!r.getTaskCompletionProof(task.id)?.note"
                  class="meta"
                >
                  已申请完成，未附带留言或图片。
                </p>
              </template>
              <p v-else class="meta">已申请完成，暂无凭证数据，请点击刷新后重试。</p>
            </div>
            <div v-if="task.status === 'COMPLETED' && task.claimerName" class="rating-block">
              <p v-if="task.claimerRating != null" class="pk-profile-task-meta">
                你已评分：{{ task.claimerRating }} 分
                <span v-if="task.claimerRatingComment">｜评价：{{ task.claimerRatingComment }}</span>
              </p>
              <div v-else class="rating-form">
                <label>给接单员评分：</label>
                <div class="star-row">
                  <div v-for="n in 5" :key="`star-inline-${task.id}-${n}`" class="star-half-wrap">
                    <button type="button" class="star-half-btn left" @click="r.setStarScore(task.id, n, true)" />
                    <button type="button" class="star-half-btn right" @click="r.setStarScore(task.id, n, false)" />
                    <span class="star-char" :class="r.starFillState(r.getRatingForm(task.id).score, n)">★</span>
                  </div>
                  <span class="star-score">{{ r.formatRating(r.getRatingForm(task.id).score) }} 分</span>
                </div>
                <textarea
                  v-model="r.getRatingForm(task.id).comment"
                  rows="2"
                  maxlength="300"
                  placeholder="可选评价（最多300字）"
                />
                <div class="actions">
                  <button :disabled="r.actionLoadingMap[task.id]" @click="r.submitTaskRating(task)">提交评分</button>
                </div>
              </div>
            </div>
            <footer class="pk-profile-task-foot">
              <span class="pk-profile-task-role">发布者操作</span>
              <div class="pk-profile-task-actions">
                <button type="button" class="pk-btn pk-btn-ghost pk-btn-sm" @click="r.openTaskDetail(task)">详情</button>
                <button
                  v-if="isAwaitingBountyPayment(task)"
                  type="button"
                  class="pk-btn pk-btn-primary pk-btn-sm"
                  :disabled="!r.isLoggedIn || r.actionLoadingMap[task.id]"
                  @click="r.syncTaskBountyPaymentForTask(task.id)"
                >
                  同步支付状态
                </button>
                <button
                  v-if="isAwaitingBountyPayment(task)"
                  type="button"
                  class="pk-btn pk-btn-ghost pk-btn-sm"
                  :disabled="!r.isLoggedIn || r.actionLoadingMap[task.id]"
                  @click="r.resumeTaskBountyPayment(task.id)"
                >
                  继续支付
                </button>
                <button
                  v-if="isAwaitingBountyPayment(task)"
                  type="button"
                  class="pk-btn pk-btn-danger pk-btn-sm"
                  :disabled="!r.isLoggedIn || r.actionLoadingMap[task.id]"
                  @click="r.abandonUnpaidPublishDraft(task.id)"
                >
                  放弃任务
                </button>
                <button
                  v-if="task.status === 'PENDING_CONFIRMATION'"
                  type="button"
                  class="pk-btn pk-btn-ghost pk-btn-sm"
                  @click="r.openProofView(task.id)"
                >
                  查看凭证
                </button>
                <button
                  v-if="task.status === 'PENDING_CONFIRMATION'"
                  type="button"
                  class="pk-btn pk-btn-primary pk-btn-sm"
                  :disabled="!r.isLoggedIn || r.actionLoadingMap[task.id]"
                  @click="r.confirmTask(task.id)"
                >
                  确认完成
                </button>
                <button
                  v-if="
                    task.status !== 'COMPLETED' &&
                    task.status !== 'CANCELLED' &&
                    task.status !== 'PENDING_CONFIRMATION'
                  "
                  type="button"
                  class="pk-btn pk-btn-danger pk-btn-sm"
                  :disabled="!r.isLoggedIn || r.actionLoadingMap[task.id]"
                  @click="r.cancelTask(task.id)"
                >
                  取消
                </button>
              </div>
              <p v-if="task.status === 'PENDING_CONFIRMATION'" class="pk-profile-task-hint">
                待确认阶段不可单方取消：请核对凭证后确认完成，或联系接单员/管理员处理争议。
              </p>
            </footer>
          </template>

          <template v-else-if="r.profileSubModule === 'claimed'">
            <p class="pk-profile-task-meta">发布人：{{ task.publisherName }}</p>
            <p class="pk-profile-task-meta">取件：{{ task.pickupLocation }} → 送达：{{ task.dropoffLocation }}</p>
            <div
              v-if="task.latestDeliveryAt"
              class="pk-task-deadline"
              :class="{ overdue: task.status === 'OVERDUE' }"
            >
              ⏰ 最晚 {{ String(task.latestDeliveryAt).replace('T', ' ').slice(0, 16) }}
              <span v-if="task.status === 'OVERDUE'"> · 已超时</span>
            </div>
            <footer class="pk-profile-task-foot">
              <span class="pk-profile-task-role">接单者操作</span>
              <div class="pk-profile-task-actions">
                <button type="button" class="pk-btn pk-btn-ghost pk-btn-sm" @click="r.openTaskDetail(task)">详情</button>
                <button
                  v-if="task.status === 'CLAIMED'"
                  type="button"
                  class="pk-btn pk-btn-primary pk-btn-sm"
                  :disabled="!r.isLoggedIn || r.actionLoadingMap[task.id]"
                  @click="r.openCompleteDialog(task.id)"
                >
                  提交完成
                </button>
                <button
                  v-if="task.status !== 'COMPLETED' && task.status !== 'CANCELLED'"
                  type="button"
                  class="pk-btn pk-btn-danger pk-btn-sm"
                  :disabled="!r.isLoggedIn || r.actionLoadingMap[task.id]"
                  @click="r.cancelTask(task.id)"
                >
                  取消
                </button>
              </div>
            </footer>
          </template>

          <template v-else>
            <p class="pk-profile-task-meta">取件：{{ task.pickupLocation }} → 送达：{{ task.dropoffLocation }}</p>
            <p class="pk-profile-task-meta">
              赏金：¥ {{ Number(task.reward).toFixed(2) }}
              <template v-if="formatUrgentBonusText(task)">
                · 加急赏 +¥{{ formatUrgentBonusText(task) }}
              </template>
            </p>
            <p class="pk-profile-task-meta">
              角色：{{
                task.publisherName === r.myName ? '发布方' : task.claimerName === r.myName ? '接单方' : '—'
              }}
            </p>
            <footer class="pk-profile-task-foot">
              <span class="pk-profile-task-role">历史操作</span>
              <div class="pk-profile-task-actions">
                <button type="button" class="pk-btn pk-btn-ghost pk-btn-sm" @click="r.openTaskDetail(task)">
                  详情
                </button>
                <button
                  v-if="r.getTaskCompletionProof(task.id)"
                  type="button"
                  class="pk-btn pk-btn-ghost pk-btn-sm"
                  @click="r.openProofView(task.id)"
                >
                  查看凭证
                </button>
              </div>
            </footer>
          </template>
        </article>

        <div
          v-if="r.profileSubModule === 'history' && allActiveTasks.length > HISTORY_PAGE_SIZE"
          class="pk-profile-pagination"
        >
          <button
            type="button"
            class="pk-btn pk-btn-ghost pk-btn-sm"
            :disabled="historyPage <= 1"
            @click="goHistoryPrev"
          >
            上一页
          </button>
          <span>第 {{ historyPage }} / {{ historyTotalPages }} 页 · 共 {{ allActiveTasks.length }} 条</span>
          <button
            type="button"
            class="pk-btn pk-btn-ghost pk-btn-sm"
            :disabled="historyPage >= historyTotalPages"
            @click="goHistoryNext"
          >
            下一页
          </button>
        </div>
      </div>
    </section>
  </main>
</template>
