<script setup>
import { computed, onMounted } from "vue"
import { storeToRefs } from "pinia"
import { useChallengeStore } from "@/stores/challengeStore"
import { formatNumber, formatWon } from "@/utils/formatters"

const DEFAULT_PROFILE_IMAGE = "/images/profiles/default-profile.svg"
const challengeStore = useChallengeStore()

// Pinia의 반응형 상태를 유지한 채 화면에서 사용할 값으로 분리함
const { startDate, endDate, rankings, myRanking, isLoading, errorMessage } =
  storeToRefs(challengeStore)

// 상위 카드가 시안처럼 2위, 1위, 3위 순서로 배치되도록 DB 조회 결과를 정렬함
const topRankings = computed(() =>
  [2, 1, 3].map((rank) => rankings.value.find((ranking) => ranking.rank === rank)).filter(Boolean),
)

// 상위 3명을 제외한 4위 이후 DB 조회 결과를 숫자 순위로 모두 표시함
const remainingRankings = computed(() => rankings.value.filter((ranking) => ranking.rank >= 4))

// 보상 금액도 각 순위 응답의 rewardPoint를 사용하여 표시함
const rankingRewards = computed(() => [
  { medal: "👑", label: "1등", point: findRewardPoint(1) },
  { medal: "🥈", label: "2등", point: findRewardPoint(2) },
  { medal: "🥉", label: "3등", point: findRewardPoint(3) },
  { medal: "", label: "4~10등", point: findRewardPoint(4) },
])

const rankingPeriod = computed(() => {
  if (!startDate.value || !endDate.value) {
    return ""
  }

  return `${formatDate(startDate.value)} ~ ${formatDate(endDate.value)} (이번 주)`
})

const formatPoint = (point) => `${formatNumber(point)}P`
const formatDate = (date) => date.replaceAll("-", ".")

function findRewardPoint(rank) {
  return rankings.value.find((ranking) => ranking.rank === rank)?.rewardPoint || 0
}

// DB 프로필 주소가 없거나 이미지 로드에 실패하면 기본 프로필을 표시함
const profileImage = (url) => url || DEFAULT_PROFILE_IMAGE
const handleImageError = (event) => {
  event.target.src = DEFAULT_PROFILE_IMAGE
}

// 페이지에 진입하면 API를 호출하여 V_WEEKLY_RANKING 조회 결과를 가져옴
onMounted(() => {
  challengeStore.fetchWeeklyRanking()
})
</script>

<template>
  <section class="ranking-page">
    <header class="ranking-heading mb-3">
      <h1 class="mb-2">주간 랭킹</h1>
      <p class="mb-0">
        매주 <strong>월요일 00시</strong>에 랭킹이 초기화됨
        <template v-if="rankingPeriod"> · {{ rankingPeriod }}</template>
      </p>
    </header>

    <div v-if="isLoading" class="ranking-state-card">주간 랭킹을 불러오는 중임...</div>

    <div v-else-if="errorMessage" class="ranking-state-card error-state">
      {{ errorMessage }}
    </div>

    <div v-else-if="rankings.length === 0" class="ranking-state-card">
      이번 주 랭킹 데이터가 아직 없음.
    </div>

    <div v-else class="ranking-layout">
      <div class="ranking-main">
        <div class="podium-grid mb-3">
          <article
            v-for="ranking in topRankings"
            :key="ranking.rank"
            class="podium-card"
            :class="`rank-${ranking.rank}`"
          >
            <span class="rank-badge">{{ ranking.rank }}</span>
            <span v-if="ranking.rank === 1" class="crown" aria-hidden="true">👑</span>
            <div class="profile-circle">
              <img
                :src="profileImage(ranking.profileImageUrl)"
                :alt="`${ranking.nickname} 프로필 이미지`"
                @error="handleImageError"
              />
            </div>
            <strong class="podium-nickname">{{ ranking.nickname }}</strong>
            <strong class="podium-saving">{{ formatWon(ranking.savingAmount) }}</strong>
            <span class="podium-streak">🔥 {{ ranking.streakDays }}일 연속</span>
          </article>
        </div>

        <div class="ranking-table-card">
          <div class="ranking-table-header ranking-row">
            <span>순위</span>
            <span>닉네임</span>
            <span>절약 금액</span>
            <span>연속 인증</span>
            <span>좋아요</span>
            <span aria-hidden="true"></span>
          </div>

          <div
            v-for="ranking in remainingRankings"
            :key="ranking.rank"
            class="ranking-row ranking-item"
          >
            <strong class="rank-number">{{ ranking.rank }}</strong>
            <div class="ranking-user">
              <img
                :src="profileImage(ranking.profileImageUrl)"
                :alt="`${ranking.nickname} 프로필 이미지`"
                @error="handleImageError"
              />
              <span>{{ ranking.nickname }}</span>
            </div>
            <strong>{{ formatWon(ranking.savingAmount) }}</strong>
            <span>{{ ranking.streakDays }}일</span>
            <span>{{ ranking.likeCount }}</span>
            <span class="heart" aria-label="좋아요">♡</span>
          </div>
        </div>

        <div class="ranking-notice mt-3">🔥 연속 인증은 오늘 인증까지 포함된 연속 인증 일수임!</div>
      </div>

      <aside class="ranking-sidebar">
        <article v-if="myRanking" class="side-card my-rank-card">
          <h2>내 순위</h2>
          <div class="my-rank-user">
            <div class="ranking-user">
              <img
                :src="profileImage(myRanking.profileImageUrl)"
                alt="내 프로필 이미지"
                @error="handleImageError"
              />
              <strong>{{ myRanking.nickname }}</strong>
            </div>
            <strong class="my-rank-badge">{{ myRanking.rank }}위</strong>
          </div>
          <div class="my-rank-stats">
            <div>
              <span>절약 금액</span>
              <strong>{{ formatWon(myRanking.savingAmount) }}</strong>
            </div>
            <div>
              <span>연속 인증</span>
              <strong>{{ myRanking.streakDays }}일</strong>
            </div>
            <div>
              <span>좋아요</span>
              <strong>{{ myRanking.likeCount }}</strong>
            </div>
          </div>
        </article>

        <article v-if="myRanking" class="side-card">
          <h2>이번 주 나의 기록</h2>
          <ul class="record-list list-unstyled mb-0">
            <li>
              <span>💰 절약 금액</span><strong>{{ formatWon(myRanking.savingAmount) }}</strong>
            </li>
            <li>
              <span>🔥 연속 인증</span><strong>{{ myRanking.streakDays }}일</strong>
            </li>
            <li>
              <span>💗 받은 좋아요</span><strong>{{ myRanking.likeCount }}개</strong>
            </li>
          </ul>
        </article>

        <article class="side-card">
          <h2>🎁 랭킹 보상 안내</h2>
          <ul class="reward-list list-unstyled mb-0">
            <li
              v-for="reward in rankingRewards"
              :key="reward.label"
              :class="{ rewardWithoutMedal: !reward.medal }"
            >
              <span v-if="reward.medal" class="reward-medal">{{ reward.medal }}</span>
              <span>{{ reward.label }}</span>
              <strong>{{ formatPoint(reward.point) }}</strong>
            </li>
          </ul>
        </article>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.ranking-page {
  width: 100%;
  color: #1f2a52;
}

.ranking-heading h1 {
  font-size: 25px;
  font-weight: 750;
}

.ranking-heading p {
  color: #8e98ba;
  font-size: 12px;
}

.ranking-heading strong {
  color: #766cf5;
}

.ranking-state-card {
  display: grid;
  min-height: 240px;
  place-items: center;
  border-radius: 18px;
  background: #fff;
  color: #7b83a5;
  box-shadow: 0 5px 20px rgb(48 60 110 / 5%);
}

.ranking-state-card.error-state {
  color: #d45b72;
}

.ranking-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 238px;
  gap: 16px;
  align-items: start;
}

.podium-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding-top: 14px;
}

.podium-card {
  position: relative;
  display: flex;
  min-height: 175px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px solid #dfe4fa;
  border-radius: 18px;
  background: #f6f7ff;
}

.podium-card.rank-1 {
  min-height: 190px;
  margin-top: -14px;
  border-color: #f5d98c;
  background: #fff9e8;
  box-shadow: 0 10px 24px rgb(239 187 55 / 12%);
}

.podium-card.rank-3 {
  border-color: #f4d4c7;
  background: #fff3ee;
}

.rank-badge {
  position: absolute;
  top: -14px;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 50%;
  background: #9ba7c9;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.rank-1 .rank-badge {
  background: #f5b400;
}

.rank-3 .rank-badge {
  background: #ef925d;
}

.crown {
  position: absolute;
  top: 17px;
  font-size: 19px;
}

.profile-circle {
  display: grid;
  width: 58px;
  height: 58px;
  margin: 11px 0 8px;
  place-items: center;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 6px 16px rgb(31 42 82 / 10%);
}

.profile-circle img {
  width: 38px;
  height: 38px;
}

.podium-nickname {
  margin-bottom: 4px;
  font-size: 13px;
}

.podium-saving {
  color: #151b35;
  font-size: 19px;
}

.rank-1 .podium-saving {
  color: #d89600;
}

.podium-streak {
  margin-top: 5px;
  color: #ef9b62;
  font-size: 11px;
}

.ranking-table-card,
.side-card {
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 5px 20px rgb(48 60 110 / 5%);
}

.ranking-table-card {
  overflow: hidden;
  padding: 8px 16px 10px;
}

.ranking-row {
  display: grid;
  grid-template-columns: 46px minmax(160px, 1fr) 110px 80px 64px 24px;
  align-items: center;
  column-gap: 10px;
}

.ranking-table-header {
  min-height: 34px;
  color: #a4abc5;
  font-size: 11px;
}

.ranking-item {
  min-height: 50px;
  border-top: 1px solid #f1f3fa;
  color: #6d769a;
  font-size: 12px;
}

.ranking-item strong {
  color: #202947;
}

.rank-number {
  color: #7b83a5 !important;
}

.ranking-user {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.ranking-user img {
  width: 28px;
  height: 28px;
  padding: 5px;
  border-radius: 50%;
  background: #f0efff;
}

.heart {
  color: #f39ab5;
  font-size: 17px;
}

.ranking-notice {
  padding: 11px 15px;
  border-radius: 10px;
  background: #fff9e7;
  color: #a48b67;
  font-size: 11px;
}

.ranking-sidebar {
  display: grid;
  gap: 14px;
}

.side-card {
  padding: 18px 16px;
}

.side-card h2 {
  margin-bottom: 14px;
  color: #273153;
  font-size: 13px;
  font-weight: 750;
}

.my-rank-user {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 13px;
  border-bottom: 1px solid #eef0f7;
  font-size: 13px;
}

.my-rank-badge {
  padding: 6px 9px;
  border-radius: 10px;
  background: #eeedff;
  color: #756bf5;
}

.my-rank-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 13px;
  text-align: center;
}

.my-rank-stats div {
  display: grid;
  gap: 5px;
}

.my-rank-stats span {
  color: #a4abc2;
  font-size: 10px;
}

.my-rank-stats strong {
  color: #1e2747;
  font-size: 12px;
}

.record-list li,
.reward-list li {
  display: flex;
  min-height: 38px;
  align-items: center;
  border-top: 1px solid #f0f2f8;
  color: #626c91;
  font-size: 11px;
}

.record-list li:first-child,
.reward-list li:first-child {
  border-top: 0;
}

.record-list strong {
  margin-left: auto;
  color: #1e2747;
}

.reward-list {
  display: grid;
}

.reward-list li {
  display: grid;
  grid-template-columns: 24px 1fr auto;
}

.reward-list li.rewardWithoutMedal {
  grid-template-columns: 1fr auto;
}

.reward-list strong {
  color: #1e2747;
  font-size: 12px;
}

.reward-medal {
  font-size: 14px;
}

@media (max-width: 1100px) {
  .ranking-layout {
    grid-template-columns: 1fr;
  }

  .ranking-sidebar {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 767.98px) {
  .podium-grid,
  .ranking-sidebar {
    grid-template-columns: 1fr;
  }

  .podium-card.rank-1 {
    min-height: 175px;
    margin-top: 0;
  }

  .ranking-table-card {
    overflow-x: auto;
  }

  .ranking-row {
    min-width: 650px;
  }
}
</style>
