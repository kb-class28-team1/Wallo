import { createApp } from "vue";
import { createPinia } from "pinia";
import "bootstrap/dist/css/bootstrap.min.css";
import "bootstrap-icons/font/bootstrap-icons.css";
import "@/assets/styles/tokens.css";
import "@/assets/styles/global.css";
import App from "./App.vue";
import router from "./router";
import { setUnauthorizedHandler } from "@/api/httpClient"
import { useToastStore } from "@/stores/toastStore"
import { useUserStore } from "@/stores/userStore"

const app = createApp(App)
const pinia = createPinia()
const toastStore = useToastStore(pinia)

let lastRuntimeErrorAt = 0
const notifyRuntimeError = (message) => {
  const now = Date.now()
  if (now - lastRuntimeErrorAt < 3000) return

  lastRuntimeErrorAt = now
  toastStore.show(message, { variant: "danger", duration: 8000 })
}

app.config.errorHandler = (error, _instance, info) => {
  console.error("[Vue runtime error]", error, info)
  notifyRuntimeError("화면을 불러오는 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.")
}

router.onError((error) => {
  console.error("[Router error]", error)
  notifyRuntimeError("페이지를 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.")
})

app.use(pinia)
app.use(router)

const userStore = useUserStore(pinia)
setUnauthorizedHandler(() => {
  const currentRoute = router.currentRoute.value
  userStore.clearAuth()

  if (currentRoute.name !== "login" && currentRoute.name !== "signup") {
    router.replace({
      name: "login",
      query: {
        redirect: currentRoute.fullPath,
        reason: "expired",
      },
    })
  }
})

app.mount("#app")

