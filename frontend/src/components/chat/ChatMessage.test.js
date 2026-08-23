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
  it("renders a product recommendation response as cards with markdown reason", () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: {
          id: 4,
          role: "assistant",
          content: "**Recommendation reason**\n\n- Matches the requested term",
          productRecommendation: {
            productType: "deposit",
            termMonths: 12,
            amountKrw: 1000000,
            products: [
              {
                ranking: 1,
                companyName: "Wallo Bank",
                productName: "Safe Deposit",
                baseRatePercent: 2.5,
                preferentialRatePercent: 3.1,
              },
            ],
          },
          animate: false,
        },
      },
    })

    expect(wrapper.find(".product-recommendation").exists()).toBe(true)
    expect(wrapper.text()).toContain("Safe Deposit")
    expect(wrapper.find(".message-content--markdown").exists()).toBe(false)
    expect(wrapper.find(".product-recommendation__reason-markdown strong").text())
      .toBe("Recommendation reason")
  })

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

  it("keeps the typing cursor inside the assistant content card", () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: {
          id: 5,
          role: "assistant",
          content: "답변을 작성하는 중입니다.",
          animate: true,
        },
      },
    })

    expect(wrapper.find(".message-content--typing").exists()).toBe(true)
    expect(wrapper.find(".message-bubble > .typing-cursor").exists()).toBe(false)

    wrapper.unmount()
  })

  it("renders an asset analysis response as cards instead of markdown", () => {
    const wrapper = mount(ChatMessage, {
      props: {
        message: {
          id: 3,
          role: "assistant",
          content: "This prose should not be rendered for an asset analysis response.",
          assetAnalysis: {
            summary: {
              totalAssetsKrw: 120000000,
              totalDebtKrw: 20000000,
              netAssetsKrw: 100000000,
            },
            cashflow: {},
            composition: [],
            dataQualityNotes: [],
          },
          animate: false,
        },
      },
    })

    expect(wrapper.find(".asset-analysis").exists()).toBe(true)
    expect(wrapper.text()).toContain("120,000,000")
    expect(wrapper.text()).not.toContain("This prose should not be rendered")
    expect(wrapper.find(".message-content--markdown").exists()).toBe(false)
  })
})
