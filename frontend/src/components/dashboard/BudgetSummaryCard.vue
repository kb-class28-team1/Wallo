<script setup>
import { computed } from "vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppState from "@/components/ui/AppState.vue"
import { formatWon } from "@/utils/formatters"

const props = defineProps({
  budget: {
    type: Object,
    default: null,
  },
})
const emit = defineEmits(["open-budget-settings"])
const isBudgetConfigured = computed(() => Number(props.budget?.totalAmount ?? 0) > 0)
const budgetUsageRate = computed(() => {
  const totalAmount = Number(props.budget?.totalAmount ?? 0)
  const spentAmount = Number(props.budget?.spentAmount ?? 0)

  if (totalAmount <= 0) {
    return 0
  }

  return Math.round((spentAmount / totalAmount) * 100)
})
const visualUsageRate = computed(() => Math.min(100, Math.max(0, budgetUsageRate.value)))
const iceRemainingRate = computed(() => 100 - visualUsageRate.value)
const isBudgetOver = computed(
  () => Number(props.budget?.spentAmount ?? 0) > Number(props.budget?.totalAmount ?? 0),
)
const budgetRemaining = computed(
  () => Number(props.budget?.totalAmount ?? 0) - Number(props.budget?.spentAmount ?? 0),
)
</script>

<template>
  <AppCard class="budget-summary-card" padding="none">
    <div class="budget-card-body">
      <div class="d-flex align-items-start justify-content-between gap-3">
        <h2 class="h4 fw-bold mb-0">이번 달 예산</h2>
        <button
          type="button"
          class="btn app-action-link"
          @click="emit('open-budget-settings')"
        >
          설정하기
          <i class="bi bi-arrow-right ms-1" aria-hidden="true"></i>
        </button>
      </div>

      <template v-if="isBudgetConfigured">
        <div class="budget-content">
          <div class="budget-remaining-summary d-flex align-items-baseline justify-content-between gap-3 mb-3">
            <span class="budget-remaining-label">남은 예산</span>
            <strong class="budget-total" :class="{ 'text-danger': isBudgetOver }">
              {{ formatWon(budgetRemaining) }}
            </strong>
          </div>

          <div
            class="ice-budget-meter"
            :class="{ 'ice-budget-meter--over': isBudgetOver }"
            role="progressbar"
            aria-label="예산 소진율"
            :aria-valuenow="budgetUsageRate"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuetext="`예산의 ${budgetUsageRate}% 사용`"
          >
            <div class="snowstorm" aria-hidden="true">
              <span
                v-for="flake in 30"
                :key="flake"
                class="snowflake"
                :style="{
                  left: `${(flake * 37) % 102}%`,
                  top: `${(flake * 23) % 82}%`,
                  width: `${2.4 + (flake % 3) * 1.1}px`,
                  height: `${2.4 + (flake % 3) * 1.1}px`,
                  '--snow-delay': `${-(flake % 12) * 0.14}s`,
                  '--snow-duration': `${0.9 + (flake % 5) * 0.12}s`,
                  '--snow-drift': `${45 + (flake % 4) * 10}px`,
                }"
              ></span>
            </div>
            <div class="ice-visual" aria-hidden="true">
              <svg class="iceberg" viewBox="0 8 180 106" focusable="false">
                <defs>
                  <g id="budget-iceberg-large">
                    <polygon class="ice-face ice-face--light" points="0,72 10,36 25,20 46,17 64,29 76,72" />
                    <polygon class="ice-face ice-face--white" points="10,36 25,20 31,48 25,72 0,72" />
                    <polygon class="ice-face ice-face--mid" points="25,20 46,17 64,29 52,48 62,72 25,72 31,48" />
                    <polygon class="ice-face ice-face--deep" points="52,48 64,29 76,72 62,72" />
                  </g>
                  <g id="budget-iceberg-medium">
                    <polygon class="ice-face ice-face--light" points="0,60 9,31 24,15 39,28 55,60" />
                    <polygon class="ice-face ice-face--white" points="9,31 24,15 25,43 18,60 0,60" />
                    <polygon class="ice-face ice-face--mid" points="24,15 39,28 55,60 31,54 18,60 25,43" />
                    <polygon class="ice-face ice-face--deep" points="39,28 55,60 31,54" />
                  </g>
                  <g id="budget-iceberg-small">
                    <polygon class="ice-face ice-face--light" points="0,38 11,20 27,17 42,25 50,38" />
                    <polygon class="ice-face ice-face--white" points="11,20 27,17 22,38 0,38" />
                    <polygon class="ice-face ice-face--mid" points="27,17 42,25 50,38 22,38" />
                  </g>
                </defs>
                <g
                  class="meltwater"
                  :style="{
                    '--meltwater-scale-x': 0.78 + visualUsageRate * 0.0038,
                    '--meltwater-scale-y': 0.64 + visualUsageRate * 0.0048,
                    '--meltwater-opacity': 0.52 + visualUsageRate * 0.0048,
                  }"
                >
                  <path
                    class="meltwater-main"
                    d="M8 101 C18 95 34 96 45 91 C58 86 69 93 81 91 C94 88 101 94 113 92 C129 89 139 94 151 96 C162 98 174 100 172 105 C169 110 151 110 139 108 C125 106 116 111 101 109 C87 107 77 112 62 109 C48 106 37 111 25 108 C15 107 5 106 8 101 Z"
                  />
                  <path
                    class="meltwater-light"
                    d="M28 101 C43 97 53 99 65 96 C77 93 84 98 96 97 C109 95 117 98 130 99 C139 100 143 102 137 104 C126 106 116 102 105 105 C91 108 82 102 69 105 C56 108 45 103 33 105 C25 105 21 103 28 101 Z"
                  />
                  <path class="meltwater-ripple" d="M18 103 C31 100 40 102 50 100" />
                  <path class="meltwater-ripple" d="M128 103 C141 101 151 104 162 102" />
                </g>

                <g class="iceberg-capacity" aria-hidden="true">
                  <polygon
                    class="iceberg-capacity-outline"
                    points="84,97 94,61 109,45 130,42 148,54 160,97"
                  />
                  <polygon
                    class="iceberg-capacity-outline"
                    points="24,99 33,70 48,54 63,67 79,99"
                  />
                  <polygon
                    class="iceberg-capacity-outline"
                    points="12,101 23,83 39,80 54,88 62,101"
                  />
                  <polygon
                    class="iceberg-capacity-outline"
                    points="116,100 127,82 143,79 158,87 166,100"
                  />
                </g>
                <g class="iceberg-cluster">
                  <use
                    v-if="iceRemainingRate >= 75"
                    class="iceberg-piece iceberg-piece--one"
                    href="#budget-iceberg-large"
                    x="84"
                    y="25"
                  />
                  <use
                    v-if="iceRemainingRate >= 50"
                    class="iceberg-piece iceberg-piece--two"
                    href="#budget-iceberg-medium"
                    x="24"
                    y="39"
                  />
                  <use
                    v-if="iceRemainingRate >= 25"
                    class="iceberg-piece iceberg-piece--three"
                    href="#budget-iceberg-small"
                    x="12"
                    y="63"
                  />
                  <use
                    v-if="iceRemainingRate > 0"
                    class="iceberg-piece iceberg-piece--four"
                    href="#budget-iceberg-small"
                    x="116"
                    y="62"
                  />
                </g>
              </svg>
            </div>

            <div class="ice-budget-copy">
              <span class="ice-budget-caption">남은 비율</span>
              <strong
                class="budget-remaining-rate"
                :class="{ 'text-danger': isBudgetOver }"
              >
                {{ iceRemainingRate }}%
              </strong>
            </div>
          </div>
          <p class="budget-detail mb-0 mt-3">
            지출 <strong>{{ formatWon(budget.spentAmount) }}</strong> / 예산 <strong>{{ formatWon(budget.totalAmount) }}</strong>
          </p>
        </div>
      </template>

      <AppState
        v-else
        class="budget-state"
        type="empty"
        title="아직 설정된 예산이 없습니다."
        message="예산을 설정해주세요"
        compact
        hide-icon
      />
    </div>
  </AppCard>
</template>

<style scoped>
.budget-summary-card {
  border-radius: var(--wallo-radius-xl);
  background: var(--wallo-color-surface);
}

.budget-card-body {
  display: flex;
  flex-direction: column;
  min-height: 312px;
  padding: var(--wallo-space-5) var(--wallo-space-6);
}

.budget-content {
  margin-top: 24px;
}

.budget-detail,
.budget-remaining-rate {
  color: var(--wallo-color-text);
}

.budget-total {
  flex: 0 0 auto;
  color: var(--wallo-color-text);
  font-size: clamp(1.75rem, 3vw, 2.25rem);
  text-align: right;
  white-space: nowrap;
}

.budget-remaining-label {
  min-width: 0;
  color: var(--wallo-color-text);
  font-size: 1.15rem;
  font-weight: 600;
}

.ice-budget-meter {
  position: relative;
  display: grid;
  grid-template-columns: minmax(155px, 58%) minmax(92px, 1fr);
  align-items: center;
  gap: clamp(0.4rem, 1.5vw, 0.85rem);
  min-height: 128px;
  padding: 8px 16px;
  overflow: hidden;
  border-radius: var(--wallo-radius-lg);
  background:
    radial-gradient(circle at 15% 20%, #ffffff 0 22px, rgba(255, 255, 255, 0.24) 23px, transparent 25px),
    linear-gradient(
      to bottom,
      #91c5d8 0%,
      #9fcddd 23%,
      #acd3e0 23%,
      #acd3e0 40%,
      #c2e0e9 40%,
      #d8edf3 67%,
      #eef8fb 100%
    );
}

.ice-budget-meter::before,
.ice-budget-meter::after {
  position: absolute;
  right: 0;
  left: 0;
  z-index: 0;
  pointer-events: none;
  content: "";
}

.ice-budget-meter::before {
  bottom: 0;
  height: 39%;
  background: linear-gradient(
    to bottom,
    #f8fdff 0 17%,
    #7fc2d8 17% 26%,
    #2f91b5 26% 48%,
    #eaf7fa 48% 72%,
    #c9e8f1 72% 78%,
    #f8fdff 78% 100%
  );
  clip-path: polygon(0 14%, 24% 18%, 46% 10%, 66% 16%, 100% 8%, 100% 100%, 0 100%);
}

.ice-budget-meter::after {
  bottom: 21%;
  height: 9px;
  background: rgba(255, 255, 255, 0.82);
  clip-path: polygon(0 40%, 19% 5%, 43% 44%, 63% 12%, 82% 55%, 100% 20%, 100% 100%, 0 100%);
}

.ice-visual {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 260px;
  height: 128px;
  overflow: hidden;
  transform: translateY(-12px);
}

.iceberg {
  display: block;
  width: 100%;
  height: 174px;
  transform: translateY(-7px);
}

.meltwater {
  opacity: var(--meltwater-opacity);
  transform: scale(var(--meltwater-scale-x), var(--meltwater-scale-y));
  transform-origin: 90px 102px;
  transition: opacity 600ms ease, transform 600ms ease;
}

.meltwater-main {
  fill: #46b6d8;
}

.meltwater-light {
  fill: #bcecf7;
}

.meltwater-ripple {
  fill: none;
  stroke: rgba(255, 255, 255, 0.88);
  stroke-linecap: round;
  stroke-width: 1.6;
}

.iceberg-piece {
  transform-box: fill-box;
  transform-origin: center bottom;
  animation: iceberg-arrive 480ms ease-out both;
}

.iceberg-piece--two { animation-delay: 70ms; }
.iceberg-piece--three { animation-delay: 140ms; }
.iceberg-piece--four { animation-delay: 210ms; }

.iceberg-capacity-outline {
  fill: rgba(255, 255, 255, 0.08);
  stroke: rgba(38, 130, 164, 0.72);
  stroke-dasharray: 3.5 3;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.15;
  vector-effect: non-scaling-stroke;
}

.snowstorm {
  position: absolute;
  z-index: 2;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.snowflake {
  position: absolute;
  display: block;
  border: 1px solid rgba(72, 151, 181, 0.2);
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 0 2px rgba(57, 139, 171, 0.16);
  opacity: 0;
  animation: snow-blow var(--snow-duration) linear var(--snow-delay) infinite;
}

.ice-face {
  stroke: rgba(36, 133, 169, 0.2);
  stroke-linejoin: round;
  stroke-width: 0.45;
}

.ice-face--white { fill: #ffffff; }
.ice-face--light { fill: #c9eef7; }
.ice-face--mid { fill: #78c9df; }
.ice-face--deep { fill: #3199bd; }

.ice-budget-copy {
  position: relative;
  z-index: 1;
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.2rem;
  color: #143a5c;
  background: transparent;
  text-align: center;
  text-shadow:
    -1px -1px 0 rgba(255, 255, 255, 0.92),
    1px -1px 0 rgba(255, 255, 255, 0.92),
    -1px 1px 0 rgba(255, 255, 255, 0.92),
    1px 1px 0 rgba(255, 255, 255, 0.92),
    0 2px 4px rgba(34, 91, 115, 0.2);
}

.ice-budget-caption { color: #24566f; }
.ice-budget-caption {
  position: relative;
  display: inline-block;
  font-size: clamp(0.78rem, 2.4vw, 0.95rem);
  font-weight: 700;
  white-space: nowrap;
}
.budget-remaining-rate {
  position: relative;
  display: inline-block;
  flex: 0 0 auto;
  max-width: 100%;
  font-size: clamp(1.9rem, 5vw, 2.65rem);
  line-height: 1.15;
  color: #143a5c;
}

.ice-budget-copy .text-danger { color: #143a5c !important; }

.ice-budget-meter--over {
  border-color: rgba(220, 53, 69, 0.28);
  background: linear-gradient(135deg, rgba(255, 239, 241, 0.82), rgba(255, 250, 250, 0.4));
}

.ice-budget-meter--over .meltwater-main { fill: rgba(220, 53, 69, 0.22); }
.ice-budget-meter--over .meltwater-light { fill: rgba(255, 226, 230, 0.78); }

@keyframes iceberg-arrive {
  from { opacity: 0; transform: translateY(5px) scale(0.96); }
  to { opacity: 1; }
}

@keyframes snow-blow {
  0% {
    opacity: 0;
    transform: translate(28px, -18px) scale(0.7);
  }
  10% { opacity: 1; }
  78% { opacity: 0.9; }
  100% {
    opacity: 0;
    transform: translate(calc(var(--snow-drift) * -1), 52px) scale(1.2);
  }
}

.budget-state {
  display: flex;
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

@media (max-width: 991.98px) {
  .budget-card-body {
    min-height: auto;
    padding: var(--wallo-space-5);
  }
}

@media (max-width: 420px) {
  .ice-budget-meter {
    grid-template-columns: minmax(124px, 54%) minmax(78px, 1fr);
    gap: 0.5rem;
    min-height: 138px;
    padding-inline: 10px;
  }
  .ice-visual {
    height: 138px;
    transform: translateY(-8px);
  }
  .iceberg {
    height: 162px;
    transform: translateY(-6px);
  }
  .ice-budget-caption { font-size: 0.76rem; }
}

@media (prefers-reduced-motion: reduce) {
  .meltwater { transition: none; }
  .iceberg-piece { animation: none; }
  .snowflake { animation: none; opacity: 0.72; }
}
</style>
