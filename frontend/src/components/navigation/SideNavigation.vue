<script setup>
import { computed, ref, watch } from "vue"
import { RouterLink, useRoute, useRouter } from "vue-router"
import { getCurrentChallenge } from "@/api/challengeApi"
import brandPenguin from "@/assets/penguin-coins.svg"
import thinkingPenguin from "@/assets/thinking-penguin.svg"

const brandLogoSource = ref(brandPenguin)

const primaryMenus = [
  { icon: "🏠", label: "대시보드", to: "/api/home" },
  { icon: "💳", label: "자산", to: "/api/institutions" },
  { icon: "🤖", label: "AI 컨설팅", to: "/api/dashboard" },
]

const utilityMenus = [
  { icon: "🛍️", label: "포인트 샵", to: "/api/point-shop" },
  { icon: "📇", label: "금융 리포트", to: "/api/reports" },
  { icon: "⚙️", label: "설정", to: "/api/users/profile" },
]

const route = useRoute()
const router = useRouter()
// 챌린지 하위 메뉴 열림 여부를 관리함
const isChallengeOpen = ref(false)
const isChallengeChecking = ref(false)

// 챌린지 관련 페이지에 접속 중인지 현재 URL로 판단함
const isChallengeRoute = computed(
  () =>
    route.path === "/api/challenges/current" ||
    route.path.startsWith("/api/challenges/") ||
    route.path === "/api/users/me/challenge-dashboard",
)
const challengeGroupClass = computed(() => ({
  "challenge-group-active": isChallengeRoute.value,
}))
const collapseMarkClass = computed(() => ({
  "collapse-mark-open": isChallengeOpen.value,
}))
const weeklyRankingClass = computed(() => ({
  "submenu-link-active": route.path === "/api/challenges/rankings/weekly",
}))
const myChallengeClass = computed(() => ({
  "submenu-link-active": route.path === "/api/users/me/challenge-dashboard",
}))

// 챌린지 관련 페이지에서는 새로고침 후에도 하위 메뉴가 펼쳐짐
watch(
  isChallengeRoute,
  (isActive) => {
    if (isActive) {
      isChallengeOpen.value = true
    }
  },
  { immediate: true },
)

const toggleChallenge = () => {
  isChallengeOpen.value = !isChallengeOpen.value
}

// 로고 이미지 로드 실패 시 기존 캐릭터 이미지를 기본 이미지로 사용함
const useDefaultBrandLogo = () => {
  brandLogoSource.value = thinkingPenguin
}

// 챌린지 참여가 확인된 사용자만 랭킹과 내 챌린지 페이지로 이동함
const moveToChallengeMemberPage = async (targetPath) => {
  if (isChallengeChecking.value) {
    return
  }

  isChallengeChecking.value = true

  try {
    const response = await getCurrentChallenge()

    if (!response?.data?.hasChallenge) {
      alert("챌린지 참여가 확인되지 않습니다.")
      return
    }

    await router.push(targetPath)
  } catch (error) {
    alert("챌린지 참여가 확인되지 않습니다.")
  } finally {
    isChallengeChecking.value = false
  }
}

const moveToWeeklyRanking = () => {
  moveToChallengeMemberPage("/api/challenges/rankings/weekly")
}

const moveToMyChallenge = () => {
  moveToChallengeMemberPage("/api/users/me/challenge-dashboard")
}
</script>

<template>
  <aside class="sidebar d-flex flex-column" aria-label="주요 메뉴">
    <RouterLink
      to="/api/home"
      class="brand d-flex align-items-center"
      aria-label="왈로 대시보드로 이동"
    >
      <img
        :src="brandLogoSource"
        class="brand-icon"
        alt="왈로 로고"
        @error="useDefaultBrandLogo"
      />
      <span class="brand-name">왈로</span>
    </RouterLink>

    <nav class="sidebar-nav d-flex flex-column">
      <div class="menu-group d-flex flex-column">
        <RouterLink
          v-for="menu in primaryMenus"
          :key="menu.to"
          :to="menu.to"
          class="menu-item menu-link d-flex align-items-center"
        >
          <span class="menu-icon" aria-hidden="true">{{ menu.icon }}</span>
          <span>{{ menu.label }}</span>
        </RouterLink>
      </div>

      <div class="challenge-group" :class="challengeGroupClass">
        <div class="challenge-heading d-flex align-items-center">
          <button
            type="button"
            class="menu-item challenge-title d-flex flex-grow-1 align-items-center"
            :aria-expanded="isChallengeOpen"
            aria-controls="challenge-submenu"
            @click="toggleChallenge"
          >
            <span class="menu-icon" aria-hidden="true">💰</span>
            <span>절약 챌린지</span>
          </button>

          <button
            type="button"
            class="collapse-toggle d-flex align-items-center justify-content-end"
            :aria-expanded="isChallengeOpen"
            aria-controls="challenge-submenu"
            aria-label="절약 챌린지 하위 메뉴 열기 및 닫기"
            @click="toggleChallenge"
          >
            <span
              class="collapse-mark ms-auto"
              :class="collapseMarkClass"
              aria-hidden="true"
            ></span>
          </button>
        </div>

        <Transition name="submenu">
          <div
            v-if="isChallengeOpen"
            id="challenge-submenu"
            class="submenu d-flex flex-column"
          >
            <RouterLink
              to="/api/challenges/current"
              class="submenu-item submenu-link d-flex align-items-center"
            >
              <span class="submenu-dot" aria-hidden="true"></span>
              <span>피드 목록</span>
            </RouterLink>

            <button
              type="button"
              class="submenu-item submenu-link d-flex align-items-center"
              :class="weeklyRankingClass"
              :disabled="isChallengeChecking"
              @click="moveToWeeklyRanking"
            >
              <span class="submenu-dot" aria-hidden="true"></span>
              <span>주간랭킹</span>
            </button>

            <button
              type="button"
              class="submenu-item submenu-link d-flex align-items-center"
              :class="myChallengeClass"
              :disabled="isChallengeChecking"
              @click="moveToMyChallenge"
            >
              <span class="submenu-dot" aria-hidden="true"></span>
              <span>내 챌린지</span>
            </button>
          </div>
        </Transition>
      </div>

      <div class="utility-group d-flex flex-column">
        <RouterLink
          v-for="menu in utilityMenus"
          :key="menu.to"
          :to="menu.to"
          class="menu-item menu-link d-flex align-items-center"
        >
          <span class="menu-icon" aria-hidden="true">{{ menu.icon }}</span>
          <span>{{ menu.label }}</span>
        </RouterLink>
      </div>
    </nav>

    <div class="sidebar-card mt-auto text-center">
      <img
        :src="thinkingPenguin"
        class="sidebar-card-image"
        alt="생각하는 왈로 캐릭터"
      />
      <p class="sidebar-card-text mb-0">뭔가 넣을 공간</p>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  flex: 0 0 273px;
  width: 273px;
  height: 100vh;
  padding: 20px 25px 30px;
  overflow-y: auto;
  color: #59647f;
  background: #ffffff;
  border-right: 1px solid #f4f5fa;
}

.brand {
  gap: 19px;
  color: inherit;
  text-decoration: none;
}

.brand-icon {
  display: block;
  width: 37px;
  height: 40px;
  object-fit: contain;
}

.brand-name {
  color: #1e2941;
  font-size: 29px;
  font-weight: 800;
  letter-spacing: -1.5px;
}

.sidebar-nav {
  margin-top: 48px;
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -0.6px;
}

.menu-group {
  gap: 20px;
}

.menu-item {
  min-height: 26px;
  font-weight: 600;
  line-height: 1.3;
  white-space: nowrap;
}

.menu-link {
  width: 100%;
  color: inherit;
  text-decoration: none;
  transition: color 0.2s ease;
}

.menu-link:hover,
.menu-link.router-link-exact-active {
  color: #7062de;
}

.menu-icon {
  display: inline-flex;
  flex: 0 0 40px;
  align-items: center;
  justify-content: flex-start;
  font-size: 22px;
  line-height: 1;
}

.challenge-group {
  margin-top: 20px;
}

.challenge-title {
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  font-family: inherit;
  font-size: inherit;
  font-weight: 600;
  letter-spacing: inherit;
  text-align: left;
  cursor: pointer;
}

.challenge-title:hover,
.challenge-group-active .challenge-title {
  color: #5f50d2;
  font-weight: 700;
}

.challenge-title:focus-visible,
.collapse-toggle:focus-visible {
  border-radius: 4px;
  outline: 2px solid #7062de;
  outline-offset: 4px;
}

.collapse-toggle {
  width: 32px;
  min-height: 32px;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
}

.collapse-mark {
  width: 11px;
  height: 11px;
  margin-right: 3px;
  border-right: 2px solid #8f96ba;
  border-bottom: 2px solid #8f96ba;
  transform: rotate(45deg) translate(-2px, -2px);
  transition: transform 0.2s ease;
}

.collapse-mark-open {
  transform: rotate(225deg) translate(-2px, -2px);
}

.submenu {
  gap: 8px;
  margin-top: 8px;
  padding-left: 12px;
}

.submenu-item {
  gap: 18px;
  min-height: 28px;
  font-size: 13.3px;
}

.submenu-link {
  width: 100%;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  font-family: inherit;
  font-weight: inherit;
  letter-spacing: inherit;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition: color 0.2s ease;
}

.submenu-link:hover,
.submenu-link.router-link-exact-active,
.submenu-link-active {
  color: #7062de;
}

.submenu-link:disabled {
  opacity: 0.65;
  cursor: wait;
}

.submenu-dot {
  width: 5px;
  height: 5px;
  flex: 0 0 5px;
  border-radius: 50%;
  background: #dfe2f3;
}

.submenu-item:last-child .submenu-dot {
  background: #737991;
}

.submenu-enter-active,
.submenu-leave-active {
  overflow: hidden;
  transition:
    max-height 0.28s ease,
    margin-top 0.28s ease,
    opacity 0.2s ease;
}

.submenu-enter-from,
.submenu-leave-to {
  max-height: 0;
  margin-top: 0;
  opacity: 0;
}

.submenu-enter-to,
.submenu-leave-from {
  max-height: 110px;
  margin-top: 8px;
  opacity: 1;
}

.utility-group {
  gap: 20px;
  margin-top: 20px;
}

.sidebar-card {
  width: 100%;
  padding: 14px 12px 12px;
  border: 1px solid #e4e7f0;
  border-radius: 10px;
  background: #ffffff;
}

.sidebar-card-image {
  display: block;
  width: 128px;
  max-width: 100%;
  height: auto;
  margin: 0 auto 8px;
}

.sidebar-card-text {
  color: #7b849b;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
}

@media (max-width: 767.98px) {
  .sidebar {
    flex-basis: 273px;
    width: 273px;
  }
}
</style>
