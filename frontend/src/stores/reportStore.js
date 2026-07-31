import { defineStore } from "pinia";
import {
  getInsight,
  getTaxSettlement,
  updateAnnualSalary,
} from "@/api/reportApi";

const getErrorMessage = (error, fallbackMessage) =>
  error.response?.data?.error?.message ||
  error.response?.data?.message ||
  (error.response?.status === 401
    ? "로그인이 만료되었습니다. 다시 로그인해 주세요."
    : fallbackMessage);

const getErrorCode = (error) =>
  error.response?.data?.error?.code ||
  error.response?.data?.code ||
  null;

export const useReportStore = defineStore("report", {
  state: () => ({
    insight: null,
    isInsightLoading: false,
    insightError: null,
    taxSettlement: null,
    isTaxSettlementLoading: false,
    taxSettlementError: null,
    isAnnualSalaryRequired: false,
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
        const errorMessage = getErrorMessage(
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

    async retryInsight() {
      return this.fetchInsight();
    },

    async fetchTaxSettlement(year) {
      this.isTaxSettlementLoading = true;
      this.taxSettlementError = null;
      this.isAnnualSalaryRequired = false;

      try {
        const response = await getTaxSettlement(year);
        this.taxSettlement = response?.data ?? null;

        return this.taxSettlement;
      } catch (error) {
        this.taxSettlement = null;

        if (getErrorCode(error) === "REPORT_002") {
          this.isAnnualSalaryRequired = true;
        } else {
          this.taxSettlementError = getErrorMessage(
            error,
            "소득공제 달성률을 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
          );
        }

        throw error;
      } finally {
        this.isTaxSettlementLoading = false;
      }
    },

    async retryTaxSettlement() {
      return this.fetchTaxSettlement();
    },

    async saveAnnualSalary(annualSalary) {
      this.isAnnualSalarySaving = true;
      this.annualSalaryError = null;

      try {
        const response = await updateAnnualSalary(annualSalary);
        this.isAnnualSalaryRequired = false;

        try {
          await this.fetchTaxSettlement();
        } catch {
          // 조회 오류는 taxSettlementError에서 별도로 안내합니다.
        }

        return response?.data ?? null;
      } catch (error) {
        this.annualSalaryError = getErrorMessage(
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
