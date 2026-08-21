import { computed } from "vue";
import {
  getExpenseCategoryMeta,
  normalizeExpenseCategory,
} from "@/features/financial/financialCategories";

const ASSET_TREND_LINE_COLOR = "#4f8fe8";
const ASSET_TREND_AREA_COLOR = "#eaf4ff";
const EXPENSE_TOP_CATEGORY_LIMIT = 5;
const EXPENSE_OTHER_CATEGORY = "ETC";

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

const createExpenseChartData = (breakdown = []) => {
  const sortedBreakdown = [...breakdown].sort(
    (first, second) => Number(second.amount) - Number(first.amount),
  );
  const chartCategories = sortedBreakdown
    .slice(0, EXPENSE_TOP_CATEGORY_LIMIT)
    .map((item) => ({
      category: item.category,
      amount: Number(item.amount) || 0,
    }));
  const otherAmount = sortedBreakdown
    .slice(EXPENSE_TOP_CATEGORY_LIMIT)
    .reduce((total, item) => total + (Number(item.amount) || 0), 0);

  if (otherAmount > 0) {
    const existingOtherCategory = chartCategories.find(
      (item) => normalizeExpenseCategory(item.category) === EXPENSE_OTHER_CATEGORY,
    );

    if (existingOtherCategory) {
      existingOtherCategory.amount += otherAmount;
    } else {
      chartCategories.push({ category: EXPENSE_OTHER_CATEGORY, amount: otherAmount });
    }
  }

  return {
    labels: chartCategories.map((item) => getExpenseCategoryMeta(item.category).label),
    datasets: [
      {
        data: chartCategories.map((item) => item.amount),
        backgroundColor: chartCategories.map(
          (item) => getExpenseCategoryMeta(item.category).color,
        ),
        borderColor: "#FFFFFF",
        borderWidth: 2,
      },
    ],
  };
};

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
