<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue"
import { useRouter } from "vue-router"

const router = useRouter()
const welcomePenguinImage = "/images/onboarding/welcome-penguin.png"
const greeting = "안녕하세요, 왈로예요!"
const introduction = "지금부터 서비스를 사용하는 방법을 알려드릴게요."
const typedGreeting = ref("")
const typedIntroduction = ref("")
const isTypingComplete = ref(false)
const currentStep = ref(1)
const totalSteps = 6
const timers = []

const stepContent = {
  2: {
    eyebrow: "STEP 01 · 자산 연동",
    title: "흩어진 자산을 한 번에 연결해요",
    description:
      "은행과 카드 정보를 연결하면 왈로가 자산과 소비 내역을 자동으로 모아 보여드려요.",
  },
  3: {
    eyebrow: "STEP 02 · 대시보드",
    title: "내 금융 생활을 한눈에 확인해요",
    description:
      "대시보드 한 화면에서 자산 변화, 이번 달 예산과 지출, 금융 목표의 진행 상황을 모두 확인할 수 있어요.",
  },
  4: {
    eyebrow: "STEP 03 · AI 금융 도우미",
    title: "AI가 나에게 필요한 답을 찾아줘요",
    description:
      "대화만으로 금융 목표를 세우고 자산과 소비를 분석하며, 내 상황에 맞는 금융 상품도 추천받을 수 있어요.",
  },
  5: {
    eyebrow: "STEP 04 · 금융 리포트",
    title: "어려운 금융 뉴스를 쉽게 읽어요",
    description:
      "최신 금융·경제 뉴스를 AI가 핵심만 요약해 드려요. 낯선 금융 용어의 뜻도 함께 확인할 수 있어요.",
  },
  6: {
    eyebrow: "STEP 05 · 챌린지와 포인트",
    title: "함께 절약하고 보상도 받아요",
    description:
      "친구들과 절약 챌린지에 참여하고 미션을 달성해 포인트를 모아보세요. 모은 포인트는 포인트 숍에서 사용할 수 있어요.",
  },
}

const activeContent = computed(() => stepContent[currentStep.value] ?? null)
const isLastStep = computed(() => currentStep.value === totalSteps)

const schedule = (callback, delay) => {
  const timer = window.setTimeout(callback, delay)
  timers.push(timer)
}

const typeLine = (text, target, onComplete, index = 0) => {
  if (index >= text.length) {
    onComplete?.()
    return
  }

  target.value += text[index]
  schedule(() => typeLine(text, target, onComplete, index + 1), 70)
}

const showFullCopy = () => {
  typedGreeting.value = greeting
  typedIntroduction.value = introduction
  isTypingComplete.value = true
}

const startTyping = () => {
  typeLine(greeting, typedGreeting, () => {
    schedule(() => {
      typeLine(introduction, typedIntroduction, () => {
        isTypingComplete.value = true
      })
    }, 280)
  })
}

const goNext = async () => {
  if (isLastStep.value) {
    await router.push({ name: "connection" })
    return
  }

  currentStep.value += 1
}

const goBack = () => {
  if (currentStep.value > 1) {
    currentStep.value -= 1
  }
}

onMounted(() => {
  if (window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches) {
    showFullCopy()
    return
  }

  schedule(startTyping, 500)
})

onBeforeUnmount(() => {
  timers.forEach((timer) => window.clearTimeout(timer))
})
</script>

<template>
  <main class="onboarding-page">
    <div
      class="onboarding-progress"
      :aria-label="`온보딩 ${totalSteps}단계 중 ${currentStep}단계`"
    >
      <span
        v-for="step in totalSteps"
        :key="step"
        class="progress-step"
        :class="{ 'progress-step--active': step === currentStep }"
      ></span>
    </div>

    <Transition name="onboarding-step" mode="out-in">
      <section
        v-if="currentStep === 1"
        key="welcome"
        class="welcome-layout"
        aria-labelledby="welcome-title"
      >
      <div class="penguin-stage" aria-hidden="true">
        <span class="speech-bubble speech-bubble--hello">안녕!</span>
        <span class="speech-bubble speech-bubble--welcome">반가워요</span>
        <span class="speech-bubble speech-bubble--wave">👋</span>
        <div class="penguin-motion">
          <img
            class="welcome-penguin"
            :src="welcomePenguinImage"
            alt=""
            width="1024"
            height="1365"
            decoding="async"
          />
        </div>
        <span class="penguin-shadow"></span>
      </div>

      <div class="welcome-copy">
        <p class="eyebrow">WELCOME TO WALLO</p>
        <h1 id="welcome-title" class="visually-hidden">
          {{ greeting }} {{ introduction }}
        </h1>
        <div class="typing-copy" aria-hidden="true">
          <p class="greeting-line">
            {{ typedGreeting }}<span v-if="!typedGreeting" class="typing-placeholder">&nbsp;</span>
            <span v-if="typedGreeting.length < greeting.length" class="typing-cursor"></span>
          </p>
          <p class="introduction-line">
            {{ typedIntroduction }}<span v-if="!typedIntroduction" class="typing-placeholder">&nbsp;</span>
            <span
              v-if="typedGreeting.length === greeting.length && !isTypingComplete"
              class="typing-cursor"
            ></span>
          </p>
        </div>

        <p class="welcome-description">
          어렵게 느껴졌던 자산 관리도 왈로와 함께라면 가볍게 시작할 수 있어요.
        </p>

        <button class="btn btn-primary next-button" type="button" @click="goNext">
          다음
          <i class="bi bi-arrow-right" aria-hidden="true"></i>
        </button>
      </div>
      </section>

      <section
        v-else
        :key="currentStep"
        class="guide-layout"
        :aria-labelledby="`guide-title-${currentStep}`"
      >
        <div v-if="currentStep === 2" class="connection-preview" aria-hidden="true">
          <div class="connection-glow"></div>
          <div class="connection-card">
            <div class="connection-card-header">
              <span class="connection-icon"><i class="bi bi-shield-lock-fill"></i></span>
              <div>
                <strong>내 자산 연결하기</strong>
                <span>안전하게 불러올게요</span>
              </div>
            </div>
            <div class="institution-list">
              <div class="institution-item">
                <span class="institution-logo institution-logo--bank">B</span>
                <span><strong>은행 계좌</strong><small>예금 · 적금 · 대출</small></span>
                <i class="bi bi-check-circle-fill"></i>
              </div>
              <div class="institution-item">
                <span class="institution-logo institution-logo--card">C</span>
                <span><strong>카드</strong><small>결제 · 소비 내역</small></span>
                <i class="bi bi-check-circle-fill"></i>
              </div>
              <div class="institution-item">
                <span class="institution-logo institution-logo--invest">S</span>
                <span><strong>투자 자산</strong><small>주식 · 펀드</small></span>
                <i class="bi bi-check-circle-fill"></i>
              </div>
            </div>
            <div class="connection-security">
              <i class="bi bi-lock-fill"></i>
              자산 정보는 안전하게 보호돼요
            </div>
          </div>
          <span class="connection-callout connection-callout--left">한 번에 연결</span>
          <span class="connection-callout connection-callout--right">자동으로 정리</span>
        </div>

        <div v-else-if="currentStep === 3" class="dashboard-preview" aria-hidden="true">
          <aside class="preview-sidebar">
            <span class="preview-brand">W</span>
            <span class="preview-nav active"><i class="bi bi-grid-fill"></i>대시보드</span>
            <span class="preview-nav"><i class="bi bi-wallet2"></i>자산</span>
            <span class="preview-nav"><i class="bi bi-stars"></i>AI 컨설팅</span>
            <span class="preview-nav"><i class="bi bi-trophy"></i>챌린지</span>
          </aside>
          <div class="preview-main">
            <div class="preview-header">
              <div><small>안녕하세요, 왈로님</small><strong>나의 대시보드</strong></div>
              <span class="preview-profile"><i class="bi bi-person-fill"></i></span>
            </div>
            <div class="preview-grid preview-grid--top">
              <article
                class="preview-card preview-card--asset is-highlighted"
              >
                <span class="card-label">총 자산</span>
                <strong>32,480,000원</strong>
                <div class="mini-chart">
                  <i v-for="height in [32, 42, 38, 55, 64, 76]" :key="height" :style="{ height: `${height}%` }"></i>
                </div>
                <span class="feature-badge">자산 현황</span>
              </article>
              <article
                class="preview-card preview-card--budget is-highlighted"
              >
                <span class="card-label">이번 달 예산</span>
                <strong>68% 사용</strong>
                <div class="budget-ring"><span>32%</span></div>
                <span class="feature-badge">예산 관리</span>
              </article>
            </div>
            <div class="preview-grid preview-grid--bottom">
              <article
                class="preview-card preview-card--expense is-highlighted"
              >
                <span class="card-label">카테고리별 소비</span>
                <div class="expense-row"><span>식비</span><i style="width: 78%"></i></div>
                <div class="expense-row"><span>쇼핑</span><i style="width: 52%"></i></div>
                <div class="expense-row"><span>교통</span><i style="width: 36%"></i></div>
                <span class="feature-badge">소비 현황</span>
              </article>
              <article
                class="preview-card preview-card--goal is-highlighted"
              >
                <span class="card-label">나의 금융 목표</span>
                <strong>여행 자금 모으기</strong>
                <div class="goal-progress"><i></i></div>
                <small>목표까지 64%</small>
                <span class="feature-badge">금융 목표</span>
              </article>
            </div>
          </div>
        </div>

        <div v-else-if="currentStep === 4" class="ai-preview" aria-hidden="true">
          <div class="service-preview-header">
            <span class="service-preview-icon"><i class="bi bi-stars"></i></span>
            <div><small>AI CONSULTING</small><strong>무엇을 도와드릴까요?</strong></div>
          </div>
          <div class="ai-feature-grid">
            <article class="ai-feature-card ai-feature-card--goal">
              <span><i class="bi bi-bullseye"></i></span>
              <div><strong>목표 설정</strong><small>대화로 목표와 로드맵 만들기</small></div>
              <i class="bi bi-arrow-right"></i>
            </article>
            <article class="ai-feature-card ai-feature-card--asset">
              <span><i class="bi bi-pie-chart-fill"></i></span>
              <div><strong>자산 분석</strong><small>자산 구성과 재무 상태 진단</small></div>
              <i class="bi bi-arrow-right"></i>
            </article>
            <article class="ai-feature-card ai-feature-card--spending">
              <span><i class="bi bi-bar-chart-line-fill"></i></span>
              <div><strong>소비 분석</strong><small>지출 습관과 개선 방법 확인</small></div>
              <i class="bi bi-arrow-right"></i>
            </article>
            <article class="ai-feature-card ai-feature-card--product">
              <span><i class="bi bi-gift-fill"></i></span>
              <div><strong>금융 상품 추천</strong><small>나에게 맞는 예·적금 찾기</small></div>
              <i class="bi bi-arrow-right"></i>
            </article>
          </div>
          <div class="ai-chat-bar"><span>궁금한 금융 이야기를 입력해 주세요</span><i class="bi bi-arrow-up"></i></div>
        </div>

        <div v-else-if="currentStep === 5" class="report-preview" aria-hidden="true">
          <div class="report-preview-header">
            <div><small>FINANCIAL REPORT</small><strong>금융 리포트</strong></div>
            <span><i class="bi bi-stars"></i> AI 핵심 요약</span>
          </div>
          <article class="report-featured-card">
            <div class="report-card-meta"><span>금융시장</span><span>NEW</span></div>
            <h2>금리 변화가 내 예금과 대출에 미치는 영향</h2>
            <p>복잡한 금융 뉴스를 내 자산 관리에 필요한 내용 중심으로 쉽고 짧게 정리해 드려요.</p>
            <div class="report-source"><span>경제 뉴스</span><span>·</span><span>오늘</span></div>
          </article>
          <div class="report-insight-grid">
            <div class="report-insight-card">
              <span class="report-insight-icon"><i class="bi bi-lightning-charge-fill"></i></span>
              <div><small>3줄 핵심 요약</small><strong>바쁜 순간에도 빠르게</strong></div>
            </div>
            <div class="report-insight-card">
              <span class="report-insight-icon"><i class="bi bi-book-fill"></i></span>
              <div><small>금융 용어 설명</small><strong>낯선 표현도 알기 쉽게</strong></div>
            </div>
          </div>
        </div>

        <div v-else class="reward-preview" aria-hidden="true">
          <div class="challenge-panel">
            <div class="challenge-panel-header">
              <span class="challenge-symbol">💰</span>
              <div><small>MY SAVING CHALLENGE</small><strong>일주일 생활비 줄이기</strong></div>
              <span class="challenge-status">진행 중</span>
            </div>
            <div class="challenge-stat-grid">
              <div><i class="bi bi-check-circle-fill"></i><strong>5개</strong><small>완료한 미션</small></div>
              <div><i class="bi bi-people-fill"></i><strong>8명</strong><small>함께하는 친구</small></div>
              <div><i class="bi bi-trophy-fill"></i><strong>3위</strong><small>이번 주 순위</small></div>
            </div>
            <div class="challenge-feed-row"><span class="feed-avatar">W</span><div><strong>오늘 커피값 아끼기 성공!</strong><small>방금 전 · +100P</small></div><i class="bi bi-heart-fill"></i></div>
          </div>
          <div class="point-panel">
            <div class="point-balance"><span><i class="bi bi-coin"></i> 보유 포인트</span><strong>2,450 P</strong></div>
            <div class="point-products">
              <div><span>☕</span><small>커피 쿠폰</small><strong>1,500P</strong></div>
              <div><span>🎁</span><small>랜덤 박스</small><strong>500P</strong></div>
              <div><span>🍦</span><small>간식 쿠폰</small><strong>1,000P</strong></div>
            </div>
          </div>
        </div>

        <div class="guide-copy">
          <p class="eyebrow">{{ activeContent.eyebrow }}</p>
          <h1 :id="`guide-title-${currentStep}`">{{ activeContent.title }}</h1>
          <p>{{ activeContent.description }}</p>

          <div v-if="currentStep === 2" class="guide-points">
            <span><i class="bi bi-check2"></i> 여러 금융기관 통합 조회</span>
            <span><i class="bi bi-check2"></i> 소비 내역 자동 분류</span>
          </div>
          <div v-else-if="currentStep === 3" class="guide-points">
            <span><i class="bi bi-check2"></i> 자산 · 예산 · 소비 현황</span>
            <span><i class="bi bi-check2"></i> 금융 목표 진행률 관리</span>
          </div>
          <div v-else-if="currentStep === 4" class="guide-points">
            <span><i class="bi bi-check2"></i> 목표 설정 · 자산 분석 · 소비 분석</span>
            <span><i class="bi bi-check2"></i> 맞춤 금융 상품 추천</span>
          </div>
          <div v-else-if="currentStep === 5" class="guide-points">
            <span><i class="bi bi-check2"></i> 최신 금융·경제 뉴스 요약</span>
            <span><i class="bi bi-check2"></i> 문맥에 맞는 금융 용어 설명</span>
          </div>
          <div v-else class="guide-points">
            <span><i class="bi bi-check2"></i> 친구와 함께하는 절약 챌린지</span>
            <span><i class="bi bi-check2"></i> 미션 포인트와 포인트 숍</span>
          </div>

          <div class="guide-actions">
            <button class="btn back-button" type="button" @click="goBack">
              <i class="bi bi-arrow-left" aria-hidden="true"></i>
              이전
            </button>
            <button class="btn btn-primary next-button" type="button" @click="goNext">
              {{ isLastStep ? "자산 연동 시작하기" : "다음" }}
              <i class="bi bi-arrow-right" aria-hidden="true"></i>
            </button>
          </div>
        </div>
      </section>
    </Transition>
  </main>
</template>

<style scoped>
.onboarding-page {
  position: relative;
  display: grid;
  min-height: 100vh;
  overflow-x: hidden;
  overflow-y: auto;
  padding: clamp(32px, 5vw, 72px);
  background:
    radial-gradient(circle at 18% 24%, rgb(133 188 255 / 22%), transparent 28%),
    radial-gradient(circle at 82% 78%, rgb(173 223 255 / 25%), transparent 26%),
    linear-gradient(135deg, #f8fbff 0%, #eef7ff 48%, #ffffff 100%);
}

.onboarding-page::before,
.onboarding-page::after {
  position: absolute;
  width: 260px;
  height: 260px;
  content: "";
  border: 1px solid rgb(79 143 232 / 16%);
  border-radius: 50%;
}

.onboarding-page::before {
  top: -150px;
  right: 12%;
}

.onboarding-page::after {
  bottom: -190px;
  left: 40%;
  width: 340px;
  height: 340px;
}

.onboarding-progress {
  position: absolute;
  top: clamp(24px, 4vw, 48px);
  left: 50%;
  z-index: 3;
  display: flex;
  gap: 8px;
  transform: translateX(-50%);
}

.progress-step {
  width: 9px;
  height: 9px;
  background: #cddced;
  border-radius: var(--wallo-radius-pill);
  transition: width 0.25s ease, background-color 0.25s ease;
}

.progress-step--active {
  width: 30px;
  background: var(--wallo-color-primary);
}

.welcome-layout {
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(360px, 0.95fr) minmax(420px, 1.05fr);
  gap: clamp(48px, 8vw, 128px);
  width: min(1240px, 100%);
  margin: auto;
  align-items: center;
}

.penguin-stage {
  position: relative;
  display: grid;
  min-height: min(690px, 72vh);
  place-items: end center;
}

.penguin-motion {
  z-index: 2;
  width: min(520px, 100%);
  transform-origin: 50% 88%;
  animation: penguin-greeting 2.8s ease-in-out infinite;
}

.welcome-penguin {
  display: block;
  width: 100%;
  height: auto;
  filter: drop-shadow(0 24px 32px rgb(41 82 125 / 16%));
}

.penguin-shadow {
  position: absolute;
  bottom: 3px;
  left: 50%;
  z-index: 1;
  width: 58%;
  height: 28px;
  background: rgb(35 72 111 / 14%);
  border-radius: 50%;
  filter: blur(11px);
  transform: translateX(-50%);
  animation: shadow-breathe 2.8s ease-in-out infinite;
}

.speech-bubble {
  position: absolute;
  z-index: 3;
  padding: 10px 17px;
  color: var(--wallo-color-primary-hover);
  font-size: clamp(0.85rem, 1.4vw, 1rem);
  font-weight: 700;
  background: rgb(255 255 255 / 92%);
  border: 1px solid rgb(79 143 232 / 18%);
  border-radius: var(--wallo-radius-pill);
  box-shadow: var(--wallo-shadow-card);
  opacity: 0;
  animation: bubble-float 3.2s ease-in-out infinite;
}

.speech-bubble--hello {
  top: 17%;
  left: 2%;
}

.speech-bubble--welcome {
  top: 7%;
  left: 28%;
  animation-delay: 0.8s;
}

.speech-bubble--wave {
  top: 31%;
  left: 3%;
  padding: 8px 12px;
  font-size: 1.25rem;
  animation-delay: 1.55s;
}

.welcome-copy {
  min-width: 0;
  max-width: 640px;
}

.eyebrow {
  margin-bottom: 18px;
  color: var(--wallo-color-primary);
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.18em;
}

.typing-copy {
  min-height: 92px;
  color: var(--wallo-color-text);
  letter-spacing: -0.045em;
  overflow-wrap: anywhere;
  word-break: keep-all;
}

.greeting-line,
.introduction-line {
  margin: 0;
  font-weight: 800;
  line-height: 1.35;
  white-space: nowrap;
}

.greeting-line {
  color: var(--wallo-color-primary);
  font-size: clamp(1.65rem, 2.55vw, 2.65rem);
}

.introduction-line {
  margin-top: 12px;
  font-size: clamp(1rem, 1.4vw, 1.45rem);
}

.typing-placeholder {
  visibility: hidden;
}

.typing-cursor {
  display: inline-block;
  width: 3px;
  height: 0.92em;
  margin-left: 5px;
  vertical-align: -0.05em;
  background: var(--wallo-color-primary);
  animation: cursor-blink 0.7s step-end infinite;
}

.welcome-description {
  max-width: 520px;
  margin: 14px 0 0;
  color: var(--wallo-color-text-muted);
  font-size: clamp(1rem, 1.4vw, 1.2rem);
  line-height: 1.8;
}

.next-button {
  display: inline-flex;
  min-width: 152px;
  min-height: 52px;
  margin-top: 40px;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 12px 28px;
  font-weight: 700;
  border: 0;
  border-radius: var(--wallo-radius-pill);
  box-shadow: 0 12px 26px rgb(79 143 232 / 24%);
}

.next-button:hover .bi-arrow-right {
  transform: translateX(4px);
}

.next-button .bi-arrow-right {
  transition: transform 0.2s ease;
}

.guide-layout {
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(520px, 1.2fr) minmax(360px, 0.8fr);
  gap: clamp(48px, 7vw, 104px);
  width: min(1280px, 100%);
  margin: auto;
  align-items: center;
}

.guide-copy {
  min-width: 0;
  max-width: 520px;
}

.guide-copy h1 {
  margin: 0;
  color: var(--wallo-color-text);
  font-size: clamp(2rem, 3.2vw, 3.35rem);
  font-weight: 800;
  letter-spacing: -0.055em;
  line-height: 1.3;
  word-break: keep-all;
}

.guide-copy > p:not(.eyebrow) {
  margin: 22px 0 0;
  color: var(--wallo-color-text-muted);
  font-size: clamp(1rem, 1.3vw, 1.16rem);
  line-height: 1.8;
  word-break: keep-all;
}

.guide-points {
  display: grid;
  gap: 12px;
  margin-top: 28px;
}

.guide-points span {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--wallo-color-text);
  font-weight: 700;
}

.guide-points i {
  display: grid;
  width: 24px;
  height: 24px;
  color: #fff;
  background: var(--wallo-color-primary);
  border-radius: 50%;
  place-items: center;
}

.guide-actions {
  display: flex;
  gap: 12px;
  margin-top: 38px;
}

.guide-actions .next-button {
  margin-top: 0;
}

.back-button {
  display: inline-flex;
  min-height: 52px;
  align-items: center;
  gap: 9px;
  padding: 12px 22px;
  color: var(--wallo-color-text-muted);
  font-weight: 700;
  background: rgb(255 255 255 / 70%);
  border: 1px solid var(--wallo-color-border);
  border-radius: var(--wallo-radius-pill);
}

.back-button:hover {
  color: var(--wallo-color-primary-hover);
  background: #fff;
  border-color: var(--wallo-color-primary);
}

.connection-preview {
  position: relative;
  display: grid;
  min-height: 620px;
  place-items: center;
}

.connection-glow {
  position: absolute;
  width: 460px;
  height: 460px;
  background: radial-gradient(circle, rgb(94 164 244 / 30%), transparent 68%);
  border-radius: 50%;
  animation: connection-glow 3s ease-in-out infinite;
}

.connection-card {
  z-index: 1;
  width: min(440px, 90%);
  padding: 28px;
  background: rgb(255 255 255 / 94%);
  border: 1px solid rgb(255 255 255 / 80%);
  border-radius: 30px;
  box-shadow: 0 30px 80px rgb(46 103 161 / 18%);
  transform: rotate(-1.5deg);
  animation: card-hover 3.4s ease-in-out infinite;
}

.connection-card-header {
  display: flex;
  align-items: center;
  gap: 15px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--wallo-color-border-soft);
}

.connection-card-header > div {
  display: grid;
  gap: 4px;
}

.connection-card-header strong {
  color: var(--wallo-color-text);
  font-size: 1.16rem;
}

.connection-card-header span:not(.connection-icon) {
  color: var(--wallo-color-text-muted);
  font-size: 0.86rem;
}

.connection-icon {
  display: grid;
  width: 52px;
  height: 52px;
  color: #fff;
  font-size: 1.3rem;
  background: linear-gradient(135deg, #4f8fe8, #78b9f3);
  border-radius: 17px;
  place-items: center;
}

.institution-list {
  display: grid;
  gap: 10px;
  padding: 20px 0;
}

.institution-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 12px;
  padding: 11px 12px;
  align-items: center;
  background: var(--wallo-color-surface-soft);
  border-radius: 15px;
}

.institution-item > span:nth-child(2) {
  display: grid;
  gap: 2px;
}

.institution-item strong {
  color: var(--wallo-color-text);
  font-size: 0.9rem;
}

.institution-item small {
  color: var(--wallo-color-text-muted);
  font-size: 0.72rem;
}

.institution-item > i {
  color: var(--wallo-color-success);
}

.institution-logo {
  display: grid;
  width: 38px;
  height: 38px;
  color: #fff;
  font-size: 0.8rem;
  font-weight: 800;
  border-radius: 12px;
  place-items: center;
}

.institution-logo--bank { background: #4f8fe8; }
.institution-logo--card { background: #806fe9; }
.institution-logo--invest { background: #19b98a; }

.connection-security {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--wallo-color-finance-info);
  font-size: 0.82rem;
  font-weight: 700;
  background: var(--wallo-color-info-bg);
  border-radius: 13px;
}

.connection-callout {
  position: absolute;
  z-index: 2;
  padding: 11px 17px;
  color: var(--wallo-color-finance-info);
  font-size: 0.86rem;
  font-weight: 800;
  background: #fff;
  border-radius: var(--wallo-radius-pill);
  box-shadow: var(--wallo-shadow-card);
  animation: bubble-float 3.2s ease-in-out infinite;
}

.connection-callout--left {
  top: 22%;
  left: 0;
}

.connection-callout--right {
  right: 0;
  bottom: 24%;
  animation-delay: 1.2s;
}

.dashboard-preview {
  display: grid;
  grid-template-columns: 126px 1fr;
  width: min(700px, 100%);
  min-height: 515px;
  overflow: hidden;
  background: #f5f9fe;
  border: 8px solid rgb(255 255 255 / 86%);
  border-radius: 28px;
  box-shadow: 0 30px 80px rgb(46 103 161 / 18%);
}

.preview-sidebar {
  display: flex;
  padding: 18px 12px;
  gap: 10px;
  color: #8da4bd;
  background: #fff;
  flex-direction: column;
}

.preview-nav {
  display: flex;
  width: 100%;
  min-height: 36px;
  padding: 0 9px;
  align-items: center;
  gap: 8px;
  font-size: 0.67rem;
  font-weight: 700;
  border-radius: 9px;
}

.preview-nav.active {
  color: #fff;
  background: var(--wallo-color-primary);
}

.preview-brand {
  display: grid;
  width: 34px;
  height: 34px;
  margin-bottom: 4px;
  color: #fff;
  font-weight: 900;
  background: var(--wallo-color-text);
  border-radius: 50%;
  place-items: center;
}

.preview-main {
  padding: 22px;
}

.preview-header {
  display: flex;
  margin-bottom: 18px;
  align-items: center;
  justify-content: space-between;
}

.preview-header > div {
  display: grid;
  gap: 3px;
}

.preview-header small,
.card-label {
  color: var(--wallo-color-text-muted);
  font-size: 0.7rem;
}

.preview-header strong {
  color: var(--wallo-color-text);
  font-size: 1.1rem;
}

.preview-profile {
  display: grid;
  width: 34px;
  height: 34px;
  color: var(--wallo-color-primary);
  background: var(--wallo-color-info-bg);
  border-radius: 50%;
  place-items: center;
}

.preview-grid {
  display: grid;
  gap: 13px;
}

.preview-grid--top {
  grid-template-columns: 1.35fr 0.65fr;
}

.preview-grid--bottom {
  grid-template-columns: 1fr 1fr;
  margin-top: 13px;
}

.preview-card {
  position: relative;
  min-width: 0;
  padding: 17px;
  background: #fff;
  border: 2px solid transparent;
  border-radius: 18px;
  box-shadow: 0 5px 16px rgb(70 113 157 / 7%);
  opacity: 0.6;
  transition: opacity 0.35s ease, transform 0.35s ease, border-color 0.35s ease;
}

.preview-card.is-highlighted {
  z-index: 2;
  border-color: var(--wallo-color-primary);
  box-shadow: 0 12px 28px rgb(79 143 232 / 20%);
  opacity: 1;
  transform: translateY(-4px);
}

.preview-card strong {
  display: block;
  margin-top: 7px;
  color: var(--wallo-color-text);
  font-size: 1rem;
}

.feature-badge {
  position: absolute;
  top: -12px;
  right: 12px;
  padding: 6px 11px;
  color: #fff;
  font-size: 0.67rem;
  font-weight: 800;
  background: var(--wallo-color-primary);
  border-radius: var(--wallo-radius-pill);
  box-shadow: 0 6px 14px rgb(79 143 232 / 24%);
  animation: badge-pop 0.45s ease both;
}

.mini-chart {
  display: flex;
  height: 66px;
  margin-top: 14px;
  align-items: end;
  gap: 7px;
}

.mini-chart i {
  flex: 1;
  background: linear-gradient(180deg, #6da9ef, #cfe5fb);
  border-radius: 5px 5px 2px 2px;
}

.preview-card--budget {
  text-align: center;
}

.budget-ring {
  display: grid;
  width: 70px;
  height: 70px;
  margin: 13px auto 0;
  background: conic-gradient(var(--wallo-color-primary) 0 68%, #e6eff9 68% 100%);
  border-radius: 50%;
  place-items: center;
}

.budget-ring::before {
  grid-area: 1 / 1;
  width: 52px;
  height: 52px;
  content: "";
  background: #fff;
  border-radius: 50%;
}

.budget-ring span {
  z-index: 1;
  grid-area: 1 / 1;
  color: var(--wallo-color-primary);
  font-size: 0.72rem;
  font-weight: 800;
}

.expense-row {
  display: grid;
  grid-template-columns: 40px 1fr;
  gap: 7px;
  margin-top: 13px;
  align-items: center;
  color: var(--wallo-color-text-muted);
  font-size: 0.67rem;
}

.expense-row::after {
  z-index: 0;
  grid-column: 2;
  grid-row: 1;
  height: 7px;
  content: "";
  background: #e9f1fa;
  border-radius: 5px;
}

.expense-row i {
  z-index: 1;
  grid-column: 2;
  grid-row: 1;
  height: 7px;
  background: var(--wallo-color-primary);
  border-radius: 5px;
}

.goal-progress {
  height: 8px;
  margin-top: 16px;
  overflow: hidden;
  background: #e7eff8;
  border-radius: 6px;
}

.goal-progress i {
  display: block;
  width: 64%;
  height: 100%;
  background: linear-gradient(90deg, #4f8fe8, #73c4ee);
}

.preview-card--goal > small {
  display: block;
  margin-top: 6px;
  color: var(--wallo-color-primary);
  font-size: 0.67rem;
  text-align: right;
}

.goal-tools {
  display: flex;
  margin-top: 18px;
  gap: 5px;
  flex-wrap: wrap;
}

.goal-tools span {
  padding: 5px 7px;
  color: var(--wallo-color-finance-info);
  font-size: 0.61rem;
  font-weight: 700;
  background: var(--wallo-color-info-bg);
  border-radius: 7px;
}

.ai-preview,
.reward-preview,
.report-preview {
  width: min(700px, 100%);
  padding: 26px;
  background: #f5f9fe;
  border: 8px solid rgb(255 255 255 / 86%);
  border-radius: 28px;
  box-shadow: 0 30px 80px rgb(46 103 161 / 18%);
}

.service-preview-header {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-bottom: 20px;
}

.service-preview-icon {
  display: grid;
  width: 48px;
  height: 48px;
  color: #fff;
  font-size: 1.25rem;
  background: linear-gradient(135deg, #4f8fe8, #826fe9);
  border-radius: 15px;
  place-items: center;
}

.service-preview-header > div {
  display: grid;
  gap: 3px;
}

.service-preview-header small,
.challenge-panel-header small {
  color: var(--wallo-color-primary);
  font-size: 0.62rem;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.service-preview-header strong {
  color: var(--wallo-color-text);
  font-size: 1.15rem;
}

.ai-feature-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.ai-feature-card {
  display: grid;
  grid-template-columns: auto 1fr auto;
  min-width: 0;
  padding: 18px;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid var(--wallo-color-border-soft);
  border-radius: 17px;
  box-shadow: 0 5px 16px rgb(70 113 157 / 7%);
}

.ai-feature-card > span {
  display: grid;
  width: 42px;
  height: 42px;
  color: #fff;
  border-radius: 13px;
  place-items: center;
}

.ai-feature-card--goal > span { background: #4f8fe8; }
.ai-feature-card--asset > span { background: #19b98a; }
.ai-feature-card--spending > span { background: #f09b51; }
.ai-feature-card--product > span { background: #806fe9; }

.ai-feature-card > div {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.ai-feature-card strong {
  color: var(--wallo-color-text);
  font-size: 0.86rem;
}

.ai-feature-card small {
  overflow: hidden;
  color: var(--wallo-color-text-muted);
  font-size: 0.67rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-feature-card > i {
  color: var(--wallo-color-text-subtle);
  font-size: 0.75rem;
}

.ai-chat-bar {
  display: flex;
  min-height: 48px;
  margin-top: 16px;
  padding: 8px 9px 8px 16px;
  align-items: center;
  justify-content: space-between;
  color: var(--wallo-color-text-subtle);
  font-size: 0.75rem;
  background: #fff;
  border: 1px solid var(--wallo-color-border);
  border-radius: var(--wallo-radius-pill);
}

.ai-chat-bar i {
  display: grid;
  width: 32px;
  height: 32px;
  color: #fff;
  background: var(--wallo-color-primary);
  border-radius: 50%;
  place-items: center;
}

.reward-preview {
  display: grid;
  gap: 14px;
}

.report-preview {
  display: grid;
  gap: 14px;
}

.report-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.report-preview-header > div {
  display: grid;
  gap: 3px;
}

.report-preview-header small {
  color: var(--wallo-color-primary);
  font-size: 0.62rem;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.report-preview-header strong {
  color: var(--wallo-color-text);
  font-size: 1.16rem;
}

.report-preview-header > span {
  padding: 7px 11px;
  color: var(--wallo-color-finance-info);
  font-size: 0.68rem;
  font-weight: 800;
  background: var(--wallo-color-info-bg);
  border-radius: var(--wallo-radius-pill);
}

.report-featured-card {
  padding: 22px;
  background: #fff;
  border: 1px solid var(--wallo-color-border-soft);
  border-radius: 19px;
  box-shadow: 0 5px 16px rgb(70 113 157 / 7%);
}

.report-card-meta {
  display: flex;
  gap: 7px;
}

.report-card-meta span {
  padding: 5px 9px;
  color: var(--wallo-color-text-muted);
  font-size: 0.6rem;
  font-weight: 800;
  background: var(--wallo-color-surface-soft);
  border-radius: var(--wallo-radius-pill);
}

.report-card-meta span:last-child {
  color: #fff;
  background: var(--wallo-color-primary);
}

.report-featured-card h2 {
  margin: 14px 0 8px;
  color: var(--wallo-color-text);
  font-size: 1.08rem;
  font-weight: 800;
  line-height: 1.45;
}

.report-featured-card p {
  margin: 0;
  color: var(--wallo-color-text-muted);
  font-size: 0.75rem;
  line-height: 1.65;
}

.report-source {
  display: flex;
  gap: 6px;
  margin-top: 13px;
  color: var(--wallo-color-text-subtle);
  font-size: 0.64rem;
}

.report-insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.report-insight-card {
  display: flex;
  padding: 14px;
  align-items: center;
  gap: 11px;
  background: #fff;
  border: 1px solid var(--wallo-color-border-soft);
  border-radius: 15px;
}

.report-insight-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  color: var(--wallo-color-primary);
  background: var(--wallo-color-info-bg);
  border-radius: 11px;
  place-items: center;
}

.report-insight-card > div {
  display: grid;
  gap: 3px;
}

.report-insight-card small {
  color: var(--wallo-color-primary);
  font-size: 0.6rem;
  font-weight: 800;
}

.report-insight-card strong {
  color: var(--wallo-color-text);
  font-size: 0.72rem;
}

.challenge-panel,
.point-panel {
  padding: 20px;
  background: #fff;
  border: 1px solid var(--wallo-color-border-soft);
  border-radius: 19px;
  box-shadow: 0 5px 16px rgb(70 113 157 / 7%);
}

.challenge-panel-header {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
}

.challenge-symbol {
  display: grid;
  width: 44px;
  height: 44px;
  font-size: 1.3rem;
  background: #fff4cf;
  border-radius: 14px;
  place-items: center;
}

.challenge-panel-header > div {
  display: grid;
  gap: 3px;
}

.challenge-panel-header strong {
  color: var(--wallo-color-text);
  font-size: 0.94rem;
}

.challenge-status {
  padding: 6px 10px;
  color: var(--wallo-color-success);
  font-size: 0.65rem;
  font-weight: 800;
  background: #eafaf5;
  border-radius: var(--wallo-radius-pill);
}

.challenge-stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 16px;
}

.challenge-stat-grid > div {
  display: grid;
  padding: 11px 7px;
  text-align: center;
  background: var(--wallo-color-surface-soft);
  border-radius: 12px;
  place-items: center;
}

.challenge-stat-grid i { color: var(--wallo-color-primary); font-size: 0.8rem; }
.challenge-stat-grid strong { margin-top: 4px; color: var(--wallo-color-text); font-size: 0.8rem; }
.challenge-stat-grid small { color: var(--wallo-color-text-muted); font-size: 0.58rem; }

.challenge-feed-row {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 10px;
  margin-top: 12px;
  padding: 10px;
  align-items: center;
  background: #f8fbff;
  border-radius: 12px;
}

.feed-avatar {
  display: grid;
  width: 32px;
  height: 32px;
  color: #fff;
  font-size: 0.7rem;
  font-weight: 800;
  background: var(--wallo-color-primary);
  border-radius: 50%;
  place-items: center;
}

.challenge-feed-row > div { display: grid; gap: 2px; }
.challenge-feed-row strong { color: var(--wallo-color-text); font-size: 0.72rem; }
.challenge-feed-row small { color: var(--wallo-color-success); font-size: 0.61rem; }
.challenge-feed-row > i { color: #f2748b; }

.point-balance {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--wallo-color-text);
  font-size: 0.76rem;
  font-weight: 700;
}

.point-balance i { color: #e6a600; }
.point-balance strong { color: var(--wallo-color-primary); font-size: 1rem; }

.point-products {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 14px;
}

.point-products > div {
  display: grid;
  padding: 10px 6px;
  text-align: center;
  background: var(--wallo-color-surface-soft);
  border-radius: 12px;
  place-items: center;
}

.point-products span { font-size: 1.25rem; }
.point-products small { margin-top: 4px; color: var(--wallo-color-text-muted); font-size: 0.6rem; }
.point-products strong { margin-top: 2px; color: var(--wallo-color-text); font-size: 0.68rem; }

.onboarding-step-enter-active,
.onboarding-step-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.onboarding-step-enter-from {
  opacity: 0;
  transform: translateX(24px);
}

.onboarding-step-leave-to {
  opacity: 0;
  transform: translateX(-24px);
}

@keyframes penguin-greeting {
  0%,
  100% {
    transform: translateY(0) rotate(-1deg);
  }
  35% {
    transform: translateY(-12px) rotate(2deg);
  }
  65% {
    transform: translateY(-5px) rotate(-2deg);
  }
}

@keyframes shadow-breathe {
  0%,
  100% {
    opacity: 0.7;
    transform: translateX(-50%) scaleX(1);
  }
  40% {
    opacity: 0.42;
    transform: translateX(-50%) scaleX(0.88);
  }
}

@keyframes bubble-float {
  0%,
  18% {
    opacity: 0;
    transform: translateY(16px) scale(0.82);
  }
  36%,
  70% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
  100% {
    opacity: 0;
    transform: translateY(-22px) scale(0.92);
  }
}

@keyframes cursor-blink {
  50% {
    opacity: 0;
  }
}

@keyframes connection-glow {
  50% { transform: scale(1.08); }
}

@keyframes card-hover {
  50% { transform: translateY(-10px) rotate(1deg); }
}

@keyframes badge-pop {
  from { opacity: 0; transform: scale(0.7); }
  to { opacity: 1; transform: scale(1); }
}

@media (max-width: 991.98px) {
  .onboarding-page {
    padding: 84px 24px 40px;
  }

  .welcome-layout {
    grid-template-columns: 1fr;
    gap: 20px;
    text-align: center;
  }

  .guide-layout {
    grid-template-columns: 1fr;
    gap: 34px;
    text-align: center;
  }

  .guide-copy {
    margin: 0 auto;
  }

  .guide-points {
    width: max-content;
    max-width: 100%;
    margin-right: auto;
    margin-left: auto;
    text-align: left;
  }

  .guide-actions {
    justify-content: center;
  }

  .connection-preview {
    min-height: 480px;
  }

  .dashboard-preview {
    width: min(650px, 90vw);
    min-height: 470px;
  }

  .ai-preview,
  .reward-preview,
  .report-preview {
    width: min(650px, 90vw);
  }

  .penguin-stage {
    min-height: 42vh;
  }

  .penguin-motion {
    width: min(310px, 72vw);
  }

  .speech-bubble--hello {
    left: calc(50% - 200px);
  }

  .speech-bubble--welcome {
    left: calc(50% + 66px);
  }

  .speech-bubble--wave {
    left: calc(50% - 176px);
  }

  .welcome-copy {
    margin: 0 auto;
  }

  .typing-copy {
    min-height: 82px;
  }

  .welcome-description {
    margin-right: auto;
    margin-left: auto;
  }
}

@media (max-width: 575.98px) {
  .onboarding-page {
    padding: 74px 20px 30px;
  }

  .penguin-stage {
    min-height: 37vh;
  }

  .penguin-motion {
    width: min(255px, 76vw);
  }

  .speech-bubble {
    padding: 7px 12px;
    font-size: 0.76rem;
  }

  .speech-bubble--hello {
    left: 4%;
  }

  .speech-bubble--welcome {
    right: 1%;
    left: auto;
  }

  .speech-bubble--wave {
    left: 5%;
  }

  .eyebrow {
    margin-bottom: 12px;
  }

  .typing-copy {
    min-height: 72px;
    letter-spacing: -0.035em;
  }

  .greeting-line {
    font-size: clamp(1.4rem, 6vw, 1.85rem);
  }

  .introduction-line {
    font-size: clamp(0.78rem, 3.55vw, 1.05rem);
  }

  .introduction-line {
    margin-top: 8px;
  }

  .welcome-description {
    margin-top: 10px;
    line-height: 1.65;
  }

  .next-button {
    width: 100%;
    margin-top: 24px;
  }

  .guide-layout {
    gap: 24px;
  }

  .guide-copy h1 {
    font-size: clamp(1.65rem, 8vw, 2.1rem);
  }

  .guide-copy > p:not(.eyebrow) {
    margin-top: 14px;
    line-height: 1.65;
  }

  .guide-points {
    margin-top: 20px;
    font-size: 0.9rem;
  }

  .guide-actions {
    margin-top: 26px;
  }

  .guide-actions .next-button {
    width: auto;
    min-width: 0;
    flex: 1;
  }

  .back-button {
    padding-right: 17px;
    padding-left: 17px;
  }

  .connection-preview {
    min-height: 405px;
  }

  .connection-card {
    width: min(360px, 92vw);
    padding: 20px;
  }

  .connection-callout--left { left: -4px; }
  .connection-callout--right { right: -4px; }

  .dashboard-preview {
    grid-template-columns: 42px 1fr;
    width: min(560px, 94vw);
    min-height: 390px;
    border-width: 5px;
    border-radius: 20px;
  }

  .preview-sidebar {
    padding: 12px 4px;
    gap: 15px;
  }

  .preview-brand,
  .preview-sidebar i {
    width: 28px;
    height: 28px;
    font-size: 0.75rem;
  }

  .preview-nav {
    justify-content: center;
    padding: 0;
    font-size: 0;
  }

  .preview-nav i {
    display: grid;
    place-items: center;
  }

  .preview-main { padding: 13px; }
  .preview-header { margin-bottom: 11px; }
  .preview-grid { gap: 8px; }
  .preview-grid--bottom { margin-top: 8px; }
  .preview-card { padding: 11px; border-radius: 12px; }
  .preview-card strong { font-size: 0.78rem; }
  .mini-chart { height: 40px; margin-top: 8px; gap: 4px; }
  .budget-ring { width: 48px; height: 48px; margin-top: 8px; }
  .budget-ring::before { width: 36px; height: 36px; }
  .expense-row { margin-top: 8px; }
  .goal-tools { margin-top: 9px; }
  .feature-badge { top: -9px; right: 6px; padding: 4px 7px; }

  .ai-preview,
  .reward-preview,
  .report-preview {
    width: min(560px, 94vw);
    padding: 15px;
    border-width: 5px;
    border-radius: 20px;
  }

  .ai-feature-grid {
    gap: 8px;
  }

  .ai-feature-card {
    grid-template-columns: auto 1fr;
    padding: 11px;
    gap: 8px;
  }

  .ai-feature-card > span {
    width: 34px;
    height: 34px;
    border-radius: 10px;
  }

  .ai-feature-card > i {
    display: none;
  }

  .ai-feature-card strong { font-size: 0.72rem; }
  .ai-feature-card small { font-size: 0.57rem; }
  .challenge-panel, .point-panel { padding: 13px; }
  .challenge-stat-grid { margin-top: 10px; }
  .challenge-feed-row { margin-top: 8px; }
  .report-featured-card { padding: 16px; }
  .report-featured-card h2 { font-size: 0.9rem; }
  .report-insight-card { padding: 10px; gap: 7px; }
  .report-insight-card strong { font-size: 0.62rem; }
}

@media (prefers-reduced-motion: reduce) {
  .penguin-motion,
  .penguin-shadow,
  .speech-bubble,
  .typing-cursor,
  .connection-glow,
  .connection-card,
  .connection-callout {
    animation: none;
  }

  .speech-bubble {
    opacity: 1;
  }

  .next-button .bi-arrow-right {
    transition: none;
  }

  .onboarding-step-enter-active,
  .onboarding-step-leave-active,
  .preview-card {
    transition: none;
  }
}
</style>
