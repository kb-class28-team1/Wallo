export const ASSET_CATEGORY_LABELS = Object.freeze({
  saving_cash: "예적금·현금",
  financial_account: "금융계좌",
  investment: "투자",
  real_estate: "부동산",
  other: "기타",
})

const numberOrNull = (value) => {
  if (value === null || value === undefined || value === "") return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

const textOrNull = (value) => {
  if (value === null || value === undefined) return null
  const text = String(value).trim()
  return text || null
}

const normalizeSummary = (summary) => {
  const source = summary && typeof summary === "object" ? summary : {}
  return {
    totalAssetsKrw: numberOrNull(source.totalAssetsKrw),
    totalDebtKrw: numberOrNull(source.totalDebtKrw),
    netAssetsKrw: numberOrNull(source.netAssetsKrw),
  }
}

const normalizeCashflow = (cashflow) => {
  const source = cashflow && typeof cashflow === "object" ? cashflow : {}
  return {
    monthlyNetIncomeKrw: numberOrNull(source.monthlyNetIncomeKrw),
    monthlySavingKrw: numberOrNull(source.monthlySavingKrw),
    monthlyExpenseKrw: numberOrNull(source.monthlyExpenseKrw),
    monthlySurplusKrw: numberOrNull(source.monthlySurplusKrw),
    annualSavingKrw: numberOrNull(source.annualSavingKrw),
    savingRatePercent: numberOrNull(source.savingRatePercent),
  }
}

const normalizeComposition = (composition) => (
  Array.isArray(composition)
    ? composition.map((item = {}) => ({
      name: textOrNull(item.name),
      category: textOrNull(item.category),
      amountKrw: numberOrNull(item.amountKrw),
      amountMinKrw: numberOrNull(item.amountMinKrw),
      amountMaxKrw: numberOrNull(item.amountMaxKrw),
      sharePercent: numberOrNull(item.sharePercent),
      estimated: Boolean(item.estimated),
    }))
    : []
)

const normalizeTextList = (value) => (
  Array.isArray(value)
    ? value.map(textOrNull).filter(Boolean)
    : []
)

const normalizeDirection = (direction) => {
  if (!direction || typeof direction !== "object") return null

  const normalized = {
    headline: textOrNull(direction.headline),
    currentStage: textOrNull(direction.currentStage),
    reasons: normalizeTextList(direction.reasons),
    keep: textOrNull(direction.keep),
    firstChange: textOrNull(direction.firstChange),
    threeMonthDirection: textOrNull(direction.threeMonthDirection),
    oneYearDirection: textOrNull(direction.oneYearDirection),
    riskSignals: normalizeTextList(direction.riskSignals),
    additionalInfo: normalizeTextList(direction.additionalInfo),
  }

  const hasContent = Object.entries(normalized).some(([key, value]) =>
    Array.isArray(value) ? value.length > 0 : value !== null,
  )
  return hasContent ? normalized : null
}

const normalizePriorityActions = (actions) => (
  Array.isArray(actions)
    ? actions.map((action = {}) => ({
      period: textOrNull(action.period),
      title: textOrNull(action.title),
      description: textOrNull(action.description ?? action.detail),
    })).filter((action) => action.description)
    : []
)

export const normalizeAssetAnalysis = (analysis) => {
  if (!analysis || typeof analysis !== "object") return null

  const normalized = {
    summary: normalizeSummary(analysis.summary),
    cashflow: normalizeCashflow(analysis.cashflow),
    composition: normalizeComposition(analysis.composition),
    dataQualityNotes: Array.isArray(analysis.dataQualityNotes)
      ? analysis.dataQualityNotes.map(textOrNull).filter(Boolean)
      : [],
    direction: normalizeDirection(analysis.direction),
    priorityActions: normalizePriorityActions(analysis.priorityActions),
  }

  const hasSummary = Object.values(normalized.summary).some((value) => value !== null)
  const hasCashflow = Object.values(normalized.cashflow).some((value) => value !== null)
  return hasSummary || hasCashflow
    || normalized.composition.length > 0
    || normalized.dataQualityNotes.length > 0
    || normalized.direction !== null
    || normalized.priorityActions.length > 0
    ? normalized
    : null
}

export const assetCategoryLabel = (category) =>
  ASSET_CATEGORY_LABELS[category] || category || "기타"

export const formatAssetAmount = (value) => {
  const number = numberOrNull(value)
  return number === null
    ? "-"
    : `${Math.round(number).toLocaleString("ko-KR")}원`
}

export const formatAssetAmountRange = (item) => {
  if (item?.amountKrw !== null && item?.amountKrw !== undefined) {
    return formatAssetAmount(item.amountKrw)
  }

  const min = numberOrNull(item?.amountMinKrw)
  const max = numberOrNull(item?.amountMaxKrw)
  if (min === null && max === null) return "-"
  if (min === null) return `${formatAssetAmount(max)} 이하`
  if (max === null) return `${formatAssetAmount(min)} 이상`
  return `${formatAssetAmount(min)} ~ ${formatAssetAmount(max)}`
}
