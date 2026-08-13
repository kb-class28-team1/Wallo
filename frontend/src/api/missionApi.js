import httpClient from "@/api/httpClient"
import { getApiErrorMessage } from "@/commonUtils/apiError"

const CATEGORY_ICONS = {
  FOOD: "🍚",
  CAFE: "☕",
  DELIVERY: "🥡",
  TRANSPORT: "🚌",
  SHOPPING: "🛍️",
  HOUSING: "🏠",
  LIVING: "🌱",
  CULTURE: "🎬",
  HEALTH: "💪",
  ETC: "✨",
}

const normalizeMission = (mission) => ({
  ...mission,
  id: mission.dailyMissionId,
  icon: CATEGORY_ICONS[mission.category] || CATEGORY_ICONS.ETC,
  completed: mission.completed || mission.status === "COMPLETED",
})

const toApiError = (error, fallbackMessage) => {
  const apiError = new Error(getApiErrorMessage(error, fallbackMessage))
  apiError.status = error.response?.status
  apiError.response = error.response
  return apiError
}

export const getTodayMissions = async () => {
  try {
    const response = await httpClient.get("/api/missions/today")
    const body = response.data || {}
    return {
      ...body,
      missions: Array.isArray(body.missions) ? body.missions.map(normalizeMission) : [],
    }
  } catch (error) {
    throw toApiError(error, "오늘의 미션을 불러오지 못했습니다.")
  }
}

export const generateNextDayMissions = async () => {
  try {
    const response = await httpClient.post("/api/dev/missions/next-day")
    const body = response.data || {}
    return {
      ...body,
      missions: Array.isArray(body.missions) ? body.missions.map(normalizeMission) : [],
    }
  } catch (error) {
    throw toApiError(error, "다음날 미션 생성에 실패했습니다.")
  }
}

export const verifyMissionWithFeed = async (dailyMissionId, feedId) => {
  try {
    const response = await httpClient.post(
      `/api/missions/${dailyMissionId}/verify`,
      null,
      { params: { feedId } },
    )
    return response.data
  } catch (error) {
    throw toApiError(error, "미션 인증에 실패했습니다.")
  }
}
