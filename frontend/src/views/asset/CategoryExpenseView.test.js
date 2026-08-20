import { flushPromises, mount } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import CategoryExpenseView from "./CategoryExpenseView.vue"
import { getExpenses } from "@/api/assetApi"

vi.mock("@/api/assetApi", () => ({
  getExpenses: vi.fn(),
}))

const createExpenseData = ({ category } = {}) => ({
  totalExpense: 300_000,
  expenseCategoryBreakdown: [
    { category: "FOOD", amount: 200_000 },
    { category: "CAFE", amount: 100_000 },
  ],
  transactions: [
    {
      transactionId: category === "CAFE" ? 2 : 1,
      date: "2026-08-11",
      type: "EXPENSE",
      category: category || "FOOD",
      amount: category === "CAFE" ? 100_000 : 200_000,
      merchantName: category === "CAFE" ? "카페 테스트" : "식당 테스트",
    },
  ],
  pagination: {
    currentPage: 0,
    totalPages: 1,
    totalElements: 1,
    hasNext: false,
  },
})

const globalStubs = {
  RouterLink: {
    props: ["to"],
    template: '<a :href="to"><slot /></a>',
  },
  AppPageHeader: {
    props: ["title"],
    template: '<header class="app-page-header"><slot name="leading" /><h1>{{ title }}</h1><slot name="actions" /></header>',
  },
  AppCard: {
    template: '<article class="app-card"><slot /></article>',
  },
  AppAlert: {
    props: ["message"],
    template: '<div class="app-alert"><span v-if="message">{{ message }}</span><slot /></div>',
  },
  AppState: {
    props: ["type", "title", "message"],
    template: '<section class="app-state" :data-state="type"><strong>{{ title }}</strong><span>{{ message }}</span><slot /></section>',
  },
  ExpenseTransactionList: {
    props: ["transactions", "hasNext", "emptyMessage"],
    template: `
      <div data-testid="transaction-list">
        <span data-testid="transaction-count">{{ transactions.length }}</span>
        <span>{{ emptyMessage }}</span>
        <button v-if="hasNext" type="button" data-testid="load-more" @click="$emit('load-more')">더보기</button>
      </div>
    `,
  },
}

describe("CategoryExpenseView", () => {
  beforeEach(() => {
    getExpenses.mockImplementation((params = {}) =>
      Promise.resolve({
        success: true,
        data: createExpenseData({ category: params.category }),
      }),
    )
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it("loads the selected month's category amounts and transactions", async () => {
    const wrapper = mount(CategoryExpenseView, { global: { stubs: globalStubs } })
    await flushPromises()

    expect(wrapper.get(".back-button").attributes("href")).toBe("/assets")
    expect(wrapper.get(".back-button").find(".bi-chevron-left").exists()).toBe(true)
    expect(getExpenses).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20 }),
    )
    expect(wrapper.find('[data-testid="category-option-ALL"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="category-option-FOOD"]').text()).toContain("식비")
    expect(wrapper.find('[data-testid="category-option-CAFE"]').text()).toContain("카페")
    expect(wrapper.find('[data-testid="transaction-count"]').text()).toBe("1")
    expect(wrapper.text()).toContain("300,000원")
  })

  it("requests and displays transactions for the selected category", async () => {
    const wrapper = mount(CategoryExpenseView, { global: { stubs: globalStubs } })
    await flushPromises()

    await wrapper.get('[data-testid="category-option-CAFE"]').trigger("click")
    await flushPromises()

    expect(getExpenses).toHaveBeenLastCalledWith(
      expect.objectContaining({ category: "CAFE", page: 0, size: 20 }),
    )
    expect(wrapper.text()).toContain("카페 거래내역")
    expect(wrapper.find('[data-testid="transaction-count"]').text()).toBe("1")
  })

  it("shows an empty state and alerts when the monthly request fails", async () => {
    const alertSpy = vi.spyOn(globalThis, "alert").mockImplementation(() => {})
    getExpenses.mockRejectedValue(new Error("network error"))

    const wrapper = mount(CategoryExpenseView, { global: { stubs: globalStubs } })
    await flushPromises()

    expect(wrapper.text()).toContain("소비 내역을 불러오지 못했습니다.")
    expect(alertSpy).toHaveBeenCalledOnce()

    alertSpy.mockRestore()
  })
})
