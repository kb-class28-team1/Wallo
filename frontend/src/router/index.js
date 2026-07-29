import { createRouter, createWebHistory } from "vue-router";
import ConnectionView from "@/views/asset/ConnectionView.vue";
import DashboardView from "@/views/dashboard/DashboardView.vue";

const routes = [
  {
    path: "/",
    redirect: "/connections/mydata",
  },
  {
    path: "/connections/mydata",
    name: "Connection",
    component: ConnectionView,
  },
  {
    path: "/dashboard",
    name: "Dashboard",
    component: DashboardView,
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

export default router;
