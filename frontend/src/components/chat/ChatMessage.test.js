import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"
import ChatMessage from "./ChatMessage.vue"

const analysis = {
  period: { type: "MONTHLY", label: "이번 달" },
  hasEnoughData: true,
  summary: {
    currentTotal: 142500,
    previousTotal: 408500,
    deltaAmount: -266000,
    deltaRate: -65.1,
    warningIncrease: false,
  },
  signals: {
    categorySpikes: [],
    newSpendings: [],
    oneTimeLarge: [],
    budget: null,
    recurringPatterns: [],
    subscriptions: [],
    positives: [],
    streaks: [],
  },
}

describe("ChatMessage", () => {
  it("소비분석 메시지는 카드만 표시하고 AI 줄글은 숨긴다", () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: {
          id: 1,
          role: "assistant",
          content: "이 문장은 화면에 표시되면 안 됩니다.",
          consumptionAnalysis: analysis,
          animate: false,
        },
      },
    })

    expect(wrapper.text()).toContain("142,500원")
    expect(wrapper.text()).not.toContain("이 문장은 화면에 표시되면 안 됩니다.")
    expect(wrapper.find(".message-content--markdown").exists()).toBe(false)
  })

  it("일반 AI 메시지는 기존 줄글을 표시한다", () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: {
          id: 2,
          role: "assistant",
          content: "일반 금융 상담 답변입니다.",
          consumptionAnalysis: null,
          animate: false,
        },
      },
    })

    expect(wrapper.text()).toContain("일반 금융 상담 답변입니다.")
    expect(wrapper.find(".message-content--markdown").exists()).toBe(true)
  })
})
