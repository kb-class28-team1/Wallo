import { ref } from "vue"
import { defineStore } from "pinia"
import { getWeeklyRanking } from "@/api/challengeApi"
import { getCachedResource, getResource, hasInFlightResource } from "@/utils/resourceCache"

const WEEKLY_RANKING_CACHE_KEY = "challenge:weekly-ranking"
const WEEKLY_RANKING_STALE_TIME = 60 * 1000

export const useChallengeStore = defineStore("challenge", () => {
  const startDate = ref("")
  const endDate = ref("")
  const rankings = ref([])
  const myRanking = ref(null)
  const isLoading = ref(false)
  const initialLoading = ref(false)
  const refreshing = ref(false)
  const hasLoadedRanking = ref(false)
  const errorMessage = ref("")

  const applyRanking = (response) => {
    startDate.value = response?.startDate || ""
    endDate.value = response?.endDate || ""
    rankings.value = Array.isArray(response?.rankings) ? response.rankings : []
    myRanking.value = response?.myRanking || null
    hasLoadedRanking.value = true
    return response
  }

  const fetchWeeklyRanking = async ({ force = false } = {}) => {
    const cached =
      !force && !hasInFlightResource(WEEKLY_RANKING_CACHE_KEY)
        ? getCachedResource(WEEKLY_RANKING_CACHE_KEY, {
            staleTime: WEEKLY_RANKING_STALE_TIME,
          })
        : undefined

    if (cached !== undefined) {
      errorMessage.value = ""
      initialLoading.value = false
      refreshing.value = false
      isLoading.value = false
      return applyRanking(cached)
    }

    const isInitialLoad = !hasLoadedRanking.value
    initialLoading.value = isInitialLoad
    refreshing.value = !isInitialLoad
    isLoading.value = isInitialLoad
    errorMessage.value = ""

    try {
      const response = await getResource(WEEKLY_RANKING_CACHE_KEY, () => getWeeklyRanking(), {
        force,
        staleTime: WEEKLY_RANKING_STALE_TIME,
      })
      return applyRanking(response)
    } catch (error) {
      if (isInitialLoad) {
        startDate.value = ""
        endDate.value = ""
        rankings.value = []
        myRanking.value = null
        hasLoadedRanking.value = false
      }
      errorMessage.value = error.message || "주간 랭킹을 불러오지 못했습니다."
      alert(errorMessage.value)
      return null
    } finally {
      initialLoading.value = false
      refreshing.value = false
      isLoading.value = false
    }
  }

  return {
    startDate,
    endDate,
    rankings,
    myRanking,
    isLoading,
    initialLoading,
    refreshing,
    hasLoadedRanking,
    errorMessage,
    fetchWeeklyRanking,
  }
})
