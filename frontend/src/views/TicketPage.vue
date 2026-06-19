<template>
  <a-card title="调试区">
    <a-form layout="inline">
      <a-form-item label="车次ID">
        <a-select v-model:value="initForm.trainId" :options="trainOptions" style="width: 260px" @change="handleTrainChange" />
      </a-form-item>
      <a-form-item label="出发站">
        <a-select v-model:value="initForm.departure" :options="stationOptions" style="width: 120px" />
      </a-form-item>
      <a-form-item label="到达站">
        <a-select v-model:value="initForm.arrival" :options="stationOptions" style="width: 120px" />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" :loading="initLoading" @click="handleInitToken">初始化余票</a-button>
      </a-form-item>
    </a-form>
  </a-card>

  <a-card title="查票区" style="margin-top: 16px">
    <a-form layout="inline">
      <a-form-item label="车次ID">
        <a-select v-model:value="queryForm.trainId" :options="trainOptions" style="width: 260px" @change="handleTrainChange" />
      </a-form-item>
      <a-form-item label="出发站">
        <a-select v-model:value="queryForm.departure" :options="stationOptions" style="width: 120px" />
      </a-form-item>
      <a-form-item label="到达站">
        <a-select v-model:value="queryForm.arrival" :options="stationOptions" style="width: 120px" />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" :loading="queryLoading" @click="handleQueryTicket">查询余票</a-button>
      </a-form-item>
    </a-form>

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
    <a-empty v-else description="请先查询余票" style="margin-top: 16px" />
  </a-card>

  <a-card title="乘车人选择区" style="margin-top: 16px">
    <a-spin :spinning="passengersLoading">
      <a-table
        v-if="passengers.length > 0"
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
            <a-select
              v-model:value="passengerSeatMap[record.id]"
              :options="seatTypeOptions"
              style="width: 100px"
              placeholder="选择座席"
            />
          </template>
        </template>
      </a-table>
      <a-empty v-else description="暂无乘车人，请先在用户页添加" />
    </a-spin>
  </a-card>

  <a-card title="下单区" style="margin-top: 16px">
    <a-form layout="vertical">
      <a-form-item>
        <a-button
          type="primary"
          size="large"
          :loading="orderLoading"
          :disabled="selectedPassengerIds.length === 0"
          @click="handleCreateOrder"
        >
          校验并创建订单
        </a-button>
      </a-form-item>
    </a-form>

    <a-descriptions v-if="orderResult" bordered :column="2" style="margin-top: 16px">
      <a-descriptions-item label="订单号">{{ orderResult.orderSn }}</a-descriptions-item>
      <a-descriptions-item label="车次">{{ orderResult.trainNumber }}</a-descriptions-item>
      <a-descriptions-item label="出发站">{{ orderResult.departure }}</a-descriptions-item>
      <a-descriptions-item label="到达站">{{ orderResult.arrival }}</a-descriptions-item>
    </a-descriptions>

    <a-table
      v-if="orderResult && orderResult.items.length > 0"
      :dataSource="orderResult.items"
      :columns="orderItemColumns"
      :pagination="false"
      rowKey="passengerId"
      style="margin-top: 16px"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'passengerId'">{{ record.passengerId }}</template>
        <template v-else-if="column.key === 'seatType'">{{ getSeatTypeLabel(record.seatType) }}</template>
        <template v-else-if="column.key === 'carriageNumber'">{{ record.carriageNumber }}</template>
        <template v-else-if="column.key === 'seatNumber'">{{ record.seatNumber }}</template>
        <template v-else-if="column.key === 'amount'">￥{{ record.amount }}</template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { initToken, preOccupy, queryTicket } from '../api/ticket'
import { getPassengers, validatePassengers } from '../api/user'
import { DEFAULT_ARRIVAL, DEFAULT_DEPARTURE, DEFAULT_TRAIN_ID, SEAT_TYPE_OPTIONS, STATIONS, TRAIN_OPTIONS } from '../constants/stations'
import { useCurrentUser } from '../composables/useCurrentUser'
import { useRecentOrder } from '../composables/useRecentOrder'
import type { Passenger, RecentOrder, TicketQueryResponse } from '../types/api'

const router = useRouter()
const { userId, username } = useCurrentUser()
const { setRecentOrder } = useRecentOrder()

const trainOptions = TRAIN_OPTIONS.map(train => ({
  value: train.trainId,
  label: `${train.trainNumber} (${train.startStation} -> ${train.endStation})`
}))
const stationOptions = STATIONS.map(station => ({ value: station, label: station }))
const seatTypeOptions = SEAT_TYPE_OPTIONS.map(s => ({ value: s.value, label: s.label }))

const initForm = reactive({
  trainId: DEFAULT_TRAIN_ID,
  departure: DEFAULT_DEPARTURE,
  arrival: DEFAULT_ARRIVAL
})

const queryForm = reactive({
  trainId: DEFAULT_TRAIN_ID,
  departure: DEFAULT_DEPARTURE,
  arrival: DEFAULT_ARRIVAL
})

const ticketResult = ref<TicketQueryResponse | null>(null)
const passengers = ref<Passenger[]>([])
const passengersLoading = ref(false)
const selectedPassengerIds = ref<number[]>([])
const passengerSeatMap = reactive<Record<number, number>>({})
const orderResult = ref<RecentOrder | null>(null)

const initLoading = ref(false)
const queryLoading = ref(false)
const orderLoading = ref(false)

const ticketColumns = [
  { title: '座席类型', key: 'seatType' },
  { title: '余票数量', key: 'remainCount' }
]

const passengerColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '姓名', dataIndex: 'realName', key: 'realName' },
  { title: '身份证', dataIndex: 'idCard', key: 'idCard' },
  { title: '座席选择', key: 'seatType' }
]

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

function handleSelectionChange(keys: number[]) {
  selectedPassengerIds.value = keys
}

function handleTrainChange(trainId: number) {
  initForm.trainId = trainId
  queryForm.trainId = trainId
}

async function handleInitToken() {
  initLoading.value = true
  try {
    await initToken({
      trainId: initForm.trainId,
      departure: initForm.departure,
      arrival: initForm.arrival
    })
    message.success('余票初始化成功')
  } catch {
  } finally {
    initLoading.value = false
  }
}

async function handleQueryTicket() {
  queryLoading.value = true
  try {
    ticketResult.value = await queryTicket({
      trainId: queryForm.trainId,
      departure: queryForm.departure,
      arrival: queryForm.arrival
    })
  } catch {
    ticketResult.value = null
  } finally {
    queryLoading.value = false
  }
}

async function fetchPassengers() {
  passengersLoading.value = true
  try {
    passengers.value = await getPassengers(userId.value)
    passengers.value.forEach(p => {
      if (!(p.id in passengerSeatMap)) {
        passengerSeatMap[p.id] = 1
      }
    })
  } catch {
    passengers.value = []
  } finally {
    passengersLoading.value = false
  }
}

async function handleCreateOrder() {
  if (selectedPassengerIds.value.length === 0) {
    message.warning('请选择至少一个乘车人')
    return
  }

  if (selectedPassengerIds.value.some(id => !passengerSeatMap[id])) {
    message.warning('请为所有选中乘车人选择座席类型')
    return
  }

  if (queryForm.departure === queryForm.arrival) {
    message.warning('出发站和到达站不能相同')
    return
  }

  orderLoading.value = true
  try {
    await validatePassengers({
      userId: userId.value,
      passengerIds: selectedPassengerIds.value
    })

    const orderSn = `ORDER_${Date.now()}_${Math.random().toString(36).substring(2, 9)}`
    const preOccupyRes = await preOccupy({
      userId: userId.value,
      trainId: queryForm.trainId,
      departure: queryForm.departure,
      arrival: queryForm.arrival,
      orderSn,
      passengers: selectedPassengerIds.value.map(id => ({
        passengerId: id,
        seatType: passengerSeatMap[id]
      }))
    })

    const recentOrder: RecentOrder = {
      orderSn,
      trainId: queryForm.trainId,
      trainNumber: preOccupyRes.trainNumber,
      departure: queryForm.departure,
      arrival: queryForm.arrival,
      userId: userId.value,
      username: username.value,
      items: preOccupyRes.items,
      frontendOrderStatus: 'PENDING'
    }

    setRecentOrder(recentOrder)
    orderResult.value = recentOrder
    message.success('订单创建成功')
    setTimeout(() => {
      router.push({ name: 'order' })
    }, 1000)
  } finally {
    orderLoading.value = false
  }
}

onMounted(() => {
  fetchPassengers()
})
</script>
