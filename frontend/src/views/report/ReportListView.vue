<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue"
import { crawlNewsNow, generateMissingReports, getReports } from "@/api/reportApi"
import ReportListCard from "@/components/report/ReportListCard.vue"
import {
  getCachedResource,
  getResource,
  hasInFlightResource,
  invalidateResource,
} from "@/utils/resourceCache"
import { getReadReportIds } from "@/utils/report/reportReadState"

const REPORT_LIST_CACHE_KEY = "reports:list"
const REPORT_LIST_STALE_TIME = 60 * 1000
const cacheScope = {}
const reports = ref([])
const hasLoadedReports = ref(false)
const initialLoading = ref(true)
const refreshing = ref(false)
const errorMessage = ref("")
const generationMessage = ref("")
const isCrawling = ref(false)
const isGenerating = ref(false)
let refreshTimer = null

const applyReports = (nextReports) => {
  const readReportIds = getReadReportIds()
  reports.value = (Array.isArray(nextReports) ? nextReports : []).map((report) => ({
    ...report,
    read: readReportIds.has(String(report.id)),
  }))
  hasLoadedReports.value = true
  return reports.value
}

const loadReports = async ({ force = false } = {}) => {
  const cachedReports =
    !force && !hasInFlightResource(REPORT_LIST_CACHE_KEY, { scope: cacheScope })
      ? getCachedResource(REPORT_LIST_CACHE_KEY, {
          scope: cacheScope,
          staleTime: REPORT_LIST_STALE_TIME,
        })
      : undefined

  if (cachedReports !== undefined) {
    errorMessage.value = ""
    initialLoading.value = false
    refreshing.value = false
    return applyReports(cachedReports)
  }

  const shouldShowInitialLoading = !hasLoadedReports.value
  initialLoading.value = shouldShowInitialLoading
  refreshing.value = !shouldShowInitialLoading
  errorMessage.value = ""

  try {
    const nextReports = await getResource(REPORT_LIST_CACHE_KEY, () => getReports(), {
      scope: cacheScope,
      force,
      staleTime: REPORT_LIST_STALE_TIME,
    })
    return applyReports(nextReports)
  } catch (error) {
    errorMessage.value = error.message
    if (shouldShowInitialLoading) reports.value = []
  } finally {
    initialLoading.value = false
    refreshing.value = false
  }
}

onMounted(() => {
  void loadReports()
})
onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})

const crawlNow = async () => {
  if (isCrawling.value) return
  isCrawling.value = true
  errorMessage.value = ""
  generationMessage.value = ""
  try {
    const result = await crawlNewsNow()
    generationMessage.value = result.started
      ? `새 뉴스 ${result.crawledNewsCount}건을 수집했습니다.`
      : "이미 뉴스 크롤링 작업이 진행 중입니다."
    invalidateResource(REPORT_LIST_CACHE_KEY, { scope: cacheScope })
    await loadReports({ force: true })
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    isCrawling.value = false
  }
}

const generateNow = async () => {
  if (isGenerating.value) return

  isGenerating.value = true
  errorMessage.value = ""
  generationMessage.value = ""

  try {
    const result = await generateMissingReports()
    generationMessage.value = result.newlyStarted
      ? "AI 리포트 생성을 시작했습니다. 1분마다 한 건씩 목록에 추가됩니다."
      : "AI 리포트 생성 작업이 이미 진행 중입니다."
    if (!refreshTimer) {
      refreshTimer = window.setInterval(() => {
        void loadReports({ force: true })
      }, 15000)
    }
    invalidateResource(REPORT_LIST_CACHE_KEY, { scope: cacheScope })
    await loadReports({ force: true })
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
      <div class="d-flex flex-wrap gap-2">
        <button
          type="button"
          class="btn btn-outline-primary"
          :disabled="isCrawling"
          @click="crawlNow"
        >
          <span
            v-if="isCrawling"
            class="spinner-border spinner-border-sm me-2"
            aria-hidden="true"
          ></span>
          {{ isCrawling ? "크롤링 중..." : "새 뉴스 크롤링" }}
        </button>
        <button type="button" class="btn btn-primary" :disabled="isGenerating" @click="generateNow">
          <span
            v-if="isGenerating"
            class="spinner-border spinner-border-sm me-2"
            aria-hidden="true"
          ></span>
          {{ isGenerating ? "요청 중..." : "AI 리포트 생성" }}
        </button>
      </div>
    </header>

    <div v-if="generationMessage" class="alert alert-info" role="status">
      {{ generationMessage }}
    </div>

    <div v-if="refreshing" class="small text-secondary mb-3" role="status">
      최신 리포트 목록을 확인하는 중...
    </div>

    <div v-if="initialLoading" class="text-center py-5">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">불러오는 중...</span>
      </div>
    </div>

    <div
      v-else-if="errorMessage && !reports.length"
      class="alert alert-danger d-flex flex-wrap justify-content-between align-items-center gap-2"
      role="alert"
    >
      <span>{{ errorMessage }}</span>
      <button
        type="button"
        class="btn btn-sm btn-outline-danger"
        @click="loadReports({ force: true })"
      >
        다시 시도
      </button>
    </div>

    <template v-else>
      <div
        v-if="errorMessage"
        class="alert alert-warning d-flex flex-wrap justify-content-between align-items-center gap-2"
        role="alert"
      >
        <span>{{ errorMessage }}</span>
        <button
          type="button"
          class="btn btn-sm btn-outline-warning"
          @click="loadReports({ force: true })"
        >
          다시 시도
        </button>
      </div>

      <div v-if="reports.length === 0" class="text-center text-secondary py-5">
        <i class="bi bi-newspaper fs-1 d-block mb-3" aria-hidden="true"></i>
        <p class="mb-0">아직 등록된 금융 리포트가 없습니다.</p>
      </div>

      <div v-else class="d-flex flex-column gap-3">
        <ReportListCard v-for="report in reports" :key="report.id" :report="report" />
      </div>
    </template>
  </section>
</template>
