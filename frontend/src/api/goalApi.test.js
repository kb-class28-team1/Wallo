import { afterEach, describe, expect, it, vi } from "vitest";
import httpClient from "@/api/httpClient";
import { getGoals } from "./goalApi";

vi.mock("@/api/httpClient", () => ({
  default: {
    get: vi.fn(),
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

  it("normalizes an API failure into a user-facing error", async () => {
    httpClient.get.mockRejectedValue(new Error("network error"));

    await expect(getGoals()).rejects.toThrow("network error");
  });
});
