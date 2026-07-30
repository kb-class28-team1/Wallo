import { computed, ref } from "vue"
import { defineStore } from "pinia"

import {
  createConversation,
  getConversations,
} from "@/api/conversationApi"

export const useConversationStore = defineStore("conversation", () => {
  const conversations = ref([])
  const activeConversationId = ref(null)
  const isLoading = ref(false)

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
    } catch (error) {
      conversations.value = []
      activeConversationId.value = null
      alert(error.message || "채팅방 목록을 불러오지 못했습니다.")
    } finally {
      isLoading.value = false
    }
  }

  const startNewConversation = async (userId) => {
    isLoading.value = true

    try {
      const conversation = await createConversation(userId)
      conversations.value.unshift(conversation)
      activeConversationId.value = conversation.conversationId
      return conversation
    } catch (error) {
      alert(error.message || "새 채팅방을 만들지 못했습니다.")
      return null
    } finally {
      isLoading.value = false
    }
  }

  const selectConversation = (conversationId) => {
    activeConversationId.value = conversationId
  }

  return {
    conversations,
    activeConversation,
    activeConversationId,
    isLoading,
    fetchConversations,
    startNewConversation,
    selectConversation,
  }
})
