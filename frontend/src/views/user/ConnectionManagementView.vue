<script setup>
import { RouterLink } from "vue-router";
</script>

<template>
  <section class="settings-panel card border-0 shadow-sm" aria-labelledby="connection-settings-title">
    <div class="card-body p-4 p-md-5">
      <h2 id="connection-settings-title" class="h5 fw-bold mb-4">연결된 계좌·카드</h2>

      <div class="connection-section-heading d-flex align-items-center justify-content-between gap-3">
        <span class="small fw-semibold text-secondary">연결된 계좌·카드</span>
        <span class="small text-secondary">관리</span>
      </div>

      <div class="connection-empty-state text-center">
        <i class="bi bi-wallet2 fs-2 text-secondary" aria-hidden="true"></i>
        <p class="fw-semibold mb-1 mt-3">연결된 계좌·카드 정보</p>
        <p class="small text-secondary mb-4">
          연결 목록 API가 연결되면 계좌와 카드 정보를 이곳에서 관리할 수 있습니다.
        </p>
        <RouterLink to="/connections/mydata" class="btn btn-primary">
          <i class="bi bi-plus-lg me-1" aria-hidden="true"></i>
          계좌·카드 연동 추가
        </RouterLink>
      </div>
    </div>
  </section>
</template>

<style scoped>
.settings-panel {
  border-radius: 20px;
  background: #ffffff;
}

.connection-section-heading {
  padding: 0 0 12px;
  border-bottom: 1px solid #eef0f5;
}

.connection-empty-state {
  padding: 72px 16px 56px;
}
</style>
