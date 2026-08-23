<script setup>
import { computed, ref, watch } from "vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppState from "@/components/ui/AppState.vue"
import { formatWon } from "@/utils/formatters"
import { getGoalAchievementRate, getGoalCurrentAmount } from "@/utils/goalProgress"

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
      <div
        class="goal-card-header d-flex align-items-start justify-content-between gap-3 mb-4"
        :class="{ 'goal-card-header--goal': selectedGoal && !loading && !error }"
      >
        <div v-if="selectedGoal" class="min-w-0">
          <h2 class="h4 fw-bold mb-0 text-truncate">
            {{ selectedGoal.title || "제목 없는 목표" }}
          </h2>
        </div>
        <h2 v-else class="h4 fw-bold mb-0">나의 목표</h2>

        <RouterLink
          v-if="goals.length > 0"
          :to="accountSettingsLink"
          class="btn app-action-link flex-shrink-0 ms-auto pressable"
        >
          계좌 설정
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
        class="goal-state goal-state--empty"
        type="empty"
        title="아직 확정된 금융 목표가 없습니다."
        message="AI 컨설팅에서 목표를 설정해보세요."
        compact
        hide-icon
      >
        <template #actions>
          <RouterLink :to="accountSettingsLink" class="goal-button pressable">
            목표 설정하기
            <i class="bi bi-arrow-right ms-2" aria-hidden="true"></i>
          </RouterLink>
        </template>
      </AppState>

      <div v-else class="goal-list">
        <section
          v-if="selectedGoal"
          :key="selectedGoal.goalId ?? selectedGoal.conversationId ?? selectedGoalIndex"
          class="goal-item"
        >
          <div class="goal-progress-summary mb-4">
            <div class="goal-progress-caption">현재 모은 금액</div>
            <div class="d-flex align-items-baseline justify-content-between gap-3">
              <div class="goal-progress-amount">
                <strong>{{ formatWon(getGoalCurrentAmount(selectedGoal)) }}</strong>
                <span> / {{ formatWon(selectedGoal.targetAmount) }}</span>
              </div>
              <strong class="goal-progress-rate"
                >{{ getGoalAchievementRate(selectedGoal) }}%</strong
              >
            </div>
            <div
              class="progress goal-progress mt-3"
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
  padding: 28px 36px;
}

.goal-card-header {
  margin-bottom: 1.5rem !important;
}

.goal-card-header--goal {
  margin-bottom: 0 !important;
}

.goal-state {
  min-height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.goal-state--empty {
  width: 100%;
  min-height: 0;
  flex: 1 1 auto;
}

.goal-button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  padding: 0.85rem 1.5rem;
  border: 0;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(135deg, #71a1e8, #5a91dc);
  box-shadow: 0 10px 24px rgb(79 143 232 / 22%);
  font-weight: 700;
  line-height: 1;
  text-decoration: none;
  white-space: nowrap;
}

.goal-button:hover,
.goal-button:focus {
  color: #fff;
  background: linear-gradient(135deg, #6599e2, #477fc8);
  text-decoration: none;
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
  background: rgb(79 143 232 / 10%);
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
  font-size: 0.95rem;
}

.goal-progress-summary {
  margin-top: auto;
  margin-bottom: 1.5rem !important;
}

.goal-account-summary {
  min-height: 3rem;
  margin-top: auto;
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
  font-size: 1.65rem;
}

.goal-progress {
  height: 0.7rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--wallo-color-progress-track);
}

.goal-progress-bar {
  border-radius: inherit;
  background: linear-gradient(90deg, #6e9fe8, #4b87d8);
}

@media (max-width: 575.98px) {
  .goal-card-body {
    padding: 30px;
  }

  .goal-summary-card .app-action-link {
    padding: 0.35rem 0.6rem;
    font-size: 0.8rem;
  }
}
</style>
