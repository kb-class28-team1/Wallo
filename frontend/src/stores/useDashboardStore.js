import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { getAssets, getBudgets, getExpenses } from "@/api/dashboardApi";

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

const createDoughnutChartData = (breakdown = []) => ({
  labels: breakdown.map((item) => item.category),
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
    createDoughnutChartData(expenses.value?.expenseCategoryBreakdown ?? []),
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

      assets.value = assetsResponse.data.data;
      budget.value = budgetResponse.data.data;
      expenses.value = expensesResponse.data.data;
    } catch (caughtError) {
      const errorMessage =
        caughtError.response?.data?.error?.message ??
        caughtError.message ??
        "대시보드 데이터를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";

      error.value = errorMessage;
      alert(errorMessage);
    } finally {
      isLoading.value = false;
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
    fetchDashboardSummary,
  };
});
