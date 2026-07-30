import { computed, ref } from "vue"
import { defineStore } from "pinia"

import {
  createConversation,
  getConversationMessages,
  getConversations,
  sendConversationMessage,
} from "@/api/conversationApi"

const toViewMessage = (message) => ({
  id: message.messageId,
  role: message.role.toLowerCase(),
  content: message.content,
  createdAt: message.createdAt,
})

export const useConversationStore = defineStore("conversation", () => {
  const conversations = ref([])
  const activeConversationId = ref(null)
  const messages = ref([])
  const isLoading = ref(false)
  const isMessageLoading = ref(false)

  const activeConversation = computed(() =>
    conversations.value.find(
      (conversation) =>
        conversation.conversationId === activeConversationId.value,
    ),
  )

  const fetchConversations = async (userId) => {
    isLoading.value = true

    try {
      conversations.value = await getConversations(userId)

      if (
        !conversations.value.some(
          (conversation) =>
            conversation.conversationId === activeConversationId.value,
        )
      ) {
        activeConversationId.value =
          conversations.value[0]?.conversationId ?? null
      }

      return activeConversationId.value
    } catch (error) {
      conversations.value = []
      activeConversationId.value = null
      alert(error.message || "채팅방 목록을 불러오지 못했습니다.")
      return null
    } finally {
      isLoading.value = false
    }
  }

  const fetchMessages = async (userId, conversationId) => {
    if (!conversationId) {
      messages.value = []
      return
    }

    isMessageLoading.value = true
    try {
      const response = await getConversationMessages(conversationId, userId)
      messages.value = response.map(toViewMessage)
    } catch (error) {
      messages.value = []
      alert(error.message || "대화 내용을 불러오지 못했습니다.")
    } finally {
      isMessageLoading.value = false
    }
  }

  const startNewConversation = async (userId) => {
    isLoading.value = true

    try {
      const conversation = await createConversation(userId)
      conversations.value.unshift(conversation)
      activeConversationId.value = conversation.conversationId
      messages.value = []
      return conversation
    } catch (error) {
      alert(error.message || "새 채팅방을 만들지 못했습니다.")
      return null
    } finally {
      isLoading.value = false
    }
  }

  const selectConversation = async (conversationId, userId) => {
    activeConversationId.value = conversationId
    await fetchMessages(userId, conversationId)
  }

  const sendMessage = async (userId, content) => {
    if (!activeConversationId.value) {
      alert("새 채팅을 먼저 시작해 주세요.")
      return false
    }

    isMessageLoading.value = true
    try {
      const response = await sendConversationMessage(
        activeConversationId.value,
        userId,
        content,
      )
      messages.value.push(
        toViewMessage(response.userMessage),
        toViewMessage(response.assistantMessage),
      )
      await fetchConversations(userId)
      return true
    } catch (error) {
      // 사용자 메시지는 AI 호출 전에 저장되므로 실패 시 DB 상태를 다시 읽는다.
      await fetchMessages(userId, activeConversationId.value)
      alert(error.message || "메시지를 전송하지 못했습니다.")
      return false
    } finally {
      isMessageLoading.value = false
    }
  }

  return {
    conversations,
    activeConversation,
    activeConversationId,
    messages,
    isLoading,
    isMessageLoading,
    fetchConversations,
    fetchMessages,
    startNewConversation,
    selectConversation,
    sendMessage,
  }
})
