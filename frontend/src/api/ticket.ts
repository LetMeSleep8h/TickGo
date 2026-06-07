import http from './http'
import type { TicketQueryResponse, PreOccupyRequest, PreOccupyResponse } from '../types/api'

// Initialize token for a train
export function initToken(params: { trainId: number; departure: string; arrival: string }) {
  return http.post('/ticket/initToken', null, { params })
}

// Query available tickets
export function queryTicket(params: { trainId: number; departure: string; arrival: string }) {
  return http.get<TicketQueryResponse>('/ticket/query', { params })
}

// Pre-occupy seats
export function preOccupy(data: PreOccupyRequest) {
  return http.post<PreOccupyResponse>('/ticket/preOccupy', data)
}
