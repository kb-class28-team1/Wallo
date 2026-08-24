<script setup>
import { computed, nextTick, ref, useSlots, watch } from "vue"
import AppButton from "@/components/ui/AppButton.vue"

let dialogInstance = 0

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
  confirmVariant: {
    type: String,
    default: "primary",
    validator: (value) => ["primary", "secondary", "outline", "danger", "ghost"].includes(value),
  },
  confirmDisabled: {
    type: Boolean,
    default: false,
  },
  confirmLoading: {
    type: Boolean,
    default: false,
  },
  closeOnBackdrop: {
    type: Boolean,
    default: true,
  },
  closeOnEsc: {
    type: Boolean,
    default: true,
  },
  showClose: {
    type: Boolean,
    default: true,
  },
  size: {
    type: String,
    default: "md",
    validator: (value) => ["sm", "md", "lg"].includes(value),
  },
})

const emit = defineEmits(["confirm", "close"])
const confirmButton = ref(null)
const slots = useSlots()
const instanceId = `app-dialog-${++dialogInstance}`
const titleId = `${instanceId}-title`
const messageId = `${instanceId}-message`
const hasBody = computed(() => Boolean(props.message || slots.default))

function closeDialog() {
  emit("close")
}

function handleBackdropClick() {
  if (props.closeOnBackdrop) closeDialog()
}

function handleKeydown(event) {
  if (event.key === "Escape" && props.closeOnEsc) closeDialog()
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return
    await nextTick()
    confirmButton.value?.$el?.focus?.()
  },
)
</script>

<template>
  <div v-if="visible" class="app-dialog-layer" @click.self="handleBackdropClick">
    <section
      class="app-dialog"
      :class="`app-dialog--${size}`"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      :aria-describedby="hasBody ? messageId : undefined"
      tabindex="-1"
      @keydown="handleKeydown"
    >
      <header class="app-dialog-header">
        <h2 :id="titleId">{{ title }}</h2>
        <button
          v-if="showClose"
          type="button"
          class="app-dialog-close pressable"
          aria-label="닫기"
          @click="closeDialog"
        >
          ×
        </button>
      </header>
      <img v-if="imageSrc" class="app-dialog-image" :src="imageSrc" :alt="imageAlt" />
      <div v-if="hasBody" :id="messageId" class="app-dialog-body">
        <p v-if="message" class="app-dialog-message">{{ message }}</p>
        <slot />
      </div>
      <footer class="app-dialog-footer">
        <AppButton
          v-if="showCancel"
          class="app-dialog-cancel"
          variant="secondary"
          size="sm"
          @click="closeDialog"
        >
          {{ cancelText }}
        </AppButton>
        <AppButton
          ref="confirmButton"
          class="app-dialog-confirm"
          :variant="confirmVariant"
          size="sm"
          :disabled="confirmDisabled"
          :loading="confirmLoading"
          data-modal-confirm
          @click="emit('confirm', $event)"
        >
          {{ confirmText }}
        </AppButton>
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
  padding: var(--wallo-space-5);
  background: rgb(19 23 43 / 58%);
  backdrop-filter: blur(3px);
}

.app-dialog {
  width: 100%;
  padding: var(--wallo-space-5);
  color: var(--wallo-color-text);
  background: var(--wallo-color-surface);
  border-radius: var(--wallo-radius-lg);
  box-shadow: var(--wallo-shadow-modal);
}

.app-dialog--sm {
  max-width: 360px;
}

.app-dialog--md {
  max-width: 420px;
}

.app-dialog--lg {
  max-width: 620px;
}

.app-dialog-header,
.app-dialog-footer {
  display: flex;
  align-items: center;
}

.app-dialog-header {
  justify-content: space-between;
  gap: var(--wallo-space-4);
}

.app-dialog-header h2 {
  margin: 0;
  color: var(--wallo-color-text);
  font-size: 1.2rem;
  font-weight: 800;
}

.app-dialog-close {
  flex: 0 0 auto;
  padding: 2px 4px;
  color: var(--wallo-color-text-subtle);
  background: transparent;
  border: 0;
  font-size: 1.8rem;
  line-height: 1;
  cursor: pointer;
}

.app-dialog-close:hover {
  color: var(--wallo-color-text);
}

.app-dialog-close:focus-visible {
  outline: 0;
  box-shadow: var(--wallo-focus-ring);
  border-radius: var(--wallo-radius-sm);
}

.app-dialog-body {
  margin: var(--wallo-space-5) 0;
}

.app-dialog-message {
  margin: 0;
  color: var(--wallo-color-text-muted);
  line-height: 1.6;
  white-space: pre-line;
}

.app-dialog-image {
  display: block;
  width: min(260px, 100%);
  height: auto;
  margin: var(--wallo-space-3) auto var(--wallo-space-4);
}

.app-dialog-footer {
  justify-content: flex-end;
  gap: var(--wallo-space-2);
}
</style>
