import { beforeEach, describe, expect, it, vi } from "vitest"
import { createPinia, setActivePinia } from "pinia"
import { getMyChallengeDashboard, getMyFeeds } from "@/api/challengeApi"
import { useMyFeedStore } from "@/stores/myFeedStore"

vi.mock("@/api/challengeApi", () => ({
  getMyFeeds: vi.fn(),
  getMyChallengeDashboard: vi.fn(),
}))

const feedResponse = ({ page = 0, totalElements = 1 } = {}) => ({
  content: totalElements
    ? [
        {
          feedId: 1,
          challengeId: 10,
          caption: "도시락 절약",
          category: "FOOD",
          savingAmount: 15000,
          likeCount: 3,
          commentCount: 1,
        },
      ]
    : [],
  page,
  size: 10,
  totalElements,
  totalPages: totalElements ? 1 : 0,
  hasNext: false,
})

describe("내 게시물 스토어", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.stubGlobal("alert", vi.fn())
    getMyFeeds.mockResolvedValue(feedResponse())
    getMyChallengeDashboard.mockResolvedValue({
      postCount: 1,
      receivedLikeCount: 3,
      commentCount: 1,
      totalSavingAmount: 15000,
    })
  })

  it("기본 조건으로 목록과 활동 요약을 함께 조회함", async () => {
    const store = useMyFeedStore()

    await store.initializeMyFeedPage()

    expect(getMyFeeds).toHaveBeenCalledWith({
      sort: "LIKE_DESC",
      category: "ALL",
      page: 0,
      size: 10,
    })
    expect(getMyChallengeDashboard).toHaveBeenCalledWith("6M")
    expect(store.feeds).toHaveLength(1)
    expect(store.summary.totalSavingAmount).toBe(15000)
  })

  it("정렬이나 카테고리를 변경하면 첫 페이지부터 다시 조회함", async () => {
    const store = useMyFeedStore()
    store.page = 2

    await store.changeSort("LATEST")

    expect(getMyFeeds).toHaveBeenLastCalledWith({
      sort: "LATEST",
      category: "ALL",
      page: 0,
      size: 10,
    })

    store.page = 2
    await store.changeCategory("FOOD")

    expect(getMyFeeds).toHaveBeenLastCalledWith({
      sort: "LATEST",
      category: "FOOD",
      page: 0,
      size: 10,
    })
  })

  it("게시물이 없으면 빈 목록과 0페이지 정보를 유지함", async () => {
    getMyFeeds.mockResolvedValue(feedResponse({ totalElements: 0 }))
    const store = useMyFeedStore()

    await store.fetchMyFeeds()

    expect(store.feeds).toEqual([])
    expect(store.totalElements).toBe(0)
    expect(store.totalPages).toBe(0)
  })

  it("API 오류가 발생하면 목록을 비우고 사용자에게 알림을 표시함", async () => {
    getMyFeeds.mockRejectedValue(new Error("목록 조회 실패"))
    const store = useMyFeedStore()

    await store.fetchMyFeeds()

    expect(store.feeds).toEqual([])
    expect(store.errorMessage).toBe("목록 조회 실패")
    expect(alert).toHaveBeenCalledWith("목록 조회 실패")
  })
})
