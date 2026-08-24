import { flushPromises, mount } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { nextTick, reactive, ref } from "vue"

import CategoryExpenseView from "./CategoryExpenseView.vue"
import { getExpenses } from "@/api/assetApi"
import { useAssetStore } from "@/stores/assetStore"
import { useBudgetStore } from "@/stores/budgetStore"

const mocks = vi.hoisted(() => ({
  route: { query: {} },
  routerReplace: vi.fn(),
}))

vi.mock("@/api/assetApi", () => ({
  getExpenses: vi.fn(),
}))

vi.mock("@/stores/assetStore", () => ({
  useAssetStore: vi.fn(),
}))

vi.mock("@/stores/budgetStore", () => ({
  useBudgetStore: vi.fn(),
}))

vi.mock("vue-router", () => ({
  RouterLink: {
    props: ["to"],
    template: '<a :href="to"><slot /></a>',
  },
  useRoute: () => mocks.route,
  useRouter: () => ({
    replace: mocks.routerReplace,
  }),
}))

const getMonthKey = (date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`

const createExpenseResponse = () => ({
  success: true,
  data: {
    totalExpense: 300_000,
    expenseCategoryBreakdown: [
      { category: "FOOD", amount: 200_000 },
      { category: "CAFE", amount: 100_000 },
    ],
  },
})

const createBudgetStore = () => ({
  categorySummary: ref({
    targetMonth: getMonthKey(new Date()),
    totalAmount: 1_000_000,
    allocatedAmount: 1_000_000,
    unallocatedAmount: 0,
    spentAmount: 300_000,
    remainingAmount: 700_000,
    usageRate: 30,
    overBudget: false,
    categories: [
      {
        category: "FOOD",
        budgetAmount: 500_000,
        spentAmount: 200_000,
        remainingAmount: 300_000,
        usageRate: 40,
        overBudget: false,
      },
    ],
  }),
  initialLoading: ref(false),
  refreshing: ref(false),
  isLoading: ref(false),
  isSaving: ref(false),
  error: ref(null),
  fetchCategoryBudgets: vi.fn().mockResolvedValue(null),
  saveCategoryBudgets: vi.fn().mockResolvedValue(null),
})

const createAssetStore = () => ({
  isSyncing: ref(false),
  syncError: ref(null),
  syncAssets: vi.fn().mockResolvedValue({
    inserted: 2,
    updated: 1,
    failedConnections: 0,
  }),
})

const globalStubs = {
  AppPageHeader: {
    props: ["title"],
    template:
      '<header class="app-page-header"><slot name="leading" /><h1><slot name="title">{{ title }}</slot></h1><slot name="actions" /></header>',
  },
  AppAlert: {
    template: '<div class="app-alert"><slot /></div>',
  },
  AppState: {
    props: ["type", "title", "message"],
    template:
      '<section class="app-state" :data-state="type"><strong>{{ title }}</strong><span>{{ message }}</span></section>',
  },
  ExpenseCategoryBreakdown: {
    props: [
      "breakdown",
      "totalExpense",
      "budgetSummary",
      "budgetLoading",
      "budgetError",
      "canEditBudget",
    ],
    template: `
      <article data-testid="expense-category-breakdown">
        <slot name="header" />
        <section data-testid="category-budget">카테고리별 예산</section>
        <button
          v-if="canEditBudget"
          type="button"
          data-testid="budget-action"
          @click="$emit('edit-budget')"
        >
          예산 수정
        </button>
      </article>
    `,
  },
  CategoryBudgetEditor: {
    props: ["visible"],
    template: '<div v-if="visible" data-testid="category-budget-editor">editor</div>',
  },
}

describe("CategoryExpenseView", () => {
  let budgetStore
  let assetStore

  beforeEach(() => {
    mocks.route = reactive({ query: {} })
    mocks.routerReplace.mockReset()
    mocks.routerReplace.mockResolvedValue(undefined)
    budgetStore = createBudgetStore()
    assetStore = createAssetStore()
    useAssetStore.mockReturnValue(assetStore)
    useBudgetStore.mockReturnValue(budgetStore)
    getExpenses.mockResolvedValue(createExpenseResponse())
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it("renders the monthly report breakdown and budget instead of the legacy category list and transactions", async () => {
    const wrapper = mount(CategoryExpenseView, { global: { stubs: globalStubs } })
    await flushPromises()

    expect(getExpenses).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 20 }))
    expect(budgetStore.fetchCategoryBudgets).toHaveBeenCalledWith(getMonthKey(new Date()), {
      notifyError: false,
    })
    expect(wrapper.find('[data-testid="expense-category-breakdown"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="category-budget"]').exists()).toBe(true)
    expect(wrapper.find(".category-option-list").exists()).toBe(false)
    expect(wrapper.find('[data-testid="transaction-list"]').exists()).toBe(false)
    expect(wrapper.find(".app-page-header h1").text()).toBe("카테고리별 소비")
    expect(wrapper.find(".category-sync-button").classes()).toContain("app-action-link")
    expect(wrapper.find(".category-sync-button").text()).toContain("새로고침")
    expect(wrapper.find(".category-sync-button .bi-arrow-clockwise").exists()).toBe(true)
    expect(wrapper.find(".app-page-header .month-navigation").exists()).toBe(false)
    expect(
      wrapper.find('[data-testid="expense-category-breakdown"] .month-navigation').exists(),
    ).toBe(true)
    expect(wrapper.find(".category-expense-toolbar").exists()).toBe(false)
    expect(wrapper.findAll(".month-button")).toHaveLength(2)

    wrapper.unmount()
  })

  it("syncs assets and reloads the selected month when refreshing", async () => {
    const wrapper = mount(CategoryExpenseView, { global: { stubs: globalStubs } })
    await flushPromises()

    await wrapper.get(".category-sync-button").trigger("click")
    await flushPromises()

    expect(assetStore.syncAssets).toHaveBeenCalledOnce()
    expect(getExpenses).toHaveBeenCalledTimes(2)
    expect(budgetStore.fetchCategoryBudgets).toHaveBeenCalledTimes(2)
    expect(budgetStore.fetchCategoryBudgets).toHaveBeenLastCalledWith(getMonthKey(new Date()), {
      notifyError: false,
      force: true,
    })
    expect(wrapper.find('[data-testid="category-refresh-button"]').element.disabled).toBe(false)
    expect(wrapper.find(".category-sync-status").exists()).toBe(true)

    wrapper.unmount()
  })

  it("keeps month navigation and reloads expense and budget data for the selected month", async () => {
    const wrapper = mount(CategoryExpenseView, { global: { stubs: globalStubs } })
    await flushPromises()
    getExpenses.mockClear()
    budgetStore.fetchCategoryBudgets.mockClear()

    const currentMonth = new Date()
    const previousMonth = new Date(currentMonth.getFullYear(), currentMonth.getMonth() - 1, 1)
    await wrapper.get('[aria-label="이전 달"]').trigger("click")
    await flushPromises()

    expect(getExpenses).toHaveBeenLastCalledWith({
      startDate: `${previousMonth.getFullYear()}-${String(previousMonth.getMonth() + 1).padStart(2, "0")}-01`,
      endDate: `${previousMonth.getFullYear()}-${String(previousMonth.getMonth() + 1).padStart(2, "0")}-${new Date(previousMonth.getFullYear(), previousMonth.getMonth() + 1, 0).getDate()}`,
      page: 0,
      size: 20,
    })
    expect(budgetStore.fetchCategoryBudgets).toHaveBeenCalledWith(getMonthKey(previousMonth), {
      notifyError: false,
    })

    wrapper.unmount()
  })

  it("keeps the refresh status beside the title while changing months", async () => {
    const wrapper = mount(CategoryExpenseView, { global: { stubs: globalStubs } })
    await flushPromises()

    let resolveRefresh
    getExpenses.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveRefresh = resolve
        }),
    )

    await wrapper.get('[aria-label="다음 달"]').trigger("click")
    await nextTick()

    expect(wrapper.find(".app-page-header .category-refresh-status").exists()).toBe(true)
    expect(wrapper.find(".category-expense-view > .category-refresh-status").exists()).toBe(false)

    resolveRefresh(createExpenseResponse())
    await flushPromises()

    expect(wrapper.find(".app-page-header .category-refresh-status").exists()).toBe(false)
    wrapper.unmount()
  })

  it("opens the existing category budget editor from the breakdown", async () => {
    const wrapper = mount(CategoryExpenseView, { global: { stubs: globalStubs } })
    await flushPromises()

    await wrapper.get('[data-testid="budget-action"]').trigger("click")
    await flushPromises()

    expect(wrapper.find('[data-testid="category-budget-editor"]').exists()).toBe(true)
    expect(mocks.routerReplace).toHaveBeenCalledWith({ query: { budget: "edit" } })

    wrapper.unmount()
  })

  it("shows an error state and alerts when the monthly expense request fails", async () => {
    const alertSpy = vi.spyOn(globalThis, "alert").mockImplementation(() => {})
    getExpenses.mockRejectedValue(new Error("network error"))

    const wrapper = mount(CategoryExpenseView, { global: { stubs: globalStubs } })
    await flushPromises()

    expect(wrapper.text()).toContain("카테고리별 소비를 불러오지 못했습니다.")
    expect(alertSpy).toHaveBeenCalledOnce()

    wrapper.unmount()
  })
})
