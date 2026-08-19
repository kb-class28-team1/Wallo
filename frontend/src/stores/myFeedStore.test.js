import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { getMyChallengeDashboard, getMyFeeds } from "@/api/challengeApi"
import { clearResourceCache } from "@/utils/resourceCache"
import { useMyFeedStore } from "./myFeedStore"

vi.mock("@/api/challengeApi", () => ({
  getMyChallengeDashboard: vi.fn(),
  getMyFeeds: vi.fn(),
}))

const feedResponse = {
  content: [{ postId: 1, title: "절약 기록" }],
  page: 0,
  size: 10,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
}

const summaryResponse = {
  postCount: 1,
  likeCount: 4,
  commentCount: 2,
  savedAmount: 30_000,
}

describe("myFeedStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    clearResourceCache()
    getMyFeeds.mockResolvedValue(feedResponse)
    getMyChallengeDashboard.mockResolvedValue(summaryResponse)
    vi.stubGlobal("alert", vi.fn())
  })

  afterEach(() => {
    clearResourceCache()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it("loads the feed and summary together, then reuses their caches", async () => {
    const store = useMyFeedStore()

    await store.initializeMyFeedPage()
    await store.initializeMyFeedPage()

    expect(getMyFeeds).toHaveBeenCalledOnce()
    expect(getMyFeeds).toHaveBeenCalledWith({
      sort: "LIKE_DESC",
      category: "ALL",
      page: 0,
      size: 10,
    })
    expect(getMyChallengeDashboard).toHaveBeenCalledWith("6M")
    expect(getMyChallengeDashboard).toHaveBeenCalledOnce()
    expect(store.feeds).toEqual(feedResponse.content)
    expect(store.summary).toEqual(summaryResponse)
    expect(store.initialLoading).toBe(false)
  })

  it("resets the page and requests the selected category", async () => {
    const store = useMyFeedStore()

    await store.fetchMyFeeds()
    await store.changeCategory("SAVING")

    expect(getMyFeeds).toHaveBeenCalledTimes(2)
    expect(getMyFeeds).toHaveBeenLastCalledWith({
      sort: "LIKE_DESC",
      category: "SAVING",
      page: 0,
      size: 10,
    })
    expect(store.category).toBe("SAVING")
    expect(store.page).toBe(0)
  })
})
