<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue"
import { RouterLink, useRoute, useRouter } from "vue-router"
import { getCurrentChallenge } from "@/api/challengeApi"
import AppDialog from "@/components/common/AppDialog.vue"

// public 폴더의 이미지는 루트 절대 경로로 참조함.
const brandPenguin = "/images/profiles/penguin-coins.svg"
const thinkingPenguin = "/images/profiles/thinking-penguin.svg"
const brandLogoSource = ref(brandPenguin)

const primaryMenus = [
  { icon: "🏠", label: "대시보드", to: "/dashboard" },
  { icon: "💳", label: "자산", to: "/assets" },
  { icon: "🤖", label: "AI 컨설팅", to: "/ai-consulting" },
]

const utilityMenus = [
  { icon: "🛍️", label: "포인트 샵", to: "/point-shop" },
  { icon: "📇", label: "금융 리포트", to: "/reports" },
  { icon: "⚙️", label: "설정", to: "/users/profile" },
]

const savingsTips = [
  "장보러 가기 전에 사야 할 물건들 적어놓고 가기",
  "당장 쓰지 않는 목돈은 파킹통장에 넣어두기",
  "체크카드 사용하면 소비 통제에 도움됨",
  "안 쓰는 구독 상품은 해제하기",
  "알뜰폰 요금제 사용하기",
  "배달 음식 주문 전에 냉장고 속 재료 확인하기",
  "사고 싶은 물건은 장바구니에 넣고 하루 기다리기",
  "할인한다는 이유로 필요 없는 물건 사지 않기",
  "무료 배송 금액을 맞추려고 불필요한 물건 담지 않기",
  "월급날 저축할 금액 먼저 떼어두기",
  "일주일에 하루는 무지출 데이로 보내기",
  "편의점 가기 전에 물과 간식 챙겨가기",
  "일주일 식단 정한 뒤 장보기",
  "유통기한 짧은 음식부터 먹기",
  "대용량 상품은 단가 비교 후 구매하기",
  "중고 거래 전 새 상품 가격과 배송비 비교하기",
  "옷 사기 전에 비슷한 옷이 있는지 확인하기",
  "사용하지 않는 멀티탭 전원 끄기",
  "외출 전에 에어컨과 난방 전원 확인하기",
  "포인트와 쿠폰 만료일 미리 확인하기",
  "카드 결제 알림 켜두기",
  "가까운 거리는 걸어가거나 자전거 이용하기",
  "소액 결제도 주간 예산 안에서 사용하기",
  "할부 결제 전에 총 결제 금액 확인하기",
  "매달 고정비 목록 점검하기",
  "사용하지 않는 앱 자동결제 해지하기",
  "냉동실에 있는 식재료부터 활용하기",
  "카페 가기 전에 텀블러와 쿠폰 챙기기",
  "하루 지출을 자기 전에 기록하기",
  "비상용품은 필요한 만큼만 구매하기",
  "소비하기 전에 꼭 필요한지 한 번 더 생각하기",
]

const getTodayKey = () => {
  const today = new Date()
  return Math.floor(Date.UTC(today.getFullYear(), today.getMonth(), today.getDate()) / 86400000)
}

const todayKey = ref(getTodayKey())
const dailySavingsTip = computed(() => savingsTips[Math.abs(todayKey.value) % savingsTips.length])
let dailyTipTimer

const scheduleDailyTipRefresh = () => {
  const now = new Date()
  const nextDay = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 1)
  dailyTipTimer = window.setTimeout(
    () => {
      todayKey.value = getTodayKey()
      scheduleDailyTipRefresh()
    },
    Math.max(nextDay.getTime() - now.getTime(), 1000),
  )
}

onMounted(scheduleDailyTipRefresh)
onBeforeUnmount(() => window.clearTimeout(dailyTipTimer))

const route = useRoute()
const router = useRouter()
// 챌린지 하위 메뉴 열림 여부를 관리함
const isChallengeOpen = ref(false)
const isChallengeChecking = ref(false)
const dialogVisible = ref(false)
const dialogMessage = ref("")

// 챌린지 관련 페이지에 접속 중인지 현재 URL로 판단함
const isChallengeRoute = computed(
  () =>
    route.path === "/challenges/current" ||
    route.path.startsWith("/challenges/") ||
    route.path === "/users/me/challenge-dashboard",
)
const challengeGroupClass = computed(() => ({
  "challenge-group-active": isChallengeRoute.value,
}))
const collapseMarkClass = computed(() => ({
  "collapse-mark-open": isChallengeOpen.value,
}))
const weeklyRankingClass = computed(() => ({
  "submenu-link-active": route.path === "/challenges/rankings/weekly",
}))
const challengeFeedClass = computed(() => ({
  "submenu-link-active": route.name === "challenge-feed" || route.path === "/challenges/current",
}))
const myChallengeClass = computed(() => ({
  "submenu-link-active": route.path === "/users/me/challenge-dashboard",
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

const showChallengeDialog = (message) => {
  dialogMessage.value = message
  dialogVisible.value = true
}

const closeChallengeDialog = () => {
  dialogVisible.value = false
}

// 챌린지 참여가 확인된 사용자만 랭킹과 내 챌린지 페이지로 이동함
const moveToChallengeMemberPage = async (targetPath) => {
  if (isChallengeChecking.value) {
    return
  }

  isChallengeChecking.value = true

  try {
    const response = await getCurrentChallenge()

    if (!response?.joined) {
      showChallengeDialog("챌린지 참여가 확인되지 않습니다.")
      return
    }

    await router.push(targetPath)
  } catch (error) {
    showChallengeDialog("챌린지 참여가 확인되지 않습니다.")
  } finally {
    isChallengeChecking.value = false
  }
}

const moveToWeeklyRanking = () => {
  moveToChallengeMemberPage("/challenges/rankings/weekly")
}

const moveToChallengeFeed = async () => {
  if (isChallengeChecking.value) {
    return
  }

  isChallengeChecking.value = true
  try {
    const challenge = await getCurrentChallenge()
    if (!challenge?.joined || !challenge.id) {
      await router.push("/challenges/current")
      return
    }
    await router.push(`/challenges/${challenge.id}/feeds`)
  } catch (error) {
    showChallengeDialog(error.message || "챌린지 정보를 확인하지 못했습니다.")
  } finally {
    isChallengeChecking.value = false
  }
}

const moveToMyChallenge = () => {
  moveToChallengeMemberPage("/users/me/challenge-dashboard")
}
</script>

<template>
  <aside class="sidebar d-flex flex-column" aria-label="주요 메뉴">
    <RouterLink
      to="/dashboard"
      class="brand d-flex align-items-center"
      aria-label="왈로 대시보드로 이동"
    >
      <img :src="brandLogoSource" class="brand-icon" alt="왈로 로고" @error="useDefaultBrandLogo" />
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
          <div v-if="isChallengeOpen" id="challenge-submenu" class="submenu d-flex flex-column">
            <button
              type="button"
              class="submenu-item submenu-link d-flex align-items-center"
              :class="challengeFeedClass"
              :disabled="isChallengeChecking"
              @click="moveToChallengeFeed"
            >
              <span class="submenu-dot" aria-hidden="true"></span>
              <span>피드 목록</span>
            </button>

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
      <img :src="thinkingPenguin" class="sidebar-card-image" alt="생각하는 왈로 캐릭터" />
      <p
        class="sidebar-card-text mb-0"
        :title="`절약 꿀팁 · ${dailySavingsTip}`"
        aria-live="polite"
      >
        {{ dailySavingsTip }}
      </p>
    </div>
  </aside>
  <AppDialog
    :visible="dialogVisible"
    title="챌린지 안내"
    :message="dialogMessage"
    @confirm="closeChallengeDialog"
    @close="closeChallengeDialog"
  />
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
.menu-link.router-link-active,
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
  font-size: 14.6px;
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

.submenu-link-active .submenu-dot {
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
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
}

.sidebar-card-tip-label {
  color: #7062de;
  font-weight: 700;
}

@media (max-width: 767.98px) {
  .sidebar {
    flex-basis: 273px;
    width: 273px;
  }
}
</style>
