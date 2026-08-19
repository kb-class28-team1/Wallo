import httpClient from "@/api/httpClient"
import { getApiErrorMessage } from "@/utils/apiError"

const normalizeRequestError = (error, fallbackMessage) => {
  const normalizedError = new Error(getApiErrorMessage(error, fallbackMessage))
  normalizedError.cause = error
  return normalizedError
}

export const getLatestProductRecommendation = async () => {
  try {
    const response = await httpClient.get("/api/product-recommendations/latest")
    return response.data
  } catch (error) {
    throw normalizeRequestError(
      error,
      "최신 상품 추천 결과를 불러오지 못했습니다.",
    )
  }
}
