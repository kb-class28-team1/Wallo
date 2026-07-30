<script setup>
import { ref } from 'vue'

const props = defineProps({
  disabled: {
    type: Boolean,
    default: false,
  },
})
const emit = defineEmits(['send'])
const inputMessage = ref('')

function submitMessage() {
  const message = inputMessage.value.trim()

  if (!message || props.disabled) {
    return
  }

  emit('send', message)
  inputMessage.value = ''
}
</script>

<template>
  <form class="message-form" @submit.prevent="submitMessage">
    <label class="sr-only" for="message-input">메시지 입력</label>
    <textarea
      id="message-input"
      v-model="inputMessage"
      rows="1"
      maxlength="1000"
      placeholder="재무 목표나 자산에 대해 물어보세요"
      :disabled="disabled"
      @keydown.enter.exact.prevent="submitMessage"
    ></textarea>
    <button
      type="submit"
      :disabled="!inputMessage.trim() || disabled"
      aria-label="메시지 전송"
    >
      전송
    </button>
  </form>
</template>

<style scoped>
.message-form {
  display: flex;
  gap: 8px;
}

.message-form textarea {
  flex: 1;
  min-height: 48px;
  resize: none;
  padding: 12px;
  border: 1px solid #aaa;
  border-radius: 4px;
}

.message-form button {
  padding: 0 20px;
  color: #fff;
  background: #333;
  border: 0;
  border-radius: 4px;
  cursor: pointer;
}

.message-form button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
