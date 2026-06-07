import http from './http'
import type { OrderCreateRequest } from '../types/api'

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
