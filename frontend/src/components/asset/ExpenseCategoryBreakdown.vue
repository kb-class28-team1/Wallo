<script setup>
import { computed, nextTick, ref } from "vue";
import { Doughnut } from "vue-chartjs";
import { ArcElement, Chart as ChartJS, Tooltip } from "chart.js";
import {
  EXPENSE_CATEGORY_META,
  getExpenseCategoryMeta,
  normalizeExpenseCategory,
} from "@/features/financial/financialCategories";
import { formatWon } from "@/utils/formatters";
import AppState from "@/components/ui/AppState.vue";

ChartJS.register(ArcElement, Tooltip);

const props = defineProps({
  breakdown: {
    type: Array,
    default: () => [],
  },
  totalExpense: {
    type: Number,
    default: 0,
  },
  budgetSummary: {
    type: Object,
    default: null,
  },
  budgetLoading: {
    type: Boolean,
    default: false,
  },
  budgetError: {
    type: String,
    default: "",
  },
  canEditBudget: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["edit-budget"]);

const chartRef = ref(null);
const hoveredIndex = ref(null);

const categories = computed(() => {
  const amountByCategory = new Map();

  for (const item of props.breakdown ?? []) {
    const category = normalizeExpenseCategory(item.category);
    amountByCategory.set(
      category,
      (amountByCategory.get(category) ?? 0) + (Number(item.amount) || 0),
    );
  }

  const normalizedCategories = [...amountByCategory.entries()]
    .map(([category, amount]) => {
      const meta = getExpenseCategoryMeta(category);
      return {
        category,
        label: meta.label,
        amount,
        color: meta.color,
        icon: meta.icon,
      };
    })
    .filter((item) => item.amount > 0)
    .sort((first, second) => second.amount - first.amount);

  return normalizedCategories;
});

const hasTwoCategoryColumns = computed(() => categories.value.length > 6);

const hoveredCategory = computed(() =>
  hoveredIndex.value === null ? null : categories.value[hoveredIndex.value] ?? null,
);

const chartData = computed(() => ({
  labels: categories.value.map((item) => item.label),
  datasets: [
    {
      data: categories.value.map((item) => item.amount),
      backgroundColor: categories.value.map((item, index) =>
        hoveredIndex.value === null || hoveredIndex.value === index
          ? item.color
          : `${item.color}45`,
      ),
      borderColor: "#ffffff",
      borderWidth: 4,
      hoverOffset: 6,
    },
  ],
}));

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  cutout: "70%",
  onHover: (_event, activeElements) => {
    hoveredIndex.value = activeElements[0]?.index ?? null;
  },
  plugins: {
    legend: { display: false },
    tooltip: { enabled: false },
  },
};

const categoryRate = (amount) => {
  const totalExpense = Number(props.totalExpense) || 0;
  return totalExpense > 0 ? Math.round(((Number(amount) || 0) / totalExpense) * 100) : 0;
};

const setHoveredCategory = async (index) => {
  hoveredIndex.value = index;
  await nextTick();
  const chart = chartRef.value?.chart;
  chart?.setActiveElements([{ datasetIndex: 0, index }]);
  chart?.update();
};

const clearHoveredCategory = async () => {
  hoveredIndex.value = null;
  await nextTick();
  const chart = chartRef.value?.chart;
  chart?.setActiveElements([]);
  chart?.update();
};

const hasConfiguredBudget = computed(
  () => Number(props.budgetSummary?.totalAmount ?? 0) > 0,
);

const budgetCategories = computed(() => (props.budgetSummary?.categories ?? [])
  .filter((category) => Number(category.budgetAmount) > 0)
  .map((category) => {
    const categoryCode = normalizeExpenseCategory(category.category);
    const meta = getExpenseCategoryMeta(categoryCode);

    return {
      ...category,
      categoryCode,
      label: categoryCode === "ETC" ? "기타·미배정" : meta.label,
      color: meta.color,
      icon: meta.icon,
    };
  }));

const formatUsageRate = (rate, spentAmount = 0) => {
  if (rate === null || rate === undefined) return spentAmount > 0 ? "예산 없음" : "0%";

  const numericRate = Number(rate);
  if (!Number.isFinite(numericRate)) return "-";
  return `${Number.isInteger(numericRate) ? numericRate : numericRate.toFixed(2)}%`;
};

const progressWidth = (rate) => {
  const numericRate = Number(rate);
  if (!Number.isFinite(numericRate)) return 0;
  return Math.min(100, Math.max(0, numericRate));
};
</script>

<template>
  <article class="card category-card border-0 shadow-sm">
    <div class="card-body category-card-body">
      <h2 class="h5 fw-bold mb-0">카테고리별 소비 내역</h2>

      <div
        v-if="categories.length"
        class="row align-items-center g-4 mt-2"
        :class="{ 'category-layout-two-columns': hasTwoCategoryColumns }"
      >
        <div :class="hasTwoCategoryColumns ? 'col-12 col-lg-4' : 'col-md-5 col-lg-4'">
          <div class="category-chart" @mouseleave="clearHoveredCategory">
            <Doughnut ref="chartRef" :data="chartData" :options="chartOptions" />
            <div class="category-chart-center">
              <template v-if="hoveredCategory">
                <span>{{ hoveredCategory.label }}</span>
                <strong>{{ formatWon(hoveredCategory.amount) }}</strong>
              </template>
              <template v-else>
                <span>총 지출</span>
                <strong>{{ formatWon(totalExpense) }}</strong>
              </template>
            </div>
          </div>
        </div>

        <div :class="hasTwoCategoryColumns ? 'col-12 col-lg-8' : 'col-md-7 col-lg-8'">
          <ul
            class="category-list list-unstyled mb-0"
            :class="{ 'category-list-two-columns': hasTwoCategoryColumns }"
          >
            <li
              v-for="(category, index) in categories"
              :key="category.category"
              :class="{ active: hoveredIndex === index }"
              @mouseenter="setHoveredCategory(index)"
              @mouseleave="clearHoveredCategory"
            >
              <span class="category-label">
                <span class="category-icon" :style="{ color: category.color, backgroundColor: `${category.color}18` }">
                  <i :class="['bi', category.icon]" aria-hidden="true"></i>
                </span>
                {{ category.label }}
              </span>
              <span class="category-value">
                <span class="category-rate">{{ categoryRate(category.amount) }}%</span>
                <strong>{{ formatWon(category.amount) }}</strong>
              </span>
            </li>
          </ul>
        </div>
      </div>

      <div v-else class="category-empty text-center">
        <i class="bi bi-pie-chart text-secondary fs-2" aria-hidden="true"></i>
        <p class="text-secondary mb-0 mt-2">표시할 지출 카테고리가 없습니다.</p>
      </div>

      <section class="category-budget-section mt-4 pt-4">
        <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
          <div>
            <h3 class="h5 fw-bold mb-1">카테고리별 예산</h3>
            <p class="text-secondary small mb-0">지출, 잔액, 예산 소진율을 함께 확인하세요.</p>
          </div>
          <button
            v-if="canEditBudget"
            type="button"
            class="btn btn-outline-primary btn-sm"
            data-testid="budget-action"
            @click="emit('edit-budget')"
          >
            {{ hasConfiguredBudget ? "예산 수정" : "예산 설정하기" }}
          </button>
        </div>

        <div v-if="budgetLoading" class="budget-state text-center py-4" aria-live="polite">
          <div class="spinner-border spinner-border-sm text-primary" role="status">
            <span class="visually-hidden">예산 정보를 불러오는 중</span>
          </div>
          <p class="text-secondary small mb-0 mt-2">예산 정보를 불러오는 중입니다.</p>
        </div>

        <div v-else-if="budgetError" class="alert alert-danger mb-0" role="alert">
          {{ budgetError }}
        </div>

        <template v-else-if="budgetSummary && hasConfiguredBudget">
          <div
            class="budget-overview rounded-3 p-3 mb-3"
            :class="{ 'budget-overview-over': budgetSummary.overBudget }"
          >
            <div class="d-flex flex-wrap align-items-center justify-content-between gap-3">
              <div>
                <span class="d-block text-secondary small">전체 예산 잔액</span>
                <strong class="fs-5" :class="{ 'text-danger': budgetSummary.overBudget }">
                  {{ formatWon(budgetSummary.remainingAmount) }}
                </strong>
              </div>
              <div class="text-end">
                <span class="d-block text-secondary small">전체 사용률</span>
                <div class="d-flex align-items-center justify-content-end gap-2">
                  <span
                    class="budget-status-badge"
                    :class="{ 'budget-status-badge-over': budgetSummary.overBudget }"
                  >
                    {{ budgetSummary.overBudget ? "초과" : "정상" }}
                  </span>
                  <strong :class="{ 'text-danger': budgetSummary.overBudget }">
                    {{ formatUsageRate(budgetSummary.usageRate, budgetSummary.spentAmount) }}
                  </strong>
                </div>
              </div>
            </div>
            <div
              class="progress mt-3"
              role="progressbar"
              :aria-valuenow="progressWidth(budgetSummary.usageRate)"
              aria-valuemin="0"
              aria-valuemax="100"
            >
              <div
                class="progress-bar"
                :class="{ 'bg-danger': budgetSummary.overBudget }"
                :style="{ width: `${progressWidth(budgetSummary.usageRate)}%` }"
              ></div>
            </div>
            <div class="d-flex justify-content-between gap-3 mt-2 text-secondary small">
              <span>지출 {{ formatWon(budgetSummary.spentAmount) }}</span>
              <span>예산 {{ formatWon(budgetSummary.totalAmount) }}</span>
            </div>
          </div>

          <ul class="budget-category-list list-unstyled mb-0">
            <li
              v-for="category in budgetCategories"
              :key="category.categoryCode"
              class="budget-category-row"
              :class="{ 'budget-category-row-over': category.overBudget }"
            >
              <div class="d-flex align-items-center justify-content-between gap-3">
                <span class="category-label">
                  <span
                    class="category-icon"
                    :style="{
                      color: category.color,
                      backgroundColor: `${category.color}18`,
                    }"
                  >
                    <i :class="['bi', category.icon]" aria-hidden="true"></i>
                  </span>
                  {{ category.label }}
                </span>
                <span class="d-flex align-items-center gap-2">
                  <span
                    class="budget-status-badge"
                    :class="{ 'budget-status-badge-over': category.overBudget }"
                  >
                    {{ category.overBudget ? "초과" : "정상" }}
                  </span>
                  <span class="budget-category-status" :class="{ 'text-danger': category.overBudget }">
                    {{ formatUsageRate(category.usageRate, category.spentAmount) }}
                  </span>
                </span>
              </div>
              <div class="progress budget-category-progress mt-2" role="progressbar">
                <div
                  class="progress-bar"
                  :class="{ 'bg-danger': category.overBudget }"
                  :style="{ width: `${progressWidth(category.usageRate)}%` }"
                ></div>
              </div>
              <div class="d-flex flex-wrap justify-content-between gap-2 mt-2 small">
                <span class="text-secondary">지출 {{ formatWon(category.spentAmount) }}</span>
                <span :class="category.overBudget ? 'text-danger' : 'text-secondary'">
                  잔액 {{ formatWon(category.remainingAmount) }}
                </span>
                <span class="text-secondary">예산 {{ formatWon(category.budgetAmount) }}</span>
              </div>
            </li>
          </ul>
        </template>

        <AppState
          v-else
          class="budget-state"
          data-testid="budget-empty-state"
          type="empty"
          title="아직 설정된 예산이 없습니다."
          message="예산을 설정해주세요"
          compact
          hide-icon
        />
      </section>
    </div>
  </article>
</template>

<style scoped>
.category-card {
  border-radius: 28px;
  background: #ffffff;
}

.category-card-body {
  padding: 32px 36px;
}

.category-chart {
  position: relative;
  height: 220px;
}

.category-chart-center {
  position: absolute;
  top: 50%;
  left: 50%;
  display: grid;
  gap: 3px;
  text-align: center;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.category-chart-center span {
  color: #8a90a2;
  font-size: 0.8rem;
  font-weight: 700;
}

.category-chart-center strong {
  color: #343044;
  font-size: 0.95rem;
  white-space: nowrap;
}

.category-list {
  display: grid;
  gap: 8px;
}

.category-list-two-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.category-list-two-columns li {
  min-width: 0;
}

.category-list-two-columns .category-value {
  gap: 12px;
}

.category-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  border-radius: 12px;
  transition: background-color 0.2s ease, transform 0.2s ease;
}

.category-list li.active {
  background: #f5f3ff;
  transform: translateX(3px);
}

.category-label {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #555b6e;
  font-weight: 700;
}

.category-icon {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
}

.category-list strong {
  color: #343044;
  white-space: nowrap;
}

.category-value {
  display: inline-flex;
  align-items: center;
  gap: 28px;
}

.category-rate {
  min-width: 38px;
  color: #8a90a2;
  font-size: 0.82rem;
  font-weight: 700;
  text-align: right;
}

.category-empty {
  display: flex;
  min-height: 210px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.category-budget-section {
  border-top: 1px solid #edf0f5;
}

.budget-overview {
  color: #555b6e;
  background: #f7f6fc;
}

.budget-overview-over {
  background: #fff1f1;
}

.budget-category-list {
  display: grid;
  gap: 8px;
}

.budget-category-row {
  padding: 12px;
  border-radius: 12px;
  background: #fbfbfd;
}

.budget-category-row-over {
  background: #fff5f5;
}

.budget-category-status {
  color: #6f7587;
  font-size: 0.85rem;
  font-weight: 800;
}

.budget-status-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 7px;
  border: 1px solid #e1e2ea;
  border-radius: 999px;
  color: #73798b;
  background: #ffffff;
  font-size: 0.7rem;
  font-weight: 800;
  line-height: 1;
}

.budget-status-badge-over {
  border-color: #ffcaca;
  color: #dc3545;
  background: #fff1f1;
}

.budget-category-progress {
  height: 8px;
  background: #e9e8f2;
}

.budget-category-progress .progress-bar {
  background: #8170ff;
}

@media (max-width: 575.98px) {
  .category-card-body {
    padding: 26px 20px;
  }

  .category-list-two-columns {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
