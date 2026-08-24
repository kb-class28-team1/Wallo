const PARENTHESIZED_STRONG_PATTERN = /(\*\*|__)([^\r\n]*?\))\1(?=\S|$)/gu

/**
 * marked가 닫는 강조 기호 앞의 괄호와 뒤의 한글이 붙은 경우를
 * strong 토큰으로 인식하지 못하는 케이스를 보정한다.
 */
export const repairParenthesizedStrongEmphasis = (value) => {
  if (typeof value !== "string" || !value) return value || ""

  return value.replace(
    PARENTHESIZED_STRONG_PATTERN,
    (_, _delimiter, content) => `<strong>${content}</strong>`,
  )
}
