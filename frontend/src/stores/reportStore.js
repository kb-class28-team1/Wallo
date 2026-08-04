import { defineStore } from "pinia";
import {
  getInsight,
  getTaxSettlement,
  updateAnnualSalary,
} from "@/api/assetApi";
import { getApiErrorCode, getApiErrorMessage } from "@/utils/apiError";

export const useReportStore = defineStore("report", {
  state: () => ({
    insight: null,
    isInsightLoading: false,
    insightError: null,
    taxSettlement: null,
    isTaxSettlementLoading: false,
    taxSettlementError: null,
    isAnnualSalarySaving: false,
    annualSalaryError: null,
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

        if (getApiErrorCode(error) !== "REPORT_002") {
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

    async saveAnnualSalary(annualSalary) {
      this.isAnnualSalarySaving = true;
      this.annualSalaryError = null;

      try {
        const response = await updateAnnualSalary(annualSalary);

        try {
          await this.fetchTaxSettlement();
        } catch {
          // 조회 오류는 taxSettlementError에서 별도로 안내합니다.
        }

        return response?.data ?? null;
      } catch (error) {
        this.annualSalaryError = getApiErrorMessage(
          error,
          "연봉을 저장하는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
        );

        throw error;
      } finally {
        this.isAnnualSalarySaving = false;
      }
    },
  },
});
