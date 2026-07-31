<script setup>
import { computed, nextTick, onMounted, ref, watch } from "vue"
import { storeToRefs } from "pinia"
import { useRoute, useRouter } from "vue-router"
import PagePlaceholder from "@/components/common/PagePlaceholder.vue"
import { useMyFeedStore } from "@/stores/myFeedStore"

const route = useRoute()
const router = useRouter()
const myFeedStore = useMyFeedStore()
const { feeds, isFeedLoading, errorMessage } = storeToRefs(myFeedStore)

const focusedFeedElement = ref(null)
const scope = computed(() => String(route.query.scope || "ALL").toUpperCase())
const focusedFeedId = computed(() => String(route.query.focusFeedId || ""))
const isMyFeedScope = computed(() => scope.value === "ME")

const categoryLabels = {
  CAFE: "카페",
  DELIVERY: "배달",
  SHOPPING: "쇼핑",
  TRANSPORT: "교통",
}

const categoryIcons = {
  CAFE: "☕",
  DELIVERY: "🍱",
  SHOPPING: "🛍️",
  TRANSPORT: "🚌",
}

const hasFocusedFeed = computed(() =>
  feeds.value.some((feed) => String(feed.feedId) === focusedFeedId.value),
)

const isFocusedFeed = (feed) => String(feed.feedId) === focusedFeedId.value
const getFeedCardClass = (feed) => (isFocusedFeed(feed) ? "focused-feed" : "")
const getCategoryLabel = (feed) =>
  feed.customCategory || categoryLabels[feed.category] || feed.category || "기타"
const getCategoryIcon = (feed) => categoryIcons[feed.category] || "💡"
const getFeedImage = (feed) => feed.thumbnailUrl || feed.mediaUrl || ""
const formatNumber = (value) => Number(value || 0).toLocaleString("ko-KR")
const formatCurrency = (value) => `${formatNumber(value)}원`
const formatDate = (value) => String(value || "").slice(0, 10).replaceAll("-", ".")

const handleImageError = (event) => {
  event.target.classList.add("d-none")
}

const goToMyFeeds = () => router.push("/my-feeds")

// 선택한 게시물이 DOM에 표시된 뒤 해당 위치로 부드럽게 이동함.
const scrollToFocusedFeed = async () => {
  if (!focusedFeedId.value) {
    return
  }

  await nextTick()
  focusedFeedElement.value?.scrollIntoView({
    behavior: "smooth",
    block: "center",
  })
}

// 내 게시물 화면을 거치지 않고 주소로 직접 접근한 경우 목록을 한 번 조회함.
const initializeFocusedFeed = async () => {
  if (!isMyFeedScope.value) {
    return
  }

  if (feeds.value.length === 0) {
    await myFeedStore.fetchMyFeeds()
  }

  await scrollToFocusedFeed()
}

watch([focusedFeedId, feeds], scrollToFocusedFeed, { flush: "post" })
onMounted(initializeFocusedFeed)
</script>

<template>
  <section v-if="isMyFeedScope" class="focused-feed-page">
    <header class="page-heading d-flex align-items-center justify-content-between gap-3 mb-4">
      <div>
        <span class="page-eyebrow">내 챌린지</span>
        <h1 class="mb-0">내 인증 게시물</h1>
      </div>
      <button type="button" class="btn back-button" @click="goToMyFeeds">
        <i class="bi bi-chevron-left" aria-hidden="true"></i>
        내 게시물로 돌아가기
      </button>
    </header>

    <div v-if="isFeedLoading && feeds.length === 0" class="feed-state-card">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">로딩 중</span>
      </div>
      <span>게시물을 불러오는 중입니다...</span>
    </div>

    <div v-else-if="errorMessage && feeds.length === 0" class="feed-state-card error-state">
      <span>{{ errorMessage }}</span>
      <button type="button" class="btn retry-button" @click="myFeedStore.fetchMyFeeds">
        다시 시도
      </button>
    </div>

    <div v-else-if="feeds.length === 0" class="feed-state-card">
      표시할 내 게시물이 없습니다.
    </div>

    <template v-else>
      <div v-if="focusedFeedId && !hasFocusedFeed" class="alert focus-alert" role="alert">
        현재 불러온 목록에서 선택한 게시물을 찾지 못했습니다.
      </div>

      <div class="feed-list">
        <article
          v-for="feed in feeds"
          :key="feed.feedId"
          :ref="(element) => { if (isFocusedFeed(feed)) focusedFeedElement = element }"
          class="feed-card"
          :class="getFeedCardClass(feed)"
          :data-feed-id="feed.feedId"
        >
          <div class="feed-thumbnail">
            <span aria-hidden="true">{{ getCategoryIcon(feed) }}</span>
            <img
              v-if="getFeedImage(feed)"
              :src="getFeedImage(feed)"
              alt="인증 게시물 미리보기"
              @error="handleImageError"
            />
          </div>

          <div class="feed-body">
            <div class="d-flex flex-wrap align-items-center gap-2">
              <span class="category-badge">{{ getCategoryLabel(feed) }}</span>
              <time :datetime="feed.createdAt">{{ formatDate(feed.createdAt) }}</time>
            </div>
            <h2>{{ feed.caption || "제목 없는 절약 인증" }}</h2>
            <p class="mb-0">총 {{ formatCurrency(feed.savingAmount) }}을 절약한 인증 게시물입니다.</p>
          </div>

          <div class="feed-stats">
            <span class="like-count">♥ {{ formatNumber(feed.likeCount) }}</span>
            <span>💬 {{ formatNumber(feed.commentCount) }}</span>
          </div>

          <span v-if="isFocusedFeed(feed)" class="focus-badge">
            선택한 게시물
          </span>
        </article>
      </div>
    </template>
  </section>

  <PagePlaceholder v-else title="챌린지 피드 페이지" />
</template>

<style scoped>
.focused-feed-page {
  width: 100%;
  color: #27304f;
}

.page-eyebrow {
  display: block;
  margin-bottom: 4px;
  color: #8b94af;
  font-size: 13px;
  font-weight: 700;
}

.page-heading h1 {
  font-size: 28px;
  font-weight: 800;
}

.back-button,
.retry-button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid #8175f4;
  color: #6f64e9;
  font-weight: 700;
}

.back-button:hover,
.retry-button:hover {
  background: #7b70f5;
  color: #fff;
}

.feed-list {
  display: grid;
  gap: 16px;
}

.feed-card,
.feed-state-card {
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 5px 20px rgb(48 60 110 / 5%);
}

.feed-card {
  position: relative;
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  gap: 20px;
  min-height: 154px;
  align-items: center;
  padding: 26px;
  border: 2px solid transparent;
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.feed-card.focused-feed {
  border-color: #8175f4;
  box-shadow: 0 12px 34px rgb(100 85 220 / 18%);
  transform: translateY(-2px);
  animation: focus-pulse 800ms ease-out;
}

.feed-thumbnail {
  position: relative;
  display: grid;
  width: 96px;
  height: 96px;
  place-items: center;
  overflow: hidden;
  border-radius: 18px;
  background: #f1efff;
  font-size: 36px;
}

.feed-thumbnail img {
  position: absolute;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.feed-body {
  min-width: 0;
}

.feed-body time,
.feed-body p,
.feed-stats {
  color: #929bb5;
  font-size: 13px;
}

.feed-body h2 {
  margin: 12px 0 7px;
  font-size: 20px;
  font-weight: 800;
}

.category-badge {
  padding: 4px 9px;
  border-radius: 999px;
  background: #f1efff;
  color: #7467ec;
  font-size: 12px;
  font-weight: 700;
}

.feed-stats {
  display: grid;
  justify-items: end;
  gap: 8px;
  font-weight: 700;
}

.like-count {
  color: #ef587d;
}

.focus-badge {
  position: absolute;
  top: -11px;
  right: 18px;
  padding: 5px 11px;
  border-radius: 999px;
  background: #7b70f5;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.feed-state-card {
  display: grid;
  min-height: 280px;
  place-items: center;
  align-content: center;
  gap: 14px;
  color: #8c95af;
}

.feed-state-card.error-state {
  color: #d45b72;
}

.focus-alert {
  border-color: #ddd8ff;
  background: #f5f3ff;
  color: #6c61d9;
}

@keyframes focus-pulse {
  from {
    box-shadow: 0 0 0 10px rgb(123 112 245 / 18%);
  }
  to {
    box-shadow: 0 12px 34px rgb(100 85 220 / 18%);
  }
}

@media (max-width: 767.98px) {
  .page-heading {
    align-items: flex-start !important;
    flex-direction: column;
  }

  .feed-card {
    grid-template-columns: 68px minmax(0, 1fr);
    padding: 20px;
  }

  .feed-thumbnail {
    width: 68px;
    height: 68px;
  }

  .feed-stats {
    grid-column: 2;
    grid-template-columns: repeat(2, auto);
    justify-content: start;
  }
}
</style>
