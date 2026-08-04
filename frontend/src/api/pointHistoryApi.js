import httpClient from "./httpClient"

// 로그인 사용자의 포인트 내역과 상단 요약을 DB에서 조회함.
export const getPointHistory = async (params = {}) => {
  try {
    const response = await httpClient.get("/api/point-history", { params })
    return response.data
  } catch (error) {
    const message =
      error.response?.data?.message ||
      error.response?.data?.data?.message ||
      "포인트 내역을 불러오지 못했습니다."
    throw new Error(message)
  }
}
