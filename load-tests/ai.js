import http from "k6/http"
import { check } from "k6"
import { Rate } from "k6/metrics"

const aiBaseUrl = (__ENV.AI_BASE_URL || "http://127.0.0.1:8000").replace(/\/$/, "")
const mode = (__ENV.AI_MODE || "health").toLowerCase()
const duration = __ENV.DURATION || "1m"
const rate = Number(__ENV.RATE || 1)
const maxVus = Number(__ENV.MAX_VUS || Math.max(5, rate * 4))
const preAllocatedVus = Number(__ENV.PREALLOCATED_VUS || Math.max(2, rate * 2))
const allowExternal = (__ENV.ALLOW_EXTERNAL_AI || "false").toLowerCase() === "true"

if (mode === "chat" && !allowExternal) {
  throw new Error(
    "AI_MODE=chat은 외부 AI 호출을 발생시킵니다. 승인된 환경에서 ALLOW_EXTERNAL_AI=true를 명시해야 합니다.",
  )
}

export const options = {
  scenarios: {
    ai_requests: {
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
    http_req_duration: ["p(95)<30000"],
    ai_errors: ["rate<0.01"],
  },
}

const aiErrors = new Rate("ai_errors")

export default function () {
  let response
  if (mode === "health") {
    response = http.get(`${aiBaseUrl}/api/health`, { tags: { endpoint: "ai_health" } })
  } else {
    response = http.post(
      `${aiBaseUrl}/api/chat`,
      JSON.stringify({ message: __ENV.AI_MESSAGE || "이번 달 소비를 줄일 방법을 알려줘" }),
      {
        headers: { "Content-Type": "application/json" },
        tags: { endpoint: "ai_chat" },
        timeout: "60s",
      },
    )
  }

  const ok = check(response, {
    [`${mode} returns 2xx`]: (res) => res.status >= 200 && res.status < 300,
  })
  aiErrors.add(!ok)
}
