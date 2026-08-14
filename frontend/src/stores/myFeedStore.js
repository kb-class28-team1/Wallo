import { computed, ref } from "vue"
import { defineStore } from "pinia"
import { getMyChallengeDashboard, getMyFeeds } from "@/api/challengeApi"
import { getCachedResource, getResource, hasInFlightResource } from "@/utils/resourceCache"

const DEFAULT_SORT = "LIKE_DESC"
const DEFAULT_CATEGORY = "ALL"
const DEFAULT_PAGE_SIZE = 10
const MY_FEED_STALE_TIME = 60 * 1000
const MY_DASHBOARD_STALE_TIME = 60 * 1000

const getFeedCacheKey = (sort, category, page, size) =>
  `challenge:my-feeds:current:${sort}:${category}:${page}:${size}`
const MY_DASHBOARD_CACHE_KEY = "challenge:my-dashboard:current:6M"

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
  const initialFeedLoading = ref(false)
  const refreshingFeed = ref(false)
  const hasLoadedFeeds = ref(false)
  const initialSummaryLoading = ref(false)
  const refreshingSummary = ref(false)
  const hasLoadedSummary = ref(false)
  const errorMessage = ref("")
  const isLoading = computed(() => isFeedLoading.value || isSummaryLoading.value)

  const applyFeeds = (response) => {
    feeds.value = Array.isArray(response?.content) ? response.content : []
    page.value = response?.page ?? page.value
    size.value = response?.size ?? size.value
    totalElements.value = response?.totalElements ?? 0
    totalPages.value = response?.totalPages ?? 0
    hasNext.value = Boolean(response?.hasNext)
    hasLoadedFeeds.value = true
    return response
  }

  const applySummary = (response) => {
    summary.value = response || null
    hasLoadedSummary.value = true
    return response
  }

  // 현재 선택된 조회 조건으로 내 게시물 목록 API를 호출함.
  const fetchMyFeeds = async ({ force = false } = {}) => {
    const requestedSort = sort.value
    const requestedCategory = category.value
    const requestedPage = page.value
    const requestedSize = size.value
    const cacheKey = getFeedCacheKey(requestedSort, requestedCategory, requestedPage, requestedSize)
    const cached =
      !force && !hasInFlightResource(cacheKey)
        ? getCachedResource(cacheKey, { staleTime: MY_FEED_STALE_TIME })
        : undefined

    if (cached !== undefined) {
      errorMessage.value = ""
      initialFeedLoading.value = false
      refreshingFeed.value = false
      isFeedLoading.value = false
      return applyFeeds(cached)
    }

    const isInitialLoad = !hasLoadedFeeds.value
    initialFeedLoading.value = isInitialLoad
    refreshingFeed.value = !isInitialLoad
    isFeedLoading.value = true
    errorMessage.value = ""

    try {
      const response = await getResource(
        cacheKey,
        () =>
          getMyFeeds({
            sort: requestedSort,
            category: requestedCategory,
            page: requestedPage,
            size: requestedSize,
          }),
        {
          force,
          staleTime: MY_FEED_STALE_TIME,
        },
      )

      return applyFeeds(response)
    } catch (error) {
      // 갱신 중 오류가 나면 이전 목록을 유지해 화면이 빈 상태로 바뀌지 않게 함.
      if (isInitialLoad) {
        feeds.value = []
        totalElements.value = 0
        totalPages.value = 0
        hasNext.value = false
        hasLoadedFeeds.value = false
      }
      errorMessage.value = error.message || "내 게시물 목록을 불러오지 못했습니다."
      alert(errorMessage.value)
      return null
    } finally {
      initialFeedLoading.value = false
      refreshingFeed.value = false
      isFeedLoading.value = false
    }
  }

  // 기존 내 챌린지 요약 API를 재사용해 게시글, 좋아요, 댓글, 절약 금액을 조회함.
  const fetchMyFeedSummary = async ({ force = false } = {}) => {
    const cached =
      !force && !hasInFlightResource(MY_DASHBOARD_CACHE_KEY)
        ? getCachedResource(MY_DASHBOARD_CACHE_KEY, {
            staleTime: MY_DASHBOARD_STALE_TIME,
          })
        : undefined

    if (cached !== undefined) {
      errorMessage.value = ""
      initialSummaryLoading.value = false
      refreshingSummary.value = false
      isSummaryLoading.value = false
      return applySummary(cached)
    }

    const isInitialLoad = !hasLoadedSummary.value
    initialSummaryLoading.value = isInitialLoad
    refreshingSummary.value = !isInitialLoad
    isSummaryLoading.value = true
    errorMessage.value = ""

    try {
      const response = await getResource(
        MY_DASHBOARD_CACHE_KEY,
        () => getMyChallengeDashboard("6M"),
        {
          force,
          staleTime: MY_DASHBOARD_STALE_TIME,
        },
      )
      return applySummary(response)
    } catch (error) {
      if (isInitialLoad) {
        summary.value = null
        hasLoadedSummary.value = false
      }
      errorMessage.value = error.message || "내 게시물 요약을 불러오지 못했습니다."
      alert(errorMessage.value)
      return null
    } finally {
      initialSummaryLoading.value = false
      refreshingSummary.value = false
      isSummaryLoading.value = false
    }
  }

  // 화면에 처음 들어왔을 때 목록과 요약 정보를 동시에 조회함.
  const initializeMyFeedPage = async ({ force = false } = {}) => {
    await Promise.all([fetchMyFeeds({ force }), fetchMyFeedSummary({ force })])
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
    initialFeedLoading,
    refreshingFeed,
    hasLoadedFeeds,
    initialSummaryLoading,
    refreshingSummary,
    hasLoadedSummary,
    errorMessage,
    fetchMyFeeds,
    fetchMyFeedSummary,
    initializeMyFeedPage,
    changeSort,
    changeCategory,
    changePage,
  }
})
