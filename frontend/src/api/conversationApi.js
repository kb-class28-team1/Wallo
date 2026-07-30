import httpClient from "./httpClient"

export const getConversations = async (userId) => {
  try {
    const response = await httpClient.get("/api/conversations", {
      params: { userId },
    })
    return response.data
  } catch (error) {
    const message =
      error.response?.data?.error?.message ||
      error.response?.data?.message ||
      "채팅방 목록을 불러오지 못했습니다."
    throw new Error(message)
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
    const message =
      error.response?.data?.error?.message ||
      error.response?.data?.message ||
      "새 채팅방을 만들지 못했습니다."
    throw new Error(message)
  }
}
