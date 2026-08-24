import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  completeSelfCheckMission,
  generateNextDayMissions,
  getTodayMissions,
  verifyMissionWithFeed,
  verifyTransactionMission,
} from "@/api/missionApi"
import {
  MAX_MISSION_POLL_ATTEMPTS,
  MISSION_POLL_INTERVAL_MS,
  useMissionStore,
} from "./missionStore"

vi.mock("@/api/missionApi", () => ({
  completeSelfCheckMission: vi.fn(),
  generateNextDayMissions: vi.fn(),
  getTodayMissions: vi.fn(),
  verifyMissionWithFeed: vi.fn(),
  verifyTransactionMission: vi.fn(),
}))

describe("missionStore", () => {
  let store

  beforeEach(() => {
    setActivePinia(createPinia())
    store = useMissionStore()
    getTodayMissions.mockResolvedValue({ status: "READY", missions: [] })
    completeSelfCheckMission.mockResolvedValue({ decision: "PASS" })
    generateNextDayMissions.mockResolvedValue({ status: "READY", missions: [] })
    verifyMissionWithFeed.mockResolvedValue({ decision: "PASS" })
    verifyTransactionMission.mockResolvedValue({ decision: "FAIL" })
    vi.clearAllMocks()
  })

  afterEach(() => {
    store?.stopLifecycle()
    store?.reset()
    vi.useRealTimers()
  })

  it("deduplicates concurrent requests and stores the response", async () => {
    let resolveRequest
    getTodayMissions.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveRequest = resolve
      }),
    )

    const firstRequest = store.fetchTodayMissions()
    const secondRequest = store.fetchTodayMissions()

    expect(getTodayMissions).toHaveBeenCalledTimes(1)

    resolveRequest({
      status: "READY",
      missions: [{ id: 1, title: "오늘 미션", completed: false }],
    })
    await Promise.all([firstRequest, secondRequest])

    expect(store.missions).toHaveLength(1)
    expect(store.missions[0].title).toBe("오늘 미션")
    expect(store.isLoading).toBe(false)
  })

  it("uses the fresh response until a forced refresh is requested", async () => {
    await store.fetchTodayMissions()
    await store.fetchTodayMissions()

    expect(getTodayMissions).toHaveBeenCalledTimes(1)

    await store.fetchTodayMissions({ force: true })

    expect(getTodayMissions).toHaveBeenCalledTimes(2)
  })

  it("applies a response included in the update event without another GET", async () => {
    await store.fetchTodayMissions()
    vi.clearAllMocks()

    const response = {
      status: "READY",
      missions: [{ id: 7, title: "생성된 미션", completed: false }],
    }
    await store.handleMissionUpdated({ detail: { missionResponse: response } })

    expect(getTodayMissions).not.toHaveBeenCalled()
    expect(store.missions[0].title).toBe("생성된 미션")
  })

  it("refreshes the shared state after completing a self-check mission", async () => {
    await store.completeSelfCheckMission(31)

    expect(completeSelfCheckMission).toHaveBeenCalledWith(31)
    expect(getTodayMissions).toHaveBeenCalledTimes(1)
  })

  it("refreshes the shared state after feed verification", async () => {
    await store.verifyMissionWithFeed(31, 42)

    expect(verifyMissionWithFeed).toHaveBeenCalledWith(31, 42)
    expect(getTodayMissions).toHaveBeenCalledTimes(1)
  })

  it("verifies pending transaction missions and refreshes after a pass", async () => {
    getTodayMissions
      .mockResolvedValueOnce({
        status: "READY",
        missions: [{ id: 9, verificationType: "TRANSACTION", completed: false }],
      })
      .mockResolvedValueOnce({
        status: "READY",
        missions: [{ id: 9, verificationType: "TRANSACTION", completed: true }],
      })
    verifyTransactionMission.mockResolvedValue({ decision: "PASS", rewardedPoint: 10 })

    const result = await store.refreshTodayMissions({ verifyTransactions: true })

    expect(verifyTransactionMission).toHaveBeenCalledWith(9)
    expect(result.verificationResults[0].value.rewardedPoint).toBe(10)
    expect(getTodayMissions).toHaveBeenCalledTimes(2)
    expect(store.missions[0].completed).toBe(true)
  })

  it("polls pending mission generation and stops when generation is complete", async () => {
    getTodayMissions
      .mockResolvedValueOnce({ status: "WAITING_ANALYSIS", missions: [] })
      .mockResolvedValueOnce({ status: "READY", missions: [] })
    vi.useFakeTimers()

    await store.fetchTodayMissions()
    expect(store.isPolling).toBe(true)

    await vi.advanceTimersByTimeAsync(MISSION_POLL_INTERVAL_MS)

    expect(getTodayMissions).toHaveBeenCalledTimes(2)
    expect(store.status).toBe("READY")
    expect(store.isPolling).toBe(false)
  })

  it("stops polling after the configured maximum attempts", async () => {
    getTodayMissions.mockResolvedValue({ status: "WAITING_ANALYSIS", missions: [] })
    vi.useFakeTimers()

    await store.fetchTodayMissions()
    await vi.advanceTimersByTimeAsync(
      MISSION_POLL_INTERVAL_MS * MAX_MISSION_POLL_ATTEMPTS,
    )

    expect(getTodayMissions).toHaveBeenCalledTimes(MAX_MISSION_POLL_ATTEMPTS + 1)
    expect(store.isPolling).toBe(false)
  })
})
