import { mount } from "@vue/test-utils"
import { describe, expect, it, vi } from "vitest"
import ExpenseCategoryBreakdown from "./ExpenseCategoryBreakdown.vue"

vi.mock("vue-chartjs", () => ({
  Doughnut: {
    name: "Doughnut",
    template: '<div data-testid="doughnut" />',
  },
}))

const globalOptions = {
  global: {
    stubs: {
      Doughnut: { template: '<div data-testid="doughnut" />' },
    },
  },
}

const budgetSummary = {
  targetMonth: "2026-08",
  totalAmount: 1_000_000,
  allocatedAmount: 300_000,
  unallocatedAmount: 700_000,
  spentAmount: 411_000,
  remainingAmount: 589_000,
  usageRate: 41.1,
  overBudget: false,
  categories: [
    {
      category: "FOOD",
      budgetAmount: 300_000,
      spentAmount: 360_000,
      remainingAmount: -60_000,
      usageRate: 120,
      overBudget: true,
    },
    {
      category: "CAFE",
      budgetAmount: 0,
      spentAmount: 1_000,
      remainingAmount: -1_000,
      usageRate: null,
      overBudget: true,
    },
    {
      category: "ETC",
      budgetAmount: 700_000,
      spentAmount: 50_000,
      remainingAmount: 650_000,
      usageRate: 7.14,
      overBudget: false,
    },
  ],
}

describe("ExpenseCategoryBreakdown budget section", () => {
  it("separates unallocated budget and other spending from budgeted categories", () => {
    const wrapper = mount(ExpenseCategoryBreakdown, {
      ...globalOptions,
      props: {
        breakdown: [{ category: "FOOD", amount: 360_000 }],
        totalExpense: 411_000,
        budgetSummary,
        canEditBudget: true,
      },
    })

    const rows = wrapper.findAll(".budget-category-row")
    expect(rows).toHaveLength(1)
    expect(wrapper.text()).toContain("미배분 예산")
    expect(wrapper.text()).toContain("700,000원")
    expect(wrapper.text()).toContain("기타 지출")
    expect(wrapper.text()).toContain("50,000원")
    expect(wrapper.text()).not.toContain("기타·미배정")
    expect(wrapper.text()).not.toContain("카페")
    expect(wrapper.text()).not.toContain("예산 없음")
    expect(rows[0].classes()).toContain("budget-category-row-over")
    expect(rows[0].find(".progress-bar").attributes("style")).toContain("width: 100%")
  })

  it("emits the budget edit event from the enabled edit action", async () => {
    const wrapper = mount(ExpenseCategoryBreakdown, {
      ...globalOptions,
      props: {
        budgetSummary,
        canEditBudget: true,
      },
    })

    const budgetAction = wrapper.get('[data-testid="budget-action"]')
    expect(budgetAction.text()).toContain("예산 수정")
    expect(budgetAction.classes()).toContain("app-action-link")
    expect(budgetAction.find(".bi-arrow-right").exists()).toBe(true)
    await budgetAction.trigger("click")
    expect(wrapper.emitted("edit-budget")).toHaveLength(1)

    const readOnlyWrapper = mount(ExpenseCategoryBreakdown, {
      ...globalOptions,
      props: { budgetSummary, canEditBudget: false },
    })
    expect(readOnlyWrapper.find("button").exists()).toBe(false)
  })

  it("shows the dashboard-style setup state when no budget is configured", async () => {
    const wrapper = mount(ExpenseCategoryBreakdown, {
      ...globalOptions,
      props: {
        budgetSummary: {
          ...budgetSummary,
          totalAmount: 0,
          categories: budgetSummary.categories.map((category) => ({
            ...category,
            budgetAmount: 0,
          })),
        },
        canEditBudget: true,
      },
    })

    expect(wrapper.find('[data-testid="budget-empty-state"]').exists()).toBe(true)
    expect(wrapper.find(".budget-category-row").exists()).toBe(false)
    expect(wrapper.text()).toContain("아직 설정된 예산이 없습니다.")
    expect(wrapper.text()).toContain("예산을 설정해주세요")
    expect(wrapper.get('[data-testid="budget-action"]').text()).toContain("예산 설정하기")

    await wrapper.get('[data-testid="budget-action"]').trigger("click")
    expect(wrapper.emitted("edit-budget")).toHaveLength(1)
  })

  it("shows historical-budget guidance for a past month without a budget", () => {
    const wrapper = mount(ExpenseCategoryBreakdown, {
      ...globalOptions,
      props: {
        budgetSummary: {
          ...budgetSummary,
          totalAmount: 0,
          categories: [],
        },
        canEditBudget: false,
      },
    })

    expect(wrapper.get('[data-testid="budget-empty-state"]').text()).toContain(
      "이 달에 설정된 예산이 없습니다.",
    )
    expect(wrapper.get('[data-testid="budget-empty-state"]').text()).toContain(
      "예산은 현재 달부터 설정하고 관리할 수 있습니다.",
    )
    expect(wrapper.find('[data-testid="budget-action"]').exists()).toBe(false)
  })
})

describe("ExpenseCategoryBreakdown category chart", () => {
  it("shows every category in two vertical columns when there are more than six", () => {
    const wrapper = mount(ExpenseCategoryBreakdown, {
      ...globalOptions,
      props: {
        breakdown: [
          { category: "FOOD", amount: 100_000 },
          { category: "CAFE", amount: 90_000 },
          { category: "TRANSPORT", amount: 80_000 },
          { category: "SHOPPING", amount: 70_000 },
          { category: "DELIVERY", amount: 60_000 },
          { category: "HOUSING", amount: 50_000 },
          { category: "LIVING", amount: 40_000 },
        ],
        totalExpense: 490_000,
      },
    })

    expect(wrapper.findAll(".category-list li")).toHaveLength(7)
    expect(wrapper.find(".category-list").classes()).toContain("category-list-two-columns")
    expect(wrapper.find(".category-list").attributes("style")).toContain(
      "--category-list-row-count: 4",
    )
  })
})
