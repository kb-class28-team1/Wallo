import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import {
  createConversation,
  getActiveGoalInterview,
  getConversationMessages,
  getConversations,
  sendConversationMessage,
} from "@/api/conversationApi"
import { useConversationStore } from "./conversationStore"

vi.mock("@/api/conversationApi", () => ({
  createConversation: vi.fn(),
  deleteConversation: vi.fn(),
  getActiveGoalInterview: vi.fn(),
  getConversationMessages: vi.fn(),
  getConversations: vi.fn(),
  sendConversationMessage: vi.fn(),
  updateConversationTitle: vi.fn(),
}))

vi.mock("@/api/goalApi", () => ({
  getGoalByConversationId: vi.fn(),
}))

const conversations = [{ conversationId: 11, title: "첫 상담" }]

const messages = [
  {
    messageId: 101,
    role: "USER",
    content: "비상금을 만들고 싶어요.",
    createdAt: "2026-08-14T09:00:00",
  },
]

describe("conversationStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getConversations.mockResolvedValue(conversations)
    getConversationMessages.mockResolvedValue(messages)
    getActiveGoalInterview.mockResolvedValue({
      active: true,
      draft: { title: "비상금 마련" },
      feasibility: null,
    })
    vi.stubGlobal("alert", vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it("restores product recommendation cards from a refreshed conversation", async () => {
    getConversationMessages.mockResolvedValueOnce([
      {
        messageId: 101,
        role: "USER",
        content: "Recommend a deposit",
      },
      {
        messageId: 102,
        role: "ASSISTANT",
        content: "**Recommendation reason**",
        productRecommendation: {
          productType: "deposit",
          products: [{ productName: "Safe Deposit" }],
        },
      },
    ])

    const store = useConversationStore()
    await store.fetchMessages(7, 11, { force: true })

    expect(store.messages[1].productRecommendation.products).toHaveLength(1)
    expect(store.messages[1].productRecommendation.products[0].productName)
      .toBe("Safe Deposit")
    expect(store.messages[1].content).toBe("**Recommendation reason**")
  })

  it("caches conversations and restores messages with a separate loading state", async () => {
    const store = useConversationStore()

    await store.fetchConversations(7)
    await store.fetchConversations(7)

    expect(getConversations).toHaveBeenCalledOnce()
    expect(store.conversations).toEqual(conversations)
    expect(store.activeConversationId).toBe(11)
    expect(store.initialLoading).toBe(false)
    expect(store.refreshing).toBe(false)

    await store.selectConversation(11, 7)
    await store.fetchMessages(7, 11)

    expect(getConversationMessages).toHaveBeenCalledOnce()
    expect(getActiveGoalInterview).toHaveBeenCalledOnce()
    expect(store.messages[0]).toMatchObject({
      id: 101,
      role: "user",
      content: "비상금을 만들고 싶어요.",
    })
    expect(store.activeGoalInterview).toMatchObject({
      action: "CONTINUE",
      active: true,
    })
    expect(store.initialMessageLoading).toBe(false)
    expect(store.refreshingMessages).toBe(false)
  })

  it("forces a new message snapshot when the conversation is refreshed", async () => {
    const store = useConversationStore()

    await store.fetchMessages(7, 11)
    await store.fetchMessages(7, 11, { force: true })

    expect(getConversationMessages).toHaveBeenCalledTimes(2)
    expect(getActiveGoalInterview).toHaveBeenCalledTimes(2)
  })

  it("clears conversation and message state when the session is reset", async () => {
    const store = useConversationStore()

    await store.fetchConversations(7)
    await store.selectConversation(11, 7)

    store.reset()

    expect(store.conversations).toEqual([])
    expect(store.activeConversationId).toBeNull()
    expect(store.messages).toEqual([])
    expect(store.activeGoalInterview).toBeNull()
    expect(store.confirmedGoal).toBeNull()
    expect(store.isLoading).toBe(false)
    expect(store.isMessageLoading).toBe(false)
    expect(store.isSending).toBe(false)

    await store.fetchConversations(8)

    expect(getConversations).toHaveBeenCalledTimes(2)
    expect(getConversations).toHaveBeenLastCalledWith(8)
  })

  it("passes a custom title when starting a goal-setting conversation", async () => {
    createConversation.mockResolvedValue({
      conversationId: 12,
      title: "목표 설정",
    })

    const store = useConversationStore()
    const conversation = await store.startNewConversation(7, "목표 설정")

    expect(createConversation).toHaveBeenCalledWith(7, "목표 설정")
    expect(conversation).toMatchObject({
      conversationId: 12,
      title: "목표 설정",
    })
  })

  it("starts a goal-setting conversation and sends the trigger message", async () => {
    createConversation.mockResolvedValue({
      conversationId: 12,
      title: "목표 설정",
    })
    sendConversationMessage.mockResolvedValue({
      userMessage: {
        messageId: 201,
        role: "USER",
        content: "목표를 설정하고 싶어요",
      },
      assistantMessage: {
        messageId: 202,
        role: "ASSISTANT",
        content: "어떤 목표를 세우고 싶으세요?",
      },
    })

    const store = useConversationStore()
    const started = await store.startGoalSettingConversation(7)

    expect(started).toBe(true)
    expect(createConversation).toHaveBeenCalledWith(7, "목표 설정")
    expect(sendConversationMessage).toHaveBeenCalledWith(
      12,
      7,
      "목표를 설정하고 싶어요",
      "GOAL_SETTING",
    )
    expect(store.isSending).toBe(false)
  })

  it("starts asset analysis in a new conversation", async () => {
    createConversation.mockResolvedValue({
      conversationId: 12,
      title: "새 채팅",
    })
    getConversations.mockResolvedValue([
      { conversationId: 12, title: "새 채팅" },
      ...conversations,
    ])
    sendConversationMessage.mockResolvedValue({
      userMessage: {
        messageId: 201,
        role: "USER",
        content: "내 자산을 분석해줘",
      },
      assistantMessage: {
        messageId: 202,
        role: "ASSISTANT",
        content: "자산 분석 결과입니다.",
      },
    })

    const store = useConversationStore()
    store.activeConversationId = 11
    const started = await store.startAssetAnalysis(7)

    expect(started).toBe(true)
    expect(createConversation).toHaveBeenCalledWith(7, "새 채팅")
    expect(sendConversationMessage).toHaveBeenCalledWith(
      12,
      7,
      "내 자산을 분석해줘",
      null,
    )
    expect(store.activeConversationId).toBe(12)
  })

  it("coalesces concurrent asset analysis starts into one conversation", async () => {
    createConversation.mockResolvedValue({
      conversationId: 12,
      title: "새 채팅",
    })
    getConversations.mockResolvedValue([
      { conversationId: 12, title: "새 채팅" },
      ...conversations,
    ])
    sendConversationMessage.mockResolvedValue({
      userMessage: {
        messageId: 201,
        role: "USER",
        content: "내 자산을 분석해줘",
      },
      assistantMessage: {
        messageId: 202,
        role: "ASSISTANT",
        content: "자산 분석 결과입니다.",
      },
    })

    const store = useConversationStore()
    const [started, duplicateStart] = await Promise.all([
      store.startAssetAnalysis(7),
      store.startAssetAnalysis(7),
    ])

    expect(started).toBe(true)
    expect(duplicateStart).toBe(true)
    expect(createConversation).toHaveBeenCalledTimes(1)
    expect(sendConversationMessage).toHaveBeenCalledTimes(1)
  })

  it("starts product recommendation in a new conversation", async () => {
    createConversation.mockResolvedValue({
      conversationId: 13,
      title: "새 채팅",
    })
    getConversations.mockResolvedValue([
      { conversationId: 13, title: "새 채팅" },
      ...conversations,
    ])
    sendConversationMessage.mockResolvedValue({
      userMessage: {
        messageId: 203,
        role: "USER",
        content: "내 상황에 맞는 금융상품을 추천해줘",
      },
      assistantMessage: {
        messageId: 204,
        role: "ASSISTANT",
        content: "상품 추천 결과입니다.",
      },
    })

    const store = useConversationStore()
    store.activeConversationId = 11
    const started = await store.startProductRecommendation(7)

    expect(started).toBe(true)
    expect(createConversation).toHaveBeenCalledWith(7, "새 채팅")
    expect(sendConversationMessage).toHaveBeenCalledWith(
      13,
      7,
      "내 상황에 맞는 금융상품을 추천해줘",
      null,
    )
    expect(store.activeConversationId).toBe(13)
  })

  it("keeps a message send error for the chat error area instead of alerting", async () => {
    sendConversationMessage.mockRejectedValue(
      new Error("현재 AI 사용량 한도에 도달했습니다. 잠시 후 다시 시도해 주세요."),
    )

    const store = useConversationStore()
    store.activeConversationId = 11

    const sent = await store.sendMessage(7, "소비를 분석해줘")

    expect(sent).toBe(false)
    expect(store.lastError).toBe(
      "현재 AI 사용량 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.",
    )
    expect(store.lastErrorStatus).toBeNull()
    expect(globalThis.alert).not.toHaveBeenCalled()
  })
})
