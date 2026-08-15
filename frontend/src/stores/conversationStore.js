import { computed, ref } from "vue"
import { defineStore } from "pinia"

import {
  createConversation,
  deleteConversation as deleteConversationApi,
  getActiveGoalInterview,
  getConversationMessages,
  getConversations,
  sendConversationMessage,
  updateConversationTitle,
} from "@/api/conversationApi"
import { getGoalByConversationId } from "@/api/goalApi"
import {
  getCachedResource,
  getResource,
  hasInFlightResource,
  invalidateResource,
  setCachedResource,
} from "@/utils/resourceCache"
import { normalizeAssetAnalysis } from "@/types/assetAnalysis"

const CONVERSATION_STALE_TIME = 60 * 1000
const MESSAGE_STALE_TIME = 30 * 1000
const GOAL_STALE_TIME = 60 * 1000

const toViewMessage = (
  message,
  animate = false,
  consumptionAnalysis = null,
  assetAnalysis = null,
) => ({
  id: message.messageId,
  role: message.role.toLowerCase(),
  content: message.content,
  createdAt: message.createdAt,
  animate,
  consumptionAnalysis: message.consumptionAnalysis ?? consumptionAnalysis,
  assetAnalysis: normalizeAssetAnalysis(message.assetAnalysis ?? assetAnalysis),
})

const toConfirmedGoalInterview = (goal) => ({
  action: "CONFIRM",
  active: false,
  draft: {
    goalType: goal.goalType,
    title: goal.title,
    targetAmount: goal.targetAmount,
    targetDate: goal.targetDate,
    currentAmount: goal.currentAmount ?? goal.initialAmount,
    state: "COMPLETED",
    confirmed: true,
    missingFields: [],
  },
  feasibility:
    goal.requiredMonthlyAmount === null || goal.requiredMonthlyAmount === undefined
      ? null
      : {
          status:
            Number(goal.currentAmount ?? goal.initialAmount) >= Number(goal.targetAmount)
              ? "ALREADY_ACHIEVED"
              : "CALCULATED",
          requiredMonthlyAmount: goal.requiredMonthlyAmount,
        },
})

const conversationCacheKey = (userId) => `chat:conversations:${userId}`
const messagesCacheKey = (userId, conversationId) => `chat:messages:${userId}:${conversationId}`
const goalCacheKey = (conversationId) => `chat:goal:${conversationId}`

export const useConversationStore = defineStore("conversation", () => {
  const conversations = ref([])
  const activeConversationId = ref(null)
  const messages = ref([])
  const activeGoalInterview = ref(null)
  const confirmedGoal = ref(null)
  const isLoading = ref(false)
  const isMessageLoading = ref(false)
  const isSending = ref(false)
  const initialLoading = ref(false)
  const refreshing = ref(false)
  const initialMessageLoading = ref(false)
  const refreshingMessages = ref(false)
  const hasLoadedConversations = ref(false)
  const loadedMessageConversationId = ref(null)
  const cacheScope = {}
  let messageRequestSequence = 0

  const activeConversation = computed(() =>
    conversations.value.find(
      (conversation) => conversation.conversationId === activeConversationId.value,
    ),
  )

  const applyConversations = (nextConversations) => {
    conversations.value = Array.isArray(nextConversations) ? [...nextConversations] : []
    hasLoadedConversations.value = true

    if (
      !conversations.value.some(
        (conversation) => conversation.conversationId === activeConversationId.value,
      )
    ) {
      activeConversationId.value = conversations.value[0]?.conversationId ?? null
    }

    return activeConversationId.value
  }

  const cacheCurrentConversations = (userId) => {
    setCachedResource(conversationCacheKey(userId), [...conversations.value], { scope: cacheScope })
  }

  const applyConfirmedGoal = (goal) => {
    confirmedGoal.value = goal
    if (confirmedGoal.value && activeGoalInterview.value?.action === "CONFIRM") {
      activeGoalInterview.value = toConfirmedGoalInterview(confirmedGoal.value)
    }
  }

  const fetchConfirmedGoal = async (conversationId, { force = false } = {}) => {
    if (!conversationId) {
      confirmedGoal.value = null
      return null
    }

    const key = goalCacheKey(conversationId)
    const cachedGoal =
      !force && !hasInFlightResource(key, { scope: cacheScope })
        ? getCachedResource(key, {
            scope: cacheScope,
            staleTime: GOAL_STALE_TIME,
          })
        : undefined

    if (cachedGoal !== undefined) {
      applyConfirmedGoal(cachedGoal)
      return cachedGoal
    }

    try {
      const goal = await getResource(
        key,
        async () => {
          const response = await getGoalByConversationId(conversationId)
          return response?.data ?? null
        },
        {
          scope: cacheScope,
          force,
          staleTime: GOAL_STALE_TIME,
        },
      )
      applyConfirmedGoal(goal)
      return goal
    } catch {
      confirmedGoal.value = null
      return null
    }
  }

  const fetchConversations = async (userId, { force = false } = {}) => {
    const key = conversationCacheKey(userId)
    const cachedConversations =
      !force && !hasInFlightResource(key, { scope: cacheScope })
        ? getCachedResource(key, {
            scope: cacheScope,
            staleTime: CONVERSATION_STALE_TIME,
          })
        : undefined

    if (cachedConversations !== undefined) {
      return applyConversations(cachedConversations)
    }

    const hasExistingData = hasLoadedConversations.value
    isLoading.value = true
    initialLoading.value = !hasExistingData
    refreshing.value = hasExistingData

    try {
      const nextConversations = await getResource(key, () => getConversations(userId), {
        scope: cacheScope,
        force,
        staleTime: CONVERSATION_STALE_TIME,
      })
      return applyConversations(nextConversations)
    } catch (error) {
      if (!hasExistingData) {
        conversations.value = []
        activeConversationId.value = null
        hasLoadedConversations.value = false
      }
      alert(error.message || "채팅방 목록을 불러오지 못했습니다.")
      return null
    } finally {
      isLoading.value = false
      initialLoading.value = false
      refreshing.value = false
    }
  }

  const loadConversationSnapshot = async (userId, conversationId) => {
    const response = await getConversationMessages(conversationId, userId)
    const snapshot = {
      messages: (Array.isArray(response) ? response : []).map((message) => toViewMessage(message)),
      activeGoalInterview: null,
      confirmedGoal: null,
    }

    try {
      const interviewResponse = await getActiveGoalInterview(conversationId)
      if (interviewResponse.active) {
        snapshot.activeGoalInterview = {
          action: "CONTINUE",
          active: true,
          draft: interviewResponse.draft,
          feasibility: interviewResponse.feasibility ?? null,
        }
      } else {
        const goalResponse = await getGoalByConversationId(conversationId)
        snapshot.confirmedGoal = goalResponse?.data ?? null
        snapshot.activeGoalInterview = snapshot.confirmedGoal
          ? toConfirmedGoalInterview(snapshot.confirmedGoal)
          : null
        setCachedResource(goalCacheKey(conversationId), snapshot.confirmedGoal, {
          scope: cacheScope,
        })
      }
    } catch {
      // 기존 대화 메시지는 유지하고, 목표 카드 복구만 건너뛴다.
    }

    return snapshot
  }

  const applyConversationSnapshot = (conversationId, snapshot) => {
    messages.value = [...(snapshot.messages ?? [])]
    activeGoalInterview.value = snapshot.activeGoalInterview ?? null
    confirmedGoal.value = snapshot.confirmedGoal ?? null
    loadedMessageConversationId.value = conversationId
  }

  const fetchMessages = async (userId, conversationId, { force = false } = {}) => {
    if (!conversationId) {
      messages.value = []
      activeGoalInterview.value = null
      confirmedGoal.value = null
      loadedMessageConversationId.value = null
      return
    }

    const key = messagesCacheKey(userId, conversationId)
    const cachedSnapshot =
      !force && !hasInFlightResource(key, { scope: cacheScope })
        ? getCachedResource(key, {
            scope: cacheScope,
            staleTime: MESSAGE_STALE_TIME,
          })
        : undefined

    if (cachedSnapshot !== undefined) {
      if (activeConversationId.value === conversationId || activeConversationId.value === null) {
        applyConversationSnapshot(conversationId, cachedSnapshot)
      }
      return cachedSnapshot
    }

    const requestId = ++messageRequestSequence
    const hasExistingData = loadedMessageConversationId.value === conversationId

    if (!hasExistingData) {
      messages.value = []
      activeGoalInterview.value = null
      confirmedGoal.value = null
      loadedMessageConversationId.value = null
    }

    isMessageLoading.value = true
    initialMessageLoading.value = !hasExistingData
    refreshingMessages.value = hasExistingData

    try {
      const snapshot = await getResource(
        key,
        () => loadConversationSnapshot(userId, conversationId),
        {
          scope: cacheScope,
          force,
          staleTime: MESSAGE_STALE_TIME,
        },
      )

      if (
        requestId === messageRequestSequence &&
        (activeConversationId.value === conversationId || activeConversationId.value === null)
      ) {
        applyConversationSnapshot(conversationId, snapshot)
      }
      return snapshot
    } catch (error) {
      if (requestId === messageRequestSequence && !hasExistingData) {
        messages.value = []
        activeGoalInterview.value = null
        confirmedGoal.value = null
        loadedMessageConversationId.value = null
      }
      alert(error.message || "대화 내용을 불러오지 못했습니다.")
      return null
    } finally {
      if (requestId === messageRequestSequence) {
        isMessageLoading.value = false
        initialMessageLoading.value = false
        refreshingMessages.value = false
      }
    }
  }

  const startNewConversation = async (userId) => {
    const hadExistingData = hasLoadedConversations.value
    isLoading.value = true
    initialLoading.value = !hadExistingData
    refreshing.value = hadExistingData

    try {
      const conversation = await createConversation(userId)
      conversations.value.unshift(conversation)
      hasLoadedConversations.value = true
      activeConversationId.value = conversation.conversationId
      cacheCurrentConversations(userId)
      messages.value = []
      activeGoalInterview.value = null
      confirmedGoal.value = null
      loadedMessageConversationId.value = conversation.conversationId
      return conversation
    } catch (error) {
      alert(error.message || "새 채팅방을 만들지 못했습니다.")
      return null
    } finally {
      isLoading.value = false
      initialLoading.value = false
      refreshing.value = false
    }
  }

  const selectConversation = async (conversationId, userId) => {
    activeConversationId.value = conversationId
    activeGoalInterview.value = null
    confirmedGoal.value = null
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
      const updatedConversation = await updateConversationTitle(conversationId, userId, title)
      const index = conversations.value.findIndex(
        (conversation) => conversation.conversationId === conversationId,
      )
      if (index >= 0) {
        conversations.value.splice(index, 1, updatedConversation)
        cacheCurrentConversations(userId)
      }
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
      cacheCurrentConversations(userId)
      invalidateResource(messagesCacheKey(userId, conversationId), {
        scope: cacheScope,
      })
      invalidateResource(goalCacheKey(conversationId), { scope: cacheScope })

      if (activeConversationId.value === conversationId) {
        const nextConversationId = conversations.value[0]?.conversationId ?? null
        activeConversationId.value = nextConversationId
        if (nextConversationId) {
          await fetchMessages(userId, nextConversationId)
        } else {
          messages.value = []
          activeGoalInterview.value = null
          confirmedGoal.value = null
          loadedMessageConversationId.value = null
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

      const response = await sendConversationMessage(conversationId, userId, content)

      if (response.consumptionAnalysis) {
        window.dispatchEvent(new CustomEvent("wallo:mission-updated"))
      }

      activeGoalInterview.value = response.goalInterview ?? null

      const goalInterview = response.goalInterview
      const isConfirmedGoal =
        goalInterview?.action === "CONFIRM" ||
        goalInterview?.draft?.confirmed ||
        goalInterview?.draft?.state === "COMPLETED"
      if (isConfirmedGoal) {
        const confirmed = await fetchConfirmedGoal(conversationId, {
          force: true,
        })
        if (confirmed) {
          activeGoalInterview.value = toConfirmedGoalInterview(confirmed)
        }
      }

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
        messages.value.push(
          toViewMessage(
            response.assistantMessage,
            true,
            response.consumptionAnalysis ?? null,
            response.assetAnalysis ?? null,
          ),
        )
      }

      invalidateResource(messagesCacheKey(userId, conversationId), {
        scope: cacheScope,
      })
      await fetchConversations(userId, { force: true })
      return true
    } catch (error) {
      // 사용자 메시지는 AI 호출 전에 저장되므로 실패 시 DB 상태를 다시 읽는다.
      if (conversationId && activeConversationId.value === conversationId) {
        await fetchMessages(userId, conversationId, { force: true })
      }
      alert(error.message || "메시지를 전송하지 못했습니다.")
      return false
    } finally {
      isSending.value = false
    }
  }

  const startConsumptionAnalysis = async (userId) => {
    if (isLoading.value || isSending.value) {
      return false
    }

    const conversation = await startNewConversation(userId)
    if (!conversation) {
      return false
    }

    return sendMessage(userId, "내 소비를 분석해줘")
  }

  return {
    conversations,
    activeConversation,
    activeConversationId,
    messages,
    activeGoalInterview,
    confirmedGoal,
    isLoading,
    isMessageLoading,
    isSending,
    initialLoading,
    refreshing,
    initialMessageLoading,
    refreshingMessages,
    fetchConversations,
    fetchMessages,
    fetchConfirmedGoal,
    startNewConversation,
    selectConversation,
    completeMessageAnimation,
    renameConversation,
    removeConversation,
    sendMessage,
    startConsumptionAnalysis,
  }
})
