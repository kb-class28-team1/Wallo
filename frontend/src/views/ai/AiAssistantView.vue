<script setup>
import { useRouter } from "vue-router"

const router = useRouter()

const startConsulting = async () => {
  await router.push({ name: "chat" })
}
</script>

<template>
  <section class="consulting-page d-flex align-items-center justify-content-center">
    <div class="consulting-card card w-100 border-0 text-center shadow-sm">
      <div
        class="card-body d-flex flex-column align-items-center justify-content-center p-5"
      >
        <span class="badge rounded-pill text-bg-light mb-3 px-3 py-2">
          Wallo AI
        </span>
        <h1 class="mb-3 fw-bold">AI 금융 컨설팅</h1>
        <p class="mb-4 text-secondary">
          현재 자산과 재무 목표에 맞는 상담을 시작해 보세요.
        </p>
        <button
          type="button"
          class="btn btn-primary btn-lg px-5"
          @click="startConsulting"
        >
          금융 컨설팅 시작하기
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.consulting-page {
  min-height: calc(100vh - 132px);
}

.consulting-card {
  min-height: 420px;
  border-radius: 28px;
}

.btn-primary {
  --bs-btn-bg: #7062de;
  --bs-btn-border-color: #7062de;
  --bs-btn-hover-bg: #5f50d2;
  --bs-btn-hover-border-color: #5f50d2;
}
</style>
