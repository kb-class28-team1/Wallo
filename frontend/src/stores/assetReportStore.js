import { defineStore } from "pinia";
import { getInsight, getTaxSettlement } from "@/api/assetApi";
import { getApiErrorCode, getApiErrorMessage } from "@/commonUtils/apiError";

export const useReportStore = defineStore("report", {
  state: () => ({
    insight: null,
    isInsightLoading: false,
    insightError: null,
    taxSettlement: null,
    isTaxSettlementLoading: false,
    taxSettlementError: null,
  }),

  actions: {
    async fetchInsight() {
      this.isInsightLoading = true;
      this.insightError = null;

      try {
        const response = await getInsight();
        this.insight = response?.data ?? null;

        return this.insight;
      } catch (error) {
        const errorMessage = getApiErrorMessage(
          error,
          "소비 리포트를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
        );

        this.insight = null;
        this.insightError = errorMessage;
        alert(errorMessage);

        throw error;
      } finally {
        this.isInsightLoading = false;
      }
    },

    async fetchTaxSettlement(year) {
      this.isTaxSettlementLoading = true;
      this.taxSettlementError = null;

      try {
        const response = await getTaxSettlement(year);
        this.taxSettlement = response?.data ?? null;

        return this.taxSettlement;
      } catch (error) {
        this.taxSettlement = null;

        const errorCode = getApiErrorCode(error);
        if (errorCode === "PROFILE_004") {
          this.taxSettlementError =
            "세전 연봉 자동 조회 결과가 없습니다. 금융기관 연결을 다시 진행해 주세요.";
        } else if (errorCode !== "REPORT_002") {
          this.taxSettlementError = getApiErrorMessage(
            error,
            "소득공제 달성률을 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
          );
        }

        throw error;
      } finally {
        this.isTaxSettlementLoading = false;
      }
    },
  },
});
