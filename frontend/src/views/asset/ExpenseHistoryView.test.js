import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import ExpenseHistoryView from "./ExpenseHistoryView.vue";
import { getExpenses } from "@/api/assetApi";
import { useAssetStore } from "@/stores/assetStore";

vi.mock("@/api/assetApi", () => ({
  getExpenses: vi.fn(),
  getCategoryBudgets: vi.fn(),
}));

vi.mock("@/stores/assetStore", () => ({
  useAssetStore: vi.fn(),
}));

vi.mock("@/stores/budgetStore", () => ({
  useBudgetStore: vi.fn(),
}));

vi.mock("vue-router", () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: vi.fn() }),
}));

import { getCategoryBudgets } from "@/api/assetApi";
import { useBudgetStore } from "@/stores/budgetStore";

const createExpenseResponse = () => ({
  success: true,
  data: {
    totalExpense: 120_000,
    totalIncome: 3_000_000,
    expenseCategoryBreakdown: [],
    dailyBreakdown: [{ date: "2026-08-11", amount: 120_000 }],
    transactions: [{ id: 1, type: "EXPENSE", amount: 120_000 }],
    pagination: {
      currentPage: 0,
      totalPages: 1,
      totalElements: 1,
      hasNext: false,
    },
  },
});

const createStore = () => ({
  isSyncing: ref(false),
  syncError: ref(null),
  syncAssets: vi.fn(),
});

const createBudgetStore = () => ({
  categorySummary: ref({
    targetMonth: "2026-08",
    totalAmount: 1_000_000,
    allocatedAmount: 300_000,
    unallocatedAmount: 700_000,
    spentAmount: 120_000,
    remainingAmount: 880_000,
    usageRate: 12,
    overBudget: false,
    categories: [],
  }),
  isLoading: ref(false),
  isSaving: ref(false),
  error: ref(null),
  fetchCategoryBudgets: vi.fn().mockResolvedValue(null),
  saveCategoryBudgets: vi.fn().mockResolvedValue(null),
});

const globalStubs = {
  RouterLink: { template: "<a><slot /></a>" },
  ExpenseCalendar: {
    template: '<button data-testid="select-date" @click="$emit(\'select-date\', \'2026-08-11\')">calendar</button>',
  },
  ExpenseTransactionList: { template: '<div data-testid="transaction-list" />' },
  ExpenseCategoryBreakdown: { template: '<div data-testid="category-breakdown" />' },
};

describe("ExpenseHistoryView manual synchronization", () => {
  let wrapper;
  let store;
  let budgetStore;

  beforeEach(async () => {
    getExpenses.mockResolvedValue(createExpenseResponse());
    getCategoryBudgets.mockResolvedValue({ success: true, data: {} });
    store = createStore();
    budgetStore = createBudgetStore();
    store.syncAssets.mockResolvedValue({
      syncedAt: "2026-08-12T10:00:00",
      inserted: 3,
      updated: 42,
      failedConnections: 0,
    });
    useAssetStore.mockReturnValue(store);
    useBudgetStore.mockReturnValue(budgetStore);
    wrapper = mount(ExpenseHistoryView, { global: { stubs: globalStubs } });
    await flushPromises();
  });

  afterEach(() => {
    wrapper?.unmount();
    vi.clearAllMocks();
  });

  it("syncs assets and reloads the selected month from the first page", async () => {
    await wrapper.get(".expense-sync-button").trigger("click");
    await flushPromises();

    expect(store.syncAssets).toHaveBeenCalledOnce();
    expect(getExpenses).toHaveBeenCalledTimes(2);
    expect(getExpenses.mock.calls[1][0]).toMatchObject({ page: 0, size: 20 });
    expect(wrapper.text()).toContain("동기화가 완료되었습니다");
    expect(wrapper.text()).toContain("신규 3건, 수정 42건");
  });

  it("closes an open daily modal before refreshing the month", async () => {
    await wrapper.get('[data-testid="select-date"]').trigger("click");
    await flushPromises();
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true);

    await wrapper.get(".expense-sync-button").trigger("click");
    await flushPromises();

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false);
    expect(getExpenses).toHaveBeenCalledTimes(3);
  });

  it("disables the button while synchronization is in progress", async () => {
    let finishSync;
    store.syncAssets.mockImplementation(() => {
      store.isSyncing.value = true;
      return new Promise((resolve) => {
        finishSync = (result) => {
          store.isSyncing.value = false;
          resolve(result);
        };
      });
    });

    const syncButton = wrapper.get(".expense-sync-button");
    const request = syncButton.trigger("click");
    await flushPromises();

    expect(syncButton.element.disabled).toBe(true);
    expect(syncButton.text()).toContain("동기화 중");

    finishSync({
      syncedAt: "2026-08-12T10:00:00",
      inserted: 0,
      updated: 1,
      failedConnections: 0,
    });
    await request;
    await flushPromises();

    expect(syncButton.element.disabled).toBe(false);
  });

  it("shows partial failures while keeping the refreshed expense data", async () => {
    store.syncAssets.mockResolvedValue({
      syncedAt: "2026-08-12T10:00:00",
      inserted: 1,
      updated: 2,
      failedConnections: 1,
    });

    await wrapper.get(".expense-sync-button").trigger("click");
    await flushPromises();

    expect(getExpenses).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain("실패한 연결기관 1건");
  });

  it("shows the synchronization error without reloading expenses after failure", async () => {
    store.syncAssets.mockRejectedValue(new Error("CODEF unavailable"));
    store.syncError.value = "CODEF unavailable";

    await wrapper.get(".expense-sync-button").trigger("click");
    await flushPromises();

    expect(getExpenses).toHaveBeenCalledOnce();
    expect(wrapper.text()).toContain("CODEF unavailable");
  });

  it("applies the selected category only to list requests", async () => {
    await wrapper.get(".view-toggle .btn:nth-child(2)").trigger("click");
    await wrapper.get("#expenseCategoryFilter").setValue("FOOD");
    await flushPromises();

    expect(getExpenses.mock.calls[1][0]).toMatchObject({
      page: 0,
      size: 20,
      category: "FOOD",
    });

    await wrapper.get(".view-toggle .btn:nth-child(1)").trigger("click");
    await wrapper.get('[data-testid="select-date"]').trigger("click");
    await flushPromises();

    expect(getExpenses.mock.calls[2][0]).not.toHaveProperty("category");
  });
});
