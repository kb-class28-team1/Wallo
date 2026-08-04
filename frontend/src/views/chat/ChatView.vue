<script setup>
import { computed, nextTick, onMounted, ref } from "vue"
import { storeToRefs } from "pinia"

import ChatInput from "@/components/chat/ChatInput.vue"
import ChatMessage from "@/components/chat/ChatMessage.vue"
import { useConversationStore } from "@/stores/conversationStore"
import { useUserStore } from "@/stores/userStore"

const WELCOME_MESSAGE = {
  id: "welcome",
  role: "assistant",
  content: "안녕하세요. 저는 Wallo 금융 컨설턴트입니다. 무엇을 도와드릴까요?",
}

const conversationStore = useConversationStore()
const userStore = useUserStore()
const {
  conversations,
  activeConversation,
  activeConversationId,
  messages,
  isLoading: isConversationLoading,
  isMessageLoading,
  isSending: isChatLoading,
} = storeToRefs(conversationStore)
const { user } = storeToRefs(userStore)

const errorMessage = ref("")
const messageList = ref(null)
const userId = computed(() => user.value?.id ?? null)
const displayMessages = computed(() =>
  messages.value.length ? messages.value : [{ ...WELCOME_MESSAGE }],
)

const formatUpdatedAt = (updatedAt) => {
  if (!updatedAt) return ""

  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
  }).format(new Date(updatedAt))
}

const selectConversation = async (conversationId) => {
  if (!userId.value) return

  errorMessage.value = ""
  await conversationStore.selectConversation(conversationId, userId.value)
  await scrollToBottom()
}

const startNewConversation = async () => {
  if (!userId.value) {
    errorMessage.value = "로그인 사용자 정보를 확인할 수 없습니다."
    return
  }

  const conversation =
    await conversationStore.startNewConversation(userId.value)

  if (conversation) errorMessage.value = ""
}

async function scrollToBottom(behavior = "smooth") {
  await nextTick()
  messageList.value?.scrollTo({
    top: messageList.value.scrollHeight,
    behavior,
  })
}

const followTypingMessage = () => scrollToBottom("auto")
const completeTypingMessage = (messageId) => {
  conversationStore.completeMessageAnimation(messageId)
}

async function sendMessage(message) {
  if (isChatLoading.value || !userId.value) return

  errorMessage.value = ""
  const sendPromise = conversationStore.sendMessage(userId.value, message)
  await scrollToBottom()
  await sendPromise
  await scrollToBottom()
}

onMounted(async () => {
  if (!userId.value) {
    await userStore.restoreSession()
  }

  if (!userId.value) {
    errorMessage.value = "로그인 사용자 정보를 확인할 수 없습니다."
    return
  }

  const conversationId =
    await conversationStore.fetchConversations(userId.value)
  if (conversationId) {
    await conversationStore.fetchMessages(userId.value, conversationId)
    await scrollToBottom()
  }
})
</script>

<template>
  <main class="chat-page">
    <div class="row g-3">
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
              v-for="message in displayMessages"
              :key="message.id"
              :message="message"
              @typing="followTypingMessage"
              @typing-complete="completeTypingMessage"
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

          <ChatInput
            :disabled="isChatLoading || isMessageLoading || !userId"
            @send="sendMessage"
          />
        </div>
      </section>

      <aside class="col-12 col-lg-4 col-xl-3">
        <section class="conversation-panel card border-0 shadow-sm">
          <div class="card-body d-flex flex-column p-3">
            <button
              type="button"
              class="btn btn-primary w-100 fw-semibold"
              :disabled="isConversationLoading || !userId"
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

    </div>
  </main>
</template>

<style scoped src="@/assets/styles/chat.css"></style>
