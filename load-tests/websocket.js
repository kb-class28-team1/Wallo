import http from "k6/http"
import ws from "k6/ws"
import { check } from "k6"
import { Counter } from "k6/metrics"

const baseUrl = (__ENV.BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, "")
const wsBaseUrl = (__ENV.WS_URL || baseUrl.replace(/^http/, "ws")).replace(/\/$/, "")
const challengeId = __ENV.CHALLENGE_ID || "1101"
const duration = __ENV.DURATION || "1m"
const vus = Number(__ENV.VUS || 10)
const socketLifetimeMs = Number(__ENV.SOCKET_LIFETIME_MS || 30000)
const sendMessages = (__ENV.SEND_MESSAGES || "false").toLowerCase() === "true"
const messageIntervalMs = Number(__ENV.MESSAGE_INTERVAL_MS || 1000)
const tokenFromEnvironment = __ENV.ACCESS_TOKEN || ""
const email = __ENV.TEST_EMAIL || ""
const password = __ENV.TEST_PASSWORD || ""

export const options = {
  scenarios: {
    websocket_sessions: {
      executor: "constant-vus",
      vus,
      duration,
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
    ws_handshake_errors: ["count==0"],
    ws_message_errors: ["rate<0.01"],
    ws_session_duration: ["p(95)<35000"],
  },
}

const handshakeErrors = new Counter("ws_handshake_errors")
const messageErrors = new Counter("ws_message_errors")

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

export default function (data) {
  const url = `${wsBaseUrl}/ws/challenges/${challengeId}?accessToken=${encodeURIComponent(data.accessToken)}`
  const response = ws.connect(url, { tags: { endpoint: "challenge_chat" } }, (socket) => {
    socket.on("open", () => {
      if (!sendMessages) return
      socket.setInterval(() => {
        try {
          socket.send(JSON.stringify({
            content: `k6-${__VU}-${Date.now()}`,
            referenceFeedId: null,
          }))
        } catch (_) {
          messageErrors.add(1)
        }
      }, messageIntervalMs)
    })
    socket.on("message", (payload) => {
      try {
        const parsed = JSON.parse(payload)
        if (parsed.type === "ERROR") messageErrors.add(1)
      } catch (_) {
        messageErrors.add(1)
      }
    })
    socket.on("error", () => messageErrors.add(1))
    socket.setTimeout(() => socket.close(), socketLifetimeMs)
  })

  const connected = check(response, {
    "WebSocket handshake returns 101": (res) => res && res.status === 101,
  })
  if (!connected) handshakeErrors.add(1)
}
