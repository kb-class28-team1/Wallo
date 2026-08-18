<script setup>
import { reactive, ref } from "vue"
import { RouterLink, useRouter } from "vue-router"
import { signup } from "@/api/authApi"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppFormField from "@/components/ui/AppFormField.vue"
import AppDialog from "@/components/common/AppDialog.vue"

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
  await router.replace({
    name: "login",
    query: { redirect: "/connections/mydata" },
  })
}

const closeSuccessModal = () => {
  showSuccessModal.value = false
}
</script>

<template>
  <main class="auth-page">
    <AppCard as="section" class="auth-card" padding="lg">
      <div class="auth-card-content">
        <img
          class="auth-logo"
          :src="'/images/profiles/Wallo-signup.svg'"
          alt="Wallo 회원가입 로고"
        />
        <h2 class="mb-2 text-center fw-bold">회원가입</h2>
        <p class="mb-4 text-center text-secondary">Wallo와 함께 절약을 시작해보세요.</p>

        <form class="auth-form" novalidate @submit.prevent="handleSignup">
          <AppFormField
            id="signup-name"
            v-model="form.name"
            label="이름"
            autocomplete="name"
            maxlength="50"
            required
            :error="errors.name"
            @input="clearError('name')"
          />

          <AppFormField
            id="signup-nickname"
            v-model="form.nickname"
            label="닉네임"
            autocomplete="nickname"
            maxlength="50"
            required
            :error="errors.nickname"
            @input="clearError('nickname')"
          />

          <AppFormField
            id="signup-email"
            v-model="form.email"
            label="이메일"
            type="email"
            autocomplete="email"
            maxlength="255"
            placeholder="test@wallo.com"
            required
            :error="errors.email"
            @input="clearError('email')"
          />

          <AppFormField
            id="signup-password"
            v-model="form.password"
            label="비밀번호"
            type="password"
            autocomplete="new-password"
            minlength="8"
            maxlength="72"
            help-text="8자 이상 72자 이하로 입력해주세요."
            required
            :error="errors.password"
            @input="clearError('password')"
          />

          <AppFormField
            id="signup-password-confirm"
            v-model="form.passwordConfirm"
            label="비밀번호 확인"
            type="password"
            autocomplete="new-password"
            minlength="8"
            maxlength="72"
            required
            :error="errors.passwordConfirm"
            @input="clearError('passwordConfirm')"
          />

          <AppButton type="submit" block :disabled="isSubmitting" :loading="isSubmitting">
            {{ isSubmitting ? "가입 중..." : "회원가입" }}
          </AppButton>
        </form>

        <p class="mt-4 mb-0 text-center text-secondary">
          이미 계정이 있나요?
          <RouterLink to="/login" class="fw-semibold">로그인</RouterLink>
        </p>
      </div>
    </AppCard>

    <AppDialog
      :visible="showSuccessModal"
      title="회원가입이 완료되었어요!"
      message="이제 로그인하고 Wallo와 함께 절약을 시작해 보세요."
      confirm-text="로그인하러 가기"
      :show-cancel="false"
      :show-close="false"
      :close-on-backdrop="false"
      :close-on-esc="false"
      @close="closeSuccessModal"
      @confirm="moveToLogin"
    />
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
  max-width: 520px;
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

.auth-logo {
  display: block;
  width: 200px;
  height: 200px;
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
