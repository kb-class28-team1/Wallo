/**
 * 용어 설명 패널에 표시할 텍스트를 결정한다. shortDefinition이 있을 때만 그 값을 그대로
 * 반환하고(생성 시점에 이미 100자 이내 2문장으로 검증됨), 없으면 빈 문자열을 반환한다.
 *
 * financial_term.description(원문)은 여기서 전혀 사용하지 않는다 — "잘린 설명"이나 "준비 중"
 * 같은 fallback 문구도 두지 않는다.
 */
export function getDisplayDefinition(term) {
  return term?.shortDefinition || ""
}

/**
 * 패널이 용어 정보를 보여줘야 하는지(true) 아니면 기본 안내 상태로 처리해야 하는지(false)를
 * 결정한다. term이 없거나 shortDefinition이 없으면(빈 문자열 포함) "선택된 용어 없음"과
 * 동일하게 취급한다 — "쉬운 설명 준비 중" 같은 fallback 문구는 절대 보여주지 않는다.
 */
export function hasDisplayableTerm(term) {
  return Boolean(term && getDisplayDefinition(term))
}
