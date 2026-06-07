import { createRouter, createWebHistory } from 'vue-router'
import TicketPage from '../views/TicketPage.vue'
import OrderPage from '../views/OrderPage.vue'
import UserPage from '../views/UserPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/ticket'
    },
    {
      path: '/ticket',
      name: 'ticket',
      component: TicketPage
    },
    {
      path: '/order',
      name: 'order',
      component: OrderPage
    },
    {
      path: '/user',
      name: 'user',
      component: UserPage
    }
  ]
})

export default router
