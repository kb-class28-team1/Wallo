import { afterEach, describe, expect, it, vi } from "vitest";
import httpClient from "@/api/httpClient";
import { updateAnnualSalary } from "./assetApi";

vi.mock("@/api/httpClient", () => ({
  default: {
    patch: vi.fn(),
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
});
