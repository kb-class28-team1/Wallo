<script setup>
import { computed, onMounted, ref } from "vue"
import { Line } from "vue-chartjs"
import {
  CategoryScale,
  Chart as ChartJS,
  Filler,
  LinearScale,
  LineElement,
  PointElement,
  Tooltip,
} from "chart.js"
import { getMyChallengeDashboard } from "@/api/challengeApi"

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Filler)

const DEFAULT_PROFILE_IMAGE = "/images/profiles/default-profile.svg"

const dashboard = ref(null)
const isLoading = ref(true)
const errorMessage = ref("")

// API 응답의 최근 6개월 데이터를 절약 금액 차트 형식으로 변환함
const chartData = computed(() => ({
  labels: (dashboard.value?.monthlySavings || []).map((item) => formatMonth(item.month)),
  datasets: [
    {
      data: (dashboard.value?.monthlySavings || []).map((item) => item.savingAmount || 0),
      borderColor: "#f5aa00",
      backgroundColor: "rgb(255 222 116 / 28%)",
      pointBackgroundColor: "#f5aa00",
      pointBorderWidth: 0,
      pointRadius: 4,
      pointHoverRadius: 5,
      borderWidth: 3,
      fill: true,
      tension: 0.35,
    },
  ],
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: {
    intersect: false,
    mode: "index",
  },
  plugins: {
    legend: {
      display: false,
    },
    tooltip: {
      callbacks: {
        label: (context) => formatCurrency(context.parsed.y),
      },
    },
  },
  scales: {
    x: {
      border: {
        display: false,
      },
      grid: {
        display: false,
      },
      ticks: {
        color: "#a1a9c2",
        font: {
          size: 11,
        },
      },
    },
    y: {
      display: false,
      beginAtZero: true,
      grid: {
        display: false,
      },
    },
  },
}

const savingChangeRate = computed(() => Number(dashboard.value?.savingChangeRate || 0))
const savingChangeClass = computed(() => ({
  "change-positive": savingChangeRate.value > 0,
  "change-negative": savingChangeRate.value < 0,
}))
const savingChangeLabel = computed(() => {
  if (savingChangeRate.value === 0) {
    return "지난달과 동일함"
  }

  const sign = savingChangeRate.value > 0 ? "+" : ""
  return `${sign}${savingChangeRate.value}% 지난달 대비`
})

const activityStats = computed(() => [
  { icon: "📝", label: "게시글", value: formatCount(dashboard.value?.postCount) },
  { icon: "💗", label: "좋아요", value: formatCount(dashboard.value?.receivedLikeCount) },
  { icon: "💬", label: "댓글", value: formatCount(dashboard.value?.commentCount) },
  { icon: "🔥", label: "연속 출석", value: `${dashboard.value?.streakDays || 0}일` },
])

const summaryStats = computed(() => [
  {
    label: "이번 달 절약",
    value: formatCurrency(dashboard.value?.currentMonthSavingAmount),
    subText: savingChangeLabel.value,
  },
  {
    label: "지난달 절약",
    value: formatCurrency(dashboard.value?.previousMonthSavingAmount),
    subText: "지난달 기록",
  },
  {
    label: "총 인증 횟수",
    value: `${Number(dashboard.value?.verificationCount || 0).toLocaleString("ko-KR")}회`,
    subText: `연속 ${dashboard.value?.streakDays || 0}일`,
  },
  {
    label: "평균 절약/건",
    value: formatCurrency(dashboard.value?.averageSavingAmount),
    subText: "인증 게시물 기준",
  },
])

const formatCurrency = (amount) => `${Number(amount || 0).toLocaleString("ko-KR")}원`
const formatCount = (count) => Number(count || 0).toLocaleString("ko-KR")
const formatMonth = (month) => {
  const monthNumber = Number(String(month || "").split("-")[1])
  return monthNumber ? `${monthNumber}월` : month
}
const formatDate = (date) => String(date || "").replaceAll("-", ".")

// 프로필 이미지가 없거나 로드에 실패하면 프로젝트 기본 이미지를 사용함
const profileImage = computed(() => dashboard.value?.profileImageUrl || DEFAULT_PROFILE_IMAGE)
const handleProfileImageError = (event) => {
  event.target.src = DEFAULT_PROFILE_IMAGE
}

// 썸네일이 없는 피드는 원본 미디어를 사용하고 모두 없으면 아이콘을 표시함
const feedImage = (feed) => feed.thumbnailUrl || feed.mediaUrl || ""
const handleFeedImageError = (event) => {
  event.target.classList.add("d-none")
}

const loadDashboard = async () => {
  isLoading.value = true
  errorMessage.value = ""

  try {
    dashboard.value = await getMyChallengeDashboard()
  } catch (error) {
    errorMessage.value = error.message
    window.alert(error.message)
  } finally {
    isLoading.value = false
  }
}

// 페이지 진입 시 하드코딩 값이 아닌 로그인 사용자의 DB 집계 결과를 불러옴
onMounted(loadDashboard)
</script>

<template>
  <section class="my-challenge-page">
    <header class="page-heading mb-3">
      <h1 class="mb-0">내 챌린지</h1>
    </header>

    <div v-if="isLoading" class="dashboard-state-card">내 챌린지 정보를 불러오는 중임...</div>

    <div v-else-if="errorMessage" class="dashboard-state-card error-state">
      <p class="mb-3">{{ errorMessage }}</p>
      <button type="button" class="btn retry-button" @click="loadDashboard">다시 시도</button>
    </div>

    <div v-else-if="dashboard" class="dashboard-grid">
      <article class="dashboard-card profile-card">
        <div class="profile-header">
          <div class="profile-avatar">
            <img
              :src="profileImage"
              :alt="`${dashboard.nickname} 프로필 이미지`"
              @error="handleProfileImageError"
            />
          </div>
          <div>
            <strong class="profile-name">{{ dashboard.nickname }}</strong>
            <span class="saving-badge">오늘도 절약 성공! 💪</span>
          </div>
        </div>

        <dl class="profile-details mb-0">
          <div>
            <dt>가입일</dt>
            <dd>{{ formatDate(dashboard.joinedAt) }}</dd>
          </div>
          <div>
            <dt>현재 챌린지</dt>
            <dd>{{ dashboard.currentChallengeName }}</dd>
          </div>
          <div>
            <dt>현재 연속 출석</dt>
            <dd class="streak-value">{{ dashboard.streakDays }}일 🔥</dd>
          </div>
          <div>
            <dt>총 인증 횟수</dt>
            <dd>{{ formatCount(dashboard.verificationCount) }}회</dd>
          </div>
        </dl>
      </article>

      <article class="dashboard-card saving-summary-card">
        <div class="total-saving">
          <span>총 절약 금액</span>
          <strong>{{ formatCurrency(dashboard.totalSavingAmount) }}</strong>
          <small>누적 절약 금액</small>
        </div>

        <div class="summary-stat-grid">
          <div v-for="stat in summaryStats" :key="stat.label" class="summary-stat">
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
            <small :class="stat.label === '이번 달 절약' ? savingChangeClass : null">
              {{ stat.subText }}
            </small>
          </div>
        </div>

        <div class="activity-badges">
          <span v-for="stat in activityStats" :key="stat.label">
            {{ stat.icon }} {{ stat.label }} <strong>{{ stat.value }}</strong>
          </span>
        </div>
      </article>

      <article class="dashboard-card trend-card">
        <div class="card-heading">
          <div>
            <h2>절약 금액 추이</h2>
            <strong :class="savingChangeClass">{{ savingChangeLabel }}</strong>
          </div>
          <span class="period-badge">6개월</span>
        </div>

        <div class="trend-chart" aria-label="최근 6개월 절약 금액 차트">
          <Line :data="chartData" :options="chartOptions" />
        </div>
      </article>

      <article class="dashboard-card liked-feed-card">
        <div class="card-heading">
          <h2>좋아요 받은 게시물 TOP 3</h2>
        </div>

        <ol v-if="dashboard.topLikedFeeds.length" class="liked-feed-list list-unstyled mb-0">
          <li v-for="(feed, index) in dashboard.topLikedFeeds" :key="feed.feedId">
            <strong class="feed-rank">{{ index + 1 }}</strong>
            <div class="feed-thumbnail">
              <span aria-hidden="true">🐧</span>
              <img
                v-if="feedImage(feed)"
                :src="feedImage(feed)"
                alt="인증 게시물 미리보기"
                @error="handleFeedImageError"
              />
            </div>
            <div class="feed-copy">
              <strong>{{ feed.caption || "제목 없는 인증 게시물" }}</strong>
              <span>{{ formatDate(feed.createdAt?.slice(0, 10)) }}</span>
            </div>
            <span class="feed-like">♥ {{ formatCount(feed.likeCount) }}</span>
          </li>
        </ol>

        <div v-else class="empty-feed-state">아직 작성한 인증 게시물이 없음.</div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.my-challenge-page {
  width: 100%;
  color: #202947;
}

.page-heading h1 {
  font-size: 25px;
  font-weight: 750;
}

.dashboard-state-card {
  display: grid;
  min-height: 260px;
  place-items: center;
  border-radius: 18px;
  background: #fff;
  color: #7b83a5;
  box-shadow: 0 5px 20px rgb(48 60 110 / 5%);
}

.dashboard-state-card.error-state {
  align-content: center;
  color: #d45b72;
  text-align: center;
}

.retry-button {
  border-color: #7b70f5;
  color: #6d62eb;
}

.retry-button:hover {
  background: #7b70f5;
  color: #fff;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 16px;
}

.dashboard-card {
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 5px 20px rgb(48 60 110 / 5%);
}

.profile-card,
.saving-summary-card {
  min-height: 230px;
  padding: 24px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 15px;
  padding-bottom: 18px;
  border-bottom: 1px solid #eef0f7;
}

.profile-avatar {
  display: grid;
  width: 62px;
  height: 62px;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  background: #8174f7;
}

.profile-avatar img {
  width: 44px;
  height: 44px;
  object-fit: contain;
}

.profile-name {
  display: block;
  margin-bottom: 7px;
  font-size: 16px;
}

.saving-badge {
  display: inline-flex;
  padding: 4px 9px;
  border-radius: 999px;
  background: #fff8df;
  color: #9b7a21;
  font-size: 10px;
  font-weight: 700;
}

.profile-details {
  display: grid;
  gap: 9px;
  padding-top: 17px;
}

.profile-details div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  font-size: 12px;
}

.profile-details dt {
  color: #8e97b5;
  font-weight: 500;
}

.profile-details dd {
  max-width: 65%;
  margin: 0;
  overflow: hidden;
  color: #273050;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-details .streak-value {
  color: #e86750;
}

.total-saving > span,
.total-saving > small {
  display: block;
  color: #9aa3bf;
  font-size: 11px;
}

.total-saving > strong {
  display: block;
  margin: 5px 0 2px;
  color: #1d2543;
  font-size: 27px;
}

.summary-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 18px;
}

.summary-stat {
  min-width: 0;
  padding: 12px;
  border-radius: 12px;
  background: #f8f8ff;
}

.summary-stat span,
.summary-stat small {
  display: block;
  overflow: hidden;
  color: #9aa3bf;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-stat strong {
  display: block;
  margin: 5px 0;
  color: #242d4d;
  font-size: 13px;
}

.change-positive {
  color: #31ad6c !important;
}

.change-negative {
  color: #e36a74 !important;
}

.activity-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 15px;
}

.activity-badges span {
  padding: 5px 9px;
  border-radius: 999px;
  background: #f8f8ff;
  color: #8d96b2;
  font-size: 10px;
}

.activity-badges strong {
  color: #566082;
}

.trend-card,
.liked-feed-card {
  min-height: 285px;
  padding: 22px;
}

.card-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.card-heading h2 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 750;
}

.card-heading strong {
  font-size: 11px;
}

.period-badge {
  padding: 6px 10px;
  border: 1px solid #eceef7;
  border-radius: 8px;
  color: #7d86a3;
  font-size: 10px;
}

.trend-chart {
  height: 205px;
  margin-top: 16px;
}

.liked-feed-list {
  margin-top: 10px;
}

.liked-feed-list li {
  display: grid;
  grid-template-columns: 24px 38px minmax(0, 1fr) auto;
  gap: 10px;
  min-height: 65px;
  align-items: center;
  border-top: 1px solid #f0f2f8;
}

.liked-feed-list li:first-child {
  border-top: 0;
}

.feed-rank {
  color: #7c70f5;
  font-size: 13px;
}

.feed-thumbnail {
  position: relative;
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  overflow: hidden;
  border-radius: 10px;
  background: #f3f1ff;
}

.feed-thumbnail img {
  position: absolute;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.feed-copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.feed-copy strong {
  overflow: hidden;
  color: #283150;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-copy span {
  color: #a2aac2;
  font-size: 9px;
}

.feed-like {
  color: #ef7898;
  font-size: 11px;
  font-weight: 700;
}

.empty-feed-state {
  display: grid;
  min-height: 195px;
  place-items: center;
  color: #9aa3bd;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767.98px) {
  .profile-card,
  .saving-summary-card,
  .trend-card,
  .liked-feed-card {
    padding: 18px;
  }

  .summary-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
