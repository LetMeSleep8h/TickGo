import http from './http'

export function createPayment(payload: { orderSn: string; userId: number; payAmount: number }) {
  return http.post('/pay/create', payload)
}

export function submitPayment(payload: { paymentSn: string }) {
  return http.post('/pay/submit', payload)
}

export function getPaymentStatus(params: { paymentSn?: string; orderSn?: string }) {
  return http.get('/pay/status', { params })
}

export function closePayment(paymentSn: string) {
  return http.post(`/pay/close?paymentSn=${paymentSn}`)
}
