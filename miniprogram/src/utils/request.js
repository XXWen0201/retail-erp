import { API_BASE, REQUEST_TIMEOUT } from '../config'
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  saveTokens
} from './token'

/**
 * 请求封装
 *
 * 三件事必须在这里收口，否则每个页面都要重复写一遍：
 *   1. 统一带令牌、统一解析响应体（后端成功失败都是 HTTP 200 + body.code）
 *   2. 401 自动续期并重放原请求
 *   3. 失败时把后端的 message 原样透出，页面不用自己拼文案
 */

/** 业务异常：带错误码，页面可以按 code 做差异化处理（比如 4001 库存不足） */
export class ApiError extends Error {
  constructor(code, message) {
    super(message || '请求失败')
    this.name = 'ApiError'
    this.code = code
  }
}

/** 刷新中的 Promise，用来做「单例刷新」：并发请求同时 401 时只真正刷新一次 */
let refreshing = null

let expireHandler = null

/** 注册登录过期回调（由 store 注入，避免 request 反过来依赖 store 造成循环引用） */
export function onExpired(handler) {
  expireHandler = handler
}

function toast(message) {
  uni.showToast({
    title: message,
    icon: 'none',
    duration: 2200
  })
}

/** 最底层请求：只负责发出去、拿回来 */
function rawRequest(method, url, data, extraHeader) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: API_BASE + url,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        ...extraHeader
      },
      timeout: REQUEST_TIMEOUT,
      success: resolve,
      fail: reject
    })
  })
}

/**
 * 刷新访问令牌
 *
 * 小程序端把刷新令牌放在请求体里传给 /auth/refresh。
 * 这里刻意用 retry:false —— 否则刷新接口自己返回 401 时会再次触发刷新，
 * 直接递归成死循环。
 */
function refreshFlow() {
  if (refreshing) {
    return refreshing
  }

  const token = getRefreshToken()
  if (!token) {
    refreshing = Promise.resolve(false)
    return refreshing
  }

  refreshing = rawRequest('POST', '/auth/refresh', { refreshToken: token })
    .then((resp) => {
      const body = resp.data
      if (body && body.code === 200 && body.data && body.data.accessToken) {
        saveTokens(body.data)
        return true
      }
      return false
    })
    .catch(() => false)
    .then((ok) => {
      refreshing = null
      if (!ok) {
        // 刷新令牌都失效了，只能重新登录
        clearTokens()
        if (expireHandler) {
          expireHandler()
        }
      }
      return ok
    })

  return refreshing
}

/**
 * 发起请求
 *
 * @param {string} method  GET / POST / PUT / DELETE
 * @param {string} url     以 / 开头的接口路径，如 /products
 * @param {object} data    请求数据（GET 会拼成 query）
 * @param {object} options
 *        silent  为 true 时不自动弹错误提示，由页面自己展示
 *        retry   内部使用，标记是否还允许续期重放
 */
export async function request(method, url, data, options = {}) {
  const { silent = false } = options
  const allowRetry = options.retry !== false

  const accessToken = getAccessToken()
  const header = accessToken ? { Authorization: 'Bearer ' + accessToken } : {}

  let resp
  try {
    resp = await rawRequest(method, url, data, header)
  } catch (e) {
    if (!silent) {
      toast('网络不可用，请检查网络连接')
    }
    throw new ApiError(-1, '网络不可用')
  }

  const body = resp.data
  const isEnvelope = body && typeof body === 'object' && 'code' in body
  const code = isEnvelope ? body.code : resp.statusCode

  if (code === 200) {
    return isEnvelope ? body.data : body
  }

  if (code === 401) {
    if (allowRetry) {
      const ok = await refreshFlow()
      if (ok) {
        // 令牌已换新，把原请求原样重放一次
        return request(method, url, data, { ...options, retry: false })
      }
    }
    const message = (isEnvelope && body.message) || '登录已过期，请重新登录'
    if (!silent) {
      toast(message)
    }
    throw new ApiError(401, message)
  }

  const message = (isEnvelope && body.message) || '请求失败（' + code + '）'
  if (!silent) {
    toast(message)
  }
  throw new ApiError(code, message)
}

export const http = {
  get: (url, params, options) => request('GET', url, params, options),
  post: (url, data, options) => request('POST', url, data, options),
  put: (url, data, options) => request('PUT', url, data, options),
  del: (url, data, options) => request('DELETE', url, data, options)
}

/**
 * 确保手上有可用的访问令牌
 *
 * 冷启动时内存里的访问令牌一定是空的（它从不落盘），
 * 这时候用本地留存的刷新令牌换一张新的，用户就免于重新登录。
 */
export function ensureAccessToken() {
  if (getAccessToken()) {
    return Promise.resolve(true)
  }
  if (!getRefreshToken()) {
    return Promise.resolve(false)
  }
  return refreshFlow()
}

export default http
