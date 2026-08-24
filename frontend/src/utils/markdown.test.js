import { describe, expect, it } from "vitest"
import { repairParenthesizedStrongEmphasis } from "./markdown"

describe("markdown helpers", () => {
  it("repairs strong emphasis ending with a parenthesis before a Korean suffix", () => {
    const content = "**예치금(예금 기준) 또는 매월 납입금(적금 기준)**은 어느 정도인지 알려 주세요."

    expect(repairParenthesizedStrongEmphasis(content)).toBe(
      "<strong>예치금(예금 기준) 또는 매월 납입금(적금 기준)</strong>은 어느 정도인지 알려 주세요.",
    )
  })

  it("supports underscore strong emphasis with the same pattern", () => {
    expect(repairParenthesizedStrongEmphasis("__가입 기간(개월)__을 알려 주세요.")).toBe(
      "<strong>가입 기간(개월)</strong>을 알려 주세요.",
    )
  })

  it("leaves regular strong emphasis unchanged", () => {
    const content = "**원하시는 상품 유형**은 예금인지 적금인지 알려 주세요."

    expect(repairParenthesizedStrongEmphasis(content)).toBe(content)
  })
})
