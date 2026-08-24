import { mount } from "@vue/test-utils"
import { describe, expect, it, vi } from "vitest"

import ExpenseSummaryCard from "./ExpenseSummaryCard.vue"

vi.mock("vue-chartjs", () => ({
  Doughnut: {
    template: '<div data-testid="doughnut-chart"></div>',
  },
}))

const globalOptions = {
  global: {
    stubs: {
      RouterLink: {
        props: ["to"],
        template: '<a :href="to"><slot /></a>',
      },
    },
  },
}

const createChartData = (data = [30_000, 20_000]) => ({
  labels: ["식비", "쇼핑"],
  datasets: [
    {
      data,
      backgroundColor: ["#ff7b6b", "#5b8def"],
    },
  ],
})

describe("ExpenseSummaryCard", () => {
  it("renders the expense chart, category rates, and shared card shell", () => {
    const wrapper = mount(ExpenseSummaryCard, {
      ...globalOptions,
      props: {
        expenses: {
          totalExpense: 50_000,
          expenseCategoryBreakdown: [
            { category: "FOOD", amount: 30_000 },
            { category: "SHOPPING", amount: 20_000 },
          ],
        },
        chartData: createChartData(),
      },
    })

    const categories = wrapper.findAll(".expense-category-list li")

    expect(wrapper.find(".expense-summary-card").classes()).toContain("app-card")
    expect(wrapper.find(".expense-total").text()).toBe("50,000원")
    expect(wrapper.find('[data-testid="doughnut-chart"]').exists()).toBe(true)
    expect(categories).toHaveLength(2)
    expect(categories[0].text()).toContain("식비")
    expect(categories[0].text()).toContain("60%")
    expect(categories[1].text()).toContain("쇼핑")
    expect(categories[1].text()).toContain("40%")
    expect(wrapper.find("a").attributes("href")).toBe("/assets/expenses")
  })

  it("renders the shared empty state when no expense data exists", () => {
    const wrapper = mount(ExpenseSummaryCard, {
      ...globalOptions,
      props: {
        expenses: {
          totalExpense: 0,
          expenseCategoryBreakdown: [],
        },
        chartData: createChartData([]),
      },
    })

    expect(wrapper.find(".expense-summary-card").classes()).toContain("app-card")
    expect(wrapper.find(".expense-empty-state").classes()).toContain("app-state")
    expect(wrapper.find(".expense-empty-state").attributes("data-state")).toBe("empty")
    expect(wrapper.find(".expense-empty-state").text()).toContain("이번 달 지출 데이터가 없습니다.")
    expect(wrapper.find('[data-testid="doughnut-chart"]').exists()).toBe(false)
  })

  it("renders safely when the API has not returned expense data yet", () => {
    const wrapper = mount(ExpenseSummaryCard, {
      ...globalOptions,
      props: {
        expenses: null,
        chartData: createChartData([]),
      },
    })

    expect(wrapper.find(".expense-total").text()).toBe("0원")
    expect(wrapper.find(".expense-empty-state").exists()).toBe(true)
    expect(wrapper.find('[data-testid="doughnut-chart"]').exists()).toBe(false)
  })
})
