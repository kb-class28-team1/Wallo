import http from "k6/http"
import { check } from "k6"
import { Rate, Trend } from "k6/metrics"

const baseUrl = (__ENV.BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, "")
const duration = __ENV.DURATION || "10m"
const rate = Number(__ENV.RATE || 2)
const maxVus = Number(__ENV.MAX_VUS || Math.max(10, rate * 4))
const preAllocatedVus = Number(__ENV.PREALLOCATED_VUS || Math.max(2, rate * 2))
const targetMonth = __ENV.TARGET_MONTH || new Date().toISOString().slice(0, 7)
const tokenFromEnvironment = __ENV.ACCESS_TOKEN || ""
const email = __ENV.TEST_EMAIL || ""
const password = __ENV.TEST_PASSWORD || ""

export const options = {
  scenarios: {
    read_soak: {
      executor: "constant-arrival-rate",
      rate,
      timeUnit: "1s",
      duration,
      preAllocatedVUs: preAllocatedVus,
      maxVUs: maxVus,
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1000", "p(99)<2000"],
    api_errors: ["rate<0.01"],
  },
}

const apiErrors = new Rate("api_errors")
const apiDuration = new Trend("api_duration", true)

function login() {
  if (tokenFromEnvironment) return tokenFromEnvironment
  if (!email || !password) {
    throw new Error("ACCESS_TOKEN 또는 TEST_EMAIL/TEST_PASSWORD를 지정해야 합니다.")
  }

  const response = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { "Content-Type": "application/json" }, tags: { endpoint: "login" } },
  )
  const ok = check(response, {
    "login returns 200": (res) => res.status === 200,
    "login returns access token": (res) => Boolean(res.json("accessToken")),
  })
  if (!ok) throw new Error(`로그인 실패: HTTP ${response.status}`)
  return response.json("accessToken")
}

export function setup() {
  return { accessToken: login() }
}

function get(path, endpoint, accessToken) {
  const response = http.get(`${baseUrl}${path}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    tags: { endpoint },
  })
  const elapsed = Number(response.timings.duration || 0)
  apiDuration.add(elapsed, { endpoint })
  const ok = check(response, {
    [`${endpoint} returns 2xx`]: (res) => res.status >= 200 && res.status < 300,
  })
  apiErrors.add(!ok, { endpoint })
}

export default function (data) {
  const accessToken = data.accessToken
  get("/api/assets", "assets", accessToken)
  get(
    `/api/assets/expense?startDate=${targetMonth}-01&endDate=${targetMonth}-31&page=0&size=50`,
    "expenses",
    accessToken,
  )
  get(`/api/budgets/categories?targetMonth=${targetMonth}`, "budget_categories", accessToken)
}
