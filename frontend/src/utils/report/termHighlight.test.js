import { describe, expect, it } from "vitest"
import { buildTermSegments } from "./termHighlight"

// shortDefinition이 있어야 하이라이트 대상이 되므로, 다른 동작(경계 규칙/dedup 등)을 검증하는
// 테스트는 기본값으로 shortDefinition을 채워 넣는다. shortDefinition 자체를 검증하는 테스트는
// 세 번째 인자로 null/빈 문자열을 명시적으로 넘긴다.
function term(termId, name, shortDefinition = "짧은 설명입니다.") {
  return { termId, term: name, shortDefinition }
}

describe("buildTermSegments", () => {
  it("returns a single text segment when there is no content", () => {
    expect(buildTermSegments("", [term(1, "금리")])).toEqual([])
  })

  it("returns a single text segment when there are no terms", () => {
    const segments = buildTermSegments("아무 용어도 없는 문장입니다.", [])

    expect(segments).toEqual([{ type: "text", content: "아무 용어도 없는 문장입니다." }])
  })

  it("wraps a matched term in the middle of the text", () => {
    const segments = buildTermSegments("한국은행이 기준금리를 발표했다.", [term(1, "기준금리")])

    expect(segments).toEqual([
      { type: "text", content: "한국은행이 " },
      { type: "term", content: "기준금리", term: term(1, "기준금리") },
      { type: "text", content: "를 발표했다." },
    ])
  })

  // 실사례 회귀 테스트: "칠리스"(외래어를 한글로 음역한 고유명사) 안에서 "리스"가 우연히
  // 매칭되던 실제 버그. 백엔드 FinancialTermMatchingServiceImpl과 동일한 규칙으로 막는다.
  it("does not highlight a short hangul term embedded inside a transliterated proper noun", () => {
    const segments = buildTermSegments("칠리스는 서빙 로봇을 매장에서 철수시켰다.", [term(1, "리스")])

    expect(segments).toEqual([{ type: "text", content: "칠리스는 서빙 로봇을 매장에서 철수시켰다." }])
  })

  // 위 회귀 테스트의 대조군: 앞에 공백이 있어 독립된 단어로 등장하면 정상적으로 하이라이트돼야 한다.
  it("still highlights a short hangul term when preceded by a space", () => {
    const segments = buildTermSegments("회사는 장비를 리스로 조달했다.", [term(1, "리스")])

    expect(segments).toEqual([
      { type: "text", content: "회사는 장비를 " },
      { type: "term", content: "리스", term: term(1, "리스") },
      { type: "text", content: "로 조달했다." },
    ])
  })

  it("does not restrict the boundary after the match (금리 inside 금리인상 still matches)", () => {
    const segments = buildTermSegments("금리인상이 예상된다.", [term(1, "금리")])

    expect(segments).toEqual([
      { type: "term", content: "금리", term: term(1, "금리") },
      { type: "text", content: "인상이 예상된다." },
    ])
  })

  it("prefers the longer overlapping term and skips the shorter one", () => {
    const segments = buildTermSegments("기준금리를 발표했다.", [term(1, "금리"), term(2, "기준금리")])

    expect(segments).toEqual([
      { type: "term", content: "기준금리", term: term(2, "기준금리") },
      { type: "text", content: "를 발표했다." },
    ])
  })

  it("still matches a shorter term independently at a non-overlapping position", () => {
    const segments = buildTermSegments("기준금리가 오르자 시중 금리도 함께 올랐다.", [
      term(1, "금리"),
      term(2, "기준금리"),
    ])

    expect(segments).toEqual([
      { type: "term", content: "기준금리", term: term(2, "기준금리") },
      { type: "text", content: "가 오르자 시중 " },
      { type: "term", content: "금리", term: term(1, "금리") },
      { type: "text", content: "도 함께 올랐다." },
    ])
  })

  it("matches multiple non-overlapping terms in order", () => {
    const segments = buildTermSegments("환율과 금리가 함께 발표됐다.", [term(1, "환율"), term(2, "금리")])

    expect(segments).toEqual([
      { type: "term", content: "환율", term: term(1, "환율") },
      { type: "text", content: "과 " },
      { type: "term", content: "금리", term: term(2, "금리") },
      { type: "text", content: "가 함께 발표됐다." },
    ])
  })

  it("does not restrict ascii terms by the hangul boundary rule (CEO는/CEO가 같은 조사 결합)", () => {
    const segments = buildTermSegments("최근 CEO가 발표했다.", [term(1, "CEO")])

    expect(segments).toEqual([
      { type: "text", content: "최근 " },
      { type: "term", content: "CEO", term: term(1, "CEO") },
      { type: "text", content: "가 발표했다." },
    ])
  })

  // 영문 약어가 다른 영문 단어의 일부로 파묻힌 경우(앞) 매칭하지 않아야 한다.
  it("does not highlight an ascii term embedded right after another ascii character", () => {
    const segments = buildTermSegments("신임 XCEO가 취임했다.", [term(1, "CEO")])

    expect(segments).toEqual([{ type: "text", content: "신임 XCEO가 취임했다." }])
  })

  // 영문 약어가 다른 영문 단어의 일부로 파묻힌 경우(뒤) 매칭하지 않아야 한다.
  it("does not highlight an ascii term embedded right before another ascii character", () => {
    const segments = buildTermSegments("CEOX가 발표했다.", [term(1, "CEO")])

    expect(segments).toEqual([{ type: "text", content: "CEOX가 발표했다." }])
  })

  // 상세 페이지 전체 기준 첫 등장 1회 규칙: 같은 섹션 안에서 같은 용어가 여러 번 나와도 첫 등장만
  // 강조하고, 이후 등장은 일반 텍스트로 둔다.
  it("highlights only the first occurrence when the same term appears multiple times in one call", () => {
    const segments = buildTermSegments("기준금리가 발표됐다. 이번 기준금리 인상은 예상된 결과다.", [
      term(1, "기준금리"),
    ])

    const highlighted = segments.filter((s) => s.type === "term")
    expect(highlighted).toHaveLength(1)
    expect(highlighted[0].content).toBe("기준금리")
  })

  // usedTermIds를 공유하면(ReportDetailView가 여러 섹션에 걸쳐 재사용하는 방식) 이미 강조한
  // 용어는 다음 호출에서 아예 대상에서 빠져, "상세 페이지 전체 기준 첫 등장 1회"가 섹션을
  // 넘나들어도 유지된다.
  it("skips terms already present in a shared usedTermIds set across multiple calls", () => {
    const terms = [term(1, "기준금리")]
    const usedTermIds = new Set()

    const firstSectionSegments = buildTermSegments("기준금리가 발표됐다.", terms, usedTermIds)
    const secondSectionSegments = buildTermSegments("기준금리 인상 여부가 주목된다.", terms, usedTermIds)

    expect(firstSectionSegments.filter((s) => s.type === "term")).toHaveLength(1)
    expect(secondSectionSegments.filter((s) => s.type === "term")).toHaveLength(0)
    expect(secondSectionSegments).toEqual([{ type: "text", content: "기준금리 인상 여부가 주목된다." }])
    expect(usedTermIds.has(1)).toBe(true)
  })

  // shortDefinition이 없는 용어는 클릭해도 보여줄 짧은 설명이 없으므로 애초에 하이라이트하지 않는다.
  it("does not highlight a term that has no shortDefinition", () => {
    const segments = buildTermSegments("한국은행이 기준금리를 발표했다.", [term(1, "기준금리", null)])

    expect(segments).toEqual([{ type: "text", content: "한국은행이 기준금리를 발표했다." }])
  })

  it("does not highlight a term whose shortDefinition is an empty string", () => {
    const segments = buildTermSegments("한국은행이 기준금리를 발표했다.", [term(1, "기준금리", "")])

    expect(segments).toEqual([{ type: "text", content: "한국은행이 기준금리를 발표했다." }])
  })

  // shortDefinition이 있는 용어만 하이라이트되고, 없는 용어는 같은 텍스트 안에서도 그냥 지나쳐야 한다.
  it("highlights only the terms that have a shortDefinition, leaving others as plain text", () => {
    const segments = buildTermSegments("환율과 금리가 함께 발표됐다.", [
      term(1, "환율", "환율은 두 나라 돈의 교환 비율이에요. 오르면 해외여행 비용이 늘어날 수 있어요."),
      term(2, "금리", null),
    ])

    expect(segments).toEqual([
      { type: "term", content: "환율", term: term(1, "환율", "환율은 두 나라 돈의 교환 비율이에요. 오르면 해외여행 비용이 늘어날 수 있어요.") },
      { type: "text", content: "과 금리가 함께 발표됐다." },
    ])
  })
})
