<template>
  <a-space direction="vertical" size="large" style="width: 100%">
    <a-card title="联调测试台">
      <a-alert
        type="info"
        show-icon
        message="单页联调：初始化余票、查票、下单、创建支付单、模拟支付、查询支付状态。"
      />

      <a-row :gutter="[16, 16]" style="margin-top: 16px">
        <a-col :xs="24" :lg="8">
          <a-form layout="vertical">
            <a-form-item label="车次 ID">
              <a-select v-model:value="form.trainId" :options="trainOptions" @change="handleTrainChange" />
            </a-form-item>
            <a-form-item label="出发站">
              <a-select v-model:value="form.departure" :options="stationOptions" />
            </a-form-item>
            <a-form-item label="到达站">
              <a-select v-model:value="form.arrival" :options="stationOptions" />
            </a-form-item>
            <a-space wrap>
              <a-button type="primary" :loading="initLoading" @click="handleInitToken">初始化余票</a-button>
              <a-button :loading="queryLoading" @click="handleQueryTicket">查询余票</a-button>
              <a-button :loading="passengerLoading" @click="handleLoadPassengers">加载乘车人</a-button>
            </a-space>
          </a-form>
        </a-col>

        <a-col :xs="24" :lg="16">
          <a-descriptions bordered :column="2" size="small">
            <a-descriptions-item label="当前用户">{{ username }}</a-descriptions-item>
            <a-descriptions-item label="用户 ID">{{ userId }}</a-descriptions-item>
            <a-descriptions-item label="最近订单号">{{ recentOrder?.orderSn || '-' }}</a-descriptions-item>
            <a-descriptions-item label="支付单号">{{ payment?.paymentSn || '-' }}</a-descriptions-item>
          </a-descriptions>

          <a-table
            v-if="ticketResult"
            :dataSource="ticketResult.seatTypeRemains"
            :columns="ticketColumns"
            :pagination="false"
            rowKey="seatType"
            style="margin-top: 16px"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'seatType'">{{ getSeatTypeLabel(record.seatType) }}</template>
              <template v-else-if="column.key === 'remainCount'">{{ record.remainCount }}</template>
            </template>
          </a-table>
          <a-empty v-else description="还没有余票查询结果" style="margin-top: 16px" />
        </a-col>
      </a-row>
    </a-card>

    <a-card title="乘车人与下单">
      <a-table
        :dataSource="passengers"
        :columns="passengerColumns"
        :pagination="false"
        rowKey="id"
        :row-selection="{ selectedRowKeys: selectedPassengerIds, onChange: handleSelectionChange }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'realName'">{{ record.realName }}</template>
          <template v-else-if="column.key === 'idCard'">{{ record.idCard }}</template>
          <template v-else-if="column.key === 'seatType'">
            <a-select v-model:value="passengerSeatMap[record.id]" :options="seatTypeOptions" style="width: 120px" />
          </template>
        </template>
      </a-table>

      <a-space style="margin-top: 16px" wrap>
        <a-button type="primary" :loading="orderLoading" @click="handleCreateOrder">创建订单</a-button>
        <a-button @click="handleSyncOrderToPay" :disabled="!recentOrder">同步订单到支付区</a-button>
      </a-space>

      <a-descriptions v-if="recentOrder" bordered :column="2" style="margin-top: 16px">
        <a-descriptions-item label="订单号">{{ recentOrder.orderSn }}</a-descriptions-item>
        <a-descriptions-item label="订单状态">
          <a-tag :color="orderStatusColorMap[recentOrder.frontendOrderStatus]">
            {{ orderStatusLabelMap[recentOrder.frontendOrderStatus] }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="车次">{{ recentOrder.trainNumber }}</a-descriptions-item>
        <a-descriptions-item label="区间">{{ recentOrder.departure }} -> {{ recentOrder.arrival }}</a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-card title="支付联调">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :lg="10">
          <a-form layout="vertical">
            <a-form-item label="订单号">
              <a-input v-model:value="paymentForm.orderSn" placeholder="订单号" />
            </a-form-item>
            <a-form-item label="用户 ID">
              <a-input-number v-model:value="paymentForm.userId" :min="1" style="width: 100%" />
            </a-form-item>
            <a-form-item label="支付金额（分）">
              <a-input-number v-model:value="paymentForm.payAmount" :min="1" style="width: 100%" />
            </a-form-item>
            <a-space wrap>
              <a-button type="primary" :loading="paymentCreateLoading" @click="handleCreatePayment">创建支付单</a-button>
              <a-button type="primary" :disabled="!paymentForm.paymentSn" :loading="paymentSubmitLoading" @click="handleSubmitPayment">
                模拟支付成功
              </a-button>
              <a-button :disabled="!paymentForm.orderSn && !paymentForm.paymentSn" @click="handleQueryPayment">查询支付状态</a-button>
            </a-space>
          </a-form>
        </a-col>

        <a-col :xs="24" :lg="14">
          <a-descriptions bordered :column="1" size="small">
            <a-descriptions-item label="支付单号">{{ payment?.paymentSn || paymentForm.paymentSn || '-' }}</a-descriptions-item>
            <a-descriptions-item label="订单号">{{ payment?.orderSn || paymentForm.orderSn || '-' }}</a-descriptions-item>
            <a-descriptions-item label="支付状态">{{ payment ? getPaymentStatusLabel(payment.status) : '-' }}</a-descriptions-item>
            <a-descriptions-item label="回调状态">{{ payment ? getCallbackStatusLabel(payment.callbackStatus) : '-' }}</a-descriptions-item>
            <a-descriptions-item label="支付金额">{{ payment?.payAmount ?? paymentForm.payAmount }}</a-descriptions-item>
            <a-descriptions-item label="成功时间">{{ payment?.successTime || '-' }}</a-descriptions-item>
          </a-descriptions>
        </a-col>
      </a-row>
    </a-card>
  </a-space>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { createOrder } from '../api/order'
import { createPayment, getPaymentStatus, submitPayment } from '../api/pay'
import { initToken, preOccupy, queryTicket } from '../api/ticket'
import { getPassengers, validatePassengers } from '../api/user'
import { DEFAULT_ARRIVAL, DEFAULT_DEPARTURE, DEFAULT_TRAIN_ID, SEAT_TYPE_OPTIONS, STATIONS, TRAIN_OPTIONS } from '../constants/stations'
import { useCurrentUser } from '../composables/useCurrentUser'
import { useRecentOrder } from '../composables/useRecentOrder'
import type { FrontendOrderStatus, Passenger, PaymentStatusResponse, RecentOrder, TicketQueryResponse } from '../types/api'

const { userId, username } = useCurrentUser()
const { recentOrder, setRecentOrder, updateOrderStatus } = useRecentOrder()

const trainOptions = TRAIN_OPTIONS.map(train => ({
  value: train.trainId,
  label: `${train.trainNumber} (${train.startStation} -> ${train.endStation})`
}))
const stationOptions = STATIONS.map(station => ({ label: station, value: station }))
const seatTypeOptions = SEAT_TYPE_OPTIONS.map(seatType => ({ label: seatType.label, value: seatType.value }))

const form = reactive({
  trainId: DEFAULT_TRAIN_ID,
  departure: DEFAULT_DEPARTURE,
  arrival: DEFAULT_ARRIVAL
})

const paymentForm = reactive({
  orderSn: '',
  paymentSn: '',
  userId: userId.value,
  payAmount: 100
})

const ticketResult = ref<TicketQueryResponse | null>(null)
const passengers = ref<Passenger[]>([])
const selectedPassengerIds = ref<number[]>([])
const passengerSeatMap = reactive<Record<number, number>>({})
const payment = ref<PaymentStatusResponse | null>(null)

const initLoading = ref(false)
const queryLoading = ref(false)
const passengerLoading = ref(false)
const orderLoading = ref(false)
const paymentCreateLoading = ref(false)
const paymentSubmitLoading = ref(false)

const ticketColumns = [
  { title: '座席类型', key: 'seatType' },
  { title: '余票数量', key: 'remainCount' }
]

const passengerColumns = [
  { title: '乘客 ID', dataIndex: 'id', key: 'id' },
  { title: '姓名', dataIndex: 'realName', key: 'realName' },
  { title: '身份证', dataIndex: 'idCard', key: 'idCard' },
  { title: '座席', key: 'seatType' }
]

const orderStatusLabelMap: Record<FrontendOrderStatus, string> = {
  PENDING: '待支付',
  PAID: '已支付',
  CANCELED: '已取消'
}

const orderStatusColorMap: Record<FrontendOrderStatus, string> = {
  PENDING: 'orange',
  PAID: 'green',
  CANCELED: 'red'
}

function getSeatTypeLabel(seatType: number): string {
  return SEAT_TYPE_OPTIONS.find(item => item.value === seatType)?.label ?? `未知(${seatType})`
}

function getPaymentStatusLabel(status: number): string {
  const map: Record<number, string> = {
    0: 'INIT',
    10: 'PAYING',
    20: 'SUCCESS',
    30: 'FAILED',
    40: 'CLOSED'
  }
  return map[status] ?? String(status)
}

function getCallbackStatusLabel(status: number): string {
  const map: Record<number, string> = {
    0: 'PENDING',
    10: 'SUCCESS',
    20: 'FAILED'
  }
  return map[status] ?? String(status)
}

function handleSelectionChange(keys: number[]) {
  selectedPassengerIds.value = keys
}

function handleTrainChange(trainId: number) {
  form.trainId = trainId
}

async function handleInitToken() {
  initLoading.value = true
  try {
    await initToken({ ...form })
    message.success('余票初始化完成')
  } finally {
    initLoading.value = false
  }
}

async function handleQueryTicket() {
  queryLoading.value = true
  try {
    ticketResult.value = await queryTicket({ ...form })
  } finally {
    queryLoading.value = false
  }
}

async function handleLoadPassengers() {
  passengerLoading.value = true
  try {
    passengers.value = await getPassengers(userId.value)
    passengers.value.forEach(passenger => {
      if (!(passenger.id in passengerSeatMap)) {
        passengerSeatMap[passenger.id] = 1
      }
    })
  } finally {
    passengerLoading.value = false
  }
}

async function handleCreateOrder() {
  if (selectedPassengerIds.value.length === 0) {
    message.warning('先选乘车人')
    return
  }

  const passengersWithoutSeat = selectedPassengerIds.value.some(id => !passengerSeatMap[id])
  if (passengersWithoutSeat) {
    message.warning('请先给所有乘车人选择座席')
    return
  }

  orderLoading.value = true
  try {
    await validatePassengers({
      userId: userId.value,
      passengerIds: selectedPassengerIds.value
    })

    const orderSn = `ORDER_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const preOccupyResp = await preOccupy({
      trainId: form.trainId,
      departure: form.departure,
      arrival: form.arrival,
      orderSn,
      passengers: selectedPassengerIds.value.map(passengerId => ({
        passengerId,
        seatType: passengerSeatMap[passengerId]
      }))
    })

    await createOrder({
      orderSn,
      userId: userId.value,
      username: username.value,
      trainId: form.trainId,
      trainNumber: preOccupyResp.trainNumber,
      departure: form.departure,
      arrival: form.arrival,
      items: preOccupyResp.items
    })

    const nextOrder: RecentOrder = {
      orderSn,
      userId: userId.value,
      username: username.value,
      trainId: form.trainId,
      trainNumber: preOccupyResp.trainNumber,
      departure: form.departure,
      arrival: form.arrival,
      items: preOccupyResp.items,
      frontendOrderStatus: 'PENDING'
    }

    setRecentOrder(nextOrder)
    handleSyncOrderToPay()
    message.success('订单创建成功')
  } finally {
    orderLoading.value = false
  }
}

function handleSyncOrderToPay() {
  if (!recentOrder.value) {
    return
  }
  paymentForm.orderSn = recentOrder.value.orderSn
  paymentForm.userId = recentOrder.value.userId
  paymentForm.payAmount = recentOrder.value.items.reduce((sum, item) => sum + item.amount, 0)
}

async function handleCreatePayment() {
  paymentCreateLoading.value = true
  try {
    payment.value = await createPayment({
      orderSn: paymentForm.orderSn,
      userId: paymentForm.userId,
      payAmount: paymentForm.payAmount
    })
    paymentForm.paymentSn = payment.value.paymentSn
    message.success('支付单创建成功')
  } finally {
    paymentCreateLoading.value = false
  }
}

async function handleSubmitPayment() {
  paymentSubmitLoading.value = true
  try {
    payment.value = await submitPayment({ paymentSn: paymentForm.paymentSn })
    syncFrontendOrderStatusFromPayment()
    message.success('模拟支付成功已提交')
  } finally {
    paymentSubmitLoading.value = false
  }
}

async function handleQueryPayment() {
  payment.value = await getPaymentStatus({
    paymentSn: paymentForm.paymentSn || undefined,
    orderSn: paymentForm.orderSn || undefined
  })
  syncFrontendOrderStatusFromPayment()
}

function syncFrontendOrderStatusFromPayment() {
  if (!payment.value) {
    return
  }
  if (payment.value.callbackStatus === 10) {
    updateOrderStatus('PAID')
  }
}
</script>
