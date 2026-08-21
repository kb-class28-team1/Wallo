<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue"
import { useRouter } from "vue-router"
import { storeToRefs } from "pinia"
import { useGoalStore } from "@/stores/goalStore"
import { useUserStore } from "@/stores/userStore"
import {
  completeSelfCheckMission,
  getTodayMissions,
  verifyTransactionMission,
} from "@/api/missionApi"
import { formatRoadmapText, formatWon } from "@/utils/formatters"
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
import { announcePointEarned } from "@/utils/pointRewardNotice"

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
const missions = ref([])
const missionStatus = ref("READY")
const missionError = ref("")
const isMissionLoading = ref(false)
let missionDateTimer = null
const missionActionId = ref(null)

const currentAmount = computed(() => getGoalCurrentAmount(currentGoal.value))
const targetAmount = computed(() => getGoalTargetAmount(currentGoal.value))
const achievementRate = computed(() => getGoalAchievementRate(currentGoal.value))
const completedMissionCount = computed(
  () => missions.value.filter((mission) => mission.completed).length,
)

const getMissionVerificationInfo = (mission) => {
  const verificationType = mission?.verificationType
  const typeGuides = {
    MEDIA_AI: {
      label: "사진·영상 AI 인증",
      guide: mission?.evidenceGuide || "미션 수행 장면을 촬영해 피드에 등록하세요.",
    },
    TRANSACTION: {
      label: "거래 내역 자동 확인",
      guide: "연결된 거래 내역을 기준으로 달성 여부를 자동 확인합니다.",
    },
    HYBRID: {
      label: "복합 인증",
      guide: "사진·영상 인증과 거래 내역을 함께 확인해 달성 여부를 판단합니다.",
    },
    SELF_CHECK: {
      label: "직접 완료 체크",
      guide: "미션을 실천한 뒤 오늘의 미션에서 완료 여부를 직접 체크하세요.",
    },
    MANUAL: {
      label: "수동 확인",
      guide: "미션 수행 증빙을 제출하면 확인 후 달성 여부가 결정됩니다.",
    },
  }
  return (
    typeGuides[verificationType] || {
      label: "달성 방법",
      guide: mission?.evidenceGuide || "미션 안내에 따라 실천해 주세요.",
    }
  )
}

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
    title: formatRoadmapText(step.title),
    date: formatGoalDate(step.targetDate),
    description: formatRoadmapText(step.description),
    actionItems: (step.actionItems ?? []).map(formatRoadmapText),
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

const loadTodayMissionList = async () => {
  isMissionLoading.value = true
  missionError.value = ""
  try {
    const response = await getTodayMissions()
    missions.value = response.missions
    missionStatus.value = response.status || "READY"
    const transactionMissions = missions.value.filter(
      (mission) => mission.verificationType === "TRANSACTION" && !mission.completed,
    )
    if (transactionMissions.length) {
      const results = await Promise.allSettled(
        transactionMissions.map((mission) => verifyTransactionMission(mission.id)),
      )
      results.forEach((result) => {
        const rewardedPoint = Number(
          result.status === "fulfilled" ? result.value?.rewardedPoint : 0,
        )
        if (
          result.status === "fulfilled" &&
          result.value?.decision === "PASS" &&
          rewardedPoint > 0
        ) {
          announcePointEarned(rewardedPoint)
        }
      })
      if (
        results.some((result) => result.status === "fulfilled" && result.value?.decision === "PASS")
      ) {
        const refreshed = await getTodayMissions()
        missions.value = refreshed.missions
        missionStatus.value = refreshed.status || "READY"
        window.dispatchEvent(
          new CustomEvent("wallo:mission-updated", {
            detail: { missionResponse: refreshed },
          }),
        )
      }
    }
  } catch (missionLoadError) {
    missions.value = []
    missionError.value = missionLoadError.message || "오늘의 미션을 불러오지 못했습니다."
  } finally {
    isMissionLoading.value = false
  }
}

const applyMissionResponse = (response) => {
  missions.value = response?.missions || []
  missionStatus.value = response?.status || "READY"
  missionError.value = ""
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

const startConsumptionAnalysis = async () => {
  await router.push({
    name: "chat",
    query: { action: "consumption-analysis" },
  })
}

const handleMissionUpdated = (event) => {
  const generatedResponse = event?.detail?.missionResponse
  if (generatedResponse) {
    applyMissionResponse(generatedResponse)
    return
  }
  void loadTodayMissionList()
}

const runMissionAction = async (mission) => {
  if (mission.completed || missionActionId.value) return
  if (mission.verificationType !== "SELF_CHECK") return
  missionActionId.value = mission.id
  try {
    const response = await completeSelfCheckMission(mission.id)
    const rewardedPoint = Number(response?.rewardedPoint || 0)
    if (response?.decision === "PASS" && rewardedPoint > 0) {
      announcePointEarned(rewardedPoint)
    }
    await loadTodayMissionList()
    window.dispatchEvent(new CustomEvent("wallo:mission-updated"))
  } catch (missionActionError) {
    alert(missionActionError.message || "미션 처리에 실패했습니다.")
  } finally {
    missionActionId.value = null
  }
}

const scheduleNextMissionDateRefresh = () => {
  if (missionDateTimer) clearTimeout(missionDateTimer)
  const now = new Date()
  const nextDate = new Date(now)
  nextDate.setHours(24, 0, 0, 250)
  missionDateTimer = setTimeout(async () => {
    await loadTodayMissionList()
    scheduleNextMissionDateRefresh()
  }, nextDate.getTime() - now.getTime())
}

const handlePageVisibility = () => {
  if (document.visibilityState === "visible") void loadTodayMissionList()
}

onMounted(() => {
  window.addEventListener("wallo:mission-updated", handleMissionUpdated)
  window.addEventListener("focus", handlePageVisibility)
  document.addEventListener("visibilitychange", handlePageVisibility)
  void loadGoalPage({ force: true })
  void loadTodayMissionList()
  scheduleNextMissionDateRefresh()
})

onBeforeUnmount(() => {
  window.removeEventListener("wallo:mission-updated", handleMissionUpdated)
  window.removeEventListener("focus", handlePageVisibility)
  document.removeEventListener("visibilitychange", handlePageVisibility)
  if (missionDateTimer) clearTimeout(missionDateTimer)
})
</script>

<template>
  <section class="assistant-page">
    <AppPageHeader class="assistant-header" title="AI 컨설팅" />

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
        <div class="col-xl-7">
          <AppCard as="article" class="content-card goal-main-card h-100" padding="none">
            <div class="card-body p-4">
              <h2 class="section-title h5 fw-bold">
                나의 목표
                <i class="bi ms-1 text-secondary" aria-hidden="true"></i>
              </h2>

              <div class="goal-empty-box mt-3">
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

              <div class="goal-coaching-inline mt-3">
                <div class="goal-coaching-copy">
                  <span class="goal-coaching-label">
                    <i class="bi bi-stars" aria-hidden="true"></i>
                    AI 한줄 코칭
                  </span>
                  <p class="mb-0">목표가 있어야 방향이 생겨요! 작은 목표부터 함께 시작해봐요.</p>
                </div>
                <img :src="walloCharacter" alt="" aria-hidden="true" />
              </div>
            </div>
          </AppCard>
        </div>

        <div class="col-xl-5">
          <AppCard as="aside" class="content-card mission-card h-100" padding="none">
            <div class="card-body p-4">
              <div class="mission-card-heading">
                <h2 class="section-title h5 fw-bold">오늘의 미션</h2>
                <strong v-if="missions.length" class="mission-count">
                  {{ completedMissionCount }}/{{ missions.length }}
                </strong>
              </div>

              <AppState
                v-if="isMissionLoading"
                class="mission-state mt-4"
                type="loading"
                compact
                title="미션을 불러오는 중입니다."
              />
              <AppAlert
                v-else-if="missionError"
                class="mt-4"
                variant="danger"
                :message="missionError"
              />
              <div
                v-else-if="missionStatus === 'WAITING_ANALYSIS'"
                class="mission-empty mission-empty-analysis mt-4"
              >
                소비 분석이 완료되면 오늘의 미션이 생성됩니다.
                <AppButton
                  class="mt-3"
                  variant="outline"
                  size="sm"
                  block
                  @click="startConsumptionAnalysis"
                >
                  <i class="bi bi-bar-chart-line me-1" aria-hidden="true"></i>
                  소비분석 하러가기
                </AppButton>
              </div>
              <div v-else-if="!missions.length" class="mission-empty mt-4">
                오늘 배정된 미션이 없습니다.
              </div>
              <div v-else class="mission-panel mt-4">
                <ul class="mission-list list-unstyled mb-0">
                  <li
                    v-for="mission in missions"
                    :key="mission.id"
                    class="mission-list-item"
                    :class="{ completed: mission.completed }"
                  >
                    <span class="mission-icon" aria-hidden="true">{{ mission.icon }}</span>
                    <span class="mission-copy">
                      <strong :title="mission.title">{{ mission.title }}</strong>
                      <span class="mission-description" :title="mission.description">
                        {{ mission.description }}
                      </span>
                    </span>
                    <i
                      class="mission-check-icon bi"
                      :class="mission.completed ? 'bi-check-circle-fill' : 'bi-circle'"
                      :aria-label="mission.completed ? '완료' : '미완료'"
                    ></i>
                    <span class="mission-guide" :title="getMissionVerificationInfo(mission).guide">
                      <b>{{ getMissionVerificationInfo(mission).label }}</b>
                      {{ getMissionVerificationInfo(mission).guide }}
                    </span>
                    <AppButton
                      v-if="!mission.completed && mission.verificationType === 'SELF_CHECK'"
                      class="mission-action-button"
                      variant="outline"
                      size="sm"
                      :disabled="missionActionId === mission.id"
                      :aria-label="`${mission.title} 완료 처리`"
                      @click="runMissionAction(mission)"
                    >
                      {{ missionActionId === mission.id ? "확인 중..." : "완료하기" }}
                    </AppButton>
                  </li>
                </ul>
              </div>
            </div>
          </AppCard>
        </div>
      </div>

      <AppCard as="article" class="content-card mt-4" padding="none">
        <div class="card-body p-4 p-lg-5">
          <h2 class="section-title h5 fw-bold">
            목표 달성을 위한 로드맵
            <i class="bi ms-1 text-secondary" aria-hidden="true"></i>
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
        <div class="col-xl-7">
          <AppCard as="article" class="content-card goal-main-card" padding="none">
            <div class="card-body p-4">
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

              <div class="goal-summary mt-3">
                <div class="goal-summary-copy">
                  <h3 class="h4 fw-bold mb-2">{{ currentGoal.title }}</h3>
                  <p class="mb-0 text-secondary">
                    {{ formatGoalDate(currentGoal.targetDate) }}까지
                    <strong class="text-dark">{{ formatWon(currentGoal.targetAmount) }}</strong>
                  </p>
                </div>
              </div>

              <div class="goal-progress-panel mt-3">
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

              <div class="goal-coaching-inline mt-3">
                <div class="goal-coaching-copy">
                  <span class="goal-coaching-label">
                    <i class="bi bi-stars" aria-hidden="true"></i>
                    AI 한줄 코칭
                  </span>
                  <p class="mb-0">{{ coachingMessage }}</p>
                </div>
                <img :src="walloCharacter" alt="" aria-hidden="true" />
              </div>
            </div>
          </AppCard>
        </div>

        <div class="col-xl-5">
          <AppCard as="aside" class="content-card mission-card h-100" padding="none">
            <div class="card-body p-4">
              <div class="mission-card-heading">
                <h2 class="section-title h5 fw-bold">오늘의 미션</h2>
                <strong v-if="missions.length" class="mission-count">
                  {{ completedMissionCount }}/{{ missions.length }}
                </strong>
              </div>

              <AppState
                v-if="isMissionLoading"
                class="mission-state mt-4"
                type="loading"
                compact
                title="미션을 불러오는 중입니다."
              />
              <AppAlert
                v-else-if="missionError"
                class="mt-4"
                variant="danger"
                :message="missionError"
              />
              <div
                v-else-if="missionStatus === 'WAITING_ANALYSIS'"
                class="mission-empty mission-empty-analysis mt-4"
              >
                소비 분석이 완료되면 오늘의 미션이 생성됩니다.
                <AppButton
                  class="mt-3"
                  variant="outline"
                  size="sm"
                  block
                  @click="startConsumptionAnalysis"
                >
                  <i class="bi bi-bar-chart-line me-1" aria-hidden="true"></i>
                  소비분석 하러가기
                </AppButton>
              </div>
              <div v-else-if="!missions.length" class="mission-empty mt-4">
                오늘 배정된 미션이 없습니다.
              </div>
              <div v-else class="mission-panel mt-4">
                <ul class="mission-list list-unstyled mb-0">
                  <li
                    v-for="mission in missions"
                    :key="mission.id"
                    class="mission-list-item"
                    :class="{ completed: mission.completed }"
                  >
                    <span class="mission-icon" aria-hidden="true">{{ mission.icon }}</span>
                    <span class="mission-copy">
                      <strong :title="mission.title">{{ mission.title }}</strong>
                      <span class="mission-description" :title="mission.description">
                        {{ mission.description }}
                      </span>
                    </span>
                    <i
                      class="mission-check-icon bi"
                      :class="mission.completed ? 'bi-check-circle-fill' : 'bi-circle'"
                      :aria-label="mission.completed ? '완료' : '미완료'"
                    ></i>
                    <span class="mission-guide" :title="getMissionVerificationInfo(mission).guide">
                      <b>{{ getMissionVerificationInfo(mission).label }}</b>
                      {{ getMissionVerificationInfo(mission).guide }}
                    </span>
                    <AppButton
                      v-if="!mission.completed && mission.verificationType === 'SELF_CHECK'"
                      class="mission-action-button"
                      variant="outline"
                      size="sm"
                      :disabled="missionActionId === mission.id"
                      :aria-label="`${mission.title} 완료 처리`"
                      @click="runMissionAction(mission)"
                    >
                      {{ missionActionId === mission.id ? "확인 중..." : "완료하기" }}
                    </AppButton>
                  </li>
                </ul>
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
  min-height: 170px;
  align-items: center;
  gap: 2rem;
  padding: 1.35rem 1.5rem;
  border: 2px dashed var(--wallo-color-border);
  border-radius: 20px;
  background: linear-gradient(135deg, #fff 0%, #f5faff 100%);
}

.character-wrap {
  position: relative;
  flex: 0 0 112px;
  text-align: center;
}

.character-wrap img {
  width: 104px;
  max-height: 112px;
  object-fit: contain;
}

.question-mark {
  position: absolute;
  top: -24px;
  right: 4px;
  color: #7fa9e8;
  font-size: 2.75rem;
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
  background: linear-gradient(135deg, #71a1e8, #5a91dc);
  box-shadow: 0 10px 24px rgb(79 143 232 / 22%);
  font-weight: 700;
}

.goal-button:hover,
.goal-button:focus {
  color: #fff;
  background: linear-gradient(135deg, #6599e2, #477fc8);
}

.goal-coaching-inline {
  display: flex;
  min-height: 78px;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  padding: 0.85rem 1.15rem;
  border: 1px solid var(--wallo-color-border);
  border-radius: 18px;
  background: linear-gradient(135deg, #f5faff 0%, #eaf4ff 100%);
}

.goal-coaching-copy {
  min-width: 0;
}

.goal-coaching-label {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.45rem;
  color: #4d85dd;
  font-size: 0.82rem;
  font-weight: 800;
}

.goal-coaching-copy p {
  color: #424862;
  font-weight: 600;
  line-height: 1.7;
}

.goal-coaching-inline img {
  width: 58px;
  height: 54px;
  flex: 0 0 auto;
  object-fit: contain;
}

.mission-card :deep(.app-card__body),
.mission-card .card-body {
  height: 100%;
}

.mission-card-heading {
  display: flex;
  align-items: center;
}

.mission-card-heading {
  align-items: flex-start;
}

.mission-card-heading {
  justify-content: space-between;
  gap: 1rem;
}

.mission-count {
  display: inline-flex;
  min-width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, #71a1e8, #4d86d1);
  box-shadow: 0 8px 18px rgb(79 143 232 / 20%);
  font-size: 0.68rem;
}

.mission-state,
.mission-empty {
  border-radius: 16px;
  background: #f6faff;
}

.mission-empty {
  padding: 1.5rem 1.25rem;
  color: #73798d;
  line-height: 1.65;
  text-align: center;
}

.mission-empty-analysis {
  background: transparent;
}

.mission-panel {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.mission-list {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.mission-list-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: start;
  gap: 0.55rem 0.8rem;
  padding: 0.9rem;
  border: 1px solid #e7e7f2;
  border-radius: 15px;
  background: var(--wallo-color-surface);
}

.mission-list-item.completed {
  border-color: #cec9fa;
  background: #f6faff;
}

.mission-icon {
  display: inline-flex;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: #eaf4ff;
  font-size: 1.2rem;
}

.mission-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 0.25rem;
}

.mission-copy strong {
  display: -webkit-box;
  overflow: hidden;
  font-size: 0.9rem;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.mission-description {
  display: -webkit-box;
  overflow: hidden;
  color: #6f7588;
  font-size: 0.78rem;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.mission-guide {
  grid-column: 1 / -1;
  overflow: hidden;
  padding: 0.45rem 0.55rem;
  border-radius: 9px;
  background: #eef7ff;
  color: #555d73;
  font-size: 0.76rem;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mission-guide b {
  margin-right: 0.25rem;
  color: #4d85dd;
  font-size: 0.72rem;
}

.mission-action-button {
  position: absolute;
  z-index: 2;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 14px;
  opacity: 0;
  color: #fff;
  background: rgb(101 85 223 / 88%);
  box-shadow: none;
  font-weight: 700;
  pointer-events: none;
  transition: opacity 160ms ease;
}

.mission-list-item > :not(.mission-action-button) {
  transition:
    opacity 160ms ease,
    filter 160ms ease;
}

.mission-list-item:has(.mission-action-button):hover > :not(.mission-action-button),
.mission-list-item:has(.mission-action-button):focus-within > :not(.mission-action-button) {
  opacity: 0.22;
  filter: blur(0.6px);
}

.mission-list-item:hover .mission-action-button,
.mission-list-item:focus-within .mission-action-button,
.mission-action-button:focus-visible {
  opacity: 1;
  pointer-events: auto;
}

@media (hover: none) {
  .mission-action-button {
    position: static;
    grid-column: 1 / -1;
    min-height: 34px;
    opacity: 1;
    color: #4d85dd;
    background: #eef7ff;
    pointer-events: auto;
  }

  .mission-list-item:has(.mission-action-button):focus-within > :not(.mission-action-button) {
    opacity: 1;
    filter: none;
  }
}

.mission-list-item.completed .mission-copy strong {
  color: #8b8fa0;
  text-decoration: line-through;
}

.mission-check-icon {
  margin-top: 0.1rem;
  color: #aaaebd;
  font-size: 1.2rem;
}

.mission-list-item.completed .mission-check-icon {
  color: #548be0;
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
  scrollbar-color: #c7ddf7 var(--wallo-color-surface-soft);
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
  color: #4d85dd;
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
  color: #6a9ce3;
  background: rgb(79 143 232 / 8%);
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
  border: 2px solid #77a8e7;
  background: #f7fbff;
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
  background: #548be0;
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
  color: #659be8;
  background: #e8f3ff;
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
  border: 1px solid #e7f1f9;
  border-radius: 18px;
  background: linear-gradient(135deg, #fff, #f8fbff);
}

.benefit-icon {
  flex: 0 0 auto;
  color: #548be0;
  font-size: 3rem;
}

.benefit-card p {
  font-size: 0.92rem;
  line-height: 1.65;
}

.goal-status-badge {
  padding: 0.4rem 0.75rem;
  border-radius: 999px;
  color: #4d85dd;
  background: #eaf4ff;
  font-size: 0.8rem;
  font-weight: 700;
}

.goal-chat-button {
  padding: 0.55rem 0.9rem;
  border: 1px solid #6a9ce3;
  border-radius: 12px;
  color: #4d85dd;
  background: #fff;
  font-size: 0.85rem;
  font-weight: 700;
}

.goal-chat-button:hover,
.goal-chat-button:focus {
  border-color: #4d85dd;
  color: #fff;
  background: #4d85dd;
}

.goal-summary {
  display: flex;
  align-items: center;
  gap: 1.25rem;
}

.goal-progress-panel {
  padding: 1rem 1.2rem;
  border-radius: 18px;
  background: #f6faff;
}

.goal-current-amount {
  color: var(--wallo-color-primary);
  font-size: 1.35rem;
  font-weight: 800;
}

.goal-current-amount small {
  color: #7f8495;
  font-size: 0.9rem;
  font-weight: 500;
}

.goal-rate {
  color: #4e84d5;
  font-size: 1.1rem;
}

.goal-progress {
  height: 0.7rem;
  border-radius: 999px;
  background: var(--wallo-color-progress-track);
}

.goal-progress .progress-bar {
  border-radius: inherit;
  background: linear-gradient(90deg, #6e9fe8, #4b87d8);
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
  border-color: #c7ddf7;
  background: #f8fbff;
}

.roadmap-item.completed .step-number {
  color: #fff;
  background: #7da9e5;
}

.step-date {
  color: #6095dc !important;
  font-weight: 700;
}

.roadmap-state {
  padding: 1.5rem;
  border-radius: 16px;
  background: #f6faff;
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
  border: 1px solid #e7f1f9;
  border-radius: 18px;
  background: var(--wallo-color-surface);
}

.action-icon {
  display: inline-flex;
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  color: #568bd6;
  background: #eaf4ff;
  font-size: 1.35rem;
}

.action-card p {
  font-size: 0.9rem;
  line-height: 1.65;
}

@media (max-width: 1199.98px) {
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

  .goal-coaching-inline {
    align-items: flex-start;
    padding: 1.15rem 1.25rem;
  }

  .goal-coaching-inline img {
    width: 64px;
    height: 60px;
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
