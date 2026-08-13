import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import { EXPENSE_CATEGORY_META } from "@/features/financial/financialCategories";
import ExpenseCategoryEditModal from "./ExpenseCategoryEditModal.vue";

const transaction = {
  transactionId: 9,
  merchantName: "동네 상점",
  category: "OTHER",
};

describe("ExpenseCategoryEditModal", () => {
  it("normalizes OTHER and shows only existing categories", () => {
    const wrapper = mount(ExpenseCategoryEditModal, {
      props: {
        visible: true,
        transaction,
      },
    });

    const options = wrapper.findAll("#expenseCategoryEditSelect option");
    expect(wrapper.get("#expenseCategoryEditSelect").element.value).toBe("ETC");
    expect(options).toHaveLength(Object.keys(EXPENSE_CATEGORY_META).length);
    expect(options.map((option) => option.element.value)).toContain("INCOME");
    expect(options.map((option) => option.element.value)).toContain("SEND");
    expect(options.map((option) => option.element.value)).not.toContain("OTHER");
  });

  it("emits the selected category with the transaction id", async () => {
    const wrapper = mount(ExpenseCategoryEditModal, {
      props: {
        visible: true,
        transaction,
      },
    });

    await wrapper.get("#expenseCategoryEditSelect").setValue("FOOD");
    await wrapper.get("form").trigger("submit");

    expect(wrapper.emitted("save")?.[0]?.[0]).toEqual({
      transactionId: 9,
      category: "FOOD",
    });
  });

  it("does not emit save while the request is in progress", async () => {
    const wrapper = mount(ExpenseCategoryEditModal, {
      props: {
        visible: true,
        transaction,
        isSaving: true,
      },
    });

    expect(wrapper.get('button[type="submit"]').element.disabled).toBe(true);
    await wrapper.get("form").trigger("submit");

    expect(wrapper.emitted("save")).toBeUndefined();
  });
});
