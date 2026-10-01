import http from './request'

export const login = (data) => http.post('/auth/login', data, { silent: true })
// 刷新失败是"未登录"的正常路径，不该弹全屏报错，所以设 silent
//
// body 传空对象而不是 null：axios 对 null 不会设置 Content-Type，
// 浏览器会按 application/x-www-form-urlencoded 发出去，
// 后端就匹配不上 JSON 转换器（已同时在后端做了容错，但这里保持契约明确）。
export const refresh = () => http.post('/auth/refresh', {}, { silent: true })
export const logout = () => http.post('/auth/logout', {}, { silent: true })
export const getMe = () => http.get('/auth/me', { silent: true })
