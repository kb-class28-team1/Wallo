import { describe, expect, it } from "vitest"
import { mount } from "@vue/test-utils"

import GoalInterviewCard from "./GoalInterviewCard.vue"

const confirmationInterview = {
  action: "CONTINUE",
  active: true,
  draft: {
    state: "CONFIRMATION",
    title: "비상금 마련",
    goalType: "EMERGENCY_FUND",
    targetAmount: 10000000,
    targetDate: "2027-11-30",
    currentAmount: 2000000,
    missingFields: [],
  },
  feasibility: {
    status: "CALCULATED",
    requiredMonthlyAmount: 500000,
  },
}

describe("GoalInterviewCard", () => {
  it("shows the goal summary and emits confirm and cancel actions", async () => {
    const wrapper = mount(GoalInterviewCard, {
      props: { interview: confirmationInterview },
    })

    expect(wrapper.text()).toContain("비상금 마련")
    expect(wrapper.text()).toContain("10,000,000원")
    expect(wrapper.text()).toContain("월 필요액 계산 완료")
    expect(wrapper.text()).toContain("월 필요 납입액")
    expect(wrapper.findAll("button")).toHaveLength(2)

    await wrapper.find(".btn-primary").trigger("click")
    await wrapper.find(".btn-outline-secondary").trigger("click")

    expect(wrapper.emitted("confirm")).toHaveLength(1)
    expect(wrapper.emitted("cancel")).toHaveLength(1)
  })

  it("shows missing fields without confirmation buttons while collecting details", () => {
    const wrapper = mount(GoalInterviewCard, {
      props: {
        interview: {
          action: "CONTINUE",
          active: true,
          draft: {
            state: "ACTIVE",
            title: "여행 자금 마련",
            targetAmount: 13000000,
            missingFields: ["targetDate", "currentAmount"],
          },
          feasibility: null,
        },
      },
    })

    expect(wrapper.text()).toContain("목표 날짜")
    expect(wrapper.text()).toContain("현재 준비금")
    expect(wrapper.findAll("button")).toHaveLength(0)
  })

  it("disables actions while a confirmation request is being sent", () => {
    const wrapper = mount(GoalInterviewCard, {
      props: {
        interview: confirmationInterview,
        loading: true,
      },
    })

    expect(wrapper.findAll("button").every((button) => button.element.disabled)).toBe(true)
  })

  it("shows a completion message after confirmation", () => {
    const wrapper = mount(GoalInterviewCard, {
      props: {
        interview: {
          ...confirmationInterview,
          action: "CONFIRM",
          active: false,
          draft: {
            ...confirmationInterview.draft,
            state: "COMPLETED",
            confirmed: true,
          },
        },
      },
    })

    expect(wrapper.text()).toContain("목표가 저장되었습니다.")
    expect(wrapper.findAll("button")).toHaveLength(0)
  })
})
