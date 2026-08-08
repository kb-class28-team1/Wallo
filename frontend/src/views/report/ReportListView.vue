<script setup>
import { onMounted, ref } from "vue"
import { generateReportsNow, getReports } from "@/api/reportApi"
import ReportListCard from "@/components/report/ReportListCard.vue"
import { getReadReportIds } from "@/utils/report/reportReadState"

const reports = ref([])
const isLoading = ref(true)
const errorMessage = ref("")
const generationMessage = ref("")
const isGenerating = ref(false)

const loadReports = async () => {
  isLoading.value = true
  errorMessage.value = ""

  try {
    const readReportIds = getReadReportIds()
    reports.value = (await getReports()).map((report) => ({
      ...report,
      read: readReportIds.has(String(report.id)),
    }))
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    isLoading.value = false
  }
}

onMounted(loadReports)

const generateNow = async () => {
  if (isGenerating.value) return

  isGenerating.value = true
  generationMessage.value = ""

  try {
    const result = await generateReportsNow()
    if (!result.started) {
      generationMessage.value = "이미 금융 리포트 생성 작업이 진행 중입니다. 잠시 후 다시 확인해 주세요."
      return
    }

    generationMessage.value = [
      `새 뉴스 ${result.crawledNewsCount}건 수집`,
      `리포트 ${result.generatedReportCount}건 생성`,
      `실패 ${result.failedReportCount}건`,
      `건너뜀 ${result.skippedReportCount}건`,
    ].join(" · ")
    await loadReports()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    isGenerating.value = false
  }
}
</script>

<template>
  <section class="report-list-view container-fluid py-4 px-4">
    <header class="d-flex flex-wrap justify-content-between align-items-start gap-3 mb-4">
      <div>
        <h1 class="h3 fw-bold mb-1">금융 리포트</h1>
        <p class="text-secondary mb-0">AI가 분석한 금융·경제 뉴스를 확인하세요.</p>
      </div>
      <button
        type="button"
        class="btn btn-primary"
        :disabled="isGenerating"
        @click="generateNow"
      >
        <span
          v-if="isGenerating"
          class="spinner-border spinner-border-sm me-2"
          aria-hidden="true"
        ></span>
        {{ isGenerating ? "생성 중..." : "새 리포트 생성" }}
      </button>
    </header>

    <div v-if="generationMessage" class="alert alert-info" role="status">
      {{ generationMessage }}
    </div>

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
      <button type="button" class="btn btn-sm btn-outline-danger" @click="loadReports">다시 시도</button>
    </div>

    <div v-else-if="reports.length === 0" class="text-center text-secondary py-5">
      <i class="bi bi-newspaper fs-1 d-block mb-3" aria-hidden="true"></i>
      <p class="mb-0">아직 등록된 금융 리포트가 없습니다.</p>
    </div>

    <div v-else class="d-flex flex-column gap-3">
      <ReportListCard v-for="report in reports" :key="report.id" :report="report" />
    </div>
  </section>
</template>
