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

// 랜덤박스의 상품 구성과 당첨 확률을 조회함
export const getRandomBoxDetail = async (boxId) => {
  try {
    const response = await httpClient.get(`/api/point-shop/boxes/${boxId}`)
    return response.data
  } catch (error) {
    const message = error.response?.data?.message || "랜덤박스 확률을 불러오지 못했습니다."
    throw new Error(message)
  }
}

// 기본 랜덤박스를 열고 포인트 차감 및 당첨 결과를 조회함
export const openRandomBox = async (boxId) => {
  try {
    const response = await httpClient.post(`/api/point-shop/boxes/${boxId}/open`)
    return response.data
  } catch (error) {
    const message = error.response?.data?.message || "랜덤박스를 열지 못했습니다."
    throw new Error(message)
  }
}

// 사용 완료된 보관함 상품을 소프트 삭제함
export const deleteUsedInventoryItem = async (inventoryId) => {
  try {
    await httpClient.delete(`/api/users/me/inventory/${inventoryId}`)
  } catch (error) {
    const message = error.response?.data?.message || "사용 완료 상품을 삭제하지 못했습니다."
    throw new Error(message)
  }
}
