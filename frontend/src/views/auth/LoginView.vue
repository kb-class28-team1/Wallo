<script setup>
import { computed, reactive, ref } from "vue"
import { RouterLink, useRoute, useRouter } from "vue-router"
import { useUserStore } from "@/stores/userStore"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppFormField from "@/components/ui/AppFormField.vue"

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const email = ref("")
const password = ref("")
const errors = reactive({ email: "", password: "" })

const sessionMessage = computed(() =>
  route.query.reason === "expired" ? "로그인이 필요하거나 세션이 만료되었습니다." : "",
)

const handleLogin = async () => {
  errors.email = ""
  errors.password = ""

  if (!email.value.trim()) errors.email = "이메일을 입력해 주세요."
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim()))
    errors.email = "올바른 이메일 형식을 입력해 주세요."
  if (!password.value) errors.password = "비밀번호를 입력해 주세요."
  if (errors.email || errors.password) return

  try {
    const authenticatedUser = await userStore.login({
      email: email.value.trim(),
      password: password.value,
    })

    const redirectPath = !authenticatedUser.connectionCompleted
      ? "/onboarding"
      : typeof route.query.redirect === "string" && route.query.redirect.startsWith("/")
        ? route.query.redirect
        : "/dashboard"
    await router.replace(redirectPath)
  } catch (error) {
    if (error.code === "AUTH_LOGIN_EMAIL_NOT_FOUND") {
      errors.email = error.message || "아이디가 틀렸습니다."
    } else if (error.code === "AUTH_LOGIN_PASSWORD_MISMATCH") {
      errors.password = error.message || "비밀번호가 틀렸습니다."
    } else {
      errors.email = error.message || "로그인에 실패했습니다."
    }
  }
}

const clearError = (field) => {
  errors[field] = ""
}
</script>

<template>
  <main class="auth-page">
    <AppCard as="section" class="auth-card" padding="lg">
      <div class="auth-card-content">
        <img
          class="auth-logo"
          :src="'/images/profiles/penguin-login.svg'"
          alt="Wallo 로그인 로고"
        />
        <h1 class="mb-2 text-center fw-bold">로그인</h1>
        <p class="mb-4 text-center text-secondary">Wallo에서 절약 습관을 이어가세요.</p>

        <AppAlert
          v-if="sessionMessage"
          class="session-message"
          variant="warning"
          role="status"
          :show-icon="false"
          :message="sessionMessage"
        />
        <form class="auth-form" novalidate @submit.prevent="handleLogin">
          <AppFormField
            id="login-email"
            v-model="email"
            label="이메일"
            type="email"
            autocomplete="email"
            placeholder="test@wallo.com"
            :error="errors.email"
            @input="clearError('email')"
          />

          <AppFormField
            id="login-password"
            v-model="password"
            label="비밀번호"
            type="password"
            autocomplete="current-password"
            placeholder="비밀번호를 입력하세요"
            :error="errors.password"
            @input="clearError('password')"
          />

          <AppButton
            class="login-submit"
            type="submit"
            block
            :disabled="userStore.isLoading"
            :loading="userStore.isLoading"
          >
            {{ userStore.isLoading ? "로그인 중..." : "로그인" }}
          </AppButton>
        </form>

        <p class="mt-4 mb-0 text-center text-secondary">
          아직 계정이 없나요?
          <RouterLink to="/signup" class="fw-semibold">회원가입</RouterLink>
        </p>
      </div>
    </AppCard>
  </main>
</template>

<style scoped>
.auth-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: var(--wallo-space-4);
  background: linear-gradient(135deg, var(--wallo-color-info-bg) 0%, #f8fbff 100%);
}

.auth-card {
  width: 100%;
  max-width: 480px;
  border-radius: var(--wallo-radius-xl);
}

.auth-card-content {
  display: flex;
  flex-direction: column;
}

.auth-form {
  display: grid;
  gap: var(--wallo-space-4);
}

.session-message {
  margin-bottom: var(--wallo-space-4);
}

.auth-logo {
  display: block;
  width: 140px;
  height: 140px;
  margin: 0 auto var(--wallo-space-2);
  object-fit: contain;
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
