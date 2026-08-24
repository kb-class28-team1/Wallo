import { createPinia, setActivePinia } from "pinia"
import { flushPromises, mount } from "@vue/test-utils"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { getTaxSettlement, updateAnnualSalary } from "@/api/assetApi"
import TaxDeductionTrackerCard from "./TaxDeductionTrackerCard.vue"

vi.mock("@/api/assetApi", () => ({
  getInsight: vi.fn(),
  getTaxSettlement: vi.fn(),
  updateAnnualSalary: vi.fn(),
}))

describe("TaxDeductionTrackerCard", () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getTaxSettlement.mockResolvedValue({
      data: {
        annualSalary: 50_000_000,
        creditCardThreshold: 12_500_000,
        cardSpentYtd: 6_250_000,
      },
    })
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it("shows the automatically queried salary and no manual input controls", async () => {
    const wrapper = mount(TaxDeductionTrackerCard, {
      global: {
        plugins: [createPinia()],
      },
    })

    await flushPromises()

    expect(wrapper.find(".tax-deduction-card").classes()).toContain("app-card")
    expect(wrapper.text()).not.toContain("50,000,000")
    expect(wrapper.text()).toContain("50%")
    expect(wrapper.text()).toContain("공제 문턱 달성률")
    expect(wrapper.findAll(".tax-deduction-label")).toHaveLength(0)
    expect(wrapper.text()).toContain("6,250,000원")
    expect(wrapper.text()).toContain("공제가 시작되기까지 6,250,000원 남았어요.")
    expect(wrapper.find(".tax-deduction-remaining").text()).toBe("6,250,000원")
    expect(wrapper.find(".tax-deduction-summary .tax-deduction-usage").exists()).toBe(true)
    expect(wrapper.find(".tax-deduction-summary .tax-deduction-rate").exists()).toBe(true)
    expect(wrapper.text()).toContain("신용카드 혜택 구간")
    expect(wrapper.find("input").exists()).toBe(false)
    expect(wrapper.find(".manual-salary-form").exists()).toBe(false)

    const guideToggle = wrapper.find(".tax-guide-toggle")
    expect(guideToggle.attributes("aria-haspopup")).toBe("true")
    expect(guideToggle.attributes("aria-expanded")).toBeUndefined()
    expect(wrapper.find(".tax-guide-panel").attributes("role")).toBe("tooltip")
    expect(wrapper.text()).toContain("연봉 7,000만 원 이하")
  })

  it("recommends check cards and cash receipts after reaching the threshold", async () => {
    getTaxSettlement.mockResolvedValueOnce({
      data: {
        annualSalary: 50_000_000,
        creditCardThreshold: 12_500_000,
        cardSpentYtd: 15_000_000,
      },
    })

    const wrapper = mount(TaxDeductionTrackerCard, {
      global: {
        plugins: [createPinia()],
      },
    })

    await flushPromises()

    expect(wrapper.find(".tax-deduction-rate").text()).toBe("달성")
    expect(wrapper.find(".tax-strategy").classes()).toContain("tax-strategy--reached")
    expect(wrapper.text()).toContain("체크카드·현금영수증 절세 구간")
    expect(wrapper.text()).toContain("공제 문턱을 달성했어요.")
  })

  it("shows the manual salary form when automatic lookup is unavailable", async () => {
    const error = new Error("salary unavailable")
    error.response = {
      data: {
        error: {
          code: "PROFILE_004",
          message: "salary unavailable",
        },
      },
    }
    getTaxSettlement.mockRejectedValue(error)
    updateAnnualSalary.mockResolvedValue({
      data: { annualSalary: 50_000_000 },
    })
    getTaxSettlement.mockRejectedValueOnce(error).mockResolvedValueOnce({
      data: {
        annualSalary: 50_000_000,
        creditCardThreshold: 12_500_000,
        cardSpentYtd: 6_250_000,
      },
    })

    const wrapper = mount(TaxDeductionTrackerCard, {
      global: {
        plugins: [createPinia()],
      },
    })

    await flushPromises()

    expect(wrapper.find("#manualAnnualSalary").exists()).toBe(true)
    expect(wrapper.find(".manual-salary-form").exists()).toBe(true)
    expect(wrapper.find(".manual-salary-state i").exists()).toBe(false)
    expect(wrapper.find(".btn-outline-danger").exists()).toBe(false)

    await wrapper.find("#manualAnnualSalary").setValue("50000000")
    await wrapper.find(".manual-salary-form").trigger("submit")
    await flushPromises()

    expect(updateAnnualSalary).toHaveBeenCalledWith(50_000_000)
    expect(wrapper.find(".manual-salary-form").exists()).toBe(false)
    expect(wrapper.text()).not.toContain("50,000,000")
  })

  it("keeps the retry state for general lookup errors", async () => {
    const error = new Error("server unavailable")
    error.response = {
      data: {
        error: {
          code: "COMMON_001",
          message: "server unavailable",
        },
      },
    }
    getTaxSettlement.mockRejectedValue(error)

    const wrapper = mount(TaxDeductionTrackerCard, {
      global: {
        plugins: [createPinia()],
      },
    })

    await flushPromises()

    expect(wrapper.find(".manual-salary-form").exists()).toBe(false)
    expect(wrapper.find(".tax-deduction-state").classes()).toContain("app-state")
    expect(wrapper.find(".tax-deduction-state").classes()).toContain("app-state--error")
    expect(wrapper.find(".btn-outline-danger").exists()).toBe(true)
  })
})
