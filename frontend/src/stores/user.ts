import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo } from '@/api/modules'
import { TOKEN_KEY, USER_KEY } from '@/api'

function loadUser(): UserInfo | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserInfo) : null
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<UserInfo | null>(loadUser())

  function setLogin(t: string, u: UserInfo) {
    token.value = t
    user.value = u
    localStorage.setItem(TOKEN_KEY, t)
    localStorage.setItem(USER_KEY, JSON.stringify(u))
  }

  function clear() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  /** 是否拥有某权限码 */
  function hasPerm(code: string): boolean {
    return !!user.value?.perms?.includes(code)
  }

  return { token, user, setLogin, clear, hasPerm }
})
