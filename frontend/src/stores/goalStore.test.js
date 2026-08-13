import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  getAvailableGoalAccounts,
  getGoalRoadmap,
  getGoals,
  selectGoalAccount,
  updateGoalRoadmapStep,
} from "@/api/goalApi";
import { useGoalStore } from "./goalStore";

vi.mock("@/api/goalApi", () => ({
  getAvailableGoalAccounts: vi.fn(),
  getGoalByConversationId: vi.fn(),
  getGoalRoadmap: vi.fn(),
  getGoals: vi.fn(),
  selectGoalAccount: vi.fn(),
  updateGoalRoadmapStep: vi.fn(),
}));

describe("goalStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.stubGlobal("alert", vi.fn());
    getGoalRoadmap.mockResolvedValue({ success: true, data: null });
    updateGoalRoadmapStep.mockResolvedValue({ success: true, data: null });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("stores confirmed goals returned by the API", async () => {
    const goals = [{ goalId: 1, title: "Emergency fund", status: "ACTIVE" }];
    getGoals.mockResolvedValue({ success: true, data: goals });

    const store = useGoalStore();

    await expect(store.fetchGoals()).resolves.toEqual(goals);
    expect(store.goals).toEqual(goals);
    expect(store.isLoading).toBe(false);
    expect(store.error).toBeNull();
    expect(getGoalRoadmap).toHaveBeenCalledWith(1);
  });

  it("stores the AI roadmap returned for a confirmed goal", async () => {
    const roadmap = {
      goalId: 31,
      generationStatus: "COMPLETED",
      roadmap: { steps: [{ title: "비상금 계좌 분리" }] },
    };
    getGoalRoadmap.mockResolvedValue({ success: true, data: roadmap });

    const store = useGoalStore();

    await expect(store.fetchGoalRoadmap(31)).resolves.toEqual(roadmap);
    expect(store.roadmap).toEqual(roadmap);
    expect(store.roadmapError).toBeNull();
    expect(store.isRoadmapLoading).toBe(false);
  });

  it("stores updated roadmap progress", async () => {
    const updated = {
      currentStepNumber: 2,
      completedStepNumbers: [1],
      roadmap: { steps: [{ stepNumber: 1 }, { stepNumber: 2 }] },
    };
    updateGoalRoadmapStep.mockResolvedValue({ success: true, data: updated });
    const store = useGoalStore();

    await expect(store.saveRoadmapStep(31, 1, true)).resolves.toEqual(updated);
    expect(updateGoalRoadmapStep).toHaveBeenCalledWith(31, 1, true);
    expect(store.roadmap).toEqual(updated);
  });

  it("keeps an empty list when the user has no confirmed goal", async () => {
    getGoals.mockResolvedValue({ success: true, data: [] });

    const store = useGoalStore();

    await expect(store.fetchGoals()).resolves.toEqual([]);
    expect(store.goals).toEqual([]);
    expect(store.error).toBeNull();
    expect(alert).not.toHaveBeenCalled();
  });

  it("exposes the error, clears stale goals, and notifies the user", async () => {
    const store = useGoalStore();
    store.goals = [{ goalId: 1, title: "Old goal" }];
    getGoals.mockRejectedValue(new Error("goal request failed"));

    await expect(store.fetchGoals()).resolves.toEqual([]);
    expect(store.goals).toEqual([]);
    expect(store.error).toBe("goal request failed");
    expect(store.isLoading).toBe(false);
    expect(alert).toHaveBeenCalledWith("goal request failed");
  });

  it("stores eligible accounts and their selected state", async () => {
    const accounts = [
      { accountId: 101, bankName: "Wallo Bank", accountType: "입출금", selected: true },
      { accountId: 102, bankName: "Wallo Securities", accountType: "CMA", selected: false },
    ];
    getAvailableGoalAccounts.mockResolvedValue({ success: true, data: accounts });

    const store = useGoalStore();

    await expect(store.fetchAvailableAccounts()).resolves.toEqual(accounts);
    expect(store.availableAccounts).toEqual(accounts);
    expect(store.isAccountLoading).toBe(false);
  });

  it("updates the selected account after saving", async () => {
    const store = useGoalStore();
    store.availableAccounts = [
      { accountId: 101, selected: true },
      { accountId: 102, selected: false },
    ];
    selectGoalAccount.mockResolvedValue({
      success: true,
      data: { accountId: 102, selected: true },
    });

    await expect(store.saveGoalAccount(31, 102)).resolves.toEqual({
      accountId: 102,
      selected: true,
    });

    expect(store.availableAccounts[0].selected).toBe(false);
    expect(store.availableAccounts[1].selected).toBe(true);
    expect(store.isAccountSaving).toBe(false);
  });
});
