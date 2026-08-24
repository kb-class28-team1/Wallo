import { computed, ref } from "vue"
import { defineStore } from "pinia"
import { getCategoryBudgets, putCategoryBudgets } from "@/api/assetApi"
import { useFinancialInvalidationStore } from "@/stores/financialInvalidationStore"
import { getApiErrorMessage } from "@/utils/apiError"

const CATEGORY_BUDGET_STALE_TIME = 60 * 1000

const normalizeCategorySummary = (data) => ({
  targetMonth: data?.targetMonth ?? "",
  totalAmount: Number(data?.totalAmount) || 0,
  allocatedAmount: Number(data?.allocatedAmount) || 0,
  unallocatedAmount: Number(data?.unallocatedAmount) || 0,
  spentAmount: Number(data?.spentAmount) || 0,
  remainingAmount: Number(data?.remainingAmount) || 0,
  usageRate:
    data?.usageRate === null || data?.usageRate === undefined ? null : Number(data.usageRate),
  overBudget: Boolean(data?.overBudget),
  categories: (data?.categories ?? []).map((category) => ({
    category: category.category,
    budgetAmount: Number(category.budgetAmount) || 0,
    spentAmount: Number(category.spentAmount) || 0,
    remainingAmount: Number(category.remainingAmount) || 0,
    usageRate:
      category.usageRate === null || category.usageRate === undefined
        ? null
        : Number(category.usageRate),
    overBudget: Boolean(category.overBudget),
  })),
})

export const useBudgetStore = defineStore("budget", () => {
  const invalidationStore = useFinancialInvalidationStore()
  const categorySummary = ref(null)
  const initialLoading = ref(false)
  const refreshing = ref(false)
  const isLoading = computed(() => initialLoading.value || refreshing.value)
  const isSaving = ref(false)
  const error = ref(null)
  const lastFetchedAt = ref(0)
  const lastFetchedRevision = ref(0)
  const lastFetchedMonth = ref(null)
  const inFlightByMonth = new Map()
  const inFlightRevisionByMonth = new Map()
  let requestSequence = 0

  const hasBudget = computed(() => Number(categorySummary.value?.totalAmount ?? 0) > 0)

  const fetchCategoryBudgets = (
    targetMonth,
    { notifyError = true, force = false, staleTime = CATEGORY_BUDGET_STALE_TIME } = {},
  ) => {
    const requestMonth = targetMonth ?? new Date().toISOString().slice(0, 7)
    const currentRevision = invalidationStore.revision
    const inFlight = inFlightByMonth.get(requestMonth)

    if (inFlight && inFlightRevisionByMonth.get(requestMonth) === currentRevision) {
      return inFlight
    }

    const isFresh =
      lastFetchedMonth.value === requestMonth &&
      lastFetchedRevision.value === currentRevision &&
      lastFetchedAt.value > 0 &&
      Date.now() - lastFetchedAt.value < staleTime

    if (!force && isFresh) {
      return Promise.resolve(categorySummary.value)
    }

    const currentRequest = ++requestSequence
    const isInitialLoad = lastFetchedAt.value === 0 && categorySummary.value === null
    initialLoading.value = isInitialLoad
    refreshing.value = !isInitialLoad
    error.value = null
    const requestRevision = currentRevision

    let request
    request = (async () => {
      try {
        const response = await getCategoryBudgets(requestMonth)
        if (currentRequest !== requestSequence || requestRevision !== invalidationStore.revision) {
          return categorySummary.value
        }

        if (!response?.success || !response?.data) {
          throw new Error(response?.error?.message || "카테고리별 예산 응답이 올바르지 않습니다.")
        }

        categorySummary.value = normalizeCategorySummary(response.data)
        lastFetchedMonth.value = requestMonth
        lastFetchedAt.value = Date.now()
        lastFetchedRevision.value = requestRevision
        return categorySummary.value
      } catch (caughtError) {
        if (currentRequest !== requestSequence || requestRevision !== invalidationStore.revision) {
          return categorySummary.value
        }

        const message = getApiErrorMessage(
          caughtError,
          "카테고리별 예산 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
        )
        if (isInitialLoad) {
          categorySummary.value = null
          lastFetchedMonth.value = null
          lastFetchedAt.value = 0
        }
        error.value = message
        if (notifyError) alert(message)
        throw caughtError
      } finally {
        if (currentRequest === requestSequence) {
          initialLoading.value = false
          refreshing.value = false
        }
        if (inFlightByMonth.get(requestMonth) === request) {
          inFlightByMonth.delete(requestMonth)
          inFlightRevisionByMonth.delete(requestMonth)
        }
      }
    })()

    inFlightByMonth.set(requestMonth, request)
    inFlightRevisionByMonth.set(requestMonth, requestRevision)
    return request
  }

  const saveCategoryBudgets = async (request, { notifyError = true, forceRefresh = true } = {}) => {
    isSaving.value = true
    error.value = null

    try {
      const response = await putCategoryBudgets(request)
      if (!response?.success || !response?.data) {
        throw new Error(response?.error?.message || "카테고리별 예산 응답이 올바르지 않습니다.")
      }

      invalidationStore.markChanged()
      categorySummary.value = normalizeCategorySummary(response.data)
      const savedTargetMonth = request.targetMonth ?? categorySummary.value.targetMonth ?? null
      lastFetchedMonth.value = savedTargetMonth
      lastFetchedAt.value = 0

      if (forceRefresh) {
        try {
          await fetchCategoryBudgets(savedTargetMonth, {
            notifyError: false,
            force: true,
          })
        } catch {
          // 저장 응답은 유지하고, 다음 조회에서 다시 최신 데이터를 요청합니다.
        }
      } else {
        lastFetchedAt.value = Date.now()
        lastFetchedRevision.value = invalidationStore.revision
      }

      return categorySummary.value
    } catch (caughtError) {
      const message = getApiErrorMessage(
        caughtError,
        "카테고리별 예산을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      )
      error.value = message
      if (notifyError) alert(message)
      throw caughtError
    } finally {
      isSaving.value = false
    }
  }

  return {
    categorySummary,
    hasBudget,
    isLoading,
    initialLoading,
    refreshing,
    lastFetchedAt,
    isSaving,
    error,
    fetchCategoryBudgets,
    saveCategoryBudgets,
  }
})
