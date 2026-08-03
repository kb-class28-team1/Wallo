<script setup>
import { RouterLink } from "vue-router";
</script>

<template>
  <section class="connection-management-view container-fluid px-4 py-4">
    <header class="mb-4">
      <p class="text-primary fw-semibold mb-2">설정</p>
      <h1 class="h3 fw-bold mb-2">연동 관리</h1>
      <p class="text-secondary mb-0">
        금융기관 연결을 추가하거나 현재 연결 상태를 관리할 수 있습니다.
      </p>
    </header>

    <article class="card border-0 shadow-sm connection-management-card">
      <div class="card-body p-4">
        <div class="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3">
          <div>
            <h2 class="h5 fw-bold mb-2">금융기관 연동</h2>
            <p class="text-secondary mb-0">
              새로운 계좌·카드·증권 자산을 연결해 자산 현황을 최신 상태로 유지하세요.
            </p>
          </div>

          <RouterLink to="/connections/mydata" class="btn btn-primary flex-shrink-0">
            자산 연동하기
            <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
          </RouterLink>
        </div>
      </div>
    </article>
  </section>
</template>

<style scoped>
.connection-management-card {
  max-width: 900px;
  border-radius: 20px;
}
</style>
