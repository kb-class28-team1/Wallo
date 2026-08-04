import httpClient from "@/api/httpClient"
import { getApiErrorMessage } from "@/utils/apiError"

export const getFeeds = async (challengeId, mineOnly = false) => {
  try {
    return (await httpClient.get(`/api/challenges/${challengeId}/feeds`, {
      params: { mineOnly },
    })).data
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "피드를 불러오지 못했습니다."))
  }
}

export const analyzeFeed = async (challengeId, formData) => {
  try {
    return (await httpClient.post(
      `/api/challenges/${challengeId}/feeds/analyze`, formData,
      { headers: { "Content-Type": "multipart/form-data" } },
    )).data
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "AI 분석에 실패했습니다."))
  }
}

export const createFeed = async (challengeId, formData) => {
  try {
    return (await httpClient.post(`/api/challenges/${challengeId}/feeds`, formData, {
      headers: { "Content-Type": "multipart/form-data" },
    })).data
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "피드를 올리지 못했습니다."))
  }
}

export const getRoomMessages = async (challengeId) => {
  try {
    return (await httpClient.get(`/api/challenges/${challengeId}/messages`)).data
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "채팅을 불러오지 못했습니다."))
  }
}

export const sendRoomMessage = async (challengeId, payload) => {
  try {
    await httpClient.post(`/api/challenges/${challengeId}/messages`, payload)
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "메시지를 보내지 못했습니다."))
  }
}
