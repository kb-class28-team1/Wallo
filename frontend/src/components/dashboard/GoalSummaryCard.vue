<script setup>
import { computed, ref, watch } from "vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppState from "@/components/ui/AppState.vue"
import { formatWon } from "@/commonUtils/formatters"
import { getGoalAchievementRate, getGoalCurrentAmount } from "@/commonUtils/goalProgress"

const props = defineProps({
  goals: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  error: {
    type: String,
    default: null,
  },
  availableAccounts: {
    type: Array,
    default: () => [],
  },
  accountsLoading: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(["retry"])
const selectedGoalIndex = ref(0)

const selectedGoal = computed(() => props.goals[selectedGoalIndex.value] ?? null)
const selectedAccount = computed(
  () => props.availableAccounts.find((account) => account.selected) ?? null,
)
const accountSettingsLink = computed(() => {
  if (!props.goals.length) {
    return "/chat?start=goal-setting"
  }

  const conversationId = selectedGoal.value?.conversationId
  return conversationId ? `/chat?conversationId=${encodeURIComponent(conversationId)}` : "/chat"
})

watch(
  () => props.goals.length,
  (goalCount) => {
    if (goalCount === 0) {
      selectedGoalIndex.value = 0
      return
    }

    if (selectedGoalIndex.value >= goalCount) {
      selectedGoalIndex.value = goalCount - 1
    }
  },
)

const showPreviousGoal = () => {
  if (props.goals.length < 2) return
  selectedGoalIndex.value = (selectedGoalIndex.value - 1 + props.goals.length) % props.goals.length
}

const showNextGoal = () => {
  if (props.goals.length < 2) return
  selectedGoalIndex.value = (selectedGoalIndex.value + 1) % props.goals.length
}

const formatGoalDate = (date) => {
  if (!date) {
    return "-"
  }

  const parsedDate = new Date(String(date).length === 10 ? `${date}T00:00:00` : date)

  if (Number.isNaN(parsedDate.getTime())) {
    return date
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(parsedDate)
}
</script>

<template>
  <AppCard class="goal-summary-card" padding="none">
    <div class="goal-card-body">
      <div class="goal-card-header d-flex align-items-start justify-content-between gap-3 mb-4">
        <div v-if="selectedGoal" class="min-w-0">
          <h2 class="h5 fw-bold mb-0 text-truncate">
            {{ selectedGoal.title || "제목 없는 목표" }}
          </h2>
        </div>

        <RouterLink
          :to="accountSettingsLink"
          class="btn dashboard-action-button flex-shrink-0 ms-auto"
        >
          {{ goals.length > 0 ? "계좌 설정" : "목표 설정하기" }}
          <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
        </RouterLink>
      </div>

      <AppState
        v-if="loading"
        class="goal-state"
        type="loading"
        title="목표 정보를 불러오는 중입니다."
        message="잠시만 기다려 주세요."
        compact
      />

      <AppAlert v-else-if="error" class="goal-state" variant="danger" :title="error">
        <AppButton variant="outline" size="sm" @click="emit('retry')"> 다시 시도 </AppButton>
      </AppAlert>

      <AppState
        v-else-if="goals.length === 0"
        class="goal-state"
        type="empty"
        title="아직 확정된 금융 목표가 없습니다."
        message="AI 컨설팅에서 목표를 설정해보세요."
        compact
      />

      <div v-else class="goal-list">
        <section
          v-if="selectedGoal"
          :key="selectedGoal.goalId ?? selectedGoal.conversationId ?? selectedGoalIndex"
          class="goal-item"
        >
          <div class="goal-progress-summary mb-4">
            <p class="goal-progress-caption mb-1">목표 설정 당시 준비금 기준</p>
            <div class="d-flex align-items-baseline justify-content-between gap-3">
              <div class="goal-progress-amount">
                <strong>{{ formatWon(getGoalCurrentAmount(selectedGoal)) }}</strong>
                <span>/ {{ formatWon(selectedGoal.targetAmount) }}</span>
              </div>
              <strong class="goal-progress-rate"
                >{{ getGoalAchievementRate(selectedGoal) }}%</strong
              >
            </div>
            <div
              class="progress goal-progress mt-2"
              role="progressbar"
              :aria-label="`${selectedGoal.title || '금융 목표'} 달성률`"
              :aria-valuenow="getGoalAchievementRate(selectedGoal)"
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <div
                class="progress-bar goal-progress-bar"
                :style="{ width: `${getGoalAchievementRate(selectedGoal)}%` }"
              ></div>
            </div>
          </div>

          <dl class="row gy-3 mb-0">
            <div class="col-sm-4">
              <dt>목표 금액</dt>
              <dd>{{ formatWon(selectedGoal.targetAmount) }}</dd>
            </div>
            <div class="col-sm-4">
              <dt>목표 날짜</dt>
              <dd>{{ formatGoalDate(selectedGoal.targetDate) }}</dd>
            </div>
            <div class="col-sm-4">
              <dt>월 필요 납입액</dt>
              <dd>{{ formatWon(selectedGoal.requiredMonthlyAmount) }}</dd>
            </div>
          </dl>

          <div class="goal-account-summary" aria-label="설정된 계좌">
            <div class="goal-account-label">설정된 계좌</div>
            <div v-if="accountsLoading" class="small text-secondary" role="status">
              <span
                class="spinner-border spinner-border-sm text-primary me-2"
                aria-hidden="true"
              ></span>
              계좌 정보를 불러오는 중입니다.
            </div>
            <div v-else-if="selectedAccount" class="goal-account-value">
              <strong>{{ selectedAccount.bankName || "연결 계좌" }}</strong>
              <span>
                {{ selectedAccount.accountName || "계좌" }}
                <template v-if="selectedAccount.displayNumber">
                  · {{ selectedAccount.displayNumber }}
                </template>
              </span>
            </div>
            <span v-else class="small text-secondary">설정된 계좌가 없습니다.</span>
          </div>

          <div v-if="goals.length > 1" class="goal-carousel-footer">
            <div class="goal-carousel-controls" aria-label="목표 선택">
              <AppButton
                class="goal-carousel-button"
                variant="ghost"
                size="sm"
                aria-label="이전 목표"
                @click="showPreviousGoal"
              >
                &lt;
              </AppButton>
              <span class="goal-carousel-position" aria-live="polite">
                {{ selectedGoalIndex + 1 }} / {{ goals.length }}
              </span>
              <AppButton
                class="goal-carousel-button"
                variant="ghost"
                size="sm"
                aria-label="다음 목표"
                @click="showNextGoal"
              >
                &gt;
              </AppButton>
            </div>
          </div>
        </section>
      </div>
    </div>
  </AppCard>
</template>

<style scoped>
.goal-summary-card {
  width: 100%;
  height: 100%;
  max-width: 1080px;
  border-radius: var(--wallo-radius-xl);
  background: var(--wallo-color-surface);
}

.goal-summary-card :deep(.app-card__body) {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
}

.goal-card-body {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  padding: 36px 42px;
}

.goal-card-header {
  margin-bottom: 2rem !important;
}

.dashboard-action-button {
  border: 1px solid var(--wallo-color-finance-info);
  border-radius: var(--wallo-radius-md);
  color: var(--wallo-color-finance-info);
  background: var(--wallo-color-surface);
  transition:
    color 0.2s ease,
    background-color 0.2s ease;
}

.dashboard-action-button:hover,
.dashboard-action-button:focus {
  border-color: var(--wallo-color-finance-info-hover);
  color: var(--wallo-color-surface);
  background: var(--wallo-color-finance-info);
}

.goal-state {
  min-height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.goal-list {
  display: flex;
  flex: 1;
  flex-direction: column;
}

.goal-item {
  display: flex;
  flex: 1;
  flex-direction: column;
}

.goal-item + .goal-item {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--wallo-color-border-soft);
}

.goal-item dt {
  margin-bottom: 0.35rem;
  color: var(--wallo-color-text-muted);
  font-size: 0.875rem;
  font-weight: 500;
}

.goal-item dd {
  margin-bottom: 0;
  color: var(--wallo-color-text);
  font-weight: 700;
}

.goal-carousel-controls {
  display: flex;
  align-items: center;
  gap: 0.2rem;
}

.goal-carousel-footer {
  display: flex;
  justify-content: center;
  margin-top: auto;
  padding-top: 2rem;
}

.goal-carousel-button {
  display: inline-flex;
  width: 32px;
  height: 32px;
  min-height: 32px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: var(--wallo-radius-sm);
  color: var(--wallo-color-text-muted);
  background: transparent;
  font-size: 1rem;
  line-height: 1;
}

.goal-carousel-button:hover,
.goal-carousel-button:focus {
  color: var(--wallo-color-primary);
  background: rgb(112 98 222 / 10%);
}

.goal-carousel-position {
  min-width: 2.7rem;
  color: var(--wallo-color-text-muted);
  font-size: 0.75rem;
  text-align: center;
}

.goal-progress-caption {
  margin-bottom: 0.5rem !important;
  color: var(--wallo-color-text-muted);
  font-size: 0.8rem;
}

.goal-progress-summary {
  margin-bottom: 2rem !important;
}

.goal-account-summary {
  margin-top: 1.5rem;
}

.goal-account-label {
  margin-bottom: 0.35rem;
  color: var(--wallo-color-text-muted);
  font-size: 0.8rem;
  font-weight: 600;
}

.goal-account-value {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  color: var(--wallo-color-text);
}

.goal-account-value span {
  color: var(--wallo-color-text-muted);
  font-size: 0.875rem;
}

.goal-progress-amount {
  color: var(--wallo-color-text);
}

.goal-progress-amount strong {
  color: var(--wallo-color-primary);
  font-size: 1.65rem;
  letter-spacing: -0.04em;
}

.goal-progress-amount span {
  color: var(--wallo-color-text-muted);
  font-size: 0.95rem;
}

.goal-progress-rate {
  color: var(--wallo-color-primary);
  font-size: 1rem;
}

.goal-progress {
  margin-top: 0.75rem !important;
  height: 0.7rem;
  overflow: hidden;
  border-radius: var(--wallo-radius-pill);
  background: var(--wallo-color-info-bg);
}

.goal-progress-bar {
  border-radius: inherit;
  background: var(--wallo-color-primary);
}

@media (max-width: 575.98px) {
  .goal-card-body {
    padding: 30px;
  }

  .goal-summary-card .dashboard-action-button {
    padding: 0.35rem 0.6rem;
    font-size: 0.8rem;
  }
}
</style>
