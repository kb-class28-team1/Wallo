<script setup>
import { nextTick, ref } from 'vue'

import { requestChat } from '@/api/chat'
import ChatInput from '@/components/chat/ChatInput.vue'
import ChatMessage from '@/components/chat/ChatMessage.vue'

const messages = ref([
  {
    id: 1,
    role: 'assistant',
    content: '안녕하세요. 저는 Wallo 금융 컨설턴트입니다. 무엇을 도와드릴까요?',
  },
])
const isLoading = ref(false)
const errorMessage = ref('')
const messageList = ref(null)

async function scrollToBottom() {
  await nextTick()
  messageList.value?.scrollTo({
    top: messageList.value.scrollHeight,
    behavior: 'smooth',
  })
}

async function sendMessage(message) {
  if (isLoading.value) {
    return
  }

  messages.value.push({
    id: Date.now(),
    role: 'user',
    content: message,
  })
  errorMessage.value = ''
  isLoading.value = true
  await scrollToBottom()

  try {
    const answer = await requestChat(message)
    messages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: answer,
    })
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : '메시지 전송에 실패했습니다.'
  } finally {
    isLoading.value = false
    await scrollToBottom()
  }
}
</script>

<template>
  <main class="page-shell">
    <section class="chat-panel" aria-labelledby="chat-title">
      <header class="chat-header">
        <h1 id="chat-title">AI 채팅</h1>
      </header>

      <div ref="messageList" class="message-list" aria-live="polite">
        <ChatMessage
          v-for="message in messages"
          :key="message.id"
          :message="message"
        />

        <div v-if="isLoading" class="loading-message" aria-label="AI 답변 생성 중">
          AI 답변을 기다리는 중...
        </div>
      </div>

      <div v-if="errorMessage" class="error-message" role="alert">
        {{ errorMessage }}
      </div>

      <ChatInput :disabled="isLoading" @send="sendMessage" />
    </section>
  </main>
</template>

<style scoped>
.page-shell {
  min-height: 100vh;
  padding: 24px;
}

.chat-panel {
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
}

.chat-header {
  padding-bottom: 16px;
  border-bottom: 1px solid #ddd;
}

.chat-header h1 {
  margin: 0;
  font-size: 24px;
}

.message-list {
  height: 500px;
  overflow-y: auto;
  padding: 20px 0;
}

.loading-message {
  width: fit-content;
  margin-bottom: 16px;
  padding: 12px;
  color: #666;
  background: #f1f1f1;
  border: 1px solid #ddd;
  border-radius: 8px;
}

.error-message {
  margin-bottom: 12px;
  padding: 10px;
  color: #b00020;
  border: 1px solid #b00020;
  border-radius: 4px;
}

@media (max-width: 640px) {
  .page-shell {
    padding: 16px;
  }
}
</style>
