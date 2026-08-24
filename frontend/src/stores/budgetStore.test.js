import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { getCategoryBudgets, putCategoryBudgets } from "@/api/assetApi"
import { useFinancialInvalidationStore } from "@/stores/financialInvalidationStore"
import { useBudgetStore } from "./budgetStore"

vi.mock("@/api/assetApi", () => ({
  getCategoryBudgets: vi.fn(),
  putCategoryBudgets: vi.fn(),
}))

const categorySummaryResponse = {
  success: true,
  data: {
    targetMonth: "2026-08",
    totalAmount: 1_000_000,
    allocatedAmount: 300_000,
    unallocatedAmount: 700_000,
    spentAmount: 360_000,
    remainingAmount: 640_000,
    usageRate: "36.00",
    overBudget: false,
    categories: [
      {
        category: "FOOD",
        budgetAmount: 300_000,
        spentAmount: 360_000,
        remainingAmount: -60_000,
        usageRate: "120.00",
        overBudget: true,
      },
      {
        category: "CAFE",
        budgetAmount: 0,
        spentAmount: 0,
        remainingAmount: 0,
        usageRate: "0.00",
        overBudget: false,
      },
    ],
  },
}

describe("budgetStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it("normalizes category budget response data and exposes budget state", async () => {
    getCategoryBudgets.mockResolvedValue(categorySummaryResponse)
    const store = useBudgetStore()

    await expect(
      store.fetchCategoryBudgets("2026-08", { notifyError: false }),
    ).resolves.toMatchObject({ targetMonth: "2026-08" })

    expect(getCategoryBudgets).toHaveBeenCalledWith("2026-08")
    expect(store.categorySummary.usageRate).toBe(36)
    expect(store.categorySummary.categories[0].usageRate).toBe(120)
    expect(store.hasBudget).toBe(true)
    expect(store.isLoading).toBe(false)
  })

  it("stores the saved summary and clears the saving state", async () => {
    putCategoryBudgets.mockResolvedValue(categorySummaryResponse)
    const store = useBudgetStore()
    const request = {
      targetMonth: "2026-08",
      totalAmount: 1_000_000,
      categoryBudgets: [{ category: "FOOD", budgetAmount: 300_000 }],
    }

    await expect(store.saveCategoryBudgets(request, { notifyError: false })).resolves.toMatchObject(
      { totalAmount: 1_000_000 },
    )

    expect(putCategoryBudgets).toHaveBeenCalledWith(request)
    expect(store.isSaving).toBe(false)
    expect(store.error).toBeNull()
  })

  it("records and rethrows a category budget load failure", async () => {
    const error = new Error("budget unavailable")
    getCategoryBudgets.mockRejectedValue(error)
    const store = useBudgetStore()

    await expect(store.fetchCategoryBudgets("2026-08", { notifyError: false })).rejects.toBe(error)

    expect(store.categorySummary).toBeNull()
    expect(store.error).toBe("budget unavailable")
    expect(store.isLoading).toBe(false)
  })

  it("refetches the cached month after a shared financial invalidation", async () => {
    getCategoryBudgets.mockResolvedValue(categorySummaryResponse)
    const store = useBudgetStore()
    const invalidationStore = useFinancialInvalidationStore()

    await store.fetchCategoryBudgets("2026-08", { notifyError: false })
    invalidationStore.markChanged()
    await store.fetchCategoryBudgets("2026-08", { notifyError: false })

    expect(getCategoryBudgets).toHaveBeenCalledTimes(2)
  })

  it("does not let an old in-flight response become the fresh cache", async () => {
    let resolveOldRequest
    getCategoryBudgets.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOldRequest = resolve
        }),
    )
    const store = useBudgetStore()
    const invalidationStore = useFinancialInvalidationStore()

    const oldRequest = store.fetchCategoryBudgets("2026-08", { notifyError: false })
    invalidationStore.markChanged()
    getCategoryBudgets.mockResolvedValueOnce({
      ...categorySummaryResponse,
      data: { ...categorySummaryResponse.data, spentAmount: 420_000 },
    })
    const currentRequest = store.fetchCategoryBudgets("2026-08", { notifyError: false })

    resolveOldRequest(categorySummaryResponse)
    await Promise.all([oldRequest, currentRequest])

    expect(getCategoryBudgets).toHaveBeenCalledTimes(2)
    expect(store.categorySummary.spentAmount).toBe(420_000)
  })
})
