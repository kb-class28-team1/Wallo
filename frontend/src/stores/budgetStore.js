import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { getCategoryBudgets, putCategoryBudgets } from "@/api/assetApi";
import { getApiErrorMessage } from "@/commonUtils/apiError";

const normalizeCategorySummary = (data) => ({
  targetMonth: data?.targetMonth ?? "",
  totalAmount: Number(data?.totalAmount) || 0,
  allocatedAmount: Number(data?.allocatedAmount) || 0,
  unallocatedAmount: Number(data?.unallocatedAmount) || 0,
  spentAmount: Number(data?.spentAmount) || 0,
  remainingAmount: Number(data?.remainingAmount) || 0,
  usageRate: data?.usageRate === null || data?.usageRate === undefined
    ? null
    : Number(data.usageRate),
  overBudget: Boolean(data?.overBudget),
  categories: (data?.categories ?? []).map((category) => ({
    category: category.category,
    budgetAmount: Number(category.budgetAmount) || 0,
    spentAmount: Number(category.spentAmount) || 0,
    remainingAmount: Number(category.remainingAmount) || 0,
    usageRate: category.usageRate === null || category.usageRate === undefined
      ? null
      : Number(category.usageRate),
    overBudget: Boolean(category.overBudget),
  })),
});

export const useBudgetStore = defineStore("budget", () => {
  const categorySummary = ref(null);
  const isLoading = ref(false);
  const isSaving = ref(false);
  const error = ref(null);
  let requestSequence = 0;

  const hasBudget = computed(() => Number(categorySummary.value?.totalAmount ?? 0) > 0);

  const fetchCategoryBudgets = async (targetMonth, { notifyError = true } = {}) => {
    const currentRequest = ++requestSequence;
    isLoading.value = true;
    error.value = null;

    try {
      const response = await getCategoryBudgets(targetMonth);
      if (currentRequest !== requestSequence) return categorySummary.value;

      if (!response?.success || !response?.data) {
        throw new Error(response?.error?.message || "카테고리별 예산 응답이 올바르지 않습니다.");
      }

      categorySummary.value = normalizeCategorySummary(response.data);
      return categorySummary.value;
    } catch (caughtError) {
      if (currentRequest !== requestSequence) return categorySummary.value;

      const message = getApiErrorMessage(
        caughtError,
        "카테고리별 예산 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
      );
      categorySummary.value = null;
      error.value = message;
      if (notifyError) alert(message);
      throw caughtError;
    } finally {
      if (currentRequest === requestSequence) isLoading.value = false;
    }
  };

  const saveCategoryBudgets = async (request, { notifyError = true } = {}) => {
    isSaving.value = true;
    error.value = null;

    try {
      const response = await putCategoryBudgets(request);
      if (!response?.success || !response?.data) {
        throw new Error(response?.error?.message || "카테고리별 예산 응답이 올바르지 않습니다.");
      }

      categorySummary.value = normalizeCategorySummary(response.data);
      return categorySummary.value;
    } catch (caughtError) {
      const message = getApiErrorMessage(
        caughtError,
        "카테고리별 예산을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.",
      );
      error.value = message;
      if (notifyError) alert(message);
      throw caughtError;
    } finally {
      isSaving.value = false;
    }
  };

  return {
    categorySummary,
    hasBudget,
    isLoading,
    isSaving,
    error,
    fetchCategoryBudgets,
    saveCategoryBudgets,
  };
});
