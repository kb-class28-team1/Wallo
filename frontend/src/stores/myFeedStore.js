import { computed, ref } from "vue"
import { defineStore } from "pinia"
import { getMyChallengeDashboard, getMyFeeds } from "@/api/challengeApi"

const DEFAULT_SORT = "LIKE_DESC"
const DEFAULT_CATEGORY = "ALL"
const DEFAULT_PAGE_SIZE = 10

export const useMyFeedStore = defineStore("myFeed", () => {
  // 내 게시물 카드 목록과 화면 상단의 게시물 통계 정보임.
  const feeds = ref([])
  const summary = ref(null)

  // 사용자가 선택한 정렬, 카테고리와 현재 페이지 상태임.
  const sort = ref(DEFAULT_SORT)
  const category = ref(DEFAULT_CATEGORY)
  const page = ref(0)
  const size = ref(DEFAULT_PAGE_SIZE)
  const totalElements = ref(0)
  const totalPages = ref(0)
  const hasNext = ref(false)

  // 목록과 요약 정보를 각각 불러올 때 로딩 상태를 구분함.
  const isFeedLoading = ref(false)
  const isSummaryLoading = ref(false)
  const errorMessage = ref("")
  const isLoading = computed(
    () => isFeedLoading.value || isSummaryLoading.value,
  )

  // 현재 선택된 조회 조건으로 내 게시물 목록 API를 호출함.
  const fetchMyFeeds = async () => {
    isFeedLoading.value = true
    errorMessage.value = ""

    try {
      const response = await getMyFeeds({
        sort: sort.value,
        category: category.value,
        page: page.value,
        size: size.value,
      })

      feeds.value = Array.isArray(response?.content) ? response.content : []
      page.value = response?.page ?? page.value
      size.value = response?.size ?? size.value
      totalElements.value = response?.totalElements ?? 0
      totalPages.value = response?.totalPages ?? 0
      hasNext.value = Boolean(response?.hasNext)

      return response
    } catch (error) {
      // 이전 목록이 오류 화면에 남지 않도록 게시물과 페이지 정보를 초기화함.
      feeds.value = []
      totalElements.value = 0
      totalPages.value = 0
      hasNext.value = false
      errorMessage.value = error.message || "내 게시물 목록을 불러오지 못했습니다."
      alert(errorMessage.value)
      return null
    } finally {
      isFeedLoading.value = false
    }
  }

  // 기존 내 챌린지 요약 API를 재사용해 게시글, 좋아요, 댓글, 절약 금액을 조회함.
  const fetchMyFeedSummary = async () => {
    isSummaryLoading.value = true
    errorMessage.value = ""

    try {
      const response = await getMyChallengeDashboard("6M")
      summary.value = response || null
      return response
    } catch (error) {
      summary.value = null
      errorMessage.value = error.message || "내 게시물 요약을 불러오지 못했습니다."
      alert(errorMessage.value)
      return null
    } finally {
      isSummaryLoading.value = false
    }
  }

  // 화면에 처음 들어왔을 때 목록과 요약 정보를 동시에 조회함.
  const initializeMyFeedPage = async () => {
    await Promise.all([fetchMyFeeds(), fetchMyFeedSummary()])
  }

  // 정렬 조건이 바뀌면 첫 페이지부터 다시 조회함.
  const changeSort = async (nextSort) => {
    sort.value = nextSort || DEFAULT_SORT
    page.value = 0
    await fetchMyFeeds()
  }

  // 카테고리가 바뀌면 첫 페이지부터 다시 조회함.
  const changeCategory = async (nextCategory) => {
    category.value = nextCategory || DEFAULT_CATEGORY
    page.value = 0
    await fetchMyFeeds()
  }

  // 화면에서 요청한 페이지가 조회 가능한 범위일 때만 이동함.
  const changePage = async (nextPage) => {
    if (nextPage < 0 || (totalPages.value > 0 && nextPage >= totalPages.value)) {
      return
    }

    page.value = nextPage
    await fetchMyFeeds()
  }

  return {
    feeds,
    summary,
    sort,
    category,
    page,
    size,
    totalElements,
    totalPages,
    hasNext,
    isLoading,
    isFeedLoading,
    isSummaryLoading,
    errorMessage,
    fetchMyFeeds,
    fetchMyFeedSummary,
    initializeMyFeedPage,
    changeSort,
    changeCategory,
    changePage,
  }
})
