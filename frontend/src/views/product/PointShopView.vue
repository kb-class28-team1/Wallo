<script setup>
import { computed, onMounted, ref } from "vue"
import { getPointShop, openRandomBox } from "@/api/pointShopApi"
import { useUserStore } from "@/stores/userStore"

const userStore = useUserStore()
const activeProbabilityBox = ref(null)
const shopPointBalance = ref(null)
const isLoading = ref(false)
const isOpeningBox = ref(false)
const errorMessage = ref("")

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
      { label: "편의점 1,000원 금액권", rate: 60 },
      { label: "아메리카노 기프티콘", rate: 30 },
      { label: "편의점 5,000원 금액권", rate: 10 },
    ],
  },
])

// 보관함 목록은 포인트샵 조회 API 응답으로 교체함
const inventoryItems = ref([])

const formattedPoint = computed(() =>
  `${Number(shopPointBalance.value ?? userStore.pointBalance ?? 0).toLocaleString("ko-KR")}P`,
)

const currentPoint = computed(() =>
  Number(shopPointBalance.value ?? userStore.pointBalance ?? 0),
)

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

const loadPointShop = async () => {
  isLoading.value = true
  errorMessage.value = ""

  try {
    const response = await getPointShop()
    const pointShop = response?.data || response

    shopPointBalance.value = pointShop?.pointBalance ?? 0
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
          used: item.status === "USED",
        }))
      : []
  } catch (error) {
    errorMessage.value = error.message || "포인트 샵 정보를 불러오지 못했습니다."
    alert(errorMessage.value)
  } finally {
    isLoading.value = false
  }
}

const toggleProbability = (boxId) => {
  activeProbabilityBox.value =
    activeProbabilityBox.value === boxId ? null : boxId
}

const handleOpenBox = async (box) => {
  if (isOpeningBox.value || currentPoint.value < box.price) {
    if (currentPoint.value < box.price) {
      alert("보유 포인트가 부족합니다.")
    }
    return
  }

  if (!window.confirm(`${box.price.toLocaleString("ko-KR")}P를 사용해 상자를 열까요?`)) {
    return
  }

  isOpeningBox.value = true
  try {
    const response = await openRandomBox(box.id)
    const result = response?.data || response
    shopPointBalance.value = result?.remainingPoint ?? shopPointBalance.value
    alert(`${result?.reward?.itemName || "상품"}에 당첨되었습니다!`)
    await loadPointShop()
  } catch (error) {
    alert(error.message || "랜덤박스를 열지 못했습니다.")
  } finally {
    isOpeningBox.value = false
  }
}

// 사용 처리된 아이템만 화면 목록에서 삭제함
const removeUsedItem = (itemId) => {
  inventoryItems.value = inventoryItems.value.filter(
    (item) => item.id !== itemId || !item.used,
  )
}

// 페이지에 들어오면 로그인 사용자의 포인트와 보관함을 조회함
onMounted(loadPointShop)
</script>

<template>
  <section class="point-shop-page">
    <header class="page-heading d-flex align-items-center gap-3 mb-4">
      <button type="button" class="btn back-button" @click="$router.back()">
        <i class="bi bi-chevron-left" aria-hidden="true"></i>
        뒤로
      </button>
      <h1 class="mb-0">포인트 샵</h1>
    </header>

    <article class="point-summary-card">
      <span>보유 포인트</span>
      <strong>{{ formattedPoint }}</strong>
      <p>오늘의 미션을 인증하고 포인트를 모아보세요 🪙</p>
    </article>

    <div v-if="isLoading" class="loading-message" role="status">
      포인트샵 정보를 불러오는 중임...
    </div>

    <div v-if="errorMessage" class="error-message" role="alert">
      <span>{{ errorMessage }}</span>
      <button type="button" class="btn retry-button" @click="loadPointShop">
        다시 시도
      </button>
    </div>

    <div class="section-title">
      <h2>🎁 랜덤 박스</h2>
      <span>확률에 따라 기프티콘 상품이 나와요</span>
    </div>

    <div class="box-grid">
      <article
        v-for="box in randomBoxes"
        :key="box.id"
        class="random-box-card"
        :class="box.colorClass"
      >
        <div class="box-icon">{{ box.icon }}</div>
        <h3>{{ box.name }}</h3>
        <p>{{ box.description }}</p>
        <strong class="box-price">🪙 {{ box.price.toLocaleString("ko-KR") }}P</strong>
        <button
          type="button"
          class="open-box-button"
          :disabled="isOpeningBox || currentPoint < box.price"
          @click="handleOpenBox(box)"
        >
          {{ isOpeningBox ? "상자를 여는 중임..." : "상자 열기" }}
        </button>
        <button
          type="button"
          class="probability-button"
          :aria-expanded="activeProbabilityBox === box.id"
          @click="toggleProbability(box.id)"
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
      </article>
    </div>

    <div class="section-title inventory-title">
      <h2>🎒 내 보관함</h2>
      <span>사용 가능한 {{ inventoryItems.filter((item) => !item.used).length }}개 · 전체 {{ inventoryItems.length }}개</span>
    </div>

    <article v-if="inventoryItems.length" class="inventory-card">
      <div
        v-for="item in inventoryItems"
        :key="item.id"
        class="inventory-item"
        :class="{ used: item.used }"
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
          @click="removeUsedItem(item.id)"
        >
          ×
        </button>
      </div>
    </article>

    <article v-else class="empty-inventory">
      <span>🎒</span>
      <strong>보관함이 비어 있어요.</strong>
      <p>랜덤박스에서 획득한 상품이 이곳에 표시돼요.</p>
    </article>
  </section>
</template>

<style scoped>
.point-shop-page {
  width: 100%;
  color: #27304f;
}

.page-heading h1 {
  font-size: 28px;
  font-weight: 800;
}

.back-button {
  border: 1px solid #e4e7f2;
  border-radius: 999px;
  background: #fff;
  color: #6d7594;
  font-size: 14px;
  font-weight: 700;
}

.point-summary-card {
  padding: 26px;
  border-radius: 20px;
  background: linear-gradient(110deg, #6857eb, #9178ff);
  color: #fff;
  box-shadow: 0 16px 30px rgb(103 83 226 / 22%);
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
  min-height: 258px;
  padding: 20px;
  border: 2px solid #e6e9f3;
  border-radius: 18px;
  background: #fff;
  text-align: center;
}

.box-icon {
  display: grid;
  width: 68px;
  height: 68px;
  margin: 0 auto 12px;
  place-items: center;
  border-radius: 20px;
  background: #f1f3fa;
  font-size: 34px;
}

.random-box-card h3 {
  margin: 0 0 6px;
  font-size: 17px;
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

.open-box-button {
  display: block;
  width: 100%;
  margin: 12px 0 8px;
  padding: 10px 14px;
  border: 0;
  border-radius: 10px;
  background: #6d5df0;
  color: #fff;
  font-size: 13px;
  font-weight: 800;
}

.open-box-button:disabled {
  background: #c9cbe0;
  cursor: not-allowed;
}

.probability-button {
  border: 0;
  background: transparent;
  color: #7770bb;
  font-size: 12px;
  cursor: pointer;
}

.probability-popover {
  position: absolute;
  z-index: 2;
  right: 18px;
  bottom: 42px;
  left: 18px;
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
  color: #6c60e9;
}

.inventory-title {
  margin-top: 28px;
}

.inventory-card,
.empty-inventory {
  padding: 8px 18px;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 5px 20px rgb(48 60 110 / 5%);
}

.inventory-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 13px 0;
  border-bottom: 1px solid #eef0f5;
}

.inventory-item:last-child {
  border-bottom: 0;
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
  color: #6670c8;
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
  .box-grid {
    grid-template-columns: 1fr;
  }

  .inventory-status {
    display: none;
  }
}
</style>
