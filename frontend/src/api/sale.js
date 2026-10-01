import http, { clean } from './request'

export const pageSales = (params) => http.get('/sales', { params: clean(params) })
export const getSale = (id) => http.get(`/sales/${id}`)
/** 开单即出库，按 FEFO 扣批次 */
export const createSale = (data) => http.post('/sales', data)
export const cancelSale = (id) => http.post(`/sales/${id}/cancel`)
export const deleteSale = (id) => http.delete(`/sales/${id}`)
