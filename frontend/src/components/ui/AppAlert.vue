<script setup>
import { computed } from "vue"

const props = defineProps({
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
  icon: {
    type: String,
    default: "",
  },
  showIcon: {
    type: Boolean,
    default: true,
  },
  dismissible: {
    type: Boolean,
    default: false,
  },
  closeLabel: {
    type: String,
    default: "닫기",
  },
  role: {
    type: String,
    default: "alert",
  },
})

const emit = defineEmits(["close"])

const defaultIcons = {
  info: "bi bi-info-circle",
  success: "bi bi-check-circle",
  warning: "bi bi-exclamation-triangle",
  danger: "bi bi-x-circle",
  neutral: "bi bi-info-circle",
}

const iconClass = computed(() => props.icon || defaultIcons[props.variant])
</script>

<template>
  <div
    class="app-alert"
    :class="`app-alert--${variant}`"
    :role="role"
    :aria-live="role === 'alert' ? 'assertive' : 'polite'"
  >
    <span v-if="showIcon" class="app-alert__icon" aria-hidden="true">
      <i :class="iconClass"></i>
    </span>
    <div class="app-alert__content">
      <strong v-if="title" class="app-alert__title">{{ title }}</strong>
      <div class="app-alert__message">
        <slot>{{ message }}</slot>
      </div>
    </div>
    <button
      v-if="dismissible"
      type="button"
      class="app-alert__close pressable"
      :aria-label="closeLabel"
      @click="emit('close')"
    >
      <i class="bi bi-x-lg" aria-hidden="true"></i>
    </button>
  </div>
</template>

<style scoped>
.app-alert {
  display: flex;
  align-items: flex-start;
  gap: var(--wallo-space-3);
  padding: var(--wallo-space-3) var(--wallo-space-4);
  border: 1px solid;
  border-radius: var(--wallo-radius-md);
  line-height: 1.5;
}

.app-alert--info {
  color: var(--wallo-color-finance-info);
  background: color-mix(in srgb, var(--wallo-color-finance-info) 8%, var(--wallo-color-surface));
  border-color: color-mix(in srgb, var(--wallo-color-finance-info) 24%, var(--wallo-color-surface));
}

.app-alert--success {
  color: #107653;
  background: color-mix(in srgb, var(--wallo-color-success) 10%, var(--wallo-color-surface));
  border-color: color-mix(in srgb, var(--wallo-color-success) 28%, var(--wallo-color-surface));
}

.app-alert--warning {
  color: #805b00;
  background: color-mix(in srgb, var(--wallo-color-warning) 12%, var(--wallo-color-surface));
  border-color: color-mix(in srgb, var(--wallo-color-warning) 30%, var(--wallo-color-surface));
}

.app-alert--danger {
  color: #a52834;
  background: color-mix(in srgb, var(--wallo-color-danger) 9%, var(--wallo-color-surface));
  border-color: color-mix(in srgb, var(--wallo-color-danger) 26%, var(--wallo-color-surface));
}

.app-alert--neutral {
  color: var(--wallo-color-text);
  background: var(--wallo-color-surface-soft);
  border-color: var(--wallo-color-border-soft);
}

.app-alert__icon {
  flex: 0 0 auto;
  font-size: 1.15rem;
  line-height: 1.35;
}

.app-alert__content {
  flex: 1;
  min-width: 0;
}

.app-alert__title {
  display: block;
  margin-bottom: var(--wallo-space-1);
  font-weight: 800;
}

.app-alert__message {
  min-width: 0;
}

.app-alert__close {
  flex: 0 0 auto;
  padding: 2px;
  color: currentColor;
  background: transparent;
  border: 0;
  line-height: 1;
  opacity: 0.72;
  cursor: pointer;
}

.app-alert__close:hover {
  opacity: 1;
}

.app-alert__close:focus-visible {
  outline: 0;
  box-shadow: var(--wallo-focus-ring);
  border-radius: var(--wallo-radius-sm);
}
</style>
