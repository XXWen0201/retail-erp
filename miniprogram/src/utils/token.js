import { STORAGE_KEYS } from '../config'

/**
 * 令牌存取
 *
 * 访问令牌只放内存，不做持久化：
 * 它有效期只有 30 分钟，持久化收益很小，但一旦本地缓存被翻出来就是可直接用的凭据。
 * 冷启动时用刷新令牌换一个新的即可，用户完全无感。
 *
 * 刷新令牌必须持久化，否则小程序一冷启动就得重新输账号密码。
 * 它落库可吊销，且每次刷新都会轮转，安全性比长期存访问令牌高得多。
 */

let memAccessToken = ''

export function getAccessToken() {
  return memAccessToken
}

export function getRefreshToken() {
  try {
    return uni.getStorageSync(STORAGE_KEYS.refreshToken) || ''
  } catch (e) {
    return ''
  }
}

export function hasRefreshToken() {
  return !!getRefreshToken()
}

/**
 * 保存令牌
 *
 * 兼容两种调用：传整个登录响应，或只更新其中一个。
 */
export function saveTokens(payload = {}) {
  if (payload.accessToken !== undefined) {
    memAccessToken = payload.accessToken || ''
  }
  if (payload.refreshToken) {
    try {
      uni.setStorageSync(STORAGE_KEYS.refreshToken, payload.refreshToken)
    } catch (e) {
      // 存储写失败不影响本次会话，只是下次冷启动需要重新登录
    }
  }
}

export function clearTokens() {
  memAccessToken = ''
  try {
    uni.removeStorageSync(STORAGE_KEYS.refreshToken)
  } catch (e) {
    /* 忽略 */
  }
}
