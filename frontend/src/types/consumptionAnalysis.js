export const CATEGORY_LABELS = {
  CAFE: "카페",
  DELIVERY: "배달",
  SHOPPING: "쇼핑",
  CULTURE: "문화",
  FOOD: "식비",
  DINING: "외식",
  TRANSPORT: "교통",
  HOUSING: "주거",
  HEALTH: "건강",
  EDUCATION: "교육",
  LOAN_REPAYMENT: "대출 상환",
  LIVING: "생활",
  CARD_WITHDRAWAL: "카드 대금 출금",
}

export const BUDGET_STATUS = {
  NORMAL: { label: "정상", variant: "success" },
  CAUTION: { label: "주의", variant: "warning" },
  DANGER: { label: "위험", variant: "danger" },
  OVER: { label: "초과", variant: "danger" },
}

export const TIME_SLOT_LABELS = {
  DAWN: "새벽",
  MORNING: "오전",
  AFTERNOON: "오후",
  EVENING: "저녁",
}

export const WEEKDAY_LABELS = [
  "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일",
]

export const categoryLabel = (code) => CATEGORY_LABELS[code] || code || "기타"

export const formatWon = (value) =>
  `${Math.round(Number(value) || 0).toLocaleString("ko-KR")}원`
