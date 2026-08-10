import { createPinia, setActivePinia } from "pinia";
import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getTaxSettlement } from "@/api/assetApi";
import TaxDeductionTrackerCard from "./TaxDeductionTrackerCard.vue";

vi.mock("@/api/assetApi", () => ({
  getInsight: vi.fn(),
  getTaxSettlement: vi.fn(),
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

  it("shows the automatically queried salary and no manual input controls", async () => {
    const wrapper = mount(TaxDeductionTrackerCard, {
      global: {
        plugins: [createPinia()],
      },
    });

    await flushPromises();

    expect(wrapper.text()).toContain("자동 조회된 세전 연봉");
    expect(wrapper.text()).toContain("50,000,000원");
    expect(wrapper.text()).toContain("50%");
    expect(wrapper.find("input").exists()).toBe(false);
    expect(wrapper.text()).not.toContain("연봉 입력");
    expect(wrapper.text()).not.toContain("연봉 수정");
  });
});
