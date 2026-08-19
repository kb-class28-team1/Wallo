import { flushPromises, mount } from "@vue/test-utils"
import { createPinia, setActivePinia } from "pinia"
import { beforeEach, describe, expect, it, vi } from "vitest"
import AiAssistantView from "./AiAssistantView.vue"
import { getGoalRoadmap, getGoals } from "@/api/goalApi"
import { getLatestProductRecommendation } from "@/api/productRecommendationApi"

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

vi.mock("@/api/productRecommendationApi", () => ({
  getLatestProductRecommendation: vi.fn(),
}))

describe("AiAssistantView", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getGoals.mockResolvedValue({ data: [] })
    getGoalRoadmap.mockResolvedValue({ data: null })
    getLatestProductRecommendation.mockResolvedValue({ data: null })
  })

  it("shows the goal empty state and roadmap introduction when no goal exists", async () => {
    const wrapper = mount(AiAssistantView)
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find(".empty-dashboard").exists()).toBe(true))

    expect(wrapper.find(".assistant-header").classes()).toContain("app-page-header")
    expect(wrapper.find(".content-card").classes()).toContain("app-card")
    expect(wrapper.find(".goal-button").classes()).toContain("app-button")
    expect(wrapper.text()).toContain("목표 달성을 위한 로드맵")
    expect(wrapper.text()).toContain("나에게 맞는 로드맵")
    expect(wrapper.text()).toContain("추천 금융 상품")
  })

  it("moves to chat when the goal setting button is selected", async () => {
    const wrapper = mount(AiAssistantView)
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find(".goal-button").exists()).toBe(true))

    await wrapper.find(".goal-button").trigger("click")

    expect(push).toHaveBeenCalledWith({
      name: "chat",
      query: { start: "goal-setting" },
    })
  })

  it("shows the saved goal, progress, roadmap, and action guide", async () => {
    getGoals.mockResolvedValue({
      data: [
        {
          goalId: 1,
          title: "비상금 1,000만 원 만들기",
          goalType: "EMERGENCY_FUND",
          targetAmount: 10000000,
          currentAmount: 2500000,
          achievementRate: 25,
          requiredMonthlyAmount: 500000,
          targetDate: "2027-12-31",
        },
      ],
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
    expect(wrapper.text()).not.toContain("AI가 생성한 맞춤 계획")
    expect(wrapper.find(".goal-roadmap-card").exists()).toBe(true)
    expect(wrapper.findAll(".roadmap-navigation-button")).toHaveLength(2)
    expect(wrapper.text()).toContain("이번 달 실천 가이드")
    expect(wrapper.text()).not.toContain("목표를 확인했어요")
    expect(wrapper.find(".goal-chat-button").classes()).toContain("app-button")

    await wrapper.find(".goal-chat-button").trigger("click")
    expect(push).toHaveBeenCalledWith({ name: "chat" })
  })

  it("shows the latest product recommendation when one is available", async () => {
    getLatestProductRecommendation.mockResolvedValue({
      data: {
        assistantMessageId: 3,
        requestMessage: "12개월 예금 100만원 추천",
        productRecommendation: {
          dataMode: "finlife_csv",
          productType: "예금",
          termMonths: 12,
          amountKrw: 1000000,
          products: [
            {
              ranking: 1,
              companyName: "Wallo Bank",
              productName: "안심 정기예금",
              baseRatePercent: 2.5,
              preferentialRatePercent: 3.1,
              estimatedAfterTaxInterestKrw: 26200,
              estimatedMaturityAmountKrw: 1026200,
              disclosureMonth: "202608",
              collectedAt: "2026-08-19T10:00:00",
            },
          ],
        },
        aiResponse: "기본금리와 우대조건을 함께 고려해 추천했어요.",
        generatedAt: "2026-08-19T10:01:00",
      },
    })

    const wrapper = mount(AiAssistantView)
    await flushPromises()
    await vi.waitFor(() => {
      expect(wrapper.find(".latest-product-recommendation-card").exists()).toBe(true)
    })

    expect(getLatestProductRecommendation).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain("안심 정기예금")
    expect(wrapper.text()).toContain("기본금리와 우대조건을 함께 고려해 추천했어요.")
    expect(wrapper.text()).toContain("Wallo Bank")
  })
})
