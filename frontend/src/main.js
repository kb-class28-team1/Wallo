import { createApp } from "vue";
import { createPinia } from "pinia";
import "pretendard/dist/web/variable/pretendardvariable.css"
import "bootstrap/dist/css/bootstrap.min.css";
import "bootstrap-icons/font/bootstrap-icons.css";
import "@/assets/styles/global.css";
import App from "./App.vue";
import router from "./router";
import { setUnauthorizedHandler } from "@/api/httpClient"
import { useUserStore } from "@/stores/userStore"

const app = createApp(App)
const pinia = createPinia()

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

