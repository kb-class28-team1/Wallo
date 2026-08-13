<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue"
import { storeToRefs } from "pinia"
import { RouterLink, useRouter } from "vue-router"
import {
  generateNextDayMissions,
  getTodayMissions,
} from "@/api/missionApi"
import { useUserStore } from "@/stores/userStore"
import { formatNumber } from "@/commonUtils/formatters"

// public 폴더의 이미지는 루트 절대 경로로 참조함.
const pointWCoin = "/images/profiles/point-w-coin.svg"
const userStore = useUserStore()
const router = useRouter()
const { nickname, profileImageUrl, pointBalance, isLoading } = storeToRefs(userStore)
const missions = ref([])
const missionMenu = ref(null)
const isMissionOpen = ref(false)
const isMissionLoading = ref(false)
const isMissionDevLoading = ref(false)
const missionDevResult = ref(null)
const isDevelopment = import.meta.env.DEV
let missionCloseTimer = null

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
  window.addEventListener("wallo:mission-updated", loadTodayMissions)
})

onBeforeUnmount(() => {
  window.removeEventListener("wallo:mission-updated", loadTodayMissions)
  clearTimeout(missionCloseTimer)
})

const loadTodayMissions = async () => {
  isMissionLoading.value = true
  try {
    const response = await getTodayMissions()
    missions.value = response.missions
  } catch (error) {
    missions.value = []
    alert(error.message || "오늘의 미션을 불러오지 못했습니다.")
  } finally {
    isMissionLoading.value = false
  }
}

const generateNextDay = async () => {
  isMissionDevLoading.value = true
  try {
    const response = await generateNextDayMissions()
    missions.value = response.missions
    missionDevResult.value = {
      mode: `${response.date} 시뮬레이션`,
      count: response.missions.length,
      titles: response.missions.map((mission) => mission.title),
    }
  } catch (error) {
    alert(error.status === 404
      ? "백엔드의 mission.dev-api.enabled 설정을 true로 변경해 주세요."
      : error.message)
  } finally {
    isMissionDevLoading.value = false
  }
}

const toggleMissionMenu = () => {
  clearTimeout(missionCloseTimer)
  isMissionOpen.value = !isMissionOpen.value
}

const openMissionMenu = () => {
  clearTimeout(missionCloseTimer)
  isMissionOpen.value = true
}

const scheduleMissionMenuClose = () => {
  clearTimeout(missionCloseTimer)
  missionCloseTimer = setTimeout(() => {
    isMissionOpen.value = false
  }, 250)
}

const handleMissionFocusOut = (event) => {
  if (missionMenu.value?.contains(event.relatedTarget)) return
  scheduleMissionMenuClose()
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
      <div
        ref="missionMenu"
        class="mission-menu"
        @mouseenter="openMissionMenu"
        @mouseleave="scheduleMissionMenuClose"
        @focusin="openMissionMenu"
        @focusout="handleMissionFocusOut"
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
          <span v-else class="mission-planned-label">오늘 0개</span>
          <i class="bi bi-chevron-down" aria-hidden="true"></i>
        </button>

        <div v-if="isMissionOpen" id="today-mission-popover" class="mission-popover">
          <div class="mission-popover-heading">
            <strong>오늘의 미션</strong>
            <span v-if="missions.length">{{ completedMissionReward }} / {{ totalMissionReward }}P</span>
          </div>

          <div v-if="isMissionLoading" class="mission-loading">미션을 불러오는 중...</div>
          <div v-else-if="!missions.length" class="mission-empty">
            오늘 배정된 미션이 없습니다.
          </div>
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
                <span class="mission-item-description">{{ mission.description }}</span>
                <span class="mission-item-meta">
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

          <div v-if="isDevelopment" class="mission-dev-panel">
            <div class="mission-dev-heading">
              <strong>개발자 검증</strong>
              <span>현재 로그인 사용자</span>
            </div>
            <div>
              <button
                type="button"
                class="btn btn-sm btn-primary w-100"
                :disabled="isMissionDevLoading"
                @click="generateNextDay"
              >
                다음날 미션 생성
              </button>
            </div>
            <div v-if="isMissionDevLoading" class="mission-dev-result">처리 중...</div>
            <div v-else-if="missionDevResult" class="mission-dev-result">
              {{ missionDevResult.mode }} 결과: {{ missionDevResult.count }}개
              <ul v-if="missionDevResult.titles.length" class="mb-0 ps-3">
                <li v-for="title in missionDevResult.titles" :key="title">{{ title }}</li>
              </ul>
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

.mission-item-description {
  display: -webkit-box;
  overflow: hidden;
  color: #7d849d;
  font-size: 10px;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
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

.mission-dev-panel {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #dfe2ef;
}

.mission-dev-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #555d78;
  font-size: 11px;
}

.mission-dev-heading span {
  color: #9ba1b6;
  font-size: 9px;
}

.mission-dev-result {
  margin-top: 8px;
  padding: 7px 9px;
  border-radius: 8px;
  background: #f6f7fb;
  color: #68708b;
  font-size: 10px;
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
