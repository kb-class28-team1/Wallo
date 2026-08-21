import { ref } from "vue";
import { describe, expect, it } from "vitest";

import { useDashboardCharts } from "./useDashboardCharts";

describe("useDashboardCharts", () => {
  it("uses the shared blue and sky-blue palette for the asset trend", () => {
    const { assetTrendChartData } = useDashboardCharts(
      ref({
        assetTrend: [{ month: "7월", amount: 1_200_000 }],
      }),
      ref({ expenseCategoryBreakdown: [] }),
    );

    expect(assetTrendChartData.value.datasets[0]).toMatchObject({
      borderColor: "#4f8fe8",
      backgroundColor: "#eaf4ff",
      pointBackgroundColor: "#4f8fe8",
    });
  });

  it("limits the expense doughnut to the top five and groups the rest as gray other", () => {
    const { expenseChartData } = useDashboardCharts(
      ref({ assetTrend: [] }),
      ref({
        expenseCategoryBreakdown: [
          { category: "FOOD", amount: 100_000 },
          { category: "SHOPPING", amount: 90_000 },
          { category: "TRANSPORT", amount: 80_000 },
          { category: "CAFE", amount: 70_000 },
          { category: "DELIVERY", amount: 60_000 },
          { category: "HOUSING", amount: 50_000 },
          { category: "LIVING", amount: 40_000 },
        ],
      }),
    );

    expect(expenseChartData.value.labels).toEqual([
      "식비",
      "쇼핑",
      "교통/차량",
      "카페",
      "배달",
      "기타",
    ]);
    expect(expenseChartData.value.datasets[0].data).toEqual([
      100_000,
      90_000,
      80_000,
      70_000,
      60_000,
      90_000,
    ]);
    expect(expenseChartData.value.datasets[0].backgroundColor.at(-1)).toBe("#a0a6b5");
  });
});
