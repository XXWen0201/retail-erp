import http, { clean } from './request'

export const pageAlerts = (params) => http.get('/alerts', { params: clean(params) })
export const alertSummary = () => http.get('/alerts/summary')
/** 手动触发全量扫描 */
export const scanAlerts = () => http.post('/alerts/scan', null, { timeout: 120000 })
export const handleAlert = (id, handleRemark) => http.put(`/alerts/${id}/handle`, { handleRemark })
export const ignoreAlert = (id) => http.put(`/alerts/${id}/ignore`)
