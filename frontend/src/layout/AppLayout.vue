<template>
  <a-config-provider>
    <a-layout class="app-layout">
      <a-layout-header class="header">
        <div class="logo">TickGo 轻量前端</div>
        <a-menu
          v-model:selectedKeys="currentMenu"
          mode="horizontal"
          :items="menuItems"
          @click="handleMenuClick"
        />
      </a-layout-header>
      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-config-provider>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useRecentOrder } from '../composables/useRecentOrder'

const router = useRouter()
const route = useRoute()
const { recentOrder } = useRecentOrder()

const currentMenu = ref<string[]>([route.name as string])

const menuItems = [
  { key: 'ticket', label: '购票' },
  { key: 'order', label: '订单' },
  { key: 'user', label: '用户' }
]

watch(() => route.name, (name) => {
  currentMenu.value = [name as string]
})

function handleMenuClick({ key }: { key: string }) {
  router.push({ name: key })
}
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  padding: 0 24px;
  background: #001529;
}

.logo {
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  margin-right: 32px;
}

.content {
  padding: 24px;
  background: #f0f2f5;
  min-height: calc(100vh - 64px);
}
</style>
