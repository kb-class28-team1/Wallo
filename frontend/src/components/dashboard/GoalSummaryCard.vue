<script setup>
import { computed, ref, watch } from "vue";
import { formatWon } from "@/commonUtils/formatters";
import {
  getGoalAchievementRate,
  getGoalCurrentAmount,
} from "@/commonUtils/goalProgress";

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
});

const emit = defineEmits(["retry"]);
const selectedGoalIndex = ref(0);

const selectedGoal = computed(() => props.goals[selectedGoalIndex.value] ?? null);
const selectedAccount = computed(() => (
  props.availableAccounts.find((account) => account.selected) ?? null
));
const accountSettingsLink = computed(() => {
  if (!props.goals.length) {
    return "/chat?start=goal-setting";
  }

  const conversationId = selectedGoal.value?.conversationId;
  return conversationId
    ? `/chat?conversationId=${encodeURIComponent(conversationId)}`
    : "/chat";
});

watch(
  () => props.goals.length,
  (goalCount) => {
    if (goalCount === 0) {
      selectedGoalIndex.value = 0;
      return;
    }

    if (selectedGoalIndex.value >= goalCount) {
      selectedGoalIndex.value = goalCount - 1;
    }
  },
);

const showPreviousGoal = () => {
  if (props.goals.length < 2) return;
  selectedGoalIndex.value = (
    selectedGoalIndex.value - 1 + props.goals.length
  ) % props.goals.length;
};

const showNextGoal = () => {
  if (props.goals.length < 2) return;
  selectedGoalIndex.value = (selectedGoalIndex.value + 1) % props.goals.length;
};

const formatGoalDate = (date) => {
  if (!date) {
    return "-";
  }

  const parsedDate = new Date(
    String(date).length === 10 ? `${date}T00:00:00` : date,
  );

  if (Number.isNaN(parsedDate.getTime())) {
    return date;
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
  }).format(parsedDate);
};

</script>

<template>
  <article class="card goal-summary-card border-0 shadow-sm">
    <div class="card-body goal-card-body">
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

      <div v-if="loading" class="goal-state text-secondary" role="status">
        <span class="spinner-border spinner-border-sm text-primary me-2" aria-hidden="true"></span>
        목표 정보를 불러오는 중입니다.
      </div>

      <div v-else-if="error" class="alert alert-danger mb-0" role="alert">
        <p class="mb-3">{{ error }}</p>
        <button type="button" class="btn btn-sm btn-outline-danger" @click="emit('retry')">
          다시 시도
        </button>
      </div>

      <div v-else-if="goals.length === 0" class="goal-state text-secondary">
        <i class="bi bi-bullseye fs-2 d-block mb-2" aria-hidden="true"></i>
        <p class="mb-1 fw-semibold text-dark">아직 확정된 금융 목표가 없습니다.</p>
        <p class="mb-0">AI 컨설팅에서 목표를 설정해보세요.</p>
      </div>

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
              <strong class="goal-progress-rate">{{ getGoalAchievementRate(selectedGoal) }}%</strong>
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
              <span class="spinner-border spinner-border-sm text-primary me-2" aria-hidden="true"></span>
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
              <button
                type="button"
                class="btn goal-carousel-button"
                aria-label="이전 목표"
                @click="showPreviousGoal"
              >
                &lt;
              </button>
              <span class="goal-carousel-position" aria-live="polite">
                {{ selectedGoalIndex + 1 }} / {{ goals.length }}
              </span>
              <button
                type="button"
                class="btn goal-carousel-button"
                aria-label="다음 목표"
                @click="showNextGoal"
              >
                &gt;
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  </article>
</template>

<style scoped>
.goal-summary-card {
  width: 100%;
  height: 100%;
  max-width: 1080px;
  border-radius: 32px;
  background: #ffffff;
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
  border: 1px solid #0000d5;
  border-radius: 14px;
  color: #0000d5;
  background: #ffffff;
  transition: color 0.2s ease, background-color 0.2s ease;
}

.dashboard-action-button:hover,
.dashboard-action-button:focus {
  border-color: #0000d5;
  color: #ffffff;
  background: #0000d5;
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
  border-top: 1px solid #e9ecef;
}

.goal-item dt {
  margin-bottom: 0.35rem;
  color: #6c757d;
  font-size: 0.875rem;
  font-weight: 500;
}

.goal-item dd {
  margin-bottom: 0;
  color: #111111;
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
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 10px;
  color: #555b6e;
  background: transparent;
  font-size: 1rem;
  line-height: 1;
}

.goal-carousel-button:hover,
.goal-carousel-button:focus {
  color: #6b5bd2;
  background: #f0edff;
}

.goal-carousel-position {
  min-width: 2.7rem;
  color: #6c757d;
  font-size: 0.75rem;
  text-align: center;
}

.goal-progress-caption {
  margin-bottom: 0.5rem !important;
  color: #6c757d;
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
  color: #6c757d;
  font-size: 0.8rem;
  font-weight: 600;
}

.goal-account-value {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  color: #111111;
}

.goal-account-value span {
  color: #6c757d;
  font-size: 0.875rem;
}

.goal-progress-amount {
  color: #111111;
}

.goal-progress-amount strong {
  color: #4f46e5;
  font-size: 1.65rem;
  letter-spacing: -0.04em;
}

.goal-progress-amount span {
  color: #6c757d;
  font-size: 0.95rem;
}

.goal-progress-rate {
  color: #4f46e5;
  font-size: 1rem;
}

.goal-progress {
  margin-top: 0.75rem !important;
  height: 0.7rem;
  overflow: hidden;
  border-radius: 999px;
  background: #e6e7ff;
}

.goal-progress-bar {
  border-radius: inherit;
  background: linear-gradient(90deg, #5d52f4, #766bff);
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
