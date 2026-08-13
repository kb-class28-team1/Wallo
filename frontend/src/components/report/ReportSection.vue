<script setup>
// segments는 이 컴포넌트가 직접 계산하지 않고 부모(ReportDetailView)로부터 미리 계산된 값을 받는다.
// 같은 용어를 "상세 페이지 전체 기준 첫 등장 1회"만 강조하려면 여러 섹션에 걸친 상태(이미 강조한
// termId 집합)가 필요한데, 그건 섹션 하나만 아는 이 컴포넌트 범위를 벗어나기 때문이다.
defineProps({
  icon: {
    type: String,
    default: "bi-info-circle",
  },
  title: {
    type: String,
    required: true,
  },
  content: {
    type: String,
    default: "",
  },
  segments: {
    type: Array,
    default: () => [],
  },
})

// term-hover: 마우스가 용어 위에 있거나(mouseenter)/키보드 포커스가 있는(focus) 동안 term을 전달하고,
// 벗어나면(mouseleave/blur) null을 전달한다 — 우측 패널의 "실시간 미리보기"용.
// term-click: 클릭/탭한 용어를 전달한다 — 모바일 하단 카드처럼 hover가 없는 환경에서 고정 표시용.
const emit = defineEmits(["term-hover", "term-click"])
</script>

<template>
  <section class="report-section mb-4">
    <h2 class="h6 fw-bold d-flex align-items-center gap-2 mb-2">
      <i class="bi" :class="icon" aria-hidden="true"></i>
      {{ title }}
    </h2>
    <p v-if="content" class="mb-0 report-section-content">
      <template v-for="(segment, index) in segments" :key="index">
        <button
          v-if="segment.type === 'term'"
          type="button"
          class="term-highlight"
          @mouseenter="emit('term-hover', segment.term, $event.currentTarget)"
          @mouseleave="emit('term-hover', null, null)"
          @focus="emit('term-hover', segment.term, $event.currentTarget)"
          @blur="emit('term-hover', null, null)"
          @click="emit('term-click', segment.term, $event.currentTarget)"
        >{{ segment.content }}</button>
        <template v-else>{{ segment.content }}</template>
      </template>
    </p>
    <p v-else class="mb-0 text-secondary fst-italic">아직 내용이 준비되지 않았습니다.</p>
  </section>
</template>

<style scoped>
.report-section-content {
  /* 기존 DB에 개행이 포함된 리포트도 한 문단으로 표시한다. */
  white-space: normal;
  line-height: 1.7;
}

.term-highlight {
  padding: 0;
  border: none;
  background: none;
  color: var(--bs-primary);
  text-decoration: underline;
  text-decoration-style: dotted;
  text-underline-offset: 3px;
  cursor: pointer;
  font: inherit;
}

.term-highlight:hover {
  text-decoration-style: solid;
}
</style>
