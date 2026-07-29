import httpClient from "@/api/httpClient";

const formatDate = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
};

const getCurrentMonthDateRange = () => {
  const today = new Date();
  const startDate = new Date(today.getFullYear(), today.getMonth(), 1);
  const endDate = new Date(today.getFullYear(), today.getMonth() + 1, 0);

  return {
    startDate: formatDate(startDate),
    endDate: formatDate(endDate),
  };
};

export const getAssets = () => httpClient.get("/api/assets");

export const getBudget = () => httpClient.get("/api/budgets");

export const getExpenses = (dateRange = getCurrentMonthDateRange()) =>
  httpClient.get("/api/assets/expense", {
    params: dateRange,
  });

