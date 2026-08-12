import { afterEach, describe, expect, it, vi } from "vitest"
import httpClient from "@/api/httpClient"
import {
  generateMissionCycle,
  getTodayMissions,
  previewMissionGeneration,
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
          difficulty: "EASY",
          status: "ASSIGNED",
          rewardPoint: 10,
        }],
      },
    })

    const result = await getTodayMissions()

    expect(httpClient.get).toHaveBeenCalledWith("/api/missions/today")
    expect(result.missions[0]).toMatchObject({ id: 3, icon: "🍚", difficulty: "쉬움" })
  })

  it("requests a dry preview without saving", async () => {
    httpClient.post.mockResolvedValue({ data: { missions: Array(30), promptVersion: "v1" } })
    await previewMissionGeneration()
    expect(httpClient.post).toHaveBeenCalledWith("/api/dev/missions/preview")
  })

  it("requests current cycle generation", async () => {
    httpClient.post.mockResolvedValue({ data: { missionCount: 30, status: "ACTIVE" } })
    await generateMissionCycle()
    expect(httpClient.post).toHaveBeenCalledWith("/api/dev/missions/generate")
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
