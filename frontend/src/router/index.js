import { createRouter, createWebHistory } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import DashboardView from '@/views/dashboard/DashboardView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: DefaultLayout,
      children: [
        {
          path: '',
          redirect: { name: 'dashboard' },
        },
        {
          path: 'api/home',
          name: 'dashboard',
          component: DashboardView,
        },
        {
          path: 'api/institutions',
          name: 'institutions',
          component: DashboardView,
        },
        {
          path: 'api/dashboard',
          name: 'ai-consulting',
          component: DashboardView,
        },
        {
          path: 'api/challenges/current',
          name: 'current-challenge',
          component: DashboardView,
        },
        {
          path: 'api/challenges/:challengeId/feeds',
          name: 'challenge-feed',
          component: DashboardView,
        },
        {
          path: 'api/challenges/rankings/weekly',
          name: 'weekly-ranking',
          component: DashboardView,
        },
        {
          path: 'api/users/me/challenge-dashboard',
          name: 'my-challenge',
          component: DashboardView,
        },
        {
          path: 'api/point-shop',
          name: 'point-shop',
          component: DashboardView,
        },
        {
          path: 'api/reports',
          name: 'reports',
          component: DashboardView,
        },
        {
          path: 'api/users/profile',
          name: 'user-profile',
          component: DashboardView,
        },
      ],
    },
  ],
})

export default router
