<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue"
import { RouterLink, useRoute, useRouter } from "vue-router"
import { getCurrentChallenge } from "@/api/challengeApi"
import AppDialog from "@/components/common/AppDialog.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import { useUserStore } from "@/stores/userStore"

// public 폴더의 이미지는 루트 절대 경로로 참조함.
const thinkingPenguin = "/images/profiles/thinking-penguin.svg"

const primaryMenus = [
  { icon: "bi bi-house-fill", label: "대시보드", to: "/dashboard" },
  { icon: "bi bi-bar-chart-line", label: "자산관리", to: "/assets" },
  { icon: "bi bi-robot", label: "AI 컨설팅", to: "/ai-consulting" },
]

const utilityMenus = [
  { icon: "bi bi-gift", label: "포인트 샵", to: "/point-shop" },
  { icon: "bi bi-newspaper", label: "금융 리포트", to: "/reports" },
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
const userStore = useUserStore()
// 자산관리 하위 메뉴 열림 여부를 관리함
const isAssetOpen = ref(false)
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
    route.path === "/users/me/challenge-dashboard" ||
    route.path === "/my-feeds",
)
const isAssetRoute = computed(
  () => route.path === "/assets" || route.path.startsWith("/assets/"),
)
const assetGroupClass = computed(() => ({
  "asset-group-active": isAssetRoute.value,
}))
const assetCollapseIconClass = computed(() => ({
  "collapse-icon-open": isAssetOpen.value,
}))
const monthlyReportClass = computed(() => ({
  "submenu-link-active": route.path === "/assets/expenses",
}))
const categoryExpenseClass = computed(() => ({
  "submenu-link-active": route.path === "/assets/categories",
}))
const challengeGroupClass = computed(() => ({
  "challenge-group-active": isChallengeRoute.value,
}))
const collapseIconClass = computed(() => ({
  "collapse-icon-open": isChallengeOpen.value,
}))
const weeklyRankingClass = computed(() => ({
  "submenu-link-active": route.path === "/challenges/rankings/weekly",
}))
const myChallengeClass = computed(() => ({
  "submenu-link-active": route.path === "/users/me/challenge-dashboard",
}))
const myFeedsClass = computed(() => ({
  "submenu-link-active": route.path === "/my-feeds",
}))

// 챌린지 관련 페이지에서는 하위 메뉴를 펼치고, 외부 페이지에서는 닫음
watch(
  isChallengeRoute,
  (isActive) => {
    isChallengeOpen.value = isActive
  },
  { immediate: true },
)

// 자산 관련 페이지에서는 새로고침 후에도 하위 메뉴가 펼쳐짐
watch(
  isAssetRoute,
  (isActive) => {
    isAssetOpen.value = isActive
  },
  { immediate: true },
)

const openAssetMenu = () => {
  isAssetOpen.value = true
}

const toggleAsset = () => {
  isAssetOpen.value = !isAssetOpen.value
}

const toggleChallenge = () => {
  isChallengeOpen.value = !isChallengeOpen.value
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

const moveToMyFeeds = () => {
  moveToChallengeMemberPage("/my-feeds")
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
  <aside class="sidebar d-flex flex-column" aria-label="주요 메뉴">
    <RouterLink
      to="/dashboard"
      class="brand d-flex align-items-center"
      aria-label="Wallo 대시보드로 이동"
    >
      <span class="brand-name">Wallo</span>
    </RouterLink>

    <nav class="sidebar-nav d-flex flex-column">
      <div class="menu-group d-flex flex-column">
        <template v-for="menu in primaryMenus" :key="menu.to">
          <RouterLink
            v-if="menu.to !== '/assets'"
            :to="menu.to"
            class="menu-item menu-link d-flex align-items-center"
          >
            <span class="menu-icon" aria-hidden="true">
              <i :class="menu.icon"></i>
            </span>
            <span>{{ menu.label }}</span>
          </RouterLink>

          <div v-else class="asset-group" :class="assetGroupClass">
            <div class="asset-heading d-flex align-items-center">
              <RouterLink
                to="/assets"
                class="menu-item menu-link asset-title d-flex flex-grow-1 align-items-center"
                @click="openAssetMenu"
              >
                <span class="menu-icon" aria-hidden="true">
                  <i :class="menu.icon"></i>
                </span>
                <span>{{ menu.label }}</span>
              </RouterLink>

              <AppButton
                class="collapse-toggle asset-collapse-toggle d-flex align-items-center justify-content-end"
                variant="ghost"
                size="sm"
                :aria-expanded="isAssetOpen"
                aria-controls="asset-submenu"
                aria-label="자산관리 하위 메뉴 열기 및 닫기"
                @click="toggleAsset"
              >
                <i
                  class="bi bi-chevron-down collapse-icon ms-auto"
                  :class="assetCollapseIconClass"
                  aria-hidden="true"
                ></i>
              </AppButton>
            </div>

            <Transition name="submenu">
              <div v-if="isAssetOpen" id="asset-submenu" class="submenu d-flex flex-column">
                <RouterLink
                  to="/assets/expenses"
                  class="submenu-item submenu-link asset-monthly-report d-flex align-items-center"
                  :class="monthlyReportClass"
                >
                  <span class="submenu-dot" aria-hidden="true"></span>
                  월별 리포트
                </RouterLink>
                <RouterLink
                  to="/assets/categories"
                  class="submenu-item submenu-link d-flex align-items-center"
                  :class="categoryExpenseClass"
                >
                  <span class="submenu-dot" aria-hidden="true"></span>
                  카테고리별 소비
                </RouterLink>
              </div>
            </Transition>
          </div>
        </template>
      </div>

      <div class="challenge-group" :class="challengeGroupClass">
        <div class="challenge-heading d-flex align-items-center">
          <button
            type="button"
            class="menu-item challenge-title d-flex flex-grow-1 align-items-center"
            :disabled="isChallengeChecking"
            @click="moveToChallengeFeed"
          >
            <span class="menu-icon" aria-hidden="true">
              <i class="bi bi-cash-coin"></i>
            </span>
            절약 챌린지
          </button>

          <AppButton
            class="collapse-toggle d-flex align-items-center justify-content-end"
            variant="ghost"
            size="sm"
            :aria-expanded="isChallengeOpen"
            aria-controls="challenge-submenu"
            aria-label="절약 챌린지 하위 메뉴 열기 및 닫기"
            @click="toggleChallenge"
          >
            <i
              class="bi bi-chevron-down collapse-icon ms-auto"
              :class="collapseIconClass"
              aria-hidden="true"
            ></i>
          </AppButton>
        </div>

        <Transition name="submenu">
          <div v-if="isChallengeOpen" id="challenge-submenu" class="submenu d-flex flex-column">
            <AppButton
              class="submenu-item submenu-link d-flex align-items-center"
              variant="ghost"
              size="sm"
              :class="weeklyRankingClass"
              :disabled="isChallengeChecking"
              @click="moveToWeeklyRanking"
            >
              <template #leading><span class="submenu-dot" aria-hidden="true"></span></template>
              주간랭킹
            </AppButton>

            <AppButton
              class="submenu-item submenu-link d-flex align-items-center"
              variant="ghost"
              size="sm"
              :class="myChallengeClass"
              :disabled="isChallengeChecking"
              @click="moveToMyChallenge"
            >
              <template #leading><span class="submenu-dot" aria-hidden="true"></span></template>
              내 챌린지
            </AppButton>

            <AppButton
              class="submenu-item submenu-link d-flex align-items-center"
              variant="ghost"
              size="sm"
              :class="myFeedsClass"
              :disabled="isChallengeChecking"
              @click="moveToMyFeeds"
            >
              <template #leading><span class="submenu-dot" aria-hidden="true"></span></template>
              내 게시물
            </AppButton>
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
          <span class="menu-icon" aria-hidden="true">
            <i :class="menu.icon"></i>
          </span>
          <span>{{ menu.label }}</span>
        </RouterLink>
      </div>
    </nav>

    <div class="sidebar-footer mt-auto">
      <div class="sidebar-footer-divider" aria-hidden="true"></div>

      <RouterLink
        to="/users/profile"
        class="menu-item menu-link d-flex align-items-center"
      >
        <span class="menu-icon" aria-hidden="true"><i class="bi bi-gear"></i></span>
        <span>설정</span>
      </RouterLink>

      <AppButton
        class="sidebar-logout menu-item d-flex align-items-center"
        variant="ghost"
        size="sm"
        aria-label="로그아웃"
        @click="handleLogout"
      >
        <template #leading>
          <span class="menu-icon" aria-hidden="true"><i class="bi bi-box-arrow-right"></i></span>
        </template>
        로그아웃
      </AppButton>

      <AppCard as="div" class="sidebar-card text-center" padding="none">
        <div class="sidebar-card-image-frame">
          <img :src="thinkingPenguin" class="sidebar-card-image" alt="생각하는 왈로 캐릭터" />
        </div>
        <p class="sidebar-card-text mb-0" :title="dailySavingsTip" aria-live="polite">
          {{ dailySavingsTip }}
        </p>
      </AppCard>
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
  flex: 0 0 var(--wallo-sidebar-width);
  width: var(--wallo-sidebar-width);
  height: 100vh;
  padding: 20px 25px 30px;
  overflow-y: auto;
  color: #59647f;
  background: #ffffff;
  border-right: 1px solid #f4f5fa;
}

.brand {
  color: inherit;
  text-decoration: none;
}

.brand-name {
  color: #1e2941;
  font-size: 32px;
  font-weight: 800;
  letter-spacing: -1.5px;
}

.sidebar-nav {
  margin-top: 48px;
  font-size: 17.5px;
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

.sidebar :deep(.app-button--ghost:hover:not(:disabled)) {
  background-color: transparent;
}

.menu-icon {
  display: inline-flex;
  flex: 0 0 40px;
  align-items: center;
  justify-content: flex-start;
  font-size: 22px;
  line-height: 1;
}

.asset-heading {
  min-height: 26px;
}

.asset-title {
  min-height: 26px;
}

.asset-title:hover,
.asset-group-active .asset-title {
  color: #5f50d2;
  font-weight: 700;
}

.asset-title:focus-visible {
  border-radius: 4px;
  outline: 2px solid #7062de;
  outline-offset: 4px;
}

.challenge-group {
  margin-top: 20px;
}

.challenge-title {
  justify-content: flex-start;
  min-height: 26px;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  font: inherit;
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

.collapse-toggle.app-button {
  min-height: 32px;
  padding: 0;
}

.collapse-icon {
  display: inline-block;
  margin-right: 3px;
  color: #8f96ba;
  font-size: 15px;
  font-weight: 700;
  line-height: 1;
  transition: transform 0.2s ease;
}

.collapse-icon-open {
  transform: rotate(180deg);
}

.submenu {
  gap: 8px;
  margin-top: 8px;
  padding-left: 12px;
}

.submenu-item {
  gap: 18px;
  min-height: 28px;
  font-size: 14px;
}

.submenu-item.app-button {
  justify-content: flex-start;
  min-height: 28px;
  padding: 0;
  border: 0;
}

.submenu-item :deep(.app-button__label) {
  display: inline-flex;
  align-items: center;
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
  max-height: 160px;
  margin-top: 8px;
  opacity: 1;
}

.utility-group {
  gap: 20px;
  margin-top: 20px;
}

.sidebar-footer {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-top: 20px;
  font-size: 17.5px;
}

.sidebar-footer-divider {
  width: 100%;
  height: 1px;
  background: #e4e7f0;
}

.sidebar-logout.app-button {
  justify-content: flex-start;
  gap: 0;
  min-height: 26px;
  padding: 0;
  color: inherit;
  font-size: inherit;
  font-weight: 600;
}

.sidebar-logout.app-button:hover:not(:disabled) {
  color: #6b5bd2;
  background: transparent;
}

.sidebar-logout :deep(.menu-icon) {
  color: #e35d6a;
}

.sidebar-card {
  width: 100%;
  padding: 14px 12px 12px;
  border: 1px solid #e4e7f0;
  border-radius: 10px;
  background: #ffffff;
}

.sidebar-card-image-frame {
  width: 109px;
  margin: 0 auto 8px;
}

.sidebar-card-image {
  display: block;
  width: 109px;
  max-width: 100%;
  height: auto;
}

.sidebar-card-text {
  color: #7b849b;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
}

@media (max-width: 767.98px) {
  .sidebar {
    flex-basis: var(--wallo-sidebar-width);
    width: var(--wallo-sidebar-width);
  }
}
</style>
