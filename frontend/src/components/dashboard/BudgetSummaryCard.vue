<script setup>
import { computed } from "vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppProgress from "@/components/ui/AppProgress.vue"
import AppState from "@/components/ui/AppState.vue"
import { formatWon } from "@/utils/formatters"

const props = defineProps({
  budget: {
    type: Object,
    default: null,
  },
})
const emit = defineEmits(["open-budget-settings"])
const currentBudgetMonthLabel = computed(() => `${new Date().getMonth() + 1}월 예산`)
const isBudgetConfigured = computed(() => Number(props.budget?.totalAmount ?? 0) > 0)
const budgetUsageRate = computed(() => {
  const totalAmount = Number(props.budget?.totalAmount ?? 0)
  const spentAmount = Number(props.budget?.spentAmount ?? 0)

  if (totalAmount <= 0) {
    return 0
  }

  return Math.round((spentAmount / totalAmount) * 100)
})
const isBudgetOver = computed(
  () => Number(props.budget?.spentAmount ?? 0) > Number(props.budget?.totalAmount ?? 0),
)
const budgetRemaining = computed(
  () => Number(props.budget?.totalAmount ?? 0) - Number(props.budget?.spentAmount ?? 0),
)
</script>

<template>
  <AppCard class="budget-summary-card" padding="none">
    <div class="budget-card-body">
      <div class="d-flex align-items-start justify-content-between gap-3">
        <h2 class="h5 fw-bold mb-0">이번 달 예산</h2>
        <button
          type="button"
          class="btn dashboard-action-button"
          @click="emit('open-budget-settings')"
        >
          설정하기
          <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
        </button>
      </div>

      <template v-if="isBudgetConfigured">
        <div class="budget-content">
          <p class="budget-balance-label mb-2">{{ currentBudgetMonthLabel }} 잔액</p>
          <strong class="budget-total d-block mb-3" :class="{ 'text-danger': isBudgetOver }">
            {{ formatWon(budgetRemaining) }}
          </strong>

          <div class="d-flex align-items-center gap-3">
            <AppProgress
              class="budget-progress flex-grow-1"
              label="예산 소진율"
              :value="budgetUsageRate"
              :variant="isBudgetOver ? 'danger' : 'info'"
              size="md"
            />
            <strong class="budget-usage-rate" :class="{ 'text-danger': isBudgetOver }">
              {{ budgetUsageRate }}%
            </strong>
          </div>
          <p class="budget-detail mb-0 mt-3">
            지출 {{ formatWon(budget.spentAmount) }} / 예산 {{ formatWon(budget.totalAmount) }}
          </p>
        </div>
      </template>

      <AppState
        v-else
        class="budget-state"
        type="empty"
        title="아직 설정된 예산이 없습니다."
        message="예산을 설정해주세요"
        compact
        hide-icon
      />
    </div>
  </AppCard>
</template>

<style scoped>
.budget-summary-card {
  border-radius: var(--wallo-radius-xl);
  background: var(--wallo-color-surface);
}

.budget-card-body {
  display: flex;
  flex-direction: column;
  min-height: 312px;
  padding: var(--wallo-space-5) var(--wallo-space-6);
}

.budget-content {
  margin-top: 24px;
}

.budget-balance-label,
.budget-detail,
.budget-usage-rate {
  color: var(--wallo-color-text);
}

.budget-total {
  color: var(--wallo-color-text);
  font-size: clamp(1.75rem, 3vw, 2.25rem);
}

.budget-progress {
  min-width: 0;
}

.budget-state {
  display: flex;
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
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

@media (max-width: 991.98px) {
  .budget-card-body {
    min-height: auto;
    padding: var(--wallo-space-5);
  }
}
</style>
