import http, { clean } from './request'

export const dashboard = () => http.get('/reports/dashboard')
export const purchaseReport = (params) => http.get('/reports/purchase', { params: clean(params) })
export const salesReport = (params) => http.get('/reports/sales', { params: clean(params) })
export const stockReport = (params) => http.get('/reports/stock', { params: clean(params) })
export const profitReport = (params) => http.get('/reports/profit', { params: clean(params) })
export const topProducts = (params) => http.get('/reports/top-products', { params: clean(params) })
