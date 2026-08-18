import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import ExpenseTransactionList from "./ExpenseTransactionList.vue";

const transaction = {
  transactionId: 9,
  date: "2026-08-11",
  type: "EXPENSE",
  category: "FOOD",
  amount: 12_000,
  merchantName: "동네 식당",
};

describe("ExpenseTransactionList category editing", () => {
  it("emits edit-category when the editable transaction icon is clicked", async () => {
    const wrapper = mount(ExpenseTransactionList, {
      props: {
        transactions: [transaction],
        editable: true,
      },
    });

    await wrapper.get('[data-testid="transaction-category-button"]').trigger("click");

    expect(wrapper.emitted("edit-category")?.[0]?.[0]).toEqual(transaction);
  });

  it("keeps the transaction icon read-only when editing is disabled", () => {
    const wrapper = mount(ExpenseTransactionList, {
      props: {
        transactions: [transaction],
        editable: false,
      },
    });

    expect(wrapper.find('[data-testid="transaction-category-button"]').exists()).toBe(false);
    expect(wrapper.find(".transaction-icon").exists()).toBe(true);
  });

  it("shows incoming and outgoing transfer directions with the correct sign", () => {
    const wrapper = mount(ExpenseTransactionList, {
      props: {
        transactions: [
          {
            ...transaction,
            transactionId: 10,
            type: "TRANSFER",
            category: "RECEIVE",
            amount: 5_000,
            merchantName: "친구",
          },
          {
            ...transaction,
            transactionId: 11,
            type: "TRANSFER",
            category: "SEND",
            amount: 7_000,
            merchantName: "이체",
          },
        ],
        editable: false,
      },
    });

    const rows = wrapper.findAll("li");
    expect(rows[0].text()).toContain("받은 돈");
    expect(rows[0].text()).toContain("+5,000원");
    expect(rows[1].text()).toContain("보낸 돈");
    expect(rows[1].text()).toContain("-7,000원");
  });
});
