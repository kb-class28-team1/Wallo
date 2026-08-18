<script setup>
import { computed } from "vue"
import AppButton from "./AppButton.vue"

const props = defineProps({
  type: {
    type: String,
    default: "empty",
    validator: (value) => ["loading", "empty", "error"].includes(value),
  },
  title: {
    type: String,
    default: "",
  },
  message: {
    type: String,
    default: "",
  },
  actionText: {
    type: String,
    default: "",
  },
  actionVariant: {
    type: String,
    default: "",
  },
  compact: {
    type: Boolean,
    default: false,
  },
  hideIcon: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(["action"])

const defaultContent = {
  loading: {
    title: "불러오는 중입니다",
    message: "잠시만 기다려 주세요.",
  },
  empty: {
    title: "표시할 내용이 없습니다",
    message: "조건을 변경하거나 새로운 내용을 추가해 보세요.",
  },
  error: {
    title: "문제가 발생했습니다",
    message: "잠시 후 다시 시도해 주세요.",
  },
}

const resolvedTitle = computed(() => props.title || defaultContent[props.type].title)
const resolvedMessage = computed(() => props.message || defaultContent[props.type].message)
const resolvedActionVariant = computed(() => {
  if (props.actionVariant) return props.actionVariant
  return props.type === "error" ? "danger" : "primary"
})
</script>

<template>
  <section
    class="app-state"
    :class="[`app-state--${type}`, { 'app-state--compact': compact }]"
    :data-state="type"
    :role="type === 'error' ? 'alert' : 'status'"
    aria-live="polite"
  >
    <div v-if="!hideIcon" class="app-state__icon" aria-hidden="true">
      <slot name="icon">
        <span v-if="type === 'loading'" class="app-state__spinner"></span>
        <i v-else-if="type === 'error'" class="bi bi-exclamation-circle"></i>
        <i v-else class="bi bi-inbox"></i>
      </slot>
    </div>
    <div class="app-state__content">
      <h2 class="app-state__title">{{ resolvedTitle }}</h2>
      <p class="app-state__message">
        <slot>{{ resolvedMessage }}</slot>
      </p>
      <div v-if="actionText || $slots.actions" class="app-state__actions">
        <AppButton
          v-if="actionText"
          :variant="resolvedActionVariant"
          size="sm"
          @click="emit('action', $event)"
        >
          {{ actionText }}
        </AppButton>
        <slot name="actions" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.app-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--wallo-space-4);
  min-height: 220px;
  padding: var(--wallo-space-6);
  color: var(--wallo-color-text);
  text-align: center;
  background: var(--wallo-color-surface);

}

.app-state--compact {
  min-height: 140px;
  padding: var(--wallo-space-5);
}

.app-state__icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  color: var(--wallo-color-primary);
  font-size: 1.8rem;
  border-radius: var(--wallo-radius-pill);
}

.app-state--error .app-state__icon {
  color: var(--wallo-color-danger);
  background: rgb(220 53 69 / 10%);
}

.app-state__spinner {
  width: 22px;
  height: 22px;
  border: 3px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: app-state-spin 700ms linear infinite;
}

.app-state__content {
  min-width: 0;
  max-width: 520px;
}

.app-state__title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 800;
}

.app-state__message {
  margin: var(--wallo-space-2) 0 0;
  color: var(--wallo-color-text-muted);
  line-height: 1.6;
}

.app-state__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--wallo-space-2);
  margin-top: var(--wallo-space-4);
}

@keyframes app-state-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 576px) {
  .app-state {
    flex-direction: column;
    padding: var(--wallo-space-5);
  }
}
</style>
