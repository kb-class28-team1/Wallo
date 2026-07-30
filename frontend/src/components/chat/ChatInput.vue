<script setup>
import { ref } from "vue"

const props = defineProps({
  disabled: {
    type: Boolean,
    default: false,
  },
})
const emit = defineEmits(["send"])
const inputMessage = ref("")

function submitMessage() {
  const message = inputMessage.value.trim()

  if (!message || props.disabled) return

  emit("send", message)
  inputMessage.value = ""
}
</script>

<template>
  <form class="message-form d-flex gap-2 border-top p-3" @submit.prevent="submitMessage">
    <label class="visually-hidden" for="message-input">메시지 입력</label>
    <textarea
      id="message-input"
      v-model="inputMessage"
      class="form-control"
      rows="1"
      maxlength="1000"
      placeholder="재무 목표나 자산에 대해 물어보세요"
      :disabled="disabled"
      @keydown.enter.exact.prevent="submitMessage"
    ></textarea>
    <button
      type="submit"
      class="btn btn-primary px-4"
      :disabled="!inputMessage.trim() || disabled"
      aria-label="메시지 전송"
    >
      전송
    </button>
  </form>
</template>

<style scoped>
.message-form textarea {
  min-height: 48px;
  resize: none;
  border-radius: 12px;
}

.message-form button {
  border-radius: 12px;
  --bs-btn-bg: #7062de;
  --bs-btn-border-color: #7062de;
}
</style>
