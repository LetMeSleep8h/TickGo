import { ref, watch } from 'vue'
import type { RecentOrder, FrontendOrderStatus } from '../types/api'
import { RECENT_ORDER_KEY } from '../constants/stations'

// Load from localStorage
function loadFromStorage(): RecentOrder | null {
  try {
    const stored = localStorage.getItem(RECENT_ORDER_KEY)
    if (stored) {
      return JSON.parse(stored)
    }
  } catch {
    // ignore parse errors
  }
  return null
}

// Save to localStorage
function saveToStorage(order: RecentOrder | null) {
  try {
    if (order) {
      localStorage.setItem(RECENT_ORDER_KEY, JSON.stringify(order))
    } else {
      localStorage.removeItem(RECENT_ORDER_KEY)
    }
  } catch {
    // ignore storage errors
  }
}

export function useRecentOrder() {
  const recentOrder = ref<RecentOrder | null>(loadFromStorage())

  // Watch for changes and persist
  watch(recentOrder, (newVal) => {
    saveToStorage(newVal)
  }, { deep: true })

  function setRecentOrder(order: RecentOrder) {
    recentOrder.value = order
  }

  function updateOrderStatus(status: FrontendOrderStatus) {
    if (recentOrder.value) {
      recentOrder.value = {
        ...recentOrder.value,
        frontendOrderStatus: status
      }
    }
  }

  function clearRecentOrder() {
    recentOrder.value = null
  }

  return {
    recentOrder,
    setRecentOrder,
    updateOrderStatus,
    clearRecentOrder
  }
}
