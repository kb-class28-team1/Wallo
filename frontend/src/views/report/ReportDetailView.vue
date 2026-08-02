<script setup>
import { computed, onMounted, ref, watch } from "vue"
import { RouterLink, useRoute } from "vue-router"
import { getReportDetail } from "@/api/reportApi"
import ReportSection from "@/components/report/ReportSection.vue"
import TermDefinitionModal from "@/components/report/TermDefinitionModal.vue"

const route = useRoute()
const newsId = computed(() => route.params.newsId)

const report = ref(null)
const isLoading = ref(true)
const errorMessage = ref("")

const selectedTerm = ref(null)

// news_report가 아직 없으면 summaryPoints가 빈 배열로 내려옴(백엔드 ReportDetailResponse 규칙)
const hasReport = computed(() => Boolean(report.value?.summaryPoints?.length))

const formattedDate = computed(() => {
  if (!report.value?.publishedAt) return ""

  const date = new Date(report.value.publishedAt)
  if (Number.isNaN(date.getTime())) return ""

  return date.toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  })
})

const loadDetail = async () => {
  isLoading.value = true
  errorMessage.value = ""

  try {
    report.value = await getReportDetail(newsId.value)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    isLoading.value = false
  }
}

const openTermModal = (term) => {
  selectedTerm.value = term
}

const closeTermModal = () => {
  selectedTerm.value = null
}

onMounted(loadDetail)

// 같은 라우트 컴포넌트를 재사용하며 newsId만 바뀌는 경우(다른 리포트로 이동)에도 다시 불러옴
watch(newsId, () => {
  loadDetail()
})
</script>

<template>
  <section class="report-detail-view container-fluid py-4 px-4">
    <RouterLink to="/reports" class="btn btn-link ps-0 mb-3 text-decoration-none">
      <i class="bi bi-arrow-left me-1" aria-hidden="true"></i>
      목록으로
    </RouterLink>

    <div v-if="isLoading" class="text-center py-5">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">불러오는 중...</span>
      </div>
    </div>

    <div
      v-else-if="errorMessage"
      class="alert alert-danger d-flex flex-wrap justify-content-between align-items-center gap-2"
      role="alert"
    >
      <span>{{ errorMessage }}</span>
      <button type="button" class="btn btn-sm btn-outline-danger" @click="loadDetail">다시 시도</button>
    </div>

    <div v-else-if="report" class="report-detail-card card border-0 shadow-sm">
      <div class="card-body p-4 p-md-5">
        <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
          <span class="badge rounded-pill text-bg-light">{{ report.category }}</span>
        </div>

        <h1 class="h3 fw-bold mb-2">{{ report.title }}</h1>
        <p class="text-secondary mb-4">{{ report.source }} · {{ formattedDate }}</p>

        <template v-if="hasReport">
          <section class="summary-highlight bg-primary-subtle rounded-4 p-4 mb-4">
            <h2 class="h6 fw-bold d-flex align-items-center gap-2 mb-3">
              <i class="bi bi-clipboard-data" aria-hidden="true"></i>
              핵심 요약
            </h2>
            <ul class="summary-points mb-0">
              <li v-for="(point, index) in report.summaryPoints" :key="index">{{ point }}</li>
            </ul>
          </section>

          <ReportSection icon="bi-newspaper" title="어떤 일이 있었나요?" :content="report.eventDescription" />
          <ReportSection icon="bi-question-circle" title="왜 이런 결정이 내려졌나요?" :content="report.cause" />
          <ReportSection
            icon="bi-people"
            title="사회에는 어떤 영향이 있을까요?"
            :content="report.socialImpact"
          />
          <ReportSection icon="bi-person" title="나에게 어떤 영향이 있을까요?" :content="report.userImpact" />
          <ReportSection
            icon="bi-lightbulb"
            title="지금 내가 할 수 있는 대응 방법"
            :content="report.actionPlan"
          />

          <section v-if="report.terms && report.terms.length > 0" class="mt-4">
            <h2 class="h6 fw-bold mb-2">관련 금융용어</h2>
            <div class="d-flex flex-wrap gap-2">
              <button
                v-for="term in report.terms"
                :key="term.termId"
                type="button"
                class="btn btn-sm btn-outline-primary rounded-pill"
                @click="openTermModal(term)"
              >
                {{ term.term }}
              </button>
            </div>
          </section>
        </template>

        <div v-else class="text-center py-5 not-ready-panel">
          <i class="bi bi-hourglass-split fs-1 text-secondary d-block mb-3" aria-hidden="true"></i>
          <p class="text-secondary mb-0">아직 리포트가 준비되지 않았습니다.</p>
        </div>
      </div>
    </div>

    <TermDefinitionModal :term="selectedTerm" @close="closeTermModal" />
  </section>
</template>

<style scoped>
.report-detail-card {
  border-radius: 20px;
  max-width: 860px;
}

.summary-points {
  padding-left: 1.25rem;
}

.summary-points li {
  margin-bottom: 0.4rem;
}

.summary-points li:last-child {
  margin-bottom: 0;
}
</style>
