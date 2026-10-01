import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, me as meApi } from '../api/auth'
import { STORAGE_KEYS } from '../config'
import { ensureAccessToken, onExpired } from '../utils/request'
import { clearTokens, getRefreshToken, saveTokens } from '../utils/token'

/** 未登录时的落地页，集中一处避免各页面硬编码路径 */
const LOGIN_PAGE = '/pages/login/index'

/** 防止并发 401 时连续触发多次跳转 */
let redirecting = false

function toLogin() {
  if (redirecting) {
    return
  }
  redirecting = true
  uni.reLaunch({
    url: LOGIN_PAGE,
    complete: () => {
      setTimeout(() => {
        redirecting = false
      }, 800)
    }
  })
}

/** 冷启动恢复过程的共享 Promise，保证只跑一次 */
let restoring = null

function safeGet(key) {
  try {
    return uni.getStorageSync(key)
  } catch (e) {
    return null
  }
}

function safeSet(key, value) {
  try {
    uni.setStorageSync(key, value)
  } catch (e) {
    /* 存储失败不影响本次会话 */
  }
}

function safeRemove(key) {
  try {
    uni.removeStorageSync(key)
  } catch (e) {
    /* 忽略 */
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    user: null,
    /** 是否已完成冷启动恢复，避免重复跑 */
    ready: false
  }),

  getters: {
    isLogin: (s) => !!s.user,
    role: (s) => (s.user && s.user.role) || '',
    /** 店长与管理员能看全部数据，店员只看自己该看的 */
    isManager: (s) => ['ADMIN', 'MANAGER'].includes((s.user && s.user.role) || ''),
    displayName: (s) => (s.user && (s.user.realName || s.user.username)) || '未登录'
  },

  actions: {
    /** 上次登录用的账号，方便登录页回填 */
    lastUsername() {
      return safeGet(STORAGE_KEYS.lastUsername) || ''
    },

    async login(username, password) {
      const data = await loginApi(username, password)
      // 小程序端刷新令牌随响应体返回，这里一并落地
      saveTokens(data)
      this.user = data.user
      safeSet(STORAGE_KEYS.user, data.user)
      safeSet(STORAGE_KEYS.lastUsername, username)
      this.ready = true
      return data
    },

    async logout() {
      const refreshToken = getRefreshToken()
      try {
        if (refreshToken) {
          await logoutApi(refreshToken)
        }
      } catch (e) {
        // 后端吊销失败不应妨碍本地登出 —— 用户的目的就是离开
      }
      this.reset()
      toLogin()
    },

    /**
     * 冷启动恢复登录态
     *
     * 访问令牌只存内存，重启后必然为空，所以这里用刷新令牌换一张新的，
     * 顺便拉一次用户信息保证角色是最新的（管理员改了权限也能马上生效）。
     * 任何一步失败都当作未登录处理，不弹错误 —— 用户还没开始操作，不该被打扰。
     */
    async restore() {
      if (this.ready) {
        return this.isLogin
      }
      if (restoring) {
        return restoring
      }

      restoring = (async () => {
        try {
          const ok = await ensureAccessToken()
          if (!ok) {
            return false
          }
          this.user = await meApi()
          safeSet(STORAGE_KEYS.user, this.user)
          return true
        } catch (e) {
          this.reset()
          return false
        } finally {
          this.ready = true
          restoring = null
        }
      })()

      return restoring
    },

    reset() {
      clearTokens()
      this.user = null
      safeRemove(STORAGE_KEYS.user)
    }
  }
})

/**
 * 令牌彻底失效（刷新也被拒）时，由请求层回调过来。
 * 注册在模块顶层而不是组件里，保证任何页面发起的请求都能触发登出。
 */
onExpired(() => {
  const store = useUserStore()
  store.reset()
  toLogin()
})
