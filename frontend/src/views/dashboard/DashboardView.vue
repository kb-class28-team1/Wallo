<script setup>
import { computed, onMounted } from "vue";
import { storeToRefs } from "pinia";
import AssetSummaryCard from "@/components/dashboard/AssetSummaryCard.vue";
import BudgetSummaryCard from "@/components/dashboard/BudgetSummaryCard.vue";
import ExpenseSummaryCard from "@/components/dashboard/ExpenseSummaryCard.vue";
import GoalSummaryCard from "@/components/dashboard/GoalSummaryCard.vue";
import { useDashboardCharts } from "@/features/financial/useDashboardCharts";
import { useDashboardStore } from "@/stores/useDashboardStore";
import { useGoalStore } from "@/stores/goalStore";

const dashboardStore = useDashboardStore();
const goalStore = useGoalStore();
const {
  isLoading,
  assets,
  budget,
  expenses,
  error,
} = storeToRefs(dashboardStore);
const {
  goals,
  isLoading: isGoalLoading,
  error: goalError,
} = storeToRefs(goalStore);
const { assetTrendChartData, expenseChartData } = useDashboardCharts(assets, expenses);

const hasDashboardData = computed(() => Boolean(
  assets.value ||
  budget.value ||
  expenses.value ||
  goals.value.length > 0 ||
  isGoalLoading.value ||
  goalError.value,
));

const handleBudgetSave = async (totalAmount) => {
  await dashboardStore.updateBudgetTotal(totalAmount);
};

const handleGoalRetry = () => {
  goalStore.fetchGoals();
};

onMounted(() => {
  dashboardStore.fetchDashboardSummary();
  goalStore.fetchGoals();
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

      <GoalSummaryCard
        :goals="goals"
        :loading="isGoalLoading"
        :error="goalError"
        class="mb-4"
        @retry="handleGoalRetry"
      />

      <div class="dashboard-card-grid">
        <AssetSummaryCard :assets="assets" :chart-data="assetTrendChartData" />
        <BudgetSummaryCard :budget="budget" @save-budget="handleBudgetSave" />
      </div>

      <ExpenseSummaryCard :expenses="expenses" :chart-data="expenseChartData" />
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
}

@media (max-width: 991.98px) {
  .dashboard-card-grid {
    grid-template-columns: 1fr;
    gap: 24px;
  }
}
</style>
