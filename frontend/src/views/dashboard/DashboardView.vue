<script setup>
import { computed, onMounted, ref } from "vue";
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
const isBudgetConfigured = computed(() => Number(budget.value?.totalAmount ?? 0) > 0);
const budgetUsageRate = computed(() => {
  const totalAmount = Number(budget.value?.totalAmount ?? 0);
  const spentAmount = Number(budget.value?.spentAmount ?? 0);

  if (totalAmount <= 0) {
    return 0;
  }

  return Math.min(100, Math.round((spentAmount / totalAmount) * 100));
});
const budgetRemaining = computed(() => (
  Number(budget.value?.totalAmount ?? 0) - Number(budget.value?.spentAmount ?? 0)
));
const budgetModalVisible = ref(false);
const budgetAmountInput = ref(0);
const currentBudgetMonthLabel = computed(() => `${new Date().getMonth() + 1}월 예산`);
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
const hasAssetTrendData = computed(() => (
  assetTrendChartData.value.datasets[0].data.length > 0
));

const formatWon = (amount = 0) => `${new Intl.NumberFormat("ko-KR").format(amount)}원`;
const formatNumber = (amount = 0) => new Intl.NumberFormat("ko-KR").format(amount);

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

const openBudgetModal = () => {
  budgetAmountInput.value = formatNumber(budget.value?.totalAmount ?? 0);
  budgetModalVisible.value = true;
};

const closeBudgetModal = () => {
  budgetModalVisible.value = false;
};

const saveBudget = () => {
  const amount = Number(String(budgetAmountInput.value).replaceAll(",", ""));

  if (!Number.isFinite(amount) || amount <= 0) {
    alert("예산은 0원보다 큰 금액으로 입력해주세요.");
    return;
  }

  dashboardStore.updateBudgetTotal(amount);
  closeBudgetModal();
};

const formatBudgetAmountInput = () => {
  const numericValue = String(budgetAmountInput.value).replaceAll(/[^0-9]/g, "");

  budgetAmountInput.value = numericValue ? formatNumber(numericValue) : "";
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

      <div class="dashboard-card-grid">
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

      <article class="card budget-summary-card border-0 shadow-sm">
        <div class="card-body budget-card-body">
          <div class="d-flex align-items-start justify-content-between gap-3">
            <h2 class="h5 fw-bold mb-0">이번 달 예산</h2>
            <button type="button" class="btn dashboard-action-button" @click="openBudgetModal">
              설정
              <i class="bi bi-gear ms-1" aria-hidden="true"></i>
            </button>
          </div>

          <template v-if="isBudgetConfigured" class="budget-content">
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
      </div>

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
              <button
                type="button"
                class="btn-close"
                aria-label="닫기"
                @click="closeBudgetModal"
              ></button>
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
  border-radius: 48px;
  background: #ffffff;
}

.dashboard-card-grid {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 3fr);
  gap: 40px;
  max-width: 1080px;
}

.asset-card-body,
.budget-card-body {
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

.budget-summary-card {
  border-radius: 48px;
  background: #ffffff;
}

.budget-content {
  margin-top: 28px;
}

.budget-balance-label,
.budget-detail {
  color: #111111;
}

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

@media (max-width: 991.98px) {
  .dashboard-card-grid {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .asset-card-body,
  .budget-card-body {
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
