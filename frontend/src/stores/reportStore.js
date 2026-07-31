import { defineStore } from "pinia";
import { getInsight } from "@/api/reportApi";

const getErrorMessage = (error) =>
  error.response?.data?.error?.message ||
  error.response?.data?.message ||
  (error.response?.status === 401
    ? "로그인이 만료되었습니다. 다시 로그인해 주세요."
    : "소비 리포트를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

export const useReportStore = defineStore("report", {
  state: () => ({
    insight: null,
    isInsightLoading: false,
    insightError: null,
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
        const errorMessage = getErrorMessage(error);

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
  },
});
