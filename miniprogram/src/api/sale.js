import http from '../utils/request'

/** 销售单分页 */
export function pageSales(params) {
  return http.get('/sales', params)
}

/** 销售单详情（含明细与批次） */
export function getSale(id) {
  return http.get('/sales/' + id)
}

/**
 * 开单并出库
 *
 * silent: true —— 库存不足（4001）这类错误要在开单页里就地提示，
 * 让用户能直接改数量重试，而不是被一个转瞬即逝的 toast 打发掉。
 */
export function createSale(data) {
  return http.post('/sales', data, { silent: true })
}

/** 作废并回滚库存 */
export function cancelSale(id) {
  return http.post('/sales/' + id + '/cancel')
}
