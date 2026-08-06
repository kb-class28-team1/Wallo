import axios from "axios";

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
  (error) => {
    const requestUrl = error.config?.url || ""
    const isLoginRequest = requestUrl.includes("/api/auth/login")
    const isSignupRequest = requestUrl.includes("/api/auth/signup")
    const isSessionCheck = requestUrl.includes("/api/auth/me")

    if (
      error.response?.status === 401 &&
      !isLoginRequest &&
      !isSignupRequest &&
      !isSessionCheck &&
      unauthorizedHandler
    ) {
      unauthorizedHandler()
    }

    return Promise.reject(error)
  },
)

export default httpClient;
