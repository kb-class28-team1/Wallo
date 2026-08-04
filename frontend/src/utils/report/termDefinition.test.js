import { describe, expect, it } from "vitest"
import { getDisplayDefinition, hasDisplayableTerm } from "./termDefinition"

describe("getDisplayDefinition", () => {
  it("returns an empty string when there is no term", () => {
    expect(getDisplayDefinition(null)).toBe("")
  })

  it("returns shortDefinition as-is when present", () => {
    const term = {
      term: "기준금리",
      shortDefinition: "기준금리는 한국은행이 정하는 대표 금리예요. 이 금리가 오르면 대출 이자도 함께 오를 수 있어요.",
      definition: "한국은행 금융통화위원회에서 결정하는 정책금리를 말한다...(아주 긴 원문)",
    }

    expect(getDisplayDefinition(term)).toBe(term.shortDefinition)
  })

  // shortDefinition이 없으면 definition을 절대 사용하지 않고("잘린 설명"이나 "준비 중" 문구도
  // 없이) 빈 문자열을 반환해야 한다 — 호출 측(TermInfoPanel)이 이를 "선택된 용어 없음"과
  // 동일한 기본 안내 상태로 처리한다.
  it("returns an empty string when shortDefinition is missing, never falling back to definition", () => {
    const term = {
      term: "CEO",
      shortDefinition: null,
      definition: "Chief Executive Officer의 줄임말로 최고경영책임자를 뜻한다. 기업이나 정부 부처 등의 임원 중 가장 높은 위치에서...",
    }

    expect(getDisplayDefinition(term)).toBe("")
  })

  it("returns an empty string when shortDefinition is an empty string", () => {
    const term = { term: "CEO", shortDefinition: "", definition: "원문 정의입니다." }

    expect(getDisplayDefinition(term)).toBe("")
  })
})

// TermInfoPanel.vue가 "용어 설명을 보여줄지, 기본 안내 상태로 둘지"를 결정하는 데 쓰는 로직.
// 컴포넌트를 마운트하지 않고도 이 규칙을 검증할 수 있도록 별도 함수로 분리했다.
describe("hasDisplayableTerm", () => {
  it("returns false when there is no term", () => {
    expect(hasDisplayableTerm(null)).toBe(false)
  })

  it("returns true when the term has a shortDefinition", () => {
    const term = { term: "기준금리", shortDefinition: "기준금리는 한국은행이 정하는 대표 금리예요." }

    expect(hasDisplayableTerm(term)).toBe(true)
  })

  // shortDefinition이 없는 term이 들어와도 "쉬운 설명 준비 중" 같은 fallback을 보여주지 않고,
  // 기본 안내 상태(false)로 처리돼야 한다.
  it("returns false when the term has no shortDefinition, even if definition exists", () => {
    const term = {
      term: "CEO",
      shortDefinition: null,
      definition: "Chief Executive Officer의 줄임말로 최고경영책임자를 뜻한다.",
    }

    expect(hasDisplayableTerm(term)).toBe(false)
  })

  it("returns false when shortDefinition is an empty string", () => {
    const term = { term: "CEO", shortDefinition: "" }

    expect(hasDisplayableTerm(term)).toBe(false)
  })
})
