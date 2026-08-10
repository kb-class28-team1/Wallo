import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getTaxSettlement } from "@/api/assetApi";
import {
  ANNUAL_SALARY_LOOKUP_STATUS,
  useReportStore,
} from "./assetReportStore";

vi.mock("@/api/assetApi", () => ({
  getInsight: vi.fn(),
  getTaxSettlement: vi.fn(),
}));

describe("assetReportStore annual salary lookup state", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("marks an available salary after a successful tax settlement response", async () => {
    getTaxSettlement.mockResolvedValue({
      data: { annualSalary: 50_000_000 },
    });
    const store = useReportStore();

    await store.fetchTaxSettlement();

    expect(store.annualSalaryLookupStatus).toBe(
      ANNUAL_SALARY_LOOKUP_STATUS.AVAILABLE,
    );
    expect(store.taxSettlementErrorCode).toBeNull();
  });

  it("marks the salary unavailable for PROFILE_004", async () => {
    const error = new Error("salary unavailable");
    error.response = {
      data: {
        error: {
          code: "PROFILE_004",
          message: "salary unavailable",
        },
      },
    };
    getTaxSettlement.mockRejectedValue(error);
    const store = useReportStore();

    await expect(store.fetchTaxSettlement()).rejects.toBe(error);

    expect(store.annualSalaryLookupStatus).toBe(
      ANNUAL_SALARY_LOOKUP_STATUS.UNAVAILABLE,
    );
    expect(store.taxSettlementErrorCode).toBe("PROFILE_004");
  });

  it("keeps general API failures separate from salary fallback", async () => {
    const error = new Error("server unavailable");
    error.response = {
      data: {
        error: {
          code: "COMMON_001",
          message: "server unavailable",
        },
      },
    };
    getTaxSettlement.mockRejectedValue(error);
    const store = useReportStore();

    await expect(store.fetchTaxSettlement()).rejects.toBe(error);

    expect(store.annualSalaryLookupStatus).toBe(
      ANNUAL_SALARY_LOOKUP_STATUS.ERROR,
    );
    expect(store.taxSettlementErrorCode).toBe("COMMON_001");
  });
});
