<script setup>
import { computed, onMounted, ref } from "vue"
import QRCode from "qrcode"
import { useRouter } from "vue-router"
import {
  deleteUsedInventoryItem,
  getPointShop,
  openRandomBox,
  openRandomBoxes,
} from "@/api/pointShopApi"
import { useUserStore } from "@/stores/userStore"
import {
  clearResourceCache,
  getCachedResource,
  getResource,
  hasInFlightResource,
} from "@/utils/resourceCache"
import AppAlert from "@/components/ui/AppAlert.vue"
import AppButton from "@/components/ui/AppButton.vue"
import AppCard from "@/components/ui/AppCard.vue"
import AppPageHeader from "@/components/ui/AppPageHeader.vue"
import AppState from "@/components/ui/AppState.vue"
import AppDialog from "@/components/common/AppDialog.vue"
import PointDrawMachine from "@/components/common/PointDrawMachine.vue"
import { announcePointEarned } from "@/utils/pointRewardNotice"

const userStore = useUserStore()
const activeProbabilityBox = ref(null)
const shopPointBalance = ref(null)
const initialLoading = ref(true)
const refreshing = ref(false)
const hasLoadedPointShop = ref(false)
const isOpeningBox = ref(false)
const errorMessage = ref("")
const BULK_OPEN_COUNT = 10
const DRAW_ANIMATION_DURATION_MS = import.meta.env.MODE === "test" ? 0 : 1500
const POINT_SHOP_STALE_TIME = 60 * 1000
const pointWCoin = "/images/profiles/point-w-coin.svg"
const drawAnimation = ref({
  open: false,
  mode: "single",
})
const inlineDrawState = ref({
  boxId: null,
  active: false,
})
const rewardModal = ref({
  open: false,
  kind: "neutral",
  icon: "📦",
  kicker: "랜덤 박스 결과",
  title: "개봉 결과",
  message: "보상 결과를 확인해보세요.",
  itemName: "",
  rewardPoint: 0,
  openedCount: 0,
  itemRewardCount: 0,
  pointRewardCount: 0,
  loseCount: 0,
  rewards: [],
  drawResults: [],
  effectClass: "reward-effect-none",
})

const rewardParticles = [
  { left: "7%", top: "9%", size: "22px", delay: "0s", duration: "3.6s", rotate: "-18deg" },
  { left: "48%", top: "6%", size: "29px", delay: "0.4s", duration: "4.2s", rotate: "12deg" },
  { left: "91%", top: "12%", size: "24px", delay: "0.9s", duration: "3.8s", rotate: "22deg" },
  { left: "16%", top: "25%", size: "27px", delay: "0.7s", duration: "4.5s", rotate: "-8deg" },
  { left: "78%", top: "29%", size: "20px", delay: "1.1s", duration: "3.4s", rotate: "28deg" },
  { left: "3%", top: "44%", size: "26px", delay: "0.3s", duration: "4.1s", rotate: "-24deg" },
  { left: "89%", top: "48%", size: "30px", delay: "1.4s", duration: "4.7s", rotate: "14deg" },
  { left: "23%", top: "67%", size: "20px", delay: "1.5s", duration: "3.9s", rotate: "-12deg" },
  { left: "72%", top: "72%", size: "25px", delay: "0.8s", duration: "4.3s", rotate: "-28deg" },
  { left: "42%", top: "85%", size: "21px", delay: "1.8s", duration: "3.7s", rotate: "18deg" },
]

const getRewardParticleStyle = (particle) => ({
  "--particle-left": particle.left,
  "--particle-top": particle.top,
  "--particle-size": particle.size,
  "--particle-delay": particle.delay,
  "--particle-duration": particle.duration,
  "--particle-rotate": particle.rotate,
})

const singleRewardCopy = computed(() => {
  const kind = rewardModal.value.kind
  if (kind === "point") {
    const rewardPoint = Number(rewardModal.value.rewardPoint || 0).toLocaleString("ko-KR")
    return {
      title: `${rewardPoint}P 당첨!`,
      message: "포인트가 적립되었어요.",
      prize: `${rewardPoint}P`,
      icon: "coin",
    }
  }
  if (kind === "win") {
    return {
      title: "상품에 당첨됐어요!",
      message: "획득한 상품을 보관함에서 확인해보세요.",
      prize: rewardModal.value.itemName || "상품",
      icon: "gift",
    }
  }
  return {
    title: "아쉽게도 꽝이에요",
    message: "다음엔 더 큰 보상이 나올 거예요.",
    prize: "꽝",
    icon: "lose",
  }
})

// API에서 박스 정보를 받기 전에도 기본 상자 UI가 유지되도록 기본값을 둠
const randomBoxes = ref([
  {
    id: 1,
    icon: "📦",
    name: "기본 절약 상자",
    description: "가볍게 도전하고 소소한 기프티콘이 나와요",
    price: 500,
    colorClass: "box-basic",
    probabilities: [
      { label: "꽝", rate: 71 },
      { label: "250P 즉시 지급", rate: 12 },
      { label: "500P 즉시 지급", rate: 8 },
      { label: "편의점 1,000원 금액권", rate: 5 },
      { label: "아메리카노 기프티콘", rate: 3 },
      { label: "편의점 5,000원 금액권", rate: 1 },
    ],
  },
])

// 보관함 목록은 포인트샵 조회 API 응답으로 교체함
const inventoryItems = ref([])
const inventoryDetailModal = ref({
  open: false,
  item: null,
})
const inventoryQrModal = ref({
  open: false,
  item: null,
  dataUrl: "",
})

const formattedPoint = computed(
  () => `${Number(shopPointBalance.value ?? userStore.pointBalance ?? 0).toLocaleString("ko-KR")}P`,
)

const currentPoint = computed(() => Number(shopPointBalance.value ?? userStore.pointBalance ?? 0))

const bulkOpenPrice = (box) => Number(box.price || 0) * BULK_OPEN_COUNT

const getInventoryIcon = (itemName) => {
  if (itemName?.includes("아메리카노") || itemName?.includes("커피")) {
    return "☕"
  }
  if (itemName?.includes("금액권") || itemName?.includes("쿠폰")) {
    return "🎟️"
  }
  return "🎁"
}

const formatAcquiredAt = (acquiredAt) =>
  acquiredAt ? String(acquiredAt).slice(0, 10).replaceAll("-", ".") : "날짜 정보 없음"

const getInventoryDescription = (itemName) => {
  const name = String(itemName || "")

  if (name.includes("아메리카노") || name.includes("커피")) {
    return "카페에서 사용할 수 있는 아메리카노 기프티콘입니다."
  }
  if (name.includes("상품권") || name.includes("쿠폰")) {
    return "상품 구매 시 사용할 수 있는 기프티콘 또는 쿠폰입니다."
  }
  return "랜덤 박스에서 획득한 기프티콘 상품입니다."
}

const openInventoryDetail = (item) => {
  inventoryDetailModal.value = {
    open: true,
    item,
  }
}

const closeInventoryDetail = () => {
  inventoryDetailModal.value.open = false
}

const closeInventoryQr = () => {
  inventoryQrModal.value = {
    open: false,
    item: null,
    dataUrl: "",
  }
}

const createQrToken = (itemId) => {
  const randomValue =
    globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`

  return `WALLO-SIMULATED-COUPON:${itemId}:${randomValue}`
}

const handleUseInventoryItem = async () => {
  const item = inventoryDetailModal.value.item
  if (!item || item.used) {
    return
  }

  try {
    const dataUrl = await QRCode.toDataURL(createQrToken(item.id), {
      width: 240,
      margin: 2,
      errorCorrectionLevel: "M",
      color: {
        dark: "#27304f",
        light: "#ffffff",
      },
    })

    closeInventoryDetail()
    inventoryQrModal.value = {
      open: true,
      item,
      dataUrl,
    }
  } catch (error) {
    alert(error.message || "QR 코드를 생성하지 못했습니다.")
  }
}

const pointShopCacheKey = () => `point-shop:${userStore.user?.id ?? "current"}`

const applyPointShop = (pointShop) => {
  shopPointBalance.value = pointShop?.pointBalance ?? 0
  userStore.updatePointBalance(shopPointBalance.value)
  if (Array.isArray(pointShop?.boxes) && pointShop.boxes.length) {
    const box = pointShop.boxes[0]
    randomBoxes.value = [
      {
        ...randomBoxes.value[0],
        id: box.boxId,
        name: box.boxName,
        price: box.price,
      },
    ]
  }

  inventoryItems.value = Array.isArray(pointShop?.inventory)
    ? pointShop.inventory.map((item) => ({
        id: item.inventoryId,
        icon: getInventoryIcon(item.itemName),
        name: item.itemName,
        acquiredAt: formatAcquiredAt(item.acquiredAt),
        description: getInventoryDescription(item.itemName),
        used: item.status === "USED",
      }))
    : []
  hasLoadedPointShop.value = true
  return pointShop
}

const loadPointShop = async ({ force = false } = {}) => {
  const key = pointShopCacheKey()
  const cachedPointShop =
    !force && !hasInFlightResource(key)
      ? getCachedResource(key, { staleTime: POINT_SHOP_STALE_TIME })
      : undefined

  if (cachedPointShop !== undefined) {
    errorMessage.value = ""
    initialLoading.value = false
    refreshing.value = false
    return applyPointShop(cachedPointShop)
  }

  const isInitialLoad = !hasLoadedPointShop.value
  initialLoading.value = isInitialLoad
  refreshing.value = !isInitialLoad
  errorMessage.value = ""

  try {
    const pointShop = await getResource(
      key,
      async () => {
        const response = await getPointShop()
        return response?.data || response || {}
      },
      {
        force,
        staleTime: POINT_SHOP_STALE_TIME,
      },
    )
    return applyPointShop(pointShop)
  } catch (error) {
    errorMessage.value = error.message || "포인트 샵 정보를 불러오지 못했습니다."
    return null
  } finally {
    initialLoading.value = false
    refreshing.value = false
  }
}

const refreshPointData = async () => {
  clearResourceCache()
  await loadPointShop({ force: true })
}

const showProbability = (boxId) => {
  activeProbabilityBox.value = boxId
}

const hideProbability = (boxId) => {
  if (activeProbabilityBox.value === boxId) {
    activeProbabilityBox.value = null
  }
}

const openRewardModal = (payload) => {
  rewardModal.value = {
    ...rewardModal.value,
    ...payload,
    effectClass:
      payload.effectClass ||
      (payload.kind === "point"
        ? getRewardEffectClass({ rewardPoint: payload.rewardPoint })
        : payload.kind === "win"
          ? getRewardEffectClass({ itemName: payload.itemName })
          : payload.kind === "bulk"
            ? getBulkRewardEffectClass(payload.rewardPoint, payload.rewards || [])
            : "reward-effect-none"),
    open: true,
  }
}

const closeRewardModal = () => {
  rewardModal.value.open = false
}

const rewardEffectRank = {
  "reward-effect-none": 0,
  "reward-effect-blue": 1,
  "reward-effect-copper": 1,
  "reward-effect-silver": 2,
  "reward-effect-gold": 3,
  "reward-effect-emerald": 4,
  "reward-effect-sky": 5,
}

const getRewardEffectClass = ({ rewardPoint = 0, itemName = "" } = {}) => {
  const name = String(itemName)

  if (Number(rewardPoint) === 250) {
    return "reward-effect-blue"
  }
  if (Number(rewardPoint) === 500) {
    return "reward-effect-silver"
  }
  if (name.includes("1,000") || name.includes("1000")) {
    return "reward-effect-gold"
  }
  if (name.includes("아메리카노") || name.includes("커피")) {
    return "reward-effect-emerald"
  }
  if (name.includes("5,000") || name.includes("5000")) {
    return "reward-effect-sky"
  }

  return "reward-effect-none"
}

const getBulkRewardEffectClass = (rewardPoint, rewards) => {
  const effectClasses = [
    getRewardEffectClass({ rewardPoint }),
    ...rewards.map((reward) => getRewardEffectClass({ itemName: reward?.itemName })),
  ]

  return effectClasses.reduce(
    (best, current) => (rewardEffectRank[current] > rewardEffectRank[best] ? current : best),
    "reward-effect-none",
  )
}

const buildFallbackDrawResults = ({ rewards, pointRewardCount, loseCount, rewardPoint }) => {
  const itemResults = rewards.map((reward) => ({
    result: "WIN",
    inventoryId: reward.inventoryId,
    itemName: reward.itemName,
    grade: reward.grade,
    rewardPoint: 0,
  }))
  const pointValue = pointRewardCount ? Math.round(Number(rewardPoint || 0) / pointRewardCount) : 0
  const pointResults = Array.from({ length: pointRewardCount }, (_, index) => ({
    result: "POINT",
    inventoryId: null,
    itemName: "",
    grade: "",
    rewardPoint: pointValue,
    fallbackIndex: index,
  }))
  const loseResults = Array.from({ length: loseCount }, (_, index) => ({
    result: "LOSE",
    inventoryId: null,
    itemName: "",
    grade: "",
    rewardPoint: 0,
    fallbackIndex: index,
  }))

  const fallbackResults = [...itemResults, ...pointResults, ...loseResults]
  while (fallbackResults.length < BULK_OPEN_COUNT) {
    fallbackResults.push({
      result: "LOSE",
      inventoryId: null,
      itemName: "",
      grade: "",
      rewardPoint: 0,
      fallbackIndex: fallbackResults.length,
    })
  }

  return fallbackResults.slice(0, BULK_OPEN_COUNT)
}

const showBoxErrorModal = (message) => {
  openRewardModal({
    kind: "error",
    icon: "💳",
    kicker: "랜덤 박스 안내",
    title: "개봉할 수 없어요",
    message,
    itemName: "",
    rewardPoint: 0,
    rewards: [],
  })
}

const showSingleBoxResult = (result) => {
  if (result?.result === "LOSE") {
    openRewardModal({
      kind: "lose",
      icon: "😢",
      kicker: "랜덤 박스 결과",
      title: "아쉽게도 꽝이에요",
      message: "다음에는 더 좋은 보상이 나오길 바랄게요.",
      itemName: "",
      rewardPoint: 0,
      rewards: [],
    })
    return
  }

  if (result?.result === "POINT") {
    const rewardPoint = Number(result?.rewardPoint || 0)
    openRewardModal({
      kind: "point",
      icon: "🪙",
      kicker: "랜덤 박스 결과",
      title: `${rewardPoint.toLocaleString("ko-KR")}P 당첨!`,
      message: "포인트가 즉시 지급되었어요.",
      itemName: "",
      rewardPoint,
      rewards: [],
    })
    return
  }

  openRewardModal({
    kind: "win",
    icon: "🎉",
    kicker: "랜덤 박스 결과",
    title: "상품에 당첨됐어요!",
    message: "획득한 상품을 보관함에서 확인해보세요.",
    itemName: result?.reward?.itemName || "상품",
    rewardPoint: 0,
    rewards: [],
  })
}

const showBulkBoxResult = (result) => {
  const openedCount = Number(result?.openedCount || BULK_OPEN_COUNT)
  const itemRewardCount = Number(result?.itemRewardCount || 0)
  const pointRewardCount = Number(result?.pointRewardCount || 0)
  const loseCount = Number(result?.loseCount || 0)
  const rewardPoint = Number(result?.rewardPoint || 0)
  const rewards = Array.isArray(result?.rewards) ? result.rewards : []
  const drawResults =
    Array.isArray(result?.drawResults) && result.drawResults.length === BULK_OPEN_COUNT
      ? result.drawResults
      : buildFallbackDrawResults({ rewards, pointRewardCount, loseCount, rewardPoint })

  openRewardModal({
    kind: "bulk",
    icon: itemRewardCount || rewardPoint ? "🎉" : "📦",
    kicker: "랜덤 박스 일괄 개봉 결과",
    title: `${openedCount}개 개봉 완료!`,
    message:
      itemRewardCount || rewardPoint
        ? "이번 개봉에서 획득한 보상이에요."
        : "이번에는 당첨된 보상이 없어요.",
    itemName: "",
    rewardPoint,
    openedCount,
    itemRewardCount,
    pointRewardCount,
    loseCount,
    rewards,
    drawResults,
  })
}

const startDrawAnimation = (mode) => {
  drawAnimation.value = {
    open: true,
    mode,
  }
}

const closeDrawAnimation = () => {
  drawAnimation.value.open = false
}

const startInlineDraw = (boxId) => {
  inlineDrawState.value = {
    boxId,
    active: true,
  }
}

const closeInlineDraw = () => {
  inlineDrawState.value = {
    boxId: null,
    active: false,
  }
}

const waitForDrawAnimation = async (startedAt) => {
  const remaining = DRAW_ANIMATION_DURATION_MS - (Date.now() - startedAt)
  if (remaining <= 0) return

  await new Promise((resolve) => {
    window.setTimeout(resolve, remaining)
  })
}

const handleOpenBox = async (box, { inline = false } = {}) => {
  if (isOpeningBox.value || currentPoint.value < box.price) {
    if (currentPoint.value < box.price) {
      showBoxErrorModal("보유 포인트가 부족합니다.")
    }
    return
  }

  isOpeningBox.value = true
  const animationStartedAt = Date.now()
  if (inline) {
    startInlineDraw(box.id)
  } else {
    startDrawAnimation("single")
  }
  try {
    const response = await openRandomBox(box.id)
    const result = response?.data || response
    shopPointBalance.value = result?.remainingPoint ?? shopPointBalance.value
    userStore.updatePointBalance(shopPointBalance.value)
    await waitForDrawAnimation(animationStartedAt)
    if (inline) {
      closeInlineDraw()
    } else {
      closeDrawAnimation()
    }
    showSingleBoxResult(result)
    if (result?.result === "POINT" && Number(result?.rewardPoint) > 0) {
      announcePointEarned(result.rewardPoint)
    }
    await refreshPointData()
  } catch (error) {
    if (inline) {
      closeInlineDraw()
    } else {
      closeDrawAnimation()
    }
    showBoxErrorModal(error.message || "랜덤박스를 열지 못했습니다.")
  } finally {
    isOpeningBox.value = false
  }
}

// 사용 완료 아이템만 서버에서 삭제한 뒤 목록을 다시 조회함.
const removeUsedItem = async (itemId) => {
  const item = inventoryItems.value.find((inventoryItem) => inventoryItem.id === itemId)
  if (!item?.used) {
    return
  }

  if (!window.confirm("사용 완료 아이템을 삭제할까요?")) {
    return
  }

  try {
    await deleteUsedInventoryItem(itemId)
    await refreshPointData()
  } catch (error) {
    alert(error.message || "아이템을 삭제하지 못했습니다.")
  }
}

const handleOpenBoxes = async (box) => {
  const totalPrice = bulkOpenPrice(box)
  if (isOpeningBox.value || currentPoint.value < totalPrice) {
    if (currentPoint.value < totalPrice) {
      showBoxErrorModal(`10개를 열려면 ${totalPrice.toLocaleString("ko-KR")}P가 필요합니다.`)
    }
    return
  }

  isOpeningBox.value = true
  const animationStartedAt = Date.now()
  startDrawAnimation("bulk")
  try {
    const response = await openRandomBoxes(box.id)
    const result = response?.data || response
    shopPointBalance.value = result?.remainingPoint ?? shopPointBalance.value
    userStore.updatePointBalance(shopPointBalance.value)

    await waitForDrawAnimation(animationStartedAt)
    closeDrawAnimation()
    showBulkBoxResult(result)
    if (Number(result?.rewardPoint) > 0) {
      announcePointEarned(result.rewardPoint)
    }
    await refreshPointData()
  } catch (error) {
    closeDrawAnimation()
    showBoxErrorModal(error.message || "랜덤박스 10개를 열지 못했습니다.")
  } finally {
    isOpeningBox.value = false
  }
}

// 페이지에 들어올 때마다 최신 포인트와 보관함을 조회해 상자 열기 가능 여부를 즉시 갱신함
onMounted(() => {
  void loadPointShop({ force: true })
})
</script>

<template>
  <section class="point-shop-page">
    <AppPageHeader class="page-heading" title="포인트 샵" compact />

    <AppCard as="article" class="point-summary-card" padding="none">
      <span>보유 포인트</span>
      <strong>{{ formattedPoint }}</strong>
      <p>오늘의 미션을 인증하고 포인트를 모아보세요 🪙</p>
      <RouterLink
        to="/point-history"
        class="point-history-button"
        aria-label="포인트 내역 페이지로 이동"
      >
        포인트 내역 보기
      </RouterLink>
    </AppCard>

    <AppState
      v-if="initialLoading"
      class="shop-state"
      type="loading"
      title="포인트샵 정보를 불러오는 중입니다."
      message="잠시만 기다려 주세요."
    />

    <AppAlert
      v-if="refreshing"
      class="shop-refresh-status"
      variant="neutral"
      role="status"
      :show-icon="false"
      message="최신 포인트샵 정보를 확인하는 중입니다."
    />

    <AppAlert v-if="errorMessage" class="shop-error" variant="danger">
      <div class="shop-error-content">
        <span>{{ errorMessage }}</span>
        <AppButton variant="outline" size="sm" @click="loadPointShop({ force: true })">
          다시 시도
        </AppButton>
      </div>
    </AppAlert>

    <div class="section-title">
      <h2>🎁 랜덤 박스</h2>
      <span>확률에 따라 기프티콘 상품이 나와요</span>
    </div>

    <div class="box-grid">
      <AppCard
        as="article"
        v-for="box in randomBoxes"
        :key="box.id"
        class="random-box-card"
        :class="box.colorClass"
        padding="none"
      >
        <h3>{{ box.name }}</h3>
        <div
          class="draw-machine-preview"
          :class="{
            'is-drawing': inlineDrawState.active && inlineDrawState.boxId === box.id,
            'is-disabled': isOpeningBox || currentPoint < box.price,
          }"
        >
          <div class="draw-machine-preview-glass" aria-hidden="true">
            <div class="draw-machine-preview-lid"></div>
            <PointDrawMachine />
            <div class="draw-machine-preview-glass-shine"></div>
          </div>
          <div class="draw-machine-preview-housing" aria-hidden="true"></div>
          <button
            type="button"
            class="draw-machine-preview-lever"
            :disabled="isOpeningBox || currentPoint < box.price"
            aria-label="뽑기통 레버를 돌려 랜덤 박스 열기"
            @click="handleOpenBox(box, { inline: true })"
          >
            <span class="draw-machine-preview-lever-arm"></span>
            <span class="draw-machine-preview-lever-knob"></span>
          </button>
          <div class="draw-machine-preview-chute" aria-hidden="true">
            <span class="draw-machine-preview-output-ball">?</span>
          </div>
          <span class="draw-machine-preview-hint">
            {{ isOpeningBox ? "공을 뽑는 중..." : "레버를 돌려 뽑기" }}
          </span>
        </div>
        <p>{{ box.description }}</p>
        <strong class="box-price">🪙 {{ box.price.toLocaleString("ko-KR") }}P</strong>
        <div class="open-box-actions">
          <AppButton
            class="bulk-open-box-button"
            variant="outline"
            size="sm"
            block
            :disabled="isOpeningBox || currentPoint < bulkOpenPrice(box)"
            :loading="isOpeningBox"
            @click="handleOpenBoxes(box)"
          >
            {{ `10개 한 번에 열기 · ${bulkOpenPrice(box).toLocaleString("ko-KR")}P` }}
          </AppButton>
        </div>
        <div
          class="probability-control"
          @mouseenter="showProbability(box.id)"
          @mouseleave="hideProbability(box.id)"
        >
          <button
            type="button"
            class="probability-button"
            :aria-expanded="activeProbabilityBox === box.id"
          >
            ▸ 확률 보기
          </button>
          <div v-if="activeProbabilityBox === box.id" class="probability-popover">
            <strong>상품별 확률</strong>
            <div v-for="item in box.probabilities" :key="item.label" class="probability-row">
              <span>{{ item.label }}</span>
              <b>{{ item.rate }}%</b>
            </div>
          </div>
        </div>
      </AppCard>
    </div>

    <div class="section-title inventory-title">
      <h2>🎒 내 보관함</h2>
      <span
        >사용 가능한 {{ inventoryItems.filter((item) => !item.used).length }}개 · 전체
        {{ inventoryItems.length }}개</span
      >
    </div>

    <AppCard v-if="inventoryItems.length" as="article" class="inventory-card" padding="none">
      <div
        v-for="item in inventoryItems"
        :key="item.id"
        class="inventory-item"
        :class="{ used: item.used }"
        role="button"
        tabindex="0"
        @click="openInventoryDetail(item)"
        @keydown.enter="openInventoryDetail(item)"
        @keydown.space.prevent="openInventoryDetail(item)"
      >
        <div class="inventory-icon">{{ item.icon }}</div>
        <div class="inventory-info">
          <strong>{{ item.name }}</strong>
          <span>획득 {{ item.acquiredAt }}</span>
        </div>
        <span class="inventory-status">{{ item.used ? "사용 완료" : "사용 가능" }}</span>
        <button
          v-if="item.used"
          type="button"
          class="remove-button"
          aria-label="사용한 아이템 삭제"
          @click.stop="removeUsedItem(item.id)"
        >
          ×
        </button>
      </div>
    </AppCard>

    <AppState
      v-else
      class="empty-inventory"
      type="empty"
      title="보관함이 비어 있어요."
      message="랜덤박스에서 획득한 상품이 이곳에 표시돼요."
    >
      <template #icon>
        <span>🎒</span>
      </template>
    </AppState>

    <AppDialog
      :visible="inventoryDetailModal.open"
      :title="inventoryDetailModal.item?.name || '기프티콘 상세'"
      :message="inventoryDetailModal.item?.description || ''"
      :confirm-text="inventoryDetailModal.item?.used ? '사용 완료' : '사용하기'"
      cancel-text="닫기"
      :show-cancel="true"
      :confirm-disabled="Boolean(inventoryDetailModal.item?.used)"
      size="sm"
      @close="closeInventoryDetail"
      @confirm="handleUseInventoryItem"
    >
      <div class="inventory-detail-icon" aria-hidden="true">
        {{ inventoryDetailModal.item?.icon }}
      </div>
      <dl class="inventory-detail-meta">
        <div>
          <dt>획득일</dt>
          <dd>{{ inventoryDetailModal.item?.acquiredAt }}</dd>
        </div>
        <div>
          <dt>상태</dt>
          <dd>{{ inventoryDetailModal.item?.used ? "사용 완료" : "사용 가능" }}</dd>
        </div>
      </dl>
    </AppDialog>

    <AppDialog
      :visible="inventoryQrModal.open"
      :title="`${inventoryQrModal.item?.name || '기프티콘'} QR`"
      message="매장에서 사용할 때 이 QR을 보여주세요."
      confirm-text="닫기"
      :show-cancel="false"
      size="sm"
      @close="closeInventoryQr"
      @confirm="closeInventoryQr"
    >
      <div class="inventory-qr-content">
        <div class="inventory-qr-frame">
          <img
            :src="inventoryQrModal.dataUrl"
            :alt="`${inventoryQrModal.item?.name || '기프티콘'} 임의 QR 코드`"
          />
        </div>
        <p class="inventory-qr-notice">
          현재는 테스트용 임의 QR입니다. QR을 닫아도 쿠폰은 사용 완료 처리되지 않습니다.
        </p>
      </div>
    </AppDialog>

    <Transition name="draw-machine">
      <div
        v-if="drawAnimation.open"
        class="draw-machine-backdrop"
        role="status"
        aria-live="polite"
        aria-label="랜덤 박스 보상을 뽑는 중"
      >
        <div
          class="draw-machine-shell"
          :class="{ 'draw-machine-shell-bulk': drawAnimation.mode === 'bulk' }"
        >
          <span class="draw-machine-kicker">WALLO POINT SHOP</span>
          <h2>행운의 공을 뽑는 중이에요</h2>
          <div class="draw-machine-stage" aria-hidden="true">
            <div class="draw-machine-glow"></div>
            <div class="draw-machine-body">
              <div class="draw-machine-cap"></div>
              <div class="draw-machine-drum">
                <PointDrawMachine :mode="drawAnimation.mode" />
                <div class="draw-machine-drum-shine"></div>
              </div>
              <div class="draw-machine-housing"></div>
              <div class="draw-machine-support draw-machine-support-left"></div>
              <div class="draw-machine-support draw-machine-support-right"></div>
              <div class="draw-machine-platform"></div>
              <div class="draw-machine-lever" aria-hidden="true">
                <span class="draw-machine-lever-arm"></span>
                <span class="draw-machine-lever-knob"></span>
              </div>
              <div class="draw-machine-chute">
                <span class="draw-machine-output-ball">?</span>
              </div>
            </div>
          </div>
          <p>
            {{
              drawAnimation.mode === "bulk"
                ? "10개의 결과를 섞어서 확인하고 있어요."
                : "공들이 섞이고 있어요. 잠시만 기다려 주세요."
            }}
          </p>
        </div>
      </div>
    </Transition>

    <Transition name="reward-modal">
      <div
        v-if="rewardModal.open"
        class="reward-modal-backdrop"
        :class="{ 'bulk-backdrop': rewardModal.kind === 'bulk' }"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="
          rewardModal.kind === 'bulk'
            ? 'bulk-result-title'
            : ['point', 'win', 'lose'].includes(rewardModal.kind)
              ? 'single-result-title'
              : 'reward-modal-title'
        "
        tabindex="-1"
        @click.self="closeRewardModal"
        @keydown.esc="closeRewardModal"
      >
        <div class="reward-particle-layer" aria-hidden="true">
          <span
            v-for="particle in rewardParticles"
            :key="`${particle.left}-${particle.top}`"
            class="reward-particle"
            :style="getRewardParticleStyle(particle)"
          >
            🐾
          </span>
        </div>
        <div
          class="reward-modal-shell"
          :class="{
            'bulk-modal-shell': rewardModal.kind === 'bulk',
          }"
        >
          <article
            class="reward-modal-card"
            :class="[
              `result-${rewardModal.kind}`,
              rewardModal.kind === 'bulk' ? rewardModal.effectClass : '',
              { 'single-modal-card': ['point', 'win', 'lose'].includes(rewardModal.kind) },
            ]"
          >
            <button
              type="button"
              class="reward-modal-close"
              aria-label="결과 창 닫기"
              @click="closeRewardModal"
            >
              <i class="bi bi-x-lg" aria-hidden="true"></i>
            </button>

            <template v-if="['error', 'neutral'].includes(rewardModal.kind)">
              <div class="reward-modal-icon" aria-hidden="true">{{ rewardModal.icon }}</div>
              <span v-if="rewardModal.kind === 'error'" class="reward-modal-kicker">{{
                rewardModal.kicker
              }}</span>
              <h2 id="reward-modal-title">{{ rewardModal.title }}</h2>
              <p v-if="rewardModal.kind === 'error'" class="reward-modal-message">
                {{ rewardModal.message }}
              </p>
            </template>

            <template v-else-if="['point', 'win', 'lose'].includes(rewardModal.kind)">
              <div class="reward-celebration" :class="`reward-celebration-${rewardModal.kind}`">
                <button
                  type="button"
                  class="reward-celebration-close"
                  aria-label="결과 창 닫기"
                  @click="closeRewardModal"
                >
                  <i class="bi bi-x-lg" aria-hidden="true"></i>
                </button>
                <div class="reward-celebration-icon" aria-hidden="true">
                  <img v-if="singleRewardCopy.icon === 'coin'" :src="pointWCoin" alt="" />
                  <span v-else-if="singleRewardCopy.icon === 'gift'">🎁</span>
                  <span v-else>😢</span>
                </div>
                <span class="reward-celebration-kicker">랜덤 박스 결과</span>
                <h2 id="single-result-title" class="reward-celebration-title">
                  {{ singleRewardCopy.title }}
                </h2>
                <p class="reward-celebration-message">{{ singleRewardCopy.message }}</p>
                <div class="reward-celebration-prize">
                  <img v-if="singleRewardCopy.icon === 'coin'" :src="pointWCoin" alt="" />
                  <span v-else aria-hidden="true">{{
                    singleRewardCopy.icon === "gift" ? "🎁" : ""
                  }}</span>
                  <strong>{{ singleRewardCopy.prize }}</strong>
                </div>
              </div>
            </template>

            <template v-if="rewardModal.kind === 'bulk'">
              <div class="bulk-result-heading" aria-hidden="true">
                <h2 id="bulk-result-title">{{ rewardModal.title }}</h2>
              </div>
              <div class="bulk-draw-panel">
                <div class="bulk-draw-grid">
                  <div
                    v-for="(draw, index) in rewardModal.drawResults"
                    :key="`${index}-${draw.inventoryId || draw.result}`"
                    class="bulk-draw-card"
                    :class="[
                      `draw-${String(draw.result).toLowerCase()}`,
                      getRewardEffectClass({
                        rewardPoint: draw.rewardPoint,
                        itemName: draw.itemName,
                      }),
                    ]"
                  >
                    <button
                      type="button"
                      class="bulk-draw-close"
                      :aria-label="`${index + 1}번 결과 닫기`"
                      @click="closeRewardModal"
                    >
                      <i class="bi bi-x-lg" aria-hidden="true"></i>
                    </button>
                    <img
                      v-if="draw.result === 'POINT'"
                      :src="pointWCoin"
                      class="bulk-draw-result-icon bulk-draw-point-icon"
                      alt=""
                      aria-hidden="true"
                    />
                    <span
                      v-else-if="draw.result === 'WIN'"
                      class="bulk-draw-result-icon"
                      aria-hidden="true"
                    >
                      {{ draw.result === "WIN" ? "🎉" : "😢" }}
                    </span>
                    <strong v-if="draw.result === 'WIN'" class="bulk-draw-title"
                      >상품에 당첨됐어요!</strong
                    >
                    <strong v-else-if="draw.result === 'POINT'" class="bulk-draw-title">
                      {{ Number(draw.rewardPoint || 0).toLocaleString("ko-KR") }}P 당첨!
                    </strong>
                    <strong v-else class="bulk-draw-title">다음 기회에..</strong>
                    <div class="bulk-draw-prize-card">
                      <img
                        v-if="draw.result === 'POINT'"
                        :src="pointWCoin"
                        class="bulk-draw-prize-icon"
                        alt=""
                        aria-hidden="true"
                      />
                      <span
                        v-else-if="draw.result === 'WIN'"
                        class="bulk-draw-prize-icon"
                        aria-hidden="true"
                      >
                        🎁
                      </span>
                      <strong v-if="draw.result === 'WIN'">{{ draw.itemName }}</strong>
                      <strong v-else-if="draw.result === 'POINT'">
                        {{ Number(draw.rewardPoint || 0).toLocaleString("ko-KR") }}P
                      </strong>
                      <strong v-else>꽝</strong>
                    </div>
                  </div>
                </div>
              </div>
              <button type="button" class="bulk-result-confirm" @click="closeRewardModal">
                확인
              </button>
            </template>
          </article>
          <button
            v-if="rewardModal.kind !== 'bulk'"
            type="button"
            class="reward-modal-confirm reward-modal-confirm-outside"
            @click="closeRewardModal"
          >
            확인
          </button>
        </div>
      </div>
    </Transition>
  </section>
</template>

<style scoped>
.point-shop-page {
  width: 100%;
  color: #27304f;
}

.shop-state,
.shop-refresh-status,
.shop-error {
  margin-bottom: 12px;
}

.shop-error-content {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}

.point-summary-card {
  position: relative;
  padding: 26px;
  padding-right: 190px;
  border-radius: 20px;
  background: linear-gradient(110deg, #619ae5, #89b9ef);
  color: #fff;
  box-shadow: none;
}

.point-summary-card span,
.point-summary-card p {
  margin: 0;
  opacity: 0.82;
  font-size: 14px;
}

.loading-message,
.error-message {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  padding: 12px 16px;
  border-radius: 12px;
  background: #fff;
  color: #747d9a;
  font-size: 13px;
}

.error-message {
  background: #fff1f1;
  color: #c85a67;
}

.retry-button {
  border: 1px solid #efb4bb;
  border-radius: 999px;
  color: #c85a67;
  font-size: 12px;
  font-weight: 700;
}

.point-summary-card strong {
  display: block;
  margin: 6px 0;
  font-size: 34px;
}

.point-history-button {
  position: absolute;
  top: 50%;
  right: 24px;
  padding: 10px 18px;
  border: 1px solid #c7dcf4;
  border-radius: 10px;
  color: #477fcb;
  background: #eaf4ff;
  font-size: 14px;
  font-weight: 700;
  text-decoration: none;
  transform: translateY(-50%);
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

.point-history-button:hover,
.point-history-button:focus-visible {
  color: #fff;
  border-color: #5b94e7;
  background: #5b94e7;
}

.section-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 22px 0 12px;
}

.section-title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
}

.section-title span {
  color: #99a2bc;
  font-size: 13px;
}

.box-grid {
  display: grid;
  grid-template-columns: minmax(280px, 380px);
  gap: 14px;
  justify-content: center;
}

.random-box-card {
  position: relative;
  min-height: 540px;
  overflow: visible;
  padding: 20px;
  border: 2px solid #e6e9f3;
  border-radius: 18px;
  background: #fff;
  text-align: center;
}

.random-box-card h3 {
  margin: 0 0 6px;
  font-size: 17px;
}

.draw-machine-preview {
  position: relative;
  width: min(100%, 276px);
  height: 316px;
  margin: 4px auto 20px;
  isolation: isolate;
}

.draw-machine-preview::after {
  position: absolute;
  right: 50%;
  bottom: 3px;
  z-index: -1;
  width: 202px;
  height: 24px;
  border-radius: 50%;
  background: rgb(39 43 59 / 18%);
  content: "";
  filter: blur(8px);
  transform: translateX(50%);
}

.draw-machine-preview-glass {
  position: absolute;
  top: 18px;
  left: 50%;
  z-index: 2;
  width: 218px;
  height: 218px;
  overflow: hidden;
  border: 7px solid rgb(209 224 225 / 88%);
  border-radius: 18px 18px 28px 28px;
  background:
    linear-gradient(120deg, rgb(255 255 255 / 28%), transparent 28%),
    linear-gradient(180deg, rgb(194 224 226 / 24%), rgb(97 154 164 / 14%));
  box-shadow:
    0 0 0 4px rgb(255 255 255 / 28%),
    inset 8px 0 13px rgb(255 255 255 / 24%),
    inset -10px 0 16px rgb(38 91 102 / 19%),
    0 18px 22px rgb(41 51 72 / 22%);
  transform: translateX(-50%);
}

.draw-machine-preview-lid {
  position: absolute;
  top: -12px;
  left: 50%;
  z-index: 4;
  width: 224px;
  height: 28px;
  border: 4px solid #25292b;
  border-radius: 14px 14px 8px 8px;
  background: linear-gradient(180deg, #151819, #080909);
  box-shadow: 0 6px 10px rgb(0 0 0 / 28%);
  transform: translateX(-50%);
}

.draw-machine-preview-glass-shine {
  position: absolute;
  top: 18px;
  left: 22px;
  z-index: 3;
  width: 34px;
  height: 126px;
  border-radius: 50%;
  background: linear-gradient(180deg, rgb(255 255 255 / 48%), rgb(255 255 255 / 5%));
  filter: blur(2px);
  opacity: 0.75;
  pointer-events: none;
  transform: rotate(8deg);
}

.draw-machine-preview-housing {
  position: absolute;
  right: 50%;
  bottom: 23px;
  z-index: 1;
  width: 168px;
  height: 86px;
  border: 5px solid #181b1b;
  border-radius: 17px 17px 9px 9px;
  background: linear-gradient(90deg, #373a48, #5b5b78 45%, #353846);
  box-shadow: 0 12px 18px rgb(38 43 57 / 30%), inset 0 5px 10px rgb(255 255 255 / 8%);
  transform: translateX(50%);
}

.draw-machine-preview-housing::before {
  position: absolute;
  top: 7px;
  right: 8px;
  left: 8px;
  height: 7px;
  border-radius: 999px;
  background: rgb(137 172 226 / 38%);
  content: "";
}

.draw-machine-preview-lever {
  position: absolute;
  right: 50%;
  bottom: 65px;
  z-index: 6;
  width: 64px;
  height: 64px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 24%, #b9bcc0, #92959a 58%, #707379);
  box-shadow:
    0 4px 0 #626368,
    0 9px 12px rgb(0 0 0 / 30%),
    inset 3px 3px 5px rgb(255 255 255 / 22%),
    inset -4px -5px 7px rgb(0 0 0 / 17%);
  cursor: pointer;
  transform: translateX(50%);
}

.draw-machine-preview-lever::before {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 40px;
  height: 16px;
  border: 0;
  border-radius: 1px;
  background: linear-gradient(180deg, #e0e2e4, #c0c3c6);
  box-shadow: inset 0 1px 1px rgb(255 255 255 / 70%), inset 0 -1px 1px rgb(80 84 88 / 22%);
  content: "";
  border-radius: 4px;
  transform: translate(-50%, -50%);
}

.draw-machine-preview-lever:disabled {
  cursor: not-allowed;
  filter: grayscale(0.28);
  opacity: 0.62;
}

.draw-machine-preview-lever:not(:disabled):hover,
.draw-machine-preview-lever:not(:disabled):focus-visible {
  filter: brightness(1.08);
  outline: 3px solid rgb(79 143 232 / 24%);
  outline-offset: 3px;
}

.draw-machine-preview-lever-arm {
  display: none;
}

.draw-machine-preview-lever-knob {
  display: none;
}

.draw-machine-preview-chute {
  position: absolute;
  bottom: 14px;
  left: 50%;
  z-index: 5;
  display: grid;
  width: 82px;
  height: 56px;
  place-items: start center;
  overflow: hidden;
  border: 5px solid rgb(210 232 232 / 84%);
  border-radius: 14px 14px 28px 28px;
  background: linear-gradient(90deg, rgb(112 177 189 / 26%), rgb(240 255 255 / 42%), rgb(112 177 189 / 26%));
  box-shadow: 0 11px 16px rgb(0 0 0 / 25%);
  transform: translateX(-50%);
}

.draw-machine-preview-output-ball {
  display: grid;
  width: 38px;
  height: 38px;
  margin-top: 6px;
  place-items: center;
  border: 3px solid rgb(255 255 255 / 74%);
  border-radius: 50%;
  background: #ffd85c;
  box-shadow:
    inset -5px -6px 9px rgb(0 0 0 / 18%),
    inset 5px 4px 6px rgb(255 255 255 / 45%),
    0 6px 9px rgb(0 0 0 / 28%);
  color: #4c3a0a;
  font-size: 16px;
  font-weight: 900;
  opacity: 0;
  transform: translate3d(-10px, -54px, 0) scale(0.62) rotate(-40deg);
}

.draw-machine-preview-hint {
  position: absolute;
  right: 0;
  bottom: -12px;
  left: 0;
  z-index: 7;
  color: #4f80c9;
  font-size: 11px;
  font-weight: 800;
}

.draw-machine-preview.is-drawing .draw-machine-preview-lever {
  animation: inline-draw-machine-lever-turn 1.05s cubic-bezier(0.24, 0.7, 0.24, 1) both;
}

.draw-machine-preview.is-drawing .draw-machine-preview-lever-arm {
  animation: none;
}

.draw-machine-preview.is-drawing .draw-machine-preview-output-ball {
  animation: inline-draw-machine-output 1.25s cubic-bezier(0.2, 0.8, 0.2, 1) 1 both;
}

.draw-machine-preview.is-disabled .draw-machine-preview-hint {
  color: #a6aabe;
}

.random-box-card p {
  min-height: 38px;
  margin: 0;
  color: #99a2bc;
  font-size: 12px;
}

.box-price {
  display: block;
  margin: 10px 0;
  color: #edaa00;
  font-size: 20px;
}

.open-box-actions {
  display: grid;
  gap: 8px;
  margin: 12px 0 8px;
}

.bulk-open-box-button {
  display: block;
  width: 100%;
  padding: 9px 12px;
  border: 1px solid #c7dcf4;
  border-radius: 10px;
  background: #eaf4ff;
  color: #477fcb;
  font-size: 12px;
  font-weight: 800;
}

.bulk-open-box-button:hover:not(:disabled),
.bulk-open-box-button:focus-visible:not(:disabled) {
  border-color: #5b94e7;
  background: #dbeafe;
}

.bulk-open-box-button:disabled {
  border-color: #d9d9e8;
  background: #f0f0f5;
  color: #aaaec0;
  cursor: not-allowed;
}

.reward-modal-backdrop {
  position: fixed;
  z-index: 1100;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  overflow: auto;
  background: rgb(0 0 0 / 84%);
}

.reward-modal-backdrop.bulk-backdrop {
  background: rgb(0 0 0 / 90%);
}

.reward-modal-shell {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: min(100%, 260px);
}

.reward-modal-shell.bulk-modal-shell {
  width: min(100%, 1180px);
}

.reward-modal-card {
  position: relative;
  width: min(100%, 260px);
  min-height: 180px;
  max-height: calc(100vh - 40px);
  overflow-y: auto;
  padding: 24px 20px 17px;
  border: 1px solid rgb(255 255 255 / 70%);
  border-radius: 17px;
  background: #fff;
  color: #27304f;
  box-shadow: 0 24px 70px rgb(0 0 0 / 38%);
  text-align: center;
  display: flex;
  flex-direction: column;
}

.reward-modal-card.single-modal-card {
  display: block;
  align-self: center;
  width: 260px;
  min-width: 260px;
  min-height: 0;
  max-height: none;
  overflow: visible;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.single-draw-panel {
  display: block;
  width: 260px;
  min-width: 260px;
  padding: 0;
}

.single-draw-grid {
  width: 260px;
  min-width: 260px;
  grid-template-columns: minmax(0, 260px);
  gap: 0;
}

.single-draw-card {
  width: 260px;
  min-width: 260px;
}

.reward-modal-card.single-modal-card > .reward-modal-close {
  display: none;
}

.reward-modal-close {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  width: 25px;
  height: 25px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: #f1f2f7;
  color: #8189a3;
  font-size: 10px;
}

.reward-modal-close:hover,
.reward-modal-close:focus-visible {
  background: #e7e8f1;
  color: #424b6d;
}

.reward-modal-icon {
  display: grid;
  width: 58px;
  height: 58px;
  margin: 0 auto 10px;
  place-items: center;
  border-radius: 17px;
  background: linear-gradient(145deg, #eeeaff, #ddd8ff);
  font-size: 30px;
  animation: reward-icon-bounce 240ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.reward-modal-point-icon {
  background: linear-gradient(145deg, #eaf3ff, #cfe2ff);
}

.reward-modal-point-icon img {
  width: 42px;
  height: 42px;
  object-fit: contain;
}

.result-point h2,
.result-point .reward-point-card,
.result-point .reward-point-card strong {
  color: #2f6fed;
}

.reward-modal-kicker {
  display: block;
  margin-bottom: 5px;
  color: #709fe2;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.reward-modal-card h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 850;
}

.reward-modal-message {
  margin: 6px 0 14px;
  color: #8d96b0;
  font-size: 11px;
}

.reward-prize-card,
.reward-point-card {
  display: grid;
  justify-items: center;
  gap: 4px;
  margin-bottom: 14px;
  padding: 13px;
  border-radius: 12px;
  background: #f5faff;
}

.reward-prize-icon {
  font-size: 22px;
}

.reward-prize-card strong {
  color: #3d72c9;
  font-size: 14px;
}

.reward-prize-card small {
  color: #9ca4bd;
  font-size: 10px;
}

.reward-point-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #db9600;
}

.reward-point-icon {
  width: 22px;
  height: 22px;
  object-fit: contain;
}

.reward-point-card strong {
  font-size: 18px;
}

.reward-modal-card.result-bulk {
  box-sizing: border-box;
  width: min(100%, 1180px);
  min-height: 0;
  max-height: none;
  overflow: visible;
  padding: 0 0 24px;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.bulk-result-heading {
  display: grid;
  justify-items: center;
  gap: 12px;
  margin-bottom: 28px;
  color: #fff;
}

.bulk-result-icon {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 12px;
  background: #e9e6ff;
  font-size: 25px;
  line-height: 1;
}

.bulk-result-heading h2 {
  margin: 0;
  font-size: 23px;
  font-weight: 700;
}

.bulk-draw-panel {
  box-sizing: border-box;
  padding: 24px 26px 10px;
  background: transparent;
  overflow: visible;
}

.bulk-draw-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 28px;
  padding: 0 0;
  margin-bottom: 0;
  overflow: visible;
}

.bulk-draw-panel.single-draw-panel {
  width: 260px;
  min-width: 260px;
  margin: 0 auto;
  padding: 0 !important;
}

.bulk-draw-grid.single-draw-grid {
  width: 260px;
  min-width: 260px;
  grid-template-columns: 260px;
  gap: 0;
}

.bulk-draw-card.single-draw-card {
  width: 260px;
  min-width: 260px;
}

.bulk-draw-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  min-height: 188px;
  box-sizing: border-box;
  gap: 7px;
  padding: 22px 10px 14px;
  border: 1px solid #e8f3ff;
  border-radius: 17px;
  background: #fff;
  text-align: center;
}

.bulk-draw-close {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  width: 25px;
  height: 25px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: #f1f2f7;
  color: #8189a3;
  font-size: 10px;
}

.bulk-draw-close:hover,
.bulk-draw-close:focus-visible {
  background: #e7e8f1;
  color: #424b6d;
}

.bulk-draw-result-icon {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 14px;
  background: #fff;
  font-size: 27px;
  line-height: 1;
}

.bulk-draw-point-icon {
  width: 48px;
  height: 48px;
  padding: 8px;
  background: #fff;
  object-fit: contain;
}

.bulk-draw-title {
  display: -webkit-box;
  width: 100%;
  overflow: hidden;
  color: #4d5675;
  font-size: 13px;
  line-height: 1.25;
  text-align: center;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  word-break: keep-all;
}

.bulk-draw-card.draw-win .bulk-draw-result-icon {
  background: #fff;
}

.bulk-draw-card.draw-point .bulk-draw-result-icon {
  background: #fff;
}

.bulk-draw-card.draw-lose .bulk-draw-result-icon {
  background: #fff;
  filter: none;
}

.bulk-draw-card.draw-win .bulk-draw-title {
  color: #3d72c9;
}

.bulk-draw-card.draw-point .bulk-draw-title {
  color: #2f6fed;
}

.bulk-draw-card.draw-lose {
  border-color: #e8f3ff;
  background: #fff;
  opacity: 1;
  justify-content: center;
}

.bulk-draw-card.draw-lose .bulk-draw-title {
  color: #3d72c9;
}

.bulk-draw-prize-card {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 48px;
  box-sizing: border-box;
  gap: 6px;
  padding: 8px;
  border-radius: 12px;
  background: #fff;
}

.bulk-draw-prize-icon {
  display: grid;
  width: 22px;
  height: 22px;
  place-items: center;
  font-size: 20px;
  line-height: 1;
  object-fit: contain;
}

.bulk-draw-card.draw-win .bulk-draw-prize-icon {
  transform: translateY(-1px);
}

.bulk-draw-prize-card strong {
  display: -webkit-box;
  max-width: 100%;
  overflow: hidden;
  color: #3d72c9;
  font-size: 13px;
  line-height: 1.25;
  text-align: center;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  word-break: keep-all;
}

.draw-point .bulk-draw-prize-card strong {
  color: #2f6fed;
}

.draw-lose .bulk-draw-prize-card {
  background: #fff;
}

.draw-lose .bulk-draw-prize-card strong {
  color: #3d72c9;
}

.bulk-draw-card.reward-effect-blue {
  border-color: #5686ed;
  border-width: 2px;
  box-shadow:
    0 0 0 3px rgb(86 134 237 / 30%),
    0 0 20px 6px rgb(86 134 237 / 78%),
    0 0 38px 11px rgb(86 134 237 / 34%),
    inset 0 0 11px rgb(86 134 237 / 20%);
}

.bulk-draw-card.reward-effect-silver {
  border-color: #9ea9b8;
  border-width: 2px;
  box-shadow:
    0 0 0 3px rgb(158 169 184 / 32%),
    0 0 24px 7px rgb(158 169 184 / 82%),
    0 0 42px 12px rgb(158 169 184 / 36%),
    inset 0 0 13px rgb(158 169 184 / 22%);
}

.bulk-draw-card.reward-effect-gold {
  border-color: #e0a51b;
  border-width: 2px;
  box-shadow:
    0 0 0 3px rgb(224 165 27 / 34%),
    0 0 28px 8px rgb(224 165 27 / 86%),
    0 0 46px 13px rgb(224 165 27 / 38%),
    inset 0 0 15px rgb(224 165 27 / 24%);
}

.bulk-draw-card.reward-effect-emerald {
  border-color: #1fa77a;
  border-width: 2px;
  box-shadow:
    0 0 0 3px rgb(31 167 122 / 36%),
    0 0 32px 9px rgb(31 167 122 / 90%),
    0 0 50px 14px rgb(31 167 122 / 40%),
    inset 0 0 17px rgb(31 167 122 / 26%);
}

.bulk-draw-card.reward-effect-sky {
  border-color: #49b9ec;
  border-width: 2px;
  box-shadow:
    0 0 0 3px rgb(73 185 236 / 38%),
    0 0 36px 10px rgb(73 185 236 / 94%),
    0 0 54px 15px rgb(73 185 236 / 42%),
    inset 0 0 19px rgb(73 185 236 / 28%);
}

.bulk-result-confirm {
  display: block;
  width: min(100%, 188px);
  margin: 14px auto 0;
  padding: 8px 16px;
  border: 0;
  border-radius: 9px;
  background: #4f8fe8;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.bulk-result-confirm:hover,
.bulk-result-confirm:focus-visible {
  background: #4f86d2;
}

.result-lose .reward-modal-icon {
  background: #f1f2f7;
  filter: grayscale(0.25);
}

.result-error .reward-modal-icon {
  background: #fff3dd;
}

.reward-modal-confirm {
  width: 100%;
  margin-top: auto;
  padding: 8px 12px;
  border: 0;
  border-radius: 8px;
  background: #4f8fe8;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}

.reward-modal-confirm:hover,
.reward-modal-confirm:focus-visible {
  background: #4f86d2;
}

.reward-modal-confirm-outside {
  box-sizing: border-box;
  width: 260px;
  max-width: 100%;
  flex: 0 0 auto;
  margin-top: 14px;
}

.reward-modal-card.result-bulk .reward-modal-confirm {
  display: none;
}

.reward-modal-card.result-bulk > .reward-modal-close {
  display: none;
}

.reward-modal-enter-active,
.reward-modal-leave-active {
  transition: opacity 120ms ease-out;
}

.reward-modal-enter-active .reward-modal-card,
.reward-modal-leave-active .reward-modal-card {
  will-change: transform, opacity;
  transition:
    transform 160ms cubic-bezier(0.22, 0.8, 0.24, 1),
    opacity 120ms ease-out;
}

.reward-modal-enter-from,
.reward-modal-leave-to {
  opacity: 0;
}

.reward-modal-enter-from .reward-modal-card,
.reward-modal-leave-to .reward-modal-card {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
}

@keyframes reward-icon-bounce {
  0% {
    opacity: 0;
    transform: scale(0.7) rotate(-8deg);
  }

  65% {
    transform: scale(1.08) rotate(3deg);
  }

  100% {
    opacity: 1;
    transform: scale(1) rotate(0);
  }
}

.probability-button {
  border: 0;
  background: transparent;
  color: #4f80c9;
  font-size: 12px;
  cursor: pointer;
}

.probability-control {
  position: absolute;
  z-index: 3;
  top: 14px;
  right: 18px;
}

.probability-popover {
  position: absolute;
  z-index: 2;
  top: calc(100% + 6px);
  right: 0;
  bottom: auto;
  left: auto;
  width: 260px;
  padding: 14px;
  border: 1px solid #e4e7f2;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 12px 28px rgb(38 48 79 / 18%);
  text-align: left;
}

.probability-popover > strong {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
}

.probability-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  padding: 5px 0;
  color: #78829f;
  font-size: 11px;
}

.probability-row b {
  color: #5d91d9;
}

.inventory-title {
  margin-top: 28px;
}

.inventory-card,
.empty-inventory {
  padding: 8px 18px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 5px 20px rgb(52 106 162 / 5%);
}

.inventory-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 13px 0;
  border-bottom: 1px solid #eef0f5;
  cursor: pointer;
  transition: background-color 160ms ease;
}

.inventory-item:last-child {
  border-bottom: 0;
}

.inventory-item:hover,
.inventory-item:focus-visible {
  outline: 0;
  background: #f8fbff;
}

.inventory-item.used {
  color: #737783;
  opacity: 0.7;
}

.inventory-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 12px;
  background: #f1f3fa;
  font-size: 22px;
}

.inventory-info {
  display: grid;
  flex: 1;
  gap: 3px;
}

.inventory-info strong {
  font-size: 14px;
}

.inventory-info span,
.inventory-status {
  color: #9aa2bc;
  font-size: 12px;
}

.inventory-status {
  padding: 5px 9px;
  border-radius: 999px;
  background: #edf2ff;
  color: #6289c2;
}

.used .inventory-status {
  background: #e0e1e5;
  color: #6d7079;
}

.remove-button {
  border: 0;
  background: transparent;
  color: #686b74;
  font-size: 22px;
  line-height: 1;
}

.inventory-detail-backdrop {
  position: fixed;
  z-index: 1100;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgb(20 25 55 / 48%);
}

.inventory-detail-card {
  position: relative;
  width: min(100%, 360px);
  padding: 28px 26px 24px;
  border: 1px solid #e7e5ff;
  border-radius: 22px;
  background: #fff;
  color: #27304f;
  box-shadow: 0 24px 70px rgb(26 31 70 / 25%);
  text-align: center;
}

.inventory-detail-close {
  position: absolute;
  top: 14px;
  right: 14px;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: #f1f2f7;
  color: #8189a3;
}

.inventory-detail-icon {
  display: grid;
  width: 68px;
  height: 68px;
  margin: 0 auto 14px;
  place-items: center;
  border-radius: 20px;
  background: #f1f3fa;
  font-size: 34px;
}

.inventory-detail-kicker {
  display: block;
  margin-bottom: 6px;
  color: #709fe2;
  font-size: 12px;
  font-weight: 800;
}

.inventory-detail-card h2 {
  margin: 0;
  color: #27304f;
  font-size: 20px;
}

.inventory-detail-card > p {
  margin: 10px 0 18px;
  color: #8992ae;
  font-size: 13px;
  line-height: 1.6;
}

.inventory-detail-meta {
  display: grid;
  gap: 9px;
  margin: 0 0 20px;
  padding: 14px 16px;
  border-radius: 14px;
  background: #f6faff;
  text-align: left;
}

.inventory-detail-meta div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.inventory-detail-meta dt,
.inventory-detail-meta dd {
  margin: 0;
  font-size: 12px;
}

.inventory-detail-meta dt {
  color: #929ab3;
}

.inventory-detail-meta dd {
  color: #4f5878;
  font-weight: 800;
}

.inventory-qr-content {
  display: grid;
  justify-items: center;
  gap: 14px;
  padding-top: 4px;
}

.inventory-qr-frame {
  display: grid;
  width: min(100%, 260px);
  aspect-ratio: 1;
  place-items: center;
  padding: 10px;
  border: 1px solid #e8f3ff;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(74 64 175 / 8%);
}

.inventory-qr-frame img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.inventory-qr-notice {
  margin: 0;
  color: #8992ae;
  font-size: 12px;
  line-height: 1.55;
  text-align: center;
}

.inventory-detail-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.inventory-detail-use,
.inventory-detail-dismiss {
  width: 100%;
  padding: 11px 16px;
  border-radius: 12px;
  font-weight: 800;
}

.inventory-detail-use {
  border: 0;
  background: #518ddf;
  color: #fff;
}

.inventory-detail-use:hover:not(:disabled),
.inventory-detail-use:focus-visible:not(:disabled) {
  background: #5d4ed4;
}

.inventory-detail-use:disabled {
  background: #d9d9e8;
  color: #9094a8;
  cursor: not-allowed;
}

.inventory-detail-dismiss {
  border: 1px solid #dedff0;
  background: #fff;
  color: #68718f;
}

.inventory-detail-dismiss:hover,
.inventory-detail-dismiss:focus-visible {
  border-color: #c9c5f4;
  background: #f6faff;
  color: #3d72c9;
}

.inventory-detail-modal-enter-active,
.inventory-detail-modal-leave-active {
  transition: opacity 160ms ease;
}

.inventory-detail-modal-enter-active .inventory-detail-card,
.inventory-detail-modal-leave-active .inventory-detail-card {
  transition: transform 160ms ease;
}

.inventory-detail-modal-enter-from,
.inventory-detail-modal-leave-to {
  opacity: 0;
}

.inventory-detail-modal-enter-from .inventory-detail-card,
.inventory-detail-modal-leave-to .inventory-detail-card {
  transform: translateY(8px) scale(0.98);
}

.empty-inventory {
  display: grid;
  min-height: 160px;
  place-items: center;
  align-content: center;
  gap: 6px;
  color: #8992ae;
  text-align: center;
}

.empty-inventory > span {
  font-size: 30px;
}

.empty-inventory strong {
  color: #4f5878;
}

.empty-inventory p {
  margin: 0;
  font-size: 13px;
}

@media (max-width: 768px) {
  .reward-modal-backdrop {
    padding: 16px 12px;
  }

  .reward-modal-card.result-bulk {
    width: 100%;
  }

  .bulk-draw-panel {
    padding: 18px 16px;
  }

  .bulk-draw-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 26px 22px;
  }

  .point-summary-card {
    padding-right: 26px;
  }

  .point-history-button {
    position: static;
    display: inline-block;
    margin-top: 14px;
    transform: none;
  }

  .box-grid {
    grid-template-columns: 1fr;
  }

  .inventory-status {
    display: none;
  }
}

@media (max-width: 420px) {
  .bulk-draw-grid {
    grid-template-columns: 1fr;
  }
}

/* 결과 연출의 구조를 고정하고, 최종 UI/UX 확정 때 아래 토큰만 교체할 수 있도록 분리함. */
.draw-machine-backdrop {
  --draw-overlay: rgb(8 9 13 / 94%);
  --draw-copy: #ffffff;
  --draw-accent: #ffd85c;
  position: fixed;
  z-index: 1550;
  inset: 0;
  display: grid;
  overflow: hidden;
  place-items: center;
  padding: 24px;
  background: var(--draw-overlay);
  color: var(--draw-copy);
  text-align: center;
  backdrop-filter: blur(4px);
}

.draw-machine-shell {
  width: min(100%, 440px);
}

.draw-machine-kicker {
  display: block;
  margin-bottom: 10px;
  color: rgb(255 255 255 / 60%);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.draw-machine-shell h2 {
  margin: 0;
  color: var(--draw-accent);
  font-size: clamp(24px, 7vw, 34px);
  font-weight: 900;
  line-height: 1.25;
  word-break: keep-all;
}

.draw-machine-shell p {
  margin: 18px auto 0;
  color: rgb(255 255 255 / 78%);
  font-size: 15px;
  font-weight: 700;
  word-break: keep-all;
}

.draw-machine-stage {
  position: relative;
  display: grid;
  width: min(100%, 350px);
  height: 360px;
  margin: 12px auto 0;
  place-items: center;
}

.draw-machine-glow {
  position: absolute;
  top: 78px;
  width: 270px;
  height: 190px;
  border-radius: 50%;
  background: rgb(255 216 92 / 16%);
  filter: blur(28px);
  animation: draw-machine-glow 1.4s ease-in-out infinite alternate;
}

.draw-machine-body {
  position: relative;
  width: 290px;
  height: 320px;
  padding-top: 16px;
}

.draw-machine-cap {
  position: absolute;
  top: 0;
  left: 50%;
  z-index: 2;
  width: 274px;
  height: 30px;
  border: 4px solid #262a2b;
  border-radius: 15px 15px 8px 8px;
  background: linear-gradient(180deg, #16191a, #080909);
  box-shadow: 0 8px 12px rgb(0 0 0 / 30%);
  transform: translateX(-50%);
}

.draw-machine-drum {
  position: relative;
  z-index: 2;
  perspective: 520px;
  width: 260px;
  height: 260px;
  margin: 0 auto;
  overflow: hidden;
  border: 8px solid rgb(235 242 240 / 78%);
  border-radius: 15px 15px 22px 22px;
  background:
    radial-gradient(circle at 30% 20%, rgb(255 255 255 / 38%), transparent 10%),
    radial-gradient(circle at 50% 45%, rgb(255 255 255 / 18%), transparent 42%),
    linear-gradient(145deg, rgb(206 232 232 / 19%), rgb(83 121 133 / 18%));
  box-shadow:
    0 0 0 5px rgb(255 255 255 / 14%),
    0 22px 38px rgb(0 0 0 / 40%),
    inset 0 0 28px rgb(0 0 0 / 28%),
    inset 12px 12px 18px rgb(255 255 255 / 13%);
  backdrop-filter: blur(1px) saturate(115%);
}

.draw-machine-drum-shine {
  position: absolute;
  z-index: 4;
  top: 18px;
  left: 48px;
  width: 74px;
  height: 22px;
  border-radius: 50%;
  background: rgb(255 255 255 / 25%);
  filter: blur(4px);
  transform: rotate(-26deg);
  pointer-events: none;
}

.draw-machine-support {
  position: absolute;
  bottom: 28px;
  z-index: 1;
  width: 52px;
  height: 108px;
  background: linear-gradient(145deg, #262a2c, #080909 72%);
  box-shadow: 0 12px 16px rgb(0 0 0 / 32%);
  clip-path: polygon(20% 0, 100% 100%, 0 100%, 32% 7%);
}

.draw-machine-support-left {
  left: 30px;
  transform: rotate(-5deg);
}

.draw-machine-support-right {
  right: 30px;
  transform: scaleX(-1) rotate(-5deg);
}

.draw-machine-housing {
  position: absolute;
  right: 50%;
  bottom: 26px;
  z-index: 2;
  width: 190px;
  height: 104px;
  border: 5px solid #17191a;
  border-radius: 18px 18px 10px 10px;
  background: linear-gradient(90deg, #373a48, #5b5b78 45%, #353846);
  box-shadow: 0 14px 20px rgb(0 0 0 / 35%), inset 0 5px 12px rgb(255 255 255 / 8%);
  transform: translateX(50%);
}

.draw-machine-platform {
  position: absolute;
  right: 50%;
  bottom: 0;
  z-index: 0;
  width: 290px;
  height: 42px;
  border: 2px solid #3a3d3e;
  border-radius: 3px 3px 12px 12px;
  background: linear-gradient(180deg, #1c1f20, #050606);
  box-shadow: 0 14px 18px rgb(0 0 0 / 36%);
  clip-path: polygon(8% 0, 92% 0, 100% 100%, 0 100%);
  transform: translateX(50%);
}

.draw-machine-lever {
  position: absolute;
  right: 50%;
  bottom: 84px;
  z-index: 6;
  width: 64px;
  height: 64px;
  border: 0;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 24%, #b9bcc0, #92959a 58%, #707379);
  box-shadow:
    0 4px 0 #626368,
    0 9px 12px rgb(0 0 0 / 30%),
    inset 3px 3px 5px rgb(255 255 255 / 22%),
    inset -4px -5px 7px rgb(0 0 0 / 17%);
  transform: translateX(50%);
  animation: draw-machine-lever-turn 1.05s cubic-bezier(0.24, 0.7, 0.24, 1) both;
}

.draw-machine-lever::before {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 40px;
  height: 16px;
  border: 0;
  border-radius: 1px;
  background: linear-gradient(180deg, #e0e2e4, #c0c3c6);
  box-shadow: inset 0 1px 1px rgb(255 255 255 / 70%), inset 0 -1px 1px rgb(80 84 88 / 22%);
  content: "";
  border-radius: 4px;
  transform: translate(-50%, -50%);
}

.draw-machine-lever-arm {
  display: none;
}

.draw-machine-lever-knob {
  display: none;
}

.draw-machine-chute {
  position: absolute;
  bottom: 24px;
  left: 50%;
  z-index: 3;
  display: grid;
  width: 106px;
  height: 67px;
  place-items: start center;
  border: 5px solid rgb(210 232 232 / 76%);
  border-radius: 18px 18px 34px 34px;
  background: linear-gradient(90deg, rgb(112 177 189 / 22%), rgb(240 255 255 / 34%), rgb(112 177 189 / 22%));
  box-shadow: 0 15px 22px rgb(0 0 0 / 32%);
  overflow: hidden;
  transform: translateX(-50%);
}

.draw-machine-output-ball {
  display: grid;
  width: 45px;
  height: 45px;
  margin-top: 8px;
  place-items: center;
  border: 3px solid rgb(255 255 255 / 74%);
  border-radius: 50%;
  background: var(--draw-accent);
  box-shadow:
    inset -6px -7px 10px rgb(0 0 0 / 18%),
    inset 6px 5px 7px rgb(255 255 255 / 45%),
    0 7px 10px rgb(0 0 0 / 28%);
  color: #4c3a0a;
  font-size: 20px;
  font-weight: 900;
  animation: draw-machine-output 1.25s cubic-bezier(0.2, 0.8, 0.2, 1) 1 both;
}

.draw-machine-enter-active,
.draw-machine-leave-active {
  transition: opacity 220ms ease;
}

.draw-machine-enter-active .draw-machine-shell,
.draw-machine-leave-active .draw-machine-shell {
  transition: transform 320ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.draw-machine-enter-from,
.draw-machine-leave-to {
  opacity: 0;
}

.draw-machine-enter-from .draw-machine-shell,
.draw-machine-leave-to .draw-machine-shell {
  transform: translateY(18px) scale(0.96);
}

@keyframes draw-machine-output {
  0%,
  34% {
    opacity: 0;
    transform: translate3d(-10px, -55px, 0) scale(0.62) rotate(-40deg);
  }

  50% {
    opacity: 1;
    transform: translate3d(-12px, -22px, 0) scale(0.86) rotate(120deg);
  }

  70%,
  100% {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1) rotate(420deg);
  }
}

@keyframes draw-machine-lever-turn {
  0% {
    transform: translateX(50%) rotate(0deg);
  }

  38% {
    transform: translateX(50%) rotate(18deg);
  }

  100% {
    transform: translateX(50%) rotate(90deg);
  }
}

@keyframes draw-machine-lever-arm {
  0% {
    transform: translateY(-50%) rotate(-12deg);
  }

  45% {
    transform: translateY(-50%) rotate(26deg);
  }

  100% {
    transform: translateY(-50%) rotate(350deg);
  }
}

@keyframes draw-machine-glow {
  from {
    opacity: 0.55;
    transform: scale(0.92);
  }

  to {
    opacity: 1;
    transform: scale(1.08);
  }
}

@keyframes inline-draw-machine-output {
  0%,
  34% {
    opacity: 0;
    transform: translate3d(-10px, -54px, 0) scale(0.62) rotate(-40deg);
  }

  50% {
    opacity: 1;
    transform: translate3d(-10px, -22px, 0) scale(0.84) rotate(120deg);
  }

  70%,
  100% {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1) rotate(420deg);
  }
}

@keyframes inline-draw-machine-lever-turn {
  0% {
    transform: translateX(50%) rotate(0deg);
  }

  38% {
    transform: translateX(50%) rotate(18deg);
  }

  100% {
    transform: translateX(50%) rotate(90deg);
  }
}

@keyframes inline-draw-machine-lever-arm {
  0% {
    transform: translateY(-50%) rotate(-12deg);
  }

  45% {
    transform: translateY(-50%) rotate(26deg);
  }

  100% {
    transform: translateY(-50%) rotate(350deg);
  }
}

.reward-modal-backdrop {
  --reward-overlay: #08090d;
  --reward-accent: #ffd85c;
  --reward-copy: #ffffff;
  --reward-action: #18e477;
  --reward-action-copy: #062713;
  padding: 0;
  overflow: hidden;
  background: var(--reward-overlay);
}

.reward-particle-layer {
  position: absolute;
  z-index: 0;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.reward-particle {
  position: absolute;
  top: var(--particle-top);
  left: var(--particle-left);
  display: block;
  font-size: var(--particle-size);
  line-height: 1;
  opacity: 0;
  animation: reward-particle-fall var(--particle-duration) ease-in-out var(--particle-delay)
    infinite;
}

.reward-modal-shell {
  position: relative;
  z-index: 1;
  box-sizing: border-box;
  justify-content: center;
  width: min(100%, 440px);
  min-height: 100%;
  padding: 56px 18px 28px;
}

.reward-modal-card.single-modal-card {
  width: min(100%, 420px);
  min-width: 0;
}

.reward-celebration {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  box-sizing: border-box;
  padding: 22px 20px 28px;
  color: var(--reward-copy);
  text-align: center;
  animation: reward-celebration-in 420ms cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.reward-celebration-close {
  position: absolute;
  top: 4px;
  right: 4px;
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 50%;
  background: rgb(255 255 255 / 7%);
  color: rgb(255 255 255 / 78%);
  font-size: 12px;
}

.reward-celebration-close:hover,
.reward-celebration-close:focus-visible {
  background: rgb(255 255 255 / 16%);
  color: var(--reward-copy);
}

.reward-celebration-icon {
  display: grid;
  width: 104px;
  height: 104px;
  margin: 0 auto 18px;
  place-items: center;
  border-radius: 50%;
  background: rgb(255 255 255 / 10%);
  font-size: 64px;
  line-height: 1;
  filter: drop-shadow(0 12px 24px rgb(0 0 0 / 28%));
  animation: reward-celebration-icon 560ms cubic-bezier(0.2, 0.8, 0.2, 1) 80ms both;
}

.reward-celebration-icon img {
  width: 78px;
  height: 78px;
  object-fit: contain;
}

.reward-celebration-kicker {
  margin-bottom: 10px;
  color: rgb(255 255 255 / 62%);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.reward-celebration-title {
  margin: 0;
  color: var(--reward-accent);
  font-size: clamp(24px, 7vw, 36px);
  font-weight: 900;
  line-height: 1.22;
  word-break: keep-all;
}

.reward-celebration-message {
  max-width: 320px;
  margin: 12px auto 20px;
  color: var(--reward-copy);
  font-size: clamp(17px, 4.8vw, 24px);
  font-weight: 800;
  line-height: 1.45;
  word-break: keep-all;
}

.reward-celebration-prize {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 128px;
  max-width: 100%;
  gap: 8px;
  padding: 12px 18px;
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: 999px;
  background: rgb(255 255 255 / 9%);
  color: var(--reward-copy);
}

.reward-celebration-prize img {
  width: 26px;
  height: 26px;
  object-fit: contain;
}

.reward-celebration-prize span {
  font-size: 24px;
  line-height: 1;
}

.reward-celebration-prize strong {
  overflow: hidden;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reward-modal-confirm-outside {
  width: min(100%, 420px);
  margin-top: 4px;
  padding: 14px 18px;
  border-radius: 14px;
  background: var(--reward-action);
  color: var(--reward-action-copy);
  font-size: 16px;
  font-weight: 900;
}

.reward-modal-confirm-outside:hover,
.reward-modal-confirm-outside:focus-visible {
  background: color-mix(in srgb, var(--reward-action) 86%, white);
  color: var(--reward-action-copy);
}

@keyframes reward-celebration-in {
  0% {
    opacity: 0;
    transform: translateY(24px) scale(0.94);
  }

  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes reward-celebration-icon {
  0% {
    opacity: 0;
    transform: scale(0.55) rotate(-12deg);
  }

  68% {
    opacity: 1;
    transform: scale(1.08) rotate(4deg);
  }

  100% {
    transform: scale(1) rotate(0);
  }
}

@keyframes reward-particle-fall {
  0% {
    opacity: 0;
    transform: translate3d(0, -24px, 0) rotate(var(--particle-rotate)) scale(0.72);
  }

  24% {
    opacity: 0.92;
  }

  100% {
    opacity: 0.18;
    transform: translate3d(12px, 34px, 0) rotate(calc(var(--particle-rotate) + 28deg)) scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .draw-machine-output-ball,
  .draw-machine-glow,
  .reward-particle,
  .reward-celebration,
  .reward-celebration-icon {
    animation-duration: 1ms;
    animation-iteration-count: 1;
  }
}
</style>
