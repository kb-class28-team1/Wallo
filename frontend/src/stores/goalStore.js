import { ref } from "vue";
import { defineStore } from "pinia";
import {
  getAvailableGoalAccounts,
  getGoalRoadmap,
  getGoals,
  selectGoalAccount as selectGoalAccountRequest,
  updateGoalRoadmapStep,
} from "@/api/goalApi";
import { getApiErrorMessage } from "@/commonUtils/apiError";

export const useGoalStore = defineStore("goal", () => {
  const goals = ref([]);
  const isLoading = ref(false);
  const error = ref(null);
  const availableAccounts = ref([]);
  const isAccountLoading = ref(false);
  const isAccountSaving = ref(false);
  const accountError = ref(null);
  const roadmap = ref(null)
  const isRoadmapLoading = ref(false)
  const roadmapError = ref(null)
  const isRoadmapProgressSaving = ref(false)

  const fetchGoalRoadmap = async (goalId, { notifyError = true } = {}) => {
    if (!goalId) {
      roadmap.value = null
      return null
    }
    isRoadmapLoading.value = true
    roadmapError.value = null
    try {
      const response = await getGoalRoadmap(goalId)
      roadmap.value = response?.data ?? null
      return roadmap.value
    } catch (caughtError) {
      roadmap.value = null
      roadmapError.value = getApiErrorMessage(caughtError, "목표 로드맵을 불러오지 못했습니다.")
      if (notifyError) alert(roadmapError.value)
      return null
    } finally {
      isRoadmapLoading.value = false
    }
  }

  const saveRoadmapStep = async (goalId, stepNumber, completed) => {
    isRoadmapProgressSaving.value = true
    roadmapError.value = null
    try {
      const response = await updateGoalRoadmapStep(goalId, stepNumber, completed)
      roadmap.value = response?.data ?? null
      return roadmap.value
    } catch (caughtError) {
      roadmapError.value = getApiErrorMessage(
        caughtError,
        "로드맵 진행 상태를 저장하지 못했습니다.",
      )
      alert(roadmapError.value)
      return null
    } finally {
      isRoadmapProgressSaving.value = false
    }
  }

  const fetchGoals = async ({ notifyError = true } = {}) => {
    isLoading.value = true;
    error.value = null;

    try {
      const response = await getGoals();
      goals.value = Array.isArray(response?.data) ? response.data : [];
      await fetchGoalRoadmap(goals.value[0]?.goalId, { notifyError: false })

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

  const fetchAvailableAccounts = async ({ notifyError = true } = {}) => {
    isAccountLoading.value = true;
    accountError.value = null;

    try {
      const response = await getAvailableGoalAccounts();
      availableAccounts.value = Array.isArray(response?.data)
        ? response.data
        : [];

      return availableAccounts.value;
    } catch (caughtError) {
      availableAccounts.value = [];
      accountError.value = getApiErrorMessage(
        caughtError,
        "목표에 사용할 수 있는 계좌를 불러오는 중 오류가 발생했습니다.",
      );

      if (notifyError) {
        alert(accountError.value);
      }

      return [];
    } finally {
      isAccountLoading.value = false;
    }
  };

  const saveGoalAccount = async (goalId, accountId) => {
    isAccountSaving.value = true;
    accountError.value = null;

    try {
      const response = await selectGoalAccountRequest(goalId, accountId);
      const selectedAccount = response?.data ?? null;

      availableAccounts.value = availableAccounts.value.map((account) => ({
        ...account,
        selected: account.accountId === selectedAccount?.accountId,
      }));

      return selectedAccount;
    } catch (caughtError) {
      accountError.value = getApiErrorMessage(
        caughtError,
        "목표 계좌를 저장하는 중 오류가 발생했습니다.",
      );
      alert(accountError.value);
      throw caughtError;
    } finally {
      isAccountSaving.value = false;
    }
  };

  return {
    goals,
    isLoading,
    error,
    fetchGoals,
    availableAccounts,
    isAccountLoading,
    isAccountSaving,
    accountError,
    roadmap,
    isRoadmapLoading,
    roadmapError,
    isRoadmapProgressSaving,
    fetchGoalRoadmap,
    saveRoadmapStep,
    fetchAvailableAccounts,
    saveGoalAccount,
  };
});
