import httpClient from "@/api/httpClient"
import { getApiErrorMessage } from "@/utils/apiError"

export const getLatestAnalysisResults = async () => {
  try {
    const response = await httpClient.get("/api/analysis-results/latest")
    if (response.data?.success === false) {
      throw new Error(response.data.error?.message || "최신 분석 결과를 불러오지 못했습니다.")
    }
    return response.data?.data ?? null
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "최신 분석 결과를 불러오지 못했습니다."))
  }
}
