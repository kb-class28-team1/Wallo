<script setup>
import { computed, ref, watch } from "vue"
import DOMPurify from "dompurify"
import { marked } from "marked"
import {
  formatCollectedAt,
  formatDisclosureMonth,
  formatProductAmount,
  formatProductRate,
  formatProductTerm,
  normalizeProductRecommendation,
} from "@/types/productRecommendation"

marked.setOptions({
  breaks: true,
  gfm: true,
})

const props = defineProps({
  recommendation: {
    type: Object,
    required: true,
  },
  reason: {
    type: String,
    default: "",
  },
  fullWidth: {
    type: Boolean,
    default: false,
  },
  showIntro: {
    type: Boolean,
    default: true,
  },
})

const normalizedRecommendation = computed(() => normalizeProductRecommendation(
  props.recommendation,
) || {
  productType: null,
  termMonths: null,
  amountKrw: null,
  products: [],
})

const products = computed(() => normalizedRecommendation.value.products || [])
const hasProducts = computed(() => products.value.length > 0)
const activeProductIndex = ref(0)
const hasMultipleProducts = computed(() => products.value.length > 1)
const productTrackStyle = computed(() => ({
  transform: `translateX(-${activeProductIndex.value * 100}%)`,
}))
const reason = computed(() => props.reason?.trim() || "")
const renderedReason = computed(() => (
  reason.value ? DOMPurify.sanitize(marked.parse(reason.value)) : ""
))

const productType = (product) =>
  product.productType || normalizedRecommendation.value.productType || "금융상품"

const amountLabel = (product) => (product.isSaving ? "월 납입금" : "예치금")

const amountValue = (product) => (
  product.isSaving
    ? product.monthlyPaymentKrw ?? product.amountKrw
    : product.depositAmountKrw ?? product.amountKrw
)

const formatPreferentialConditions = (value) => (
  String(value || "")
    .trim()
    .replace(
      /\s+(?=(?:\d+(?:-\d+)?[.)]|[\u2460-\u2473])\s)/g,
      "\n",
    )
)

const productKey = (product, index) =>
  `${product.companyCode || product.companyName || "company"}-${product.productCode || product.productName || index}`

const showPreviousProduct = () => {
  if (!hasMultipleProducts.value) return
  activeProductIndex.value =
    (activeProductIndex.value - 1 + products.value.length) % products.value.length
}

const showNextProduct = () => {
  if (!hasMultipleProducts.value) return
  activeProductIndex.value = (activeProductIndex.value + 1) % products.value.length
}

watch(products, () => {
  activeProductIndex.value = 0
})

const FINANCIAL_SUPERVISION_LOGO_URL =
  "/images/institutions/financial-supervision-service.jpg"
</script>

<template>
  <section
    class="product-recommendation"
    :class="{ 'product-recommendation--full-width': fullWidth }"
    aria-label="AI 금융상품 추천 결과"
  >
    <div v-if="showIntro" class="product-recommendation__intro">
      <span class="product-recommendation__eyebrow">
        <i class="bi bi-stars me-1" aria-hidden="true"></i>
        AI 금융상품 추천
      </span>
      <strong v-if="hasProducts">조건에 맞는 금융상품을 비교해봤어요</strong>
      <strong v-else>조건에 맞는 금융상품을 찾지 못했어요</strong>
    </div>

    <div v-if="hasProducts" class="product-carousel">
      <div class="product-carousel__viewport">
        <div class="product-carousel__track" :style="productTrackStyle">
          <div
            v-for="(product, index) in products"
            :key="productKey(product, index)"
            class="product-carousel__slide"
            :aria-hidden="index !== activeProductIndex"
          >
            <article class="product-card h-100">
          <div class="d-flex align-items-center justify-content-between gap-2 mb-2">
            <span class="product-card__rank">추천 {{ product.ranking }}위</span>
            <span class="badge rounded-pill text-bg-light">
              {{ productType(product) }}
            </span>
          </div>

          <small class="product-card__company-group">
            {{ product.financialGroup || "금융회사" }}
          </small>
          <h3 class="product-card__title">
            {{ product.productName || "상품명 확인 필요" }}
          </h3>
          <p class="product-card__company">
            {{ product.companyName || "금융회사 정보 확인 필요" }}
          </p>

          <div class="product-card__details">
            <div>
              <small>가입 기간</small>
              <strong>{{ formatProductTerm(product.termMonths) }}</strong>
            </div>
            <div>
              <small>{{ amountLabel(product) }}</small>
              <strong>{{ formatProductAmount(amountValue(product)) }}</strong>
            </div>
            <div>
              <small>가입방법</small>
              <strong>{{ product.joinWay || "-" }}</strong>
            </div>
            <div>
              <small>가입대상</small>
              <strong>{{ product.joinTarget || "-" }}</strong>
            </div>
          </div>

          <div class="product-card__rates row g-2 mt-1">
            <div class="col-6">
              <div class="product-card__rate product-card__rate--base">
                <small>기본금리</small>
                <strong>{{ formatProductRate(product.baseRatePercent) }}</strong>
              </div>
            </div>
            <div class="col-6">
              <div class="product-card__rate product-card__rate--highlight">
                <small>최고 우대금리</small>
                <strong>{{ formatProductRate(product.preferentialRatePercent) }}</strong>
              </div>
            </div>
            <div v-if="product.afterTaxRatePercent !== null" class="col-12">
              <div class="d-flex justify-content-between small px-1">
                <span class="text-secondary">세후 금리</span>
                <strong>{{ formatProductRate(product.afterTaxRatePercent) }}</strong>
              </div>
            </div>
          </div>

          <div class="product-card__estimate mt-3">
            <div>
              <small>세후 예상 이자</small>
              <strong>{{ formatProductAmount(product.estimatedAfterTaxInterestKrw) }}</strong>
            </div>
            <div>
              <small>예상 만기금액</small>
              <strong>{{ formatProductAmount(product.estimatedMaturityAmountKrw) }}</strong>
            </div>
          </div>

          <div
            v-if="product.interestCalculation || product.estimateAssumption"
            class="product-card__assumption"
          >
            <small>예상 이자 계산 기준</small>
            <p v-if="product.interestCalculation">{{ product.interestCalculation }}</p>
            <p v-if="product.estimateAssumption">{{ product.estimateAssumption }}</p>
          </div>

          <details v-if="product.preferentialConditions" class="product-card__conditions">
            <summary>우대조건 확인</summary>
            <p>{{ formatPreferentialConditions(product.preferentialConditions) }}</p>
          </details>

          <div class="product-card__metadata">
            <span>공시월 {{ formatDisclosureMonth(product.disclosureMonth) }}</span>
            <span>수집일시 {{ formatCollectedAt(product.collectedAt) }}</span>
          </div>
            </article>
          </div>
        </div>
      </div>

      <div v-if="hasMultipleProducts" class="product-carousel__controls">
        <button
          type="button"
          class="product-carousel__button"
          aria-label="이전 추천 상품 보기"
          @click="showPreviousProduct"
        >
          <i class="bi bi-chevron-left" aria-hidden="true"></i>
        </button>
        <div class="product-carousel__position" role="status" aria-live="polite">
          <strong>{{ activeProductIndex + 1 }}</strong>
          <span>/ {{ products.length }}</span>
        </div>
        <button
          type="button"
          class="product-carousel__button"
          aria-label="다음 추천 상품 보기"
          @click="showNextProduct"
        >
          <i class="bi bi-chevron-right" aria-hidden="true"></i>
        </button>
      </div>
    </div>

    <div v-else class="product-recommendation__empty" role="status">
      <i class="bi bi-search me-2" aria-hidden="true"></i>
      기간, 금액 또는 가입방법을 바꿔 다시 검색해보세요.
    </div>

    <div v-if="reason" class="product-recommendation__reason">
      <strong><i class="bi bi-chat-left-text me-1" aria-hidden="true"></i>AI 추천 이유</strong>
      <div
        class="product-recommendation__reason-markdown"
        v-html="renderedReason"
      ></div>
    </div>

    <div class="alert alert-warning-subtle border rounded-4 mb-0 mt-2" role="note">
      <small>
        <i class="bi bi-info-circle me-1" aria-hidden="true"></i>
        최고 우대금리는 조건 충족 시 적용될 수 있으며, 가입 전 금융회사에서 최신 조건을 확인해야 합니다.
      </small>
    </div>
    <a
      class="product-recommendation__external-link"
      href="https://finlife.fss.or.kr/finlife/main/main.do"
      target="_blank"
      rel="noopener noreferrer"
    >
      <span class="product-recommendation__external-icon">
        <img
          :src="FINANCIAL_SUPERVISION_LOGO_URL"
          alt="금융감독원"
        />
      </span>
      <span class="product-recommendation__external-copy">
        <strong>금융상품 한눈에</strong>
        <small>금융감독원에서 최신 상품 조건을 확인해 보세요</small>
      </span>
      <i class="bi bi-box-arrow-up-right product-recommendation__external-arrow" aria-hidden="true"></i>
    </a>
  </section>
</template>

<style scoped>
.product-recommendation {
  display: flex;
  width: min(100%, 760px);
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.product-recommendation--full-width {
  width: 100%;
  max-width: none;
}

.product-recommendation__intro {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.1rem 0.2rem;
  color: #3f4660;
}

.product-recommendation__eyebrow {
  color: #7062de;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.03em;
}

.product-card {
  display: flex;
  flex-direction: column;
  padding: 1rem;
  background: #fff;
  border: 1px solid #e9e6f3;
  border-radius: 1rem;
  box-shadow: 0 0.25rem 0.8rem rgba(57, 45, 110, 0.06);
}

.product-carousel {
  width: 100%;
}

.product-carousel__viewport {
  overflow: hidden;
  border-radius: 1rem;
}

.product-carousel__track {
  display: flex;
  align-items: stretch;
  transition: transform 0.35s ease;
}

.product-carousel__slide {
  min-width: 0;
  flex: 0 0 100%;
}

.product-carousel__controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.8rem;
  margin-top: 0.75rem;
}

.product-carousel__button {
  display: inline-flex;
  width: 2.25rem;
  height: 2.25rem;
  align-items: center;
  justify-content: center;
  padding: 0;
  color: #5749c5;
  background: #faf9ff;
  border: 1px solid #ded9fa;
  border-radius: 50%;
}

.product-carousel__button:hover,
.product-carousel__button:focus-visible {
  color: #fff;
  background: #7062de;
  border-color: #7062de;
  outline: 0;
  box-shadow: 0 0 0 3px rgba(112, 98, 222, 0.18);
}

.product-carousel__position {
  display: inline-flex;
  min-width: 3.5rem;
  justify-content: center;
  gap: 0.25rem;
  color: #8a91a3;
  font-size: 0.8rem;
}

.product-carousel__position strong {
  color: #5749c5;
}

.product-card__rank {
  color: #5749c5;
  font-size: 0.78rem;
  font-weight: 800;
}

.product-card__company-group {
  color: #7b849b;
}

.product-card__title {
  margin: 0.2rem 0 0;
  color: #2f354d;
  font-size: 1.05rem;
  line-height: 1.4;
}

.product-card__company {
  min-height: 1.3rem;
  margin: 0.2rem 0 0.85rem;
  color: #62697d;
  font-size: 0.84rem;
}

.product-card__details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem;
}

.product-card__details > div,
.product-card__rate,
.product-card__estimate > div {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.65rem;
  background: #f8f7fc;
  border-radius: 0.7rem;
}

.product-card__details small,
.product-card__rate small,
.product-card__estimate small,
.product-card__assumption small {
  color: #7b849b;
  font-size: 0.72rem;
}

.product-card__details strong {
  overflow-wrap: anywhere;
  color: #3b4156;
  font-size: 0.82rem;
  line-height: 1.4;
}

.product-card__rate--base {
  background: #f8f7fc;
}

.product-card__rate--highlight {
  background: #f0eefe;
}

.product-card__rate--highlight strong {
  color: #5749c5;
}

.product-card__rate strong {
  color: #30364d;
  font-size: 1rem;
}

.product-card__estimate {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem;
}

.product-card__estimate > div {
  background: #f3fbf6;
}

.product-card__estimate strong {
  color: #267548;
  font-size: 0.95rem;
}

.product-card__assumption {
  margin-top: 0.75rem;
  padding: 0.7rem;
  color: #62697d;
  background: #fbfbfd;
  border: 1px solid #eeecf5;
  border-radius: 0.7rem;
  font-size: 0.78rem;
}

.product-card__assumption p,
.product-card__conditions p {
  margin: 0.25rem 0 0;
  white-space: pre-wrap;
  line-height: 1.5;
}

.product-card__conditions {
  margin-top: 0.7rem;
  color: #514879;
  font-size: 0.8rem;
}

.product-card__conditions summary {
  cursor: pointer;
  font-weight: 700;
}

.product-card__metadata {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  margin-top: auto;
  padding-top: 0.8rem;
  color: #8a91a3;
  font-size: 0.7rem;
}

.product-recommendation__empty {
  padding: 1rem;
  color: #62697d;
  background: #fbfbfd;
  border: 1px dashed #d9d5eb;
  border-radius: 1rem;
  font-size: 0.86rem;
}

.product-recommendation__reason {
  padding: 0.85rem 1rem;
  color: #4a426f;
  background: #faf9ff;
  border: 1px solid #ded9fa;
  border-radius: 0.9rem;
}

.product-recommendation__reason strong {
  font-size: 0.82rem;
}

.product-recommendation__reason-markdown {
  margin-top: 0.35rem;
  font-size: 0.84rem;
  line-height: 1.55;
}

.product-recommendation__reason-markdown :deep(p),
.product-recommendation__reason-markdown :deep(ul),
.product-recommendation__reason-markdown :deep(ol),
.product-recommendation__reason-markdown :deep(blockquote),
.product-recommendation__reason-markdown :deep(pre) {
  margin: 0 0 0.55rem;
}

.product-recommendation__external-link {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 0.75rem;
  padding: 0.8rem 0.9rem;
  color: #514879;
  background: #faf9ff;
  border: 1px solid #ded9fa;
  border-radius: 0.9rem;
  text-decoration: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.product-recommendation__external-link:hover,
.product-recommendation__external-link:focus-visible {
  color: #42369e;
  background: #f6f4ff;
  border-color: #bcb4f3;
  box-shadow: 0 0.3rem 0.9rem rgba(87, 73, 197, 0.1);
  outline: 0;
  transform: translateY(-1px);
}

.product-recommendation__external-link:focus-visible {
  box-shadow: 0 0 0 3px rgba(112, 98, 222, 0.18);
}

.product-recommendation__external-icon {
  display: inline-flex;
  width: 2.25rem;
  height: 2.25rem;
  flex: 0 0 2.25rem;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e8e5f5;
  border-radius: 0.7rem;
}

.product-recommendation__external-icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.product-recommendation__external-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 0.1rem;
}

.product-recommendation__external-copy strong {
  font-size: 0.88rem;
}

.product-recommendation__external-copy small {
  color: #7b849b;
  font-size: 0.75rem;
}

.product-recommendation__external-arrow {
  color: #8f86cf;
  font-size: 0.85rem;
}

.product-recommendation__reason-markdown :deep(> :last-child) {
  margin-bottom: 0;
}

.product-recommendation__reason-markdown :deep(ul),
.product-recommendation__reason-markdown :deep(ol) {
  padding-left: 1.35rem;
}

.product-recommendation__reason-markdown :deep(li + li) {
  margin-top: 0.2rem;
}

.product-recommendation__reason-markdown :deep(code) {
  padding: 0.1rem 0.3rem;
  background: #ebe9f8;
  border-radius: 0.3rem;
  font-size: 0.9em;
}

.product-recommendation__reason-markdown :deep(pre) {
  overflow-x: auto;
  padding: 0.65rem;
  color: #f5f5f5;
  background: #29273a;
  border-radius: 0.55rem;
}

@media (max-width: 640px) {
  .product-card {
    padding: 0.85rem;
  }

  .product-card__title {
    font-size: 1rem;
  }

  .product-card__details strong {
    font-size: 0.78rem;
  }
}
</style>
