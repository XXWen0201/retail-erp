import http from '../utils/request'

/**
 * 登录
 *
 * 传 client: 'MINI' 是本次移动端改造的关键：后端收到后会把握手用的
 * 刷新令牌放进响应体返回，而不是写 httpOnly Cookie ——
 * 小程序没有浏览器那套 Cookie 语义，自己存本地缓存更可靠。
 */
export function login(username, password) {
  return http.post(
    '/auth/login',
    { username, password, client: 'MINI' },
    { silent: true }
  )
}

/** 退出：把刷新令牌交回后端吊销 */
export function logout(refreshToken) {
  return http.post('/auth/logout', { refreshToken }, { silent: true })
}

/** 当前登录用户 */
export function me() {
  return http.get('/auth/me')
}
