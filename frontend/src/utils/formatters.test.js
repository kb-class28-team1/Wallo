import { describe, expect, it } from "vitest"

import { formatRoadmapText } from "./formatters"

describe("formatRoadmapText", () => {
  it("converts million shorthand to Korean amount units", () => {
    expect(formatRoadmapText("현재 3M에 1M 추가")).toBe(
      "현재 3백만원에 1백만원 추가",
    )
    expect(formatRoadmapText("3M원과 1.5m을 비교")).toBe(
      "3백만원과 1.5백만원을 비교",
    )
  })

  it("does not alter unrelated words containing M", () => {
    expect(formatRoadmapText("MVP 미션과 3M 목표")).toBe(
      "MVP 미션과 3백만원 목표",
    )
  })

  it("preserves non-string values", () => {
    expect(formatRoadmapText(null)).toBeNull()
    expect(formatRoadmapText(undefined)).toBeUndefined()
  })
})
