import httpClient from "@/api/httpClient"
import { getApiErrorMessage } from "@/utils/apiError"
import { getAppToday } from "@/utils/appDate"

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

const DEV_MISSION_PREVIEW_KEY = "wallo:dev-mission-preview"

export const clearDevMissionPreview = () => {
  if (typeof sessionStorage === "undefined") return
  sessionStorage.removeItem(DEV_MISSION_PREVIEW_KEY)
}

const readDevMissionPreview = () => {
  if (!import.meta.env.DEV || typeof sessionStorage === "undefined") return null
  try {
    const preview = JSON.parse(sessionStorage.getItem(DEV_MISSION_PREVIEW_KEY) || "null")
    if (!preview?.date) return null
    const today = getAppToday()
    const localDate = [
      today.getFullYear(),
      String(today.getMonth() + 1).padStart(2, "0"),
      String(today.getDate()).padStart(2, "0"),
    ].join("-")
    if (preview.date <= localDate) {
      clearDevMissionPreview()
      return null
    }
    return preview
  } catch {
    clearDevMissionPreview()
    return null
  }
}

const saveDevMissionPreview = (response) => {
  if (!import.meta.env.DEV || typeof sessionStorage === "undefined") return
  sessionStorage.setItem(DEV_MISSION_PREVIEW_KEY, JSON.stringify(response))
}

const normalizeMission = (mission) => ({
  ...mission,
  id: mission.dailyMissionId,
  icon: CATEGORY_ICONS[mission.category] || CATEGORY_ICONS.ETC,
  completed: mission.completed || mission.status === "COMPLETED",
})

const normalizeMissionResponse = (body) => ({
  ...body,
  status: typeof body.status === "string" ? body.status : "READY",
  missions: Array.isArray(body.missions) ? body.missions.map(normalizeMission) : [],
})

const toApiError = (error, fallbackMessage) => {
  const apiError = new Error(getApiErrorMessage(error, fallbackMessage))
  apiError.status = error.response?.status
  apiError.response = error.response
  return apiError
}

export const getTodayMissions = async () => {
  try {
    const preview = readDevMissionPreview()
    if (preview) return normalizeMissionResponse(preview)
    const response = await httpClient.get("/api/missions/today")
    const body = response.data || {}
    return normalizeMissionResponse(body)
  } catch (error) {
    throw toApiError(error, "오늘의 미션을 불러오지 못했습니다.")
  }
}

export const generateNextDayMissions = async () => {
  try {
    const response = await httpClient.post("/api/dev/missions/next-day")
    const body = response.data || {}
    const normalized = normalizeMissionResponse(body)
    saveDevMissionPreview(normalized)
    return normalized
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

export const completeSelfCheckMission = async (dailyMissionId) => {
  try {
    const response = await httpClient.post(`/api/missions/${dailyMissionId}/self-check`)
    return response.data
  } catch (error) {
    throw toApiError(error, "미션 완료 처리에 실패했습니다.")
  }
}

export const verifyTransactionMission = async (dailyMissionId) => {
  try {
    const response = await httpClient.post(
      `/api/missions/${dailyMissionId}/transaction/verify`,
    )
    return response.data
  } catch (error) {
    throw toApiError(error, "거래내역으로 미션을 확인하지 못했습니다.")
  }
}
