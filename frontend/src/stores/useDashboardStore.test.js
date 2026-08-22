import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { getAssets, getBudgets, getExpenses } from "@/api/assetApi"
import { useFinancialInvalidationStore } from "@/stores/financialInvalidationStore"
import { useDashboardStore } from "./useDashboardStore"

vi.mock("@/api/assetApi", () => ({
  getAssets: vi.fn(),
  getBudgets: vi.fn(),
  getExpenses: vi.fn(),
  putBudget: vi.fn(),
  syncAssets: vi.fn(),
}))

describe("useDashboardStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getAssets.mockResolvedValue({ success: true, data: { totalAssets: 1_000_000 } })
    getBudgets.mockResolvedValue({ success: true, data: { totalAmount: 500_000 } })
    getExpenses.mockResolvedValue({ success: true, data: { totalExpense: 120_000 } })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it("loads dashboard resources once and exposes the initial loading state", async () => {
    const store = useDashboardStore()
    const request = store.fetchDashboardSummary({ notifyError: false })

    expect(store.initialLoading).toBe(true)
    expect(store.refreshing).toBe(false)

    const duplicateRequest = store.fetchDashboardSummary({ notifyError: false })

    const [summary] = await Promise.all([request, duplicateRequest])

    expect(summary).toMatchObject({
      assets: { totalAssets: 1_000_000 },
      budget: { totalAmount: 500_000 },
      expenses: { totalExpense: 120_000 },
    })

    expect(store.initialLoading).toBe(false)
    expect(store.refreshing).toBe(false)
    expect(getAssets).toHaveBeenCalledOnce()
    expect(getBudgets).toHaveBeenCalledOnce()
    expect(getExpenses).toHaveBeenCalledOnce()
  })

  it("keeps cached dashboard data and separates a forced refresh", async () => {
    const store = useDashboardStore()

    await store.fetchDashboardSummary({ notifyError: false })
    await store.fetchDashboardSummary({ notifyError: false })

    expect(getAssets).toHaveBeenCalledOnce()
    expect(getBudgets).toHaveBeenCalledOnce()
    expect(getExpenses).toHaveBeenCalledOnce()

    const refreshRequest = store.fetchDashboardSummary({
      force: true,
      notifyError: false,
    })
    expect(store.refreshing).toBe(true)

    await refreshRequest

    expect(store.refreshing).toBe(false)
    expect(getAssets).toHaveBeenCalledOnce()
    expect(getBudgets).toHaveBeenCalledTimes(2)
    expect(getExpenses).toHaveBeenCalledTimes(2)
  })

  it("refetches the summary after a shared financial invalidation", async () => {
    const store = useDashboardStore()
    const invalidationStore = useFinancialInvalidationStore()

    await store.fetchDashboardSummary({ notifyError: false })
    invalidationStore.markChanged()
    await store.fetchDashboardSummary({ notifyError: false })

    expect(getAssets).toHaveBeenCalledTimes(2)
    expect(getBudgets).toHaveBeenCalledTimes(2)
    expect(getExpenses).toHaveBeenCalledTimes(2)
  })

  it("does not let an old in-flight response become the current summary", async () => {
    let resolveOldBudget
    let resolveOldExpenses
    getBudgets.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOldBudget = resolve
        }),
    )
    getExpenses.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOldExpenses = resolve
        }),
    )
    const store = useDashboardStore()
    const invalidationStore = useFinancialInvalidationStore()

    const oldRequest = store.fetchDashboardSummary({ notifyError: false })
    invalidationStore.markChanged()
    getBudgets.mockResolvedValueOnce({ success: true, data: { totalAmount: 900_000 } })
    getExpenses.mockResolvedValueOnce({ success: true, data: { totalExpense: 220_000 } })
    const currentRequest = store.fetchDashboardSummary({ notifyError: false })

    resolveOldBudget({ success: true, data: { totalAmount: 500_000 } })
    resolveOldExpenses({ success: true, data: { totalExpense: 120_000 } })
    await Promise.all([oldRequest, currentRequest])

    expect(store.budget.totalAmount).toBe(900_000)
    expect(store.expenses.totalExpense).toBe(220_000)
    expect(store.initialLoading).toBe(false)
    expect(store.refreshing).toBe(false)
  })
})
