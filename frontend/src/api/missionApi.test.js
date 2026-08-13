import { afterEach, describe, expect, it, vi } from "vitest"
import httpClient from "@/api/httpClient"
import {
  generateNextDayMissions,
  getTodayMissions,
  verifyMissionWithFeed,
} from "./missionApi"

vi.mock("@/api/httpClient", () => ({
  default: { get: vi.fn(), post: vi.fn() },
}))

describe("missionApi", () => {
  afterEach(() => vi.clearAllMocks())

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
})
