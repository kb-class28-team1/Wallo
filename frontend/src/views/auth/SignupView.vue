<script setup>
import { reactive, ref } from "vue"
import { RouterLink, useRouter } from "vue-router"
import { signup } from "@/api/authApi"

const router = useRouter()
const form = reactive({
  name: "",
  nickname: "",
  email: "",
  password: "",
  passwordConfirm: "",
})
const isSubmitting = ref(false)
const errorMessage = ref("")

const validate = () => {
  if (
    !form.name.trim() ||
    !form.nickname.trim() ||
    !form.email.trim() ||
    !form.password ||
    !form.passwordConfirm
  ) {
    return "모든 항목을 입력해주세요."
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    return "올바른 이메일 형식을 입력해주세요."
  }
  if (form.password.length < 8 || form.password.length > 72) {
    return "비밀번호는 8자 이상 72자 이하로 입력해주세요."
  }
  if (form.password !== form.passwordConfirm) {
    return "비밀번호와 비밀번호 확인이 일치하지 않습니다."
  }
  return ""
}

const handleSignup = async () => {
  errorMessage.value = validate()
  if (errorMessage.value) {
    return
  }

  isSubmitting.value = true
  try {
    await signup({
      name: form.name.trim(),
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      password: form.password,
    })
    alert("회원가입이 완료되었습니다. 로그인해주세요.")
    await router.replace("/login")
  } catch (error) {
    errorMessage.value = error.message || "회원가입에 실패했습니다."
  } finally {
    isSubmitting.value = false
    form.password = ""
    form.passwordConfirm = ""
  }
}
</script>

<template>
  <main class="auth-page d-flex min-vh-100 align-items-center justify-content-center p-3">
    <section class="auth-card card w-100 border-0 p-3 p-sm-4 shadow-sm">
      <div class="card-body">
        <p class="mb-2 text-center fs-1" aria-hidden="true">🐧</p>
        <h1 class="mb-2 text-center fw-bold">회원가입</h1>
        <p class="mb-4 text-center text-secondary">Wallo와 함께 절약을 시작해보세요.</p>

        <div v-if="errorMessage" class="alert alert-danger" role="alert">
          {{ errorMessage }}
        </div>

        <form @submit.prevent="handleSignup">
          <div class="mb-3">
            <label for="signup-name" class="form-label">이름</label>
            <input
              id="signup-name"
              v-model="form.name"
              type="text"
              maxlength="50"
              class="form-control"
              autocomplete="name"
              required
            />
          </div>

          <div class="mb-3">
            <label for="signup-nickname" class="form-label">닉네임</label>
            <input
              id="signup-nickname"
              v-model="form.nickname"
              type="text"
              maxlength="50"
              class="form-control"
              autocomplete="nickname"
              required
            />
          </div>

          <div class="mb-3">
            <label for="signup-email" class="form-label">이메일</label>
            <input
              id="signup-email"
              v-model="form.email"
              type="email"
              maxlength="255"
              class="form-control"
              autocomplete="email"
              placeholder="test@wallo.com"
              required
            />
          </div>

          <div class="mb-3">
            <label for="signup-password" class="form-label">비밀번호</label>
            <input
              id="signup-password"
              v-model="form.password"
              type="password"
              class="form-control"
              autocomplete="new-password"
              minlength="8"
              maxlength="72"
              required
            />
            <div class="form-text">8자 이상 72자 이하로 입력해주세요.</div>
          </div>

          <div class="mb-4">
            <label for="signup-password-confirm" class="form-label">비밀번호 확인</label>
            <input
              id="signup-password-confirm"
              v-model="form.passwordConfirm"
              type="password"
              class="form-control"
              autocomplete="new-password"
              minlength="8"
              maxlength="72"
              required
            />
          </div>

          <button class="btn btn-primary w-100" type="submit" :disabled="isSubmitting">
            <span
              v-if="isSubmitting"
              class="spinner-border spinner-border-sm me-2"
              aria-hidden="true"
            />
            {{ isSubmitting ? "가입 중..." : "회원가입" }}
          </button>
        </form>

        <p class="mt-4 mb-0 text-center text-secondary">
          이미 계정이 있나요?
          <RouterLink to="/login" class="fw-semibold">로그인</RouterLink>
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
  max-width: 520px;
  border-radius: 24px;
}
</style>
