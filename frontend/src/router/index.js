import { createRouter, createWebHistory } from "vue-router"
import DefaultLayout from "@/layouts/DefaultLayout.vue"
import LandingView from "@/views/auth/LandingView.vue"
import LoginView from "@/views/auth/LoginView.vue"
import SignupView from "@/views/auth/SignupView.vue"
import ConnectionView from "@/views/asset/ConnectionView.vue"
import DashboardView from "@/views/dashboard/DashboardView.vue"
import SettingsView from "@/views/user/SettingsView.vue"

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "landing",
      component: LandingView,
    },
    {
      path: "/login",
      name: "login",
      component: LoginView,
    },
    {
      path: "/signup",
      name: "signup",
      component: SignupView,
    },
    {
      path: "/app",
      component: DefaultLayout,
      children: [
        {
          path: "/connections/mydata",
          name: "connection",
          component: ConnectionView,
        },
        {
          path: "/api/home",
          alias: "/dashboard",
          name: "dashboard",
          component: DashboardView,
        },
        {
          path: "/api/dashboard",
          name: "ai-consulting",
          component: DashboardView,
        },
        {
          path: "/api/institutions",
          name: "institutions",
          component: DashboardView,
        },
        {
          path: "/api/challenges/current",
          name: "current-challenge",
          component: DashboardView,
        },
        {
          path: "/api/challenges/:challengeId/feeds",
          name: "challenge-feed",
          component: DashboardView,
        },
        {
          path: "/api/challenges/rankings/weekly",
          name: "weekly-ranking",
          component: DashboardView,
        },
        {
          path: "/api/users/me/challenge-dashboard",
          name: "my-challenge",
          component: DashboardView,
        },
        {
          path: "/api/point-shop",
          name: "point-shop",
          component: DashboardView,
        },
        {
          path: "/api/reports",
          name: "reports",
          component: DashboardView,
        },
        {
          path: "/api/users/profile",
          name: "user-profile",
          component: SettingsView,
        },
      ],
    },
  ],
})

export default router
