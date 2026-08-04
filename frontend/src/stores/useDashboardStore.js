import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { getBudgets, getExpenses, putBudget } from "@/api/assetApi";
import { useAssetStore } from "@/stores/assetStore";
import { getApiErrorMessage } from "@/commonUtils/apiError";

export const useDashboardStore = defineStore("dashboard", () => {
  const assetStore = useAssetStore();
  const isLoading = ref(false);
  const assets = computed(() => assetStore.assets);
  const budget = ref(null);
  const expenses = ref(null);
  const error = ref(null);

  const fetchDashboardSummary = async () => {
    isLoading.value = true;
    error.value = null;

    try {
      const [, budgetResponse, expensesResponse] = await Promise.all([
        assetStore.fetchAssets({ notifyError: false }),
        getBudgets(),
        getExpenses(),
      ]);

      budget.value = budgetResponse.data;
      expenses.value = expensesResponse.data;
    } catch (caughtError) {
      const errorMessage = getApiErrorMessage(
        caughtError,
        "대시보드 데이터를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
      );

      error.value = errorMessage;
      alert(errorMessage);
    } finally {
      isLoading.value = false;
    }
  };

  const updateBudgetTotal = async (totalAmount) => {
    const targetMonth = budget.value?.targetMonth ?? new Date().toISOString().slice(0, 7);

    try {
      const response = await putBudget(targetMonth, Number(totalAmount));

      budget.value = response.data;
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
    assets,
    budget,
    expenses,
    error,
    fetchDashboardSummary,
    updateBudgetTotal,
  };
});
