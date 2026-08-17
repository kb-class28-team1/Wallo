import { flushPromises, mount } from "@vue/test-utils"
import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { nextTick } from "vue"

import ChatView from "./ChatView.vue"
import ChatInput from "@/components/chat/ChatInput.vue"
import {
  createConversation,
  deleteConversation,
  getActiveGoalInterview,
  getConversationMessages,
  getConversations,
  sendConversationMessage,
} from "@/api/conversationApi"
import {
  getAvailableGoalAccounts,
  getGoalByConversationId,
  getGoalRoadmap,
  getGoals,
  selectGoalAccount,
} from "@/api/goalApi"
import { useConversationStore } from "@/stores/conversationStore"
import { useUserStore } from "@/stores/userStore"

const { route, replaceMock, pushMock } = vi.hoisted(() => {
  const routeState = { query: {} }

  return {
    route: routeState,
    replaceMock: vi.fn(() => {
      routeState.query = {}
      return Promise.resolve()
    }),
    pushMock: vi.fn(() => Promise.resolve()),
  }
})

vi.mock("vue-router", () => ({
  useRoute: () => route,
  useRouter: () => ({ replace: replaceMock, push: pushMock }),
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

const mountChat = () =>
  mount(ChatView, {
    global: {
      stubs: {
        ChatMessage: {
          props: ["message"],
          template: '<div class="stub-message">{{ message.content }}</div>',
        },
        ChatInput: {
          props: ["disabled"],
          template: '<div class="stub-input" :data-disabled="String(disabled)" />',
        },
        RouterLink: {
          props: ["to"],
          template: '<a :href="to"><slot /></a>',
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
    deleteConversation.mockResolvedValue({ success: true })
    getConversationMessages.mockResolvedValue([
      { messageId: 1, role: "ASSISTANT", content: "목표를 확인해 주세요." },
    ])
    getActiveGoalInterview.mockResolvedValue({ active: false, draft: null })
    getGoalByConversationId.mockResolvedValue({ data: goal })
    getGoalRoadmap.mockResolvedValue({ data: null })
    getGoals.mockResolvedValue({ data: [] })
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

    expect(wrapper.find(".goal-interview-card").classes()).toContain("app-card")
    expect(wrapper.find(".chat-panel").classes()).toContain("app-card")
    expect(wrapper.find(".conversation-panel").classes()).toContain("app-card")
    expect(wrapper.find(".new-conversation-button").classes()).toContain("app-button")
    expect(wrapper.find(".message-list").exists()).toBe(true)
    expect(wrapper.text()).toContain("비상금 목표")
    expect(wrapper.text()).toContain("10,000,000원")
    expect(wrapper.text()).not.toContain("이대로 확정")
    expect(wrapper.text()).toContain("Wallo Bank")
    expect(getGoalByConversationId).toHaveBeenCalledWith(11)
  })

  it("uses the shared button for sending chat messages", async () => {
    const wrapper = mount(ChatInput)
    const sendButton = wrapper.get(".message-send-button")

    expect(sendButton.classes()).toContain("app-button")
    expect(sendButton.classes()).toContain("app-button--primary")
    expect(sendButton.element.disabled).toBe(true)

    await wrapper.get("#message-input").setValue("자산을 확인하고 싶어요")

    expect(sendButton.element.disabled).toBe(false)
  })

  it("opens the requested goal conversation from the dashboard", async () => {
    route.query = { conversationId: "12" }
    getConversations.mockResolvedValue([
      { conversationId: 11, title: "최근 채팅", updatedAt: "2026-08-12T00:00:00" },
      { conversationId: 12, title: "비상금 목표", updatedAt: "2026-08-11T00:00:00" },
    ])
    getGoalByConversationId.mockResolvedValue({
      data: { ...goal, conversationId: 12 },
    })

    const wrapper = mountChat()
    await flushPromises()

    await vi.waitFor(() => {
      expect(getConversationMessages).toHaveBeenCalledWith(12, 7)
    })

    expect(useConversationStore().activeConversationId).toBe(12)
    wrapper.unmount()
  })

  it("blocks deleting a conversation linked to a confirmed goal", async () => {
    getGoals.mockResolvedValue({ data: [{ ...goal, conversationId: 11 }] })
    const wrapper = mountChat()
    await flushPromises()

    await wrapper.find('button[aria-label="채팅방 삭제"]').trigger("click")
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').text()).toContain(
      "목표 설정이 완료된 채팅은 계좌 변경에 필요하므로 삭제할 수 없습니다.",
    )
    expect(wrapper.find("[data-modal-confirm]").text()).toBe("확인")

    await wrapper.find("[data-modal-confirm]").trigger("click")
    await flushPromises()

    expect(deleteConversation).not.toHaveBeenCalled()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it("opens a modal before deleting an unlinked conversation", async () => {
    const wrapper = mountChat()
    await flushPromises()

    await wrapper.find('button[aria-label="채팅방 삭제"]').trigger("click")
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.find('[role="dialog"]').text()).toContain("'비상금 목표' 채팅방을 삭제할까요?")

    await wrapper.find("[data-modal-confirm]").trigger("click")
    await flushPromises()

    expect(deleteConversation).toHaveBeenCalledWith(11, 7)
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it("shows a completion modal and opens AI consulting after the roadmap is completed", async () => {
    getActiveGoalInterview.mockResolvedValue({
      active: true,
      draft: {
        state: "CONFIRMATION",
        title: "비상금 목표",
        goalType: "EMERGENCY_FUND",
        targetAmount: 10000000,
        targetDate: "2027-12-31",
        currentAmount: 2500000,
        missingFields: [],
      },
      feasibility: {
        status: "CALCULATED",
        requiredMonthlyAmount: 500000,
      },
    })
    getGoalByConversationId.mockResolvedValue({ data: goal })
    getGoalRoadmap.mockResolvedValue({
      data: {
        goalId: 31,
        generationStatus: "COMPLETED",
        roadmap: { steps: [] },
      },
    })
    sendConversationMessage.mockResolvedValue({
      userMessage: {
        messageId: 2,
        role: "USER",
        content: "이대로 확정할게",
      },
      assistantMessage: {
        messageId: 3,
        role: "ASSISTANT",
        content: "목표 설정 및 로드맵이 완성되었습니다!",
      },
      goalInterview: {
        action: "CONFIRM",
        active: false,
        draft: {
          state: "COMPLETED",
          title: "비상금 목표",
          goalType: "EMERGENCY_FUND",
          targetAmount: 10000000,
          targetDate: "2027-12-31",
          currentAmount: 2500000,
          confirmed: true,
          missingFields: [],
        },
      },
    })

    const wrapper = mountChat()
    await flushPromises()

    await wrapper.find(".goal-interview-card .btn-primary").trigger("click")
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    await vi.waitFor(() => expect(wrapper.findAll('input[type="radio"]')).toHaveLength(2))

    await wrapper.findAll('input[type="radio"]')[1].setValue()
    await wrapper.find(".goal-account-selection button.btn-primary").trigger("click")
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').text()).toContain(
      "목표 설정 및 로드맵이 완성되었습니다!",
    )
    expect(wrapper.find("[data-modal-confirm]").text()).toBe("확인하기")
    expect(pushMock).not.toHaveBeenCalled()

    await wrapper.find("[data-modal-confirm]").trigger("click")
    await flushPromises()

    expect(pushMock).toHaveBeenCalledWith({ name: "ai-consulting" })
    wrapper.unmount()
  })

  it("does not show the completion modal when roadmap generation fails", async () => {
    getActiveGoalInterview.mockResolvedValue({
      active: true,
      draft: {
        state: "CONFIRMATION",
        title: "비상금 목표",
        goalType: "EMERGENCY_FUND",
        targetAmount: 10000000,
        targetDate: "2027-12-31",
        currentAmount: 2500000,
        missingFields: [],
      },
      feasibility: {
        status: "CALCULATED",
        requiredMonthlyAmount: 500000,
      },
    })
    getGoalByConversationId.mockResolvedValue({ data: goal })
    getGoalRoadmap.mockResolvedValue({
      data: {
        goalId: 31,
        generationStatus: "FAILED",
        failureReason: "AI 오류",
      },
    })
    sendConversationMessage.mockResolvedValue({
      userMessage: {
        messageId: 2,
        role: "USER",
        content: "이대로 확정할게",
      },
      assistantMessage: {
        messageId: 3,
        role: "ASSISTANT",
        content: "목표 설정은 완료되었지만 로드맵 생성에 실패했습니다.",
      },
      goalInterview: {
        action: "CONFIRM",
        active: false,
        draft: {
          state: "COMPLETED",
          title: "비상금 목표",
          goalType: "EMERGENCY_FUND",
          targetAmount: 10000000,
          targetDate: "2027-12-31",
          currentAmount: 2500000,
          confirmed: true,
          missingFields: [],
        },
      },
    })

    const wrapper = mountChat()
    await flushPromises()

    await wrapper.find(".goal-interview-card .btn-primary").trigger("click")
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.text()).toContain("목표 설정은 완료되었지만 로드맵 생성에 실패했습니다.")
    expect(pushMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it("shows a dashboard link instead of falling back when the requested goal chat is deleted", async () => {
    route.query = { conversationId: "999" }
    const wrapper = mountChat()
    await flushPromises()

    expect(wrapper.text()).toContain("목표 설정 채팅을 찾을 수 없습니다.")
    expect(wrapper.find('a[href="/dashboard"]').text()).toBe("대시보드로 이동")
    expect(useConversationStore().activeConversationId).toBe(null)
    expect(getConversationMessages).not.toHaveBeenCalled()
    wrapper.unmount()
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
    expect(wrapper.text()).not.toContain(
      "안녕하세요. 저는 Wallo 금융 컨설턴트입니다. 무엇을 도와드릴까요?",
    )
    await flushPromises()

    await vi.waitFor(() => {
      expect(sendConversationMessage).toHaveBeenCalledWith(
        12,
        7,
        "목표를 설정하고 싶어요",
        "GOAL_SETTING",
      )
    })
    await nextTick()

    expect(createConversation).toHaveBeenCalledWith(7, "목표 설정")
    expect(replaceMock).toHaveBeenCalledWith({ name: "chat" })
    expect(wrapper.find("#chat-title").text()).toBe("목표 설정")
    expect(wrapper.text()).toContain("어떤 상황이나 계획을 위해 돈을 마련하고 싶으세요?")
    expect(wrapper.find(".goal-interview-card").exists()).toBe(true)
    expect(wrapper.text()).not.toContain("안녕하세요. 저는 Wallo 금융 컨설턴트입니다.")
  })

  it("disables input and shows an error while goal-setting startup fails", async () => {
    route.query = { start: "goal-setting" }
    let rejectConversation
    createConversation.mockReturnValue(
      new Promise((_, reject) => {
        rejectConversation = reject
      }),
    )

    const wrapper = mountChat()

    await vi.waitFor(() => {
      expect(wrapper.find(".stub-input").attributes("data-disabled")).toBe("true")
    })
    expect(wrapper.text()).not.toContain(
      "안녕하세요. 저는 Wallo 금융 컨설턴트입니다. 무엇을 도와드릴까요?",
    )

    rejectConversation(new Error("채팅방 생성 실패"))
    await flushPromises()

    expect(wrapper.text()).toContain("목표 설정 채팅을 시작하지 못했습니다.")
    expect(wrapper.find(".stub-input").attributes("data-disabled")).toBe("false")
    wrapper.unmount()
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

  it("refetches available accounts after leaving and returning to a goal conversation", async () => {
    const wrapper = mountChat()
    await flushPromises()

    await vi.waitFor(() => {
      expect(getAvailableGoalAccounts).toHaveBeenCalledTimes(1)
    })

    const conversationStore = useConversationStore()
    conversationStore.confirmedGoal = null
    await nextTick()
    conversationStore.confirmedGoal = goal
    await flushPromises()

    await vi.waitFor(() => {
      expect(getAvailableGoalAccounts).toHaveBeenCalledTimes(2)
    })

    wrapper.unmount()
  })
})
