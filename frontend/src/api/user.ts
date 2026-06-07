import http from './http'
import type { UserInfo, Passenger, ValidatePassengersRequest } from '../types/api'

// Get user info by id
export function getUserById(id: number) {
  return http.get<UserInfo>(`/user/${id}`)
}

// Get passengers for a user
export function getPassengers(userId: number) {
  return http.get<Passenger[]>(`/user/${userId}/passengers`)
}

// Validate passengers
export function validatePassengers(data: ValidatePassengersRequest) {
  return http.post<boolean>('/user/validate-passengers', data)
}
