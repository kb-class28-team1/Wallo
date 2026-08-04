import httpClient from "./httpClient"
import { getApiErrorMessage } from "@/utils/apiError"

// 상단바에 표시할 로그인 사용자 프로필과 포인트를 조회함
export const getUserProfile = async () => {
  try {
    const response = await httpClient.get("/api/users/profile")
    return response.data
  } catch (error) {
    // 화면에서 사용자에게 통신 실패 원인을 알릴 수 있도록 오류를 전달함
    throw new Error(getApiErrorMessage(error, "사용자 정보를 불러오지 못했습니다."))
  }
}
