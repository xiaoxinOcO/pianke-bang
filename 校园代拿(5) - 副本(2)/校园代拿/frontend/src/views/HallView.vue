<script setup lang="ts">
import { useHallStore } from '../stores/useHallStore'
import type { TaskItem, TaskStatus } from '../types/app'
import { formatUrgentBonusText, isTaskUrgent } from '../utils/taskUrgent'

const r = useHallStore()

function statusBadgeModifier(status: TaskStatus) {
  if (status === 'OPEN') return 'pk-badge--open'
  if (status === 'COMPLETED') return 'pk-badge--done'
  return 'pk-badge--busy'
}

function formatReward(reward: number) {
  const n = Number(reward)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

function urgentBonusText(task: TaskItem) {
  return formatUrgentBonusText(task)
}
</script>

<template>
  <main class="pk-hall">
    <section class="pk-zone-panel" aria-label="热门区域">
      <div class="pk-section-head pk-section-head--compact">
        <div>
          <h2>热门区域</h2>
          <p>点选区域快速筛选附近任务</p>
        </div>
      </div>
      <div class="pk-zone-grid">
        <button
          v-for="zone in r.hallZones"
          :key="`zone-${zone.value || 'all'}`"
          type="button"
          class="pk-zone-chip"
          :class="{ active: r.filters.zone === zone.value }"
          @click="r.selectZoneFilter(zone.value)"
        >
          {{ zone.label }}
        </button>
      </div>
    </section>

    <div class="pk-content-split">
      <div class="pk-content-main">
        <section id="hall-task-list" class="pk-task-section" aria-labelledby="pk-task-title">
          <div class="pk-section-head">
            <div>
              <h2 id="pk-task-title">附近可接任务</h2>
              <p>共 {{ r.visibleTasks.length }} 条正在发布 · 仅展示可接单任务</p>
            </div>
            <div class="pk-filter-bar">
              <div class="pk-tab-group" role="tablist" aria-label="排序">
                <button
                  type="button"
                  class="pk-tab"
                  :class="{ active: r.filters.sortMode === 'default' }"
                  @click="r.selectSortMode('default')"
                >
                  推荐
                </button>
                <button
                  type="button"
                  class="pk-tab"
                  :class="{ active: r.filters.sortMode === 'new' }"
                  @click="r.selectSortMode('new')"
                >
                  最新
                </button>
                <button
                  type="button"
                  class="pk-tab"
                  :class="{ active: r.filters.sortMode === 'reward' }"
                  @click="r.selectSortMode('reward')"
                >
                  高酬劳
                </button>
              </div>
              <select v-model="r.filters.rewardRange" class="pk-select" aria-label="酬劳筛选" @change="r.applyFilters()">
                <option value="">全部酬劳</option>
                <option value="low">¥10 以下</option>
                <option value="mid">¥10 - ¥20</option>
                <option value="high">¥20 以上</option>
              </select>
              <button type="button" class="pk-btn pk-btn-ghost pk-btn-sm" @click="r.resetFilters(); r.applyFilters()">
                重置
              </button>
            </div>
          </div>

          <p v-if="!r.isLoggedIn" class="pk-guest-tip">游客可浏览，登录后可接单与发布</p>
          <p v-if="r.errorMsg" class="pk-alert pk-alert--error">{{ r.errorMsg }}</p>
          <p v-if="r.loading" class="pk-empty-state">任务加载中…</p>

          <div v-else-if="r.visibleTasks.length === 0" class="pk-empty-state">
            没有找到匹配的任务，换个关键词或分类试试。
          </div>

          <div v-else class="pk-task-grid">
            <article
              v-for="task in r.visibleTasks"
              :key="task.id"
              class="pk-task-card"
              :class="{ 'pk-task-card--urgent': isTaskUrgent(task) }"
            >
              <div class="pk-task-card-top">
                <div class="pk-task-publisher pk-task-publisher--top">
                  <span class="pk-task-publisher-avatar-wrap">
                    <img
                      v-if="r.publisherAvatarUrl(task.publisherName)"
                      class="pk-task-publisher-avatar"
                      :src="r.normalizeImageUrl(r.publisherAvatarUrl(task.publisherName))"
                      alt=""
                      width="40"
                      height="40"
                    />
                    <span v-else class="pk-task-publisher-fallback" aria-hidden="true">
                      {{ r.publisherNameInitial(task.publisherName) }}
                    </span>
                    <span class="pk-task-emoji-corner" aria-hidden="true">{{ r.hallTaskEmoji(task.title) }}</span>
                  </span>
                  <span class="pk-task-publisher-copy">
                    <strong>{{ r.isLoggedIn ? task.publisherName : r.maskName(task.publisherName) }}</strong>
                    <small>发布人</small>
                  </span>
                </div>
                <div class="pk-task-card-badges">
                  <span v-if="isTaskUrgent(task)" class="pk-badge pk-badge--urgent">加急</span>
                  <span class="pk-badge" :class="statusBadgeModifier(task.status)">
                    {{ r.hallStatusLabel(task.status) }}
                  </span>
                </div>
              </div>

              <h3 class="pk-task-title-line">
                <span class="pk-task-title-text">{{ task.title }}</span>
              </h3>
              <p class="pk-task-route">
                <strong>{{ task.pickupLocation }}</strong>
                →
                <strong>{{ task.dropoffLocation }}</strong>
              </p>

              <div class="pk-task-extra">
                <span>📍 {{ r.hallTaskDistance(task) }}</span>
                <span>🕐 {{ r.formatTaskTimeAgo(task.createdAt) }}</span>
              </div>

              <div class="pk-task-meta">
                <div class="pk-reward">
                  <div>
                    <small>酬劳</small>¥{{ formatReward(task.reward) }}
                  </div>
                  <p v-if="urgentBonusText(task)" class="pk-urgent-fee">含加急赏 +¥{{ urgentBonusText(task) }}</p>
                </div>
                <span class="pk-task-category">{{ r.hallTaskCategoryLabel(task) }}</span>
              </div>

              <div
                v-if="task.latestDeliveryAt"
                class="pk-task-deadline"
                :class="{ overdue: task.status === 'OVERDUE' }"
              >
                ⏰ 最晚 {{ String(task.latestDeliveryAt).replace('T', ' ').slice(0, 16) }}
              </div>

              <div class="pk-task-actions">
                <button type="button" class="pk-btn pk-btn-ghost pk-btn-sm" @click="r.openTaskDetail(task)">
                  {{ r.isLoggedIn ? '详情' : '简介' }}
                </button>
                <button
                  v-if="task.status === 'OPEN' && r.isLoggedIn && r.isRiderApproved && task.publisherName !== r.myName"
                  type="button"
                  class="pk-btn pk-btn-ghost pk-btn-sm"
                  @click="r.askPublisherFirst(task)"
                >
                  先联系
                </button>
                <button
                  v-if="task.status === 'OPEN' && task.publisherName !== r.myName && r.isRiderApproved"
                  type="button"
                  class="pk-btn pk-btn-accent pk-btn-sm"
                  :disabled="!r.isLoggedIn || r.actionLoadingMap[task.id]"
                  @click="r.claimTask(task.id)"
                >
                  我来帮
                </button>
              </div>
            </article>
          </div>

          <div v-if="r.visibleTasks.length > 0" class="pk-pagination">
            <button class="pk-btn pk-btn-ghost pk-btn-sm" type="button" :disabled="r.paging.page <= 1" @click="r.goPrevPage">
              上一页
            </button>
            <span>第 {{ r.paging.page }} / {{ r.paging.totalPages }} 页（共 {{ r.paging.totalElements }} 条）</span>
            <button
              class="pk-btn pk-btn-ghost pk-btn-sm"
              type="button"
              :disabled="r.paging.page >= r.paging.totalPages"
              @click="r.goNextPage"
            >
              下一页
            </button>
          </div>
        </section>

        <section class="pk-steps-panel" aria-labelledby="pk-steps-title">
          <h2 id="pk-steps-title">三步上手</h2>
          <div class="pk-steps-grid">
            <article class="pk-step-card">
              <span class="pk-step-num">01</span>
              <h3>浏览或搜索</h3>
              <p>按区域、类型、酬劳筛选，找到顺路且时间合适的小忙。</p>
            </article>
            <article class="pk-step-card">
              <span class="pk-step-num">02</span>
              <h3>沟通后接单</h3>
              <p>先看详情，确认取送地点与交接方式，再点「我来帮」。</p>
            </article>
            <article class="pk-step-card">
              <span class="pk-step-num">03</span>
              <h3>完成并评价</h3>
              <p>当面交接后标记完成，酬劳自动入账，双方互评。</p>
            </article>
          </div>
        </section>

        <section class="pk-faq-panel" aria-labelledby="pk-faq-title">
          <h2 id="pk-faq-title">常见问题</h2>
          <div class="pk-faq-list">
            <details v-for="(item, idx) in r.hallFaqItems" :key="`faq-${idx}`" class="pk-faq-item" :open="idx === 0">
              <summary>{{ item.question }}</summary>
              <p>{{ item.answer }}</p>
            </details>
          </div>
        </section>
      </div>

      <aside class="pk-content-aside">
        <section class="pk-aside-card">
          <div class="pk-aside-head">
            <h3>实时动态</h3>
            <span class="pk-live-dot">Live</span>
          </div>
          <ul class="pk-activity-list">
            <li v-for="(item, idx) in r.hallActivities" :key="`activity-${idx}`">
              <span>{{ item.text }}</span>
              <time>{{ item.time }}</time>
            </li>
          </ul>
        </section>

        <section class="pk-aside-card">
          <div class="pk-aside-head">
            <h3>本周帮王</h3>
            <a href="#" @click.prevent>完整榜单</a>
          </div>
          <ol class="pk-rank-list">
            <li v-for="(item, idx) in r.hallLeaderboard" :key="`rank-${item.seed}`">
              <span class="pk-rank-no">{{ idx + 1 }}</span>
              <img
                v-if="item.avatarUrl"
                :src="r.normalizeImageUrl(item.avatarUrl)"
                alt=""
                width="36"
                height="36"
              />
              <span v-else class="pk-rank-avatar-fallback">{{ (item.name || '?').slice(0, 1).toUpperCase() }}</span>
              <div class="pk-rank-info">
                <strong>{{ item.name }}</strong>
                <span>{{ item.score }} 单</span>
              </div>
            </li>
          </ol>
        </section>

        <section class="pk-aside-card pk-aside-testimonial">
          <blockquote>「顺路帮同学带个打印，来回十分钟赚了杯奶茶钱，比刷短视频实在。」</blockquote>
          <cite>— 经管学院 · 小林</cite>
        </section>
      </aside>
    </div>
  </main>
</template>
