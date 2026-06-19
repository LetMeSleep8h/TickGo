export interface StaticTrainOption {
  trainId: number
  trainNumber: string
  startStation: string
  endStation: string
}

export const TRAIN_OPTIONS: StaticTrainOption[] = [
  {
    trainId: 1,
    trainNumber: 'G1001',
    startStation: '北京南',
    endStation: '宁波'
  }
]

export const STATIONS = [
  '北京南',
  '济南西',
  '南京南',
  '杭州东',
  '宁波'
] as const

export type Station = (typeof STATIONS)[number]

export const SEAT_TYPE_OPTIONS = [
  { value: 1, label: '一等座' },
  { value: 2, label: '二等座' }
] as const

export type SeatType = (typeof SEAT_TYPE_OPTIONS)[number]['value']

export const DEFAULT_TRAIN_ID = 1
export const DEFAULT_DEPARTURE: Station = '南京南'
export const DEFAULT_ARRIVAL: Station = '杭州东'

export const RECENT_ORDER_KEY = 'tickgo_recent_order'
