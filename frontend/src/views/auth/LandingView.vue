<script setup>
import { onMounted, onUnmounted, ref } from "vue"

const loadingDots = [0, 1, 2, 3, 4]
const activeDot = ref(0)
let dotTimer

onMounted(() => {
  dotTimer = window.setInterval(() => {
    activeDot.value = (activeDot.value + 1) % loadingDots.length
  }, 650)
})

onUnmounted(() => {
  window.clearInterval(dotTimer)
})
</script>

<template>
  <main class="landing-page" aria-live="polite">
    <section class="landing-loader" aria-labelledby="landing-title">
      <div class="landing-visual" aria-hidden="true">
        <div class="landing-stars-motion landing-stars-motion-primary">
          <img
            class="landing-stars-art"
            src="/images/landing/wallo-stars.svg"
            alt=""
            width="360"
            height="230"
            loading="eager"
            decoding="async"
          />
        </div>
        <div class="landing-stars-motion landing-stars-motion-secondary">
          <img
            class="landing-stars-art"
            src="/images/landing/wallo-stars.svg"
            alt=""
            width="360"
            height="230"
            loading="eager"
            decoding="async"
          />
        </div>
        <div class="landing-character-motion">
          <img
            class="landing-generated-art"
            src="/images/landing/wallo-flying-generated.svg"
            alt=""
            width="787"
            height="479"
            loading="eager"
            decoding="async"
          />
        </div>
      </div>

      <div class="landing-copy">
        <h1 id="landing-title"><span>왈로</span> 페이지 불러오는 중이에요</h1>
        <p>잠시만 기다려주세요.</p>
      </div>

      <div class="landing-dots" aria-label="페이지 준비 중">
        <span
          v-for="(dot, index) in loadingDots"
          :key="dot"
          class="landing-dot"
          :class="{ 'landing-dot-active': index === activeDot }"
        ></span>
      </div>

    </section>
  </main>
</template>

<style scoped>
.landing-page {
  min-height: 100vh;
  overflow: hidden;
  color: #202430;
  background: #ffffff;
}

.landing-loader {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px 20px 68px;
  text-align: center;
}

.landing-visual {
  position: relative;
  overflow: hidden;
  width: min(700px, 100vw);
  height: clamp(300px, 44vw, 430px);
  margin-bottom: 72px;
}

.landing-stars-motion {
  position: absolute;
  top: 42%;
  left: 0;
  z-index: 0;
  width: min(700px, 100vw);
  aspect-ratio: 360 / 230;
  will-change: transform;
  animation: star-sweep 0.75s linear infinite;
}

.landing-stars-motion-secondary {
  animation-delay: -0.525s;
  opacity: 0.72;
}

.landing-stars-art {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.landing-character-motion {
  position: relative;
  z-index: 1;
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  transform-origin: center;
  will-change: transform;
  animation: character-flight 1.35s ease-in-out infinite;
}

.landing-generated-art {
  display: block;
  width: 79%;
  height: 79%;
  object-fit: contain;
}

.landing-copy h1 {
  margin: 0;
  color: #202430;
  font-size: clamp(1.65rem, 3.2vw, 2rem);
  font-weight: 850;
  letter-spacing: -0.07em;
  line-height: 1.4;
}

.landing-copy {
  position: relative;
  z-index: 1;
}

.landing-copy h1 span {
  color: #5967e9;
}

.landing-copy p {
  margin: 12px 0 0;
  color: #7c8497;
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: -0.04em;
}

.landing-dots {
  display: flex;
  gap: 14px;
  margin-top: 36px;
}

.landing-dot {
  display: block;
  width: 11px;
  height: 11px;
  background: #ded9ff;
  border-radius: 50%;
  opacity: 0.55;
  transform: scale(0.88);
  transition:
    background-color 0.25s ease,
    box-shadow 0.25s ease,
    opacity 0.25s ease,
    transform 0.25s ease;
}

.landing-dot-active {
  background: #6859e8;
  box-shadow: 0 0 0 3px rgba(104, 89, 232, 0.1);
  opacity: 1;
  transform: scale(1.18);
}

@keyframes character-flight {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }
  50% {
    transform: translate3d(0, -16px, 0);
  }
}

@keyframes star-sweep {
  0% {
    transform: translate3d(100%, -50%, 0);
  }
  100% {
    transform: translate3d(-100%, -50%, 0);
  }
}

@keyframes dot-pulse {
  0%,
  100% {
    opacity: 0.45;
    transform: scale(0.88);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 576px) {
  .landing-loader {
    padding-bottom: 52px;
  }

  .landing-visual {
    width: 100vw;
    height: 310px;
    margin-bottom: 52px;
  }

  .landing-copy h1 {
    font-size: 1.55rem;
  }

  .landing-generated-art {
    width: 77%;
    height: 77%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .landing-character-motion {
    animation: none;
  }

  .landing-stars-motion {
    animation: none;
    transform: translate3d(0, -50%, 0);
  }

  .landing-dot {
    transition: none;
  }
}
</style>
