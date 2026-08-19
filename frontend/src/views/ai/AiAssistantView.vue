<script setup>
import { computed, onMounted, ref } from "vue"
import { useRouter } from "vue-router"
import { storeToRefs } from "pinia"
import { useGoalStore } from "@/stores/goalStore"
import { useUserStore } from "@/stores/userStore"
import { formatWon } from "@/utils/formatters"
import {
  getGoalAchievementRate,
  getGoalCurrentAmount,
  getGoalTargetAmount,
} from "@/utils/goalProgress"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppState from "@/components/ui/AppState.vue"

const router = useRouter()
const goalStore = useGoalStore()
const userStore = useUserStore()
const {
  goals,
  initialLoading,
  refreshing,
  error,
  roadmap,
  isRoadmapLoading,
  roadmapError,
  isRoadmapProgressSaving,
} = storeToRefs(goalStore)
const { user } = storeToRefs(userStore)

const walloCharacter = "/images/profiles/thinking-penguin.svg"
const hasGoal = computed(() => goals.value.length > 0)
const currentGoal = computed(() => goals.value[0] ?? null)
const roadmapSlider = ref(null)
const userId = computed(() => user.value?.id ?? null)

const currentAmount = computed(() => getGoalCurrentAmount(currentGoal.value))
const targetAmount = computed(() => getGoalTargetAmount(currentGoal.value))
const achievementRate = computed(() => getGoalAchievementRate(currentGoal.value))

const parseGoalDate = (value) => {
  if (!value) return null
  const parsed = new Date(String(value).length === 10 ? `${value}T00:00:00` : value)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

const formatGoalDate = (value) => {
  const parsed = parseGoalDate(value)
  if (!parsed) return "날짜 미정"
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(parsed)
}

const remainingMonths = computed(() => {
  const targetDate = parseGoalDate(currentGoal.value?.targetDate)
  if (!targetDate) return 0
  const today = new Date()
  const months =
    (targetDate.getFullYear() - today.getFullYear()) * 12 + targetDate.getMonth() - today.getMonth()
  return Math.max(0, months)
})

const goalRoadmapSteps = computed(() => {
  const steps = roadmap.value?.roadmap?.steps
  if (!Array.isArray(steps)) return []
  const completedSteps = new Set(roadmap.value?.completedStepNumbers ?? [])
  const currentStepNumber = Number(roadmap.value?.currentStepNumber) || 1
  return steps.map((step, index) => ({
    number: step.stepNumber ?? index + 1,
    icon: index === steps.length - 1 ? "bi-flag" : "bi-clipboard-check",
    title: step.title,
    date: formatGoalDate(step.targetDate),
    description: step.description,
    actionItems: step.actionItems ?? [],
    completed: completedSteps.has(step.stepNumber ?? index + 1),
    active:
      !completedSteps.has(step.stepNumber ?? index + 1) &&
      (step.stepNumber ?? index + 1) === currentStepNumber,
  }))
})

const toggleRoadmapStep = async (step) => {
  if (!currentGoal.value?.goalId || isRoadmapProgressSaving.value) return
  await goalStore.saveRoadmapStep(currentGoal.value.goalId, step.number, !step.completed)
}

const scrollRoadmap = (direction) => {
  const slider = roadmapSlider.value
  if (!slider) return
  const distance = Math.max(280, slider.clientWidth * 0.8)
  slider.scrollBy({ left: direction * distance, behavior: "smooth" })
}

const coachingMessage = computed(() => {
  if (achievementRate.value >= 100) return "목표를 달성했어요! 새로운 목표를 준비해볼까요?"
  if (achievementRate.value >= 50) return "절반 이상 달성했어요! 지금의 저축 흐름을 유지해보세요."
  return `매월 ${formatWon(currentGoal.value?.requiredMonthlyAmount)}씩 모으면 목표에 가까워져요!`
})

const roadmapSteps = [
  {
    number: 1,
    icon: "bi-bullseye",
    title: "목표 설정",
    description: "달성하고 싶은 금융 목표를 설정해보세요!",
  },
  {
    number: 2,
    icon: "bi-bar-chart-line",
    title: "상황 분석",
    description: "AI가 당신의 재무 상황을 분석해요.",
  },
  {
    number: 3,
    icon: "bi-clipboard-check",
    title: "맞춤 로드맵",
    description: "목표 달성을 위한 단계별 계획을 받아보세요.",
  },
  {
    number: 4,
    icon: "bi-flag",
    title: "실천 & 관리",
    description: "로드맵을 따라 실천하고 진행 상황을 관리해요.",
  },
]

const benefits = [
  {
    icon: "bi-map",
    title: "나에게 맞는 로드맵",
    description: "목표와 상황에 맞춘 단계별 계획을 제공해요.",
  },
  {
    icon: "bi-pie-chart",
    title: "소비 & 저축 가이드",
    description: "줄여야 할 소비와 늘려야 할 저축을 알려드려요.",
  },
  {
    icon: "bi-gift",
    title: "추천 금융 상품",
    description: "목표 달성에 도움이 되는 상품을 추천해드려요.",
  },
]

const loadGoalPage = async ({ force = false } = {}) => {
  error.value = null
  await goalStore.fetchGoals({
    userId: userId.value,
    notifyError: false,
    force,
  })
}

const startGoalSetting = async () => {
  await router.push({
    name: "chat",
    query: { start: "goal-setting" },
  })
}

const startAiChat = async () => {
  await router.push({ name: "chat" })
}

onMounted(() => loadGoalPage({ force: true }))
</script>

<template>
  <section class="assistant-page">
    <AppPageHeader
      class="assistant-header"
      title="AI 컨설팅"
    />

    <AppAlert
      v-if="refreshing"
      class="assistant-refresh-status"
      variant="neutral"
      role="status"
      :show-icon="false"
      message="최신 목표 정보를 확인하는 중입니다."
    />

    <AppAlert
      v-if="error && goals.length"
      class="assistant-refresh-error"
      variant="warning"
      :show-icon="false"
    >
      <div class="assistant-error-content">
        <span>{{ error }}</span>
        <AppButton variant="outline" size="sm" @click="loadGoalPage({ force: true })">
          다시 시도
        </AppButton>
      </div>
    </AppAlert>

    <AppState
      v-if="initialLoading"
      class="state-card"
      type="loading"
      title="목표 정보를 불러오는 중입니다."
      message="잠시만 기다려 주세요."
    />

    <AppState
      v-else-if="error && !goals.length"
      class="state-card"
      type="error"
      title="목표 정보를 불러오지 못했습니다."
      :message="error"
      action-text="다시 시도"
      @action="loadGoalPage({ force: true })"
    />

    <div v-else-if="!hasGoal" class="empty-dashboard">
      <div class="row g-4 align-items-stretch">
        <div class="col-xl-8">
          <AppCard as="article" class="content-card" padding="none">
            <div class="card-body p-4 p-lg-5">
              <h2 class="section-title h5 fw-bold">
                나의 목표
                <i class="bi bi-info-circle ms-1 text-secondary" aria-hidden="true"></i>
              </h2>

              <div class="goal-empty-box mt-4">
                <div class="character-wrap" aria-hidden="true">
                  <span class="question-mark">?</span>
                  <img :src="walloCharacter" alt="" />
                </div>
                <div class="goal-empty-copy">
                  <h3 class="h4 fw-bold mb-2">아직 목표가 설정되지 않았어요!</h3>
                  <p class="mb-0 text-secondary">
                    목표를 설정하면 AI가 당신만의 로드맵을 만들어 드릴게요.
                  </p>
                </div>
                <AppButton class="goal-button" variant="primary" @click="startGoalSetting">
                  목표 설정하기
                  <i class="bi bi-arrow-right ms-2" aria-hidden="true"></i>
                </AppButton>
              </div>
            </div>
          </AppCard>
        </div>

        <div class="col-xl-4">
          <AppCard as="aside" class="content-card coaching-card" padding="none">
            <div class="card-body p-4 p-lg-5">
              <h2 class="section-title h5 fw-bold">AI 한줄 코칭</h2>
              <div class="coach-bubble mt-4">
                목표가 있어야 방향이 생겨요!<br />
                작은 목표부터 함께 시작해봐요.
              </div>
              <div class="coach-character" aria-hidden="true">
                <span class="sparkle sparkle-one">✦</span>
                <span class="sparkle sparkle-two">✦</span>
                <img :src="walloCharacter" alt="" />
              </div>
            </div>
          </AppCard>
        </div>
      </div>

      <AppCard as="article" class="content-card mt-4" padding="none">
        <div class="card-body p-4 p-lg-5">
          <h2 class="section-title h5 fw-bold">
            목표 달성을 위한 로드맵
            <i class="bi bi-info-circle ms-1 text-secondary" aria-hidden="true"></i>
          </h2>

          <ol class="roadmap-list list-unstyled mt-4 mb-0">
            <li
              v-for="(step, index) in roadmapSteps"
              :key="step.number"
              class="roadmap-item"
              :class="{ active: index === 0 }"
            >
              <span class="step-number">{{ step.number }}</span>
              <span class="step-icon"
                ><i class="bi" :class="step.icon" aria-hidden="true"></i
              ></span>
              <span class="step-copy">
                <strong>{{ step.title }}</strong>
                <small>{{ step.description }}</small>
              </span>
              <i
                v-if="index < roadmapSteps.length - 1"
                class="bi bi-arrow-right roadmap-arrow"
                aria-hidden="true"
              ></i>
            </li>
          </ol>
        </div>
      </AppCard>

      <AppCard as="article" class="content-card mt-4" padding="none">
        <div class="card-body p-4 p-lg-5">
          <h2 class="section-title h5 fw-bold">목표를 설정하면 얻을 수 있어요</h2>
          <div class="row g-3 mt-2">
            <div v-for="benefit in benefits" :key="benefit.title" class="col-lg-4">
              <div class="benefit-card h-100">
                <i class="bi benefit-icon" :class="benefit.icon" aria-hidden="true"></i>
                <div>
                  <h3 class="h6 fw-bold mb-2">{{ benefit.title }}</h3>
                  <p class="mb-0 text-secondary">{{ benefit.description }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </AppCard>
    </div>

    <div v-else class="goal-dashboard">
      <div class="row g-4 align-items-stretch">
        <div class="col-xl-8">
          <AppCard as="article" class="content-card" padding="none">
            <div class="card-body p-4 p-lg-5">
              <div class="goal-card-heading d-flex align-items-start justify-content-between gap-3">
                <h2 class="section-title h5 fw-bold">나의 목표</h2>
                <div class="goal-heading-actions d-flex align-items-center gap-2">
                  <span class="goal-status-badge">진행 중</span>
                  <AppButton
                    class="goal-chat-button"
                    variant="outline"
                    size="sm"
                    @click="startAiChat"
                  >
                    <i class="bi bi-chat-dots me-1" aria-hidden="true"></i>
                    AI와 상담하기
                    <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
                  </AppButton>
                </div>
              </div>

              <div class="goal-summary mt-4">
                <div class="goal-summary-icon" aria-hidden="true">
                  <i class="bi bi-bullseye"></i>
                </div>
                <div class="goal-summary-copy">
                  <p class="goal-type mb-1">{{ currentGoal.goalType || "금융 목표" }}</p>
                  <h3 class="h4 fw-bold mb-2">{{ currentGoal.title }}</h3>
                  <p class="mb-0 text-secondary">
                    {{ formatGoalDate(currentGoal.targetDate) }}까지
                    <strong class="text-dark">{{ formatWon(currentGoal.targetAmount) }}</strong>
                  </p>
                </div>
              </div>

              <div class="goal-progress-panel mt-4">
                <div class="d-flex align-items-end justify-content-between gap-3">
                  <div>
                    <span class="small text-secondary">현재 모은 금액</span>
                    <p class="goal-current-amount mb-0">
                      {{ formatWon(currentAmount) }}
                      <small>/ {{ formatWon(targetAmount) }}</small>
                    </p>
                  </div>
                  <strong class="goal-rate">{{ achievementRate }}%</strong>
                </div>
                <div
                  class="progress goal-progress mt-3"
                  role="progressbar"
                  :aria-valuenow="achievementRate"
                  aria-valuemin="0"
                  aria-valuemax="100"
                >
                  <div class="progress-bar" :style="{ width: `${achievementRate}%` }"></div>
                </div>
                <div class="goal-meta row g-3 mt-2">
                  <div class="col-sm-6">
                    <span>월 필요 저축액</span>
                    <strong>{{ formatWon(currentGoal.requiredMonthlyAmount) }}</strong>
                  </div>
                  <div class="col-sm-6">
                    <span>남은 기간</span>
                    <strong>{{ remainingMonths }}개월</strong>
                  </div>
                </div>
              </div>
            </div>
          </AppCard>
        </div>

        <div class="col-xl-4">
          <AppCard as="aside" class="content-card coaching-card" padding="none">
            <div class="card-body p-4 p-lg-5">
              <h2 class="section-title h5 fw-bold">AI 한줄 코칭</h2>
              <div class="coach-bubble mt-4">{{ coachingMessage }}</div>
              <div class="coach-character" aria-hidden="true">
                <span class="sparkle sparkle-one">✦</span>
                <span class="sparkle sparkle-two">✦</span>
                <img :src="walloCharacter" alt="" />
              </div>
            </div>
          </AppCard>
        </div>
      </div>

      <AppCard as="article" class="content-card goal-roadmap-card mt-4" padding="none">
        <div class="card-body p-4 p-lg-5">
          <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
            <h2 class="section-title h5 fw-bold">목표 달성을 위한 로드맵</h2>
            <div
              v-if="goalRoadmapSteps.length > 1"
              class="roadmap-navigation"
              aria-label="로드맵 이동"
            >
              <AppButton
                class="roadmap-navigation-button"
                variant="ghost"
                size="sm"
                aria-label="이전 로드맵 단계 보기"
                @click="scrollRoadmap(-1)"
              >
                <i class="bi bi-chevron-left" aria-hidden="true"></i>
              </AppButton>
              <AppButton
                class="roadmap-navigation-button"
                variant="ghost"
                size="sm"
                aria-label="다음 로드맵 단계 보기"
                @click="scrollRoadmap(1)"
              >
                <i class="bi bi-chevron-right" aria-hidden="true"></i>
              </AppButton>
            </div>
          </div>

          <AppState
            v-if="isRoadmapLoading"
            class="roadmap-state mt-4"
            type="loading"
            compact
            title="저장된 로드맵을 불러오는 중입니다."
            message="잠시만 기다려 주세요."
          />
          <AppAlert
            v-else-if="roadmapError"
            class="roadmap-alert mt-4"
            variant="danger"
            :message="roadmapError"
          />
          <AppAlert
            v-else-if="roadmap?.generationStatus === 'FAILED'"
            class="roadmap-alert mt-4"
            variant="warning"
            message="AI 로드맵 생성에 실패했습니다. 목표는 정상적으로 저장되어 있습니다."
          />
          <ol
            v-else-if="goalRoadmapSteps.length"
            ref="roadmapSlider"
            class="roadmap-list goal-roadmap-list list-unstyled mt-4 mb-0"
            aria-label="목표 달성 로드맵 단계"
          >
            <li
              v-for="(step, index) in goalRoadmapSteps"
              :key="step.number"
              class="roadmap-item"
              :class="{ active: step.active, completed: step.completed }"
            >
              <span class="step-number">
                <i v-if="step.completed" class="bi bi-check-lg" aria-hidden="true"></i>
                <template v-else>{{ step.number }}</template>
              </span>
              <span class="step-icon"
                ><i class="bi" :class="step.icon" aria-hidden="true"></i
              ></span>
              <span class="step-copy">
                <small class="step-date">{{ step.date }}</small>
                <strong>{{ step.title }}</strong>
                <small>{{ step.description }}</small>
                <small v-for="action in step.actionItems" :key="action" class="roadmap-action">
                  · {{ action }}
                </small>
                <AppButton
                  class="roadmap-progress-button mt-2"
                  :variant="step.completed ? 'secondary' : 'outline'"
                  size="sm"
                  :disabled="isRoadmapProgressSaving"
                  @click="toggleRoadmapStep(step)"
                >
                  <i
                    class="bi me-1"
                    :class="step.completed ? 'bi-arrow-counterclockwise' : 'bi-check-circle'"
                  ></i>
                  {{ step.completed ? "완료 취소" : "이 단계까지 완료" }}
                </AppButton>
              </span>
              <i
                v-if="index < goalRoadmapSteps.length - 1"
                class="bi bi-arrow-right roadmap-arrow"
                aria-hidden="true"
              ></i>
            </li>
          </ol>
          <AppState
            v-else
            class="roadmap-state mt-4"
            type="empty"
            compact
            title="아직 생성된 로드맵이 없습니다."
            message="목표를 저장하면 AI가 맞춤 로드맵을 준비합니다."
          />
        </div>
      </AppCard>

      <AppCard as="article" class="content-card mt-4" padding="none">
        <div class="card-body p-4 p-lg-5">
          <h2 class="section-title h5 fw-bold">이번 달 실천 가이드</h2>
          <div class="row g-3 mt-2">
            <div class="col-lg-4">
              <div class="action-card h-100">
                <span class="action-icon"><i class="bi bi-arrow-repeat"></i></span>
                <div>
                  <h3 class="h6 fw-bold mb-2">자동 저축 설정</h3>
                  <p class="mb-0 text-secondary">
                    급여일에 {{ formatWon(currentGoal.requiredMonthlyAmount) }}이 자동으로
                    이체되도록 설정해보세요.
                  </p>
                </div>
              </div>
            </div>
            <div class="col-lg-4">
              <div class="action-card h-100">
                <span class="action-icon"><i class="bi bi-calendar-check"></i></span>
                <div>
                  <h3 class="h6 fw-bold mb-2">월말 진행 점검</h3>
                  <p class="mb-0 text-secondary">
                    월말에 목표 계좌 잔액과 계획 대비 달성률을 확인해보세요.
                  </p>
                </div>
              </div>
            </div>
            <div class="col-lg-4">
              <div class="action-card h-100">
                <span class="action-icon"><i class="bi bi-shield-check"></i></span>
                <div>
                  <h3 class="h6 fw-bold mb-2">계획 유지하기</h3>
                  <p class="mb-0 text-secondary">
                    부족한 달은 다음 달 납입액을 조정해 목표 일정이 밀리지 않게 관리해요.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </AppCard>
    </div>
  </section>
</template>

<style scoped>
.assistant-page {
  padding-bottom: 2rem;
  color: #1c2440;
}

.assistant-header :deep(.app-page-header__title) {
  font-size: clamp(1.75rem, 3vw, 2.35rem);
  letter-spacing: -0.045em;
}

.assistant-header :deep(.app-page-header__description) {
  font-size: 1.05rem;
}

.assistant-refresh-status,
.assistant-refresh-error {
  margin-bottom: var(--wallo-space-3);
}

.assistant-error-content {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--wallo-space-2);
}

.content-card,
.state-card {
  overflow: hidden;
  border-radius: 24px;
  background: #fff;
}

.state-card {
  min-height: 300px;
}

.section-title {
  margin: 0;
  letter-spacing: -0.025em;
}

.section-title .bi {
  font-size: 0.85rem;
}

.goal-empty-box {
  display: flex;
  min-height: 230px;
  align-items: center;
  gap: 2rem;
  padding: 2rem 2.25rem;
  border: 2px dashed #c9c5ff;
  border-radius: 20px;
  background: linear-gradient(135deg, #fff 0%, #f7f6ff 100%);
}

.character-wrap {
  position: relative;
  flex: 0 0 150px;
  text-align: center;
}

.character-wrap img,
.coach-character img {
  width: 135px;
  max-height: 145px;
  object-fit: contain;
}

.question-mark {
  position: absolute;
  top: -24px;
  right: 4px;
  color: #8a7df2;
  font-size: 3.6rem;
  font-weight: 800;
}

.goal-empty-copy {
  min-width: 0;
  flex: 1;
  line-height: 1.75;
}

.goal-button {
  flex: 0 0 auto;
  padding: 0.85rem 1.5rem;
  border: 0;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(135deg, #7769f5, #6453e8);
  box-shadow: 0 10px 24px rgb(100 83 232 / 22%);
  font-weight: 700;
}

.goal-button:hover,
.goal-button:focus {
  color: #fff;
  background: linear-gradient(135deg, #695ce6, #5644d8);
}

.coaching-card {
  height: 100%;
}

.coaching-card :deep(.app-card__body),
.coaching-card .card-body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.coach-bubble {
  position: relative;
  width: fit-content;
  max-width: 100%;
  padding: 1.25rem 1.5rem;
  border-radius: 22px 22px 8px 22px;
  background: #f1f0ff;
  color: #424862;
  font-weight: 600;
  line-height: 1.7;
}

.coach-character {
  position: relative;
  align-self: flex-end;
  flex: 0 0 auto;
  margin-top: auto;
}

.sparkle {
  position: absolute;
  color: #8b7cf4;
  font-size: 1.5rem;
}

.sparkle-one {
  top: 12px;
  left: -22px;
}

.sparkle-two {
  top: 52px;
  left: -43px;
  font-size: 1rem;
}

.roadmap-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 3.25rem;
}

.goal-roadmap-list {
  display: flex;
  gap: 2.25rem;
  overflow-x: auto;
  padding: 0.85rem 0.5rem 1.25rem;
  scroll-behavior: smooth;
  scroll-padding-inline: 0.5rem;
  scroll-snap-type: x mandatory;
  scrollbar-color: #c8c3fb #f1f0fa;
  scrollbar-width: thin;
}

.goal-roadmap-list .roadmap-item {
  min-width: min(360px, calc(100vw - 5rem));
  flex: 0 0 min(360px, calc(100vw - 5rem));
  scroll-snap-align: start;
}

.goal-roadmap-list .roadmap-arrow {
  right: -1.8rem;
  font-size: 1.35rem;
}

.goal-roadmap-card {
  position: relative;
}

.goal-roadmap-card .card-body {
  position: relative;
}

.roadmap-navigation {
  position: absolute;
  z-index: 2;
  inset: 0;
  display: block;
  pointer-events: none;
}

.roadmap-navigation-button {
  position: absolute;
  top: 50%;
  display: inline-flex;
  width: 40px;
  height: 52px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: var(--wallo-radius-md);
  color: #6555df;
  background: transparent;
  pointer-events: auto;
  transform: translateY(-50%);
}

.roadmap-navigation-button :deep(.bi) {
  font-size: 1.75rem;
  font-weight: 700;
  -webkit-text-stroke: 0.35px currentColor;
}

.roadmap-navigation-button:hover,
.roadmap-navigation-button:focus-visible {
  color: #7567e9;
  background: rgb(112 98 222 / 8%);
}

.roadmap-navigation-button:first-child {
  left: 0.75rem;
}

.roadmap-navigation-button:last-child {
  right: 0.75rem;
}

.roadmap-item {
  position: relative;
  display: flex;
  min-height: 145px;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem;
  border: 1px solid #e6e8f0;
  border-radius: 18px;
  background: #fafbfe;
}

.roadmap-item.active {
  border: 2px solid #7a6df0;
  background: #fbfaff;
}

.step-number {
  position: absolute;
  top: -12px;
  left: -8px;
  display: inline-flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #34384c;
  background: #d9dce5;
  font-weight: 800;
}

.active .step-number {
  color: #fff;
  background: #7162eb;
}

.step-icon {
  display: inline-flex;
  width: 58px;
  height: 58px;
  flex: 0 0 58px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #85899b;
  background: #eff0f4;
  font-size: 1.75rem;
}

.active .step-icon {
  color: #6b5ce8;
  background: #ebe9ff;
}

.step-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.45rem;
}

.step-copy small {
  color: #73798d;
  line-height: 1.55;
}

.roadmap-arrow {
  position: absolute;
  top: 50%;
  right: -2.3rem;
  color: #9ca0ad;
  font-size: 1.6rem;
  transform: translateY(-50%);
}

.benefit-card {
  display: flex;
  min-height: 130px;
  align-items: center;
  gap: 1.25rem;
  padding: 1.5rem;
  border: 1px solid #e8e8f4;
  border-radius: 18px;
  background: linear-gradient(135deg, #fff, #faf9ff);
}

.benefit-icon {
  flex: 0 0 auto;
  color: #7162eb;
  font-size: 3rem;
}

.benefit-card p {
  font-size: 0.92rem;
  line-height: 1.65;
}

.goal-status-badge {
  padding: 0.4rem 0.75rem;
  border-radius: 999px;
  color: #6555df;
  background: #eeecff;
  font-size: 0.8rem;
  font-weight: 700;
}

.goal-chat-button {
  padding: 0.55rem 0.9rem;
  border: 1px solid #7567e9;
  border-radius: 12px;
  color: #6555df;
  background: #fff;
  font-size: 0.85rem;
  font-weight: 700;
}

.goal-chat-button:hover,
.goal-chat-button:focus {
  border-color: #6555df;
  color: #fff;
  background: #6555df;
}

.goal-summary {
  display: flex;
  align-items: center;
  gap: 1.25rem;
}

.goal-summary-icon {
  display: inline-flex;
  width: 72px;
  height: 72px;
  flex: 0 0 72px;
  align-items: center;
  justify-content: center;
  border-radius: 22px;
  color: #6d5dea;
  background: #eeecff;
  font-size: 2.1rem;
}

.goal-type {
  color: #7568e7;
  font-size: 0.8rem;
  font-weight: 700;
}

.goal-progress-panel {
  padding: 1.4rem 1.5rem;
  border-radius: 18px;
  background: #f8f8fe;
}

.goal-current-amount {
  color: #5f50d8;
  font-size: 1.55rem;
  font-weight: 800;
}

.goal-current-amount small {
  color: #7f8495;
  font-size: 0.9rem;
  font-weight: 500;
}

.goal-rate {
  color: #6455df;
  font-size: 1.1rem;
}

.goal-progress {
  height: 0.7rem;
  border-radius: 999px;
  background: #e6e4fb;
}

.goal-progress .progress-bar {
  border-radius: inherit;
  background: linear-gradient(90deg, #7567ee, #5e4fde);
}

.goal-meta > div {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  color: #777d90;
  font-size: 0.88rem;
}

.goal-meta strong {
  color: #282d40;
}

.roadmap-item.completed:not(.active) {
  border-color: #c8c3fb;
  background: #faf9ff;
}

.roadmap-item.completed .step-number {
  color: #fff;
  background: #8275ec;
}

.step-date {
  color: #7162e4 !important;
  font-weight: 700;
}

.roadmap-state {
  padding: 1.5rem;
  border-radius: 16px;
  background: #f8f8fe;
  text-align: center;
}

.roadmap-action {
  color: #555d73 !important;
}

.roadmap-progress-button {
  align-self: flex-start;
  border-radius: 10px;
  font-weight: 700;
}

.action-card {
  display: flex;
  min-height: 130px;
  align-items: flex-start;
  gap: 1rem;
  padding: 1.4rem;
  border: 1px solid #e8e8f4;
  border-radius: 18px;
  background: #fcfcff;
}

.action-icon {
  display: inline-flex;
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  color: #6d5dea;
  background: #eeecff;
  font-size: 1.35rem;
}

.action-card p {
  font-size: 0.9rem;
  line-height: 1.65;
}

@media (max-width: 1199.98px) {
  .coaching-card {
    min-height: 310px;
  }

  .roadmap-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .goal-roadmap-list {
    display: flex;
  }

  .roadmap-item:nth-child(2) .roadmap-arrow {
    display: none;
  }

  .goal-roadmap-list .roadmap-item:nth-child(2) .roadmap-arrow {
    display: block;
  }
}

@media (max-width: 767.98px) {
  .assistant-header :deep(.app-page-header__description) {
    font-size: 0.95rem;
  }

  .goal-empty-box {
    flex-direction: column;
    padding: 1.75rem;
    text-align: center;
  }

  .character-wrap {
    flex-basis: auto;
  }

  .goal-button {
    width: 100%;
  }

  .goal-card-heading {
    flex-direction: column;
  }

  .goal-heading-actions {
    width: 100%;
    justify-content: space-between;
  }

  .roadmap-list {
    grid-template-columns: 1fr;
    gap: 1.5rem;
  }

  .goal-roadmap-list {
    display: flex;
    gap: 1rem;
  }

  .roadmap-arrow {
    display: none;
  }

  .goal-roadmap-list .roadmap-item:nth-child(2) .roadmap-arrow {
    display: none;
  }
}
</style>
