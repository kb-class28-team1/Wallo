<script setup>
import { nextTick, onMounted, ref } from "vue"
import { storeToRefs } from "pinia"

import { requestChat } from "@/api/chat"
import ChatInput from "@/components/chat/ChatInput.vue"
import ChatMessage from "@/components/chat/ChatMessage.vue"
import { useConversationStore } from "@/stores/conversationStore"

// 로그인 기능 연동 전 더미 사용자 people1의 ID를 사용한다.
const TEST_USER_ID = 1
const WELCOME_MESSAGE = {
  id: "welcome",
  role: "assistant",
  content: "안녕하세요. 저는 Wallo 금융 컨설턴트입니다. 무엇을 도와드릴까요?",
}

const conversationStore = useConversationStore()
const {
  conversations,
  activeConversation,
  activeConversationId,
  isLoading: isConversationLoading,
} = storeToRefs(conversationStore)

const messages = ref([{ ...WELCOME_MESSAGE }])
const isChatLoading = ref(false)
const errorMessage = ref("")
const messageList = ref(null)

const formatUpdatedAt = (updatedAt) => {
  if (!updatedAt) return ""

  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
  }).format(new Date(updatedAt))
}

const resetMessages = () => {
  messages.value = [{ ...WELCOME_MESSAGE }]
  errorMessage.value = ""
}

const selectConversation = (conversationId) => {
  conversationStore.selectConversation(conversationId)
  resetMessages()
}

const startNewConversation = async () => {
  const conversation =
    await conversationStore.startNewConversation(TEST_USER_ID)

  if (conversation) {
    resetMessages()
  }
}

async function scrollToBottom() {
  await nextTick()
  messageList.value?.scrollTo({
    top: messageList.value.scrollHeight,
    behavior: "smooth",
  })
}

async function sendMessage(message) {
  if (isChatLoading.value) return

  messages.value.push({
    id: Date.now(),
    role: "user",
    content: message,
  })
  errorMessage.value = ""
  isChatLoading.value = true
  await scrollToBottom()

  try {
    const answer = await requestChat(message)
    messages.value.push({
      id: Date.now() + 1,
      role: "assistant",
      content: answer,
    })
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : "메시지 전송에 실패했습니다."
  } finally {
    isChatLoading.value = false
    await scrollToBottom()
  }
}

onMounted(() => {
  conversationStore.fetchConversations(TEST_USER_ID)
})
</script>

<template>
  <main class="chat-page">
    <div class="row g-3">
      <aside class="col-12 col-lg-4 col-xl-3">
        <section class="conversation-panel card border-0 shadow-sm">
          <div class="card-body d-flex flex-column p-3">
            <button
              type="button"
              class="btn btn-primary w-100 fw-semibold"
              :disabled="isConversationLoading"
              @click="startNewConversation"
            >
              <i class="bi bi-plus-lg me-2" aria-hidden="true"></i>
              새 채팅
            </button>

            <div
              class="d-flex align-items-center justify-content-between px-1 pb-2 pt-4"
            >
              <h2 class="mb-0 fs-6 fw-bold">채팅 목록</h2>
              <span class="badge text-bg-light">{{ conversations.length }}</span>
            </div>

            <div
              v-if="isConversationLoading && !conversations.length"
              class="py-4 text-center text-secondary"
            >
              <span class="spinner-border spinner-border-sm me-2"></span>
              불러오는 중
            </div>

            <div
              v-else-if="!conversations.length"
              class="empty-conversations py-5 text-center text-secondary"
            >
              <i class="bi bi-chat-left-text d-block mb-2 fs-3"></i>
              저장된 채팅이 없습니다.
            </div>

            <div v-else class="conversation-list list-group list-group-flush">
              <button
                v-for="conversation in conversations"
                :key="conversation.conversationId"
                type="button"
                class="conversation-item list-group-item list-group-item-action rounded-3 border-0"
                :class="{
                  active:
                    conversation.conversationId === activeConversationId,
                }"
                @click="selectConversation(conversation.conversationId)"
              >
                <span class="d-block text-truncate fw-semibold">
                  {{ conversation.title }}
                </span>
                <small class="conversation-date">
                  {{ formatUpdatedAt(conversation.updatedAt) }}
                </small>
              </button>
            </div>
          </div>
        </section>
      </aside>

      <section class="col-12 col-lg-8 col-xl-9" aria-labelledby="chat-title">
        <div class="chat-panel card border-0 shadow-sm">
          <header class="card-header border-bottom bg-white px-4 py-3">
            <h1 id="chat-title" class="mb-1 fs-5 fw-bold">
              {{ activeConversation?.title || "새 채팅" }}
            </h1>
            <p class="mb-0 small text-secondary">Wallo AI 금융 컨설턴트</p>
          </header>

          <div ref="messageList" class="message-list card-body" aria-live="polite">
            <ChatMessage
              v-for="message in messages"
              :key="message.id"
              :message="message"
            />

            <div
              v-if="isChatLoading"
              class="loading-message"
              aria-label="AI 답변 생성 중"
            >
              AI 답변을 기다리는 중...
            </div>
          </div>

          <div v-if="errorMessage" class="alert alert-danger mx-3 mb-2" role="alert">
            {{ errorMessage }}
          </div>

          <ChatInput :disabled="isChatLoading" @send="sendMessage" />
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.chat-page {
  width: 100%;
}

.conversation-panel,
.chat-panel {
  height: calc(100vh - 132px);
  min-height: 560px;
  border-radius: 20px;
  overflow: hidden;
}

.conversation-list {
  min-height: 0;
  overflow-y: auto;
}

.conversation-item {
  margin-bottom: 4px;
  padding: 12px;
  color: #59647f;
}

.conversation-item.active {
  color: #4f43b8;
  background: #f0eefe;
}

.conversation-date {
  color: #9299ab;
}

.conversation-item.active .conversation-date {
  color: #7062de;
}

.message-list {
  min-height: 0;
  overflow-y: auto;
  padding: 24px;
}

.loading-message {
  width: fit-content;
  padding: 12px;
  color: #666;
  background: #f1f1f1;
  border-radius: 8px;
}

.btn-primary {
  --bs-btn-bg: #7062de;
  --bs-btn-border-color: #7062de;
  --bs-btn-hover-bg: #5f50d2;
  --bs-btn-hover-border-color: #5f50d2;
}

@media (max-width: 991.98px) {
  .conversation-panel {
    height: auto;
    min-height: 0;
    max-height: 320px;
  }

  .chat-panel {
    height: 640px;
    min-height: 0;
  }
}
</style>
