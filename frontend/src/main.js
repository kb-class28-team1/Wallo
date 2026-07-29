import { createApp } from "vue";
import { createPinia } from "pinia";
import "pretendard/dist/web/variable/pretendardvariable.css"
import "bootstrap/dist/css/bootstrap.min.css";
import "bootstrap-icons/font/bootstrap-icons.css";
import "@/assets/styles/global.css";
import App from "./App.vue";
import router from "./router";

createApp(App)
  .use(createPinia())
  .use(router)
  .mount("#app");


