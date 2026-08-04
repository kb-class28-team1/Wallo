import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { getAssets, getBudgets, getExpenses, putBudget } from "@/api/assetApi";
import { getExpenseCategoryLabel } from "@/constants/financialCategories";

const CHART_COLORS = [
  "#0D6EFD",
  "#20C997",
  "#FFC107",
  "#DC3545",
  "#6F42C1",
  "#0DCAF0",
  "#FD7E14",
  "#6C757D",
];

const createDoughnutChartData = (breakdown = [], labelResolver = (value) => value) => ({
  labels: breakdown.map((item) => labelResolver(item.category)),
  datasets: [
    {
      data: breakdown.map((item) => item.amount),
      backgroundColor: breakdown.map(
        (_, index) => CHART_COLORS[index % CHART_COLORS.length],
      ),
      borderColor: "#FFFFFF",
      borderWidth: 2,
    },
  ],
});

const createAssetTrendChartData = (assetTrend = []) => ({
  labels: assetTrend.map((item) => item.month),
  datasets: [
    {
      label: "총 자산",
      data: assetTrend.map((item) => item.amount),
      borderColor: "#8170FF",
      backgroundColor: "rgba(129, 112, 255, 0.14)",
      pointBackgroundColor: "#8170FF",
      pointBorderColor: "#FFFFFF",
      pointBorderWidth: 2,
      pointRadius: 4,
      pointHoverRadius: 6,
      borderWidth: 3,
      tension: 0.35,
      fill: true,
    },
  ],
});

const getErrorMessage = (caughtError) =>
  caughtError.response?.data?.error?.message ??
  caughtError.message ??
  "대시보드 데이터를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";

export const useDashboardStore = defineStore("dashboard", () => {
  const isLoading = ref(false);
  const assets = ref(null);
  const budget = ref(null);
  const expenses = ref(null);
  const error = ref(null);

  const assetChartData = computed(() =>
    createDoughnutChartData(assets.value?.assetCategoryBreakdown ?? []),
  );
  const expenseChartData = computed(() =>
    createDoughnutChartData(
      expenses.value?.expenseCategoryBreakdown ?? [],
      getExpenseCategoryLabel,
    ),
  );
  const assetTrendChartData = computed(() =>
    createAssetTrendChartData(assets.value?.assetTrend ?? []),
  );

  const fetchDashboardSummary = async () => {
    isLoading.value = true;
    error.value = null;

    try {
      const [assetsResponse, budgetResponse, expensesResponse] = await Promise.all([
        getAssets(),
        getBudgets(),
        getExpenses(),
      ]);

      assets.value = assetsResponse.data;
      budget.value = budgetResponse.data;
      expenses.value = expensesResponse.data;
    } catch (caughtError) {
      const errorMessage = getErrorMessage(caughtError);

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
      const errorMessage = getErrorMessage(caughtError);

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
    assetChartData,
    expenseChartData,
    assetTrendChartData,
    fetchDashboardSummary,
    updateBudgetTotal,
  };
});
