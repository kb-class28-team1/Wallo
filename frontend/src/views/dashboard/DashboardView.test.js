import { flushPromises, mount } from "@vue/test-utils"
import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import DashboardView from "./DashboardView.vue"
import { getAssets, getBudgets, getExpenses } from "@/api/assetApi"
import { getAvailableGoalAccounts, getGoalRoadmap, getGoals } from "@/api/goalApi"
import { useGoalStore } from "@/stores/goalStore"
import { useUserStore } from "@/stores/userStore"

const push = vi.hoisted(() => vi.fn())

vi.mock("@/api/goalApi", () => ({
  getAvailableGoalAccounts: vi.fn(),
  getGoalRoadmap: vi.fn(),
  getGoals: vi.fn(),
  selectGoalAccount: vi.fn(),
  updateGoalRoadmapStep: vi.fn(),
}))

vi.mock("vue-router", () => ({
  useRouter: () => ({ push }),
}))

vi.mock("@/api/assetApi", () => ({
  getAssets: vi.fn(),
  getBudgets: vi.fn(),
  getExpenses: vi.fn(),
  putBudget: vi.fn(),
  syncAssets: vi.fn(),
}))

vi.mock("@/features/financial/useDashboardCharts", () => ({
  useDashboardCharts: () => ({
    assetTrendChartData: null,
    expenseChartData: null,
  }),
}))

const latestGoal = {
  goalId: 31,
  title: "鍮꾩긽湲?紐⑺몴",
  goalType: "EMERGENCY_FUND",
  targetAmount: 10_000_000,
  currentAmount: 1_400_000,
  achievementRate: 14,
  requiredMonthlyAmount: 600_000,
  targetDate: "2027-12-31",
}

const mountDashboard = () =>
  mount(DashboardView, {
    global: {
      stubs: {
        AssetSummaryCard: { template: '<div data-testid="asset-summary-card" />' },
        BudgetSummaryCard: {
          template: '<button data-testid="budget-settings" @click="$emit(\'open-budget-settings\')">설정하기</button>',
        },
        ExpenseSummaryCard: { template: '<div data-testid="expense-summary-card" />' },
        RouterLink: {
          props: ["to"],
          template: '<a :href="to"><slot /></a>',
        },
      },
    },
  })

describe("DashboardView", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getGoals.mockResolvedValue({ data: [latestGoal] })
    getAvailableGoalAccounts.mockResolvedValue({ data: [] })
    getGoalRoadmap.mockResolvedValue({ data: null })
    getAssets.mockResolvedValue({ data: null })
    getBudgets.mockResolvedValue({ data: null })
    getExpenses.mockResolvedValue({ data: null })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it("shows the latest goal amount and achievement rate returned after account save", async () => {
    const wrapper = mountDashboard()

    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find(".goal-summary-card").exists()).toBe(true))

    expect(getGoals).toHaveBeenCalledWith({ syncAccounts: false })
    expect(getAvailableGoalAccounts).toHaveBeenCalledWith()
    expect(wrapper.find(".goal-progress-amount").text()).toContain("1,400,000")
    expect(wrapper.find(".goal-progress-rate").text()).toContain("14%")
    expect(wrapper.find('[data-testid="asset-summary-card"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="expense-summary-card"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it("opens the budget editor on the category expense page", async () => {
    const wrapper = mountDashboard()

    await flushPromises()
    await wrapper.get('[data-testid="budget-settings"]').trigger("click")

    expect(push).toHaveBeenCalledWith({
      name: "category-expenses",
      query: { budget: "edit" },
    })

    wrapper.unmount()
  })

  it("loads dashboard goals in the current authenticated user's cache scope", async () => {
    const userStore = useUserStore()
    const goalStore = useGoalStore()
    userStore.user = { id: 42, nickname: "Tester" }

    const wrapper = mountDashboard()

    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find(".goal-summary-card").exists()).toBe(true))

    expect(goalStore.lastFetchedUserId).toBe("42")

    wrapper.unmount()
  })

  it("renders the shared page header and dashboard card grids", async () => {
    const wrapper = mountDashboard()

    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find(".app-page-header").exists()).toBe(true))

    expect(wrapper.find(".app-page-header__title").text()).toBe("대시보드")
    expect(wrapper.find(".app-page-header__description").exists()).toBe(false)
    expect(wrapper.find(".dashboard-card-grid").exists()).toBe(true)
    expect(wrapper.find(".dashboard-summary-grid").exists()).toBe(true)

    wrapper.unmount()
  })
})
