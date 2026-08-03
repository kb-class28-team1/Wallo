import httpClient from "./httpClient"

// 포인트 잔액, 랜덤 박스, 보관함 정보를 한 번에 조회함
export const getPointShop = async () => {
  try {
    const response = await httpClient.get("/api/point-shop")
    return response.data
  } catch (error) {
    // 서버에서 전달한 메시지를 우선 사용하고, 없으면 기본 안내 문구를 사용함
    const message = error.response?.data?.message || "포인트 샵 정보를 불러오지 못했습니다."
    throw new Error(message)
  }
}
