<template>
  <a-row :gutter="[16, 16]">
    <a-col :xs="24" :lg="10">
      <a-card title="全部订单">
        <template #extra>
          <a-button type="link" size="small" :loading="listLoading" @click="fetchOrders">
            刷新列表
          </a-button>
        </template>

        <a-table
          :dataSource="orders"
          :columns="orderColumns"
          :pagination="{ pageSize: 6 }"
          :loading="listLoading"
          rowKey="orderSn"
          :customRow="customRow"
          :rowClassName="rowClassName"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'orderSn'">{{ record.orderSn }}</template>
            <template v-else-if="column.key === 'trainNumber'">{{ record.trainNumber }}</template>
            <template v-else-if="column.key === 'route'">{{ record.departure }} -> {{ record.arrival }}</template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="getStatusColor(mapBackendStatus(record.status))">
                {{ getStatusLabel(mapBackendStatus(record.status)) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'orderTime'">{{ formatOrderTime(record.orderTime) }}</template>
            <template v-else-if="column.key === 'expireTime'">{{ formatOrderTime(record.expireTime) }}</template>
          </template>
        </a-table>

        <a-empty v-if="!listLoading && !orders.length" description="暂无订单">
          <a-button type="primary" @click="goToTicketPage">去购票</a-button>
        </a-empty>
      </a-card>
    </a-col>

    <a-col :xs="24" :lg="14">
      <a-card title="订单详情">
        <template #extra>
          <a-button
            v-if="selectedOrder"
            type="link"
            size="small"
            :loading="statusLoading"
            @click="refreshSelectedOrderStatus"
          >
            刷新状态
          </a-button>
        </template>

        <template v-if="selectedOrder">
          <a-descriptions bordered :column="2">
            <a-descriptions-item label="订单号">{{ selectedOrder.orderSn }}</a-descriptions-item>
            <a-descriptions-item label="订单状态">
              <a-tag :color="getStatusColor(selectedOrderStatus)">
                {{ getStatusLabel(selectedOrderStatus) }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="车次ID">{{ selectedOrder.trainId }}</a-descriptions-item>
            <a-descriptions-item label="车次号">{{ selectedOrder.trainNumber }}</a-descriptions-item>
            <a-descriptions-item label="出发站">{{ selectedOrder.departure }}</a-descriptions-item>
            <a-descriptions-item label="到达站">{{ selectedOrder.arrival }}</a-descriptions-item>
            <a-descriptions-item label="用户ID">{{ selectedOrder.userId }}</a-descriptions-item>
            <a-descriptions-item label="用户名">{{ selectedOrder.username }}</a-descriptions-item>
            <a-descriptions-item label="下单时间">{{ formatOrderTime(selectedOrder.orderTime) }}</a-descriptions-item>
            <a-descriptions-item label="过期时间">{{ formatOrderTime(selectedOrder.expireTime) }}</a-descriptions-item>
          </a-descriptions>

          <a-divider>乘客信息</a-divider>

          <a-table
            :dataSource="selectedOrder.items"
            :columns="orderItemColumns"
            :pagination="false"
            rowKey="seatNumber"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'passengerName'">{{ record.passengerName }}</template>
              <template v-else-if="column.key === 'seatType'">{{ getSeatTypeLabel(record.seatType) }}</template>
              <template v-else-if="column.key === 'carriageNumber'">{{ record.carriageNumber }}</template>
              <template v-else-if="column.key === 'seatNumber'">{{ record.seatNumber }}</template>
              <template v-else-if="column.key === 'amount'">￥{{ formatAmount(record.amount) }}</template>
            </template>
          </a-table>

          <a-divider />

          <a-space>
            <a-button
              type="primary"
              :loading="payLoading"
              :disabled="selectedOrderStatus !== 'PENDING'"
              @click="handlePay"
            >
              支付订单
            </a-button>
            <a-button
              :loading="cancelLoading"
              :disabled="selectedOrderStatus !== 'PENDING' && selectedOrderStatus !== 'PAID'"
              @click="handleCancel"
            >
              {{ selectedOrderStatus === 'PAID' ? '退票' : '取消订单' }}
            </a-button>
          </a-space>
        </template>

        <a-empty v-else description="请选择一个订单" />
      </a-card>
    </a-col>
  </a-row>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { cancelOrder as cancelOrderApi, getOrderList, getOrderStatus, payOrder as payOrderApi } from '../api/order'
import { useCurrentUser } from '../composables/useCurrentUser'
import { useRecentOrder } from '../composables/useRecentOrder'
import { SEAT_TYPE_OPTIONS } from '../constants/stations'
import type { FrontendOrderStatus, OrderDetail } from '../types/api'

const router = useRouter()
const { userId } = useCurrentUser()
const { recentOrder, updateOrderStatus } = useRecentOrder()

const listLoading = ref(false)
const payLoading = ref(false)
const cancelLoading = ref(false)
const statusLoading = ref(false)
const orders = ref<OrderDetail[]>([])
const selectedOrderSn = ref<string>()

const orderColumns = [
  { title: '订单号', key: 'orderSn' },
  { title: '车次', key: 'trainNumber' },
  { title: '区间', key: 'route' },
  { title: '状态', key: 'status' },
  { title: '下单时间', key: 'orderTime' },
  { title: '过期时间', key: 'expireTime' }
]

const orderItemColumns = [
  { title: '乘客', key: 'passengerName' },
  { title: '座席类型', key: 'seatType' },
  { title: '车厢号', key: 'carriageNumber' },
  { title: '座位号', key: 'seatNumber' },
  { title: '金额', key: 'amount' }
]

const selectedOrder = computed(() => orders.value.find(order => order.orderSn === selectedOrderSn.value) ?? null)

const selectedOrderStatus = computed<FrontendOrderStatus>(() => {
  if (!selectedOrder.value) {
    return 'PENDING'
  }
  return mapBackendStatus(selectedOrder.value.status)
})

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

function formatAmount(amount: number): string {
  return (amount / 100).toFixed(2)
}

function formatOrderTime(value?: string): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ')
}

function goToTicketPage() {
  router.push({ name: 'ticket' })
}

function mapBackendStatus(status: number): FrontendOrderStatus {
  switch (status) {
    case 10:
      return 'PAID'
    case 30:
      return 'CANCELED'
    default:
      return 'PENDING'
  }
}

function syncOrderStatus(orderSn: string, status: number) {
  orders.value = orders.value.map(order => order.orderSn === orderSn ? { ...order, status } : order)
  if (recentOrder.value?.orderSn === orderSn) {
    updateOrderStatus(mapBackendStatus(status))
  }
}

function pickSelectedOrder() {
  if (!orders.value.length) {
    selectedOrderSn.value = undefined
    return
  }
  const preferredOrderSn = recentOrder.value?.orderSn
  if (preferredOrderSn && orders.value.some(order => order.orderSn === preferredOrderSn)) {
    selectedOrderSn.value = preferredOrderSn
    return
  }
  if (selectedOrderSn.value && orders.value.some(order => order.orderSn === selectedOrderSn.value)) {
    return
  }
  selectedOrderSn.value = orders.value[0].orderSn
}

async function fetchOrders() {
  listLoading.value = true
  try {
    orders.value = await getOrderList(userId.value)
    pickSelectedOrder()
  } finally {
    listLoading.value = false
  }
}

async function refreshSelectedOrderStatus() {
  if (!selectedOrder.value) return
  statusLoading.value = true
  try {
    const res = await getOrderStatus(selectedOrder.value.orderSn)
    syncOrderStatus(selectedOrder.value.orderSn, res.status)
  } finally {
    statusLoading.value = false
  }
}

async function handlePay() {
  if (!selectedOrder.value) return
  payLoading.value = true
  try {
    await payOrderApi(selectedOrder.value.orderSn)
    syncOrderStatus(selectedOrder.value.orderSn, 10)
    message.success('支付成功')
  } finally {
    payLoading.value = false
  }
}

async function handleCancel() {
  if (!selectedOrder.value) return
  cancelLoading.value = true
  try {
    await cancelOrderApi(selectedOrder.value.orderSn)
    syncOrderStatus(selectedOrder.value.orderSn, 30)
    message.success('订单已取消')
  } finally {
    cancelLoading.value = false
  }
}

function customRow(record: OrderDetail) {
  return {
    onClick: () => {
      selectedOrderSn.value = record.orderSn
    }
  }
}

function rowClassName(record: OrderDetail) {
  return record.orderSn === selectedOrderSn.value ? 'order-row-selected' : ''
}

onMounted(() => {
  fetchOrders()
})
</script>

<style scoped>
:deep(.order-row-selected > td) {
  background: #e6f4ff !important;
}
</style>
