<script setup>
import { computed, ref, watch } from "vue";
import { formatWon } from "@/commonUtils/formatters";

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
  accountLoading: {
    type: Boolean,
    default: false,
  },
  accountSaving: {
    type: Boolean,
    default: false,
  },
  accountError: {
    type: String,
    default: null,
  },
});

const emit = defineEmits(["retry", "retry-accounts", "select-account"]);
const selectedGoalIndex = ref(0);
const selectedAccountId = ref(null);

const selectedGoal = computed(() => props.goals[selectedGoalIndex.value] ?? null);
const savedAccountId = computed(() => {
  const selectedAccount = props.availableAccounts.find((account) => account.selected);
  return selectedAccount?.accountId ?? null;
});
const accountSelectionChanged = computed(() => (
  selectedAccountId.value !== null
  && Number(selectedAccountId.value) !== Number(savedAccountId.value)
));

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

watch(
  () => props.availableAccounts,
  (accounts) => {
    const selectedAccount = accounts.find((account) => account.selected);
    selectedAccountId.value = selectedAccount?.accountId ?? null;
  },
  { deep: true, immediate: true },
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

const getCurrentAmount = (goal) => {
  const currentAmount = Number(goal?.currentAmount);
  if (Number.isFinite(currentAmount)) {
    return currentAmount;
  }

  return Number(goal?.initialAmount) || 0;
};

const getAchievementRate = (goal) => {
  const targetAmount = Number(goal?.targetAmount);
  const serverRate = Number(goal?.achievementRate);

  if (Number.isFinite(serverRate)) {
    return Math.min(100, Math.max(0, Math.round(serverRate)));
  }

  if (!Number.isFinite(targetAmount) || targetAmount <= 0) {
    return 0;
  }

  return Math.min(100, Math.max(0, Math.round((getCurrentAmount(goal) / targetAmount) * 100)));
};

const formatAccountBalance = (account) => {
  if (!account?.currency || account.currency === "KRW") {
    return formatWon(account?.balance);
  }

  return `${new Intl.NumberFormat("ko-KR").format(Number(account.balance) || 0)} ${account.currency}`;
};

const submitAccountSelection = () => {
  if (!selectedGoal.value?.goalId || !accountSelectionChanged.value) {
    return;
  }

  emit("select-account", {
    goalId: selectedGoal.value.goalId,
    accountId: selectedAccountId.value,
  });
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

        <RouterLink to="/ai-consulting" class="btn dashboard-action-button flex-shrink-0 ms-auto">
          목표 설정
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
                <strong>{{ formatWon(getCurrentAmount(selectedGoal)) }}</strong>
                <span>/ {{ formatWon(selectedGoal.targetAmount) }}</span>
              </div>
              <strong class="goal-progress-rate">{{ getAchievementRate(selectedGoal) }}%</strong>
            </div>
            <div
              class="progress goal-progress mt-2"
              role="progressbar"
              :aria-label="`${selectedGoal.title || '금융 목표'} 달성률`"
              :aria-valuenow="getAchievementRate(selectedGoal)"
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <div
                class="progress-bar goal-progress-bar"
                :style="{ width: `${getAchievementRate(selectedGoal)}%` }"
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

          <section class="goal-account-selection" aria-labelledby="goal-account-selection-title">
            <div class="goal-account-heading d-flex align-items-start justify-content-between gap-3">
              <div>
                <h3 id="goal-account-selection-title" class="h6 fw-bold mb-1">
                  목표에 사용할 계좌
                </h3>
                <p class="small text-secondary mb-0">
                  연결된 계좌 중 하나를 선택해 주세요.
                </p>
              </div>
              <span v-if="availableAccounts.length > 0" class="small text-secondary text-nowrap">
                {{ availableAccounts.length }}개 사용 가능
              </span>
            </div>

            <div v-if="accountLoading" class="goal-account-state text-secondary" role="status">
              <span class="spinner-border spinner-border-sm text-primary me-2" aria-hidden="true"></span>
              사용 가능한 계좌를 불러오는 중입니다.
            </div>

            <div v-else class="mt-3">
              <div v-if="accountError" class="alert alert-danger py-2" role="alert">
                <div class="d-flex align-items-center justify-content-between gap-3">
                  <span>{{ accountError }}</span>
                  <button
                    type="button"
                    class="btn btn-sm btn-outline-danger text-nowrap"
                    @click="emit('retry-accounts')"
                  >
                    다시 시도
                  </button>
                </div>
              </div>

              <div v-if="availableAccounts.length === 0" class="goal-account-empty text-secondary">
                <p class="mb-1 fw-semibold text-dark">사용 가능한 계좌가 없습니다.</p>
                <p class="small mb-0">입출금·예금·적금·CMA 계좌를 연결해 주세요.</p>
              </div>

              <div v-else>
                <div class="goal-account-list" role="radiogroup" aria-label="목표에 사용할 계좌 선택">
                  <label
                    v-for="account in availableAccounts"
                    :key="account.accountId"
                    class="goal-account-option"
                    :class="{ 'goal-account-option-selected': Number(selectedAccountId) === Number(account.accountId) }"
                  >
                    <input
                      v-model="selectedAccountId"
                      class="form-check-input mt-1"
                      type="radio"
                      :name="`goal-account-${selectedGoal.goalId}`"
                      :value="account.accountId"
                    />
                    <span class="goal-account-content">
                      <span class="d-flex align-items-center justify-content-between gap-3">
                        <span class="min-w-0">
                          <strong class="d-block text-truncate">{{ account.bankName }}</strong>
                          <span class="small text-secondary d-block text-truncate">
                            {{ account.accountName }} · {{ account.displayNumber }}
                          </span>
                        </span>
                        <span class="text-end text-nowrap">
                          <span class="goal-account-type d-block">{{ account.accountType }}</span>
                          <strong class="d-block">{{ formatAccountBalance(account) }}</strong>
                        </span>
                      </span>
                    </span>
                  </label>
                </div>

                <button
                  type="button"
                  class="btn btn-primary w-100 mt-3"
                  :disabled="!accountSelectionChanged || accountSaving"
                  @click="submitAccountSelection"
                >
                  <span
                    v-if="accountSaving"
                    class="spinner-border spinner-border-sm me-2"
                    aria-hidden="true"
                  ></span>
                  {{ accountSaving ? "계좌 저장 중..." : "이 계좌를 목표 계좌로 선택" }}
                </button>
              </div>
            </div>
          </section>

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

.goal-account-selection {
  margin-top: 2rem;
  padding-top: 1.5rem;
  border-top: 1px solid #eef0f5;
}

.goal-account-heading {
  min-width: 0;
}

.goal-account-state,
.goal-account-empty {
  padding: 1rem;
  border-radius: 14px;
  background: #f8f9fc;
  text-align: center;
}

.goal-account-list {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.goal-account-option {
  display: flex;
  gap: 0.75rem;
  margin: 0;
  padding: 0.85rem 1rem;
  border: 1px solid #e4e8f0;
  border-radius: 14px;
  background: #ffffff;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.goal-account-option:hover {
  border-color: #aaa4ee;
  background: #fafaff;
}

.goal-account-option-selected {
  border-color: #6559e8;
  background: #f7f6ff;
  box-shadow: 0 0 0 2px rgb(101 89 232 / 10%);
}

.goal-account-content {
  min-width: 0;
  flex: 1;
}

.goal-account-type {
  color: #6559e8;
  font-size: 0.75rem;
  font-weight: 700;
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
