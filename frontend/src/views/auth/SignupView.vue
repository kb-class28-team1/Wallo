<script setup>
import { RouterLink, useRouter } from "vue-router"

const router = useRouter()

// 임시 화면이므로 회원가입 처리 없이 로그인 페이지로 이동함
const completeSignup = () => {
  router.push("/login")
}
</script>

<template>
  <main class="d-flex min-vh-100 align-items-center justify-content-center bg-light p-3">
    <section class="card w-100 border-0 p-4 text-center shadow-sm" style="max-width: 480px">
      <div class="card-body">
        <h1 class="mb-3 fw-bold">회원가입 페이지</h1>
        <p class="mb-4 text-secondary">회원가입 입력 화면이 들어갈 예정임</p>

        <div class="d-grid gap-2">
          <button type="button" class="btn btn-primary" @click="completeSignup">
            회원가입 완료
          </button>
          <RouterLink to="/login" class="btn btn-outline-secondary">로그인으로 돌아가기</RouterLink>
        </div>
      </div>
    </section>
  </main>
</template>
