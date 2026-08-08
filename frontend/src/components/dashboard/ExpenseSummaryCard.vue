<script setup>
import { computed } from "vue";
import { Doughnut } from "vue-chartjs";
import { ArcElement, Chart as ChartJS, Legend, Tooltip } from "chart.js";
import { getExpenseCategoryLabel } from "@/features/financial/financialCategories";
import { formatWon } from "@/commonUtils/formatters";

ChartJS.register(ArcElement, Tooltip, Legend);

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
});

const topExpenseCategories = computed(() => (
  [...(props.expenses.expenseCategoryBreakdown ?? [])]
    .sort((first, second) => Number(second.amount) - Number(first.amount))
    .slice(0, 5)
));
const hasExpenseData = computed(() => props.chartData.datasets[0].data.length > 0);
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
};

const expenseCategoryColor = (category) => {
  const categoryIndex = (props.expenses.expenseCategoryBreakdown ?? []).findIndex(
    (item) => item.category === category,
  );

  return props.chartData.datasets[0].backgroundColor[categoryIndex] ?? "#8170ff";
};

const expenseCategoryRate = (amount) => {
  const totalExpense = Number(props.expenses.totalExpense ?? 0);

  if (totalExpense <= 0) {
    return 0;
  }

  return Math.round((Number(amount) / totalExpense) * 100);
};
</script>

<template>
  <article class="card expense-summary-card border-0 shadow-sm">
    <div class="card-body expense-card-body">
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

      <p v-else class="expense-empty-state text-center text-secondary mb-0">
        이번 달 지출 데이터가 없습니다.
      </p>
    </div>
  </article>
</template>

<style scoped>
.expense-summary-card {
  max-width: 540px;
  border-radius: 32px;
  background: #ffffff;
}

.expense-card-body {
  min-height: 310px;
  padding: 36px 42px;
}

.expense-total {
  color: #000000;
  font-size: clamp(1.75rem, 3vw, 2.25rem);
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

.expense-doughnut-chart {
  position: relative;
  height: 190px;
}

.expense-doughnut-center {
  position: absolute;
  top: 50%;
  left: 50%;
  color: #6c757d;
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.45;
  text-align: center;
  transform: translate(-50%, -50%);
}

.expense-category-list {
  display: grid;
  gap: 12px;
}

.expense-category-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 44px 118px;
  padding-bottom: 10px;
  border-bottom: 1px solid #edf0f5;
  color: #111111;
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
  color: #6c757d;
  font-size: 0.9rem;
  text-align: right;
}

.expense-category-list strong {
  text-align: right;
  white-space: nowrap;
}

.expense-empty-state {
  display: flex;
  min-height: 180px;
  align-items: center;
  justify-content: center;
}

@media (max-width: 991.98px) {
  .expense-card-body {
    min-height: auto;
    padding: 30px;
  }
}
</style>
