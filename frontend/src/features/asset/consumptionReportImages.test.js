import { describe, expect, it } from "vitest";
import {
  CONSUMPTION_REPORT_FALLBACK_IMAGE,
  getConsumptionReportImage,
} from "./consumptionReportImages.js";

describe("consumptionReportImages", () => {
  it("returns the category image for a supported category", () => {
    expect(getConsumptionReportImage("DELIVERY")).toBe(
      "/images/asset-reports/wallow-delivery.png",
    );
  });

  it("returns the fallback image for unsupported categories", () => {
    expect(getConsumptionReportImage("UNKNOWN_CATEGORY")).toBe(
      CONSUMPTION_REPORT_FALLBACK_IMAGE,
    );
    expect(getConsumptionReportImage("ETC")).toBe(
      CONSUMPTION_REPORT_FALLBACK_IMAGE,
    );
    expect(getConsumptionReportImage(null)).toBe(
      CONSUMPTION_REPORT_FALLBACK_IMAGE,
    );
  });
});
