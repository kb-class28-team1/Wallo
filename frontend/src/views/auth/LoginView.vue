<script setup>
import { computed, ref } from "vue"
import { RouterLink, useRoute, useRouter } from "vue-router"
import { useUserStore } from "@/stores/userStore"

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const email = ref("")
const password = ref("")
const errorMessage = ref("")

const sessionMessage = computed(() =>
  route.query.reason === "expired" ? "로그인이 필요하거나 세션이 만료되었습니다." : "",
)

const handleLogin = async () => {
  errorMessage.value = ""

  if (!email.value.trim() || !password.value) {
    errorMessage.value = "이메일과 비밀번호를 모두 입력해주세요."
    return
  }

  try {
    await userStore.login({
      email: email.value.trim(),
      password: password.value,
    })

    const redirectPath =
      typeof route.query.redirect === "string" && route.query.redirect.startsWith("/")
        ? route.query.redirect
        : "/dashboard"
    await router.replace(redirectPath)
  } catch (error) {
    errorMessage.value = error.message || "로그인에 실패했습니다."
  } finally {
    password.value = ""
  }
}
</script>

<template>
  <main class="auth-page d-flex min-vh-100 align-items-center justify-content-center p-3">
    <section class="auth-card card w-100 border-0 p-3 p-sm-4 shadow-sm">
      <div class="card-body">
        <p class="mb-2 text-center fs-1" aria-hidden="true">🐧</p>
        <h1 class="mb-2 text-center fw-bold">로그인</h1>
        <p class="mb-4 text-center text-secondary">Wallo에서 절약 습관을 이어가세요.</p>

        <div v-if="sessionMessage" class="alert alert-warning" role="status">
          {{ sessionMessage }}
        </div>
        <div v-if="errorMessage" class="alert alert-danger" role="alert">
          {{ errorMessage }}
        </div>

        <form @submit.prevent="handleLogin">
          <div class="mb-3">
            <label for="login-email" class="form-label">이메일</label>
            <input
              id="login-email"
              v-model="email"
              type="email"
              class="form-control"
              autocomplete="email"
              placeholder="test@wallo.com"
              required
            />
          </div>

          <div class="mb-4">
            <label for="login-password" class="form-label">비밀번호</label>
            <input
              id="login-password"
              v-model="password"
              type="password"
              class="form-control"
              autocomplete="current-password"
              placeholder="비밀번호를 입력하세요"
              required
            />
          </div>

          <button class="btn btn-primary w-100" type="submit" :disabled="userStore.isLoading">
            <span
              v-if="userStore.isLoading"
              class="spinner-border spinner-border-sm me-2"
              aria-hidden="true"
            />
            {{ userStore.isLoading ? "로그인 중..." : "로그인" }}
          </button>
        </form>

        <p class="mt-4 mb-0 text-center text-secondary">
          아직 계정이 없나요?
          <RouterLink to="/signup" class="fw-semibold">회원가입</RouterLink>
        </p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  background: linear-gradient(135deg, #f4f2ff 0%, #f8fbff 100%);
}

.auth-card {
  max-width: 480px;
  border-radius: 24px;
}
</style>
