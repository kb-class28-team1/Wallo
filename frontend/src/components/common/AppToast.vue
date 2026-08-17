<script setup>
import { storeToRefs } from "pinia"
import { useToastStore } from "@/stores/toastStore"

const toastStore = useToastStore()
const { toasts } = storeToRefs(toastStore)
</script>

<template>
  <div class="wallo-toast-container" aria-live="polite" aria-atomic="true">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="wallo-toast"
      :class="`wallo-toast-${toast.variant}`"
      role="alert"
    >
      <span class="wallo-toast-message">{{ toast.message }}</span>
      <button
        type="button"
        class="wallo-toast-close"
        aria-label="알림 닫기"
        @click="toastStore.remove(toast.id)"
      >
        ×
      </button>
    </div>
  </div>
</template>

<style scoped>
.wallo-toast-container {
  position: fixed;
  top: 24px;
  right: 24px;
  z-index: 1600;
  display: flex;
  width: min(420px, calc(100vw - 32px));
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}

.wallo-toast {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  color: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.18);
  pointer-events: auto;
}

.wallo-toast-danger {
  background: #b42318;
}

.wallo-toast-success {
  background: #147d4d;
}

.wallo-toast-message {
  line-height: 1.45;
  white-space: pre-line;
}

.wallo-toast-close {
  flex: 0 0 auto;
  padding: 0;
  color: inherit;
  background: transparent;
  border: 0;
  font-size: 1.35rem;
  line-height: 1;
}
</style>
