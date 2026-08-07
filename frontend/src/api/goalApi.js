import httpClient from "@/api/httpClient";
import { getApiErrorMessage } from "@/commonUtils/apiError";

const normalizeRequestError = (error, fallbackMessage) => {
  const normalizedError = new Error(getApiErrorMessage(error, fallbackMessage));
  normalizedError.cause = error;

  return normalizedError;
};

export const getGoals = async () => {
  try {
    const response = await httpClient.get("/api/goals");

    return response.data;
  } catch (error) {
    throw normalizeRequestError(error, "확정된 목표를 불러오지 못했습니다.");
  }
};

export const getGoalByConversationId = async (conversationId) => {
  try {
    const response = await httpClient.get(
      `/api/conversations/${conversationId}/goal`,
    );

    return response.data;
  } catch (error) {
    throw normalizeRequestError(error, "채팅방의 목표를 불러오지 못했습니다.");
  }
};
