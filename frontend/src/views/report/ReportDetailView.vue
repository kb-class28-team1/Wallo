<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue"
import { RouterLink, useRoute } from "vue-router"
import { getReportDetail } from "@/api/reportApi"
import ReportSection from "@/components/report/ReportSection.vue"
import TermInfoPanel from "@/components/report/TermInfoPanel.vue"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppState from "@/components/ui/AppState.vue"
import { getCachedResource, getResource, hasInFlightResource } from "@/utils/resourceCache"
import { markReportAsRead } from "@/utils/report/reportReadState"
import { buildTermSegments } from "@/utils/report/termHighlight"

const route = useRoute()
const newsId = computed(() => route.params.newsId)

const REPORT_DETAIL_STALE_TIME = 5 * 60 * 1000
const cacheScope = {}
const detailCacheKey = (id) => `reports:detail:${id}`
const report = ref(null)
const initialLoading = ref(true)
const refreshing = ref(false)
const errorMessage = ref("")
const loadedNewsId = ref(null)
let loadSequence = 0

// 밑줄 강조 대상 5개 섹션. 순서대로 처리해야 "상세 페이지 전체 기준 첫 등장 1회" 규칙이
// 위에서 아래로 읽는 사용자 시선과 일치한다(먼저 나오는 섹션에서 강조되고, 같은 용어가 나중
// 섹션에 또 나오면 그때는 강조하지 않음).
const HIGHLIGHTED_SECTIONS = [
  { key: "eventDescription", field: "eventDescription" },
  { key: "cause", field: "cause" },
  { key: "socialImpact", field: "socialImpact" },
  { key: "userImpact", field: "userImpact" },
  { key: "actionPlan", field: "actionPlan" },
]

// 5개 섹션을 한 computed 안에서 순서대로 처리하면서 usedTermIds를 공유해, 같은 용어가 여러
// 섹션에 걸쳐 반복 등장해도 맨 처음 나온 곳 딱 한 번만 밑줄이 붙게 한다.
const sectionSegments = computed(() => {
  const terms = report.value?.terms
  const usedTermIds = new Set()
  const result = {}

  for (const { key, field } of HIGHLIGHTED_SECTIONS) {
    result[key] = buildTermSegments(report.value?.[field], terms, usedTermIds)
  }

  return result
})

// hoveredTerm: 실시간 미리보기(마우스가 용어 위에 있는 동안만). desktop 우측 패널용.
// clickedTerm: 클릭/탭으로 "고정"된 용어. hover가 없는 모바일 하단 카드는 이것만 사용한다.
// 우측 패널은 hover가 있으면 그걸 우선 보여주고, hover가 끝나면 마지막으로 클릭한 용어로 되돌아간다.
const clickedTerm = ref(null)
const hoveredTerm = ref(null)
const termAnchor = ref(null)
const termPopover = ref(null)
const reportCard = ref(null)
const termPopoverPosition = ref({ top: 0, left: 0 })

const TERM_CARD_WIDTH = 360
const TERM_CARD_GAP = 12
const VIEWPORT_PADDING = 16

const updateTermPopoverPosition = () => {
  if (!termAnchor.value || !hoveredTerm.value || !reportCard.value) return

  const anchorRect = termAnchor.value.getBoundingClientRect()
  const reportCardRect = reportCard.value.getBoundingClientRect()
  const cardHeight = termPopover.value?.offsetHeight || 0
  let left = reportCardRect.right + TERM_CARD_GAP
  let top = anchorRect.top

  if (left + TERM_CARD_WIDTH > window.innerWidth - VIEWPORT_PADDING) {
    left = reportCardRect.left - TERM_CARD_GAP - TERM_CARD_WIDTH
  }

  left = Math.max(
    VIEWPORT_PADDING,
    Math.min(left, window.innerWidth - TERM_CARD_WIDTH - VIEWPORT_PADDING),
  )
  top = Math.max(
    VIEWPORT_PADDING,
    Math.min(top, window.innerHeight - cardHeight - VIEWPORT_PADDING),
  )

  termPopoverPosition.value = { top, left }
}

const termPopoverStyle = computed(() => ({
  top: `${termPopoverPosition.value.top}px`,
  left: `${termPopoverPosition.value.left}px`,
  width: `${TERM_CARD_WIDTH}px`,
}))

const handleTermClick = async (term, anchorElement) => {
  clickedTerm.value = term
}

const handleTermHover = async (term, anchorElement) => {
  hoveredTerm.value = term
  termAnchor.value = anchorElement

  if (!term) return

  await nextTick()
  updateTermPopoverPosition()
}

const closeMobileTermCard = () => {
  clickedTerm.value = null
  hoveredTerm.value = null
  termAnchor.value = null
}

const handleViewportChange = () => updateTermPopoverPosition()

const handleEscape = (event) => {
  if (event.key === "Escape") closeMobileTermCard()
}

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

const loadDetail = async ({ force = false } = {}) => {
  const requestedNewsId = newsId.value
  const requestId = ++loadSequence
  errorMessage.value = ""
  closeMobileTermCard()

  if (!requestedNewsId) {
    report.value = null
    loadedNewsId.value = null
    initialLoading.value = false
    refreshing.value = false
    return null
  }

  const key = detailCacheKey(requestedNewsId)
  const cachedReport =
    !force && !hasInFlightResource(key, { scope: cacheScope })
      ? getCachedResource(key, {
          scope: cacheScope,
          staleTime: REPORT_DETAIL_STALE_TIME,
        })
      : undefined

  if (cachedReport !== undefined) {
    report.value = cachedReport
    loadedNewsId.value = requestedNewsId
    initialLoading.value = false
    refreshing.value = false
    markReportAsRead(requestedNewsId)
    return cachedReport
  }

  const hasExistingReport = loadedNewsId.value === requestedNewsId && Boolean(report.value)
  initialLoading.value = !hasExistingReport
  refreshing.value = hasExistingReport
  if (!hasExistingReport) report.value = null

  try {
    const nextReport = await getResource(key, () => getReportDetail(requestedNewsId), {
      scope: cacheScope,
      force,
      staleTime: REPORT_DETAIL_STALE_TIME,
    })

    if (requestId !== loadSequence || newsId.value !== requestedNewsId) {
      return nextReport
    }

    report.value = nextReport
    loadedNewsId.value = requestedNewsId
    markReportAsRead(requestedNewsId)
    return nextReport
  } catch (error) {
    if (requestId === loadSequence && newsId.value === requestedNewsId) {
      errorMessage.value = error.message
      if (!hasExistingReport) {
        report.value = null
        loadedNewsId.value = null
      }
    }
    return null
  } finally {
    if (requestId === loadSequence) {
      initialLoading.value = false
      refreshing.value = false
    }
  }
}

onMounted(() => {
  void loadDetail()
  window.addEventListener("resize", handleViewportChange)
  window.addEventListener("scroll", handleViewportChange, true)
  window.addEventListener("keydown", handleEscape)
})

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleViewportChange)
  window.removeEventListener("scroll", handleViewportChange, true)
  window.removeEventListener("keydown", handleEscape)
})

// 같은 라우트 컴포넌트를 재사용하며 newsId만 바뀌는 경우(다른 리포트로 이동)에도 다시 불러옴
watch(newsId, () => {
  void loadDetail()
})
</script>

<template>
  <section class="report-detail-view">
    <AppPageHeader
      eyebrow="금융·경제"
      title="금융 리포트 상세"
      compact
    >
      <template #leading>
        <RouterLink to="/reports" class="report-back-link" aria-label="금융 리포트 목록으로 이동">
          <i class="bi bi-chevron-left" aria-hidden="true"></i>
        </RouterLink>
      </template>
    </AppPageHeader>

    <AppState
      v-if="initialLoading"
      class="report-detail-state"
      type="loading"
      title="리포트를 불러오는 중입니다."
      message="뉴스 내용을 분석하고 있습니다."
    />

    <AppState
      v-else-if="errorMessage && !report"
      class="report-detail-state"
      type="error"
      title="리포트를 불러오지 못했습니다."
      :message="errorMessage"
      action-text="다시 시도"
      action-variant="danger"
      @action="loadDetail({ force: true })"
    />

    <div v-else-if="report" class="report-detail-layout">
      <div v-if="refreshing" class="report-refresh-status" role="status">
        최신 리포트를 확인하는 중...
      </div>

      <AppAlert v-if="errorMessage" class="report-detail-refresh-error" variant="warning">
        <span>{{ errorMessage }}</span>
        <AppButton variant="outline" size="sm" @click="loadDetail({ force: true })">
          다시 시도
        </AppButton>
      </AppAlert>

      <div ref="reportCard" class="report-detail-card-shell">
        <AppCard class="report-detail-card" padding="lg">
          <div class="report-detail-card-content">
            <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
              <span class="badge rounded-pill text-bg-light">{{ report.category }}</span>
            </div>

            <h2 class="h3 fw-bold mb-2">{{ report.title }}</h2>
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

              <ReportSection
                icon="bi-newspaper"
                title="어떤 일이 있었나요?"
                :content="report.eventDescription"
                :segments="sectionSegments.eventDescription"
                @term-hover="handleTermHover"
                @term-click="handleTermClick"
              />
              <ReportSection
                icon="bi-question-circle"
                title="왜 이런 일이 발생했나요?"
                :content="report.cause"
                :segments="sectionSegments.cause"
                @term-hover="handleTermHover"
                @term-click="handleTermClick"
              />
              <ReportSection
                icon="bi-people"
                title="사회에는 어떤 영향이 있을까요?"
                :content="report.socialImpact"
                :segments="sectionSegments.socialImpact"
                @term-hover="handleTermHover"
                @term-click="handleTermClick"
              />
              <ReportSection
                icon="bi-person"
                title="나에게는 어떤 영향이 있을까요?"
                :content="report.userImpact"
                :segments="sectionSegments.userImpact"
                @term-hover="handleTermHover"
                @term-click="handleTermClick"
              />
              <ReportSection
                icon="bi-lightbulb"
                title="지금 내가 할 수 있는 대응 방법"
                :content="report.actionPlan"
                :segments="sectionSegments.actionPlan"
                @term-hover="handleTermHover"
                @term-click="handleTermClick"
              />
            </template>

            <AppState
              v-else
              class="not-ready-panel"
              type="empty"
              title="아직 리포트가 준비되지 않았습니다."
              message="AI 분석이 완료되면 이곳에서 상세 내용을 확인할 수 있습니다."
              compact
            />
          </div>
        </AppCard>
      </div>
    </div>

    <AppState
      v-else
      class="report-detail-state"
      type="empty"
      title="리포트를 찾을 수 없습니다."
      message="목록으로 돌아가 다른 리포트를 선택해 주세요."
    />

    <Teleport to="body">
      <div
        v-if="hoveredTerm"
        ref="termPopover"
        class="term-popover-card d-none d-lg-block"
        :style="termPopoverStyle"
      >
        <AppCard padding="md">
          <TermInfoPanel :term="hoveredTerm" />
        </AppCard>
      </div>
    </Teleport>

    <div v-if="clickedTerm" class="term-mobile-card d-lg-none">
      <AppCard padding="md">
        <TermInfoPanel :term="clickedTerm" closable @close="closeMobileTermCard" />
      </AppCard>
    </div>
  </section>
</template>

<style scoped>
.report-detail-view {
  --report-detail-content-offset: calc(38px + var(--wallo-space-4));
  width: 100%;
  padding: var(--wallo-space-6) var(--wallo-space-4);
}

.report-back-link {
  display: inline-flex;
  width: 38px;
  height: 38px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 12px;
  color: #555b6e;
  background: transparent;
  font-size: 1.1rem;
  text-decoration: none;
  transition:
    color 160ms ease,
    background-color 160ms ease;
}

.report-back-link:hover,
.report-back-link:focus-visible {
  color: #6b5bd2;
  background: #f0edff;
}

.report-detail-state {
  width: min(calc(100% - var(--report-detail-content-offset)), 980px);
  min-height: 320px;
  margin-left: var(--report-detail-content-offset);
}

.report-detail-layout {
  width: min(calc(100% - var(--report-detail-content-offset)), 980px);
  margin-left: var(--report-detail-content-offset);
}

.report-refresh-status,
.report-detail-refresh-error {
  margin-bottom: var(--wallo-space-4);
}

.report-refresh-status {
  color: var(--wallo-color-text-muted);
  font-size: 0.875rem;
}

.report-detail-refresh-error {
  align-items: center;
}

.report-detail-refresh-error :deep(.app-alert__message) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wallo-space-3);
}

.report-detail-card-shell {
  min-width: 0;
}

.report-detail-card {
  width: 100%;
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

.term-popover-card {
  position: fixed;
  z-index: 1080;
}

.term-mobile-card {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1040;
  max-height: 45vh;
  overflow-y: auto;
  padding: 1rem 1.25rem;
  background: var(--wallo-color-surface);
  border-top: 1px solid var(--wallo-color-border);
  box-shadow: var(--wallo-shadow-modal);
}

@media (max-width: 576px) {
  .report-detail-view {
    --report-detail-content-offset: 0px;
    padding-right: var(--wallo-space-3);
    padding-left: var(--wallo-space-3);
  }

  .report-detail-refresh-error :deep(.app-alert__message) {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
