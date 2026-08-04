<script setup>
import { RouterLink } from "vue-router";
</script>

<template>
  <section class="connection-management-view container-fluid px-4 py-4">
    <header class="mb-4">
      <p class="text-primary fw-semibold mb-2">설정</p>
      <h1 class="h3 fw-bold mb-2">자산 연결 관리</h1>
      <p class="text-secondary mb-0">
        연결된 금융기관을 확인하고 새로운 자산을 추가할 수 있습니다.
      </p>
    </header>

    <article class="card border-0 shadow-sm connection-management-card">
      <div class="card-body p-4">
        <div class="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3">
          <div>
            <h2 class="h5 fw-bold mb-2">자산 연결</h2>
            <p class="text-secondary mb-0">
              은행·카드·증권 계정을 연결하여 자산과 거래 내역을 불러옵니다.
            </p>
          </div>

          <RouterLink to="/connections/mydata" class="btn btn-primary flex-shrink-0">
            자산 연결하기
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
