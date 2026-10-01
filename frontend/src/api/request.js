import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一请求层
 *
 * 三条设计约束（和 docs/api-contract.md 对齐）：
 *  1. 访问令牌只放内存（模块变量 + Pinia），绝不进 localStorage —— 避免 XSS 直接盗走长期凭据
 *  2. 后端业务失败也返回 HTTP 200，靠 body.code 判断，所以拦截器必须解包 body
 *  3. 令牌 30 分钟过期，遇到 code=401 自动调 /auth/refresh 续期并重放原请求；
 *     刷新用"单例 Promise"实现，防止页面同时发 8 个请求时打出 8 次刷新
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

/** 业务错误对象，页面可 catch 后取 code 做差异化处理 */
export class ApiError extends Error {
  constructor(message, code, config, requestId) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.config = config
    this.requestId = requestId
  }
}

/* ---------------- 令牌状态（由 user store 注入读写） ---------------- */
let accessToken = ''
let onUnauthorized = null
let onTokenRefreshed = null

export function setAccessToken(token) {
  accessToken = token || ''
}
export function getAccessToken() {
  return accessToken
}
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn
}
export function setTokenRefreshedHandler(fn) {
  onTokenRefreshed = fn
}

const http = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  // 刷新令牌在 httpOnly Cookie 里，必须带 Cookie
  withCredentials: true
})

http.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

/* ---------------- 刷新令牌：单例，避免并发风暴 ---------------- */
let refreshPromise = null

function refreshToken() {
  if (!refreshPromise) {
    // 这里用裸 axios，绕开拦截器，防止刷新失败又触发刷新造成递归
    refreshPromise = axios
      .post(`${BASE_URL}/auth/refresh`, {}, { withCredentials: true })
      .then((res) => {
        const body = res.data
        if (body && body.code === 200 && body.data && body.data.accessToken) {
          setAccessToken(body.data.accessToken)
          onTokenRefreshed && onTokenRefreshed(body.data)
          return body.data
        }
        throw new Error((body && body.message) || '登录状态已失效')
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

function notifyError(message) {
  if (message) ElMessage.error(message)
}

http.interceptors.response.use(
  async (res) => {
    const config = res.config
    const body = res.data

    // 流式响应 / 非标准响应体：原样透传
    if (!body || typeof body !== 'object' || body.code === undefined) {
      return body
    }

    if (body.code === 200) {
      return body.data
    }

    // 令牌过期：刷新一次后重放原请求
    if (body.code === 401 && !config._retried) {
      config._retried = true
      try {
        await refreshToken()
        return http(config)
      } catch (e) {
        // 刷新也失败，说明刷新令牌也过期/被吊销，只能重新登录
        onUnauthorized && onUnauthorized()
        return Promise.reject(new ApiError('登录已过期，请重新登录', 401, config))
      }
    }

    if (!config.silent) notifyError(body.message)
    return Promise.reject(new ApiError(body.message || '操作失败', body.code, config, body.requestId))
  },
  (err) => {
    const config = err.config || {}

    // 请求被主动取消：静默（切换页面时的常规现象）
    if (axios.isCancel(err)) {
      return Promise.reject(err)
    }

    let message
    let code = -1
    if (err.code === 'ECONNABORTED') {
      message = '请求超时，请稍后重试'
      code = 'TIMEOUT'
    } else if (!err.response) {
      message = '网络异常，请确认后端服务已启动'
      code = 'NETWORK'
    } else {
      const status = err.response.status
      code = status
      if (status === 401) {
        onUnauthorized && onUnauthorized()
        message = '登录已过期，请重新登录'
      } else if (status === 403) {
        message = '没有权限执行该操作'
      } else if (status === 404) {
        message = '请求的接口不存在'
      } else if (status >= 500) {
        message = '服务器开小差了，请稍后重试'
      } else {
        message = (err.response.data && err.response.data.message) || '请求失败'
      }
    }

    if (!config.silent) notifyError(message)
    return Promise.reject(new ApiError(message, code, config))
  }
)

/**
 * 把查询对象转成干净参数：剔除 undefined / null / 空字符串，
 * 免得后端收到 categoryId='' 这种脏值
 */
export function clean(params) {
  const out = {}
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v
  })
  return out
}

export default http
