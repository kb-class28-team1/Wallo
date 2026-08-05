<script setup>
import { computed, ref } from "vue";
import { formatNumber, formatWon } from "@/commonUtils/formatters";

const props = defineProps({
  budget: {
    type: Object,
    default: null,
  },
});
const emit = defineEmits(["save-budget"]);

const budgetModalVisible = ref(false);
const budgetAmountInput = ref("");
const currentBudgetMonthLabel = computed(() => `${new Date().getMonth() + 1}월 예산`);
const isBudgetConfigured = computed(() => Number(props.budget?.totalAmount ?? 0) > 0);
const budgetUsageRate = computed(() => {
  const totalAmount = Number(props.budget?.totalAmount ?? 0);
  const spentAmount = Number(props.budget?.spentAmount ?? 0);

  if (totalAmount <= 0) {
    return 0;
  }

  return Math.min(100, Math.round((spentAmount / totalAmount) * 100));
});
const budgetRemaining = computed(() => (
  Number(props.budget?.totalAmount ?? 0) - Number(props.budget?.spentAmount ?? 0)
));

const openBudgetModal = () => {
  budgetAmountInput.value = formatNumber(props.budget?.totalAmount ?? 0);
  budgetModalVisible.value = true;
};

const closeBudgetModal = () => {
  budgetModalVisible.value = false;
};

const formatBudgetAmountInput = () => {
  const numericValue = String(budgetAmountInput.value).replace(/[^0-9]/g, "");

  budgetAmountInput.value = numericValue ? formatNumber(numericValue) : "";
};

const saveBudget = () => {
  const amount = Number(String(budgetAmountInput.value).replaceAll(",", ""));

  if (!Number.isFinite(amount) || amount <= 0) {
    alert("예산은 0원보다 큰 금액으로 입력해주세요.");
    return;
  }

  emit("save-budget", amount);
  closeBudgetModal();
};
</script>

<template>
  <article class="card budget-summary-card border-0 shadow-sm">
    <div class="card-body budget-card-body">
      <div class="d-flex align-items-start justify-content-between gap-3">
        <h2 class="h5 fw-bold mb-0">이번 달 예산</h2>
        <button type="button" class="btn dashboard-action-button" @click="openBudgetModal">
          설정
          <i class="bi bi-gear ms-1" aria-hidden="true"></i>
        </button>
      </div>

      <template v-if="isBudgetConfigured">
        <div class="budget-content">
          <p class="budget-balance-label mb-2">{{ currentBudgetMonthLabel }} 잔액</p>
          <strong class="budget-total d-block mb-3">{{ formatWon(budgetRemaining) }}</strong>

          <div class="d-flex align-items-center gap-3">
            <div
              class="progress budget-progress flex-grow-1"
              role="progressbar"
              aria-label="이번 달 예산 소진율"
              :aria-valuenow="budgetUsageRate"
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <div class="progress-bar" :style="{ width: `${budgetUsageRate}%` }"></div>
            </div>
            <strong class="budget-usage-rate">{{ budgetUsageRate }}%</strong>
          </div>
          <p class="budget-detail mb-0 mt-3">
            지출 {{ formatWon(budget.spentAmount) }} / 예산 {{ formatWon(budget.totalAmount) }}
          </p>
        </div>
      </template>

      <div v-else class="budget-empty-state text-center py-4">
        <p class="text-secondary mb-3">예산이 없습니다. 예산을 설정해주세요.</p>
        <button type="button" class="btn btn-primary" @click="openBudgetModal">설정하기</button>
      </div>
    </div>
  </article>

  <div v-if="budgetModalVisible" class="modal-backdrop fade show"></div>
  <div
    v-if="budgetModalVisible"
    class="modal fade show d-block"
    tabindex="-1"
    role="dialog"
    aria-modal="true"
    aria-labelledby="budgetModalTitle"
    @click.self="closeBudgetModal"
  >
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h2 id="budgetModalTitle" class="modal-title h5">이번 달 예산 설정</h2>
          <button type="button" class="btn-close" aria-label="닫기" @click="closeBudgetModal"></button>
        </div>
        <form @submit.prevent="saveBudget">
          <div class="modal-body">
            <label for="budgetAmount" class="form-label">{{ currentBudgetMonthLabel }}</label>
            <div class="input-group">
              <input
                id="budgetAmount"
                v-model="budgetAmountInput"
                type="text"
                class="form-control"
                inputmode="numeric"
                autocomplete="off"
                required
                @input="formatBudgetAmountInput"
              />
              <span class="input-group-text">원</span>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" @click="closeBudgetModal">취소</button>
            <button type="submit" class="btn btn-primary">저장</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.budget-summary-card {
  border-radius: 48px;
  background: #ffffff;
}

.budget-card-body {
  min-height: 328px;
  padding: 42px;
}

.budget-content {
  margin-top: 28px;
}

.budget-balance-label,
.budget-detail,
.budget-usage-rate {
  color: #111111;
}

.budget-total {
  color: #000000;
  font-size: clamp(1.75rem, 3vw, 2.25rem);
}

.budget-progress {
  height: 12px;
  border-radius: 999px;
}

.budget-progress .progress-bar {
  background: #8170ff;
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

@media (max-width: 991.98px) {
  .budget-card-body {
    min-height: auto;
    padding: 30px;
  }
}
</style>
