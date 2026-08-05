import { computed } from "vue";
import { getExpenseCategoryMeta } from "@/features/financial/financialCategories";

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

const createExpenseChartData = (breakdown = []) => ({
  labels: breakdown.map((item) => getExpenseCategoryMeta(item.category).label),
  datasets: [
    {
      data: breakdown.map((item) => item.amount),
      backgroundColor: breakdown.map(
        (item) => getExpenseCategoryMeta(item.category).color,
      ),
      borderColor: "#FFFFFF",
      borderWidth: 2,
    },
  ],
});

export const useDashboardCharts = (assets, expenses) => {
  const assetTrendChartData = computed(() =>
    createAssetTrendChartData(assets.value?.assetTrend ?? []),
  );
  const expenseChartData = computed(() =>
    createExpenseChartData(expenses.value?.expenseCategoryBreakdown ?? []),
  );

  return {
    assetTrendChartData,
    expenseChartData,
  };
};
