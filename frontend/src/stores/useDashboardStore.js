import { computed, ref } from "vue"
import { defineStore } from "pinia"
import { getBudgets, getExpenses, putBudget } from "@/api/assetApi"
import { useAssetStore } from "@/stores/assetStore"
import { useFinancialInvalidationStore } from "@/stores/financialInvalidationStore"
import { getApiErrorMessage } from "@/utils/apiError"

const DASHBOARD_STALE_TIME = 30 * 1000

export const useDashboardStore = defineStore("dashboard", () => {
  const assetStore = useAssetStore()
  const invalidationStore = useFinancialInvalidationStore()
  const initialLoading = ref(false)
  const refreshing = ref(false)
  const assets = computed(() => assetStore.assets)
  const assetInitialLoading = computed(() => assetStore.initialLoading)
  const assetRefreshing = computed(() => assetStore.refreshing)
  const assetLoading = computed(
    () => assetInitialLoading.value || assetRefreshing.value,
  )
  const assetError = computed(() => assetStore.error)
  const hasAssetData = computed(() => assets.value !== null)
  const hasFetchedAssets = computed(() => assetStore.hasFetched)

  const budget = ref(null)
  const budgetInitialLoading = ref(false)
  const budgetRefreshing = ref(false)
  const budgetLoading = computed(
    () => budgetInitialLoading.value || budgetRefreshing.value,
  )
  const budgetError = ref(null)
  const hasFetchedBudget = ref(false)
  const hasBudgetData = computed(() => budget.value !== null)

  const expenses = ref(null)
  const expenseInitialLoading = ref(false)
  const expenseRefreshing = ref(false)
  const expenseLoading = computed(
    () => expenseInitialLoading.value || expenseRefreshing.value,
  )
  const expenseError = ref(null)
  const hasFetchedExpenses = ref(false)
  const hasExpenseData = computed(() => expenses.value !== null)

  const isLoading = computed(
    () =>
      initialLoading.value ||
      refreshing.value ||
      assetLoading.value ||
      budgetLoading.value ||
      expenseLoading.value,
  )
  const error = ref(null)
  const lastFetchedAt = ref(0)
  const lastFetchedRevision = ref(0)
  let inFlight = null
  let inFlightRevision = null

  const hasFetchedDashboardResources = computed(
    () =>
      hasFetchedAssets.value &&
      hasFetchedBudget.value &&
      hasFetchedExpenses.value,
  )
  const hasDashboardData = computed(
    () => hasAssetData.value || hasBudgetData.value || hasExpenseData.value,
  )

  const getDashboardSummary = () => ({
    assets: assets.value,
    budget: budget.value,
    expenses: expenses.value,
  })

  const normalizeDashboardResponse = (response, fallbackMessage) => {
    if (!response || response.success === false) {
      throw new Error(response?.error?.message || fallbackMessage)
    }

    return response.data ?? null
  }

  const loadDashboardResource = async ({
    requestRevision,
    load,
    setData,
    hasFetched,
    setInitialLoading,
    setRefreshing,
    setError,
    fallbackMessage,
  }) => {
    const isInitialLoad = !hasFetched.value
    setInitialLoading(isInitialLoad)
    setRefreshing(!isInitialLoad)
    setError(null)

    try {
      const response = await load()

      if (requestRevision !== invalidationStore.revision) {
        return null
      }

      const data = normalizeDashboardResponse(response, fallbackMessage)
      setData(data)
      hasFetched.value = true
      return data
    } catch (caughtError) {
      if (requestRevision !== invalidationStore.revision) {
        return null
      }

      const message = getApiErrorMessage(caughtError, fallbackMessage)
      if (isInitialLoad) {
        setData(null)
      }
      setError(message)
      throw caughtError
    } finally {
      if (requestRevision === invalidationStore.revision) {
        setInitialLoading(false)
        setRefreshing(false)
      }
    }
  }

  const fetchDashboardSummary = ({
    force = false,
    notifyError = true,
    staleTime = DASHBOARD_STALE_TIME,
  } = {}) => {
    const currentRevision = invalidationStore.revision

    if (inFlight && inFlightRevision === currentRevision) {
      return inFlight
    }

    const isFresh =
      hasFetchedDashboardResources.value &&
      lastFetchedAt.value > 0 &&
      lastFetchedRevision.value === currentRevision &&
      Date.now() - lastFetchedAt.value < staleTime

    if (!force && isFresh) {
      return Promise.resolve(getDashboardSummary())
    }

    const isInitialLoad = !hasFetchedDashboardResources.value
    initialLoading.value = isInitialLoad
    refreshing.value = !isInitialLoad
    error.value = null
    const requestRevision = currentRevision

    let request
    request = (async () => {
      const resourceRequests = [
        assetStore.fetchAssets({ notifyError: false }),
        loadDashboardResource({
          requestRevision,
          load: getBudgets,
          setData: (data) => {
            budget.value = data
          },
          hasFetched: hasFetchedBudget,
          setInitialLoading: (value) => {
            budgetInitialLoading.value = value
          },
          setRefreshing: (value) => {
            budgetRefreshing.value = value
          },
          setError: (value) => {
            budgetError.value = value
          },
          fallbackMessage: "예산 정보를 불러오지 못했습니다.",
        }),
        loadDashboardResource({
          requestRevision,
          load: getExpenses,
          setData: (data) => {
            expenses.value = data
          },
          hasFetched: hasFetchedExpenses,
          setInitialLoading: (value) => {
            expenseInitialLoading.value = value
          },
          setRefreshing: (value) => {
            expenseRefreshing.value = value
          },
          setError: (value) => {
            expenseError.value = value
          },
          fallbackMessage: "소비 정보를 불러오지 못했습니다.",
        }),
      ]
      const resourceResults = await Promise.allSettled(resourceRequests)

      if (requestRevision !== invalidationStore.revision) {
        return getDashboardSummary()
      }

      const resourceErrors = [
        assetError.value,
        budgetError.value,
        expenseError.value,
      ].filter(Boolean)

      if (resourceErrors.length > 0) {
        error.value = [...new Set(resourceErrors)].join("\n")
        lastFetchedAt.value = 0
        if (notifyError) {
          alert(error.value)
        }
      } else {
        lastFetchedAt.value = Date.now()
        lastFetchedRevision.value = requestRevision
      }

      return resourceResults.some((result) => result.status === "fulfilled")
        ? getDashboardSummary()
        : null
    })().finally(() => {
      if (inFlight === request) {
        initialLoading.value = false
        refreshing.value = false
        inFlight = null
        inFlightRevision = null
      }
    })

    inFlight = request
    inFlightRevision = requestRevision
    return request
  }

  const updateBudgetTotal = async (totalAmount, { forceRefresh = true } = {}) => {
    const targetMonth = budget.value?.targetMonth ?? new Date().toISOString().slice(0, 7)

    try {
      const response = await putBudget(targetMonth, Number(totalAmount))

      budget.value = response.data
      budgetError.value = null
      hasFetchedBudget.value = true
      lastFetchedAt.value = 0
      invalidationStore.markChanged()

      if (forceRefresh) {
        await fetchDashboardSummary({ force: true, notifyError: false })
      }

      return budget.value
    } catch (caughtError) {
      const errorMessage = getApiErrorMessage(
        caughtError,
        "예산을 저장하는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
      )

      budgetError.value = errorMessage
      error.value = errorMessage
      alert(errorMessage)
      throw caughtError
    }
  }

  return {
    isLoading,
    initialLoading,
    refreshing,
    assetLoading,
    assetInitialLoading,
    assetRefreshing,
    assetError,
    hasAssetData,
    hasFetchedAssets,
    assets,
    budgetLoading,
    budgetInitialLoading,
    budgetRefreshing,
    budgetError,
    hasBudgetData,
    hasFetchedBudget,
    budget,
    expenseLoading,
    expenseInitialLoading,
    expenseRefreshing,
    expenseError,
    hasExpenseData,
    hasFetchedExpenses,
    expenses,
    error,
    hasDashboardData,
    lastFetchedAt,
    fetchDashboardSummary,
    updateBudgetTotal,
  }
})
