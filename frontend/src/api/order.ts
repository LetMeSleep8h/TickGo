import http from './http'
import type { OrderCreateRequest, OrderDetail, OrderStatusResponse } from '../types/api'

// Create order
export function createOrder(data: OrderCreateRequest) {
  return http.post('/order/create', data)
}

// Pay order
export function payOrder(orderSn: string) {
  return http.post(`/order/pay?orderSn=${orderSn}`)
}

// Cancel order
export function cancelOrder(orderSn: string) {
  return http.post(`/order/cancel?orderSn=${orderSn}`)
}

export function getOrderStatus(orderSn: string) {
  return http.get<OrderStatusResponse>(`/order/status?orderSn=${orderSn}`)
}

export function getOrderList(userId: number) {
  return http.get<OrderDetail[]>(`/order/list?userId=${userId}`)
}
