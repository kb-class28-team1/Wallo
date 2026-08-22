import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"
import BudgetGauge from "./BudgetGauge.vue"

describe("BudgetGauge", () => {
  it("shows the amount spent above the recommended cumulative spending", () => {
    const wrapper = mount(BudgetGauge, {
      props: {
        budget: {
          status: "CAUTION",
          budgetAmount: 1_000_000,
          spent: 665_300,
          usageRate: 66.5,
          monthProgress: 50,
        },
      },
    })

    expect(wrapper.text()).toContain("권장 지출액보다 165,300원 더 쓰고 있어요.")
    expect(wrapper.get(".text-danger").text()).toBe("165,300원")
    expect(wrapper.text()).not.toContain("월 진행률")
    expect(wrapper.text()).not.toContain("%p")
  })

  it("shows when spending is below the recommended cumulative spending", () => {
    const wrapper = mount(BudgetGauge, {
      props: {
        budget: {
          status: "NORMAL",
          budgetAmount: 1_000_000,
          spent: 350_000,
          usageRate: 35,
          monthProgress: 50,
        },
      },
    })

    expect(wrapper.text()).toContain("권장 지출액보다 150,000원 덜 쓰고 있어요.")
    expect(wrapper.get(".text-success").text()).toBe("150,000원")
  })
})
