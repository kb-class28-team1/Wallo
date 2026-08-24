const configuredDemoDate = String(import.meta.env.VITE_DEMO_DATE ?? "").trim()
const demoDateParts = configuredDemoDate.match(/^(\d{4})-(\d{2})-(\d{2})$/)

const getConfiguredDemoDate = () => {
  if (!demoDateParts) return null

  const [, year, month, day] = demoDateParts
  const date = new Date(Number(year), Number(month) - 1, Number(day), 12)
  if (
    date.getFullYear() !== Number(year) ||
    date.getMonth() !== Number(month) - 1 ||
    date.getDate() !== Number(day)
  ) {
    return null
  }

  return date
}

export const getAppToday = () => getConfiguredDemoDate() ?? new Date()

export const getAppMonthKey = () => {
  const today = getAppToday()
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`
}

export const getAppYear = () => getAppToday().getFullYear()
