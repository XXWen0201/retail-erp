import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import {
  setAccessToken,
  setTokenRefreshedHandler
} from '@/api/request'

/**
 * 用户会话
 *
 * 令牌只存在这里（内存）+ 后端下发的 httpOnly Cookie，页面刷新后 accessToken 会丢，
 * 靠 restore() 用 Cookie 里的刷新令牌换一张新的回来。
 * 刻意不落 localStorage：XSS 拿不到 localStorage 里的东西就换不走长期凭据。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const user = ref(null)
  const restoring = ref(false)
  let restoredOnce = false

  const isLogin = computed(() => !!token.value)
  const role = computed(() => (user.value && user.value.role) || '')
  const isAdmin = computed(() => role.value === 'ADMIN')
  const displayName = computed(
    () => (user.value && (user.value.realName || user.value.username)) || '未登录'
  )

  function applyToken(t) {
    token.value = t || ''
    setAccessToken(token.value)
  }

  function applyUser(u) {
    if (u) user.value = u
  }

  function clear() {
    applyToken('')
    user.value = null
  }

  async function login(form) {
    const data = await authApi.login(form)
    applyToken(data.accessToken)
    applyUser(data.user)
    return data
  }

  /**
   * 用刷新令牌恢复会话。
   * @param {boolean} force 强制再试一次（比如手动点"重新登录"）
   */
  async function restore(force = false) {
    if (token.value) return true
    if (restoring.value) return false
    if (restoredOnce && !force) return false
    restoring.value = true
    restoredOnce = true
    try {
      const data = await authApi.refresh()
      applyToken(data.accessToken)
      applyUser(data.user)
      return true
    } catch {
      clear()
      return false
    } finally {
      restoring.value = false
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 后端吊销失败也要让前端登出，否则用户卡在"退不出去"的状态里
    } finally {
      clear()
    }
  }

  /** 重新拉一次当前用户信息（权限变更后刷新用） */
  async function reloadMe() {
    const me = await authApi.getMe()
    applyUser(me)
    return me
  }

  // 拦截器刷新令牌成功后，把新令牌与新用户信息同步进来
  setTokenRefreshedHandler((data) => {
    if (data && data.accessToken) applyToken(data.accessToken)
    if (data && data.user) applyUser(data.user)
  })

  return {
    token,
    user,
    restoring,
    isLogin,
    role,
    isAdmin,
    displayName,
    login,
    restore,
    logout,
    reloadMe,
    clear,
    applyToken,
    applyUser
  }
})
