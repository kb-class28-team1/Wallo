import httpClient from "@/api/httpClient"
import { getApiErrorMessage } from "@/utils/apiError"
import { getResource, invalidateResource } from "@/utils/resourceCache"

const CONNECTIONS_CACHE_KEY = "connections:current"
const CONNECTIONS_STALE_TIME = 60 * 1000

export const getConnections = async ({
  force = false,
  staleTime = CONNECTIONS_STALE_TIME,
} = {}) => {
  try {
    return await getResource(
      CONNECTIONS_CACHE_KEY,
      async () => {
        const response = await httpClient.get("/api/connections")
        return response.data?.data ?? response.data
      },
      { force, staleTime },
    )
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "연결된 자산 정보를 불러오지 못했습니다."))
  }
}

export const invalidateConnectionsCache = () => {
  invalidateResource(CONNECTIONS_CACHE_KEY)
}

export const disconnectConnection = async (connectionId) => {
  try {
    const response = await httpClient.delete(`/api/connections/${connectionId}`)
    invalidateConnectionsCache()
    return response.data?.data ?? response.data
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "자산 연결을 해제하지 못했습니다."))
  }
}
