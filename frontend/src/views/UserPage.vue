<template>
  <a-card title="用户信息">
    <a-spin :spinning="userLoading">
      <a-descriptions v-if="userInfo" bordered :column="2">
        <a-descriptions-item label="ID">{{ userInfo.id }}</a-descriptions-item>
        <a-descriptions-item label="用户名">{{ userInfo.username }}</a-descriptions-item>
        <a-descriptions-item label="真实姓名">{{ userInfo.realName }}</a-descriptions-item>
      </a-descriptions>
      <a-empty v-else description="暂无用户信息" />
    </a-spin>
  </a-card>

  <a-card title="乘车人列表" style="margin-top: 16px">
    <a-spin :spinning="passengersLoading">
      <a-table
        v-if="passengers.length > 0"
        :dataSource="passengers"
        :columns="passengerColumns"
        :pagination="false"
        rowKey="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'id'">{{ record.id }}</template>
          <template v-else-if="column.key === 'realName'">{{ record.realName }}</template>
          <template v-else-if="column.key === 'idCard'">{{ record.idCard }}</template>
        </template>
      </a-table>
      <a-empty v-else description="暂无乘车人信息" />
    </a-spin>
  </a-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUserById, getPassengers } from '../api/user'
import { useCurrentUser } from '../composables/useCurrentUser'
import type { UserInfo, Passenger } from '../types/api'

const { userId } = useCurrentUser()

const userInfo = ref<UserInfo | null>(null)
const passengers = ref<Passenger[]>([])
const userLoading = ref(false)
const passengersLoading = ref(false)

const passengerColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '姓名', dataIndex: 'realName', key: 'realName' },
  { title: '身份证', dataIndex: 'idCard', key: 'idCard' }
]

async function fetchUserInfo() {
  userLoading.value = true
  try {
    const res = await getUserById(userId.value)
    userInfo.value = res.data
  } catch {
    userInfo.value = null
  } finally {
    userLoading.value = false
  }
}

async function fetchPassengers() {
  passengersLoading.value = true
  try {
    const res = await getPassengers(userId.value)
    passengers.value = res.data
  } catch {
    passengers.value = []
  } finally {
    passengersLoading.value = false
  }
}

onMounted(() => {
  fetchUserInfo()
  fetchPassengers()
})
</script>
