import http, { clean } from './request'

export const pagePurchases = (params) => http.get('/purchases', { params: clean(params) })
export const getPurchase = (id) => http.get(`/purchases/${id}`)
export const createPurchase = (data) => http.post('/purchases', data)
export const updatePurchase = (id, data) => http.put(`/purchases/${id}`, data)
/** 入库：生成批次、增库存、写流水 */
export const receivePurchase = (id) => http.post(`/purchases/${id}/receive`)
export const cancelPurchase = (id) => http.post(`/purchases/${id}/cancel`)
export const deletePurchase = (id) => http.delete(`/purchases/${id}`)
