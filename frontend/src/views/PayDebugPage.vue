<template>
  <a-card title="支付联调页">
    <a-space direction="vertical" style="width: 100%">
      <a-alert
        type="info"
        show-icon
        message="建议先在购票页完成下单，这里会优先读取最近订单号"
      />

      <a-input v-model:value="orderSn" placeholder="订单号" />
      <a-input v-model:value="paymentSn" placeholder="支付单号" />
      <a-input-number v-model:value="userId" :min="1" style="width: 220px" />
      <a-input-number v-model:value="payAmount" :min="1" style="width: 220px" />

      <a-space>
        <a-button type="primary" @click="handleCreatePayment">创建支付单</a-button>
        <a-button type="primary" :disabled="!paymentSn" @click="handleSubmitPayment">模拟支付成功</a-button>
        <a-button @click="handleQueryPayment">查询支付状态</a-button>
      </a-space>

      <a-descriptions v-if="payment" bordered :column="1">
        <a-descriptions-item label="支付单号">{{ payment.paymentSn }}</a-descriptions-item>
        <a-descriptions-item label="订单号">{{ payment.orderSn }}</a-descriptions-item>
        <a-descriptions-item label="支付状态">{{ payment.status }}</a-descriptions-item>
        <a-descriptions-item label="回调状态">{{ payment.callbackStatus }}</a-descriptions-item>
        <a-descriptions-item label="支付金额">{{ payment.payAmount }}</a-descriptions-item>
        <a-descriptions-item label="成功时间">{{ payment.successTime || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-space>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { createPayment, getPaymentStatus, submitPayment } from '../api/pay'
import { useCurrentUser } from '../composables/useCurrentUser'
import { useRecentOrder } from '../composables/useRecentOrder'
import type { PaymentStatusResponse } from '../types/api'

const { userId: currentUserId } = useCurrentUser()
const { recentOrder } = useRecentOrder()

const orderSn = ref('')
const paymentSn = ref('')
const userId = ref(currentUserId.value)
const payAmount = ref(100)
const payment = ref<PaymentStatusResponse | null>(null)

onMounted(() => {
  if (recentOrder.value) {
    orderSn.value = recentOrder.value.orderSn
    payAmount.value = recentOrder.value.items.reduce((sum, item) => sum + item.amount, 0)
  }
})

async function handleCreatePayment() {
  const res = await createPayment({
    orderSn: orderSn.value,
    userId: userId.value,
    payAmount: payAmount.value
  })
  payment.value = res
  paymentSn.value = res.paymentSn
  message.success('支付单创建成功')
}

async function handleSubmitPayment() {
  const res = await submitPayment({ paymentSn: paymentSn.value })
  payment.value = res
  message.success('支付成功已提交')
}

async function handleQueryPayment() {
  const res = await getPaymentStatus({
    paymentSn: paymentSn.value || undefined,
    orderSn: orderSn.value || undefined
  })
  payment.value = res
}
</script>
