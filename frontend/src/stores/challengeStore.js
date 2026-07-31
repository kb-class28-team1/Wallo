import { ref } from "vue"
import { defineStore } from "pinia"
import { getWeeklyRanking } from "@/api/challengeApi"

export const useChallengeStore = defineStore("challenge", () => {
  // 백엔드가 반환한 이번 주 월요일과 일요일 날짜임
  const startDate = ref("")
  const endDate = ref("")

  // 주간 전체 랭킹 목록과 로그인 사용자의 순위 정보임
  const rankings = ref([])
  const myRanking = ref(null)

  // 화면에서 로딩과 오류 상태를 표시하기 위한 값임
  const isLoading = ref(false)
  const errorMessage = ref("")

  // 주간 랭킹 API를 호출하고 여러 화면에서 사용할 수 있도록 Pinia 상태에 저장함
  const fetchWeeklyRanking = async () => {
    if (isLoading.value) {
      return
    }

    isLoading.value = true
    errorMessage.value = ""

    try {
      const response = await getWeeklyRanking()

      startDate.value = response?.startDate || ""
      endDate.value = response?.endDate || ""
      rankings.value = Array.isArray(response?.rankings) ? response.rankings : []
      myRanking.value = response?.myRanking || null

      return response
    } catch (error) {
      // 이전 조회 결과가 실패한 요청 뒤에 남지 않도록 랭킹 상태를 초기화함
      startDate.value = ""
      endDate.value = ""
      rankings.value = []
      myRanking.value = null

      errorMessage.value = error.message || "주간 랭킹을 불러오지 못했습니다."
      alert(errorMessage.value)
    } finally {
      isLoading.value = false
    }
  }

  return {
    startDate,
    endDate,
    rankings,
    myRanking,
    isLoading,
    errorMessage,
    fetchWeeklyRanking,
  }
})
