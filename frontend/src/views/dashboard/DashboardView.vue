<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { storeToRefs } from "pinia";
import { useRouter } from "vue-router";
import AssetSummaryCard from "@/components/dashboard/AssetSummaryCard.vue";
import BudgetSummaryCard from "@/components/dashboard/BudgetSummaryCard.vue";
import ExpenseSummaryCard from "@/components/dashboard/ExpenseSummaryCard.vue";
import GoalSummaryCard from "@/components/dashboard/GoalSummaryCard.vue";
import { useDashboardCharts } from "@/features/financial/useDashboardCharts";
import { useDashboardStore } from "@/stores/useDashboardStore";
import { useGoalStore } from "@/stores/goalStore";
import { useUserStore } from "@/stores/userStore";

const dashboardStore = useDashboardStore();
const goalStore = useGoalStore();
const userStore = useUserStore();
const router = useRouter();
const {
  initialLoading,
  refreshing,
  assets,
  budget,
  expenses,
  error,
} = storeToRefs(dashboardStore);
const {
  goals,
  initialLoading: isGoalInitialLoading,
  refreshing: isGoalRefreshing,
  error: goalError,
  availableAccounts,
  isAccountLoading,
} = storeToRefs(goalStore);
const { user } = storeToRefs(userStore);
const { assetTrendChartData, expenseChartData } = useDashboardCharts(assets, expenses);

const GOAL_REFRESH_INTERVAL_MS = 5 * 60 * 1000;
const isDashboardReady = ref(false);
const hadDashboardDataBeforeLoad = ref(false);
const userId = computed(() => user.value?.id ?? null);
let goalRefreshTimer = null;
let goalRefreshInFlight = null;

const hasRenderedDashboardData = computed(() => Boolean(
  assets.value ||
  budget.value ||
  expenses.value ||
  goals.value.length > 0
));

const isDashboardInitialLoading = computed(() => (
  !hadDashboardDataBeforeLoad.value &&
  (!isDashboardReady.value || initialLoading.value || isGoalInitialLoading.value)
));

const isDashboardRefreshing = computed(() => (
  refreshing.value || isGoalRefreshing.value
));

const hasDashboardData = computed(() => Boolean(
  hasRenderedDashboardData.value ||
  isGoalInitialLoading.value ||
  goalError.value,
));

const handleBudgetSettings = async () => {
  await router.push({
    name: "expenses",
    query: { budget: "edit" },
  });
};

const handleGoalRetry = () => {
  goalStore.fetchGoals({
    userId: userId.value,
    force: true,
    syncAccounts: false,
  });
};

const refreshGoalData = ({ refreshDashboard = false } = {}) => {
  if (goalRefreshInFlight) {
    return goalRefreshInFlight;
  }

  goalRefreshInFlight = (async () => {
    const goalRequest = goalStore.fetchGoals({
      userId: userId.value,
      notifyError: false,
      syncAccounts: false,
    });
    const accountRequest = goalRequest.then((loadedGoals) => {
      if (!loadedGoals.length) {
        return [];
      }

      return goalStore.fetchAvailableAccounts({ notifyError: false });
    });

    if (refreshDashboard) {
      await Promise.all([
        accountRequest,
        dashboardStore.fetchDashboardSummary(),
      ]);
      return;
    }
    await accountRequest;
  })().finally(() => {
    goalRefreshInFlight = null;
  });

  return goalRefreshInFlight;
};

const loadDashboard = async () => {
  hadDashboardDataBeforeLoad.value = hasRenderedDashboardData.value;
  isDashboardReady.value = false;
  // 대시보드는 저장된 데이터를 먼저 읽고, CODEF 동기화와 분리해 렌더링한다.
  try {
    await Promise.all([
      refreshGoalData(),
      dashboardStore.fetchDashboardSummary(),
    ]);
  } finally {
    isDashboardReady.value = true;
  }
};

const handleVisibilityChange = () => {
  if (document.visibilityState === "visible") {
    refreshGoalData({ refreshDashboard: true });
  }
};

onMounted(() => {
  loadDashboard();
  goalRefreshTimer = window.setInterval(
    () => refreshGoalData({ refreshDashboard: true }),
    GOAL_REFRESH_INTERVAL_MS,
  );
  document.addEventListener("visibilitychange", handleVisibilityChange);
});

onBeforeUnmount(() => {
  if (goalRefreshTimer !== null) {
    window.clearInterval(goalRefreshTimer);
    goalRefreshTimer = null;
  }
  document.removeEventListener("visibilitychange", handleVisibilityChange);
});
</script>

<template>
  <section class="container-fluid py-4 px-4">
    <div v-if="isDashboardInitialLoading" class="dashboard-state text-center py-5">
      <div class="spinner-border text-primary" role="status" aria-label="대시보드 데이터 로딩 중"></div>
      <p class="mt-3 mb-0 text-secondary">대시보드 데이터를 불러오는 중입니다.</p>
    </div>

    <div v-else-if="error && !hasRenderedDashboardData" class="dashboard-state alert alert-danger mb-0" role="alert">
      {{ error }}
    </div>

    <div v-else-if="!hasDashboardData" class="dashboard-state text-center py-5">
      <i class="bi bi-inbox fs-1 text-secondary" aria-hidden="true"></i>
      <p class="mt-3 mb-0 text-secondary">표시할 대시보드 데이터가 없습니다.</p>
    </div>

    <div v-else>
      <header class="mb-4">
        <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
          <div>
            <h1 class="h3 fw-bold mb-1">대시보드</h1>
            <p class="text-secondary mb-0">자산과 소비 현황을 확인하세요.</p>
          </div>
          <span v-if="isDashboardRefreshing" class="small text-secondary" role="status">
            <span class="spinner-border spinner-border-sm text-primary me-2" aria-hidden="true"></span>
            최신 정보 갱신 중
          </span>
        </div>
        <div v-if="error" class="alert alert-warning mt-3 mb-0" role="alert">
          최신 대시보드 정보를 갱신하지 못했습니다. 기존 정보를 표시하고 있습니다.
        </div>
      </header>

      <div class="dashboard-card-grid">
        <AssetSummaryCard :assets="assets" :chart-data="assetTrendChartData" />
        <BudgetSummaryCard :budget="budget" @open-budget-settings="handleBudgetSettings" />
      </div>

      <div class="dashboard-summary-grid">
        <ExpenseSummaryCard :expenses="expenses" :chart-data="expenseChartData" />
        <GoalSummaryCard
          :goals="goals"
          :loading="isGoalInitialLoading"
          :error="goalError"
          :available-accounts="availableAccounts"
          :accounts-loading="isAccountLoading"
          @retry="handleGoalRetry"
        />
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

.dashboard-card-grid {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 3fr);
  gap: 40px;
  max-width: 1080px;
  margin-top: 40px;
}

.dashboard-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 40px;
  max-width: 1080px;
  margin-top: 40px;
}

@media (max-width: 991.98px) {
  .dashboard-card-grid {
    grid-template-columns: 1fr;
    gap: 24px;
    margin-top: 24px;
  }

  .dashboard-summary-grid {
    grid-template-columns: 1fr;
    gap: 24px;
    margin-top: 24px;
  }
}
</style>
