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
const showSuccessModal = ref(false)
const errors = reactive({
  name: "",
  nickname: "",
  email: "",
  password: "",
  passwordConfirm: "",
})

const validate = () => {
  Object.keys(errors).forEach((field) => (errors[field] = ""))
  if (!form.name.trim()) errors.name = "이름을 입력해 주세요."
  if (!form.nickname.trim()) errors.nickname = "닉네임을 입력해 주세요."
  if (!form.email.trim()) errors.email = "이메일을 입력해 주세요."
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()))
    errors.email = "올바른 이메일 형식을 입력해 주세요."
  if (!form.password) errors.password = "비밀번호를 입력해 주세요."
  if (form.password.length < 8 || form.password.length > 72) {
    errors.password = "비밀번호는 8자 이상 72자 이하로 입력해 주세요."
  }
  if (!form.passwordConfirm) errors.passwordConfirm = "비밀번호를 한 번 더 입력해 주세요."
  else if (form.password !== form.passwordConfirm)
    errors.passwordConfirm = "비밀번호가 일치하지 않습니다."
  return !Object.values(errors).some(Boolean)
}

const handleSignup = async () => {
  if (!validate()) return

  isSubmitting.value = true
  try {
    await signup({
      name: form.name.trim(),
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      password: form.password,
    })
    showSuccessModal.value = true
  } catch (error) {
    if (error.code === "AUTH_EMAIL_ALREADY_EXISTS") errors.email = error.message
    else if (error.code === "AUTH_NICKNAME_ALREADY_EXISTS") errors.nickname = error.message
    else if (error.code === "AUTH_INVALID_EMAIL") errors.email = error.message
    else if (error.code === "AUTH_INVALID_PASSWORD") errors.password = error.message
    else errors.email = error.message || "회원가입에 실패했습니다."
  } finally {
    isSubmitting.value = false
  }
}

const clearError = (field) => {
  errors[field] = ""
  if (field === "password") errors.passwordConfirm = ""
}

const moveToLogin = async () => {
  showSuccessModal.value = false
  await router.replace("/login")
}
</script>

<template>
  <main class="auth-page d-flex min-vh-100 align-items-center justify-content-center p-3">
    <section class="auth-card card w-100 border-0 p-3 p-sm-4 shadow-sm">
      <div class="card-body">
        <p class="mb-2 text-center fs-1" aria-hidden="true">🐧</p>
        <h1 class="mb-2 text-center fw-bold">회원가입</h1>
        <p class="mb-4 text-center text-secondary">Wallo와 함께 절약을 시작해보세요.</p>

        <form novalidate @submit.prevent="handleSignup">
          <div class="mb-3">
            <label for="signup-name" class="form-label">이름</label>
            <input
              id="signup-name"
              v-model="form.name"
              type="text"
              maxlength="50"
              :class="['form-control', { 'is-invalid field-shake': errors.name }]"
              autocomplete="name"
              :aria-describedby="errors.name ? 'signup-name-error' : undefined"
              @input="clearError('name')"
            />
            <small v-if="errors.name" id="signup-name-error" class="field-error">{{ errors.name }}</small>
          </div>

          <div class="mb-3">
            <label for="signup-nickname" class="form-label">닉네임</label>
            <input
              id="signup-nickname"
              v-model="form.nickname"
              type="text"
              maxlength="50"
              :class="['form-control', { 'is-invalid field-shake': errors.nickname }]"
              autocomplete="nickname"
              @input="clearError('nickname')"
            />
            <small v-if="errors.nickname" class="field-error">{{ errors.nickname }}</small>
          </div>

          <div class="mb-3">
            <label for="signup-email" class="form-label">이메일</label>
            <input
              id="signup-email"
              v-model="form.email"
              type="email"
              maxlength="255"
              :class="['form-control', { 'is-invalid field-shake': errors.email }]"
              autocomplete="email"
              placeholder="test@wallo.com"
              @input="clearError('email')"
            />
            <small v-if="errors.email" class="field-error">{{ errors.email }}</small>
          </div>

          <div class="mb-3">
            <label for="signup-password" class="form-label">비밀번호</label>
            <input
              id="signup-password"
              v-model="form.password"
              type="password"
              :class="['form-control', { 'is-invalid field-shake': errors.password }]"
              autocomplete="new-password"
              minlength="8"
              maxlength="72"
              @input="clearError('password')"
            />
            <small v-if="errors.password" class="field-error">{{ errors.password }}</small>
            <div v-else class="form-text">8자 이상 72자 이하로 입력해주세요.</div>
          </div>

          <div class="mb-4">
            <label for="signup-password-confirm" class="form-label">비밀번호 확인</label>
            <input
              id="signup-password-confirm"
              v-model="form.passwordConfirm"
              type="password"
              :class="['form-control', { 'is-invalid field-shake': errors.passwordConfirm }]"
              autocomplete="new-password"
              minlength="8"
              maxlength="72"
              @input="clearError('passwordConfirm')"
            />
            <small v-if="errors.passwordConfirm" class="field-error">
              {{ errors.passwordConfirm }}
            </small>
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

    <div v-if="showSuccessModal" class="modal-backdrop-custom" role="presentation">
      <section
        class="success-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="signup-success-title"
      >
        <div class="success-icon" aria-hidden="true">✓</div>
        <h2 id="signup-success-title">회원가입이 완료되었어요!</h2>
        <p>이제 로그인하고 Wallo와 함께 절약을 시작해 보세요.</p>
        <button type="button" class="btn btn-primary w-100" autofocus @click="moveToLogin">
          로그인하러 가기
        </button>
      </section>
    </div>
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

.field-error {
  display: block;
  margin-top: 6px;
  color: #dc3545;
  font-size: 0.78rem;
}

.field-shake {
  animation: field-shake 0.35s ease-in-out;
}

.modal-backdrop-custom {
  position: fixed;
  inset: 0;
  z-index: 1055;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(25, 28, 43, 0.52);
  backdrop-filter: blur(3px);
}

.success-modal {
  width: min(100%, 420px);
  padding: 36px;
  text-align: center;
  background: #fff;
  border-radius: 24px;
  box-shadow: 0 24px 70px rgba(20, 22, 38, 0.24);
  animation: modal-in 0.22s ease-out;
}

.success-modal h2 {
  margin: 18px 0 10px;
  font-size: 1.45rem;
  font-weight: 800;
}

.success-modal p {
  margin-bottom: 26px;
  color: #6c757d;
}

.success-icon {
  width: 64px;
  height: 64px;
  display: grid;
  place-items: center;
  margin: 0 auto;
  color: #fff;
  background: #7062dc;
  border-radius: 50%;
  font-size: 1.8rem;
  font-weight: 800;
}

@keyframes field-shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-6px); }
  50% { transform: translateX(6px); }
  75% { transform: translateX(-3px); }
}

@keyframes modal-in {
  from { opacity: 0; transform: translateY(10px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

@media (prefers-reduced-motion: reduce) {
  .field-shake,
  .success-modal { animation: none; }
}
</style>
