import { afterEach, describe, expect, it, vi } from "vitest";
import httpClient from "@/api/httpClient";
import {
  getCategoryBudgets,
  putCategoryBudgets,
  syncAssets,
  updateExpenseCategory,
  updateAnnualSalary,
} from "./assetApi";

vi.mock("@/api/httpClient", () => ({
  default: {
    get: vi.fn(),
    patch: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

describe("assetApi annual salary fallback", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("updates the authenticated user's annual salary", async () => {
    const response = {
      success: true,
      data: { annualSalary: 50_000_000 },
    };
    httpClient.patch.mockResolvedValue({ data: response });

    await expect(updateAnnualSalary(50_000_000)).resolves.toEqual(response);

    expect(httpClient.patch).toHaveBeenCalledWith("/api/users/profile", {
      annualSalary: 50_000_000,
    });
  });

  it("preserves the backend error code for manual salary failures", async () => {
    const response = {
      status: 400,
      data: {
        error: {
          code: "PROFILE_001",
          message: "invalid salary",
        },
      },
    };
    const error = new Error("request failed");
    error.response = response;
    httpClient.patch.mockRejectedValue(error);

    await expect(updateAnnualSalary(0)).rejects.toMatchObject({
      message: "invalid salary",
      code: "PROFILE_001",
      status: 400,
      response,
    });
  });

  it("requests manual asset synchronization", async () => {
    const response = {
      success: true,
      data: {
        syncedAt: "2026-08-12T10:00:00",
        inserted: 3,
        updated: 42,
        failedConnections: 0,
      },
    };
    httpClient.post.mockResolvedValue({ data: response });

    await expect(syncAssets()).resolves.toEqual(response);

    expect(httpClient.post).toHaveBeenCalledWith("/api/assets/sync");
  });

  it("normalizes manual asset synchronization failures", async () => {
    const response = {
      status: 503,
      data: {
        error: {
          code: "COMMON_001",
          message: "sync unavailable",
        },
      },
    };
    const error = new Error("request failed");
    error.response = response;
    httpClient.post.mockRejectedValue(error);

    await expect(syncAssets()).rejects.toMatchObject({
      message: "sync unavailable",
      code: "COMMON_001",
      status: 503,
      response,
    });
  });

  it("requests category budgets for the selected month", async () => {
    const response = {
      success: true,
      data: { targetMonth: "2026-08", categories: [] },
    };
    httpClient.get.mockResolvedValue({ data: response });

    await expect(getCategoryBudgets("2026-08")).resolves.toEqual(response);

    expect(httpClient.get).toHaveBeenCalledWith("/api/budgets/categories", {
      params: { targetMonth: "2026-08" },
    });
  });

  it("saves the category budget batch", async () => {
    const request = {
      targetMonth: "2026-08",
      totalAmount: 1_000_000,
      categoryBudgets: [{ category: "FOOD", budgetAmount: 300_000 }],
    };
    const response = { success: true, data: { targetMonth: "2026-08" } };
    httpClient.put.mockResolvedValue({ data: response });

    await expect(putCategoryBudgets(request)).resolves.toEqual(response);

    expect(httpClient.put).toHaveBeenCalledWith("/api/budgets/categories", request);
  });

  it("updates a transaction category", async () => {
    const response = { success: true, data: null };
    httpClient.patch.mockResolvedValue({ data: response });

    await expect(updateExpenseCategory(9, "FOOD")).resolves.toEqual(response);

    expect(httpClient.patch).toHaveBeenCalledWith(
      "/api/assets/expense/9/category",
      { category: "FOOD" },
    );
  });

  it("preserves the backend error code for category update failures", async () => {
    const response = {
      status: 400,
      data: {
        error: {
          code: "DASHBOARD_001",
          message: "invalid category",
        },
      },
    };
    const error = new Error("request failed");
    error.response = response;
    httpClient.patch.mockRejectedValue(error);

    await expect(updateExpenseCategory(9, "UNKNOWN")).rejects.toMatchObject({
      message: "invalid category",
      code: "DASHBOARD_001",
      status: 400,
      response,
    });
  });
});
