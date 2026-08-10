import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getTaxSettlement, updateAnnualSalary } from "@/api/assetApi";
import TaxDeductionTrackerCard from "./TaxDeductionTrackerCard.vue";

vi.mock("@/api/assetApi", () => ({
  getInsight: vi.fn(),
  getTaxSettlement: vi.fn(),
  updateAnnualSalary: vi.fn(),
}));

describe("TaxDeductionTrackerCard", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    getTaxSettlement.mockResolvedValue({
      data: {
        annualSalary: 50_000_000,
        creditCardThreshold: 12_500_000,
        cardSpentYtd: 6_250_000,
      },
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("shows the automatically queried salary and no manual input controls", async () => {
    const wrapper = mount(TaxDeductionTrackerCard, {
      global: {
        plugins: [createPinia()],
      },
    });

    await flushPromises();

    expect(wrapper.text()).toContain("50,000,000");
    expect(wrapper.text()).toContain("50%");
    expect(wrapper.find("input").exists()).toBe(false);
    expect(wrapper.find(".manual-salary-form").exists()).toBe(false);
  });

  it("shows the manual salary form when automatic lookup is unavailable", async () => {
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
    updateAnnualSalary.mockResolvedValue({
      data: { annualSalary: 50_000_000 },
    });
    getTaxSettlement
      .mockRejectedValueOnce(error)
      .mockResolvedValueOnce({
        data: {
          annualSalary: 50_000_000,
          creditCardThreshold: 12_500_000,
          cardSpentYtd: 6_250_000,
        },
      });

    const wrapper = mount(TaxDeductionTrackerCard, {
      global: {
        plugins: [createPinia()],
      },
    });

    await flushPromises();

    expect(wrapper.find("#manualAnnualSalary").exists()).toBe(true);
    expect(wrapper.find(".manual-salary-form").exists()).toBe(true);
    expect(wrapper.find(".btn-outline-danger").exists()).toBe(false);

    await wrapper.find("#manualAnnualSalary").setValue("50000000");
    await wrapper.find(".manual-salary-form").trigger("submit");
    await flushPromises();

    expect(updateAnnualSalary).toHaveBeenCalledWith(50_000_000);
    expect(wrapper.find(".manual-salary-form").exists()).toBe(false);
    expect(wrapper.text()).toContain("50,000,000");
  });

  it("keeps the retry state for general lookup errors", async () => {
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

    const wrapper = mount(TaxDeductionTrackerCard, {
      global: {
        plugins: [createPinia()],
      },
    });

    await flushPromises();

    expect(wrapper.find(".manual-salary-form").exists()).toBe(false);
    expect(wrapper.find(".btn-outline-danger").exists()).toBe(true);
  });
});
