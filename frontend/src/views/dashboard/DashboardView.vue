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
      <h1 class="h3 fw-bold mb-1">대시보드</h1>
      <p class="text-secondary mb-0">자산과 소비 현황을 확인하세요.</p>
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
</style>
