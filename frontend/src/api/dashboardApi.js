import httpClient from "@/api/httpClient";

const MOCK_DELAY = 500;

const mockAssetsResponse = {
  success: true,
  data: {
    totalAssets: 39900000,
    previousMonthTotalAssets: 38400000,
    accounts: [
      {
        bank: "국민은행",
        balance: 10000000,
      },
    ],
    cards: [
      {
        company: "신한카드",
        billedAmount: 500000,
      },
    ],
    stocks: [
      {
        company: "키움증권",
        evalAmount: 14500000,
      },
      {
        company: "미래에셋증권",
        evalAmount: 10400000,
      },
    ],
    assetCategoryBreakdown: [
      {
        category: "DEPOSIT",
        amount: 10000000,
      },
      {
        category: "SAVINGS",
        amount: 5000000,
      },
      {
        category: "STOCK",
        amount: 24900000,
      },
    ],
  },
  error: null,
};

const mockBudgetResponse = {
  success: true,
  data: {
    targetMonth: "2026-07",
    totalAmount: 500000,
    spentAmount: 350000,
  },
  error: null,
};

const mockExpensesResponse = {
  success: true,
  data: {
    totalExpense: 155000,
    totalIncome: 3000000,
    expenseCategoryBreakdown: [
      {
        category: "FOOD",
        amount: 130000,
      },
      {
        category: "TRANSPORT",
        amount: 25000,
      },
    ],
    transactions: [
      {
        date: "2026-07-20",
        type: "EXPENSE",
        category: "FOOD",
        amount: 15000,
        merchantName: "스타벅스",
      },
      {
        date: "2026-07-21",
        type: "EXPENSE",
        category: "TRANSPORT",
        amount: 1400,
        merchantName: "지하철",
      },
    ],
  },
  error: null,
};

const resolveMockResponse = async (response) => {
  await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY));

  return Promise.resolve({ data: response });
};

export const getAssets = async () => {
  // return httpClient.get("/api/assets");
  return resolveMockResponse(mockAssetsResponse);
};

export const getBudgets = async () => {
  // return httpClient.get("/api/budgets");
  return resolveMockResponse(mockBudgetResponse);
};

export const getExpenses = async (startDate, endDate) => {
  // return httpClient.get("/api/assets/expense", { params: { startDate, endDate } });
  return resolveMockResponse(mockExpensesResponse);
};
