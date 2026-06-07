// Station list
export const STATIONS = [
  '北京南',
  '天津南',
  '济南西',
  '南京南',
  '杭州东',
  '宁波',
  '温州南',
  '福州南',
  '厦门北',
  '深圳北'
] as const

export type Station = (typeof STATIONS)[number]

// Seat type mapping
export const SEAT_TYPE_OPTIONS = [
  { value: 1, label: '一等座' },
  { value: 2, label: '二等座' }
] as const

export type SeatType = (typeof SEAT_TYPE_OPTIONS)[number]['value']

// Default search values
export const DEFAULT_TRAIN_ID = 1
export const DEFAULT_DEPARTURE: Station = '南京南'
export const DEFAULT_ARRIVAL: Station = '杭州东'

// LocalStorage key
export const RECENT_ORDER_KEY = 'tickgo_recent_order'
