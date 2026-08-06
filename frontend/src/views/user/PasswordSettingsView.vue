<script setup>
const passwordFields = [
  {
    id: "current-password",
    label: "현재 비밀번호",
    placeholder: "현재 비밀번호를 입력해 주세요",
  },
  {
    id: "new-password",
    label: "새 비밀번호",
    placeholder: "새 비밀번호를 입력해 주세요",
  },
  {
    id: "new-password-confirm",
    label: "새 비밀번호 확인",
    placeholder: "새 비밀번호를 다시 입력해 주세요",
  },
]
</script>

<template>
  <section class="settings-panel card border-0 shadow-sm" aria-labelledby="password-settings-title">
    <div class="card-body p-4 p-md-5">
      <div class="password-form">
        <h2 id="password-settings-title" class="h5 fw-bold mb-2">비밀번호 변경</h2>
        <p class="small text-secondary mb-4">
          비밀번호 변경 API가 연결되면 이 화면에서 안전하게 비밀번호를 변경할 수 있습니다.
        </p>

        <form @submit.prevent>
          <div v-for="field in passwordFields" :key="field.id" class="mb-3">
            <label :for="field.id" class="form-label">{{ field.label }}</label>
            <input
              :id="field.id"
              type="password"
              class="form-control"
              :placeholder="field.placeholder"
              disabled
            />
            <div v-if="field.id === 'new-password'" class="form-text">
              비밀번호는 8자 이상 72자 이하로 입력해 주세요.
            </div>
          </div>

          <button type="submit" class="btn btn-primary w-100 mt-2" disabled>
            비밀번호 변경하기
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
