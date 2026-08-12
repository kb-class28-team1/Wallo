import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import GoalAccountSelector from "./GoalAccountSelector.vue";

const accounts = [
  {
    accountId: 101,
    bankName: "Wallo Bank",
    accountName: "생활비 통장",
    displayNumber: "1234-****-7890",
    accountType: "입출금",
    balance: 2500000,
    currency: "KRW",
    selected: true,
  },
  {
    accountId: 102,
    bankName: "Wallo Securities",
    accountName: "CMA 통장",
    displayNumber: "9876-****-1234",
    accountType: "CMA",
    balance: 1000000,
    currency: "KRW",
    selected: false,
  },
];

describe("GoalAccountSelector", () => {
  it("shows available account details and the saved account", () => {
    const wrapper = mount(GoalAccountSelector, {
      props: { accounts },
    });

    expect(wrapper.text()).toContain("Wallo Bank");
    expect(wrapper.text()).toContain("생활비 통장 · 1234-****-7890");
    expect(wrapper.text()).toContain("2,500,000원");
    expect(wrapper.text()).toContain("CMA");
    expect(wrapper.findAll('input[type="radio"]')).toHaveLength(2);
    expect(wrapper.findAll(".goal-account-option-selected")).toHaveLength(1);
  });

  it("emits only a changed account id", async () => {
    const wrapper = mount(GoalAccountSelector, {
      props: { accounts },
    });

    await wrapper.findAll('input[type="radio"]')[1].setValue();
    await wrapper.find("button.btn-primary").trigger("click");

    expect(wrapper.emitted("select-account")).toEqual([[102]]);
  });

  it("shows the loading and error states", async () => {
    const loadingWrapper = mount(GoalAccountSelector, {
      props: { loading: true },
    });
    expect(loadingWrapper.text()).toContain("사용 가능한 계좌를 불러오는 중입니다.");

    const errorWrapper = mount(GoalAccountSelector, {
      props: { error: "계좌 조회 실패" },
    });
    expect(errorWrapper.text()).toContain("계좌 조회 실패");

    await errorWrapper.find("button").trigger("click");
    expect(errorWrapper.emitted("retry")).toHaveLength(1);
  });
});
