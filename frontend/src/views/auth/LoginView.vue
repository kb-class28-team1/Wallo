<script setup>
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/userStore'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const email = ref('')
const password = ref('')
const errors = reactive({ email: '', password: '' })

const sessionMessage = computed(() =>
  route.query.reason === 'expired' ? '로그인이 필요하거나 세션이 만료되었습니다.' : '',
)

const handleLogin = async () => {
  errors.email = ''
  errors.password = ''

  if (!email.value.trim()) errors.email = '이메일을 입력해 주세요.'
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim()))
    errors.email = '올바른 이메일 형식을 입력해 주세요.'
  if (!password.value) errors.password = '비밀번호를 입력해 주세요.'
  if (errors.email || errors.password) return

  try {
    const authenticatedUser = await userStore.login({
      email: email.value.trim(),
      password: password.value,
    })

    const redirectPath = !authenticatedUser.connectionCompleted
      ? '/connections/mydata'
      : typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
        ? route.query.redirect
        : '/dashboard'
    await router.replace(redirectPath)
  } catch (error) {
    if (error.code === 'AUTH_LOGIN_EMAIL_NOT_FOUND') {
      errors.email = error.message || '아이디가 틀렸습니다.'
    } else if (error.code === 'AUTH_LOGIN_PASSWORD_MISMATCH') {
      errors.password = error.message || '비밀번호가 틀렸습니다.'
    } else {
      errors.email = error.message || '로그인에 실패했습니다.'
    }
  }
}

const clearError = (field) => {
  errors[field] = ''
}
</script>

<template>
  <main class="auth-page d-flex min-vh-100 align-items-center justify-content-center p-3">
    <section class="auth-card card w-100 border-0 p-3 p-sm-4 shadow-sm">
      <div class="card-body">
        <img
          class="auth-logo"
          src="/images/profiles/penguin-login.svg"
          alt="Wallo 로그인 로고"
        />
        <h1 class="mb-2 text-center fw-bold">로그인</h1>
        <p class="mb-4 text-center text-secondary">Wallo에서 절약 습관을 이어가세요.</p>

        <div v-if="sessionMessage" class="alert alert-warning" role="status">
          {{ sessionMessage }}
        </div>
        <form novalidate @submit.prevent="handleLogin">
          <div class="mb-3">
            <label for="login-email" class="form-label">이메일</label>
            <input
              id="login-email"
              v-model="email"
              type="email"
              :class="['form-control', { 'is-invalid field-shake': errors.email }]"
              autocomplete="email"
              placeholder="test@wallo.com"
              :aria-describedby="errors.email ? 'login-email-error' : undefined"
              @input="clearError('email')"
            />
            <small v-if="errors.email" id="login-email-error" class="field-error">
              {{ errors.email }}
            </small>
          </div>

          <div class="mb-4">
            <label for="login-password" class="form-label">비밀번호</label>
            <input
              id="login-password"
              v-model="password"
              type="password"
              :class="['form-control', { 'is-invalid field-shake': errors.password }]"
              autocomplete="current-password"
              placeholder="비밀번호를 입력하세요"
              :aria-describedby="errors.password ? 'login-password-error' : undefined"
              @input="clearError('password')"
            />
            <small v-if="errors.password" id="login-password-error" class="field-error">
              {{ errors.password }}
            </small>
          </div>

          <button class="btn btn-primary w-100" type="submit" :disabled="userStore.isLoading">
            <span
              v-if="userStore.isLoading"
              class="spinner-border spinner-border-sm me-2"
              aria-hidden="true"
            />
            {{ userStore.isLoading ? '로그인 중...' : '로그인' }}
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

.auth-logo {
  display: block;
  width: 140px;
  height: 140px;
  margin: 0 auto 0.5rem;
  object-fit: contain;
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

@keyframes field-shake {
  0%,
  100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-6px);
  }
  50% {
    transform: translateX(6px);
  }
  75% {
    transform: translateX(-3px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .field-shake {
    animation: none;
  }
}
</style>
