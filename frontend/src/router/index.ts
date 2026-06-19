import { createRouter, createWebHistory } from 'vue-router'
import LabPage from '../views/LabPage.vue'
import TicketPage from '../views/TicketPage.vue'
import OrderPage from '../views/OrderPage.vue'
import PayDebugPage from '../views/PayDebugPage.vue'
import UserPage from '../views/UserPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/lab'
    },
    {
      path: '/lab',
      name: 'lab',
      component: LabPage
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
      path: '/pay',
      name: 'pay',
      component: PayDebugPage
    },
    {
      path: '/user',
      name: 'user',
      component: UserPage
    }
  ]
})

export default router
