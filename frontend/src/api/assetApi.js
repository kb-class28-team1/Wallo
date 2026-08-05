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

export const getAssets = async () => {
  const response = await httpClient.get("/api/assets");

  return response.data;
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

export const getInsight = async () => {
  const response = await httpClient.get("/api/reports/insights");

  return response.data;
};

export const getTaxSettlement = async (year) => {
  const response = await httpClient.get("/api/reports/tax-settlement", {
    params: year ? { year } : {},
  });

  return response.data;
};

export const updateAnnualSalary = async (annualSalary) => {
  const response = await httpClient.patch("/api/users/profile", {
    annualSalary,
  });

  return response.data;
};

