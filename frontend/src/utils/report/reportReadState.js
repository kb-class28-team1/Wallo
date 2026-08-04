const STORAGE_KEY = "wallo.readReportIds"

const normalizeReportId = (reportId) => String(reportId ?? "")

export const getReadReportIds = () => {
  if (typeof window === "undefined" || !window.localStorage) return new Set()

  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || "[]")
    return new Set(Array.isArray(parsed) ? parsed.map(normalizeReportId).filter(Boolean) : [])
  } catch {
    return new Set()
  }
}

export const isReportRead = (reportId) => getReadReportIds().has(normalizeReportId(reportId))

export const markReportAsRead = (reportId) => {
  const normalizedId = normalizeReportId(reportId)
  if (!normalizedId || typeof window === "undefined" || !window.localStorage) return

  const readIds = getReadReportIds()
  readIds.add(normalizedId)
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify([...readIds]))
}
