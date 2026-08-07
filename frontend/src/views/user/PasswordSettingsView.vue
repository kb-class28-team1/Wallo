<script setup>
import { reactive, ref } from "vue"
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
  <section class="settings-panel card border-0 shadow-sm" aria-labelledby="password-settings-title">
    <div class="card-body p-4 p-md-5">
      <div class="password-form">
        <h2 id="password-settings-title" class="h5 fw-bold mb-2">비밀번호 변경</h2>
        <br>

        <form @submit.prevent="changePassword">
          <div v-for="field in passwordFields" :key="field.id" class="mb-3">
            <label :for="field.id" class="form-label">{{ field.label }}</label>
            <input
              :id="field.id"
              type="password"
              class="form-control"
              :placeholder="field.placeholder"
              :autocomplete="field.autocomplete"
              :value="form[field.model]"
              :disabled="isSaving"
              @input="form[field.model] = $event.target.value; clearMessages()"
            />
            <div v-if="field.id === 'new-password'" class="form-text">
              비밀번호는 8자 이상 72자 이하로 입력해 주세요.
            </div>
          </div>

          <div v-if="errorMessage" class="alert alert-danger py-2 mt-3 mb-0" role="alert">
            {{ errorMessage }}
          </div>
          <div v-else-if="successMessage" class="alert alert-success py-2 mt-3 mb-0" role="status">
            {{ successMessage }}
          </div>

          <button type="submit" class="btn btn-primary w-100 mt-3" :disabled="isSaving">
            {{ isSaving ? "변경 중..." : "비밀번호 변경하기" }}
          </button>
        </form>
      </div>
    </div>
  </section>
</template>

<style scoped>
.settings-panel {
  min-height: 420px;
  border-radius: 20px;
  background: #ffffff;
}

.password-form {
  width: min(100%, 420px);
  margin: 0 auto;
}
</style>
