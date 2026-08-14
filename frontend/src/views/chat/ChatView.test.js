import { flushPromises, mount } from "@vue/test-utils"
import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import ChatView from "./ChatView.vue"
import {
  createConversation,
  getActiveGoalInterview,
  getConversationMessages,
  getConversations,
  sendConversationMessage,
} from "@/api/conversationApi"
import {
  getAvailableGoalAccounts,
  getGoalByConversationId,
  selectGoalAccount,
} from "@/api/goalApi"
import { useUserStore } from "@/stores/userStore"

const { route, replaceMock } = vi.hoisted(() => ({
  route: { query: {} },
  replaceMock: vi.fn(() => Promise.resolve()),
}))

vi.mock("vue-router", () => ({
  useRoute: () => route,
  useRouter: () => ({ replace: replaceMock }),
}))

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
  getAvailableGoalAccounts: vi.fn(),
  getGoalByConversationId: vi.fn(),
  getGoalRoadmap: vi.fn(),
  getGoals: vi.fn(),
  selectGoalAccount: vi.fn(),
  updateGoalRoadmapStep: vi.fn(),
}))

const goal = {
  goalId: 31,
  title: "비상금 목표",
  goalType: "EMERGENCY_FUND",
  targetAmount: 10000000,
  currentAmount: 2500000,
  targetDate: "2027-12-31",
}

const updatedGoal = {
  ...goal,
  currentAmount: 1400000,
  achievementRate: 14,
}

const initialAccounts = [
  {
    accountId: 101,
    bankName: "Wallo Bank",
    accountName: "생활비 통장",
    displayNumber: "1234-****-7890",
    accountType: "입출금",
    balance: 2500000,
    currency: "KRW",
    selected: true,
  },
  {
    accountId: 102,
    bankName: "Wallo Securities",
    accountName: "CMA 통장",
    displayNumber: "9876-****-1234",
    accountType: "CMA",
    balance: 1000000,
    currency: "KRW",
    selected: false,
  },
]

const savedAccounts = initialAccounts.map((account) => ({
  ...account,
  selected: account.accountId === 102,
}))

const mountChat = () => mount(ChatView, {
  global: {
    stubs: {
      ChatMessage: {
        props: ["message"],
        template: '<div class="stub-message">{{ message.content }}</div>',
      },
      ChatInput: {
        template: '<div class="stub-input" />',
      },
    },
  },
})

describe("ChatView", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    route.query = {}
    vi.stubGlobal("alert", vi.fn())
    vi.clearAllMocks()

    useUserStore().user = { id: 7, nickname: "Tester" }
    getConversations.mockResolvedValue([
      { conversationId: 11, title: "비상금 목표", updatedAt: "2026-08-12T00:00:00" },
    ])
    getConversationMessages.mockResolvedValue([
      { messageId: 1, role: "ASSISTANT", content: "목표를 확인해 주세요." },
    ])
    getActiveGoalInterview.mockResolvedValue({ active: false, draft: null })
    getGoalByConversationId.mockResolvedValue({ data: goal })
    getAvailableGoalAccounts.mockResolvedValue({ data: initialAccounts })
    selectGoalAccount.mockResolvedValue({
      data: { accountId: 102, selected: true },
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("restores the confirmed goal card and account selector after refresh", async () => {
    const wrapper = mountChat()
    await flushPromises()

    await vi.waitFor(() => {
      expect(wrapper.find(".goal-interview-card").exists()).toBe(true)
      expect(wrapper.find(".goal-account-selection").exists()).toBe(true)
    })

    expect(wrapper.text()).toContain("비상금 목표")
    expect(wrapper.text()).toContain("10,000,000원")
    expect(wrapper.text()).not.toContain("이대로 확정")
    expect(wrapper.text()).toContain("Wallo Bank")
    expect(getGoalByConversationId).toHaveBeenCalledWith(11)
  })

  it("does not show the welcome message during automatic consumption analysis", () => {
    route.query = { action: "consumption-analysis" }

    const wrapper = mountChat()

    expect(wrapper.text()).not.toContain(
      "안녕하세요. 저는 Wallo 금융 컨설턴트입니다. 무엇을 도와드릴까요?",
    )

    wrapper.unmount()
  })

  it("starts a named goal-setting conversation from the dashboard entry", async () => {
    route.query = { start: "goal-setting" }
    createConversation.mockResolvedValue({
      conversationId: 12,
      title: "목표 설정",
      updatedAt: "2026-08-14T00:00:00",
    })
    getConversations.mockResolvedValue([
      { conversationId: 12, title: "목표 설정", updatedAt: "2026-08-14T00:00:00" },
    ])
    sendConversationMessage.mockResolvedValue({
      userMessage: {
        messageId: 2,
        role: "USER",
        content: "목표를 설정하고 싶어요",
      },
      assistantMessage: {
        messageId: 3,
        role: "ASSISTANT",
        content: "어떤 상황이나 계획을 위해 돈을 마련하고 싶으세요?",
      },
      goalInterview: {
        action: "CONTINUE",
        active: true,
        draft: {
          state: "DISCOVERY",
          title: null,
          goalType: null,
          targetAmount: null,
          targetDate: null,
          currentAmount: null,
          missingFields: ["goalType"],
        },
        feasibility: null,
      },
    })

    const wrapper = mountChat()
    await flushPromises()

    await vi.waitFor(() => {
      expect(sendConversationMessage).toHaveBeenCalledWith(
        12,
        7,
        "목표를 설정하고 싶어요",
      )
    })

    expect(createConversation).toHaveBeenCalledWith(7, "목표 설정")
    expect(replaceMock).toHaveBeenCalledWith({ name: "chat" })
    expect(wrapper.find("#chat-title").text()).toBe("목표 설정")
    expect(wrapper.text()).toContain("어떤 상황이나 계획을 위해 돈을 마련하고 싶으세요?")
    expect(wrapper.find(".goal-interview-card").exists()).toBe(true)
    expect(wrapper.text()).not.toContain("안녕하세요. 저는 Wallo 금융 컨설턴트입니다.")
  })

  it("saves the account selected below the confirmed goal card", async () => {
    getGoalByConversationId
      .mockResolvedValueOnce({ data: goal })
      .mockResolvedValueOnce({ data: updatedGoal })
    getAvailableGoalAccounts
      .mockResolvedValueOnce({ data: initialAccounts })
      .mockResolvedValueOnce({ data: savedAccounts })

    const wrapper = mountChat()
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.findAll('input[type="radio"]')).toHaveLength(2))

    await wrapper.findAll('input[type="radio"]')[1].setValue()
    await wrapper.find(".goal-account-selection button.btn-primary").trigger("click")
    await flushPromises()

    expect(selectGoalAccount).toHaveBeenCalledWith(31, 102)
    expect(getGoalByConversationId).toHaveBeenCalledTimes(2)
    expect(getAvailableGoalAccounts).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('input[type="radio"]')[1].element.checked).toBe(true)
    expect(wrapper.text()).toContain("1,400,000")
  })
})
