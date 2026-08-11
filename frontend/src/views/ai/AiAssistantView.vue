<script setup>
import { computed, onMounted } from "vue"
import { useRouter } from "vue-router"
import { storeToRefs } from "pinia"
import { useGoalStore } from "@/stores/goalStore"
import { formatWon } from "@/commonUtils/formatters"

const router = useRouter()
const goalStore = useGoalStore()
const { goals, isLoading, error } = storeToRefs(goalStore)

const walloCharacter = "/images/profiles/thinking-penguin.svg"
const hasGoal = computed(() => goals.value.length > 0)
const currentGoal = computed(() => goals.value[0] ?? null)

const currentAmount = computed(() => {
  const amount = Number(currentGoal.value?.currentAmount)
  return Number.isFinite(amount) ? amount : Number(currentGoal.value?.initialAmount) || 0
})

const targetAmount = computed(() => Number(currentGoal.value?.targetAmount) || 0)
const achievementRate = computed(() => {
  const serverRate = Number(currentGoal.value?.achievementRate)
  if (Number.isFinite(serverRate)) {
    return Math.min(100, Math.max(0, Math.round(serverRate)))
  }
  if (targetAmount.value <= 0) return 0
  return Math.min(100, Math.round((currentAmount.value / targetAmount.value) * 100))
})

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
  const months = (targetDate.getFullYear() - today.getFullYear()) * 12
    + targetDate.getMonth() - today.getMonth()
  return Math.max(0, months)
})

const addMonths = (date, months) => {
  const result = new Date(date)
  result.setMonth(result.getMonth() + months)
  return result
}

const formatMilestoneDate = (date) => new Intl.DateTimeFormat("ko-KR", {
  year: "numeric",
  month: "short",
}).format(date)

const goalRoadmapSteps = computed(() => {
  const goal = currentGoal.value
  if (!goal) return []

  const today = new Date()
  const targetDate = parseGoalDate(goal.targetDate) ?? today
  const months = Math.max(1, remainingMonths.value)
  const remainingAmount = Math.max(0, targetAmount.value - currentAmount.value)
  const checkpoints = [
    { ratio: 0, title: "저축 계획 시작", icon: "bi-wallet2" },
    { ratio: 1 / 3, title: "1차 진행 점검", icon: "bi-graph-up-arrow" },
    { ratio: 2 / 3, title: "중간 목표 달성", icon: "bi-clipboard-check" },
    { ratio: 1, title: "최종 목표 달성", icon: "bi-flag" },
  ]

  return checkpoints.map((checkpoint, index) => {
    const milestoneAmount = index === 3
      ? targetAmount.value
      : Math.round((currentAmount.value + remainingAmount * checkpoint.ratio) / 10000) * 10000
    const milestoneDate = index === 3
      ? targetDate
      : addMonths(today, Math.round(months * checkpoint.ratio))
    const milestoneRate = targetAmount.value > 0
      ? Math.round((milestoneAmount / targetAmount.value) * 100)
      : 0

    return {
      number: index + 1,
      icon: checkpoint.icon,
      title: checkpoint.title,
      date: formatMilestoneDate(milestoneDate),
      description: index === 0
        ? `매월 ${formatWon(goal.requiredMonthlyAmount)} 자동 저축을 시작하세요.`
        : `${formatWon(milestoneAmount)}까지 모으는 단계예요.`,
      completed: achievementRate.value >= milestoneRate,
      active: index === Math.min(3, Math.floor(achievementRate.value / 25)),
    }
  })
})

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

const startGoalSetting = async () => {
  await router.push({ name: "chat" })
}

onMounted(() => {
  goalStore.fetchGoals({ notifyError: false })
})
</script>

<template>
  <section class="assistant-page">
    <header class="assistant-header mb-4">
      <h1 class="mb-2 fw-bold">AI 컨설팅</h1>
      <p class="mb-0 text-secondary">
        AI가 당신의 재무 상황을 분석하고, 목표 달성을 위한 맞춤 로드맵을 제안해드립니다.
      </p>
    </header>

    <div v-if="isLoading" class="state-card card border-0 shadow-sm" role="status">
      <div class="card-body d-flex align-items-center justify-content-center gap-2">
        <span class="spinner-border spinner-border-sm text-primary" aria-hidden="true"></span>
        목표 정보를 불러오는 중입니다.
      </div>
    </div>

    <div v-else-if="error" class="state-card card border-0 shadow-sm">
      <div class="card-body text-center">
        <p class="mb-3 text-danger">{{ error }}</p>
        <button type="button" class="btn btn-outline-primary" @click="goalStore.fetchGoals()">
          다시 시도
        </button>
      </div>
    </div>

    <div v-else-if="!hasGoal" class="empty-dashboard">
      <div class="row g-4 align-items-stretch">
        <div class="col-xl-8">
          <article class="content-card card h-100 border-0 shadow-sm">
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
                <button type="button" class="btn goal-button" @click="startGoalSetting">
                  목표 설정하기
                  <i class="bi bi-arrow-right ms-2" aria-hidden="true"></i>
                </button>
              </div>
            </div>
          </article>
        </div>

        <div class="col-xl-4">
          <aside class="content-card coaching-card card h-100 border-0 shadow-sm">
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
          </aside>
        </div>
      </div>

      <article class="content-card card border-0 shadow-sm mt-4">
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
              <span class="step-icon"><i class="bi" :class="step.icon" aria-hidden="true"></i></span>
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
      </article>

      <article class="content-card card border-0 shadow-sm mt-4">
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
      </article>
    </div>

    <div v-else class="goal-dashboard">
      <div class="row g-4 align-items-stretch">
        <div class="col-xl-8">
          <article class="content-card card h-100 border-0 shadow-sm">
            <div class="card-body p-4 p-lg-5">
              <div class="goal-card-heading d-flex align-items-start justify-content-between gap-3">
                <h2 class="section-title h5 fw-bold">나의 목표</h2>
                <div class="goal-heading-actions d-flex align-items-center gap-2">
                  <span class="goal-status-badge">진행 중</span>
                  <button type="button" class="btn goal-chat-button" @click="startGoalSetting">
                    <i class="bi bi-chat-dots me-1" aria-hidden="true"></i>
                    AI와 상담하기
                    <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
                  </button>
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
          </article>
        </div>

        <div class="col-xl-4">
          <aside class="content-card coaching-card card h-100 border-0 shadow-sm">
            <div class="card-body p-4 p-lg-5">
              <h2 class="section-title h5 fw-bold">AI 한줄 코칭</h2>
              <div class="coach-bubble mt-4">{{ coachingMessage }}</div>
              <div class="coach-character" aria-hidden="true">
                <span class="sparkle sparkle-one">✦</span>
                <span class="sparkle sparkle-two">✦</span>
                <img :src="walloCharacter" alt="" />
              </div>
            </div>
          </aside>
        </div>
      </div>

      <article class="content-card card border-0 shadow-sm mt-4">
        <div class="card-body p-4 p-lg-5">
          <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
            <h2 class="section-title h5 fw-bold">목표 달성을 위한 로드맵</h2>
            <span class="small text-secondary">현재 목표 기준 예상 계획</span>
          </div>

          <ol class="roadmap-list goal-roadmap-list list-unstyled mt-4 mb-0">
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
              <span class="step-icon"><i class="bi" :class="step.icon" aria-hidden="true"></i></span>
              <span class="step-copy">
                <small class="step-date">{{ step.date }}</small>
                <strong>{{ step.title }}</strong>
                <small>{{ step.description }}</small>
              </span>
              <i
                v-if="index < goalRoadmapSteps.length - 1"
                class="bi bi-arrow-right roadmap-arrow"
                aria-hidden="true"
              ></i>
            </li>
          </ol>
        </div>
      </article>

      <article class="content-card card border-0 shadow-sm mt-4">
        <div class="card-body p-4 p-lg-5">
          <h2 class="section-title h5 fw-bold">이번 달 실천 가이드</h2>
          <div class="row g-3 mt-2">
            <div class="col-lg-4">
              <div class="action-card h-100">
                <span class="action-icon"><i class="bi bi-arrow-repeat"></i></span>
                <div>
                  <h3 class="h6 fw-bold mb-2">자동 저축 설정</h3>
                  <p class="mb-0 text-secondary">급여일에 {{ formatWon(currentGoal.requiredMonthlyAmount) }}이 자동으로 이체되도록 설정해보세요.</p>
                </div>
              </div>
            </div>
            <div class="col-lg-4">
              <div class="action-card h-100">
                <span class="action-icon"><i class="bi bi-calendar-check"></i></span>
                <div>
                  <h3 class="h6 fw-bold mb-2">월말 진행 점검</h3>
                  <p class="mb-0 text-secondary">월말에 목표 계좌 잔액과 계획 대비 달성률을 확인해보세요.</p>
                </div>
              </div>
            </div>
            <div class="col-lg-4">
              <div class="action-card h-100">
                <span class="action-icon"><i class="bi bi-shield-check"></i></span>
                <div>
                  <h3 class="h6 fw-bold mb-2">계획 유지하기</h3>
                  <p class="mb-0 text-secondary">부족한 달은 다음 달 납입액을 조정해 목표 일정이 밀리지 않게 관리해요.</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.assistant-page {
  padding-bottom: 2rem;
  color: #1c2440;
}

.assistant-header h1 {
  font-size: clamp(1.75rem, 3vw, 2.35rem);
  letter-spacing: -0.045em;
}

.assistant-header p {
  font-size: 1.05rem;
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

.coaching-card .card-body {
  position: relative;
  min-height: 100%;
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
  position: absolute;
  right: 1.75rem;
  bottom: 1.5rem;
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

  .roadmap-item:nth-child(2) .roadmap-arrow {
    display: none;
  }
}

@media (max-width: 767.98px) {
  .assistant-header p {
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

  .roadmap-arrow {
    display: none;
  }
}
</style>
