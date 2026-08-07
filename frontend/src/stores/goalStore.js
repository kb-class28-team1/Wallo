import { ref } from "vue";
import { defineStore } from "pinia";
import { getGoals } from "@/api/goalApi";
import { getApiErrorMessage } from "@/commonUtils/apiError";

export const useGoalStore = defineStore("goal", () => {
  const goals = ref([]);
  const isLoading = ref(false);
  const error = ref(null);

  const fetchGoals = async ({ notifyError = true } = {}) => {
    isLoading.value = true;
    error.value = null;

    try {
      const response = await getGoals();
      goals.value = Array.isArray(response?.data) ? response.data : [];

      return goals.value;
    } catch (caughtError) {
      goals.value = [];
      error.value = getApiErrorMessage(
        caughtError,
        "확정된 목표를 불러오는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
      );

      if (notifyError) {
        alert(error.value);
      }

      return [];
    } finally {
      isLoading.value = false;
    }
  };

  return {
    goals,
    isLoading,
    error,
    fetchGoals,
  };
});
