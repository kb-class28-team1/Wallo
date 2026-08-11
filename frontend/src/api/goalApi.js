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

export const getAvailableGoalAccounts = async () => {
  try {
    const response = await httpClient.get("/api/goals/available-accounts");

    return response.data;
  } catch (error) {
    throw normalizeRequestError(
      error,
      "목표에 사용할 수 있는 계좌를 불러오지 못했습니다.",
    );
  }
};

export const getGoalRoadmap = async (goalId) => {
  try {
    const response = await httpClient.get(`/api/goals/${goalId}/roadmap`)
    return response.data
  } catch (error) {
    throw normalizeRequestError(error, "목표 로드맵을 불러오지 못했습니다.")
  }
}

export const updateGoalRoadmapStep = async (goalId, stepNumber, completed) => {
  try {
    const response = await httpClient.put(
      `/api/goals/${goalId}/roadmap/steps/${stepNumber}`,
      { completed },
    )
    return response.data
  } catch (error) {
    throw normalizeRequestError(error, "로드맵 진행 상태를 저장하지 못했습니다.")
  }
}

export const selectGoalAccount = async (goalId, accountId) => {
  try {
    const response = await httpClient.put(`/api/goals/${goalId}/account`, {
      accountId,
    });

    return response.data;
  } catch (error) {
    throw normalizeRequestError(error, "목표 계좌를 저장하지 못했습니다.");
  }
};
