import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import GoalSummaryCard from "./GoalSummaryCard.vue";

const globalOptions = {
  stubs: {
    RouterLink: {
      template: "<a><slot /></a>",
    },
  },
};

describe("GoalSummaryCard", () => {
  it("shows the confirmed goal details", () => {
    const wrapper = mount(GoalSummaryCard, {
      ...globalOptions,
      props: {
        goals: [
          {
            goalId: 1,
            title: "Emergency fund",
            targetAmount: 5000000,
            targetDate: "2027-11-30",
            initialAmount: 3250000,
            currentAmount: 3250000,
            achievementRate: 65,
            requiredMonthlyAmount: 500000,
            status: "ACTIVE",
          },
        ],
      },
    });

    expect(wrapper.text()).toContain("Emergency fund");
    expect(wrapper.find("h2").text()).toBe("Emergency fund");
    expect(wrapper.find("h3").text()).toBe("목표에 사용할 계좌");
    expect(wrapper.text()).not.toContain("확정된 목표");
    expect(wrapper.text()).not.toContain("금융 목표");
    expect(wrapper.text()).toContain("5,000,000원");
    expect(wrapper.text()).toContain("3,250,000원");
    expect(wrapper.text()).toContain("65%");
    expect(wrapper.text()).toContain("목표 설정 당시 준비금 기준");
    expect(wrapper.text()).toContain("월 필요 납입액");
    expect(wrapper.text()).not.toContain("진행 중");
    expect(wrapper.find(".goal-progress-bar").attributes("style")).toContain("width: 65%");
    expect(wrapper.find(".goal-carousel-controls").exists()).toBe(false);
    expect(wrapper.find(".goal-card-body").exists()).toBe(true);
  });

  it("shows one goal at a time and navigates between goals", async () => {
    const wrapper = mount(GoalSummaryCard, {
      ...globalOptions,
      props: {
        goals: [
          {
            goalId: 1,
            title: "Emergency fund",
            targetAmount: 5000000,
            targetDate: "2027-11-30",
            initialAmount: 3250000,
            requiredMonthlyAmount: 500000,
            status: "ACTIVE",
          },
          {
            goalId: 2,
            title: "Travel fund",
            targetAmount: 10000000,
            targetDate: "2028-06-30",
            initialAmount: 1000000,
            requiredMonthlyAmount: 500000,
            status: "ACTIVE",
          },
        ],
      },
    });

    expect(wrapper.findAll(".goal-item")).toHaveLength(1);
    expect(wrapper.text()).toContain("Emergency fund");
    expect(wrapper.text()).not.toContain("Travel fund");
    expect(wrapper.find(".goal-carousel-footer").exists()).toBe(true);
    expect(wrapper.find(".goal-carousel-position").text()).toBe("1 / 2");

    await wrapper.find('button[aria-label="다음 목표"]').trigger("click");

    expect(wrapper.findAll(".goal-item")).toHaveLength(1);
    expect(wrapper.text()).toContain("Travel fund");
    expect(wrapper.text()).not.toContain("Emergency fund");
    expect(wrapper.find(".goal-carousel-position").text()).toBe("2 / 2");

    await wrapper.find('button[aria-label="이전 목표"]').trigger("click");

    expect(wrapper.text()).toContain("Emergency fund");
    expect(wrapper.find(".goal-carousel-position").text()).toBe("1 / 2");
  });

  it("shows eligible account details and emits only the selected account", async () => {
    const wrapper = mount(GoalSummaryCard, {
      ...globalOptions,
      props: {
        goals: [
          {
            goalId: 1,
            title: "Emergency fund",
            targetAmount: 5000000,
            targetDate: "2027-11-30",
            initialAmount: 3250000,
            requiredMonthlyAmount: 500000,
          },
        ],
        availableAccounts: [
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
        ],
      },
    });

    expect(wrapper.text()).toContain("Wallo Bank");
    expect(wrapper.text()).toContain("생활비 통장 · 1234-****-7890");
    expect(wrapper.text()).toContain("2,500,000원");
    expect(wrapper.text()).toContain("CMA");
    expect(wrapper.findAll('input[type="radio"]')).toHaveLength(2);

    await wrapper.findAll('input[type="radio"]')[1].setValue();
    await wrapper.find(".goal-account-selection button.btn-primary").trigger("click");

    expect(wrapper.emitted("select-account")).toEqual([
      [{ goalId: 1, accountId: 102 }],
    ]);
  });

  it("shows the empty state when no goal exists", () => {
    const wrapper = mount(GoalSummaryCard, {
      ...globalOptions,
      props: { goals: [] },
    });

    expect(wrapper.text()).toContain("아직 확정된 금융 목표가 없습니다.");
    expect(wrapper.find(".goal-state").exists()).toBe(true);
  });

  it("shows the error state and emits retry", async () => {
    const wrapper = mount(GoalSummaryCard, {
      ...globalOptions,
      props: { error: "목표 조회 실패" },
    });

    expect(wrapper.text()).toContain("목표 조회 실패");

    await wrapper.find("button").trigger("click");

    expect(wrapper.emitted("retry")).toHaveLength(1);
  });
});
