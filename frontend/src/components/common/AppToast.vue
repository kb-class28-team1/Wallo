<script setup>
import { onBeforeUnmount, watch } from "vue"
import AppAlert from "@/components/ui/AppAlert.vue"

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  variant: {
    type: String,
    default: "info",
    validator: (value) => ["info", "success", "warning", "danger", "neutral"].includes(value),
  },
  title: {
    type: String,
    default: "",
  },
  message: {
    type: String,
    default: "",
  },
  duration: {
    type: Number,
    default: 0,
    validator: (value) => value >= 0,
  },
  dismissible: {
    type: Boolean,
    default: true,
  },
  pauseOnHover: {
    type: Boolean,
    default: true,
  },
  placement: {
    type: String,
    default: "top-end",
    validator: (value) => ["top-end", "top-center", "bottom-end", "bottom-center"].includes(value),
  },
  role: {
    type: String,
    default: "status",
  },
  closeLabel: {
    type: String,
    default: "닫기",
  },
})

const emit = defineEmits(["close"])
let closeTimer = null

function clearCloseTimer() {
  if (!closeTimer) return
  clearTimeout(closeTimer)
  closeTimer = null
}

function scheduleClose() {
  clearCloseTimer()
  if (!props.visible || props.duration <= 0) return
  closeTimer = setTimeout(() => emit("close"), props.duration)
}

function handleMouseEnter() {
  if (props.pauseOnHover) clearCloseTimer()
}

function handleMouseLeave() {
  if (props.pauseOnHover) scheduleClose()
}

watch(() => [props.visible, props.duration], scheduleClose, { immediate: true })
onBeforeUnmount(clearCloseTimer)
</script>

<template>
  <Transition name="app-toast">
    <div
      v-if="visible"
      class="app-toast"
      :class="`app-toast--${placement}`"
      @mouseenter="handleMouseEnter"
      @mouseleave="handleMouseLeave"
    >
      <AppAlert
        :variant="variant"
        :title="title"
        :dismissible="dismissible"
        :close-label="closeLabel"
        :role="role"
        @close="emit('close')"
      >
        <slot>{{ message }}</slot>
      </AppAlert>
    </div>
  </Transition>
</template>

<style scoped>
.app-toast {
  position: fixed;
  z-index: 1500;
  width: min(420px, calc(100vw - var(--wallo-space-6)));
  pointer-events: auto;
}

.app-toast--top-end {
  top: var(--wallo-space-5);
  right: var(--wallo-space-5);
}

.app-toast--top-center {
  top: var(--wallo-space-5);
  left: 50%;
  transform: translateX(-50%);
}

.app-toast--bottom-end {
  right: var(--wallo-space-5);
  bottom: var(--wallo-space-5);
}

.app-toast--bottom-center {
  bottom: var(--wallo-space-5);
  left: 50%;
  transform: translateX(-50%);
}

.app-toast :deep(.app-alert) {
  width: 100%;
  box-shadow: var(--wallo-shadow-card);
}

.app-toast-enter-active,
.app-toast-leave-active {
  transition: opacity 160ms ease;
}

.app-toast-enter-from,
.app-toast-leave-to {
  opacity: 0;
}

@media (max-width: 576px) {
  .app-toast {
    right: var(--wallo-space-3);
    left: var(--wallo-space-3);
    width: auto;
  }

  .app-toast--top-center,
  .app-toast--bottom-center {
    transform: none;
  }
}
</style>
