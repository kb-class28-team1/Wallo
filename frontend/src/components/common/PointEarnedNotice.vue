<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue"

const NOTICE_DURATION_MS = 4200
const pointWCoin = "/images/profiles/point-w-coin.svg"
const point = ref(0)
const visible = ref(false)
let hideTimer = null

const clearHideTimer = () => {
  if (hideTimer !== null) {
    window.clearTimeout(hideTimer)
    hideTimer = null
  }
}

const hide = () => {
  clearHideTimer()
  visible.value = false
}

const handlePointEarned = (event) => {
  const earnedPoint = Number(event?.detail?.point) || 0
  if (earnedPoint <= 0) return

  point.value = earnedPoint
  visible.value = true
  clearHideTimer()
  hideTimer = window.setTimeout(hide, NOTICE_DURATION_MS)
}

onMounted(() => {
  window.addEventListener("wallo:point-earned", handlePointEarned)
})

onBeforeUnmount(() => {
  window.removeEventListener("wallo:point-earned", handlePointEarned)
  clearHideTimer()
})
</script>

<template>
  <Transition name="point-earned-notice">
    <div
      v-if="visible"
      class="point-earned-notice"
      role="alert"
      aria-live="polite"
      aria-atomic="true"
    >
      <img :src="pointWCoin" alt="" aria-hidden="true" />
      <span>포인트 {{ point.toLocaleString("ko-KR") }}P가 적립되었습니다.</span>
      <button type="button" aria-label="포인트 적립 알림 닫기" @click="hide">×</button>
    </div>
  </Transition>
</template>

<style scoped>
.point-earned-notice {
  position: fixed;
  top: 20px;
  left: 50%;
  z-index: 1700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  width: max-content;
  max-width: calc(100vw - 32px);
  padding: 12px 14px 12px 16px;
  border: 1px solid rgb(255 255 255 / 78%);
  border-radius: 999px;
  background: linear-gradient(135deg, #ecfff1, #c9f9d7);
  color: #08753b;
  box-shadow: 0 12px 30px rgb(21 147 72 / 24%);
  transform: translateX(-50%);
}

.point-earned-notice img {
  width: 27px;
  height: 27px;
  object-fit: contain;
  filter: drop-shadow(0 3px 3px rgb(7 117 59 / 18%));
}

.point-earned-notice span {
  overflow: hidden;
  font-weight: 800;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.point-earned-notice button {
  flex: 0 0 auto;
  padding: 0 0 2px;
  border: 0;
  background: transparent;
  color: #31905a;
  font-size: 1.35rem;
  line-height: 1;
}

.point-earned-notice-enter-active,
.point-earned-notice-leave-active {
  transition: opacity 260ms ease, transform 260ms cubic-bezier(0.22, 0.8, 0.24, 1);
}

.point-earned-notice-enter-from,
.point-earned-notice-leave-to {
  opacity: 0;
  transform: translate(-50%, -14px) scale(0.92);
}

@media (max-width: 767.98px) {
  .point-earned-notice {
    right: 16px;
    left: 16px;
    width: auto;
    max-width: none;
    transform: none;
  }

  .point-earned-notice-enter-from,
  .point-earned-notice-leave-to {
    transform: translateY(-14px) scale(0.92);
  }
}

@media (prefers-reduced-motion: reduce) {
  .point-earned-notice-enter-active,
  .point-earned-notice-leave-active {
    transition-duration: 1ms;
  }
}
</style>
