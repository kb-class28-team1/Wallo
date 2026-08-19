<script setup>
import { computed } from "vue"
import { Doughnut } from "vue-chartjs"
import { ArcElement, Chart as ChartJS, Legend, Tooltip } from "chart.js"
import AppCard from "@/components/ui/AppCard.vue"
import AppState from "@/components/ui/AppState.vue"
import { getExpenseCategoryLabel } from "@/features/financial/financialCategories"
import { formatWon } from "@/utils/formatters"

ChartJS.register(ArcElement, Tooltip, Legend)

const props = defineProps({
  expenses: {
    type: Object,
    default: () => ({
      totalExpense: 0,
      expenseCategoryBreakdown: [],
    }),
  },
  chartData: {
    type: Object,
    required: true,
  },
})

const topExpenseCategories = computed(() =>
  [...(props.expenses.expenseCategoryBreakdown ?? [])]
    .sort((first, second) => Number(second.amount) - Number(first.amount))
    .slice(0, 5),
)
const hasExpenseData = computed(() => props.chartData.datasets[0].data.length > 0)
const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  events: [],
  cutout: "68%",
  plugins: {
    legend: {
      display: false,
    },
    tooltip: {
      callbacks: {
        label: (context) => `${context.label}: ${formatWon(context.parsed)}`,
      },
    },
  },
}

const expenseCategoryColor = (category) => {
  const categoryIndex = (props.expenses.expenseCategoryBreakdown ?? []).findIndex(
    (item) => item.category === category,
  )

  return props.chartData.datasets[0].backgroundColor[categoryIndex] ?? "#8170ff"
}

const expenseCategoryRate = (amount) => {
  const totalExpense = Number(props.expenses.totalExpense ?? 0)

  if (totalExpense <= 0) {
    return 0
  }

  return Math.round((Number(amount) / totalExpense) * 100)
}
</script>

<template>
  <AppCard class="expense-summary-card" padding="none">
    <div class="expense-card-body">
      <div class="d-flex align-items-start justify-content-between gap-3">
        <div>
          <h2 class="h5 fw-bold mb-2">이번 달 총 지출</h2>
          <strong class="expense-total">{{ formatWon(expenses.totalExpense) }}</strong>
        </div>
        <RouterLink to="/assets/expenses" class="btn dashboard-action-button">
          더보기
          <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
        </RouterLink>
      </div>

      <div v-if="hasExpenseData" class="row align-items-center g-4 mt-2">
        <div class="col-md-4">
          <div class="expense-doughnut-chart">
            <Doughnut :data="chartData" :options="chartOptions" />
            <p class="expense-doughnut-center mb-0">지출 비중<br />TOP 5</p>
          </div>
        </div>
        <div class="col-md-8">
          <ol class="expense-category-list list-unstyled mb-0">
            <li v-for="category in topExpenseCategories" :key="category.category">
              <span class="expense-category-name d-flex align-items-center gap-2">
                <span
                  class="expense-category-dot"
                  :style="{ backgroundColor: expenseCategoryColor(category.category) }"
                ></span>
                {{ getExpenseCategoryLabel(category.category) }}
              </span>
              <span class="expense-category-rate">{{ expenseCategoryRate(category.amount) }}%</span>
              <strong>{{ formatWon(category.amount) }}</strong>
            </li>
          </ol>
        </div>
      </div>

      <AppState
        v-else
        class="expense-empty-state"
        type="empty"
        title="이번 달 지출 데이터가 없습니다."
        message="지출 내역이 등록되면 카테고리별 현황을 확인할 수 있습니다."
        compact
      />
    </div>
  </AppCard>
</template>

<style scoped>
.expense-summary-card {
  width: 100%;
  border-radius: var(--wallo-radius-xl);
  background: var(--wallo-color-surface);
}

.expense-card-body {
  min-height: 294px;
  padding: var(--wallo-space-5) var(--wallo-space-6);
}

.expense-total {
  color: var(--wallo-color-text);
  font-size: clamp(1.75rem, 3vw, 2.25rem);
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

.expense-doughnut-chart {
  position: relative;
  height: 180px;
}

.expense-doughnut-center {
  position: absolute;
  top: 50%;
  left: 50%;
  color: var(--wallo-color-text-muted);
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.45;
  text-align: center;
  transform: translate(-50%, -50%);
}

.expense-category-list {
  display: grid;
  gap: var(--wallo-space-3);
}

.expense-category-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 44px 118px;
  padding-bottom: var(--wallo-space-2);
  border-bottom: 1px solid var(--wallo-color-border-soft);
  color: var(--wallo-color-text);
}

.expense-category-list li:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.expense-category-name {
  min-width: 0;
  font-weight: 600;
}

.expense-category-dot {
  display: inline-flex;
  width: 13px;
  height: 13px;
  flex: 0 0 13px;
  border-radius: 50%;
}

.expense-category-rate {
  color: var(--wallo-color-text-muted);
  font-size: 0.9rem;
  text-align: right;
}

.expense-category-list strong {
  text-align: right;
  white-space: nowrap;
}

.expense-empty-state {
  min-height: 180px;
  padding: var(--wallo-space-4);
  background: transparent;
  border: 0;
  box-shadow: none;
}

@media (max-width: 991.98px) {
  .expense-card-body {
    min-height: auto;
    padding: var(--wallo-space-5);
  }
}
</style>
