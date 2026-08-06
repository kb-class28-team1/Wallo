<script setup>
import { computed, onMounted } from "vue"
import { storeToRefs } from "pinia"
import { useRouter } from "vue-router"
import { useMyFeedStore } from "@/stores/myFeedStore"
import { formatNumber, formatWon } from "@/commonUtils/formatters"
import { EXPENSE_CATEGORY_META, FEED_CATEGORY_CODES } from "@/features/financial/financialCategories"

const router = useRouter()
const myFeedStore = useMyFeedStore()
const {
  feeds,
  summary,
  sort,
  category,
  page,
  size,
  totalPages,
  isLoading,
  errorMessage,
} = storeToRefs(myFeedStore)

const sortOptions = [
  { value: "LIKE_DESC", label: "좋아요 많은순" },
  { value: "LATEST", label: "최신순" },
  { value: "SAVING_DESC", label: "절약 금액순" },
]

const categoryOptions = [
  { value: "ALL", label: "전체" },
  ...FEED_CATEGORY_CODES.map((value) => ({
    value,
    label: EXPENSE_CATEGORY_META[value].label,
  })),
]

// 내 챌린지 요약 응답에서 화면 상단에 필요한 네 가지 통계를 구성함.
const summaryCards = computed(() => [
  {
    label: "총 게시물",
    value: `${formatNumber(summary.value?.postCount)}개`,
    className: "summary-post",
  },
  {
    label: "받은 좋아요",
    value: `${formatNumber(summary.value?.receivedLikeCount)}개`,
    className: "summary-like",
  },
  {
    label: "댓글 수",
    value: `${formatNumber(summary.value?.commentCount)}개`,
    className: "summary-comment",
  },
  {
    label: "총 절약 금액",
    value: formatWon(summary.value?.totalSavingAmount),
    className: "summary-saving",
  },
])

const hasPreviousPage = computed(() => page.value > 0)
const hasNextPage = computed(() => page.value + 1 < totalPages.value)
const selectedSortLabel = computed(
  () => sortOptions.find((option) => option.value === sort.value)?.label || "좋아요 많은순",
)

const formatDate = (value) => {
  const date = String(value || "").slice(0, 10)
  return date ? date.replaceAll("-", ".") : ""
}

const getCategoryLabel = (feed) =>
  feed.customCategory || EXPENSE_CATEGORY_META[feed.category]?.label || feed.category || "기타"

const getCategoryIcon = (feed) => EXPENSE_CATEGORY_META[feed.category]?.icon || "bi-receipt"
const isVideoFeed = (feed) => {
  const mediaType = String(feed?.mediaType || "").toUpperCase()
  if (mediaType) {
    return mediaType === "VIDEO"
  }

  return /\.(mp4|webm|ogg|mov)(?:$|[?#])/i.test(String(feed?.mediaUrl || ""))
}
const getFeedImage = (feed) => {
  if (isVideoFeed(feed)) {
    return feed.thumbnailUrl || ""
  }

  return feed.thumbnailUrl || feed.mediaUrl || ""
}

// 이미지가 만료되었거나 불러오기 실패하면 카테고리 아이콘을 대신 표시함.
const handleImageError = (event) => {
  event.target.classList.add("d-none")
}

// 게시물 클릭 시 해당 챌린지 피드에서 선택한 게시물을 바로 찾을 수 있도록 이동함.
const openFeed = (feed) => {
  router.push({
    name: "challenge-feed",
    params: { challengeId: feed.challengeId },
    query: {
      scope: "ME",
      focusFeedId: feed.feedId,
    },
  })
}

const handleSortChange = () => myFeedStore.changeSort(sort.value)
const handleCategoryChange = () => myFeedStore.changeCategory(category.value)
const movePage = (nextPage) => myFeedStore.changePage(nextPage)

onMounted(() => myFeedStore.initializeMyFeedPage())
</script>

<template>
  <section class="my-feed-page">
    <header class="page-heading d-flex align-items-center gap-3 mb-4">
      <button
        type="button"
        class="btn page-back-button"
        aria-label="내 챌린지로 이동"
        @click="router.push({ name: &quot;my-challenge&quot; })"
      >
        <i class="bi bi-chevron-left" aria-hidden="true"></i>
      </button>
      <h1 class="mb-0">내 게시물</h1>
    </header>

    <article class="overview-card">
      <div class="overview-header d-flex flex-wrap justify-content-between gap-3">
        <div>
          <h2>내가 올린 게시물 · {{ selectedSortLabel }}</h2>
          <p class="mb-0">내가 인증한 게시물과 모아 놓은 절약 기록을 확인해보세요.</p>
        </div>

        <div class="filter-group d-flex gap-2">
          <label>
            <span>정렬</span>
            <select v-model="sort" class="form-select" @change="handleSortChange">
              <option v-for="option in sortOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>

          <label>
            <span>카테고리</span>
            <select v-model="category" class="form-select" @change="handleCategoryChange">
              <option
                v-for="option in categoryOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
          </label>
        </div>
      </div>

      <div class="summary-grid">
        <div
          v-for="card in summaryCards"
          :key="card.label"
          class="summary-card"
          :class="card.className"
        >
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
        </div>
      </div>
    </article>

    <div v-if="isLoading && feeds.length === 0" class="page-state-card">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">로딩 중</span>
      </div>
      <span>내 게시물을 불러오는 중입니다...</span>
    </div>

    <div v-else-if="errorMessage && feeds.length === 0" class="page-state-card error-state">
      <span>{{ errorMessage }}</span>
      <button type="button" class="btn retry-button" @click="myFeedStore.initializeMyFeedPage">
        다시 시도
      </button>
    </div>

    <div v-else-if="feeds.length === 0" class="page-state-card">
      <span class="empty-icon" aria-hidden="true">📝</span>
      <strong>조건에 맞는 게시물이 없습니다.</strong>
      <span>절약 인증 게시물을 작성하면 이곳에서 확인할 수 있습니다.</span>
    </div>

    <template v-else>
      <div class="feed-grid">
        <article
          v-for="(feed, index) in feeds"
          :key="feed.feedId"
          class="feed-card"
          role="link"
          tabindex="0"
          @click="openFeed(feed)"
          @keydown.enter="openFeed(feed)"
        >
          <span class="feed-order">{{ page * size + index + 1 }}</span>

          <div class="feed-thumbnail">
            <span aria-hidden="true"><i :class="['bi', getCategoryIcon(feed)]"></i></span>
            <video
              v-if="isVideoFeed(feed) && feed.mediaUrl"
              :src="feed.mediaUrl"
              autoplay
              muted
              loop
              playsinline
              preload="metadata"
              aria-label="절약 인증 영상 미리보기"
              @error="handleImageError"
            ></video>
            <img
              v-else-if="getFeedImage(feed)"
              :src="getFeedImage(feed)"
              alt="게시물 미리보기"
              @error="handleImageError"
            />
          </div>

          <div class="feed-content">
            <strong>{{ feed.caption || "제목 없는 절약 인증" }}</strong>
            <span>절약 인증 게시물</span>
            <div class="feed-meta">
              <span class="category-badge">{{ getCategoryLabel(feed) }}</span>
              <time :datetime="feed.createdAt">{{ formatDate(feed.createdAt) }}</time>
            </div>
          </div>

          <div class="feed-result">
            <span class="like-count">♥ {{ formatNumber(feed.likeCount) }}</span>
            <span class="comment-count">💬 {{ formatNumber(feed.commentCount) }}</span>
            <strong>{{ formatWon(feed.savingAmount) }}</strong>
          </div>
        </article>
      </div>

      <nav v-if="totalPages > 1" class="pagination-wrap" aria-label="내 게시물 페이지 이동">
        <button
          type="button"
          class="btn page-button"
          :disabled="!hasPreviousPage || isLoading"
          @click="movePage(page - 1)"
        >
          이전
        </button>
        <span>{{ page + 1 }} / {{ totalPages }}</span>
        <button
          type="button"
          class="btn page-button"
          :disabled="!hasNextPage || isLoading"
          @click="movePage(page + 1)"
        >
          다음
        </button>
      </nav>
    </template>
  </section>
</template>

<style scoped>
.my-feed-page {
  width: 100%;
  color: #27304f;
}

.page-heading h1 {
  font-size: 28px;
  font-weight: 800;
}

.overview-card,
.feed-card,
.page-state-card {
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 5px 20px rgb(48 60 110 / 5%);
}

.overview-card {
  padding: 26px;
}

.overview-header h2 {
  margin-bottom: 4px;
  font-size: 19px;
  font-weight: 800;
}

.overview-header p {
  color: #8992ae;
  font-size: 14px;
}

.filter-group label {
  min-width: 132px;
}

.filter-group label > span {
  display: block;
  margin-bottom: 5px;
  color: #9ba3bb;
  font-size: 12px;
}

.filter-group .form-select {
  border-color: #e8eaf3;
  border-radius: 10px;
  color: #4f5878;
  font-size: 14px;
  cursor: pointer;
}

.filter-group .form-select:focus {
  border-color: #9f96f8;
  box-shadow: 0 0 0 3px rgb(126 113 246 / 12%);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 24px;
}

.summary-card {
  display: grid;
  min-height: 78px;
  place-content: center;
  gap: 5px;
  border-radius: 13px;
  background: #f8f8ff;
  text-align: center;
}

.summary-card span {
  color: #9aa2bc;
  font-size: 13px;
}

.summary-card strong {
  color: #283150;
  font-size: 20px;
}

.summary-like strong {
  color: #ef587d;
}

.summary-comment strong {
  color: #7667ef;
}

.summary-saving strong {
  color: #c58a00;
}

.feed-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.feed-card {
  position: relative;
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) auto;
  gap: 14px;
  min-height: 112px;
  align-items: center;
  padding: 20px;
  cursor: pointer;
  transition: transform 160ms ease, box-shadow 160ms ease;
}

.feed-card:hover,
.feed-card:focus-visible {
  outline: 0;
  box-shadow: 0 10px 26px rgb(71 66 143 / 12%);
  transform: translateY(-2px);
}

.feed-order {
  position: absolute;
  top: -7px;
  left: -7px;
  display: grid;
  width: 25px;
  height: 25px;
  place-items: center;
  border-radius: 50%;
  background: #edf0f8;
  color: #6f7897;
  font-size: 12px;
  font-weight: 800;
}

.feed-thumbnail {
  position: relative;
  display: grid;
  width: 56px;
  height: 56px;
  place-items: center;
  overflow: hidden;
  border-radius: 14px;
  background: #f2f0ff;
  font-size: 25px;
}

.feed-thumbnail img,
.feed-thumbnail video {
  position: absolute;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.feed-content {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.feed-content > strong {
  overflow: hidden;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-content > span,
.feed-meta time {
  color: #99a2bc;
  font-size: 12px;
}

.feed-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
}

.category-badge {
  padding: 3px 8px;
  border-radius: 999px;
  background: #f1efff;
  color: #7769ed;
  font-size: 11px;
  font-weight: 700;
}

.feed-result {
  display: grid;
  justify-items: end;
  gap: 4px;
  font-size: 12px;
}

.like-count {
  color: #ef557c;
  font-weight: 700;
}

.comment-count {
  color: #a0a7bc;
}

.feed-result strong {
  margin-top: 4px;
  color: #c58a00;
  font-size: 14px;
}

.page-state-card {
  display: grid;
  min-height: 290px;
  place-items: center;
  align-content: center;
  gap: 13px;
  margin-top: 16px;
  color: #8c95af;
}

.page-state-card.error-state {
  color: #d45b72;
}

.empty-icon {
  font-size: 34px;
}

.retry-button,
.page-button {
  border: 1px solid #8478f5;
  color: #7065eb;
  font-weight: 700;
}

.retry-button:hover,
.page-button:hover:not(:disabled) {
  background: #7b70f5;
  color: #fff;
}

.pagination-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 22px;
  color: #757e9d;
  font-size: 14px;
  font-weight: 700;
}

@media (max-width: 991.98px) {
  .feed-grid {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 575.98px) {
  .overview-card {
    padding: 18px;
  }

  .filter-group {
    width: 100%;
  }

  .filter-group label {
    width: 50%;
    min-width: 0;
  }

  .feed-card {
    grid-template-columns: 48px minmax(0, 1fr);
    padding: 17px;
  }

  .feed-thumbnail {
    width: 48px;
    height: 48px;
  }

  .feed-result {
    grid-column: 2;
    grid-template-columns: repeat(3, auto);
    justify-content: start;
    justify-items: start;
  }
}
</style>
