import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"

import BudgetSummaryCard from "./BudgetSummaryCard.vue"

describe("BudgetSummaryCard", () => {
  it("renders budget usage as melting ice", async () => {
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
    expect(wrapper.find(".ice-budget-meter").exists()).toBe(true)
    expect(progress.attributes("aria-valuenow")).toBe("25")
    expect(progress.findAll(".iceberg-piece")).toHaveLength(4)
    expect(progress.findAll(".iceberg-capacity-outline")).toHaveLength(4)
    expect(progress.findAll(".ice-face").length).toBeGreaterThan(3)
    expect(progress.findAll(".snowflake")).toHaveLength(30)
    expect(progress.find(".meltwater").attributes("style")).toContain(
      "--meltwater-scale-x: 0.875",
    )
    expect(progress.find(".ice-stage-badge").exists()).toBe(false)
    expect(wrapper.find(".budget-total").text()).toBe("375,000원")
    expect(wrapper.find(".ice-budget-caption").text()).toBe("남은 비율")
    expect(wrapper.find(".budget-remaining-rate").text()).toBe("75%")
    expect(wrapper.find(".ice-budget-status").exists()).toBe(false)

    await wrapper.find(".dashboard-action-button").trigger("click")

    expect(wrapper.emitted("open-budget-settings")).toHaveLength(1)
  })

  it("renders the shared empty state when a budget is not configured", async () => {
    const wrapper = mount(BudgetSummaryCard, {
      props: {
        budget: null,
      },
    })

    expect(wrapper.find(".budget-summary-card").classes()).toContain("app-card")
    expect(wrapper.find(".budget-state").classes()).toContain("app-state")
    expect(wrapper.find(".budget-state").attributes("data-state")).toBe("empty")
    expect(wrapper.find(".budget-state .app-state__title").text()).toBe(
      "아직 설정된 예산이 없습니다.",
    )
    expect(wrapper.find(".budget-state .app-state__message").text()).toBe("예산을 설정해주세요")
    expect(wrapper.find(".budget-state .app-state__icon").exists()).toBe(false)
    expect(wrapper.find(".dashboard-action-button").text()).toBe("설정하기")
    expect(wrapper.find('[role="progressbar"]').exists()).toBe(false)

    await wrapper.find(".dashboard-action-button").trigger("click")

    expect(wrapper.emitted("open-budget-settings")).toHaveLength(1)
  })
})
