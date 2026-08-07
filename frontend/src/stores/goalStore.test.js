import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getGoals } from "@/api/goalApi";
import { useGoalStore } from "./goalStore";

vi.mock("@/api/goalApi", () => ({
  getGoals: vi.fn(),
}));

describe("goalStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.stubGlobal("alert", vi.fn());
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
});
