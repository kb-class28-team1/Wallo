import {
  getExpenseCategoryLabel,
  normalizeExpenseCategory,
} from "@/features/financial/financialCategories.js";

const CONSUMPTION_REPORT_IMAGE_PATHS = Object.freeze({
  FOOD: "/images/asset-reports/wallow-food.png",
  CAFE: "/images/asset-reports/wallow-cafe.png",
  DELIVERY: "/images/asset-reports/wallow-delivery.png",
  TRANSPORT: "/images/asset-reports/wallow-transport.png",
  SHOPPING: "/images/asset-reports/wallow-shopping.svg",
  HOUSING: "/images/asset-reports/wallow-housing.png",
  LIVING: "/images/asset-reports/wallow-living.svg",
  CULTURE: "/images/asset-reports/wallow-culture.png",
  HEALTH: "/images/asset-reports/wallow-health.png",
  EDUCATION: "/images/asset-reports/wallow-education.png",
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
