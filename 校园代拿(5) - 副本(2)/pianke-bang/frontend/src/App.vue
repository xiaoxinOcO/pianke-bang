<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { getCampusApp } from './app/campusAppState'
import { statusClass, statusLabelMap } from './utils/taskStatus'
import { formatUrgentBonusText, isTaskUrgent } from './utils/taskUrgent'
import FloatingDock from './components/FloatingDock.vue'
import SmartSupportChat from './components/SmartSupportChat.vue'
import WebPet from './components/WebPet.vue'
import SiteFooter from './components/SiteFooter.vue'
import SiteHeader from './components/SiteHeader.vue'
import HallView from './views/HallView.vue'

const AuthPage = defineAsyncComponent(() => import('./views/AuthPage.vue'))
const ScanLoginPage = defineAsyncComponent(() => import('./views/ScanLoginPage.vue'))
const AdminShell = defineAsyncComponent(() => import('./views/AdminShell.vue'))
const ChatView = defineAsyncComponent(() => import('./views/ChatView.vue'))
const FeedbackView = defineAsyncComponent(() => import('./views/FeedbackView.vue'))
const PersonalInfoView = defineAsyncComponent(() => import('./views/PersonalInfoView.vue'))
const ProfileView = defineAsyncComponent(() => import('./views/ProfileView.vue'))
const CouponsView = defineAsyncComponent(() => import('./views/CouponsView.vue'))
const PublishView = defineAsyncComponent(() => import('./views/PublishView.vue'))
const RiderApplyView = defineAsyncComponent(() => import('./views/RiderApplyView.vue'))
const WalletView = defineAsyncComponent(() => import('./views/WalletView.vue'))

const {
  actionLoadingMap,
  activeHallNoticeId,
  activeHeroPromo,
  adminAppealReply,
  adminAppealReplySubmitting,
  adminAppealReplyText,
  appealSubmitting,
  appealTask,
  appealText,
  askPublisherFirst,
  canRevealTaskContact,
  chatAvatarUrl,
  claimHeroPromo,
  claimTask,
  claimerRatingStatsMap,
  claimingHeroPromoId,
  closeAdminAppealReply,
  closeAppeal,
  closeCompleteDialog,
  closeContactProfileModal,
  closeImagePreview,
  closeKickedDialog,
  closeProofView,
  closeRealtimeToast,
  closeTaskDetail,
  completeDialogTaskId,
  completeProofFileInputRef,
  completeProofImages,
  completeProofText,
  completeTask,
  confirmTask,
  contactProfileLoading,
  currentView,
  formatDateOnly,
  formatRating,
  getTaskCompletionProof,
  goLoginFromKickedDialog,
  goToHallNotice,
  goToHeroPromo,
  hallTopNotice,
  openActiveHallNoticeLink,
  hallNoticeBannerText,
  hallNoticeCarouselIndex,
  hallTaskEmoji,
  hasMultipleHallNotices,
  hasMultipleHeroPromos,
  heroPromoCtaText,
  heroPromoIndex,
  heroPromoSlides,
  heroSideStats,
  isAdmin,
  isLoggedIn,
  isRiderApproved,
  isScanLoginRoute,
  kickedDialogOpen,
  kickedDialogText,
  maskContact,
  maskName,
  myName,
  normalizeImageUrl,
  noticeBarVisible,
  noticeMarqueeActive,
  noticeMeasureRef,
  noticeTrackRef,
  onCompleteProofFilesSelected,
  openCompleteDialog,
  openImagePreview,
  openRealtimeToastDetail,
  pageClass,
  pauseHeroPromoCarousel,
  pauseNoticeCarousel,
  previewImageUrl,
  proofViewTaskId,
  publicNotices,
  publisherAvatarUrl,
  publisherNameInitial,
  realtimeToast,
  removeCompleteProofImage,
  resumeHeroPromoCarousel,
  resumeNoticeCarousel,
  selectedContactProfile,
  selectedTask,
  showAuthPage,
  showContactProfileModal,
  stats,
  submitAdminAppealReply,
  submitAppeal,
  taskTypeLabel,
  triggerCompleteProofFilePick,
} = getCampusApp()
</script>


<template>
  <div v-if="realtimeToast" class="realtime-toast" :class="`realtime-toast--${realtimeToast.kind}`" @click="openRealtimeToastDetail">
    <div class="realtime-toast__icon">{{ realtimeToast.kind === 'chat' ? '💬' : '📣' }}</div>
    <div class="realtime-toast__content">
      <p class="realtime-toast__title">{{ realtimeToast.title }}</p>
      <p class="realtime-toast__message">{{ realtimeToast.message }}</p>
    </div>
    <div class="realtime-toast__actions">
      <button type="button" class="realtime-toast__action-btn" @click.stop="openRealtimeToastDetail">
        {{ realtimeToast.kind === 'chat' ? '去查看' : '查看任务' }}
      </button>
      <button type="button" class="realtime-toast__close-btn" @click.stop="closeRealtimeToast">×</button>
    </div>
  </div>

  <div v-if="kickedDialogOpen" class="detail-mask kicked-mask" @click.self="closeKickedDialog">
    <section class="detail-panel kicked-panel">
      <div class="detail-head">
        <h3>登录提醒</h3>
        <button class="ghost-local" @click="closeKickedDialog">关闭</button>
      </div>
      <p class="meta" style="margin-top: 6px;">{{ kickedDialogText }}</p>
      <div class="actions" style="margin-top: 14px; justify-content: flex-end;">
        <button class="ghost-local" type="button" @click="goLoginFromKickedDialog">返回登录</button>
      </div>
    </section>
  </div>

  <SmartSupportChat />
  <WebPet />

  <ScanLoginPage v-if="isScanLoginRoute" />

  <AuthPage v-else-if="showAuthPage" />

  <FeedbackView v-else-if="currentView === 'feedback'" />

  <AdminShell v-else-if="currentView === 'admin' && isAdmin" />

  <div v-else class="app-shell app-shell--editorial app-shell--piankebang">
    <SiteHeader v-if="currentView !== 'chat'" />

    <div
      v-if="currentView === 'hall' && noticeBarVisible && hallNoticeBannerText"
      class="pk-notice-bar"
      :class="{
        'has-multiple': hasMultipleHallNotices,
        'is-link': !!(hallTopNotice && hallTopNotice.linkEnabled && hallTopNotice.linkPath)
      }"
      @mouseenter="pauseNoticeCarousel()"
      @mouseleave="resumeNoticeCarousel()"
    >
      <span class="pk-notice-badge">公告</span>
      <div
        ref="noticeTrackRef"
        class="pk-notice-track"
        :class="{ 'is-clickable': !!(hallTopNotice && hallTopNotice.linkEnabled && hallTopNotice.linkPath) }"
        role="button"
        :tabindex="hallTopNotice && hallTopNotice.linkEnabled && hallTopNotice.linkPath ? 0 : -1"
        @click="openActiveHallNoticeLink()"
        @keydown.enter.prevent="openActiveHallNoticeLink()"
      >
        <div class="pk-notice-carousel">
          <Transition name="pk-notice-switch" mode="out-in">
            <div
              :key="activeHallNoticeId"
              class="pk-notice-marquee"
              :class="{ 'is-scrolling': noticeMarqueeActive }"
              aria-live="polite"
            >
              <p ref="noticeMeasureRef" class="pk-notice-text">{{ hallNoticeBannerText }}</p>
              <p v-if="noticeMarqueeActive" class="pk-notice-text" aria-hidden="true">{{ hallNoticeBannerText }}</p>
            </div>
          </Transition>
        </div>
      </div>
      <div v-if="hasMultipleHallNotices" class="pk-notice-dots" role="tablist" aria-label="切换公告">
        <button
          v-for="(n, idx) in publicNotices"
          :key="n.id"
          type="button"
          class="pk-notice-dot"
          :class="{ active: idx === hallNoticeCarouselIndex }"
          :aria-label="`查看第 ${idx + 1} 条公告`"
          :aria-selected="idx === hallNoticeCarouselIndex"
          role="tab"
          @click="goToHallNotice(idx)"
        />
      </div>
      <button class="pk-notice-close" type="button" aria-label="关闭公告" @click="noticeBarVisible = false">×</button>
    </div>

    <FloatingDock v-if="currentView !== 'chat'" />

    <div class="page pk-page" :class="pageClass" :data-view="currentView">
      <section v-if="currentView === 'hall'" class="pk-hero">
        <div
          class="pk-hero-main"
          :class="[
            `pk-hero-theme-${activeHeroPromo.bannerTheme || 'warm'}`,
            {
              'pk-hero-main--has-image': !!activeHeroPromo.bannerImageUrl,
              'pk-hero-main--soft-banner': !!activeHeroPromo.softBanner && !!activeHeroPromo.bannerImageUrl
            }
          ]"
        >
          <img
            v-if="activeHeroPromo.bannerImageUrl"
            class="pk-hero-promo-bg"
            :src="normalizeImageUrl(activeHeroPromo.bannerImageUrl)"
            alt=""
          />
          <div
            v-if="activeHeroPromo.bannerImageUrl"
            class="pk-hero-promo-shade"
            :class="{ 'pk-hero-promo-shade--soft': !!activeHeroPromo.softBanner }"
            aria-hidden="true"
          ></div>
          <div
            class="pk-hero-promo"
            @mouseenter="pauseHeroPromoCarousel()"
            @mouseleave="resumeHeroPromoCarousel()"
          >
            <Transition name="pk-hero-promo-switch" mode="out-in">
              <div :key="activeHeroPromo.key" class="pk-hero-promo-slide">
                <div class="pk-hero-promo-copy">
                  <span class="pk-eyebrow">{{ activeHeroPromo.eyebrow }}</span>
                  <h1>{{ activeHeroPromo.title }}</h1>
                  <p class="pk-hero-lead">{{ activeHeroPromo.subtitle }}</p>
                </div>
                <button
                  v-if="activeHeroPromo.isPromo"
                  type="button"
                  class="pk-btn pk-btn-accent pk-btn-sm pk-hero-promo-cta"
                  :class="{
                    'pk-hero-promo-cta--claimed':
                      activeHeroPromo.actionType === 'COUPON_CLAIM' && !!activeHeroPromo.claimed
                  }"
                  :disabled="
                    (activeHeroPromo.actionType === 'COUPON_CLAIM' && !!activeHeroPromo.claimed) ||
                    claimingHeroPromoId != null
                  "
                  @click="claimHeroPromo"
                >
                  {{ heroPromoCtaText(activeHeroPromo) }}
                </button>
              </div>
            </Transition>
            <div
              v-if="hasMultipleHeroPromos"
              class="pk-hero-promo-dots"
              role="tablist"
              aria-label="切换活动"
            >
              <button
                v-for="(slide, idx) in heroPromoSlides"
                :key="`hero-promo-dot-${slide.key}`"
                type="button"
                class="pk-hero-promo-dot"
                :class="{ active: idx === heroPromoIndex }"
                :aria-label="`查看第 ${idx + 1} 条活动`"
                :aria-selected="idx === heroPromoIndex"
                role="tab"
                @click="goToHeroPromo(idx)"
              />
            </div>
          </div>
        </div>

        <div class="pk-hero-side">
          <div
            v-for="(card, idx) in heroSideStats"
            :key="`hero-stat-${idx}`"
            class="pk-stat-card"
            :class="`pk-stat-card--${card.variant}`"
          >
            <div class="pk-stat-label">{{ card.label }}</div>
            <div class="pk-stat-value">
              {{ card.value }}<span v-if="card.unit" class="pk-stat-unit">{{ card.unit }}</span>
            </div>
            <div class="pk-stat-note">{{ card.note }}</div>
          </div>
        </div>
      </section>

      <header
        v-else-if="
          currentView !== 'chat' &&
          currentView !== 'publish' &&
          currentView !== 'hall' &&
          currentView !== 'wallet' &&
          currentView !== 'profile' &&
          currentView !== 'personalInfo' &&
          currentView !== 'riderApply' &&
          currentView !== 'feedback' &&
          currentView !== 'coupons'
        "
        class="hero"
      >
        <div class="hero-top">
          <div>
            <h1>校园代拿</h1>
            <p>同学互助，一键发布取件/代拿任务</p>
          </div>
        </div>
        <div class="stats">
          <span>总任务 {{ stats.total }}</span>
          <span>待接单 {{ stats.open }}</span>
          <span>进行中 {{ stats.claimed }}</span>
          <span>已完成 {{ stats.completed }}</span>
        </div>
      </header>

        <HallView v-if="currentView === 'hall'" />

        <PublishView v-if="currentView === 'publish'" />

        <WalletView v-if="currentView === 'wallet'" />

        <CouponsView v-if="currentView === 'coupons'" />

        <RiderApplyView v-if="currentView === 'riderApply'" />

        <ChatView v-if="currentView === 'chat'" />

        <PersonalInfoView v-if="currentView === 'personalInfo'" />

        <ProfileView v-if="currentView === 'profile'" />




    <div v-if="showContactProfileModal" class="detail-mask" @click.self="closeContactProfileModal">
      <section class="detail-panel" style="max-width: 420px;">
        <div class="detail-head">
          <h3>接单员资料</h3>
          <button class="ghost-local" @click="closeContactProfileModal">关闭</button>
        </div>
        <p v-if="contactProfileLoading" class="meta">资料加载中...</p>
        <template v-else-if="selectedContactProfile">
          <p><strong>用户名：</strong>{{ selectedContactProfile.username }}</p>
          <p><strong>身份：</strong>{{ selectedContactProfile.riderApproved ? '已认证接单员' : '普通用户' }}</p>
          <p><strong>平均评分：</strong>{{ selectedContactProfile.claimerAverageRating != null ? `⭐ ${formatRating(selectedContactProfile.claimerAverageRating)}（${selectedContactProfile.claimerRatingCount || 0}条）` : '暂无' }}</p>
          <p><strong>接单数：</strong>{{ selectedContactProfile.claimedTaskCount || 0 }}</p>
          <p><strong>性别：</strong>{{ selectedContactProfile.gender || '未知' }}</p>
          <p><strong>手机号：</strong>{{ selectedContactProfile.phone || '未公开' }}</p>
          <img
            v-if="chatAvatarUrl(selectedContactProfile.username)"
            :src="chatAvatarUrl(selectedContactProfile.username)"
            alt="联系人头像"
            class="publisher-profile-avatar publisher-profile-avatar--brand"
          />
        </template>
        <p v-else class="meta">暂无该用户公开资料</p>
      </section>
    </div>

    <div v-if="completeDialogTaskId != null" class="detail-mask" @click.self="closeCompleteDialog">
      <section class="detail-panel complete-proof-panel">
        <div class="detail-head">
          <h3>提交完成凭证</h3>
          <button class="ghost-local" @click="closeCompleteDialog">关闭</button>
        </div>
        <p class="meta">可上传截图/照片作为证明，并填写完成记录（最多 6 张）。</p>
        <textarea
          v-model="completeProofText"
          rows="4"
          maxlength="500"
          placeholder="例如：已送达到宿舍门口，对方已确认签收。"
        />
        <div class="complete-proof-actions">
          <input
            ref="completeProofFileInputRef"
            type="file"
            accept="image/*"
            multiple
            class="complete-proof-file"
            @change="onCompleteProofFilesSelected"
          />
          <button type="button" class="ghost-local" @click="triggerCompleteProofFilePick">上传照片/截图</button>
          <span class="meta">已上传 {{ completeProofImages.length }} / 6</span>
        </div>
        <div v-if="completeProofImages.length" class="complete-proof-grid">
          <div v-for="(img, idx) in completeProofImages" :key="`proof-${idx}`" class="complete-proof-item">
            <img :src="img" alt="完成凭证" @click="openImagePreview(img)" />
            <button type="button" class="complete-proof-remove" @click="removeCompleteProofImage(idx)">删除</button>
          </div>
        </div>
        <div class="actions" style="margin-top: 12px; justify-content: flex-end;">
          <button class="ghost-local" type="button" @click="closeCompleteDialog">取消</button>
          <button
            type="button"
            :disabled="completeDialogTaskId == null || actionLoadingMap[completeDialogTaskId] || !isLoggedIn"
            @click="completeDialogTaskId != null && completeTask(completeDialogTaskId)"
          >
            {{ completeDialogTaskId != null && actionLoadingMap[completeDialogTaskId] ? '提交中...' : '确认提交完成' }}
          </button>
        </div>
      </section>
    </div>

    <div v-if="proofViewTaskId != null" class="detail-mask" @click.self="closeProofView">
      <section class="detail-panel complete-proof-panel">
        <div class="detail-head">
          <h3>接单方提交凭证</h3>
          <button class="ghost-local" @click="closeProofView">关闭</button>
        </div>
        <div v-if="getTaskCompletionProof(proofViewTaskId)" class="complete-proof-card" style="margin-top: 0;">
          <p v-if="getTaskCompletionProof(proofViewTaskId)?.note" class="desc">
            {{ getTaskCompletionProof(proofViewTaskId)?.note }}
          </p>
          <div v-if="getTaskCompletionProof(proofViewTaskId)?.images?.length" class="complete-proof-grid">
            <div
              v-for="(img, idx) in getTaskCompletionProof(proofViewTaskId)?.images"
              :key="`proof-view-${proofViewTaskId}-${idx}`"
              class="complete-proof-item"
            >
              <img :src="img" alt="完成凭证" @click="openImagePreview(img)" />
            </div>
          </div>
          <p v-else class="meta">未上传图片凭证</p>
        </div>
        <p v-else class="meta">暂无凭证数据</p>
      </section>
    </div>

    <div v-if="selectedTask" class="detail-mask task-detail-mask" @click.self="closeTaskDetail">
      <section class="detail-panel task-detail-panel">
        <div class="task-detail-head">
          <div class="task-detail-head-main">
            <span class="task-detail-emoji" aria-hidden="true">{{ hallTaskEmoji(selectedTask.title) }}</span>
            <div>
              <p class="task-detail-kicker">
                {{ taskTypeLabel(selectedTask.taskType) }}
                <span v-if="isTaskUrgent(selectedTask)" class="task-detail-urgent-chip">加急</span>
              </p>
          <h3>{{ selectedTask.title }}</h3>
        </div>
          </div>
          <button type="button" class="ghost-local" @click="closeTaskDetail">关闭</button>
        </div>

        <div class="task-detail-meta-row">
          <span class="status hall-task-status" :class="statusClass(selectedTask.status)">
            {{ selectedTask.overdueRejected ? '接单员超时' : statusLabelMap[selectedTask.status] }}
          </span>
          <div class="task-detail-reward-inline">
            <span>赏金</span>
            <strong>¥ {{ Number(selectedTask.reward).toFixed(2) }}</strong>
            <span
              v-if="formatUrgentBonusText(selectedTask)"
              class="task-detail-urgent-bonus"
            >加急赏 +¥{{ formatUrgentBonusText(selectedTask) }}</span>
          </div>
        </div>

        <div class="task-detail-route">
          <span class="route-pill route-from">{{ selectedTask.pickupLocation }}</span>
          <span class="route-arrow" aria-hidden="true">→</span>
          <span class="route-pill route-to">{{ selectedTask.dropoffLocation }}</span>
        </div>

        <div class="task-detail-info">
          <p v-if="selectedTask.status === 'REJECTED' && isLoggedIn && selectedTask.publisherName === myName" class="task-detail-note task-detail-note--warn">
          驳回原因：{{ selectedTask.adminRejectRemark || '-' }}
        </p>
          <p class="task-detail-line">
            <span class="task-detail-label">发布人</span>
            <span class="task-detail-publisher">
              <img
                v-if="publisherAvatarUrl(selectedTask.publisherName)"
                class="task-detail-publisher-avatar"
                :src="normalizeImageUrl(publisherAvatarUrl(selectedTask.publisherName))"
                alt=""
                width="28"
                height="28"
              />
              <span v-else class="task-detail-publisher-fallback" aria-hidden="true">
                {{ publisherNameInitial(selectedTask.publisherName) }}
              </span>
              {{ isLoggedIn ? selectedTask.publisherName : maskName(selectedTask.publisherName) }}
            </span>
          </p>
          <p class="task-detail-line">
            <span class="task-detail-label">联系方式</span>
            <span>{{
              canRevealTaskContact(selectedTask)
                ? selectedTask.contact
                : maskContact(selectedTask.contact)
            }}</span>
          </p>
          <p v-if="selectedTask.deliveryStartAt || selectedTask.deliveryEndAt || selectedTask.latestDeliveryAt" class="task-detail-line">
            <span class="task-detail-label">配送时间</span>
            <span>
              <template v-if="selectedTask.deliveryStartAt || selectedTask.deliveryEndAt">
            {{ String(selectedTask.deliveryStartAt || '').replace('T', ' ').slice(0, 16) || '-' }}
            至
            {{ String((selectedTask.deliveryEndAt || selectedTask.latestDeliveryAt || '')).replace('T', ' ').slice(0, 16) || '-' }}
              </template>
              <template v-else>
            {{ String(selectedTask.latestDeliveryAt).replace('T', ' ').slice(0, 16) }}
              </template>
              <span v-if="selectedTask.status === 'OVERDUE'" class="task-detail-overdue"> · 已超时</span>
          </span>
          </p>
          <p v-if="selectedTask.claimerName" class="task-detail-line">
            <span class="task-detail-label">接单人</span>
            <span>
              {{ isLoggedIn ? selectedTask.claimerName : '已有人接单' }}
              <template v-if="isLoggedIn && claimerRatingStatsMap[String(selectedTask.claimerName).toLowerCase()]">
                ｜ ⭐ {{ claimerRatingStatsMap[String(selectedTask.claimerName).toLowerCase()].avg }}
              </template>
            </span>
          </p>
          <p class="task-detail-line">
            <span class="task-detail-label">发布时间</span>
            <span>{{ formatDateOnly(selectedTask.createdAt) }}</span>
          </p>
          <p v-if="selectedTask.description && isLoggedIn" class="task-detail-desc">{{ selectedTask.description }}</p>
        </div>

        <div
          v-if="selectedTask.status === 'PENDING_CONFIRMATION'"
          class="complete-proof-card task-detail-proof"
        >
          <p class="meta"><strong>接单方提交记录</strong></p>
          <template v-if="getTaskCompletionProof(selectedTask.id)">
            <p v-if="getTaskCompletionProof(selectedTask.id)?.note" class="desc">
              {{ getTaskCompletionProof(selectedTask.id)?.note }}
            </p>
            <div v-if="getTaskCompletionProof(selectedTask.id)?.images?.length" class="complete-proof-grid">
              <div
                v-for="(img, idx) in getTaskCompletionProof(selectedTask.id)?.images"
                :key="`detail-proof-${selectedTask.id}-${idx}`"
                class="complete-proof-item"
              >
                <img :src="img" alt="完成凭证" @click="openImagePreview(img)" />
              </div>
            </div>
            <p
              v-else-if="!getTaskCompletionProof(selectedTask.id)?.note"
              class="meta"
            >
              已申请完成，未附带留言或图片。
            </p>
          </template>
          <p v-else class="meta">已申请完成，暂无凭证数据，请刷新后重试。</p>
        </div>

        <div class="task-detail-actions">
          <button
            v-if="selectedTask.status === 'OPEN' && isLoggedIn && isRiderApproved && selectedTask.publisherName !== myName"
            type="button"
            class="ghost-local hall-task-action hall-task-action-ghost"
            @click="askPublisherFirst(selectedTask)"
          >
            先联系发布者
          </button>
          <button
            v-if="selectedTask.status === 'OPEN' && selectedTask.publisherName !== myName && isRiderApproved"
            type="button"
            class="hall-task-action hall-task-action-primary"
            :disabled="!isLoggedIn || actionLoadingMap[selectedTask.id]"
            @click="claimTask(selectedTask.id)"
          >
            接单
          </button>
          <button
            v-if="selectedTask.status === 'CLAIMED' && selectedTask.claimerName === myName"
            type="button"
            class="hall-task-action hall-task-action-primary"
            :disabled="!isLoggedIn || actionLoadingMap[selectedTask.id]"
            @click="openCompleteDialog(selectedTask.id); closeTaskDetail()"
          >
            提交完成
          </button>
          <button
            v-if="selectedTask.status === 'PENDING_CONFIRMATION' && selectedTask.publisherName === myName"
            type="button"
            class="hall-task-action hall-task-action-primary"
            :disabled="!isLoggedIn || actionLoadingMap[selectedTask.id]"
            @click="confirmTask(selectedTask.id)"
          >
            确认完成
          </button>
          <button type="button" class="ghost-local" @click="closeTaskDetail">返回</button>
        </div>
      </section>
    </div>

    <div v-if="appealTask" class="detail-mask" @click.self="closeAppeal">
      <section class="detail-panel" style="max-width: 560px;">
        <div class="detail-head">
          <h3>向管理员申诉</h3>
          <button class="ghost-local" @click="closeAppeal">关闭</button>
        </div>
        <p class="meta" style="margin-top: 0;">
          任务：<strong>{{ appealTask.title }}</strong>（#{{ appealTask.id }}）
        </p>
        <textarea v-model="appealText" rows="5" maxlength="800" placeholder="请输入申诉内容（最多800字）" />
        <div class="actions" style="margin-top: 10px;">
          <button class="primary" :disabled="appealSubmitting" @click="submitAppeal">
            {{ appealSubmitting ? '提交中...' : '提交申诉' }}
          </button>
        </div>
        <p class="meta" style="margin-top: 10px;">提交后会发送给管理员账号 <strong>admin</strong>。</p>
      </section>
    </div>
    </div>

    <section v-if="currentView === 'hall'" class="site-quote">
      <p>每一次顺路代取，都<em>连接着同学之间的信任</em>，也让校园生活更轻松一点。</p>
      <small>校园代拿 · 互助跑腿 / 致每一位用户</small>
    </section>

    <SiteFooter v-if="currentView === 'hall'" />
  </div>

  <div v-if="previewImageUrl" class="detail-mask" @click.self="closeImagePreview">
    <section class="detail-panel image-preview-panel">
      <div class="detail-head">
        <h3>头像预览</h3>
        <button class="ghost-local" @click="closeImagePreview">关闭</button>
      </div>
      <img :src="previewImageUrl" alt="头像预览" class="image-preview-full" />
    </section>
  </div>

  <div v-if="adminAppealReply" class="detail-mask kicked-mask" @click.self="closeAdminAppealReply">
    <section class="detail-panel" style="max-width: 560px;">
      <div class="detail-head">
        <h3>申诉补充说明</h3>
        <button class="ghost-local" @click="closeAdminAppealReply">关闭</button>
      </div>
      <p class="meta" style="margin-top: 0;">
        收件人：<strong>{{ adminAppealReply.toUsername }}</strong>
        <span v-if="adminAppealReply.taskId">｜ 任务 #{{ adminAppealReply.taskId }}</span>
      </p>
      <p class="meta">可选：在裁决前向用户补充说明，将通过系统通知发送（非私聊）。</p>
      <textarea v-model="adminAppealReplyText" rows="5" maxlength="800" placeholder="请输入补充说明（最多800字）" />
      <div class="actions" style="margin-top: 10px;">
        <button class="primary" :disabled="adminAppealReplySubmitting" @click="submitAdminAppealReply">
          {{ adminAppealReplySubmitting ? '发送中...' : '发送' }}
        </button>
      </div>
    </section>
  </div>
</template>

