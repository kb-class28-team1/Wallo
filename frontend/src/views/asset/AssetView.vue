<script setup>
import { RouterLink } from "vue-router";
</script>

<template>
  <section class="asset-view container-fluid py-4 px-4">
    <header class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-4">
      <div>
        <h1 class="h3 fw-bold mb-1">자산 관리</h1>
        <p class="text-secondary mb-0">연동된 계좌, 카드, 증권 자산을 확인하세요.</p>
      </div>
      <RouterLink to="/assets/expenses" class="btn btn-outline-primary">
        소비내역 보기
        <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
      </RouterLink>
    </header>

    <article class="card border-0 shadow-sm asset-overview-card">
      <div class="card-body p-4">
        <h2 class="h5 fw-bold mb-2">연동 자산 현황</h2>
        <p class="text-secondary mb-0">
          자산 상세 조회 API가 연결되면 계좌, 카드, 증권별 자산 목록을 이 영역에 표시합니다.
        </p>
      </div>
    </article>
  </section>
</template>

<style scoped>
.asset-overview-card {
  max-width: 900px;
  border-radius: 20px;
}
</style>
