<script setup>
// 이 프로젝트는 bootstrap.bundle.js(JS 런타임)를 전역으로 로드하지 않아, 다른 화면(SignupView 등)과
// 동일하게 Bootstrap 모달 마크업/클래스를 그대로 쓰되 Vue의 v-if로 표시 여부를 제어함.
defineProps({
  term: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(["close"])
</script>

<template>
  <div
    v-if="term"
    class="modal d-block term-modal-backdrop"
    tabindex="-1"
    role="dialog"
    aria-modal="true"
    :aria-label="`${term.term} 용어 설명`"
    @click.self="emit('close')"
  >
    <div class="modal-dialog modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">{{ term.term }}</h5>
          <button type="button" class="btn-close" aria-label="닫기" @click="emit('close')"></button>
        </div>
        <div class="modal-body">
          <p class="mb-3">{{ term.definition || "정의 정보가 없습니다." }}</p>
          <p class="text-secondary small mb-0">출처: {{ term.source || "알 수 없음" }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-primary" @click="emit('close')">확인</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.term-modal-backdrop {
  background: rgba(0, 0, 0, 0.5);
}
</style>
