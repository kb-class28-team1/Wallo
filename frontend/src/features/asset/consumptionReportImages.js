import {
  getExpenseCategoryLabel,
  normalizeExpenseCategory,
} from "@/features/financial/financialCategories.js";

const CONSUMPTION_REPORT_IMAGE_PATHS = Object.freeze({
  CAFE: "/images/asset-reports/wallow-cafe.png",
  DELIVERY: "/images/asset-reports/wallow-delivery.png",
});

export const CONSUMPTION_REPORT_FALLBACK_IMAGE =
  "/images/asset-reports/questionMark.svg";

export const getConsumptionReportImage = (category) => {
  const normalizedCategory = normalizeExpenseCategory(category);

  return CONSUMPTION_REPORT_IMAGE_PATHS[normalizedCategory] ?? "";
};

export const getConsumptionReportImageAlt = (category) => {
  const normalizedCategory = normalizeExpenseCategory(category);
  const categoryLabel = getExpenseCategoryLabel(normalizedCategory);

  return `${categoryLabel} 소비 리포트 이미지`;
};
