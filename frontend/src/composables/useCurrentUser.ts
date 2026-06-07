import { ref } from 'vue'

const CURRENT_USER = {
  userId: 1,
  username: 'admin'
}

export function useCurrentUser() {
  const userId = ref(CURRENT_USER.userId)
  const username = ref(CURRENT_USER.username)

  return {
    userId,
    username
  }
}
