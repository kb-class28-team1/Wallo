const STORAGE_KEY_PREFIX = "wallo.readReportIds"

const normalizeReportId = (reportId) => String(reportId ?? "")
const normalizeUserId = (userId) => String(userId ?? "").trim()

const getStorageKey = (userId) => {
  const normalizedUserId = normalizeUserId(userId)
  return normalizedUserId ? `${STORAGE_KEY_PREFIX}:${normalizedUserId}` : null
}

export const getReadReportIds = (userId) => {
  const storageKey = getStorageKey(userId)
  if (!storageKey || typeof window === "undefined" || !window.localStorage) return new Set()

  try {
    const parsed = JSON.parse(window.localStorage.getItem(storageKey) || "[]")
    return new Set(Array.isArray(parsed) ? parsed.map(normalizeReportId).filter(Boolean) : [])
  } catch {
    return new Set()
  }
}

export const isReportRead = (reportId, userId) =>
  getReadReportIds(userId).has(normalizeReportId(reportId))

export const markReportAsRead = (reportId, userId) => {
  const normalizedId = normalizeReportId(reportId)
  const storageKey = getStorageKey(userId)
  if (!normalizedId || !storageKey || typeof window === "undefined" || !window.localStorage) return

  const readIds = getReadReportIds(userId)
  readIds.add(normalizedId)
  window.localStorage.setItem(storageKey, JSON.stringify([...readIds]))
}
