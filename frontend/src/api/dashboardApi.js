import httpClient from "@/api/httpClient";

const formatDate = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
};

const getCurrentMonthDateRange = () => {
  const today = new Date();

  return {
    startDate: formatDate(new Date(today.getFullYear(), today.getMonth(), 1)),
    endDate: formatDate(new Date(today.getFullYear(), today.getMonth() + 1, 0)),
  };
};

export const getAssets = () => httpClient.get("/api/assets");

export const getBudgets = () => httpClient.get("/api/budgets");

export const getExpenses = (params = {}) =>
  httpClient.get("/api/assets/expense", {
    params: {
      ...getCurrentMonthDateRange(),
      page: 0,
      size: 20,
      ...params,
    },
  });

export const putBudget = (targetMonth, totalAmount) =>
  httpClient.put("/api/budgets", {
    targetMonth,
    totalAmount,
  });
