<script setup>
import { computed, onMounted, ref, watch } from "vue"
import { RouterLink, useRoute } from "vue-router"
import { generateReport, getReportDetail } from "@/api/reportApi"
import ReportSection from "@/components/report/ReportSection.vue"
import TermDefinitionModal from "@/components/report/TermDefinitionModal.vue"

const route = useRoute()
const newsId = computed(() => route.params.newsId)

const report = ref(null)
const isLoading = ref(true)
const errorMessage = ref("")

const isGenerating = ref(false)
const generateErrorMessage = ref("")

const selectedTerm = ref(null)

// news_report가 아직 없으면 summary를 포함한 AI 필드가 전부 null로 내려옴(백엔드 ReportDetailResponse 규칙)
const hasReport = computed(() => Boolean(report.value?.summary))

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

const handleGenerate = async () => {
  // 버튼 disabled와 별개로 중복 클릭을 한 번 더 막음
  if (isGenerating.value) return

  isGenerating.value = true
  generateErrorMessage.value = ""

  try {
    report.value = await generateReport(newsId.value)
  } catch (error) {
    generateErrorMessage.value = error.message
  } finally {
    isGenerating.value = false
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
          <ReportSection icon="bi-clipboard-data" title="핵심 요약" :content="report.summary" />
          <ReportSection icon="bi-question-circle" title="왜 이런 일이 발생했나요?" :content="report.cause" />
          <ReportSection
            icon="bi-people"
            title="사회에는 어떤 영향이 있나요?"
            :content="report.socialImpact"
          />
          <ReportSection icon="bi-person" title="나에게 어떤 영향이 있나요?" :content="report.userImpact" />
          <ReportSection
            icon="bi-lightbulb"
            title="지금 할 수 있는 대응 방법"
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

        <div v-else class="text-center py-5 generate-panel">
          <i class="bi bi-robot fs-1 text-secondary d-block mb-3" aria-hidden="true"></i>
          <p class="text-secondary mb-3">아직 이 뉴스에 대한 AI 리포트가 생성되지 않았습니다.</p>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="isGenerating"
            @click="handleGenerate"
          >
            <span
              v-if="isGenerating"
              class="spinner-border spinner-border-sm me-2"
              role="status"
              aria-hidden="true"
            ></span>
            {{ isGenerating ? "AI 리포트 생성 중..." : "AI 리포트 생성하기" }}
          </button>
          <p v-if="generateErrorMessage" class="text-danger mt-3 mb-0">{{ generateErrorMessage }}</p>
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
</style>
