import http, { clean } from './request'

/** 分页查询：keyword / categoryId / status / lowStockOnly / page / size */
export const pageProducts = (params) => http.get('/products', { params: clean(params) })
export const getProduct = (id) => http.get(`/products/${id}`)
export const createProduct = (data) => http.post('/products', data)
export const updateProduct = (id, data) => http.put(`/products/${id}`, data)
export const deleteProduct = (id) => http.delete(`/products/${id}`)
