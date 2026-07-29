<script setup>
import { onMounted } from "vue";
import { useDashboardStore } from "@/stores/useDashboardStore";

const dashboardStore = useDashboardStore();

onMounted(() => {
  dashboardStore.fetchDashboardSummary();
});
</script>

<template>
  <main class="container py-5">
    <div v-if="dashboardStore.isLoading" class="text-center py-5">
      <span class="spinner-border text-primary" aria-label="대시보드 데이터를 불러오는 중"></span>
    </div>
    <p v-else-if="dashboardStore.error" class="text-danger mb-0">
      {{ dashboardStore.error }}
    </p>
    <section v-else>
      <h1 class="h3 fw-bold mb-3">대시보드</h1>
      <p class="text-secondary mb-0">자산 정보를 불러왔습니다.</p>
    </section>
  </main>
</template>
