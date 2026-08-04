// 한글 완성형 음절 범위(가~힣). 백엔드 FinancialTermMatchingServiceImpl.isHangulSyllable과 동일 기준.
const HANGUL_SYLLABLE_START = 0xac00
const HANGUL_SYLLABLE_END = 0xd7a3

function isHangulSyllable(char) {
  if (!char) return false
  const code = char.codePointAt(0)
  return code >= HANGUL_SYLLABLE_START && code <= HANGUL_SYLLABLE_END
}

function isAsciiAlnum(char) {
  if (!char) return false
  return /[A-Za-z0-9]/.test(char)
}

/**
 * 용어가 다른 단어/고유명사의 일부로 파묻혀 있는지 확인한다("칠리스" 안의 "리스", "MCEO" 안의 "CEO").
 *
 * - 한글로 시작하는 용어: 바로 앞이 한글이면 다른 단어에 파묻힌 것으로 보고 거부한다. 뒤쪽은
 *   검사하지 않는다 — 한국어 조사가 명사에 공백 없이 바로 붙기 때문에("금리가", "칠리스는") 뒤쪽까지
 *   막으면 사실상 아무 것도 매칭되지 않고, "금리"가 "금리인상" 앞부분에서 매칭되는 의도된 동작도 깨진다.
 * - 영문·숫자로 시작/끝나는 용어(CEO, CIO 등): 표준 단어 경계처럼 앞뒤 모두 다른 영문·숫자가
 *   아니어야 한다. 한글이 바로 붙는 것은 정상적인 한국어-영어 혼용 표기라 막지 않는다("CEO와", "CIO는").
 */
function isEmbeddedInAnotherWord(text, start, end, term) {
  const precedingChar = start > 0 ? text[start - 1] : ""
  const followingChar = end < text.length ? text[end] : ""
  const firstChar = term[0]
  const lastChar = term[term.length - 1]

  if (isHangulSyllable(firstChar) && isHangulSyllable(precedingChar)) {
    return true
  }
  if (isAsciiAlnum(firstChar) && isAsciiAlnum(precedingChar)) {
    return true
  }
  if (isAsciiAlnum(lastChar) && isAsciiAlnum(followingChar)) {
    return true
  }

  return false
}

/**
 * 리포트 섹션 본문에서 매칭된 금융용어를 찾아, 밑줄/클릭 가능한 조각과 일반 텍스트 조각으로 나눈다.
 * 매칭은 news_term(제목+본문 기준)에서 이미 확정된 report.terms의 term 문자열을 정확히 그대로
 * 찾는 방식이라, AI가 새로 생성한 문장에는 등장하지 않는 용어도 있을 수 있다.
 *
 * @param {string} text 하이라이트를 적용할 섹션 텍스트
 * @param {Array} terms report.terms(매칭된 금융용어 목록)
 * @param {Set<number>} [usedTermIds] 상세 페이지 전체에서 이미 강조 표시한 termId 집합. 전달하면
 *   이미 등장한 용어는 건너뛰고, 이번에 새로 강조한 용어의 termId를 이 Set에 추가한다(호출자가
 *   여러 섹션에 걸쳐 같은 Set을 재사용해 "전체 페이지 기준 첫 등장 1회"를 구현하는 방식).
 */
export function buildTermSegments(text, terms, usedTermIds = new Set()) {
  if (!text) return []
  if (!terms || terms.length === 0) return [{ type: "text", content: text }]

  // shortDefinition이 없는 용어는 밑줄/hover/click 대상으로 삼지 않는다 — 클릭해도 보여줄 짧은
  // 설명이 없으면 사용자 입장에서 의미 없는 강조이기 때문이다(패널에도 "준비 중" 문구를 더 이상
  // 띄우지 않기로 했다). 긴 용어부터 먼저 찾아 자리를 선점해, 짧은 용어가 긴 용어 내부를 잘못
  // 잘라내지 않게 한다.
  const sortedTerms = [...terms]
    .filter((term) => term.term && term.shortDefinition && !usedTermIds.has(term.termId))
    .sort((a, b) => b.term.length - a.term.length)

  const matches = []
  const matchedTermIdsInThisCall = new Set()
  for (const term of sortedTerms) {
    let searchFrom = 0
    while (searchFrom <= text.length) {
      const start = text.indexOf(term.term, searchFrom)
      if (start === -1) break

      const end = start + term.term.length

      if (!isEmbeddedInAnotherWord(text, start, end, term.term)) {
        const overlapsExisting = matches.some((m) => start < m.end && m.start < end)
        if (!overlapsExisting) {
          matches.push({ start, end, term })
          matchedTermIdsInThisCall.add(term.termId)
          // 같은 섹션 안에서도 같은 용어는 첫 등장만 강조하고, 이후 등장은 그대로 일반 텍스트로 둔다.
          break
        }
      }
      searchFrom = start + 1
    }
  }

  for (const termId of matchedTermIdsInThisCall) {
    usedTermIds.add(termId)
  }

  matches.sort((a, b) => a.start - b.start)

  const segments = []
  let cursor = 0
  for (const match of matches) {
    if (match.start > cursor) {
      segments.push({ type: "text", content: text.slice(cursor, match.start) })
    }
    segments.push({ type: "term", content: text.slice(match.start, match.end), term: match.term })
    cursor = match.end
  }
  if (cursor < text.length) {
    segments.push({ type: "text", content: text.slice(cursor) })
  }

  return segments
}
