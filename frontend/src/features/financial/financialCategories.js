const assetCategory = (label, color) => Object.freeze({ label, color });

const expenseCategory = (label, color, icon, colorClass) =>
  Object.freeze({ label, color, icon, colorClass });

export const ASSET_CATEGORY_META = Object.freeze({
  DEPOSIT: assetCategory("입출금", "#8170ff"),
  SAVINGS: assetCategory("예·적금", "#55c2a3"),
  STOCK: assetCategory("투자", "#ffb657"),
  LOAN: assetCategory("대출", "#ff7b86"),
  ETC: assetCategory("기타 자산", "#8d99ae"),
});

export const ASSET_FALLBACK_COLORS = Object.freeze([
  "#5f8cff",
  "#46b8d8",
  "#c47cff",
  "#f28c66",
  "#8d99ae",
]);

export const EXPENSE_CATEGORY_META = Object.freeze({
  FOOD: expenseCategory("식비", "#ff7b6b", "bi-cup-hot", "coral"),
  CAFE: expenseCategory("카페", "#f28c66", "bi-cup-straw", "coral"),
  TRANSPORT: expenseCategory("교통/차량", "#2fc595", "bi-bus-front", "green"),
  SHOPPING: expenseCategory("쇼핑", "#5b8def", "bi-bag", "blue"),
  DELIVERY: expenseCategory("배달", "#ffad66", "bi-fork-knife", "coral"),
  HOUSING: expenseCategory("주거/통신", "#8170ff", "bi-house", "purple"),
  LIVING: expenseCategory("생활", "#46b8d8", "bi-basket", "green"),
  CULTURE: expenseCategory("문화", "#45a7c8", "bi-film", "blue"),
  HEALTH: expenseCategory("건강", "#ef6f91", "bi-heart-pulse", "coral"),
  EDUCATION: expenseCategory("교육", "#f0b44d", "bi-book", "blue"),
  LOAN_REPAYMENT: expenseCategory("대출상환", "#c47cff", "bi-bank", "purple"),
  INCOME: expenseCategory("수입", "#4f73e8", "bi-wallet2", "blue"),
  SEND: expenseCategory("보낸 돈", "#8170ff", "bi-arrow-up-right", "purple"),
  ETC: expenseCategory("기타", "#a0a6b5", "bi-receipt", "gray"),
});

export const BUDGET_CATEGORY_CODES = Object.freeze([
  "FOOD",
  "CAFE",
  "TRANSPORT",
  "SHOPPING",
  "DELIVERY",
  "HOUSING",
  "LIVING",
  "CULTURE",
  "HEALTH",
  "EDUCATION",
  "LOAN_REPAYMENT",
]);

// 인증 글에서 사용할 지출 카테고리 코드
export const FEED_CATEGORY_CODES = Object.freeze([
  "FOOD",
  "CAFE",
  "TRANSPORT",
  "SHOPPING",
  "DELIVERY",
  "HOUSING",
  "LIVING",
  "CULTURE",
  "HEALTH",
  "ETC",
]);

export const getAssetCategoryMeta = (value) => {
  const categoryCode = String(value || "ETC").trim().toUpperCase();
  return ASSET_CATEGORY_META[categoryCode];
};

export const normalizeExpenseCategory = (value) => {
  const normalized = String(value || "ETC").trim().toUpperCase();
  const categoryCode = normalized === "OTHER" ? "ETC" : normalized;

  return EXPENSE_CATEGORY_META[categoryCode] ? categoryCode : "ETC";
};

export const getExpenseCategoryMeta = (value) =>
  EXPENSE_CATEGORY_META[normalizeExpenseCategory(value)];

export const getExpenseCategoryLabel = (value) => getExpenseCategoryMeta(value).label;
