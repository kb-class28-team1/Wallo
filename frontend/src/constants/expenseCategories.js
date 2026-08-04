const category = (label, color, icon, colorClass) =>
  Object.freeze({ label, color, icon, colorClass });

export const EXPENSE_CATEGORY_META = Object.freeze({
  FOOD: category("식비", "#ff7b6b", "bi-cup-hot", "coral"),
  CAFE: category("카페", "#f28c66", "bi-cup-straw", "coral"),
  TRANSPORT: category("교통/차량", "#2fc595", "bi-bus-front", "green"),
  SHOPPING: category("쇼핑", "#5b8def", "bi-bag", "blue"),
  DELIVERY: category("배달", "#ffad66", "bi-fork-knife", "coral"),
  HOUSING: category("주거/통신", "#8170ff", "bi-house", "purple"),
  LIVING: category("생활", "#46b8d8", "bi-basket", "green"),
  CULTURE: category("문화", "#45a7c8", "bi-film", "blue"),
  HEALTH: category("건강", "#ef6f91", "bi-heart-pulse", "coral"),
  EDUCATION: category("교육", "#f0b44d", "bi-book", "blue"),
  LOAN_REPAYMENT: category("대출상환", "#c47cff", "bi-bank", "purple"),
  INCOME: category("수입", "#4f73e8", "bi-wallet2", "blue"),
  SEND: category("보낸 돈", "#8170ff", "bi-arrow-up-right", "purple"),
  ETC: category("기타", "#a0a6b5", "bi-receipt", "gray"),
});

// 인증 글에서 사용할 지출 카테고리임. 교육·대출상환·수입·보낸 돈은 제외함.
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

export const normalizeExpenseCategory = (value) => {
  const normalized = String(value || "ETC").trim().toUpperCase();
  const categoryCode = normalized === "OTHER" ? "ETC" : normalized;

  return EXPENSE_CATEGORY_META[categoryCode] ? categoryCode : "ETC";
};

export const getExpenseCategoryMeta = (value) =>
  EXPENSE_CATEGORY_META[normalizeExpenseCategory(value)];

export const getExpenseCategoryLabel = (value) => getExpenseCategoryMeta(value).label;
