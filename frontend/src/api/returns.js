import http, { clean } from './request'

export const pageReturns = (params) => http.get('/returns', { params: clean(params) })
export const getReturn = (id) => http.get(`/returns/${id}`)
/** 新建退货并立即生效（回退库存） */
export const createReturn = (data) => http.post('/returns', data)
export const cancelReturn = (id) => http.post(`/returns/${id}/cancel`)
