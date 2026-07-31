import httpClient from "@/api/httpClient";

export const getInsight = async () => {
  try {
    const response = await httpClient.get("/api/reports/insights");

    return response.data;
  } catch (error) {
    throw error;
  }
};
