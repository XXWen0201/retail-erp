import http, { clean } from './request'

export const pageStockChecks = (params) => http.get('/stock-checks', { params: clean(params) })
export const getStockCheck = (id) => http.get(`/stock-checks/${id}`)
/** 新建盘点单：{ checkDate, remark, productIds } —— productIds 不传则全量建账 */
export const createStockCheck = (data) => http.post('/stock-checks', data)
/** 录入实盘数：{ items: [{ id, actualQuantity, reason }] } */
export const updateCheckItems = (id, data) => http.put(`/stock-checks/${id}/items`, data)
/** 提交盘点：盘盈入库 / 盘亏出库，并写流水 */
export const finishStockCheck = (id) => http.post(`/stock-checks/${id}/finish`)
export const cancelStockCheck = (id) => http.post(`/stock-checks/${id}/cancel`)
