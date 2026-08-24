import { createPinia, setActivePinia } from "pinia"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { getInsight, getTaxSettlement, updateAnnualSalary } from "@/api/assetApi"
import { useFinancialInvalidationStore } from "@/stores/financialInvalidationStore"
import { ANNUAL_SALARY_LOOKUP_STATUS, useReportStore } from "./assetReportStore"

vi.mock("@/api/assetApi", () => ({
  getInsight: vi.fn(),
  getTaxSettlement: vi.fn(),
  updateAnnualSalary: vi.fn(),
}))

describe("assetReportStore annual salary lookup state", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it("marks an available salary after a successful tax settlement response", async () => {
    getTaxSettlement.mockResolvedValue({
      data: { annualSalary: 50_000_000 },
    })
    const store = useReportStore()

    await store.fetchTaxSettlement()

    expect(store.annualSalaryLookupStatus).toBe(ANNUAL_SALARY_LOOKUP_STATUS.AVAILABLE)
    expect(store.taxSettlementErrorCode).toBeNull()
    expect(store.annualSalaryError).toBeNull()
  })

  it("refetches the insight on each request", async () => {
    const insight = { score: 82, title: "좋은 흐름이에요" }
    getInsight.mockResolvedValue({ data: insight })
    const store = useReportStore()

    await expect(store.fetchInsight({ notifyError: false })).resolves.toEqual(insight)
    await expect(store.fetchInsight({ notifyError: false })).resolves.toEqual(insight)

    expect(getInsight).toHaveBeenCalledTimes(2)
    expect(store.initialInsightLoading).toBe(false)

    const refreshRequest = store.fetchInsight({
      notifyError: false,
      force: true,
    })
    expect(store.refreshingInsight).toBe(true)

    await refreshRequest

    expect(getInsight).toHaveBeenCalledTimes(3)
    expect(store.refreshingInsight).toBe(false)
    expect(store.isInsightLoading).toBe(false)
  })

  it("refetches cached tax settlement data after a shared invalidation", async () => {
    getTaxSettlement.mockResolvedValue({
      data: { annualSalary: 50_000_000, cardSpentYtd: 1_000_000 },
    })
    const store = useReportStore()
    const invalidationStore = useFinancialInvalidationStore()

    await store.fetchTaxSettlement()
    invalidationStore.markChanged()
    getTaxSettlement.mockResolvedValueOnce({
      data: { annualSalary: 50_000_000, cardSpentYtd: 2_000_000 },
    })
    await store.fetchTaxSettlement()

    expect(getTaxSettlement).toHaveBeenCalledTimes(2)
    expect(store.taxSettlement.cardSpentYtd).toBe(2_000_000)
  })

  it("saves a manual salary and refetches the tax settlement", async () => {
    updateAnnualSalary.mockResolvedValue({
      data: { annualSalary: 50_000_000 },
    })
    getTaxSettlement.mockResolvedValue({
      data: {
        annualSalary: 50_000_000,
        creditCardThreshold: 12_500_000,
      },
    })
    const store = useReportStore()
    store.setAnnualSalaryLookupStatus("UNAVAILABLE")

    await store.saveAnnualSalary(50_000_000)

    expect(updateAnnualSalary).toHaveBeenCalledWith(50_000_000)
    expect(getTaxSettlement).toHaveBeenCalledTimes(1)
    expect(store.taxSettlement.annualSalary).toBe(50_000_000)
    expect(store.annualSalaryLookupStatus).toBe(ANNUAL_SALARY_LOOKUP_STATUS.AVAILABLE)
    expect(store.isAnnualSalarySaving).toBe(false)
    expect(store.annualSalaryError).toBeNull()
  })

  it("keeps the fallback form available when manual salary saving fails", async () => {
    const error = new Error("invalid salary")
    error.response = {
      data: {
        error: {
          code: "PROFILE_001",
          message: "invalid salary",
        },
      },
    }
    updateAnnualSalary.mockRejectedValue(error)
    const store = useReportStore()
    store.setAnnualSalaryLookupStatus("UNAVAILABLE")

    await expect(store.saveAnnualSalary(0)).rejects.toBe(error)

    expect(store.annualSalaryLookupStatus).toBe(ANNUAL_SALARY_LOOKUP_STATUS.UNAVAILABLE)
    expect(store.isAnnualSalarySaving).toBe(false)
    expect(store.annualSalaryError).toBe("invalid salary")
    expect(getTaxSettlement).not.toHaveBeenCalled()
  })

  it("reports a refetch failure after the salary was saved", async () => {
    const error = new Error("report unavailable")
    error.response = {
      data: {
        error: {
          code: "COMMON_001",
          message: "report unavailable",
        },
      },
    }
    updateAnnualSalary.mockResolvedValue({
      data: { annualSalary: 50_000_000 },
    })
    getTaxSettlement.mockRejectedValue(error)
    const store = useReportStore()
    store.setAnnualSalaryLookupStatus("UNAVAILABLE")

    await expect(store.saveAnnualSalary(50_000_000)).rejects.toBe(error)

    expect(updateAnnualSalary).toHaveBeenCalledWith(50_000_000)
    expect(store.annualSalaryLookupStatus).toBe(ANNUAL_SALARY_LOOKUP_STATUS.ERROR)
    expect(store.isAnnualSalarySaving).toBe(false)
    expect(store.annualSalaryError).toBe("report unavailable")
  })

  it("accepts the lookup status returned by asset connection", () => {
    const store = useReportStore()

    expect(store.setAnnualSalaryLookupStatus("UNAVAILABLE")).toBe(
      ANNUAL_SALARY_LOOKUP_STATUS.UNAVAILABLE,
    )
    expect(store.setAnnualSalaryLookupStatus("ERROR")).toBe(ANNUAL_SALARY_LOOKUP_STATUS.ERROR)
    expect(store.setAnnualSalaryLookupStatus("unknown")).toBe(ANNUAL_SALARY_LOOKUP_STATUS.ERROR)
  })

  it("marks the salary unavailable for PROFILE_004", async () => {
    const error = new Error("salary unavailable")
    error.response = {
      data: {
        error: {
          code: "PROFILE_004",
          message: "salary unavailable",
        },
      },
    }
    getTaxSettlement.mockRejectedValue(error)
    const store = useReportStore()

    await expect(store.fetchTaxSettlement()).rejects.toBe(error)

    expect(store.annualSalaryLookupStatus).toBe(ANNUAL_SALARY_LOOKUP_STATUS.UNAVAILABLE)
    expect(store.taxSettlementErrorCode).toBe("PROFILE_004")
  })

  it("keeps general API failures separate from salary fallback", async () => {
    const error = new Error("server unavailable")
    error.response = {
      data: {
        error: {
          code: "COMMON_001",
          message: "server unavailable",
        },
      },
    }
    getTaxSettlement.mockRejectedValue(error)
    const store = useReportStore()

    await expect(store.fetchTaxSettlement()).rejects.toBe(error)

    expect(store.annualSalaryLookupStatus).toBe(ANNUAL_SALARY_LOOKUP_STATUS.ERROR)
    expect(store.taxSettlementErrorCode).toBe("COMMON_001")
  })
})
