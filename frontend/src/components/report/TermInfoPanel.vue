<script setup>
import { computed } from "vue"
import { getDisplayDefinition, hasDisplayableTerm } from "@/utils/termDefinition"

const props = defineProps({
  term: {
    type: Object,
    default: null,
  },
  // true면 우측 상단에 닫기 버튼을 보여준다(모바일 하단 카드 전용 — 데스크톱 사이드 패널은
  // 항상 떠 있는 고정 영역이라 닫을 필요가 없다).
  closable: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(["close"])

const displayDefinition = computed(() => getDisplayDefinition(props.term))

// shortDefinition이 없는 용어는 termHighlight.js가 애초에 하이라이트 대상에서 제외하지만,
// 혹시라도 그런 term이 넘어오면 여기서도 한 번 더 방어한다 — "선택된 용어 없음"과 똑같이
// 기본 안내 상태로 처리하고, "준비 중" 같은 fallback 문구는 절대 보여주지 않는다.
const shouldShowTerm = computed(() => hasDisplayableTerm(props.term))
</script>

<template>
  <div class="term-info-panel">
    <template v-if="shouldShowTerm">
      <div class="d-flex align-items-start justify-content-between gap-2 mb-2">
        <h2 class="h6 fw-bold mb-0">{{ term.term }}</h2>
        <button
          v-if="closable"
          type="button"
          class="btn-close flex-shrink-0"
          aria-label="닫기"
          @click="emit('close')"
        ></button>
      </div>
      <p class="mb-2 term-info-description">{{ displayDefinition }}</p>
      <p class="text-secondary small mb-0">출처: {{ term.source || "알 수 없음" }}</p>
    </template>
    <p v-else class="text-secondary mb-0 term-info-placeholder">
      <i class="bi bi-hand-index-thumb me-1" aria-hidden="true"></i>
      궁금한 금융용어에 마우스를 올려보세요.
    </p>
  </div>
</template>

<style scoped>
.term-info-panel {
  min-height: 96px;
}

.term-info-description {
  line-height: 1.6;
}

.term-info-placeholder {
  font-size: 0.9rem;
}
</style>
