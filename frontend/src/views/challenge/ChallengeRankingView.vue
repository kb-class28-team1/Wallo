<script setup>
import { computed, onMounted, ref } from "vue"
import { storeToRefs } from "pinia"
import { grantWeeklyRankingRewardsForTest } from "@/api/challengeApi"
import AuthenticatedImage from "@/components/common/AuthenticatedImage.vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppState from "@/components/ui/AppState.vue"
import { useChallengeStore } from "@/stores/challengeStore"
import { formatNumber, formatWon } from "@/utils/formatters"
import { useUserStore } from "@/stores/userStore"
import { announcePointEarned } from "@/utils/pointRewardNotice"

const DEFAULT_PROFILE_IMAGE = "/images/profiles/default-profile.svg"
const challengeStore = useChallengeStore()
const userStore = useUserStore()
const isRewarding = ref(false)

// Pinia의 반응형 상태를 유지한 채 화면에서 사용할 값으로 분리함
const { startDate, endDate, rankings, myRanking, initialLoading, refreshing, errorMessage } =
  storeToRefs(challengeStore)

const formatRankingDate = (date) => String(date || "").replaceAll("-", ".")

const rankingPeriodLabel = computed(() => {
  if (!startDate.value || !endDate.value) {
    return ""
  }

  return `${formatRankingDate(startDate.value)} ~ ${formatRankingDate(endDate.value)}`
})

// 인원수와 상관없이 시상대 슬롯을 2위, 1위, 3위 위치로 고정함
const podiumSlots = computed(() =>
  [2, 1, 3].map((rank) => ({
    rank,
    ranking: rankings.value.find((item) => item.rank === rank) || null,
  })),
)

// 상위 3명을 제외한 4위 이후 DB 조회 결과를 숫자 순위로 모두 표시함
const remainingRankings = computed(() => rankings.value.filter((ranking) => ranking.rank >= 4))

// 화면의 랭킹 보상 안내에 표시할 포인트 기준임
const rankingRewards = computed(() => [
  { medal: "🏆", label: "1등", point: 2000 },
  { medal: "🥈", label: "2등", point: 1000 },
  { medal: "🥉", label: "3등", point: 800 },
  { medal: "", label: "4~10등", point: 500 },
])

const formatPoint = (point) => `${formatNumber(point)}P`

// DB 프로필 주소가 없거나 이미지 로드에 실패하면 기본 프로필을 표시함
const profileImage = (url) => url || DEFAULT_PROFILE_IMAGE

// 테스트 버튼에서 현재 주 랭킹 보상 지급 API를 호출함
const grantRewardsForTest = async () => {
  if (isRewarding.value) {
    return
  }

  isRewarding.value = true
  try {
    const response = await grantWeeklyRankingRewardsForTest()
    const rewardedPoint = Number(response?.rewardedPoint || 0)
    if (rewardedPoint > 0) {
      // 실제 포인트가 적립된 경우에는 전역 공통 적립 알림으로 안내함.
      announcePointEarned(rewardedPoint)
    } else {
      alert(
        response?.rewardedCount > 0
          ? "이번 주 랭킹 보상이 지급되었습니다."
          : "참여자가 2명 미만이라 주간 랭킹 보상을 지급할 수 없습니다.",
      )
    }
    // 지급 후 세션의 사용자 포인트를 강제로 다시 조회해 상단바를 갱신함.
    await userStore.restoreSession(true)
    await challengeStore.fetchWeeklyRanking({ force: true })
  } catch (error) {
    alert(error.message || "주간 랭킹 보상을 지급하지 못했습니다.")
  } finally {
    isRewarding.value = false
  }
}

// 페이지에 진입하면 API를 호출하여 V_WEEKLY_RANKING 조회 결과를 가져옴
onMounted(() => {
  challengeStore.fetchWeeklyRanking()
})
</script>

<template>
  <section class="ranking-page">
    <AppPageHeader
      class="ranking-heading"
      title="주간 랭킹"
      :description="rankingPeriodLabel"
      compact
    >
      <template #actions>
        <AppButton
          class="test-reward-button"
          variant="outline"
          size="sm"
          :disabled="isRewarding"
          :loading="isRewarding"
          @click="grantRewardsForTest"
        >
          {{ isRewarding ? "지급 중..." : "테스트 보상 지급" }}
        </AppButton>
      </template>
    </AppPageHeader>

    <AppAlert
      v-if="refreshing"
      class="ranking-refresh-status"
      variant="neutral"
      role="status"
      :show-icon="false"
      message="최신 주간 랭킹을 확인하는 중..."
    />

    <AppAlert
      v-if="errorMessage && rankings.length > 0"
      class="ranking-error-alert"
      variant="warning"
    >
      <div class="ranking-alert-content">
        <span>{{ errorMessage }}</span>
        <AppButton
          variant="outline"
          size="sm"
          @click="challengeStore.fetchWeeklyRanking({ force: true })"
        >
          다시 시도
        </AppButton>
      </div>
    </AppAlert>

    <AppState
      v-if="initialLoading"
      class="ranking-state-card"
      type="loading"
      title="주간 랭킹을 불러오는 중입니다"
      message="잠시만 기다려 주세요."
    />

    <AppState
      v-else-if="errorMessage && rankings.length === 0"
      class="ranking-state-card"
      type="error"
      title="주간 랭킹을 불러오지 못했습니다"
      :message="errorMessage"
      action-text="다시 시도"
      action-variant="danger"
      @action="challengeStore.fetchWeeklyRanking({ force: true })"
    />

    <AppState
      v-else-if="rankings.length === 0"
      class="ranking-state-card"
      type="empty"
      title="이번 주 랭킹 데이터가 아직 없습니다"
      message="랭킹이 집계되면 이곳에서 확인할 수 있어요."
    />

    <div v-else class="ranking-layout">
      <div class="ranking-main">
        <div class="podium-grid mb-3 podium-count-3">
          <article
            v-for="slot in podiumSlots"
            :key="slot.rank"
            class="podium-card"
            :class="[`rank-${slot.rank}`, { 'podium-card-placeholder': !slot.ranking }]"
          >
            <template v-if="slot.ranking">
              <span class="rank-badge">{{ slot.rank }}</span>
              <span v-if="slot.rank === 1" class="trophy" aria-hidden="true">🏆</span>
              <div class="profile-circle">
                <AuthenticatedImage
                  :src="profileImage(slot.ranking.profileImageUrl)"
                  :alt="`${slot.ranking.nickname} 프로필 이미지`"
                />
              </div>
              <strong class="podium-nickname">{{ slot.ranking.nickname }}</strong>
              <strong class="podium-saving">{{ formatWon(slot.ranking.savingAmount) }}</strong>
              <span class="podium-streak">🔥 {{ slot.ranking.streakDays }}일 연속</span>
            </template>
          </article>
        </div>

        <AppCard as="div" class="ranking-table-card" padding="none">
          <div class="ranking-table-header ranking-row">
            <span>순위</span>
            <span>닉네임</span>
            <span>절약 금액</span>
            <span>연속 인증</span>
            <span>좋아요</span>
          </div>

          <div
            v-for="ranking in remainingRankings"
            :key="ranking.rank"
            class="ranking-row ranking-item"
          >
            <strong class="rank-number">{{ ranking.rank }}</strong>
            <div class="ranking-user">
              <AuthenticatedImage
                :src="profileImage(ranking.profileImageUrl)"
                :alt="`${ranking.nickname} 프로필 이미지`"
              />
              <span>{{ ranking.nickname }}</span>
            </div>
            <strong>{{ formatWon(ranking.savingAmount) }}</strong>
            <span>{{ ranking.streakDays }}일</span>
            <span>{{ ranking.likeCount }}</span>
          </div>
        </AppCard>

        <div class="ranking-notice mt-3">🔥 연속 인증은 오늘 인증까지 포함된 연속 인증 일수임!</div>
      </div>

      <aside class="ranking-sidebar">
        <AppCard v-if="myRanking" as="article" class="side-card my-rank-card" padding="none">
          <h2>내 순위</h2>
          <div class="my-rank-user">
            <div class="ranking-user">
              <AuthenticatedImage
                :src="profileImage(myRanking.profileImageUrl)"
                alt="내 프로필 이미지"
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
        </AppCard>

        <AppCard v-if="myRanking" as="article" class="side-card" padding="none">
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
        </AppCard>

        <AppCard as="article" class="side-card" padding="none">
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
        </AppCard>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.ranking-page {
  width: 100%;
  color: #1f2a52;
}

.ranking-heading :deep(.app-page-header__description) {
  color: #8e98ba;
  font-size: 12px;
}

.test-reward-button {
  border: 1px solid #c9def7;
  border-radius: 9px;
  padding: 8px 12px;
  background: #eef7ff;
  color: #4c80cf;
  font-size: 12px;
  font-weight: 700;
}

.test-reward-button:disabled {
  cursor: wait;
  opacity: 0.6;
}

.ranking-refresh-status,
.ranking-error-alert {
  margin-bottom: 16px;
}

.ranking-alert-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ranking-state-card {
  display: grid;
  min-height: 240px;
  place-items: center;
  border-radius: 18px;
  background: #fff;
  color: #7b83a5;
  box-shadow: 0 5px 20px rgb(52 106 162 / 5%);
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
  position: relative;
  display: grid;
  align-items: end;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  isolation: isolate;
  padding: 24px 10px 24px;
}

.podium-grid::before {
  position: absolute;
  z-index: -1;
  right: 2%;
  bottom: 5px;
  left: 2%;
  height: 30px;
  content: "";
  background: #b5906b;
  border: 3px solid #3a3638;
  border-radius: 5px 3px 8px 4px;
  box-shadow: 3px 3px 0 rgb(58 54 56 / 15%);
  clip-path: polygon(
    0 13%,
    18% 3%,
    38% 10%,
    58% 0,
    80% 8%,
    100% 3%,
    99% 93%,
    65% 100%,
    38% 95%,
    1% 89%
  );
  transform: rotate(-0.8deg) skewX(-0.7deg);
}

.podium-grid.podium-count-1 {
  width: 33.333%;
  margin-inline: auto;
  grid-template-columns: minmax(0, 1fr);
}

.podium-grid.podium-count-2 {
  width: 66.666%;
  margin-inline: auto;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.podium-card {
  position: relative;
  display: flex;
  min-height: 174px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 3px solid #3a3638;
  border-radius: 6px 4px 5px 3px;
  background: #b5906b;
  box-shadow: 3px 4px 0 rgb(58 54 56 / 15%);
  clip-path: polygon(
    1% 3%,
    21% 1%,
    44% 3%,
    67% 0%,
    99% 2%,
    98% 31%,
    100% 64%,
    97% 99%,
    76% 97%,
    52% 100%,
    26% 98%,
    3% 100%,
    1% 70%,
    0% 38%
  );
  filter: drop-shadow(1px 0 #3a3638) drop-shadow(-1px 0 #3a3638) drop-shadow(0 1px #3a3638)
    drop-shadow(0 -1px #3a3638);
  transform: rotate(-1deg) skewX(-0.8deg);
}

.podium-card.rank-1 {
  min-height: 220px;
  background: #c19b67;
  clip-path: polygon(
    0% 2%,
    23% 0%,
    48% 2%,
    74% 0%,
    100% 3%,
    98% 36%,
    100% 97%,
    77% 99%,
    52% 97%,
    29% 100%,
    2% 97%,
    1% 64%
  );
  transform: rotate(0.45deg) skewX(0.55deg);
}

.podium-card.rank-2 {
  min-height: 195px;
  background: #b99a78;
  clip-path: polygon(
    2% 0%,
    31% 2%,
    58% 0%,
    100% 4%,
    98% 34%,
    100% 96%,
    67% 99%,
    42% 97%,
    17% 100%,
    1% 96%,
    3% 59%
  );
  transform: rotate(-1.25deg) skewX(-0.85deg);
}

.podium-card.rank-3 {
  min-height: 174px;
  background: #ae8c6b;
  clip-path: polygon(
    1% 4%,
    25% 0%,
    53% 3%,
    78% 1%,
    100% 4%,
    99% 61%,
    97% 98%,
    74% 96%,
    48% 100%,
    22% 97%,
    0% 100%,
    2% 48%
  );
  transform: rotate(1.05deg) skewX(0.7deg);
}

.rank-badge {
  position: absolute;
  top: -14px;
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border: 3px solid #3a3638;
  border-radius: 48% 52% 45% 55%;
  background: #86da82;
  color: #2e2a31;
  font-size: 17px;
  font-weight: 900;
  box-shadow: 2px 2px 0 rgb(58 54 56 / 20%);
  transform: rotate(-3deg);
}

.rank-1 .rank-badge {
  background: #f7c83b;
  transform: rotate(2deg);
}

.rank-3 .rank-badge {
  background: #7fc5f2;
  transform: rotate(4deg);
}

.trophy {
  position: absolute;
  top: 28px;
  font-size: 25px;
  line-height: 1;
  filter: drop-shadow(2px 2px 0 rgb(58 54 56 / 20%));
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
  width: 42px;
  height: 42px;
  aspect-ratio: 1;
  border-radius: 50%;
  object-fit: cover;
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
  color: #2c2430;
}

.rank-1 .podium-nickname {
  color: #3f3331;
  font-weight: 800;
}

.podium-streak {
  margin-top: 5px;
  color: #c9362f;
  font-size: 11px;
  font-weight: 800;
}

.ranking-table-card,
.side-card {
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 5px 20px rgb(52 106 162 / 5%);
}

.ranking-table-card {
  overflow: hidden;
  padding: 8px 16px 10px;
}

.ranking-row {
  display: grid;
  grid-template-columns: 46px minmax(160px, 1fr) 110px 80px 64px;
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
  background: #eaf4ff;
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
  color: #6c9fe7;
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
  .test-reward-button {
    align-self: flex-start;
  }

  .ranking-alert-content {
    align-items: flex-start;
    flex-direction: column;
  }

  .podium-grid,
  .ranking-sidebar {
    grid-template-columns: 1fr;
  }

  .podium-grid.podium-count-1,
  .podium-grid.podium-count-2 {
    width: 100%;
  }

  .podium-card.rank-1 {
    min-height: 200px;
  }

  .podium-card.rank-2 {
    min-height: 185px;
  }

  .podium-card.rank-3 {
    min-height: 165px;
  }

  .ranking-table-card {
    overflow-x: auto;
  }

  .ranking-row {
    min-width: 650px;
  }
}

/* Dashboard-style visual treatment for the weekly ranking. */
.ranking-page {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.ranking-heading {
  box-sizing: border-box;
  padding: 23px 26px;
  border: 1px solid #d8e8f8;
  border-radius: 23px;
  background: rgb(255 255 255 / 84%);
  box-shadow: 0 12px 28px rgb(76 132 190 / 8%);
}

.ranking-heading :deep(.app-page-header__description) {
  color: #7188a5;
}

.test-reward-button {
  color: #397bd2;
  background: #edf6ff;
  border-color: #c9e0fb;
}

.ranking-layout {
  grid-template-columns: minmax(0, 1fr) 290px;
  gap: 20px;
}

.podium-grid {
  padding: 34px 16px 26px;
  border: 1px solid #d6e7f7;
  border-radius: 23px;
  background: rgb(255 255 255 / 73%);
  box-shadow: 0 12px 28px rgb(76 132 190 / 8%);
}

.ranking-table-card,
.side-card {
  border: 1px solid #d7e7f7;
  background: #fff;
  box-shadow: 0 12px 26px rgb(64 105 151 / 9%);
}

.ranking-table-card {
  padding: 10px 18px 12px;
}

.ranking-table-header {
  color: #7c92ad;
}

.ranking-item {
  border-top-color: #edf3f8;
  color: #7188a2;
}

.ranking-item strong,
.ranking-item strong.rank-number {
  color: #213957;
}

.ranking-user img {
  background: #edf6ff;
}

.ranking-notice {
  color: #58718e;
  background: #eaf5ff;
}

.side-card {
  padding: 20px 18px;
  border-radius: 20px;
}

.side-card h2 {
  color: #203654;
}

.my-rank-user {
  border-bottom-color: #eaf1f7;
}

.my-rank-badge {
  color: #397bd2;
  background: #eaf4ff;
}

.my-rank-stats span,
.record-list li,
.reward-list li {
  color: #7b90aa;
}

.my-rank-stats strong,
.record-list strong,
.reward-list strong {
  color: #213957;
}

.record-list li,
.reward-list li {
  border-top-color: #edf3f8;
}

@media (max-width: 1100px) {
  .ranking-page {
    padding: 0;
  }
}

@media (max-width: 767.98px) {
  .ranking-page {
    padding: 0;
    border-radius: 0;
  }

  .ranking-heading {
    padding: 18px;
    border-radius: 18px;
  }
}

/* Replace the paper podium with the shared light-blue dashboard theme. */
.podium-grid::before {
  display: none;
}

.podium-card,
.podium-card.rank-1,
.podium-card.rank-2,
.podium-card.rank-3 {
  border: 1px solid #c9e0f7;
  border-radius: 22px;
  background: linear-gradient(180deg, #e4f1ff 0%, #ffffff 100%);
  box-shadow: 0 12px 24px rgb(71 124 181 / 12%);
  clip-path: none;
  filter: none;
  transform: none;
}

.podium-card.rank-1 {
  background: linear-gradient(180deg, #d8e8ff 0%, #ffffff 100%);
  border-color: #86b4ed;
  box-shadow: 0 16px 30px rgb(71 124 181 / 18%);
}

.podium-card.rank-2 {
  background: linear-gradient(180deg, #dff7f5 0%, #ffffff 100%);
  border-color: #8bd4d0;
}

.podium-card.rank-3 {
  background: linear-gradient(180deg, #edf6ff 0%, #ffffff 100%);
  border-color: #9ebfea;
}

.podium-card-placeholder {
  visibility: hidden;
  border-color: transparent;
  background: transparent;
  box-shadow: none;
}

.rank-badge,
.rank-1 .rank-badge,
.rank-3 .rank-badge {
  border: 0;
  color: #1f426a;
  box-shadow: 0 5px 12px rgb(71 124 181 / 18%);
  transform: none;
}

.rank-badge {
  background: #a9d2fa;
}

.rank-1 .rank-badge {
  background: #4f8ee8;
  color: #fff;
}

.rank-2 .rank-badge {
  background: #56bdb8;
  color: #fff;
}

.rank-3 .rank-badge {
  background: #73a3e7;
  color: #fff;
}

.trophy {
  filter: drop-shadow(0 4px 6px rgb(71 124 181 / 20%));
}

.podium-card.rank-1 {
  --ranking-accent: #1d4d83;
}

.podium-card.rank-2 {
  --ranking-accent: #247a77;
}

.podium-card.rank-3 {
  --ranking-accent: #4f83c7;
}

.podium-card .podium-nickname,
.podium-card .podium-saving {
  color: var(--ranking-accent);
}

.podium-streak {
  color: #d8784e;
}
</style>
