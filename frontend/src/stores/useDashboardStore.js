import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { getBudgets, getExpenses, putBudget } from "@/api/assetApi";
import { useAssetStore } from "@/stores/assetStore";
import { getApiErrorMessage } from "@/commonUtils/apiError";

const DASHBOARD_STALE_TIME = 30 * 1000;

export const useDashboardStore = defineStore("dashboard", () => {
  const assetStore = useAssetStore();
  const initialLoading = ref(false);
  const refreshing = ref(false);
  const isLoading = computed(() => initialLoading.value || refreshing.value);
  const assets = computed(() => assetStore.assets);
  const budget = ref(null);
  const expenses = ref(null);
  const error = ref(null);
  const lastFetchedAt = ref(0);
  let inFlight = null;

  const hasDashboardData = computed(() => (
    assets.value !== null &&
    budget.value !== null &&
    expenses.value !== null
  ));

  const getDashboardSummary = () => ({
    assets: assets.value,
    budget: budget.value,
    expenses: expenses.value,
  });

  const fetchDashboardSummary = ({
    force = false,
    notifyError = true,
    staleTime = DASHBOARD_STALE_TIME,
  } = {}) => {
    if (inFlight) {
      return inFlight;
    }

    const isFresh = (
      hasDashboardData.value &&
      lastFetchedAt.value > 0 &&
      Date.now() - lastFetchedAt.value < staleTime
    );

    if (!force && isFresh) {
      return Promise.resolve(getDashboardSummary());
    }

    const isInitialLoad = !hasDashboardData.value;
    initialLoading.value = isInitialLoad;
    refreshing.value = !isInitialLoad;
    error.value = null;

    let request;
    request = (async () => {
      try {
        const [, budgetResponse, expensesResponse] = await Promise.all([
          assetStore.fetchAssets({ notifyError: false }),
          getBudgets(),
          getExpenses(),
        ]);

        budget.value = budgetResponse.data;
        expenses.value = expensesResponse.data;
        lastFetchedAt.value = Date.now();

        return getDashboardSummary();
      } catch (caughtError) {
        const errorMessage = getApiErrorMessage(
          caughtError,
          "대시보드 데이터를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
        );

        error.value = errorMessage;
        if (notifyError) {
          alert(errorMessage);
        }

        return null;
      } finally {
        initialLoading.value = false;
        refreshing.value = false;
        if (inFlight === request) {
          inFlight = null;
        }
      }
    })();

    inFlight = request;
    return request;
  };

  const updateBudgetTotal = async (totalAmount, { forceRefresh = true } = {}) => {
    const targetMonth = budget.value?.targetMonth ?? new Date().toISOString().slice(0, 7);

    try {
      const response = await putBudget(targetMonth, Number(totalAmount));

      budget.value = response.data;
      lastFetchedAt.value = 0;

      if (forceRefresh) {
        await fetchDashboardSummary({ force: true, notifyError: false });
      }

      return budget.value;
    } catch (caughtError) {
      const errorMessage = getApiErrorMessage(
        caughtError,
        "예산을 저장하는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
      );

      error.value = errorMessage;
      alert(errorMessage);
      throw caughtError;
    }
  };

  return {
    isLoading,
    initialLoading,
    refreshing,
    assets,
    budget,
    expenses,
    error,
    lastFetchedAt,
    fetchDashboardSummary,
    updateBudgetTotal,
  };
});
