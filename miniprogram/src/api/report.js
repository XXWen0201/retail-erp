import http from '../utils/request'

/** 工作台汇总：今日销售、本月销售、待办、趋势 */
export function dashboard() {
  return http.get('/reports/dashboard')
}

/** 销售统计（趋势 / 分类 / 排行） */
export function salesReport(params) {
  return http.get('/reports/sales', params)
}
