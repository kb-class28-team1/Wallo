<script setup>
import { RouterLink } from "vue-router";
</script>

<template>
  <section class="expense-history-view container-fluid py-4 px-4">
    <header class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-4">
      <div>
        <h1 class="h3 fw-bold mb-1">소비내역</h1>
        <p class="text-secondary mb-0">이번 달 소비 기록을 확인하세요.</p>
      </div>
      <RouterLink to="/assets" class="btn btn-outline-primary">
        자산 관리
        <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
      </RouterLink>
    </header>

    <article class="card border-0 shadow-sm expense-history-card">
      <div class="card-body p-4 text-center py-5">
        <i class="bi bi-receipt fs-1 text-secondary" aria-hidden="true"></i>
        <p class="text-secondary mt-3 mb-0">
          소비내역 조회 API가 연결되면 날짜·카테고리별 거래 목록을 이 영역에 표시합니다.
        </p>
      </div>
    </article>
  </section>
</template>

<style scoped>
.expense-history-card {
  max-width: 900px;
  border-radius: 20px;
}
</style>
