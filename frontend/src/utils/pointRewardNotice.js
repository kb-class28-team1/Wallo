import { invalidateResourcesByPrefix } from "./resourceCache"

const POINT_SHOP_CACHE_PREFIX = "point-shop:"

// 미션 완료 화면과 전역 토스트 사이의 결합을 줄이기 위해 적립 이벤트만 발행함.
export const announcePointEarned = (point) => {
  if (typeof window === "undefined") return

  const normalizedPoint = Number(point) || 0
  if (normalizedPoint <= 0) return

  // 다른 화면에서 포인트가 변경된 뒤 포인트샵이 이전 조회 캐시를 보여주지 않도록 무효화함.
  invalidateResourcesByPrefix(POINT_SHOP_CACHE_PREFIX)

  window.dispatchEvent(
    new CustomEvent("wallo:point-earned", {
      detail: { point: normalizedPoint },
    }),
  )
}
