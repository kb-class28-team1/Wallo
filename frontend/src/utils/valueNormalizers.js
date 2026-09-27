export const numberOrNull = (value) => {
  if (value === null || value === undefined || value === "") return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

export const textOrNull = (value) => {
  if (value === null || value === undefined) return null
  const text = String(value).trim()
  return text || null
}
