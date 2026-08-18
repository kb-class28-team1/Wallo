<script setup>
import { computed } from "vue"

const props = defineProps({
  value: {
    type: [Number, String],
    default: 0,
  },
  max: {
    type: [Number, String],
    default: 100,
  },
  label: {
    type: String,
    default: "",
  },
  showValue: {
    type: Boolean,
    default: false,
  },
  indeterminate: {
    type: Boolean,
    default: false,
  },
  variant: {
    type: String,
    default: "primary",
    validator: (value) => ["primary", "info", "success", "warning", "danger"].includes(value),
  },
  size: {
    type: String,
    default: "md",
    validator: (value) => ["sm", "md", "lg"].includes(value),
  },
  striped: {
    type: Boolean,
    default: false,
  },
  animated: {
    type: Boolean,
    default: false,
  },
})

function toFiniteNumber(value, fallback) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

const normalizedMax = computed(() => Math.max(toFiniteNumber(props.max, 100), 1))
const normalizedValue = computed(() =>
  Math.min(Math.max(toFiniteNumber(props.value, 0), 0), normalizedMax.value),
)
const percentage = computed(() => Math.round((normalizedValue.value / normalizedMax.value) * 100))
const displayValue = computed(() => (props.indeterminate ? "진행 중" : `${percentage.value}%`))
const progressClasses = computed(() => [
  `app-progress--${props.variant}`,
  `app-progress--${props.size}`,
  {
    "app-progress--indeterminate": props.indeterminate,
    "app-progress--striped": props.striped,
    "app-progress--animated": props.animated,
  },
])
</script>

<template>
  <div class="app-progress">
    <div v-if="label || showValue" class="app-progress__header">
      <span v-if="label" class="app-progress__label">{{ label }}</span>
      <span v-if="showValue" class="app-progress__value">{{ displayValue }}</span>
    </div>
    <div
      class="app-progress__track"
      :class="progressClasses"
      role="progressbar"
      aria-valuemin="0"
      :aria-valuemax="normalizedMax"
      :aria-valuenow="indeterminate ? undefined : normalizedValue"
      :aria-label="label || '진행률'"
    >
      <div
        class="app-progress__bar"
        :style="{ width: indeterminate ? undefined : `${percentage}%` }"
      ></div>
    </div>
  </div>
</template>

<style scoped>
.app-progress {
  min-width: 0;
}

.app-progress__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wallo-space-3);
  margin-bottom: var(--wallo-space-2);
}

.app-progress__label,
.app-progress__value {
  color: var(--wallo-color-text-muted);
  font-size: 0.875rem;
  font-weight: 700;
}

.app-progress__value {
  color: var(--wallo-color-text);
  font-variant-numeric: tabular-nums;
}

.app-progress__track {
  position: relative;
  overflow: hidden;
  width: 100%;
  background: var(--wallo-color-progress-track);
  border-radius: var(--wallo-radius-pill);
}

.app-progress--sm {
  height: 6px;
}

.app-progress--md {
  height: 10px;
}

.app-progress--lg {
  height: 14px;
}

.app-progress__bar {
  height: 100%;
  min-width: 0;
  background: var(--wallo-color-primary);
  border-radius: inherit;
  transition: width 240ms ease;
}

.app-progress--info .app-progress__bar {
  background: var(--wallo-color-finance-info);
}

.app-progress--success .app-progress__bar {
  background: var(--wallo-color-success);
}

.app-progress--warning .app-progress__bar {
  background: var(--wallo-color-warning);
}

.app-progress--danger .app-progress__bar {
  background: var(--wallo-color-danger);
}

.app-progress--striped .app-progress__bar {
  background-image: linear-gradient(
    135deg,
    rgb(255 255 255 / 18%) 25%,
    transparent 25%,
    transparent 50%,
    rgb(255 255 255 / 18%) 50%,
    rgb(255 255 255 / 18%) 75%,
    transparent 75%
  );
  background-size: 18px 18px;
}

.app-progress--animated .app-progress__bar {
  animation: app-progress-slide 900ms linear infinite;
}

.app-progress--indeterminate .app-progress__bar {
  width: 36%;
  animation: app-progress-indeterminate 1.2s ease-in-out infinite;
}

@keyframes app-progress-slide {
  from {
    background-position: 0 0;
  }

  to {
    background-position: 18px 0;
  }
}

@keyframes app-progress-indeterminate {
  0% {
    transform: translateX(-100%);
  }

  50% {
    transform: translateX(160%);
  }

  100% {
    transform: translateX(280%);
  }
}
</style>
