<script setup>
import { RouterLink } from "vue-router"
</script>

<template>
  <section class="container-fluid p-4">
    <div class="card border-0 shadow-sm">
      <div class="card-body p-4">
        <h1 class="mb-3 fw-bold">설정 페이지</h1>
        <p class="mb-4 text-secondary">사용자 설정 화면이 들어갈 예정임</p>
        <RouterLink to="/api/home" class="btn btn-primary">대시보드로 이동</RouterLink>
      </div>
    </div>
  </section>
</template>
