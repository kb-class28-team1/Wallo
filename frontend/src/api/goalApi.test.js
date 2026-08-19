import { afterEach, describe, expect, it, vi } from "vitest";
import httpClient from "@/api/httpClient";
import {
  getAvailableGoalAccounts,
  getGoalRoadmap,
  getGoals,
  selectGoalAccount,
  updateGoalRoadmapStep,
} from "./goalApi";

vi.mock("@/api/httpClient", () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

describe("goalApi", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("requests the authenticated user's confirmed goals", async () => {
    const response = { success: true, data: [{ goalId: 1, title: "Emergency fund" }] };
    httpClient.get.mockResolvedValue({ data: response });

    await expect(getGoals()).resolves.toEqual(response);
    expect(httpClient.get).toHaveBeenCalledWith("/api/goals");
  });

  it("requests dashboard goals without triggering account synchronization", async () => {
    const response = { success: true, data: [{ goalId: 1, title: "Emergency fund" }] };
    httpClient.get.mockResolvedValue({ data: response });

    await expect(getGoals({ syncAccounts: false })).resolves.toEqual(response);
    expect(httpClient.get).toHaveBeenCalledWith("/api/goals/summary");
  });

  it("normalizes an API failure into a user-facing error", async () => {
    httpClient.get.mockRejectedValue(new Error("network error"));

    await expect(getGoals()).rejects.toThrow("network error");
  });

  it("requests eligible accounts for the goal", async () => {
    const response = {
      success: true,
      data: [{ accountId: 101, accountType: "입출금", selected: false }],
    };
    httpClient.get.mockResolvedValue({ data: response });

    await expect(getAvailableGoalAccounts()).resolves.toEqual(response);
    expect(httpClient.get).toHaveBeenCalledWith("/api/goals/available-accounts");
  });

  it("requests the authenticated user's roadmap for a goal", async () => {
    const response = {
      success: true,
      data: { goalId: 31, generationStatus: "COMPLETED", roadmap: { steps: [] } },
    };
    httpClient.get.mockResolvedValue({ data: response });

    await expect(getGoalRoadmap(31)).resolves.toEqual(response);
    expect(httpClient.get).toHaveBeenCalledWith("/api/goals/31/roadmap");
  });

  it("updates a roadmap step's completion state", async () => {
    const response = { success: true, data: { currentStepNumber: 2 } };
    httpClient.put.mockResolvedValue({ data: response });

    await expect(updateGoalRoadmapStep(31, 1, true)).resolves.toEqual(response);
    expect(httpClient.put).toHaveBeenCalledWith(
      "/api/goals/31/roadmap/steps/1",
      { completed: true },
    );
  });

  it("saves one selected goal account", async () => {
    const response = {
      success: true,
      data: { accountId: 101, accountType: "CMA", selected: true },
    };
    httpClient.put.mockResolvedValue({ data: response });

    await expect(selectGoalAccount(31, 101)).resolves.toEqual(response);
    expect(httpClient.put).toHaveBeenCalledWith("/api/goals/31/account", {
      accountId: 101,
    });
  });
});
