import { createRouter, createWebHistory } from "vue-router"
import DefaultLayout from "@/layouts/DefaultLayout.vue"
import DashboardView from "@/views/dashboard/DashboardView.vue"
import ConnectionView from "@/views/asset/ConnectionView.vue";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      component: DefaultLayout,
      children: [
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
          path: "/dashboard", //home
          name: "Dashboard",
          component: DashboardView,
        },
        {
          path: "api/dashboard",//ai컨설팅 체크
          name: "ai-consulting",
          component: DashboardView,
        },
        {
          path: "api/institutions",//자산 관리
          name: "institutions",
          component: DashboardView,
        },
        {
          path: "api/challenges/current",
          name: "current-challenge",
          component: DashboardView,
        },
        {
          path: "api/challenges/:challengeId/feeds",
          name: "challenge-feed",
          component: DashboardView,
        },
        {
          path: "api/challenges/rankings/weekly",
          name: "weekly-ranking",
          component: DashboardView,
        },
        {
          path: "api/users/me/challenge-dashboard",
          name: "my-challenge",
          component: DashboardView,
        },
        {
          path: "api/point-shop",
          name: "point-shop",
          component: DashboardView,
        },
        {
          path: "api/reports",
          name: "reports",
          component: DashboardView,
        },
        {
          path: "api/users/profile",
          name: "user-profile",
          component: DashboardView,
        },
      ],
    },
  ],
})

