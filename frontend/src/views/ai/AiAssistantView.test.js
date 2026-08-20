import { flushPromises, mount } from "@vue/test-utils"
import { createPinia, setActivePinia } from "pinia"
import { beforeEach, describe, expect, it, vi } from "vitest"
import AiAssistantView from "./AiAssistantView.vue"
import { getGoalRoadmap, getGoals } from "@/api/goalApi"
import {
  completeSelfCheckMission,
  getTodayMissions,
  verifyTransactionMission,
} from "@/api/missionApi"

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

vi.mock("@/api/missionApi", () => ({
  getTodayMissions: vi.fn().mockResolvedValue({ status: "READY", missions: [] }),
  completeSelfCheckMission: vi.fn(),
  verifyTransactionMission: vi.fn(),
}))

describe("AiAssistantView", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getGoals.mockResolvedValue({ data: [] })
    getGoalRoadmap.mockResolvedValue({ data: null })
    getTodayMissions.mockResolvedValue({ status: "READY", missions: [] })
    completeSelfCheckMission.mockResolvedValue({ decision: "PASS" })
    verifyTransactionMission.mockResolvedValue({ decision: "PASS" })
  })

  it("shows the goal empty state and roadmap introduction when no goal exists", async () => {
    const wrapper = mount(AiAssistantView)
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find(".empty-dashboard").exists()).toBe(true))

    expect(wrapper.find(".assistant-header").classes()).toContain("app-page-header")
    expect(wrapper.find(".content-card").classes()).toContain("app-card")
    expect(wrapper.find(".goal-main-card").classes()).toContain("h-100")
    expect(wrapper.find(".goal-button").classes()).toContain("app-button")
    expect(wrapper.text()).toContain("목표 달성을 위한 로드맵")
    expect(wrapper.text()).toContain("나에게 맞는 로드맵")
    expect(wrapper.text()).toContain("추천 금융 상품")
    expect(wrapper.text()).toContain("오늘의 미션")
  })

  it("shows today's missions in the right-hand dashboard card", async () => {
    getTodayMissions.mockResolvedValue({
      status: "READY",
      missions: [
        {
          id: 11,
          title: "커피 대신 물 마시기",
          description: "오후에는 커피 대신 물을 마셔보세요.",
          evidenceGuide: "물을 마신 뒤 직접 완료 여부를 확인하세요.",
          verificationType: "SELF_CHECK",
          icon: "☕",
          rewardPoint: 10,
          completed: true,
        },
        {
          id: 12,
          title: "배달 대신 집밥 먹기",
          description: "오늘 한 끼는 집에 있는 재료로 준비하세요.",
          evidenceGuide: "완성한 음식 사진을 피드에 등록하세요.",
          verificationType: "MEDIA_AI",
          icon: "🍚",
          rewardPoint: 10,
          completed: false,
        },
      ],
    })

    const wrapper = mount(AiAssistantView)
    await flushPromises()

    expect(wrapper.find(".mission-card").exists()).toBe(true)
    expect(wrapper.find(".mission-count").text()).toBe("1/2")
    expect(wrapper.findAll(".mission-list-item")).toHaveLength(2)
    expect(wrapper.findAll(".mission-list-item")[0].classes()).toContain("completed")
    expect(wrapper.text()).toContain("오후에는 커피 대신 물을 마셔보세요.")
    expect(wrapper.text()).toContain("직접 완료 체크")
    expect(wrapper.text()).toContain("미션을 실천한 뒤 오늘의 미션에서 완료 여부를 직접 체크하세요.")
    expect(wrapper.text()).toContain("사진·영상 AI 인증")
    expect(wrapper.text()).toContain("완성한 음식 사진을 피드에 등록하세요.")
    expect(wrapper.find(".mission-action-button").exists()).toBe(false)
  })

  it("shows the hover completion action only for self-check missions", async () => {
    getTodayMissions.mockResolvedValue({
      status: "READY",
      missions: [{
        id: 31,
        title: "지출 기록 확인",
        description: "오늘 지출을 확인하세요.",
        evidenceGuide: "확인 후 직접 완료하세요.",
        verificationType: "SELF_CHECK",
        icon: "✨",
        completed: false,
      }],
    })
    const wrapper = mount(AiAssistantView)
    await flushPromises()

    expect(wrapper.find(".mission-action-button").text()).toBe("완료하기")
    await wrapper.find(".mission-action-button").trigger("click")
    expect(completeSelfCheckMission).toHaveBeenCalledWith(31)
    wrapper.unmount()
  })

  it("reflects missions generated for the next development date", async () => {
    const wrapper = mount(AiAssistantView)
    await flushPromises()

    window.dispatchEvent(
      new CustomEvent("wallo:mission-updated", {
        detail: {
          missionResponse: {
            date: "2026-08-20",
            status: "READY",
            missions: [
              { id: 21, title: "다음날 외식 줄이기", icon: "🍚", rewardPoint: 10, completed: false },
            ],
          },
        },
      }),
    )
    await flushPromises()

    expect(wrapper.text()).toContain("다음날 외식 줄이기")
    expect(wrapper.find(".mission-count").text()).toBe("0/1")
    wrapper.unmount()
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

  it("links to consumption analysis when missions are waiting for analysis", async () => {
    getTodayMissions.mockResolvedValue({ status: "WAITING_ANALYSIS", missions: [] })
    const wrapper = mount(AiAssistantView)
    await flushPromises()

    const analysisButton = wrapper.findAll("button").find(
      (button) => button.text().includes("소비분석 하러가기"),
    )
    expect(analysisButton).toBeTruthy()
    expect(wrapper.find(".mission-empty-analysis").exists()).toBe(true)
    await analysisButton.trigger("click")

    expect(push).toHaveBeenCalledWith({
      name: "chat",
      query: { action: "consumption-analysis" },
    })
    wrapper.unmount()
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

})
