import { computed, ref } from "vue"
import { defineStore } from "pinia"

import {
  createConversation,
  deleteConversation as deleteConversationApi,
  getConversationMessages,
  getConversations,
  sendConversationMessage,
  updateConversationTitle,
} from "@/api/conversationApi"

const toViewMessage = (message, animate = false) => ({
  id: message.messageId,
  role: message.role.toLowerCase(),
  content: message.content,
  createdAt: message.createdAt,
  animate,
})

export const useConversationStore = defineStore("conversation", () => {
  const conversations = ref([])
  const activeConversationId = ref(null)
  const messages = ref([])
  const isLoading = ref(false)
  const isMessageLoading = ref(false)
  const isSending = ref(false)

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
      messages.value = response.map((message) => toViewMessage(message))
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

  const completeMessageAnimation = (messageId) => {
    const message = messages.value.find((item) => item.id === messageId)
    if (message) {
      message.animate = false
    }
  }

  const renameConversation = async (conversationId, userId, title) => {
    try {
      const updatedConversation = await updateConversationTitle(
        conversationId,
        userId,
        title,
      )
      const index = conversations.value.findIndex(
        (conversation) => conversation.conversationId === conversationId,
      )
      if (index >= 0) conversations.value.splice(index, 1, updatedConversation)
      return true
    } catch (error) {
      alert(error.message || "채팅방 제목을 변경하지 못했습니다.")
      return false
    }
  }

  const removeConversation = async (conversationId, userId) => {
    try {
      await deleteConversationApi(conversationId, userId)
      conversations.value = conversations.value.filter(
        (conversation) => conversation.conversationId !== conversationId,
      )

      if (activeConversationId.value === conversationId) {
        const nextConversationId = conversations.value[0]?.conversationId ?? null
        activeConversationId.value = nextConversationId
        if (nextConversationId) {
          await fetchMessages(userId, nextConversationId)
        } else {
          messages.value = []
        }
      }
      return true
    } catch (error) {
      alert(error.message || "채팅방을 삭제하지 못했습니다.")
      return false
    }
  }

  const sendMessage = async (userId, content) => {
    if (isSending.value) {
      return false
    }

    isSending.value = true
    let conversationId = activeConversationId.value
    let pendingMessageId = null

    try {
      if (!conversationId) {
        const conversation = await startNewConversation(userId)
        if (!conversation) return false
        conversationId = conversation.conversationId
      }

      pendingMessageId = `pending-${Date.now()}`
      messages.value.push({
        id: pendingMessageId,
        role: "user",
        content,
        createdAt: new Date().toISOString(),
      })

      const response = await sendConversationMessage(
        conversationId,
        userId,
        content,
      )

      if (activeConversationId.value === conversationId) {
        const pendingMessageIndex = messages.value.findIndex(
          (message) => message.id === pendingMessageId,
        )
        const savedUserMessage = toViewMessage(response.userMessage)

        if (pendingMessageIndex >= 0) {
          messages.value.splice(pendingMessageIndex, 1, savedUserMessage)
        } else {
          messages.value.push(savedUserMessage)
        }
        messages.value.push(toViewMessage(response.assistantMessage, true))
      }

      await fetchConversations(userId)
      return true
    } catch (error) {
      // 사용자 메시지는 AI 호출 전에 저장되므로 실패 시 DB 상태를 다시 읽는다.
      if (conversationId && activeConversationId.value === conversationId) {
        await fetchMessages(userId, conversationId)
      }
      alert(error.message || "메시지를 전송하지 못했습니다.")
      return false
    } finally {
      isSending.value = false
    }
  }

  return {
    conversations,
    activeConversation,
    activeConversationId,
    messages,
    isLoading,
    isMessageLoading,
    isSending,
    fetchConversations,
    fetchMessages,
    startNewConversation,
    selectConversation,
    completeMessageAnimation,
    renameConversation,
    removeConversation,
    sendMessage,
  }
})
