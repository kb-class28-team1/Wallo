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
  try {
    const response = await httpClient.get("/api/assets");

    return response.data;
  } catch (error) {
    throw error;
  }
};

export const connectAllAssets = async (consentAgreed) => {
  try {
    const response = await httpClient.post("/api/connections", {
      consentAgreed,
    });

    return response.data;
  } catch (error) {
    throw error;
  }
};

export const getExpenses = async (params = {}) => {
  try {
    const response = await httpClient.get("/api/assets/expense", {
      params: {
        ...getCurrentMonthDateRange(),
        page: 0,
        size: 20,
        ...params,
      },
    });

    return response.data;
  } catch (error) {
    throw error;
  }
};

export const getBudgets = async () => {
  try {
    const response = await httpClient.get("/api/budgets");

    return response.data;
  } catch (error) {
    throw error;
  }
};

export const putBudget = async (targetMonth, totalAmount) => {
  try {
    const response = await httpClient.put("/api/budgets", {
      targetMonth,
      totalAmount,
    });

    return response.data;
  } catch (error) {
    throw error;
  }
};

export const getInsight = async () => {
  try {
    const response = await httpClient.get("/api/reports/insights");

    return response.data;
  } catch (error) {
    throw error;
  }
};

export const getTaxSettlement = async (year) => {
  try {
    const response = await httpClient.get("/api/reports/tax-settlement", {
      params: year ? { year } : {},
    });

    return response.data;
  } catch (error) {
    throw error;
  }
};

export const updateAnnualSalary = async (annualSalary) => {
  try {
    const response = await httpClient.patch("/api/users/profile", {
      annualSalary,
    });

    return response.data;
  } catch (error) {
    throw error;
  }
};

