import axios from "axios"

// API 기본 주소와 인증 쿠키 전달 설정을 공통 적용함
const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "",
  withCredentials: true,
})

export default httpClient
