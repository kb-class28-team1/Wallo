import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"

import BudgetSummaryCard from "./BudgetSummaryCard.vue"

describe("BudgetSummaryCard", () => {
  it("renders the budget progress through the shared progress component", async () => {
    const wrapper = mount(BudgetSummaryCard, {
      props: {
        budget: {
          totalAmount: 500_000,
          spentAmount: 125_000,
        },
      },
    })

    const progress = wrapper.find('[role="progressbar"]')

    expect(wrapper.find(".budget-summary-card").classes()).toContain("app-card")
    expect(wrapper.find(".app-progress").exists()).toBe(true)
    expect(progress.attributes("aria-valuenow")).toBe("25")
    expect(progress.find(".app-progress__bar").attributes("style")).toContain("width: 25%")
    expect(wrapper.find(".budget-total").text()).toBe("375,000원")
    expect(wrapper.find(".budget-usage-rate").text()).toBe("25%")

    await wrapper.find(".dashboard-action-button").trigger("click")

    expect(wrapper.emitted("open-budget-settings")).toHaveLength(1)
  })

  it("offers the shared button when a budget is not configured", async () => {
    const wrapper = mount(BudgetSummaryCard, {
      props: {
        budget: null,
      },
    })

    expect(wrapper.find(".budget-summary-card").classes()).toContain("app-card")
    expect(wrapper.find(".budget-empty-state").text()).toContain(
      "예산이 없습니다. 예산을 설정해주세요.",
    )
    expect(wrapper.find(".budget-empty-state .app-button").text()).toBe("설정하기")
    expect(wrapper.find('[role="progressbar"]').exists()).toBe(false)

    await wrapper.find(".budget-empty-state .app-button").trigger("click")

    expect(wrapper.emitted("open-budget-settings")).toHaveLength(1)
  })
})
