<template>
  <main class="d-flex min-vh-100 align-items-center justify-content-center bg-light p-3">
    <section class="card w-100 border-0 p-4 text-center shadow-sm" style="max-width: 480px">
      <div class="card-body">
        <h1 class="mb-3 fw-bold">왈로 랜딩 페이지</h1>
        <p class="mb-4 text-secondary">왈로에 오신 것을 환영함</p>
        <RouterLink to="/login" class="btn btn-primary px-4">로그인 페이지로 이동</RouterLink>
      </div>
    </section>
  </main>
</template>

<script setup>
import { RouterLink } from "vue-router"
</script>
