<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { getCurrentChallenge } from '@/api/challengeApi'
import brandPenguin from '@/assets/penguin-coins.svg'
import thinkingPenguin from '@/assets/thinking-penguin.svg'

const primaryMenus = [
  { icon: '🏠', label: '대시보드', to: '/api/home' },
  { icon: '💳', label: '자산', to: '/api/institutions' },
  { icon: '🤖', label: 'AI 컨설팅', to: '/api/dashboard' },
]

const utilityMenus = [
  { icon: '🛍️', label: '포인트 샵', to: '/api/point-shop' },
  { icon: '📇', label: '금융 리포트', to: '/api/reports' },
  { icon: '⚙️', label: '설정', to: '/api/users/profile' },
]

const route = useRoute()
const router = useRouter()
// 챌린지 하위 메뉴 열림 여부와 피드 조회 상태를 관리함
const isChallengeOpen = ref(false)
const isFeedLoading = ref(false)

// 챌린지 관련 페이지에 접속 중인지 현재 URL로 판단함
const isChallengeRoute = computed(
  () =>
    route.path === '/api/challenges/current' ||
    route.path.startsWith('/api/challenges/') ||
    route.path === '/api/users/me/challenge-dashboard',
)

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

const openChallenge = () => {
  isChallengeOpen.value = true
}

// 현재 챌린지 ID를 조회한 뒤 해당 챌린지 피드로 이동함
const openChallengeFeed = async () => {
  // 연속 클릭으로 동일 요청이 중복 실행되는 것을 방지함
  if (isFeedLoading.value) {
    return
  }

  isFeedLoading.value = true

  try {
    const response = await getCurrentChallenge()
    const challenge = response?.data?.challenge

    // 참여 중인 챌린지가 없으면 챌린지 메인 화면으로 이동됨
    if (!response?.data?.hasChallenge || !challenge?.challengeId) {
      alert('현재 참여 중인 챌린지가 없습니다.')
      await router.push('/api/challenges/current')
      return
    }

    // 조회된 ID가 URL에 안전하게 포함된 피드 경로로 이동함
    await router.push(`/api/challenges/${encodeURIComponent(challenge.challengeId)}/feeds`)
  } catch (error) {
    // API 통신 실패 사유가 사용자에게 alert로 표시됨
    alert(error.message || '피드 목록으로 이동하지 못했습니다.')
  } finally {
    // 성공 및 실패 여부와 관계없이 로딩 상태가 해제됨
    isFeedLoading.value = false
  }
}
</script>

<template>
  <aside class="sidebar d-flex flex-column" aria-label="주요 메뉴">
    <RouterLink
      to="/api/home"
      class="brand d-flex align-items-center"
      aria-label="왈로 대시보드로 이동"
    >
      <img :src="brandPenguin" class="brand-icon" alt="왈로 로고" />
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

      <div class="challenge-group" :class="{ 'challenge-group-active': isChallengeRoute }">
        <div class="challenge-heading d-flex align-items-center">
          <RouterLink
            to="/api/challenges/current"
            class="menu-item challenge-title d-flex flex-grow-1 align-items-center"
            @click="openChallenge"
          >
            <span class="menu-icon" aria-hidden="true">💰</span>
            <span>절약 챌린지</span>
          </RouterLink>

          <button
            type="button"
            class="collapse-toggle d-flex align-items-center justify-content-end"
            :aria-expanded="isChallengeOpen"
            aria-controls="challenge-submenu"
            aria-label="절약 챌린지 하위 메뉴 열기 및 닫기"
            @click="toggleChallenge"
          >
            <span class="collapse-mark ms-auto" aria-hidden="true">
              {{ isChallengeOpen ? '⌃' : '⌄' }}
            </span>
          </button>
        </div>

        <Transition name="submenu">
          <div
            v-if="isChallengeOpen"
            id="challenge-submenu"
            class="submenu d-flex flex-column"
          >
            <button
              type="button"
              class="submenu-item submenu-link d-flex align-items-center"
              :disabled="isFeedLoading"
              @click="openChallengeFeed"
            >
              <span class="submenu-dot" aria-hidden="true"></span>
              <span>{{ isFeedLoading ? '피드 확인 중...' : '피드 목록' }}</span>
            </button>

            <RouterLink
              to="/api/challenges/rankings/weekly"
              class="submenu-item submenu-link d-flex align-items-center"
            >
              <span class="submenu-dot" aria-hidden="true"></span>
              <span>주간랭킹</span>
            </RouterLink>

            <RouterLink
              to="/api/users/me/challenge-dashboard"
              class="submenu-item submenu-link d-flex align-items-center"
            >
              <span class="submenu-dot" aria-hidden="true"></span>
              <span>내 챌린지</span>
            </RouterLink>
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
  flex: 0 0 273px;
  width: 273px;
  min-height: 100vh;
  padding: 20px 25px 30px;
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
  color: #7062de;
  background: transparent;
  font-family: inherit;
  font-size: inherit;
  font-weight: 700;
  letter-spacing: inherit;
  text-align: left;
  cursor: pointer;
}

.challenge-title:hover,
.challenge-group-active .challenge-title {
  color: #5f50d2;
}

.challenge-title:focus-visible,
.collapse-toggle:focus-visible {
  border-radius: 4px;
  outline: 2px solid #7062de;
  outline-offset: 4px;
}

.collapse-toggle {
  width: 24px;
  min-height: 26px;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
}

.collapse-mark {
  position: relative;
  top: 2px;
  color: #aab0cb;
  font-size: 18px;
  line-height: 1;
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
.submenu-link.router-link-exact-active {
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
