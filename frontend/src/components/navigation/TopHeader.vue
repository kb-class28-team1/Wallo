<script setup>
import { computed, onMounted, ref } from "vue"
import { storeToRefs } from "pinia"
import { RouterLink, useRouter } from "vue-router"
import { getTodayMissions } from "@/api/missionApi"
import { useUserStore } from "@/stores/userStore"
import { formatNumber } from "@/utils/formatters"

// public 폴더의 이미지는 루트 절대 경로로 참조함.
const pointWCoin = "/images/profiles/point-w-coin.svg"
const userStore = useUserStore()
const router = useRouter()
const { nickname, profileImageUrl, pointBalance, isLoading } = storeToRefs(userStore)
const missions = ref([])
const isMissionOpen = ref(false)
const isMissionLoading = ref(false)

// 포인트 숫자에 천 단위 구분 기호를 적용함
const formattedPointBalance = computed(() => formatNumber(pointBalance.value))
const displayedNickname = computed(() =>
  isLoading.value && !nickname.value ? "불러오는 중..." : nickname.value || "username",
)

const completedMissionCount = computed(
  () => missions.value.filter((mission) => mission.completed).length,
)
const completedMissionReward = computed(() =>
  missions.value
    .filter((mission) => mission.completed)
    .reduce((total, mission) => total + Number(mission.rewardPoint || 0), 0),
)
const totalMissionReward = computed(() =>
  missions.value.reduce((total, mission) => total + Number(mission.rewardPoint || 0), 0),
)

onMounted(() => {
  userStore.fetchUserProfile()
  loadTodayMissions()
})

const loadTodayMissions = async () => {
  isMissionLoading.value = true
  try {
    const response = await getTodayMissions()
    missions.value = Array.isArray(response?.data) ? response.data : response
  } catch (error) {
    missions.value = []
    alert(error.message || "오늘의 미션을 불러오지 못했습니다.")
  } finally {
    isMissionLoading.value = false
  }
}

const toggleMissionMenu = () => {
  isMissionOpen.value = !isMissionOpen.value
}

const closeMissionMenu = () => {
  isMissionOpen.value = false
}

const handleLogout = async () => {
  try {
    await userStore.logout()
    await router.replace("/login")
  } catch (error) {
    alert(error.message || "로그아웃에 실패했습니다.")
  }
}
</script>

<template>
  <header class="top-header d-flex flex-shrink-0 align-items-center justify-content-center">
    <div class="top-header-content">
      <!-- 미션 테이블이 준비되기 전까지 임시 데이터를 호버 목록으로 표시함 -->
      <div
        class="mission-menu"
        @mouseenter="isMissionOpen = true"
        @mouseleave="closeMissionMenu"
        @focusin="isMissionOpen = true"
        @focusout="closeMissionMenu"
      >
        <button
          type="button"
          class="mission-trigger d-inline-flex align-items-center"
          :aria-expanded="isMissionOpen"
          aria-controls="today-mission-popover"
          @click="toggleMissionMenu"
        >
          <span class="mission-check" aria-hidden="true">✓</span>
          <span>오늘의 미션</span>
          <strong v-if="missions.length">{{ completedMissionCount }}/{{ missions.length }}</strong>
          <span v-else class="mission-planned-label">추후에 추가 예정</span>
          <i class="bi bi-chevron-down" aria-hidden="true"></i>
        </button>

        <div v-if="isMissionOpen" id="today-mission-popover" class="mission-popover">
          <div class="mission-popover-heading">
            <strong>오늘의 미션</strong>
            <span v-if="missions.length">{{ completedMissionReward }} / {{ totalMissionReward }}P</span>
          </div>

          <div v-if="isMissionLoading" class="mission-loading">미션을 불러오는 중...</div>
          <div v-else-if="!missions.length" class="mission-empty">추후에 추가 예정</div>
          <div v-else class="mission-list">
            <div
              v-for="mission in missions"
              :key="mission.id"
              class="mission-item"
              :class="{ completed: mission.completed }"
            >
              <span class="mission-item-icon" aria-hidden="true">{{ mission.icon }}</span>
              <span class="mission-item-content">
                <strong>{{ mission.title }}</strong>
                <span class="mission-item-meta">
                  <em :class="`difficulty-${mission.difficulty}`">{{ mission.difficulty }}</em>
                  <b>● +{{ mission.rewardPoint }}P</b>
                </span>
              </span>
              <span class="mission-item-status" :aria-label="mission.completed ? '완료' : '미완료'">
                <i
                  :class="mission.completed ? 'bi bi-check-circle-fill' : 'bi bi-circle'"
                  aria-hidden="true"
                ></i>
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="user-summary d-flex align-items-center">
      <!-- 프로필 이미지와 이름을 누르면 설정 페이지로 이동함 -->
      <RouterLink
        to="/users/profile"
        class="profile-link d-flex align-items-center"
        aria-label="설정 페이지로 이동"
      >
        <img
          :src="profileImageUrl"
          class="profile-image rounded-circle"
          alt="사용자 프로필"
          @error="userStore.useDefaultProfileImage"
        />

        <span class="user-name">
          {{ displayedNickname }}
        </span>
      </RouterLink>

      <!-- 보유 포인트를 누르면 포인트 샵으로 이동함 -->
      <RouterLink
        to="/point-shop"
        class="point-badge d-inline-flex align-items-center"
        aria-label="포인트 샵으로 이동"
      >
        <img :src="pointWCoin" class="point-icon" alt="" aria-hidden="true" />
        {{ formattedPointBalance }} P
      </RouterLink>

      <button
        type="button"
        class="logout-button"
        aria-label="로그아웃"
        :disabled="isLoading"
        @click="handleLogout"
      >
        <span aria-hidden="true">[→</span>
      </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.top-header {
  position: fixed;
  z-index: 1020;
  top: 0;
  right: 0;
  left: 273px;
  height: 68px;
  min-height: 68px;
  padding: 0 32px;
  background: #f1f2ff;
}

.top-header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  max-width: 1180px;
  padding: 0 16px;
}

.user-summary {
  gap: 14px;
}

.mission-menu {
  position: relative;
}

.mission-trigger {
  gap: 6px;
  min-height: 34px;
  padding: 6px 10px;
  border: 1px solid #d9daf3;
  border-radius: 999px;
  background: #fff;
  color: #6a61dc;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.mission-trigger strong {
  color: #6559db;
}

.mission-planned-label {
  color: #8178dd;
  font-size: 10px;
  font-weight: 700;
}

.mission-trigger > i {
  color: #8c91b1;
  font-size: 11px;
}

.mission-check {
  display: grid;
  width: 16px;
  height: 16px;
  place-items: center;
  border-radius: 4px;
  background: #7565ed;
  color: #fff;
  font-size: 11px;
}

.mission-popover {
  position: absolute;
  z-index: 1030;
  top: calc(100% + 10px);
  right: 0;
  width: 330px;
  padding: 16px;
  border: 1px solid #e3e6f2;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 16px 36px rgb(39 48 79 / 18%);
}

.mission-popover::before {
  position: absolute;
  top: -7px;
  right: 32px;
  width: 13px;
  height: 13px;
  border-top: 1px solid #e3e6f2;
  border-left: 1px solid #e3e6f2;
  background: #fff;
  content: "";
  transform: rotate(45deg);
}

.mission-popover-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #27304f;
  font-size: 14px;
}

.mission-popover-heading span {
  padding: 5px 9px;
  border-radius: 999px;
  background: #f0efff;
  color: #6754e8;
  font-size: 12px;
  font-weight: 700;
}

.mission-list {
  display: grid;
  gap: 8px;
  max-height: 360px;
  overflow-y: auto;
}

.mission-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px;
  border: 1px solid #e8eaf3;
  border-radius: 13px;
  background: #fff;
}

.mission-item.completed {
  background: #fafaff;
  color: #9aa0b5;
}

.mission-item-icon {
  display: grid;
  flex: 0 0 35px;
  width: 35px;
  height: 35px;
  place-items: center;
  border-radius: 11px;
  background: #eef2ff;
  font-size: 18px;
}

.mission-item-content {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 4px;
}

.mission-item-content > strong {
  overflow: hidden;
  color: #414a6a;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.completed .mission-item-content > strong {
  color: #9aa0b5;
  text-decoration: line-through;
}

.mission-item-meta {
  display: flex;
  align-items: center;
  gap: 7px;
}

.mission-item-meta em {
  padding: 3px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
}

.difficulty-쉬움 {
  background: #e5faf0;
  color: #16a978;
}

.difficulty-보통 {
  background: #e9f2ff;
  color: #4a83d6;
}

.difficulty-어려움 {
  background: #fff0f3;
  color: #ed637c;
}

.mission-item-meta b {
  color: #d99900;
  font-size: 10px;
}

.mission-item-status {
  color: #dce0ed;
  font-size: 21px;
}

.completed .mission-item-status {
  color: #6b5ee8;
}

.mission-loading,
.mission-empty {
  padding: 24px 0;
  color: #98a0b8;
  font-size: 12px;
  text-align: center;
}

.profile-link {
  gap: 14px;
  color: inherit;
  text-decoration: none;
}

.profile-image {
  width: 44px;
  height: 44px;
  object-fit: cover;
  background: #ffffff;
}

.user-name {
  max-width: 220px;
  overflow: hidden;
  color: #111111;
  font-size: 22px;
  font-weight: 500;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.point-badge {
  gap: 5px;
  padding: 5px 12px;
  border: 1px solid #c8c2ff;
  border-radius: 999px;
  color: #5648c4;
  background: #eeecff;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  text-decoration: none;
}

.point-icon {
  width: 16px;
  height: 16px;
  object-fit: contain;
}

.point-badge:hover,
.point-badge:focus-visible {
  border-color: #7565ed;
  color: #fff;
  background: #7565ed;
}

.logout-button {
  display: inline-flex;
  padding: 0 0 0 6px;
  border: 0;
  color: #5d62c8;
  background: transparent;
  font-family: inherit;
  font-size: 29px;
  line-height: 1;
  cursor: pointer;
  text-decoration: none;
}

@media (max-width: 991.98px) {
  .mission-menu {
    margin-left: 0;
  }
}

@media (max-width: 767.98px) {
  .top-header {
    padding: 0 14px;
  }

  .top-header-content {
    max-width: none;
    padding: 0;
  }

  .user-summary {
    gap: 9px;
  }

  .mission-trigger {
    padding-right: 8px;
    padding-left: 8px;
  }

  .mission-trigger > span:nth-child(2) {
    display: none;
  }

  .mission-popover {
    right: -70px;
    width: min(330px, calc(100vw - 28px));
  }

  .user-name {
    max-width: 110px;
    font-size: 18px;
  }
}
</style>
