import httpClient from "@/api/httpClient";

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

export const getExpenseHistory = async ({ startDate, endDate, page, size }) => {
  try {
    const response = await httpClient.get("/api/assets/expense", {
      params: {
        startDate,
        endDate,
        page,
        size,
      },
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

