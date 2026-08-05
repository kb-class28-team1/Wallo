<script setup>
import { computed, ref } from "vue";
import { Doughnut } from "vue-chartjs";
import { ArcElement, Chart as ChartJS, Tooltip } from "chart.js";
import {
  ASSET_FALLBACK_COLORS,
  getAssetCategoryMeta,
} from "@/features/financial/financialCategories";
import { formatWon } from "@/commonUtils/formatters";

ChartJS.register(ArcElement, Tooltip);

const props = defineProps({
  assets: {
    type: Object,
    required: true,
  },
});

const totalAssets = computed(() => Number(props.assets.totalAssets) || 0);
const loanBalance = computed(() =>
  Math.abs(
    (props.assets.assetCategoryBreakdown ?? [])
      .filter((item) => String(item.category || "").toUpperCase() === "LOAN")
      .reduce((sum, item) => sum + (Number(item.amount) || 0), 0),
  ),
);
const totalHoldings = computed(() => totalAssets.value + loanBalance.value);

const categories = computed(() =>
  (props.assets.assetCategoryBreakdown ?? [])
    .map((item, index) => {
      const category = String(item.category || "ETC").toUpperCase();
      const amount = Number(item.amount) || 0;
      const meta = getAssetCategoryMeta(category);

      return {
        category,
        label: meta?.label ?? item.category ?? "기타 자산",
        amount,
        color: meta?.color ?? ASSET_FALLBACK_COLORS[index % ASSET_FALLBACK_COLORS.length],
      };
    })
    .filter((item) => item.amount > 0)
    .sort((first, second) => second.amount - first.amount),
);

const categoryTotal = computed(() =>
  categories.value.reduce((sum, item) => sum + item.amount, 0),
);

const categoryRate = (amount) => {
  const denominator = totalHoldings.value > 0
    ? totalHoldings.value
    : categoryTotal.value;

  if (denominator <= 0) {
    return 0;
  }

  return Math.round((amount / denominator) * 100);
};

const hoveredCategoryIndex = ref(null);
const hoveredCategory = computed(() => {
  if (hoveredCategoryIndex.value === null) {
    return null;
  }

  return categories.value[hoveredCategoryIndex.value] ?? null;
});

const hasCategoryData = computed(() => categories.value.length > 0);

const chartData = computed(() => ({
  labels: categories.value.map((item) => item.label),
  datasets: [
    {
      data: categories.value.map((item) => item.amount),
      backgroundColor: categories.value.map((item) => item.color),
      borderColor: "#ffffff",
      borderWidth: 4,
      hoverOffset: 5,
    },
  ],
}));

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  cutout: "70%",
  onHover: (_event, activeElements) => {
    hoveredCategoryIndex.value = activeElements[0]?.index ?? null;
  },
  plugins: {
    legend: {
      display: false,
    },
    tooltip: {
      enabled: false,
    },
  },
};
</script>

<template>
  <article class="card asset-overview-card border-0 shadow-sm">
    <div class="card-body asset-overview-body">
      <RouterLink
        to="/users/profile/connections"
        class="btn connection-management-button"
      >
        연동관리
        <i class="bi bi-gear ms-1" aria-hidden="true"></i>
      </RouterLink>

      <div class="row align-items-center g-4 g-xl-5 asset-content-row">
        <section class="col-lg-4 asset-summary-panel" aria-label="자산 금액 요약">
          <h2 class="h5 fw-bold mb-3">자산 한눈에 보기</h2>
          <div class="asset-summary-content">
            <p class="asset-total-label mb-1">총 보유자산</p>
            <strong class="asset-total d-block">{{ formatWon(totalHoldings) }}</strong>
            <div class="asset-balance-summary mt-3">
              <div>
                <span>순자산</span>
                <strong>{{ formatWon(totalAssets) }}</strong>
              </div>
              <div>
                <span>대출(부채)</span>
                <strong class="loan-balance">{{ formatWon(loanBalance) }}</strong>
              </div>
            </div>
          </div>
        </section>

        <section class="col-lg-8 asset-visual-panel" aria-label="카테고리별 자산 구성">
          <div v-if="hasCategoryData" class="row align-items-center g-4">
            <div class="col-md-5" aria-label="자산 카테고리 비율 차트">
              <div class="asset-doughnut-chart">
                <Doughnut :data="chartData" :options="chartOptions" />
                <div class="asset-doughnut-center">
                  <template v-if="hoveredCategory">
                    <strong>{{ hoveredCategory.label }}</strong>
                    <span>{{ categoryRate(hoveredCategory.amount) }}%</span>
                    <small>{{ formatWon(hoveredCategory.amount) }}</small>
                  </template>
                  <template v-else>
                    <span>자산 비중</span>
                    <strong>전체</strong>
                  </template>
                </div>
              </div>
            </div>

            <div class="col-md-7" aria-label="카테고리별 금액과 비율">
              <ul class="asset-category-list list-unstyled mb-0">
                <li v-for="category in categories" :key="category.category">
                  <span class="asset-category-label d-flex align-items-center gap-2">
                    <span
                      class="asset-category-dot"
                      :style="{ backgroundColor: category.color }"
                      aria-hidden="true"
                    ></span>
                    {{ category.label }}
                  </span>
                  <span class="asset-category-rate">{{ categoryRate(category.amount) }}%</span>
                  <strong>{{ formatWon(category.amount) }}</strong>
                </li>
              </ul>
            </div>
          </div>

          <div v-else class="asset-empty-state text-center text-secondary">
            <i class="bi bi-pie-chart fs-2" aria-hidden="true"></i>
            <p class="mb-0 mt-2">
              자산을 연동하면 카테고리별 금액과 비율을 확인할 수 있습니다.
            </p>
          </div>
        </section>
      </div>
    </div>
  </article>
</template>

<style scoped>
.asset-overview-card {
  width: 100%;
  border-radius: 32px;
  background: #ffffff;
}

.asset-overview-body {
  position: relative;
  min-height: 310px;
  padding: 36px 42px;
}

.asset-content-row {
  min-height: 238px;
}

.asset-summary-panel {
  align-self: flex-start;
  padding-top: 0;
  padding-bottom: 12px;
}

.asset-summary-content,
.asset-visual-panel {
  transform: translateY(10px);
}

.connection-management-button {
  position: absolute;
  z-index: 1;
  top: 36px;
  right: 42px;
  border: 1px solid #8170ff;
  border-radius: 12px;
  color: #6b5bd2;
  background: #ffffff;
  font-weight: 600;
}

.connection-management-button:hover,
.connection-management-button:focus {
  border-color: #8170ff;
  color: #ffffff;
  background: #8170ff;
}

.asset-total {
  color: #000000;
  font-size: clamp(1.75rem, 3vw, 2.25rem);
}

.asset-total-label {
  color: #6c757d;
  font-size: 0.95rem;
  font-weight: 600;
}

.asset-balance-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.asset-balance-summary > div {
  display: flex;
  min-width: 145px;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.asset-balance-summary span {
  color: #70768a;
  font-size: 0.95rem;
  font-weight: 600;
}

.asset-balance-summary strong {
  color: #343044;
  font-size: 1.05rem;
  white-space: nowrap;
}

.asset-balance-summary .loan-balance {
  color: #dc3545;
}

.asset-doughnut-chart {
  position: relative;
  height: 190px;
}

.asset-doughnut-center {
  position: absolute;
  top: 50%;
  left: 50%;
  color: #6c757d;
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.45;
  text-align: center;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.asset-doughnut-center strong,
.asset-doughnut-center span,
.asset-doughnut-center small {
  display: block;
  white-space: nowrap;
}

.asset-doughnut-center strong {
  color: #343044;
  font-size: 0.95rem;
}

.asset-doughnut-center span {
  color: #6b5bd2;
}

.asset-doughnut-center small {
  color: #6c757d;
  font-size: 0.75rem;
}

.asset-category-list {
  display: grid;
  gap: 12px;
  transform: translateY(18px);
}

.asset-category-list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 44px 118px;
  padding-bottom: 10px;
  border-bottom: 1px solid #edf0f5;
  color: #111111;
}

.asset-category-list li:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.asset-category-label {
  min-width: 0;
  font-weight: 600;
}

.asset-category-dot {
  display: inline-flex;
  width: 13px;
  height: 13px;
  flex: 0 0 13px;
  border-radius: 50%;
}

.asset-category-rate {
  color: #6c757d;
  font-size: 0.9rem;
  text-align: right;
}

.asset-category-list strong {
  text-align: right;
  white-space: nowrap;
}

.asset-empty-state {
  display: flex;
  min-height: 180px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

@media (max-width: 991.98px) {
  .asset-overview-body {
    min-height: auto;
    padding: 32px;
  }

  .asset-summary-panel {
    padding-bottom: 4px;
  }
}

@media (max-width: 575.98px) {
  .asset-overview-body {
    padding: 26px 22px;
  }

  .connection-management-button {
    top: 26px;
    right: 22px;
  }

  .asset-summary-panel h2 {
    padding-right: 112px;
  }

  .asset-category-list li {
    grid-template-columns: minmax(0, 1fr) 40px auto;
  }

  .asset-category-list strong {
    font-size: 0.9rem;
  }
}
</style>
