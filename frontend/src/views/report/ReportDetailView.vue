<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue"
import { RouterLink, useRoute } from "vue-router"
import { getReportDetail } from "@/api/reportApi"
import ReportSection from "@/components/report/ReportSection.vue"
import TermInfoPanel from "@/components/report/TermInfoPanel.vue"
import { markReportAsRead } from "@/utils/report/reportReadState"
import { buildTermSegments } from "@/utils/report/termHighlight"

const route = useRoute()
const newsId = computed(() => route.params.newsId)

const report = ref(null)
const isLoading = ref(true)
const errorMessage = ref("")

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

  left = Math.max(VIEWPORT_PADDING, Math.min(left, window.innerWidth - TERM_CARD_WIDTH - VIEWPORT_PADDING))
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

const loadDetail = async () => {
  isLoading.value = true
  errorMessage.value = ""
  closeMobileTermCard()

  try {
    report.value = await getReportDetail(newsId.value)
    markReportAsRead(newsId.value)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  loadDetail()
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

    <div v-else-if="report" class="row g-4">
      <div class="col-12 col-lg-8">
        <div ref="reportCard" class="report-detail-card card border-0 shadow-sm">
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

            <div v-else class="text-center py-5 not-ready-panel">
              <i class="bi bi-hourglass-split fs-1 text-secondary d-block mb-3" aria-hidden="true"></i>
              <p class="text-secondary mb-0">아직 리포트가 준비되지 않았습니다.</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 데스크톱 전용 우측 용어 설명 패널. 본문 위에 겹치는 tooltip 대신, 스크롤을 따라
           함께 움직이다가(sticky) 본문을 가리지 않는 여백 영역에 고정된다. -->
    </div>

    <Teleport to="body">
      <div
        v-if="hoveredTerm"
        ref="termPopover"
        class="term-popover-card card border-0 shadow d-none d-lg-block"
        :style="termPopoverStyle"
      >
        <div class="card-body p-4">
          <TermInfoPanel :term="hoveredTerm" />
        </div>
      </div>
    </Teleport>

    <!-- 모바일/태블릿 fallback: 우측 여백이 없어 패널을 고정 배치할 수 없으므로, 용어를
         탭했을 때만 화면 하단에 카드로 띄운다. 닫기 버튼으로 명시적으로 닫는다(hover가 없어서). -->
    <div v-if="clickedTerm" class="term-mobile-card d-lg-none">
      <TermInfoPanel :term="clickedTerm" closable @close="closeMobileTermCard" />
    </div>
  </section>
</template>

<style scoped>
.report-detail-card {
  border-radius: 20px;
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
  border-radius: 20px;
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
  background-color: var(--bs-body-bg);
  border-top: 1px solid var(--bs-border-color);
  box-shadow: 0 -0.5rem 1.5rem rgba(0, 0, 0, 0.12);
}
</style>
