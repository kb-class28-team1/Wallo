import { flushPromises, mount } from "@vue/test-utils"
import { createPinia, setActivePinia } from "pinia"
import { beforeEach, describe, expect, it, vi } from "vitest"
import AiAssistantView from "./AiAssistantView.vue"
import {
  getAvailableGoalAccounts,
  getGoalRoadmap,
  getGoals,
  selectGoalAccount,
} from "@/api/goalApi"

const push = vi.fn()

vi.mock("vue-router", () => ({
  useRouter: () => ({ push }),
}))

vi.mock("@/api/goalApi", () => ({
  getGoals: vi.fn().mockResolvedValue({ data: [] }),
  getGoalRoadmap: vi.fn().mockResolvedValue({ data: null }),
  getAvailableGoalAccounts: vi.fn(),
  selectGoalAccount: vi.fn(),
  updateGoalRoadmapStep: vi.fn(),
}))

describe("AiAssistantView", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getGoals.mockResolvedValue({ data: [] })
    getGoalRoadmap.mockResolvedValue({ data: null })
    getAvailableGoalAccounts.mockResolvedValue({ data: [] })
  })

  it("shows the goal empty state and roadmap introduction when no goal exists", async () => {
    const wrapper = mount(AiAssistantView)
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find(".empty-dashboard").exists()).toBe(true))

    expect(wrapper.text()).toContain("목표 달성을 위한 로드맵")
    expect(wrapper.text()).toContain("나에게 맞는 로드맵")
    expect(wrapper.text()).toContain("추천 금융 상품")
    expect(getAvailableGoalAccounts).not.toHaveBeenCalled()
  })

  it("moves to chat when the goal setting button is selected", async () => {
    const wrapper = mount(AiAssistantView)
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find(".goal-button").exists()).toBe(true))

    await wrapper.find(".goal-button").trigger("click")

    expect(push).toHaveBeenCalledWith({ name: "chat" })
  })

  it("loads available accounts when a goal exists", async () => {
    getGoals.mockResolvedValue({
      data: [{
        goalId: 1,
        title: "비상금 목표",
        targetAmount: 10000000,
        currentAmount: 2500000,
        targetDate: "2027-12-31",
      }],
    })
    getAvailableGoalAccounts.mockResolvedValue({
      data: [{
        accountId: 101,
        bankName: "Wallo Bank",
        accountName: "생활비 통장",
        displayNumber: "1234-****-7890",
        accountType: "입출금",
        balance: 2500000,
        currency: "KRW",
        selected: true,
      }],
    })

    const wrapper = mount(AiAssistantView)
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find(".goal-account-selection").exists()).toBe(true))

    expect(wrapper.text()).toContain("Wallo Bank")
    expect(getAvailableGoalAccounts).toHaveBeenCalledTimes(1)
  })

  it("saves the selected account and refreshes goal data", async () => {
    getGoals.mockResolvedValue({
      data: [{
        goalId: 1,
        title: "비상금 목표",
        targetAmount: 10000000,
        currentAmount: 2500000,
        targetDate: "2027-12-31",
      }],
    })
    getAvailableGoalAccounts.mockResolvedValue({
      data: [
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
      ],
    })
    selectGoalAccount.mockResolvedValue({
      data: { accountId: 102, selected: true },
    })

    const wrapper = mount(AiAssistantView)
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.findAll('input[type="radio"]')).toHaveLength(2))

    await wrapper.findAll('input[type="radio"]')[1].setValue()
    await wrapper.find(".goal-account-selection button.btn-primary").trigger("click")
    await flushPromises()

    expect(selectGoalAccount).toHaveBeenCalledWith(1, 102)
    expect(getGoals).toHaveBeenCalledTimes(2)
    expect(getAvailableGoalAccounts).toHaveBeenCalledTimes(2)
  })

  it("shows the saved goal, progress, roadmap, and action guide", async () => {
    getGoals.mockResolvedValue({
      data: [{
        goalId: 1,
        title: "비상금 1,000만 원 만들기",
        goalType: "EMERGENCY_FUND",
        targetAmount: 10000000,
        currentAmount: 2500000,
        achievementRate: 25,
        requiredMonthlyAmount: 500000,
        targetDate: "2027-12-31",
      }],
    })
    getGoalRoadmap.mockResolvedValue({
      data: {
        generationStatus: "COMPLETED",
        currentStepNumber: 1,
        completedStepNumbers: [],
        roadmap: {
          steps: [
            {
              stepNumber: 1,
              title: "자동 저축 시작",
              description: "전용 계좌를 준비합니다.",
              targetDate: "2026-09-30",
              targetAmount: 3000000,
              actionItems: ["자동이체 설정"],
            },
            {
              stepNumber: 2,
              title: "최종 목표 달성",
              description: "목표 잔액을 확인합니다.",
              targetDate: "2027-12-31",
              targetAmount: 10000000,
              actionItems: ["최종 잔액 확인"],
            },
          ],
        },
      },
    })

    const wrapper = mount(AiAssistantView)
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.text()).toContain("비상금 1,000만 원 만들기"))

    expect(wrapper.text()).toContain("2,500,000원")
    expect(wrapper.text()).toContain("25%")
    expect(wrapper.text()).toContain("최종 목표 달성")
    expect(wrapper.text()).toContain("이번 달 실천 가이드")
    expect(wrapper.text()).not.toContain("목표를 확인했어요")

    await wrapper.find(".goal-chat-button").trigger("click")
    expect(push).toHaveBeenCalledWith({ name: "chat" })
  })
})
