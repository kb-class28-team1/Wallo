import { afterEach, describe, expect, it, vi } from "vitest"
import httpClient from "@/api/httpClient"
import { sendConversationMessage } from "./conversationApi"

vi.mock("@/api/httpClient", () => ({
  default: { post: vi.fn() },
}))

describe("conversationApi", () => {
  afterEach(() => vi.clearAllMocks())

  it("sends an explicit chat mode when provided", async () => {
    const response = { answer: "어떤 목표를 세우고 싶으세요?" }
    httpClient.post.mockResolvedValue({ data: response })

    await expect(
      sendConversationMessage(12, 7, "새 목표를 만들고 싶어요", "GOAL_SETTING"),
    ).resolves.toEqual(response)

    expect(httpClient.post).toHaveBeenCalledWith("/api/conversations/12/messages", {
      userId: 7,
      message: "새 목표를 만들고 싶어요",
      chatMode: "GOAL_SETTING",
    })
  })

  it("omits chat mode for regular messages", async () => {
    httpClient.post.mockResolvedValue({ data: { answer: "일반 답변" } })

    await sendConversationMessage(12, 7, "안녕하세요")

    expect(httpClient.post).toHaveBeenCalledWith("/api/conversations/12/messages", {
      userId: 7,
      message: "안녕하세요",
    })
  })
})
