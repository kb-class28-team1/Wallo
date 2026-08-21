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
});
