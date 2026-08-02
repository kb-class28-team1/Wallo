<script setup>
import { computed } from "vue"
import { RouterLink } from "vue-router"

const props = defineProps({
  report: {
    type: Object,
    required: true,
  },
})

// 게시일시(publishedAt)를 카드에 보여줄 날짜 형식으로 변환함
const formattedDate = computed(() => {
  if (!props.report.publishedAt) return ""

  const date = new Date(props.report.publishedAt)
  if (Number.isNaN(date.getTime())) return ""

  return date.toLocaleDateString("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" })
})
</script>

<template>
  <RouterLink :to="`/reports/${report.id}`" class="text-decoration-none text-reset d-block">
    <article class="card border-0 shadow-sm report-card">
      <div class="card-body p-4">
        <div class="d-flex flex-wrap align-items-center gap-2 mb-2">
          <span class="badge rounded-pill text-bg-light">{{ report.category }}</span>
        </div>

        <h2 class="h5 fw-bold mb-2 report-title">{{ report.title }}</h2>
        <p class="text-secondary mb-3 report-summary">{{ report.summary }}</p>

        <div class="d-flex align-items-center gap-2 text-secondary small">
          <span>{{ report.source }}</span>
          <span aria-hidden="true">·</span>
          <span>{{ formattedDate }}</span>
        </div>
      </div>
    </article>
  </RouterLink>
</template>

<style scoped>
.report-card {
  border-radius: 16px;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
}

.report-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.08) !important;
}

.report-title,
.report-summary {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.report-title {
  -webkit-line-clamp: 2;
}

.report-summary {
  -webkit-line-clamp: 2;
}
</style>
