<script setup>
import { computed, nextTick, ref } from "vue";
import { Doughnut } from "vue-chartjs";
import { ArcElement, Chart as ChartJS, Tooltip } from "chart.js";

ChartJS.register(ArcElement, Tooltip);

const CATEGORY_META = {
  FOOD: { label: "식비", color: "#ff7b6b", icon: "bi-cup-hot" },
  CAFE: { label: "카페", color: "#f28c66", icon: "bi-cup-straw" },
  TRANSPORT: { label: "교통/차량", color: "#2fc595", icon: "bi-bus-front" },
  SHOPPING: { label: "쇼핑", color: "#5b8def", icon: "bi-bag" },
  DELIVERY: { label: "배달", color: "#ffad66", icon: "bi-fork-knife" },
  HOUSING: { label: "주거/통신", color: "#8170ff", icon: "bi-house" },
  LIVING: { label: "생활", color: "#46b8d8", icon: "bi-basket" },
  LOAN_REPAYMENT: { label: "대출상환", color: "#c47cff", icon: "bi-bank" },
  ETC: { label: "기타", color: "#a0a6b5", icon: "bi-receipt" },
};
const FALLBACK_COLORS = ["#46b8d8", "#c47cff", "#f28c66", "#8d99ae"];

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
    const sourceCategory = String(item.category || "ETC").toUpperCase();
    const category = sourceCategory === "OTHER" ? "ETC" : sourceCategory;
    amountByCategory.set(
      category,
      (amountByCategory.get(category) ?? 0) + (Number(item.amount) || 0),
    );
  }

  const normalizedCategories = [...amountByCategory.entries()]
    .map(([category, amount], index) => {
      const meta = CATEGORY_META[category];
      return {
        category,
        label: meta?.label ?? category,
        amount,
        color: meta?.color ?? FALLBACK_COLORS[index % FALLBACK_COLORS.length],
        icon: meta?.icon ?? "bi-receipt",
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
      label: CATEGORY_META.ETC.label,
      amount: etcAmount,
      color: CATEGORY_META.ETC.color,
      icon: CATEGORY_META.ETC.icon,
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

const formatWon = (amount) => `${new Intl.NumberFormat("ko-KR").format(Number(amount) || 0)}원`;

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
              <strong>{{ formatWon(category.amount) }}</strong>
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
