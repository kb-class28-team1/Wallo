import { afterEach, describe, expect, it, vi } from "vitest"
import httpClient from "@/api/httpClient"
import {
  generateNextDayMissions,
  getTodayMissions,
  verifyMissionWithFeed,
  completeSelfCheckMission,
  verifyTransactionMission,
} from "./missionApi"

vi.mock("@/api/httpClient", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}))

describe("missionApi", () => {
  afterEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  it("loads and normalizes today's missions", async () => {
    httpClient.get.mockResolvedValue({
      data: {
        date: "2026-08-17",
        missions: [{
          dailyMissionId: 3,
          title: "집밥 먹기",
          category: "FOOD",
          status: "ASSIGNED",
          rewardPoint: 10,
        }],
      },
    })

    const result = await getTodayMissions()

    expect(httpClient.get).toHaveBeenCalledWith("/api/missions/today")
    expect(result.missions[0]).toMatchObject({ id: 3, icon: "🍚" })
  })

  it("preserves WAITING_ANALYSIS responses with an empty mission list", async () => {
    httpClient.get.mockResolvedValue({
      data: {
        date: "2026-08-17",
        status: "WAITING_ANALYSIS",
        missions: [],
      },
    })

    const result = await getTodayMissions()

    expect(result).toMatchObject({
      status: "WAITING_ANALYSIS",
      missions: [],
    })
  })

  it("generates and normalizes next-day missions for development", async () => {
    httpClient.post.mockResolvedValue({
      data: {
        date: "2026-08-18",
        missions: [{ dailyMissionId: 4, category: "CAFE", status: "ASSIGNED" }],
      },
    })
    const result = await generateNextDayMissions()
    expect(httpClient.post).toHaveBeenCalledWith("/api/dev/missions/next-day")
    expect(result.missions[0]).toMatchObject({ id: 4, icon: "☕" })
  })

  it("keeps a generated future mission preview on subsequent loads", async () => {
    const futureDate = new Date()
    futureDate.setDate(futureDate.getDate() + 1)
    const date = [
      futureDate.getFullYear(),
      String(futureDate.getMonth() + 1).padStart(2, "0"),
      String(futureDate.getDate()).padStart(2, "0"),
    ].join("-")
    httpClient.post.mockResolvedValue({
      data: {
        date,
        missions: [{ dailyMissionId: 9, category: "FOOD", title: "다음날 미션" }],
      },
    })

    await generateNextDayMissions()
    const result = await getTodayMissions()

    expect(result.date).toBe(date)
    expect(result.missions[0].title).toBe("다음날 미션")
    expect(httpClient.get).not.toHaveBeenCalled()
  })

  it("verifies a mission using an already uploaded feed", async () => {
    httpClient.post.mockResolvedValue({
      data: { decision: "PASS", missionStatus: "COMPLETED", rewardedPoint: 10 },
    })

    await verifyMissionWithFeed(3, 21)

    expect(httpClient.post).toHaveBeenCalledWith(
      "/api/missions/3/verify",
      null,
      { params: { feedId: 21 } },
    )
  })

  it("completes a self-check mission", async () => {
    httpClient.post.mockResolvedValue({ data: { decision: "PASS" } })
    await completeSelfCheckMission(3)
    expect(httpClient.post).toHaveBeenCalledWith("/api/missions/3/self-check")
  })

  it("verifies a transaction mission", async () => {
    httpClient.post.mockResolvedValue({ data: { decision: "PASS" } })
    await verifyTransactionMission(3)
    expect(httpClient.post).toHaveBeenCalledWith("/api/missions/3/transaction/verify")
  })
})
