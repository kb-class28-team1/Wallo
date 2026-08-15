import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import ExpenseCategoryBreakdown from "./ExpenseCategoryBreakdown.vue";

vi.mock("vue-chartjs", () => ({
  Doughnut: {
    name: "Doughnut",
    template: "<div data-testid=\"doughnut\" />",
  },
}));

const globalOptions = {
  global: {
    stubs: {
      Doughnut: { template: "<div data-testid=\"doughnut\" />" },
    },
  },
};

const budgetSummary = {
  targetMonth: "2026-08",
  totalAmount: 1_000_000,
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
};

describe("ExpenseCategoryBreakdown budget section", () => {
  it("shows budget progress, remaining amounts, zero-budget status, and ETC label", () => {
    const wrapper = mount(ExpenseCategoryBreakdown, {
      ...globalOptions,
      props: {
        breakdown: [{ category: "FOOD", amount: 360_000 }],
        totalExpense: 411_000,
        budgetSummary,
        canEditBudget: true,
      },
    });

    const rows = wrapper.findAll(".budget-category-row");
    expect(rows).toHaveLength(3);
    expect(wrapper.text()).toContain("기타·미배정");
    expect(wrapper.text()).toContain("예산 없음");
    expect(rows[0].classes()).toContain("budget-category-row-over");
    expect(rows[1].classes()).toContain("budget-category-row-over");
    expect(rows[0].find(".progress-bar").attributes("style")).toContain("width: 100%");
  });

  it("emits the budget edit event only from the enabled action", async () => {
    const wrapper = mount(ExpenseCategoryBreakdown, {
      ...globalOptions,
      props: {
        budgetSummary,
        canEditBudget: true,
      },
    });

    await wrapper.find("button").trigger("click");
    expect(wrapper.emitted("edit-budget")).toHaveLength(1);

    const readOnlyWrapper = mount(ExpenseCategoryBreakdown, {
      ...globalOptions,
      props: { budgetSummary, canEditBudget: false },
    });
    expect(readOnlyWrapper.find("button").exists()).toBe(false);
  });
});

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
    });

    expect(wrapper.findAll(".category-list li")).toHaveLength(7);
    expect(wrapper.find(".category-list").classes()).toContain("category-list-two-columns");
  });
});
