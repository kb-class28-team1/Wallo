import httpClient from "@/api/httpClient";
import { getApiErrorCode, getApiErrorMessage } from "@/utils/apiError";
import { getAppToday } from "@/utils/appDate";

const formatDate = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
};

const getCurrentMonthDateRange = () => {
  const today = getAppToday();

  return {
    startDate: formatDate(new Date(today.getFullYear(), today.getMonth(), 1)),
    endDate: formatDate(new Date(today.getFullYear(), today.getMonth() + 1, 0)),
  };
};

const createApiError = (error, fallbackMessage) => {
  const apiError = new Error(getApiErrorMessage(error, fallbackMessage));
  apiError.code = getApiErrorCode(error);
  apiError.status = error.response?.status;
  apiError.response = error.response;
  return apiError;
};

export const getAssets = async () => {
  const response = await httpClient.get("/api/assets");

  return response.data;
};

export const syncAssets = async () => {
  try {
    const response = await httpClient.post("/api/assets/sync");

    return response.data;
  } catch (error) {
    const apiError = new Error(
      getApiErrorMessage(
        error,
        "자산 거래내역 동기화에 실패했습니다. 잠시 후 다시 시도해 주세요.",
      ),
    );
    apiError.code = getApiErrorCode(error);
    apiError.status = error.response?.status;
    apiError.response = error.response;
    throw apiError;
  }
};

export const connectAllAssets = async (consentAgreed) => {
  const response = await httpClient.post("/api/connections", {
    consentAgreed,
  });

  return response.data;
};

export const getExpenses = async (params = {}) => {
  const response = await httpClient.get("/api/assets/expense", {
    params: {
      ...getCurrentMonthDateRange(),
      page: 0,
      size: 20,
      ...params,
    },
  });

  return response.data;
};

export const updateExpenseCategory = async (transactionId, category) => {
  try {
    const response = await httpClient.patch(
      `/api/assets/expense/${transactionId}/category`,
      { category },
    );

    return response.data;
  } catch (error) {
    throw createApiError(
      error,
      "거래내역 카테고리를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
  }
};

export const getBudgets = async () => {
  const response = await httpClient.get("/api/budgets");

  return response.data;
};

export const putBudget = async (targetMonth, totalAmount) => {
  const response = await httpClient.put("/api/budgets", {
    targetMonth,
    totalAmount,
  });

  return response.data;
};

export const getCategoryBudgets = async (targetMonth) => {
  try {
    const response = await httpClient.get("/api/budgets/categories", {
      params: targetMonth ? { targetMonth } : {},
    });

    return response.data;
  } catch (error) {
    throw createApiError(
      error,
      "카테고리별 예산 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
  }
};

export const putCategoryBudgets = async (request) => {
  try {
    const response = await httpClient.put("/api/budgets/categories", request);

    return response.data;
  } catch (error) {
    throw createApiError(
      error,
      "카테고리별 예산을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
  }
};

export const getInsight = async () => {
  const response = await httpClient.get("/api/asset-reports/insights/");

  return response.data;
};

export const getTaxSettlement = async (year) => {
  const response = await httpClient.get("/api/asset-reports/tax-settlement", {
    params: year ? { year } : {},
  });

  return response.data;
};

export const updateAnnualSalary = async (annualSalary) => {
  try {
    const response = await httpClient.patch("/api/users/profile", {
      annualSalary,
    });

    return response.data;
  } catch (error) {
    const apiError = new Error(
      getApiErrorMessage(error, "연봉을 저장하는 중 오류가 발생했습니다."),
    );
    apiError.code = getApiErrorCode(error);
    apiError.status = error.response?.status;
    apiError.response = error.response;
    throw apiError;
  }
};

