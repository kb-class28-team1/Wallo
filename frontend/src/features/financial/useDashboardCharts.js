import { computed } from "vue";
import { getExpenseCategoryMeta } from "@/features/financial/financialCategories";

const ASSET_TREND_LINE_COLOR = "#4f8fe8";
const ASSET_TREND_AREA_COLOR = "#eaf4ff";

const createAssetTrendChartData = (assetTrend = []) => ({
  labels: assetTrend.map((item) => item.month),
  datasets: [
    {
      label: "총 자산",
      data: assetTrend.map((item) => item.amount),
      borderColor: ASSET_TREND_LINE_COLOR,
      backgroundColor: ASSET_TREND_AREA_COLOR,
      pointBackgroundColor: ASSET_TREND_LINE_COLOR,
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
