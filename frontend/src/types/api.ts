// API response wrapper
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// User types
export interface UserInfo {
  id: number
  username: string
  realName: string
}

export interface Passenger {
  id: number
  realName: string
  idCard: string
}

// Ticket types
export interface SeatTypeRemain {
  seatType: number
  remainCount: number
}

export interface TicketQueryResponse {
  trainId: number
  departure: string
  arrival: string
  seatTypeRemains: SeatTypeRemain[]
}

export interface PreOccupyRequest {
  trainId: number
  departure: string
  arrival: string
  orderSn: string
  passengers: {
    passengerId: number
    seatType: number
  }[]
}

export interface PreOccupyItem {
  passengerId: number
  seatType: number
  carriageNumber: string
  seatNumber: string
  amount: number
}

export interface PreOccupyResponse {
  trainNumber: string
  items: PreOccupyItem[]
}

// Order types
export interface OrderItem {
  passengerId: number
  seatType: number
  carriageNumber: string
  seatNumber: string
  amount: number
}

export interface OrderCreateRequest {
  orderSn: string
  userId: number
  username: string
  trainId: number
  trainNumber: string
  departure: string
  arrival: string
  items: OrderItem[]
}

export type FrontendOrderStatus = 'PENDING' | 'PAID' | 'CANCELED'

export interface RecentOrder {
  orderSn: string
  trainId: number
  trainNumber: string
  departure: string
  arrival: string
  userId: number
  username: string
  items: OrderItem[]
  frontendOrderStatus: FrontendOrderStatus
}

// Validate passengers request
export interface ValidatePassengersRequest {
  userId: number
  passengerIds: number[]
}
