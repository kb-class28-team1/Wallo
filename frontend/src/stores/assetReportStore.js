import { defineStore } from "pinia"
import { getInsight, getTaxSettlement, updateAnnualSalary } from "@/api/assetApi"
import { useFinancialInvalidationStore } from "@/stores/financialInvalidationStore"
import { getApiErrorCode, getApiErrorMessage } from "@/utils/apiError"
import { getAppYear } from "@/utils/appDate"

const TAX_SETTLEMENT_STALE_TIME = 5 * 60 * 1000
const requestStateByStore = new WeakMap()

const getRequestState = (store) => {
  if (!requestStateByStore.has(store)) {
    requestStateByStore.set(store, {
      insight: null,
      insightRevision: null,
      taxSettlement: new Map(),
      taxSettlementRevision: new Map(),
    })
  }

  return requestStateByStore.get(store)
}

export const ANNUAL_SALARY_LOOKUP_STATUS = Object.freeze({
  IDLE: "idle",
  LOADING: "loading",
  AVAILABLE: "available",
  UNAVAILABLE: "unavailable",
  ERROR: "error",
})

export const useReportStore = defineStore("report", {
  state: () => ({
    insight: null,
    initialInsightLoading: false,
    refreshingInsight: false,
    isInsightLoading: false,
    insightError: null,
    taxSettlementYear: null,
    taxSettlement: null,
    initialTaxSettlementLoading: false,
    refreshingTaxSettlement: false,
    isTaxSettlementLoading: false,
    taxSettlementError: null,
    taxSettlementErrorCode: null,
    lastFetchedAt: {
      insight: 0,
      taxSettlement: 0,
    },
    lastFetchedRevision: {
      insight: 0,
      taxSettlement: 0,
    },
    annualSalaryLookupStatus: ANNUAL_SALARY_LOOKUP_STATUS.IDLE,
    isAnnualSalarySaving: false,
    annualSalaryError: null,
  }),

  getters: {
    initialLoading: (state) => state.initialInsightLoading || state.initialTaxSettlementLoading,
    refreshing: (state) => state.refreshingInsight || state.refreshingTaxSettlement,
  },

  actions: {
    setAnnualSalaryLookupStatus(status) {
      const normalizedStatus = String(status ?? "").toLowerCase()
      if (Object.values(ANNUAL_SALARY_LOOKUP_STATUS).includes(normalizedStatus)) {
        this.annualSalaryLookupStatus = normalizedStatus
      }

      return this.annualSalaryLookupStatus
    },

    fetchInsight({ notifyError = true } = {}) {
      const invalidationStore = useFinancialInvalidationStore()
      const currentRevision = invalidationStore.revision
      const requestState = getRequestState(this)

      if (requestState.insight && requestState.insightRevision === currentRevision) {
        return requestState.insight
      }

      const isInitialLoad = this.lastFetchedAt.insight === 0 && this.insight === null
      this.initialInsightLoading = isInitialLoad
      this.refreshingInsight = !isInitialLoad
      this.isInsightLoading = true
      this.insightError = null
      const requestRevision = currentRevision

      let request
      request = (async () => {
        try {
          const response = await getInsight()
          if (requestRevision !== invalidationStore.revision) {
            return this.insight
          }

          this.insight = response?.data ?? null
          this.lastFetchedAt.insight = Date.now()
          this.lastFetchedRevision.insight = requestRevision

          return this.insight
        } catch (error) {
          if (requestRevision !== invalidationStore.revision) {
            return this.insight
          }

          const errorMessage = getApiErrorMessage(
            error,
            "소비 리포트를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
          )

          if (isInitialLoad) {
            this.insight = null
            this.lastFetchedAt.insight = 0
          }
          this.insightError = errorMessage
          if (notifyError) {
            alert(errorMessage)
          }

          throw error
        } finally {
          if (requestState.insight === request) {
            this.initialInsightLoading = false
            this.refreshingInsight = false
            this.isInsightLoading = false
            requestState.insight = null
            requestState.insightRevision = null
          }
        }
      })()

      requestState.insight = request
      requestState.insightRevision = requestRevision
      return request
    },

    fetchTaxSettlement(
      year,
      { notifyError = false, force = false, staleTime = TAX_SETTLEMENT_STALE_TIME } = {},
    ) {
      const invalidationStore = useFinancialInvalidationStore()
      const currentRevision = invalidationStore.revision
      const requestState = getRequestState(this)
      const normalizedYear = year ?? getAppYear()
      const inFlight = requestState.taxSettlement.get(normalizedYear)

      if (inFlight && requestState.taxSettlementRevision.get(normalizedYear) === currentRevision) {
        return inFlight
      }

      const isFresh =
        this.taxSettlementYear === normalizedYear &&
        this.lastFetchedRevision.taxSettlement === currentRevision &&
        this.lastFetchedAt.taxSettlement > 0 &&
        Date.now() - this.lastFetchedAt.taxSettlement < staleTime

      if (!force && isFresh) {
        return Promise.resolve(this.taxSettlement)
      }

      const isInitialLoad = this.lastFetchedAt.taxSettlement === 0 && this.taxSettlement === null
      this.initialTaxSettlementLoading = isInitialLoad
      this.refreshingTaxSettlement = !isInitialLoad
      this.isTaxSettlementLoading = true
      this.taxSettlementError = null
      this.taxSettlementErrorCode = null
      this.annualSalaryError = null
      this.annualSalaryLookupStatus = ANNUAL_SALARY_LOOKUP_STATUS.LOADING
      const requestRevision = currentRevision

      let request
      request = (async () => {
        try {
          const response = await getTaxSettlement(year)
          if (requestRevision !== invalidationStore.revision) {
            return this.taxSettlement
          }

          this.taxSettlement = response?.data ?? null
          this.taxSettlementYear = normalizedYear
          this.lastFetchedAt.taxSettlement = Date.now()
          this.lastFetchedRevision.taxSettlement = requestRevision
          this.annualSalaryLookupStatus =
            Number(this.taxSettlement?.annualSalary) > 0
              ? ANNUAL_SALARY_LOOKUP_STATUS.AVAILABLE
              : ANNUAL_SALARY_LOOKUP_STATUS.UNAVAILABLE

          return this.taxSettlement
        } catch (error) {
          if (requestRevision !== invalidationStore.revision) {
            return this.taxSettlement
          }

          if (isInitialLoad) {
            this.taxSettlement = null
            this.taxSettlementYear = null
            this.lastFetchedAt.taxSettlement = 0
          }

          const errorCode = getApiErrorCode(error)
          this.taxSettlementErrorCode = errorCode
          if (errorCode === "PROFILE_004") {
            this.annualSalaryLookupStatus = ANNUAL_SALARY_LOOKUP_STATUS.UNAVAILABLE
            this.taxSettlementError =
              "세전 연봉 자동 조회 결과가 없습니다. 금융기관 연결을 다시 진행해 주세요."
          } else if (errorCode !== "REPORT_002") {
            this.annualSalaryLookupStatus = ANNUAL_SALARY_LOOKUP_STATUS.ERROR
            this.taxSettlementError = getApiErrorMessage(
              error,
              "소득공제 달성률을 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
            )
          } else {
            this.annualSalaryLookupStatus = ANNUAL_SALARY_LOOKUP_STATUS.ERROR
          }

          if (notifyError && this.taxSettlementError) {
            alert(this.taxSettlementError)
          }

          throw error
        } finally {
          if (requestState.taxSettlement.get(normalizedYear) === request) {
            this.initialTaxSettlementLoading = false
            this.refreshingTaxSettlement = false
            this.isTaxSettlementLoading = false
            requestState.taxSettlement.delete(normalizedYear)
            requestState.taxSettlementRevision.delete(normalizedYear)
          }
        }
      })()

      requestState.taxSettlement.set(normalizedYear, request)
      requestState.taxSettlementRevision.set(normalizedYear, requestRevision)
      return request
    },

    async saveAnnualSalary(annualSalary) {
      this.isAnnualSalarySaving = true
      this.annualSalaryError = null

      try {
        const response = await updateAnnualSalary(annualSalary)
        const invalidationStore = useFinancialInvalidationStore()
        invalidationStore.markChanged()
        this.lastFetchedAt.taxSettlement = 0
        await this.fetchTaxSettlement(undefined, { force: true })

        return response?.data ?? response ?? null
      } catch (error) {
        this.annualSalaryError = getApiErrorMessage(
          error,
          "연봉을 저장하는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
        )
        throw error
      } finally {
        this.isAnnualSalarySaving = false
      }
    },
  },
})
