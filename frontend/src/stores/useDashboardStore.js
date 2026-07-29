import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { getAssets, getBudget, getExpenses } from "@/api/dashboardApi";

const CHART_COLORS = [
  "#0D6EFD", // Bootstrap primary
  "#20C997", // teal
  "#FFC107", // warning
  "#DC3545", // danger
  "#6F42C1", // purple
  "#0DCAF0", // info
  "#FD7E14", // orange
  "#6C757D", // secondary
];

const EMPTY_DOUGHNUT_DATA = {
  labels: [],
  datasets: [
    {
      data: [],
      backgroundColor: [],
      borderColor: "#FFFFFF",
      borderWidth: 2,
    },
  ],
};

const toDoughnutChartData = (breakdown = []) => {
  const safeBreakdown = Array.isArray(breakdown) ? breakdown : [];

  return {
    labels: safeBreakdown.map(
      (item) => item.categoryName ?? item.category ?? item.name ?? "기타",
    ),
    datasets: [
      {
        data: safeBreakdown.map((item) =>
          Number(item.amount ?? item.expenseAmount ?? item.value ?? 0),
        ),
        backgroundColor: safeBreakdown.map(
          (_, index) => CHART_COLORS[index % CHART_COLORS.length],
        ),
        borderColor: "#FFFFFF",
        borderWidth: 2,
      },
    ],
  };
};

export const useDashboardStore = defineStore("dashboard", () => {
  const isLoading = ref(false);
  const assets = ref(null);
  const budget = ref(null);
  const expenses = ref(null);
  const error = ref("");

  const assetChartData = computed(() => {
    const breakdown = assets.value?.assetCategoryBreakdown;

    return breakdown ? toDoughnutChartData(breakdown) : EMPTY_DOUGHNUT_DATA;
  });

  const expenseChartData = computed(() => {
    const breakdown = expenses.value?.expenseCategoryBreakdown;

    return breakdown ? toDoughnutChartData(breakdown) : EMPTY_DOUGHNUT_DATA;
  });

  const fetchDashboardData = async () => {
    isLoading.value = true;
    error.value = "";

    try {
      const [assetsResponse, budgetResponse, expensesResponse] = await Promise.all([
        getAssets(),
        getBudget(),
        getExpenses(),
      ]);

      assets.value = assetsResponse.data.data;
      budget.value = budgetResponse.data.data;
      expenses.value = expensesResponse.data.data;
    } catch (caughtError) {
      const errorMessage =
        caughtError.response?.data?.error?.message ??
        caughtError.response?.data?.message ??
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
    fetchDashboardData,
  };
});
