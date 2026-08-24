import {
  getExpenseCategoryLabel,
  normalizeExpenseCategory,
} from "@/features/financial/financialCategories.js";

export const CONSUMPTION_REPORT_FALLBACK_IMAGE =
  "/images/asset-reports/questionMark.svg";

const CONSUMPTION_REPORT_IMAGE_PATHS = Object.freeze({
  FOOD: "/images/asset-reports/wallow-food.webp",
  CAFE: "/images/asset-reports/wallow-cafe.webp",
  DELIVERY: "/images/asset-reports/wallow-delivery.webp",
  TRANSPORT: "/images/asset-reports/wallow-transport.webp",
  SHOPPING: "/images/asset-reports/wallow-shopping.svg",
  HOUSING: "/images/asset-reports/wallow-housing.webp",
  LIVING: "/images/asset-reports/wallow-living.svg",
  CULTURE: "/images/asset-reports/wallow-culture.webp",
  HEALTH: "/images/asset-reports/wallow-health.webp",
  EDUCATION: "/images/asset-reports/wallow-education.webp",
});

export const getConsumptionReportImage = (category) => {
  const normalizedCategory = normalizeExpenseCategory(category);

  return (
    CONSUMPTION_REPORT_IMAGE_PATHS[normalizedCategory] ??
    CONSUMPTION_REPORT_FALLBACK_IMAGE
  );
};

export const getConsumptionReportImageAlt = (category) => {
  const normalizedCategory = normalizeExpenseCategory(category);
  const categoryLabel = getExpenseCategoryLabel(normalizedCategory);

  return `${categoryLabel} 소비 리포트 이미지`;
};
