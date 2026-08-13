import axios from "axios";
import { clearAccessToken, getAccessToken, setAccessToken } from "./authToken"

const httpClient = axios.create({
  baseURL: "/",
  withCredentials: true,
  headers: {
    "Content-Type": "application/json",
  },
});

let unauthorizedHandler = null

export const setUnauthorizedHandler = (handler) => {
  unauthorizedHandler = handler
}

httpClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const requestUrl = error.config?.url || ""
    const isLoginRequest = requestUrl.includes("/api/auth/login")
    const isSignupRequest = requestUrl.includes("/api/auth/signup")
    const isSessionCheck = requestUrl.includes("/api/auth/me")
    const isRefreshRequest = requestUrl.includes("/api/auth/refresh")
    const isLogoutRequest = requestUrl.includes("/api/auth/logout")

    if (
      error.response?.status === 401 &&
      !error.config?._retry &&
      !isLoginRequest &&
      !isSignupRequest &&
      !isSessionCheck &&
      !isRefreshRequest &&
      !isLogoutRequest
    ) {
      error.config._retry = true
      try {
        const refreshResponse = await axios.post("/api/auth/refresh", null, {
          withCredentials: true,
        })
        setAccessToken(refreshResponse.data?.accessToken)
        error.config.headers = error.config.headers || {}
        error.config.headers.Authorization = `Bearer ${getAccessToken()}`
        return httpClient.request(error.config)
      } catch (refreshError) {
        clearAccessToken()
      }
    }

    if (
      error.response?.status === 401 &&
      !isLoginRequest &&
      !isSignupRequest &&
      !isSessionCheck &&
      !isRefreshRequest &&
      unauthorizedHandler
    ) {
      unauthorizedHandler()
    }

    return Promise.reject(error)
  },
)

httpClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export default httpClient;
