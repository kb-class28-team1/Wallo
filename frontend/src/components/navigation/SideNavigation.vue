<script setup>
import { ref } from 'vue'
import brandPenguin from '@/assets/penguin-coins.svg'
import thinkingPenguin from '@/assets/thinking-penguin.svg'

const primaryMenus = [
  { icon: '🏠', label: '대시보드' },
  { icon: '💳', label: '자산' },
  { icon: '🤖', label: 'AI 컨설팅' },
]

const challengeMenus = ['피드 목록', '랭킹 · 리워드', '내 챌린지']

const utilityMenus = [
  { icon: '🛍️', label: '포인트 샵' },
  { icon: '📇', label: '금융 리포트' },
  { icon: '⚙️', label: '설정' },
]

const isChallengeOpen = ref(false)

const toggleChallenge = () => {
  isChallengeOpen.value = !isChallengeOpen.value
}
</script>

<template>
  <aside class="sidebar d-flex flex-column" aria-label="주요 메뉴">
    <div class="brand d-flex align-items-center">
      <img :src="brandPenguin" class="brand-icon" alt="왈로 로고" />
      <span class="brand-name">왈로</span>
    </div>

    <nav class="sidebar-nav d-flex flex-column">
      <div class="menu-group d-flex flex-column">
        <div v-for="menu in primaryMenus" :key="menu.label" class="menu-item d-flex align-items-center">
          <span class="menu-icon" aria-hidden="true">{{ menu.icon }}</span>
          <span>{{ menu.label }}</span>
        </div>
      </div>

      <div class="challenge-group">
        <button
          type="button"
          class="menu-item challenge-title d-flex w-100 align-items-center"
          :aria-expanded="isChallengeOpen"
          aria-controls="challenge-submenu"
          @click="toggleChallenge"
        >
          <span class="menu-icon" aria-hidden="true">💰</span>
          <span>절약 챌린지</span>
          <span class="collapse-mark ms-auto" aria-hidden="true">
            {{ isChallengeOpen ? '⌃' : '⌄' }}
          </span>
        </button>

        <div
          v-if="isChallengeOpen"
          id="challenge-submenu"
          class="submenu d-flex flex-column"
        >
          <div v-for="menu in challengeMenus" :key="menu" class="submenu-item d-flex align-items-center">
            <span class="submenu-dot" aria-hidden="true"></span>
            <span>{{ menu }}</span>
          </div>
        </div>
      </div>

      <div class="utility-group d-flex flex-column">
        <div v-for="menu in utilityMenus" :key="menu.label" class="menu-item d-flex align-items-center">
          <span class="menu-icon" aria-hidden="true">{{ menu.icon }}</span>
          <span>{{ menu.label }}</span>
        </div>
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

.challenge-title:focus-visible {
  border-radius: 4px;
  outline: 2px solid #7062de;
  outline-offset: 4px;
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
