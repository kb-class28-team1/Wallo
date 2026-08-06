<script setup>
import { nextTick, ref, watch } from "vue"

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    default: "알림",
  },
  message: {
    type: String,
    default: "",
  },
  imageSrc: {
    type: String,
    default: "",
  },
  imageAlt: {
    type: String,
    default: "",
  },
  confirmText: {
    type: String,
    default: "확인",
  },
  cancelText: {
    type: String,
    default: "취소",
  },
  showCancel: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(["confirm", "close"])
const confirmButton = ref(null)

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return
    await nextTick()
    confirmButton.value?.focus()
  },
)
</script>

<template>
  <div v-if="visible" class="app-dialog-layer" @click.self="emit('close')">
    <section
      class="app-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="app-dialog-title"
      aria-describedby="app-dialog-message"
      tabindex="-1"
      @keydown.esc="emit('close')"
    >
      <header class="app-dialog-header">
        <h2 id="app-dialog-title">{{ title }}</h2>
        <button type="button" class="app-dialog-close" aria-label="닫기" @click="emit('close')">
          ×
        </button>
      </header>
      <img v-if="imageSrc" class="app-dialog-image" :src="imageSrc" :alt="imageAlt" />
      <p id="app-dialog-message" class="app-dialog-message">{{ message }}</p>
      <footer class="app-dialog-footer">
        <button v-if="showCancel" type="button" class="app-dialog-cancel" @click="emit('close')">
          {{ cancelText }}
        </button>
        <button
          ref="confirmButton"
          type="button"
          class="app-dialog-confirm"
          data-modal-confirm
          @click="emit('confirm')"
        >
          {{ confirmText }}
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.app-dialog-layer {
  position: fixed;
  inset: 0;
  z-index: 1400;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(19, 23, 43, 0.58);
  backdrop-filter: blur(3px);
}

.app-dialog {
  width: min(420px, 100%);
  padding: 24px;
  color: #202840;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 25px 80px rgba(0, 0, 0, 0.24);
}

.app-dialog-header,
.app-dialog-footer {
  display: flex;
  align-items: center;
}

.app-dialog-header {
  justify-content: space-between;
  gap: 16px;
}

.app-dialog-header h2 {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 850;
}

.app-dialog-close {
  padding: 0 4px;
  color: #9ba2b7;
  background: transparent;
  border: 0;
  font-size: 1.8rem;
  line-height: 1;
}

.app-dialog-message {
  margin: 20px 0;
  color: #59647f;
  line-height: 1.6;
  white-space: pre-line;
}

.app-dialog-image {
  display: block;
  width: min(260px, 100%);
  height: auto;
  margin: 12px auto 16px;
}

.app-dialog-footer {
  justify-content: flex-end;
  gap: 8px;
}

.app-dialog-footer button {
  min-width: 82px;
  padding: 10px 16px;
  border-radius: 10px;
  font-weight: 750;
}

.app-dialog-cancel {
  color: #687086;
  background: #fff;
  border: 1px solid #dedfeb;
}

.app-dialog-confirm {
  color: #fff;
  background: #6d5ddd;
  border: 1px solid #6d5ddd;
}
</style>
