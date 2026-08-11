import { mount } from "@vue/test-utils"
import { createPinia, setActivePinia } from "pinia"
import { beforeEach, describe, expect, it, vi } from "vitest"
import AiAssistantView from "./AiAssistantView.vue"
import { getGoals } from "@/api/goalApi"

const push = vi.fn()

vi.mock("vue-router", () => ({
  useRouter: () => ({ push }),
}))

vi.mock("@/api/goalApi", () => ({
  getGoals: vi.fn().mockResolvedValue({ data: [] }),
  getAvailableGoalAccounts: vi.fn(),
  selectGoalAccount: vi.fn(),
}))

describe("AiAssistantView", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    push.mockReset()
    getGoals.mockResolvedValue({ data: [] })
  })

  it("shows the goal empty state and roadmap introduction when no goal exists", async () => {
    const wrapper = mount(AiAssistantView)
    await vi.waitFor(() => expect(wrapper.text()).toContain("아직 목표가 설정되지 않았어요!"))

    expect(wrapper.text()).toContain("목표 달성을 위한 로드맵")
    expect(wrapper.text()).toContain("나에게 맞는 로드맵")
    expect(wrapper.text()).toContain("추천 금융 상품")
  })

  it("moves to chat when the goal setting button is selected", async () => {
    const wrapper = mount(AiAssistantView)
    await vi.waitFor(() => expect(wrapper.find(".goal-button").exists()).toBe(true))

    await wrapper.find(".goal-button").trigger("click")

    expect(push).toHaveBeenCalledWith({ name: "chat" })
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

    const wrapper = mount(AiAssistantView)
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
