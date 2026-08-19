<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue"
import { crawlNewsNow, generateMissingReports, getReports } from "@/api/reportApi"
import ReportListCard from "@/components/report/ReportListCard.vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppState from "@/components/ui/AppState.vue"
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
  <section class="report-list-view">
    <AppPageHeader
      eyebrow="금융·경제"
      title="금융 리포트"
    >
      <template #actions>
        <AppButton
          class="report-crawl-button"
          variant="outline"
          :disabled="isCrawling"
          :loading="isCrawling"
          @click="crawlNow"
        >
          {{ isCrawling ? "크롤링 중..." : "새 뉴스 크롤링" }}
        </AppButton>
        <AppButton
          class="report-generate-button"
          variant="primary"
          :disabled="isGenerating"
          :loading="isGenerating"
          @click="generateNow"
        >
          {{ isGenerating ? "요청 중..." : "AI 리포트 생성" }}
        </AppButton>
      </template>
    </AppPageHeader>

    <AppAlert
      v-if="generationMessage"
      class="report-generation-message"
      variant="info"
      role="status"
      :message="generationMessage"
    />

    <div v-if="refreshing" class="report-refresh-status" role="status">
      최신 리포트 목록을 확인하는 중...
    </div>

    <AppState
      v-if="initialLoading"
      class="report-state"
      type="loading"
      title="금융 리포트 목록을 불러오는 중입니다."
      message="최신 금융·경제 뉴스를 확인하고 있습니다."
    />

    <AppAlert v-else-if="errorMessage && !reports.length" class="report-error" variant="danger">
      <span>{{ errorMessage }}</span>
      <AppButton variant="outline" size="sm" @click="loadReports({ force: true })">
        다시 시도
      </AppButton>
    </AppAlert>

    <template v-else>
      <AppAlert v-if="errorMessage" class="report-error" variant="warning">
        <span>{{ errorMessage }}</span>
        <AppButton variant="outline" size="sm" @click="loadReports({ force: true })">
          다시 시도
        </AppButton>
      </AppAlert>

      <AppState
        v-if="reports.length === 0"
        class="report-empty-state"
        type="empty"
        title="아직 등록된 금융 리포트가 없습니다."
        message="새 뉴스가 수집되면 금융 리포트가 이곳에 표시됩니다."
      />

      <div v-else class="report-list-grid" aria-label="금융 리포트 목록">
        <ReportListCard v-for="report in reports" :key="report.id" :report="report" />
      </div>
    </template>
  </section>
</template>

<style scoped>
.report-list-view {
  width: 100%;
  padding: var(--wallo-space-6) var(--wallo-space-4);
}

.report-generation-message,
.report-error,
.report-refresh-status {
  margin-bottom: var(--wallo-space-4);
}

.report-error {
  align-items: center;
}

.report-error :deep(.app-alert__message) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wallo-space-3);
}

.report-refresh-status {
  color: var(--wallo-color-text-muted);
  font-size: 0.875rem;
}

.report-state,
.report-empty-state {
  min-height: 280px;
}

.report-list-grid {
  display: grid;
  gap: var(--wallo-space-4);
}

@media (max-width: 576px) {
  .report-list-view {
    padding-right: var(--wallo-space-3);
    padding-left: var(--wallo-space-3);
  }

  .report-error :deep(.app-alert__message) {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
