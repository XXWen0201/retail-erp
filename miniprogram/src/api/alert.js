import http from '../utils/request'

/** 预警分页：可按类型、级别、处理状态、关键词筛选 */
export function pageAlerts(params) {
  return http.get('/alerts', params)
}

/** 各类型未处理数量统计 */
export function alertSummary() {
  return http.get('/alerts/summary')
}

/** 手动触发一次全量扫描 */
export function scanAlerts() {
  return http.post('/alerts/scan')
}

/** 标记已处理 */
export function handleAlert(id, handleRemark) {
  return http.put('/alerts/' + id + '/handle', { handleRemark })
}

/** 忽略 */
export function ignoreAlert(id) {
  return http.put('/alerts/' + id + '/ignore')
}
