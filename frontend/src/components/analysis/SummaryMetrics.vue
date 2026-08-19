<script setup>
import { computed } from "vue"
import { formatWon } from "@/types/consumptionAnalysis"

const props = defineProps({
  summary: { type: Object, required: true },
  periodLabel: { type: String, default: "분석 기간" },
})

const deltaClass = computed(() =>
  Number(props.summary.deltaAmount) > 0 ? "text-danger" : "text-success",
)
const rateText = computed(() => {
  const rate = props.summary.deltaRate
  if (rate === null || rate === undefined) return "비교 기간 지출 없음"
  const direction = Number(rate) > 0 ? "증가" : "감소"
  return `${Math.abs(Number(rate)).toLocaleString("ko-KR", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  })}% ${direction}`
})
</script>

<template>
  <div>
    <div class="d-flex align-items-center justify-content-between gap-3">
      <div>
        <small class="text-secondary">{{ periodLabel }} 총지출</small>
        <div class="fs-4 fw-bold">{{ formatWon(summary.currentTotal) }}</div>
      </div>
      <span
        class="badge rounded-pill"
        :class="summary.warningIncrease ? 'text-bg-danger' : 'text-bg-light'"
      >
        {{ rateText }}
      </span>
    </div>
    <div class="small mt-2" :class="deltaClass">
      비교 기간 {{ formatWon(summary.previousTotal) }}에서
      {{ formatWon(Math.abs(summary.deltaAmount)) }}
      {{ Number(summary.deltaAmount) > 0 ? "더 썼어요" : "덜 썼어요" }}
    </div>
  </div>
</template>
