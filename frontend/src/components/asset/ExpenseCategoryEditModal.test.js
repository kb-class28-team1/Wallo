import { mount } from "@vue/test-utils"
import { describe, expect, it } from "vitest"
import { EXPENSE_CATEGORY_META } from "@/features/financial/financialCategories"
import ExpenseCategoryEditModal from "./ExpenseCategoryEditModal.vue"

const transaction = {
  transactionId: 9,
  merchantName: "동네 상점",
  type: "EXPENSE",
  category: "OTHER",
}

describe("ExpenseCategoryEditModal", () => {
  it("normalizes OTHER and shows only existing categories", () => {
    const wrapper = mount(ExpenseCategoryEditModal, {
      props: {
        visible: true,
        transaction,
      },
    })

    expect(wrapper.find('[role="dialog"]').classes()).toContain("app-dialog")
    expect(wrapper.find(".app-tabs").exists()).toBe(true)
    const options = wrapper.findAll(".category-option")
    expect(wrapper.get('[data-testid="category-option-ETC"]').classes()).toContain("selected")
    expect(wrapper.findAll('[role="tab"]')[1].classes()).toContain("app-tabs__tab--active")
    expect(options).toHaveLength(Object.keys(EXPENSE_CATEGORY_META).length - 3)
    expect(wrapper.get('[data-testid="category-option-FOOD"] .category-option-icon').exists()).toBe(
      true,
    )
    expect(wrapper.get('[data-testid="category-option-FOOD"] .category-option-label').text()).toBe(
      EXPENSE_CATEGORY_META.FOOD.label,
    )
    expect(options.map((option) => option.attributes("data-testid"))).not.toContain(
      "category-option-INCOME",
    )
    expect(options.map((option) => option.attributes("data-testid"))).not.toContain(
      "category-option-SEND",
    )
    expect(options.map((option) => option.attributes("data-testid"))).not.toContain(
      "category-option-RECEIVE",
    )
  })

  it("shows only the categories related to the selected transaction type", async () => {
    const wrapper = mount(ExpenseCategoryEditModal, {
      props: {
        visible: true,
        transaction,
      },
    })

    await wrapper.get('[data-testid="transaction-type-INCOME"]').trigger("click")
    expect(wrapper.findAll(".category-option")).toHaveLength(1)
    expect(wrapper.get('[data-testid="category-option-INCOME"]').classes()).toContain("selected")
    expect(wrapper.find('[data-testid="category-option-FOOD"]').exists()).toBe(false)

    await wrapper.get('[data-testid="transaction-type-TRANSFER"]').trigger("click")
    expect(wrapper.findAll(".category-option")).toHaveLength(2)
    expect(wrapper.get('[data-testid="category-option-SEND"]').classes()).toContain("selected")
    expect(wrapper.get('[data-testid="category-option-RECEIVE"]').exists()).toBe(true)

    await wrapper.get('[data-testid="category-option-RECEIVE"]').trigger("click")
    expect(wrapper.get('[data-testid="category-option-RECEIVE"]').classes()).toContain("selected")
  })

  it("opens the RECEIVE filter in the transfer category group", () => {
    const wrapper = mount(ExpenseCategoryEditModal, {
      props: {
        visible: true,
        mode: "filter",
        initialCategory: "RECEIVE",
      },
    })

    expect(wrapper.findAll('[role="tab"]')[2].classes()).toContain("app-tabs__tab--active")
    expect(wrapper.get('[data-testid="category-option-RECEIVE"]').classes()).toContain("selected")
  })

  it("reuses the category cards for an ALL filter and emits only the category", async () => {
    const wrapper = mount(ExpenseCategoryEditModal, {
      props: {
        visible: true,
        mode: "filter",
        initialCategory: "ALL",
      },
    })

    expect(wrapper.find('[role="dialog"] h2').text()).toBe("카테고리 필터")
    expect(wrapper.get('[data-testid="category-option-ALL"]').classes()).toContain("selected")

    await wrapper.get('[data-testid="category-option-FOOD"]').trigger("click")
    await wrapper.get("[data-modal-confirm]").trigger("click")

    expect(wrapper.emitted("save")?.[0]?.[0]).toEqual({ category: "FOOD" })
  })

  it("emits the selected category with the transaction id", async () => {
    const wrapper = mount(ExpenseCategoryEditModal, {
      props: {
        visible: true,
        transaction,
      },
    })

    await wrapper.get('[data-testid="category-option-FOOD"]').trigger("click")
    await wrapper.get("[data-modal-confirm]").trigger("click")

    expect(wrapper.emitted("save")?.[0]?.[0]).toEqual({
      transactionId: 9,
      category: "FOOD",
    })
  })

  it("does not emit save while the request is in progress", async () => {
    const wrapper = mount(ExpenseCategoryEditModal, {
      props: {
        visible: true,
        transaction,
        isSaving: true,
      },
    })

    expect(wrapper.get("[data-modal-confirm]").element.disabled).toBe(true)
    await wrapper.get("[data-modal-confirm]").trigger("click")

    expect(wrapper.emitted("save")).toBeUndefined()
  })
})
