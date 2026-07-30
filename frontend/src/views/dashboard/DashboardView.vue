<script setup>
import { computed, onMounted } from "vue";
import { storeToRefs } from "pinia";
import { useDashboardStore } from "@/stores/useDashboardStore";

const dashboardStore = useDashboardStore();
const {
  isLoading,
  assets,
  budget,
  expenses,
  error,
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

  const direction = assetChangeAmount.value > 0 ? "증가" : "감소";

  return `지난달 대비 ${formatCurrency(Math.abs(assetChangeAmount.value))} ${direction}`;
});
const assetChangeClass = computed(() => (
  assetChangeAmount.value !== null && assetChangeAmount.value < 0
    ? "text-danger"
    : "text-primary"
));
const assetChangeIcon = computed(() => (
  assetChangeAmount.value >= 0
    ? "bi bi-arrow-up-right"
    : "bi bi-arrow-down-right"
));

const formatCurrency = (amount = 0) => new Intl.NumberFormat("ko-KR", {
  style: "currency",
  currency: "KRW",
  maximumFractionDigits: 0,
}).format(amount);

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
        <div class="card-body p-4">
          <div class="d-flex flex-wrap justify-content-between gap-3">
            <div>
              <p class="text-secondary fw-semibold mb-2">총 자산</p>
              <strong class="asset-total d-block mb-2">{{ formatCurrency(assets.totalAssets) }}</strong>
              <p
                class="mb-0 fw-semibold"
                :class="assetChangeClass"
              >
                <i
                  v-if="assetChangeAmount !== null"
                  :class="assetChangeIcon"
                  aria-hidden="true"
                ></i>
                {{ assetChangeMessage }}
              </p>
            </div>

            <RouterLink to="/assets" class="btn btn-outline-primary align-self-start">
              자산 현황
              <i class="bi bi-chevron-right ms-1" aria-hidden="true"></i>
            </RouterLink>
          </div>

          <div class="row row-cols-1 row-cols-md-3 g-3 mt-2">
            <div class="col">
              <div class="asset-detail-item">
                <span>계좌</span>
                <strong>{{ assets.accounts?.length ?? 0 }}개</strong>
              </div>
            </div>
            <div class="col">
              <div class="asset-detail-item">
                <span>카드</span>
                <strong>{{ assets.cards?.length ?? 0 }}개</strong>
              </div>
            </div>
            <div class="col">
              <div class="asset-detail-item">
                <span>증권</span>
                <strong>{{ assets.stocks?.length ?? 0 }}개</strong>
              </div>
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
  max-width: 960px;
  border-radius: 16px;
}

.asset-total {
  color: #1e2941;
  font-size: clamp(2rem, 4vw, 2.75rem);
  letter-spacing: -1.5px;
}

.asset-detail-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: 10px;
  color: #59647f;
  background: #f8f9fc;
}

.asset-detail-item strong {
  color: #1e2941;
}
</style>
