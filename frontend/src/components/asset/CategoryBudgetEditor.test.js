import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"
import { BUDGET_CATEGORY_CODES } from "@/features/financial/financialCategories"
import CategoryBudgetEditor from "./CategoryBudgetEditor.vue"

const createBudgetSummary = () => ({
  targetMonth: "2026-08",
  totalAmount: 1_000_000,
  categories: BUDGET_CATEGORY_CODES.map((category) => ({
    category,
    budgetAmount: category === "FOOD" ? 300_000 : 0,
    spentAmount: category === "FOOD" ? 360_000 : 0,
  })),
})

describe("CategoryBudgetEditor", () => {
  it("shows every editable category, including zero-budget categories", () => {
    const wrapper = mount(CategoryBudgetEditor, {
      props: {
        visible: true,
        targetMonth: "2026-08",
        budgetSummary: createBudgetSummary(),
      },
    })

    expect(wrapper.find('[role="dialog"]').classes()).toContain("app-dialog")
    expect(wrapper.text()).toContain("2026-08부터 매월 적용됩니다.")
    expect(wrapper.find(".app-dialog-close").exists()).toBe(true)
    expect(wrapper.get("[data-modal-confirm]").text()).toContain("확인")
    expect(wrapper.get(".category-budget-editor-body").classes()).toContain(
      "category-budget-editor-body",
    )
    expect(wrapper.findAll(".category-budget-editor-item")).toHaveLength(
      BUDGET_CATEGORY_CODES.length,
    )
    expect(wrapper.find("#budget-CAFE").element.value).toBe("0")
    expect(wrapper.text()).toContain("기타·미배정 예산")
    expect(wrapper.text()).toContain("700,000원")
  })

  it("emits all category budgets with the calculated total", async () => {
    const wrapper = mount(CategoryBudgetEditor, {
      props: {
        visible: true,
        targetMonth: "2026-08",
        budgetSummary: createBudgetSummary(),
      },
    })

    await wrapper.find("#budget-FOOD").setValue("350000")
    expect(wrapper.find("#categoryBudgetTotal").element.value).toBe("350,000")
    await wrapper.find("#categoryBudgetTotal").setValue("1000000")
    await wrapper.find("form").trigger("submit")

    const saveEvent = wrapper.emitted("save")?.[0]?.[0]
    expect(saveEvent).toMatchObject({
      targetMonth: "2026-08",
      totalAmount: 1_000_000,
    })
    expect(saveEvent.categoryBudgets).toHaveLength(BUDGET_CATEGORY_CODES.length)
    expect(saveEvent.categoryBudgets).toContainEqual({
      category: "FOOD",
      budgetAmount: 350_000,
    })
    expect(wrapper.find("#budget-FOOD").element.value).toBe("350,000")
  })

  it("disables saving when category allocations exceed the total budget", async () => {
    const wrapper = mount(CategoryBudgetEditor, {
      props: {
        visible: true,
        targetMonth: "2026-08",
        budgetSummary: createBudgetSummary(),
      },
    })

    await wrapper.find("#categoryBudgetTotal").setValue("100000")
    await wrapper.find("#budget-FOOD").setValue("100000")
    await wrapper.find("#budget-CAFE").setValue("100000")
    await wrapper.find("#categoryBudgetTotal").setValue("100000")

    expect(wrapper.find("[data-modal-confirm]").element.disabled).toBe(true)
    expect(wrapper.text()).toContain("카테고리 배분 합계가 전체 예산을 초과했습니다.")
    expect(wrapper.emitted("save")).toBeUndefined()
  })
})
