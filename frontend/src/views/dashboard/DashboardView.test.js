import { flushPromises, mount } from "@vue/test-utils"
import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import DashboardView from "./DashboardView.vue"
import { getAssets, getBudgets, getExpenses } from "@/api/assetApi"
import { getGoalRoadmap, getGoals } from "@/api/goalApi"

vi.mock("@/api/goalApi", () => ({
  getAvailableGoalAccounts: vi.fn(),
  getGoalRoadmap: vi.fn(),
  getGoals: vi.fn(),
  selectGoalAccount: vi.fn(),
  updateGoalRoadmapStep: vi.fn(),
}))

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
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

const mountDashboard = () => mount(DashboardView, {
  global: {
    stubs: {
      AssetSummaryCard: { template: "<div />" },
      BudgetSummaryCard: { template: "<div />" },
      ExpenseSummaryCard: { template: "<div />" },
      RouterLink: {
        props: ["to"],
        template: "<a :href=\"to\"><slot /></a>",
      },
    },
  },
})

describe("DashboardView", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getGoals.mockResolvedValue({ data: [latestGoal] })
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

    expect(wrapper.find(".goal-progress-amount").text()).toContain("1,400,000")
    expect(wrapper.find(".goal-progress-rate").text()).toContain("14%")

    wrapper.unmount()
  })
})
