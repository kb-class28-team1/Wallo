<script setup>
import { onMounted, ref } from "vue"
import { getReports } from "@/api/reportApi"
import ReportListCard from "@/components/report/ReportListCard.vue"

const reports = ref([])
const isLoading = ref(true)
const errorMessage = ref("")

const loadReports = async () => {
  isLoading.value = true
  errorMessage.value = ""

  try {
    reports.value = await getReports()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    isLoading.value = false
  }
}

onMounted(loadReports)
</script>

<template>
  <section class="report-list-view container-fluid py-4 px-4">
    <header class="mb-4">
      <h1 class="h3 fw-bold mb-1">금융 리포트</h1>
      <p class="text-secondary mb-0">AI가 분석한 금융·경제 뉴스를 확인하세요.</p>
    </header>

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
