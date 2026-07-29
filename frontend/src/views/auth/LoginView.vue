<script setup>
import { ref } from "vue"
import { RouterLink, useRouter } from "vue-router"

const router = useRouter()
const userId = ref("")
const password = ref("")

// 임시 로그인 버튼이며 인증 확인 없이 첫 자산 연결 화면으로 이동함
const handleLogin = () => {
  router.push("/connections/mydata")
}

// 기존 사용자가 로그인했다고 가정하고 대시보드로 바로 이동함
const moveToDashboard = () => {
  router.push("/api/home")
}
</script>

<template>
  <main class="d-flex min-vh-100 align-items-center justify-content-center bg-light p-3">
    <section class="card w-100 border-0 p-4 shadow-sm" style="max-width: 480px">
      <div class="card-body">
        <h1 class="mb-4 text-center fw-bold">로그인 페이지</h1>

        <form @submit.prevent="handleLogin">
          <div class="mb-3">
            <label for="user-id" class="form-label">아이디</label>
            <input
              id="user-id"
              v-model="userId"
              type="text"
              class="form-control"
              placeholder="아이디를 입력하세요"
            />
          </div>

          <div class="mb-4">
            <label for="password" class="form-label">비밀번호</label>
            <input
              id="password"
              v-model="password"
              type="password"
              class="form-control"
              placeholder="비밀번호를 입력하세요"
            />
          </div>

          <div class="d-grid gap-2">
            <button type="submit" class="btn btn-primary">로그인</button>
            <button type="button" class="btn btn-outline-primary" @click="moveToDashboard">
              대시보드로 바로 가기
            </button>
            <RouterLink to="/signup" class="btn btn-outline-secondary">회원가입</RouterLink>
          </div>
        </form>
      </div>
    </section>
  </main>
</template>
