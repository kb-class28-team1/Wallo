import { computed, ref } from "vue"
import { defineStore } from "pinia"
import { getLatestProductRecommendation } from "@/api/productRecommendationApi"
import { normalizeProductRecommendation } from "@/types/productRecommendation"
import { getApiErrorMessage } from "@/utils/apiError"

const RECOMMENDATION_STALE_TIME = 60 * 1000

export const useProductRecommendationStore = defineStore(
  "productRecommendation",
  () => {
    const productRecommendation = ref(null)
    const recommendationReason = ref("")
    const requestMessage = ref("")
    const generatedAt = ref(null)
    const isLoading = ref(false)
    const error = ref(null)
    const hasFetched = ref(false)
    const lastFetchedAt = ref(0)
    let request = null

    const status = computed(() => {
      if (isLoading.value) return "loading"
      if (error.value) return "error"
      if (!hasFetched.value) return "idle"
      return productRecommendation.value ? "success" : "empty"
    })

    const clearResult = () => {
      productRecommendation.value = null
      recommendationReason.value = ""
      requestMessage.value = ""
      generatedAt.value = null
    }

    const applyResult = (payload) => {
      if (!payload || typeof payload !== "object") {
        clearResult()
        return
      }

      const rawRecommendation =
        payload.productRecommendation ?? payload.recommendation ?? payload
      productRecommendation.value = normalizeProductRecommendation(rawRecommendation)
      recommendationReason.value = typeof payload.aiResponse === "string"
        ? payload.aiResponse
        : ""
      requestMessage.value = typeof payload.requestMessage === "string"
        ? payload.requestMessage
        : ""
      generatedAt.value = payload.generatedAt ?? null
    }

    const fetchLatest = ({ force = false } = {}) => {
      if (request) return request

      const isFresh =
        hasFetched.value &&
        Date.now() - lastFetchedAt.value < RECOMMENDATION_STALE_TIME
      if (!force && isFresh) {
        return Promise.resolve(productRecommendation.value)
      }

      isLoading.value = true
      error.value = null
      request = (async () => {
        try {
          const response = await getLatestProductRecommendation()
          if (response?.success === false) {
            throw new Error(
              response.error?.message || "최신 상품 추천 결과를 불러오지 못했습니다.",
            )
          }
          applyResult(response?.data ?? null)
          hasFetched.value = true
          lastFetchedAt.value = Date.now()
          return productRecommendation.value
        } catch (caughtError) {
          clearResult()
          error.value = getApiErrorMessage(
            caughtError,
            "최신 상품 추천 결과를 불러오지 못했습니다.",
          )
          alert(error.value)
          return null
        } finally {
          isLoading.value = false
          request = null
        }
      })()

      return request
    }

    const reset = () => {
      clearResult()
      isLoading.value = false
      error.value = null
      hasFetched.value = false
      lastFetchedAt.value = 0
      request = null
    }

    return {
      productRecommendation,
      recommendationReason,
      requestMessage,
      generatedAt,
      isLoading,
      error,
      hasFetched,
      status,
      fetchLatest,
      reset,
    }
  },
)
