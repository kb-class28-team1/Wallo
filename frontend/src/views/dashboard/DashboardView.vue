<script setup>
import { computed, onMounted } from "vue";
import { storeToRefs } from "pinia";
import { Line } from "vue-chartjs";
import {
  CategoryScale,
  Chart as ChartJS,
  Filler,
  LinearScale,
  LineElement,
  PointElement,
  Tooltip,
} from "chart.js";
import { useDashboardStore } from "@/stores/useDashboardStore";

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Filler,
);

const dashboardStore = useDashboardStore();
const {
  isLoading,
  assets,
  budget,
  expenses,
  error,
  assetTrendChartData,
} = storeToRefs(dashboardStore);

const hasDashboardData = computed(() => Boolean(
  assets.value || budget.value || expenses.value,
));
const assetChangeAmount = computed(() => {
  if (!assets.value?.previousMonthTotalAssets) {
    return null;
  }

  return assets.value.totalAssets - assets.value.previousMonthTotalAssets;
});
const assetChangeMessage = computed(() => {
  if (assetChangeAmount.value === null) {
    return "지난달 자산 데이터가 없습니다.";
  }

  if (assetChangeAmount.value === 0) {
    return "지난달과 자산이 동일합니다.";
  }

  const sign = assetChangeAmount.value > 0 ? "+" : "-";

  return `${sign}${formatWon(Math.abs(assetChangeAmount.value))}`;
});
const assetChangeClass = computed(() => (
  assetChangeAmount.value !== null && assetChangeAmount.value < 0
    ? "asset-change-negative"
    : "asset-change-positive"
));
const assetChangeIcon = computed(() => (
  assetChangeAmount.value >= 0
    ? "bi bi-arrow-up-right"
    : "bi bi-arrow-down-right"
));
const hasAssetTrendData = computed(() => (
  assetTrendChartData.value.datasets[0].data.length > 0
));

const formatWon = (amount = 0) => `${new Intl.NumberFormat("ko-KR").format(amount)}원`;

const assetTrendChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      display: false,
    },
    tooltip: {
      callbacks: {
        label: (context) => `총 자산 ${formatWon(context.parsed.y)}`,
      },
    },
  },
  scales: {
    x: {
      grid: {
        display: false,
      },
    },
    y: {
      display: false,
      beginAtZero: false,
    },
  },
};

onMounted(() => {
  dashboardStore.fetchDashboardSummary();
});
</script>

<template>
  <section class="container-fluid py-4 px-4">
    <div v-if="isLoading" class="dashboard-state text-center py-5">
      <div class="spinner-border text-primary" role="status" aria-label="대시보드 데이터 로딩 중"></div>
      <p class="mt-3 mb-0 text-secondary">대시보드 데이터를 불러오는 중입니다.</p>
    </div>

    <div v-else-if="error" class="dashboard-state alert alert-danger mb-0" role="alert">
      {{ error }}
    </div>

    <div v-else-if="!hasDashboardData" class="dashboard-state text-center py-5">
      <i class="bi bi-inbox fs-1 text-secondary" aria-hidden="true"></i>
      <p class="mt-3 mb-0 text-secondary">표시할 대시보드 데이터가 없습니다.</p>
    </div>

    <div v-else>
      <header class="mb-4">
        <h1 class="h3 fw-bold mb-1">대시보드</h1>
        <p class="text-secondary mb-0">자산과 소비 현황을 확인하세요.</p>
      </header>

      <article class="card asset-summary-card border-0 shadow-sm">
        <div class="card-body asset-card-body">
          <div class="asset-card-content row g-4 h-100">
            <div class="col-lg-5 d-flex flex-column">
              <p class="asset-label fw-semibold mb-3">총 자산</p>
              <strong class="asset-total d-block">{{ formatWon(assets.totalAssets) }}</strong>

              <div class="asset-change mt-5">
                <p class="asset-change-label mb-2">지난달 대비</p>
                <p class="mb-0 fw-semibold" :class="assetChangeClass">
                  {{ assetChangeMessage }}
                  <i
                    v-if="assetChangeAmount !== null"
                    :class="assetChangeIcon"
                    class="ms-1"
                    aria-hidden="true"
                  ></i>
                </p>
              </div>
            </div>

            <div class="col-lg-7">
              <section class="asset-trend-section h-100" aria-label="자산 변동 그래프">
                <div class="asset-status-action">
                  <RouterLink to="/assets" class="btn asset-status-button">
                    자산 현황
                    <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
                  </RouterLink>
                </div>

                <div v-if="hasAssetTrendData" class="asset-trend-chart">
                  <Line :data="assetTrendChartData" :options="assetTrendChartOptions" />
                </div>
                <p v-else class="asset-trend-empty text-center text-secondary mb-0">
                  자산 변동 데이터가 없습니다.
                </p>
              </section>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.dashboard-state {
  min-height: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.asset-summary-card {
  max-width: 1080px;
  border-radius: 48px;
  background: #ffffff;
}

.asset-card-body {
  min-height: 328px;
  padding: 42px;
}

.asset-card-content {
  min-height: 244px;
}

.asset-label,
.asset-change-label {
  color: #111111;
  font-size: 1.25rem;
}

.asset-total {
  color: #000000;
  font-size: clamp(2rem, 4vw, 2.75rem);
  letter-spacing: -1.5px;
}

.asset-change-positive {
  color: #ff0000;
}

.asset-change-negative {
  color: #0000d5;
}

.asset-status-button {
  border: 1px solid #0000d5;
  border-radius: 14px;
  color: #0000d5;
  background: #ffffff;
}

.asset-status-button:hover,
.asset-status-button:focus {
  border-color: #0000d5;
  color: #0000d5;
  background: #ffffff;
}

.asset-change {
  margin-top: auto !important;
}

.asset-trend-section {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  min-height: 244px;
}

.asset-status-action {
  align-self: flex-end;
}

.asset-trend-chart {
  height: 178px;
  margin-top: auto;
}

.asset-trend-empty {
  display: flex;
  min-height: 178px;
  align-items: center;
  justify-content: center;
}

@media (max-width: 991.98px) {
  .asset-card-body {
    min-height: auto;
    padding: 30px;
  }

  .asset-trend-section {
    padding-top: 24px;
  }

  .asset-trend-chart {
    margin-top: 24px;
  }
}
</style>
