<script setup>
import { reactive, ref } from "vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppFormField from "@/components/ui/AppFormField.vue"
import { useUserStore } from "@/stores/userStore"

const userStore = useUserStore()

const passwordFields = [
  {
    id: "current-password",
    model: "currentPassword",
    label: "현재 비밀번호",
    placeholder: "현재 비밀번호를 입력해 주세요",
    autocomplete: "current-password",
  },
  {
    id: "new-password",
    model: "newPassword",
    label: "새 비밀번호",
    placeholder: "새 비밀번호를 입력해 주세요",
    autocomplete: "new-password",
  },
  {
    id: "new-password-confirm",
    model: "newPasswordConfirm",
    label: "새 비밀번호 확인",
    placeholder: "새 비밀번호를 다시 입력해 주세요",
    autocomplete: "new-password",
  },
]

const form = reactive({
  currentPassword: "",
  newPassword: "",
  newPasswordConfirm: "",
})
const errorMessage = ref("")
const successMessage = ref("")
const isSaving = ref(false)

const clearMessages = () => {
  errorMessage.value = ""
  successMessage.value = ""
}

const validate = () => {
  if (!form.currentPassword.trim()) {
    return "현재 비밀번호를 입력해 주세요."
  }
  if (!form.newPassword) {
    return "새 비밀번호를 입력해 주세요."
  }
  if (form.newPassword.length < 8 || form.newPassword.length > 72) {
    return "비밀번호는 8자 이상 72자 이하로 입력해 주세요."
  }
  if (form.newPassword !== form.newPasswordConfirm) {
    return "새 비밀번호가 일치하지 않습니다."
  }
  return ""
}

const changePassword = async () => {
  clearMessages()
  const validationMessage = validate()
  if (validationMessage) {
    errorMessage.value = validationMessage
    return
  }

  isSaving.value = true
  try {
    await userStore.changePassword({ ...form })
    form.currentPassword = ""
    form.newPassword = ""
    form.newPasswordConfirm = ""
    successMessage.value = "비밀번호가 변경되었습니다."
  } catch (error) {
    errorMessage.value = error.message || "비밀번호를 변경하지 못했습니다."
  } finally {
    isSaving.value = false
  }
}
</script>

<template>
  <AppCard
    as="section"
    class="settings-panel"
    padding="none"
    aria-labelledby="password-settings-title"
  >
    <div class="password-card-body">
      <div class="password-form">
        <h2 id="password-settings-title" class="h5 fw-bold mb-2">비밀번호 변경</h2>

        <form @submit.prevent="changePassword">
          <div v-for="field in passwordFields" :key="field.id" class="mb-3">
            <AppFormField
              :id="field.id"
              v-model="form[field.model]"
              :label="field.label"
              type="password"
              :placeholder="field.placeholder"
              :autocomplete="field.autocomplete"
              :disabled="isSaving"
              :help-text="
                field.id === 'new-password' ? '비밀번호는 8자 이상 72자 이하로 입력해 주세요.' : ''
              "
              @input="clearMessages"
            />
          </div>

          <AppAlert
            v-if="errorMessage"
            class="password-message"
            variant="danger"
            :message="errorMessage"
            :show-icon="false"
          />
          <AppAlert
            v-else-if="successMessage"
            class="password-message"
            variant="success"
            :message="successMessage"
            :show-icon="false"
            role="status"
          />

          <AppButton
            type="submit"
            class="password-submit w-100 mt-3"
            variant="primary"
            block
            :disabled="isSaving"
            :loading="isSaving"
          >
            비밀번호 변경하기
          </AppButton>
        </form>
      </div>
    </div>
  </AppCard>
</template>

<style scoped>
.settings-panel {
  min-height: 420px;
  border-radius: var(--wallo-radius-xl);
}

.password-card-body {
  padding: var(--wallo-space-6);
}

.password-form {
  width: min(100%, 420px);
  margin: 0 auto;
}

.password-message {
  margin-top: var(--wallo-space-3);
}

@media (max-width: 767.98px) {
  .password-card-body {
    padding: var(--wallo-space-5);
  }
}
</style>
