import { defineStore } from "pinia";
import { getInsight, getTaxSettlement } from "@/api/assetApi";
import { getApiErrorCode, getApiErrorMessage } from "@/commonUtils/apiError";

export const ANNUAL_SALARY_LOOKUP_STATUS = Object.freeze({
  IDLE: "idle",
  LOADING: "loading",
  AVAILABLE: "available",
  UNAVAILABLE: "unavailable",
  ERROR: "error",
});

export const useReportStore = defineStore("report", {
  state: () => ({
    insight: null,
    isInsightLoading: false,
    insightError: null,
    taxSettlement: null,
    isTaxSettlementLoading: false,
    taxSettlementError: null,
    taxSettlementErrorCode: null,
    annualSalaryLookupStatus: ANNUAL_SALARY_LOOKUP_STATUS.IDLE,
  }),

  actions: {
    setAnnualSalaryLookupStatus(status) {
      const normalizedStatus = String(status ?? "").toLowerCase();
      if (Object.values(ANNUAL_SALARY_LOOKUP_STATUS).includes(normalizedStatus)) {
        this.annualSalaryLookupStatus = normalizedStatus;
      }

      return this.annualSalaryLookupStatus;
    },

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
      this.taxSettlementErrorCode = null;
      this.annualSalaryLookupStatus = ANNUAL_SALARY_LOOKUP_STATUS.LOADING;

      try {
        const response = await getTaxSettlement(year);
        this.taxSettlement = response?.data ?? null;
        this.annualSalaryLookupStatus =
          Number(this.taxSettlement?.annualSalary) > 0
            ? ANNUAL_SALARY_LOOKUP_STATUS.AVAILABLE
            : ANNUAL_SALARY_LOOKUP_STATUS.UNAVAILABLE;

        return this.taxSettlement;
      } catch (error) {
        this.taxSettlement = null;

        const errorCode = getApiErrorCode(error);
        this.taxSettlementErrorCode = errorCode;
        if (errorCode === "PROFILE_004") {
          this.annualSalaryLookupStatus =
            ANNUAL_SALARY_LOOKUP_STATUS.UNAVAILABLE;
          this.taxSettlementError =
            "세전 연봉 자동 조회 결과가 없습니다. 금융기관 연결을 다시 진행해 주세요.";
        } else if (errorCode !== "REPORT_002") {
          this.annualSalaryLookupStatus = ANNUAL_SALARY_LOOKUP_STATUS.ERROR;
          this.taxSettlementError = getApiErrorMessage(
            error,
            "소득공제 달성률을 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
          );
        } else {
          this.annualSalaryLookupStatus = ANNUAL_SALARY_LOOKUP_STATUS.ERROR;
        }

        throw error;
      } finally {
        this.isTaxSettlementLoading = false;
      }
    },
  },
});
