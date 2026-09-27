import { numberOrNull, textOrNull } from "@/utils/valueNormalizers"

const MAX_PRODUCT_COUNT = 3

const objectOrEmpty = (value) => (
  value && typeof value === "object" && !Array.isArray(value) ? value : {}
)

const normalizeSearchSummary = (summary) => {
  const source = objectOrEmpty(summary)
  return {
    loadedRows: numberOrNull(source.loadedRows),
    matchedRows: numberOrNull(source.matchedRows),
    returnedRows: numberOrNull(source.returnedRows),
  }
}

const normalizeProduct = (product, index, recommendation) => {
  const source = objectOrEmpty(product)
  const productType = textOrNull(source.productType ?? recommendation.productType)
  const depositAmountKrw = numberOrNull(source.depositAmountKrw)
  const monthlyPaymentKrw = numberOrNull(source.monthlyPaymentKrw)
  const typeText = (productType || "").toLowerCase()
  const isSaving = monthlyPaymentKrw !== null
    || Boolean(source.savingType)
    || typeText.includes("saving")
    || typeText.includes("적금")
  const rank = numberOrNull(source.rank ?? source.ranking)

  return {
    ranking: rank === null ? index + 1 : Math.max(1, Math.round(rank)),
    financialGroup: textOrNull(source.financialGroup),
    companyName: textOrNull(source.companyName),
    productName: textOrNull(source.productName),
    productType,
    isSaving,
    savingType: textOrNull(source.savingType),
    termMonths: numberOrNull(source.termMonths ?? recommendation.termMonths),
    depositAmountKrw,
    monthlyPaymentKrw,
    amountKrw: numberOrNull(
      depositAmountKrw ?? monthlyPaymentKrw ?? recommendation.amountKrw,
    ),
    baseRatePercent: numberOrNull(source.baseRatePercent),
    preferentialRatePercent: numberOrNull(source.preferentialRatePercent),
    afterTaxRatePercent: numberOrNull(source.afterTaxRatePercent),
    estimatedAfterTaxInterestKrw: numberOrNull(source.estimatedAfterTaxInterestKrw),
    estimatedMaturityAmountKrw: numberOrNull(source.estimatedMaturityAmountKrw),
    interestCalculation: textOrNull(source.interestCalculation),
    estimateAssumption: textOrNull(source.estimateAssumption),
    joinWay: textOrNull(source.joinWay),
    joinTarget: textOrNull(source.joinTarget),
    preferentialConditions: textOrNull(source.preferentialConditions),
    maturityInterest: textOrNull(source.maturityInterest),
    maximumLimitKrw: numberOrNull(source.maximumLimitKrw),
    disclosureMonth: textOrNull(source.disclosureMonth),
    collectedAt: textOrNull(source.collectedAt),
    companyCode: textOrNull(source.companyCode),
    productCode: textOrNull(source.productCode),
  }
}

export const normalizeProductRecommendation = (recommendation) => {
  const source = objectOrEmpty(recommendation)
  const hasRecommendationShape = Array.isArray(source.products)
    || source.productType !== undefined
    || source.dataMode !== undefined
  if (!hasRecommendationShape) return null

  const products = (Array.isArray(source.products) ? source.products : [])
    .slice(0, MAX_PRODUCT_COUNT)
    .map((product, index) => normalizeProduct(product, index, source))

  return {
    dataMode: textOrNull(source.dataMode),
    productType: textOrNull(source.productType),
    termMonths: numberOrNull(source.termMonths),
    amountKrw: numberOrNull(source.amountKrw),
    amountMeaning: textOrNull(source.amountMeaning),
    joinPreference: textOrNull(source.joinPreference),
    products,
    searchSummary: normalizeSearchSummary(source.searchSummary),
    instructions: Array.isArray(source.instructions)
      ? source.instructions.map(textOrNull).filter(Boolean)
      : [],
  }
}

export const formatProductAmount = (value) => {
  const number = numberOrNull(value)
  return number === null ? "-" : `${Math.round(number).toLocaleString("ko-KR")}원`
}

export const formatProductRate = (value) => {
  const number = numberOrNull(value)
  return number === null
    ? "-"
    : `${number.toLocaleString("ko-KR", { maximumFractionDigits: 2 })}%`
}

export const formatProductTerm = (value) => {
  const number = numberOrNull(value)
  return number === null ? "-" : `${Math.round(number)}개월`
}

export const formatDisclosureMonth = (value) => {
  const text = textOrNull(value)
  if (!text) return "-"
  const match = text.match(/^(\d{4})(\d{2})$/)
  return match ? `${match[1]}.${match[2]}` : text
}

export const formatCollectedAt = (value) => {
  const text = textOrNull(value)
  if (!text) return "-"
  return text.replace("T", " ").slice(0, 16)
}

export { MAX_PRODUCT_COUNT }
