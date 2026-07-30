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

