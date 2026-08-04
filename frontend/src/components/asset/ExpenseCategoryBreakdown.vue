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
});

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

  if (normalizedCategories.length <= 6) {
    return normalizedCategories;
  }

  const topCategories = normalizedCategories
    .filter((item) => item.category !== "ETC")
    .slice(0, 5);
  const topCategoryCodes = new Set(topCategories.map((item) => item.category));
  const etcAmount = normalizedCategories
    .filter((item) => !topCategoryCodes.has(item.category))
    .reduce((sum, item) => sum + item.amount, 0);

  if (etcAmount > 0) {
    topCategories.push({
      category: "ETC",
      label: EXPENSE_CATEGORY_META.ETC.label,
      amount: etcAmount,
      color: EXPENSE_CATEGORY_META.ETC.color,
      icon: EXPENSE_CATEGORY_META.ETC.icon,
    });
  }

  return topCategories;
});

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
</script>

<template>
  <article class="card category-card border-0 shadow-sm">
    <div class="card-body category-card-body">
      <h2 class="h5 fw-bold mb-0">카테고리별 소비 내역</h2>

      <div v-if="categories.length" class="row align-items-center g-4 mt-2">
        <div class="col-md-5 col-lg-4">
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

        <div class="col-md-7 col-lg-8">
          <ul class="category-list list-unstyled mb-0">
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

@media (max-width: 575.98px) {
  .category-card-body {
    padding: 26px 20px;
  }
}
</style>
