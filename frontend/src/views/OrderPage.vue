<template>
  <a-card title="最近订单">
    <a-spin :spinning="false">
      <template v-if="recentOrder">
        <a-descriptions bordered :column="2">
          <a-descriptions-item label="订单号">{{ recentOrder.orderSn }}</a-descriptions-item>
          <a-descriptions-item label="订单状态">
            <a-tag :color="getStatusColor(recentOrder.frontendOrderStatus)">
              {{ getStatusLabel(recentOrder.frontendOrderStatus) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="车次ID">{{ recentOrder.trainId }}</a-descriptions-item>
          <a-descriptions-item label="车次号">{{ recentOrder.trainNumber }}</a-descriptions-item>
          <a-descriptions-item label="出发站">{{ recentOrder.departure }}</a-descriptions-item>
          <a-descriptions-item label="到达站">{{ recentOrder.arrival }}</a-descriptions-item>
          <a-descriptions-item label="用户ID">{{ recentOrder.userId }}</a-descriptions-item>
          <a-descriptions-item label="用户名">{{ recentOrder.username }}</a-descriptions-item>
        </a-descriptions>

        <a-divider>乘客信息</a-divider>

        <a-table
          :dataSource="recentOrder.items"
          :columns="orderItemColumns"
          :pagination="false"
          rowKey="passengerId"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'passengerId'">{{ record.passengerId }}</template>
            <template v-else-if="column.key === 'seatType'">{{ getSeatTypeLabel(record.seatType) }}</template>
            <template v-else-if="column.key === 'carriageNumber'">{{ record.carriageNumber }}</template>
            <template v-else-if="column.key === 'seatNumber'">{{ record.seatNumber }}</template>
            <template v-else-if="column.key === 'amount'">￥{{ record.amount }}</template>
          </template>
        </a-table>

        <a-divider />

        <a-space>
          <a-button
            type="primary"
            :loading="payLoading"
            :disabled="recentOrder.frontendOrderStatus !== 'PENDING'"
            @click="handlePay"
          >
            支付订单
          </a-button>
          <a-button
            :loading="cancelLoading"
            :disabled="recentOrder.frontendOrderStatus !== 'PENDING'"
            @click="handleCancel"
          >
            取消订单
          </a-button>
        </a-space>
      </template>

      <a-empty v-else description="暂无最近订单">
        <a-button type="primary" @click="goToTicketPage">去购票</a-button>
      </a-empty>
    </a-spin>
  </a-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { payOrder as payOrderApi, cancelOrder as cancelOrderApi } from '../api/order'
import { useRecentOrder } from '../composables/useRecentOrder'
import { SEAT_TYPE_OPTIONS } from '../constants/stations'
import type { FrontendOrderStatus } from '../types/api'

const router = useRouter()
const { recentOrder, updateOrderStatus } = useRecentOrder()

const payLoading = ref(false)
const cancelLoading = ref(false)

const orderItemColumns = [
  { title: '乘客ID', key: 'passengerId' },
  { title: '座席类型', key: 'seatType' },
  { title: '车厢号', key: 'carriageNumber' },
  { title: '座位号', key: 'seatNumber' },
  { title: '金额', key: 'amount' }
]

function getSeatTypeLabel(seatType: number): string {
  return SEAT_TYPE_OPTIONS.find(s => s.value === seatType)?.label ?? `未知(${seatType})`
}

function getStatusColor(status: FrontendOrderStatus): string {
  switch (status) {
    case 'PENDING': return 'orange'
    case 'PAID': return 'green'
    case 'CANCELED': return 'red'
    default: return 'default'
  }
}

function getStatusLabel(status: FrontendOrderStatus): string {
  switch (status) {
    case 'PENDING': return '待支付'
    case 'PAID': return '已支付'
    case 'CANCELED': return '已取消'
    default: return status
  }
}

function goToTicketPage() {
  router.push({ name: 'ticket' })
}

async function handlePay() {
  if (!recentOrder.value) return

  payLoading.value = true
  try {
    await payOrderApi(recentOrder.value.orderSn)
    updateOrderStatus('PAID')
    message.success('支付成功')
  } catch {
    // error handled by interceptor
  } finally {
    payLoading.value = false
  }
}

async function handleCancel() {
  if (!recentOrder.value) return

  cancelLoading.value = true
  try {
    await cancelOrderApi(recentOrder.value.orderSn)
    updateOrderStatus('CANCELED')
    message.success('订单已取消')
  } catch {
    // error handled by interceptor
  } finally {
    cancelLoading.value = false
  }
}
</script>
