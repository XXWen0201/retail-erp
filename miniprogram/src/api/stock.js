import http from '../utils/request'

/** 库存总览：SKU 数、库存成本、各类预警计数 */
export function stockOverview() {
  return http.get('/stock/overview')
}

/** 批次分页，可按商品筛选或只看临期 / 过期 */
export function pageBatches(params) {
  return http.get('/stock/batches', params)
}

/** 库存流水分页 */
export function pageRecords(params) {
  return http.get('/stock/records', params)
}
