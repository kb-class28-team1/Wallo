<script setup>
import { computed } from "vue";
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
import { formatWon } from "@/commonUtils/formatters";

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Filler,
);

const props = defineProps({
  assets: {
    type: Object,
    default: () => ({
      totalAssets: 0,
      previousMonthTotalAssets: 0,
    }),
  },
  chartData: {
    type: Object,
    required: true,
  },
});

const assetChangeAmount = computed(() => {
  if (!props.assets.previousMonthTotalAssets) {
    return null;
  }

  return props.assets.totalAssets - props.assets.previousMonthTotalAssets;
});
const assetChangeMessage = computed(() => {
  if (assetChangeAmount.value === null) {
    return "지난달 자산 데이터가 없습니다.";
  }

  if (assetChangeAmount.value === 0) {
    return "±0원";
  }

  const sign = assetChangeAmount.value > 0 ? "+" : "-";

  return `${sign}${formatWon(Math.abs(assetChangeAmount.value))}`;
});
const assetChangeClass = computed(() => (
  assetChangeAmount.value !== null && assetChangeAmount.value < 0
    ? "asset-change-negative"
    : "asset-change-positive"
));
const hasTrendData = computed(() => props.chartData.datasets[0].data.length > 0);

const chartOptions = {
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
</script>

<template>
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
            </p>
          </div>
        </div>

        <div class="col-lg-7">
          <section class="asset-trend-section h-100" aria-label="자산 변동 그래프">
            <div class="asset-status-action">
              <RouterLink to="/assets" class="btn dashboard-action-button">
                자산 현황
                <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
              </RouterLink>
            </div>

            <div v-if="hasTrendData" class="asset-trend-chart">
              <Line :data="chartData" :options="chartOptions" />
            </div>
            <p v-else class="asset-trend-empty text-center text-secondary mb-0">
              자산 변동 데이터가 없습니다.
            </p>
          </section>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.asset-summary-card {
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
  font-size: clamp(1.65rem, 2.7vw, 2.4rem);
  letter-spacing: -1.5px;
  white-space: nowrap;
}

.asset-change {
  margin-top: auto !important;
}

.asset-change-positive {
  color: #ff0000;
}

.asset-change-negative {
  color: #0000d5;
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
