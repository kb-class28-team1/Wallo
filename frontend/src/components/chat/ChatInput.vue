<script setup>
import { nextTick, onMounted, ref, watch } from "vue"
import AppButton from "@/components/ui/AppButton.vue"

const props = defineProps({
  disabled: {
    type: Boolean,
    default: false,
  },
})
const emit = defineEmits(["send"])
const inputMessage = ref("")
const messageInputElement = ref(null)

const focusInput = async () => {
  await nextTick()
  if (!props.disabled) messageInputElement.value?.focus()
}

function submitMessage() {
  const message = inputMessage.value.trim()

  if (!message || props.disabled) return

  emit("send", message)
  inputMessage.value = ""
  focusInput()
}

function handleEnter(event) {
  // 한글 조합 중 Enter는 전송이 아니라 마지막 글자 확정에 먼저 사용된다.
  // 이때 전송하면 조합 중인 마지막 글자가 빠지고 입력창에도 남는다.
  if (event.isComposing || event.keyCode === 229) return

  event.preventDefault()
  submitMessage()
}

watch(
  () => props.disabled,
  (disabled) => {
    if (!disabled) focusInput()
  },
)

onMounted(focusInput)
</script>

<template>
  <form class="message-form d-flex gap-2 border-top p-3" @submit.prevent="submitMessage">
    <label class="visually-hidden" for="message-input">메시지 입력</label>
    <textarea
      id="message-input"
      ref="messageInputElement"
      v-model="inputMessage"
      class="form-control"
      rows="1"
      maxlength="1000"
      placeholder="재무 목표나 자산에 대해 물어보세요"
      :disabled="disabled"
      @keydown.enter.exact="handleEnter"
    ></textarea>
    <AppButton
      class="message-send-button"
      type="submit"
      variant="primary"
      :disabled="!inputMessage.trim() || disabled"
      aria-label="메시지 전송"
    >
      전송
    </AppButton>
  </form>
</template>

<style scoped>
.message-form textarea {
  min-height: 48px;
  resize: none;
  border-radius: 12px;
}

.message-send-button {
  min-width: 78px;
  border-radius: var(--wallo-radius-md);
}
</style>
