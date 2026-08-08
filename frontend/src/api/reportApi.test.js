import { afterEach, describe, expect, it, vi } from "vitest"
import httpClient from "@/api/httpClient"
import { generateReportsNow } from "./reportApi"

vi.mock("@/api/httpClient", () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

describe("reportApi", () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it("requests immediate crawling and financial report generation", async () => {
    const result = {
      started: true,
      crawledNewsCount: 3,
      generatedReportCount: 2,
    }
    httpClient.post.mockResolvedValue({ data: { success: true, data: result } })

    await expect(generateReportsNow()).resolves.toEqual(result)
    expect(httpClient.post).toHaveBeenCalledWith("/api/reports/generate-now")
  })

  it("converts a manual generation failure into a user-facing error", async () => {
    httpClient.post.mockRejectedValue({
      response: { data: { error: { message: "리포트 생성 서버가 응답하지 않습니다." } } },
    })

    await expect(generateReportsNow()).rejects.toThrow("리포트 생성 서버가 응답하지 않습니다.")
  })
})
