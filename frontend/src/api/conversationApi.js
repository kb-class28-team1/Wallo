import httpClient from "./httpClient"
import { getApiErrorMessage } from "@/commonUtils/apiError"

const toApiError = (error, fallbackMessage) => {
  const apiError = new Error(getApiErrorMessage(error, fallbackMessage))
  apiError.status = error.response?.status
  apiError.response = error.response
  return apiError
}

const createRequestId = () => {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `wallo-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export const getConversations = async (userId) => {
  try {
    const response = await httpClient.get("/api/conversations", {
      params: { userId },
    })
    return response.data
  } catch (error) {
    throw toApiError(error, "채팅방 목록을 불러오지 못했습니다.")
  }
}

export const createConversation = async (userId, title = "새 채팅") => {
  try {
    const response = await httpClient.post("/api/conversations", {
      userId,
      title,
    })
    return response.data
  } catch (error) {
    throw toApiError(error, "새 채팅방을 만들지 못했습니다.")
  }
}

export const getConversationMessages = async (conversationId, userId) => {
  try {
    const response = await httpClient.get(
      `/api/conversations/${conversationId}/messages`,
      { params: { userId } },
    )
    return response.data
  } catch (error) {
    throw toApiError(error, "대화 내용을 불러오지 못했습니다.")
  }
}

export const getActiveGoalInterview = async (conversationId) => {
  try {
    const response = await httpClient.get(
      `/api/conversations/${conversationId}/goal-interview`,
    )
    return response.data
  } catch (error) {
    throw toApiError(error, "진행 중인 목표 설정을 불러오지 못했습니다.")
  }
}

export const sendConversationMessage = async (
  conversationId,
  userId,
  message,
  chatMode = null,
  requestId = null,
) => {
  try {
    const effectiveRequestId = requestId || createRequestId()
    const payload = {
      userId,
      message,
      ...(chatMode ? { chatMode } : {}),
    }
    const url = `/api/conversations/${conversationId}/messages`
    const response = await httpClient.post(url, payload, {
      headers: { "X-Request-Id": effectiveRequestId },
    })
    return response.data
  } catch (error) {
    throw toApiError(error, "메시지를 전송하지 못했습니다.")
  }
}

export const updateConversationTitle = async (
  conversationId,
  userId,
  title,
) => {
  try {
    const response = await httpClient.patch(
      `/api/conversations/${conversationId}`,
      { userId, title },
    )
    return response.data
  } catch (error) {
    throw toApiError(error, "채팅방 제목을 변경하지 못했습니다.")
  }
}

export const deleteConversation = async (conversationId, userId) => {
  try {
    await httpClient.delete(`/api/conversations/${conversationId}`, {
      params: { userId },
    })
  } catch (error) {
    throw toApiError(error, "채팅방을 삭제하지 못했습니다.")
  }
}
