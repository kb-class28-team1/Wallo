import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { getWeeklyRanking } from "@/api/challengeApi"
import { clearResourceCache } from "@/utils/resourceCache"
import { useChallengeStore } from "./challengeStore"

vi.mock("@/api/challengeApi", () => ({
  getWeeklyRanking: vi.fn(),
}))

const ranking = {
  startDate: "2026-08-10",
  endDate: "2026-08-16",
  rankings: [{ rank: 1, nickname: "Wallo", score: 100 }],
  myRanking: { rank: 3, score: 80 },
}

describe("challengeStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    clearResourceCache()
    getWeeklyRanking.mockResolvedValue(ranking)
    vi.stubGlobal("alert", vi.fn())
  })

  afterEach(() => {
    clearResourceCache()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it("caches weekly rankings and uses refreshing state for a forced request", async () => {
    const store = useChallengeStore()

    await expect(store.fetchWeeklyRanking()).resolves.toEqual(ranking)
    await expect(store.fetchWeeklyRanking()).resolves.toEqual(ranking)

    expect(getWeeklyRanking).toHaveBeenCalledOnce()
    expect(store.rankings).toEqual(ranking.rankings)
    expect(store.myRanking).toEqual(ranking.myRanking)
    expect(store.initialLoading).toBe(false)

    const refreshRequest = store.fetchWeeklyRanking({ force: true })
    expect(store.refreshing).toBe(true)

    await refreshRequest

    expect(getWeeklyRanking).toHaveBeenCalledTimes(2)
    expect(store.refreshing).toBe(false)
  })

  it("keeps the previous ranking when a refresh fails", async () => {
    await useChallengeStore().fetchWeeklyRanking()
    getWeeklyRanking.mockRejectedValue(new Error("ranking unavailable"))
    const store = useChallengeStore()

    await expect(store.fetchWeeklyRanking({ force: true })).resolves.toBeNull()

    expect(store.rankings).toEqual(ranking.rankings)
    expect(store.errorMessage).toBe("ranking unavailable")
    expect(alert).toHaveBeenCalledWith("ranking unavailable")
  })
})
