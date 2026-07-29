import { createRouter, createWebHistory } from "vue-router";
import ConnectionView from "@/views/asset/ConnectionView.vue";

const DashboardView = {
  template: `
    <main class="container py-5">
      <h1 class="h3 fw-bold mb-3">대시보드</h1>
      <p class="text-secondary mb-0">자산 연동이 완료되었습니다.</p>
    </main>
  `,
};

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


