import httpClient from "@/api/httpClient"
import { getApiErrorMessage } from "@/commonUtils/apiError"

export const getConnections = async () => {
  try {
    const response = await httpClient.get("/api/connections")
    return response.data?.data ?? response.data
  } catch (error) {
    throw new Error(
      getApiErrorMessage(error, "연결된 자산 정보를 불러오지 못했습니다."),
    )
  }
}

export const disconnectConnection = async (connectionId) => {
  try {
    const response = await httpClient.delete(`/api/connections/${connectionId}`)
    return response.data?.data ?? response.data
  } catch (error) {
    throw new Error(
      getApiErrorMessage(error, "자산 연결을 해제하지 못했습니다."),
    )
  }
}
