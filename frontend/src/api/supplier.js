import http, { clean } from './request'

export const pageSuppliers = (params) => http.get('/suppliers', { params: clean(params) })
export const supplierOptions = () => http.get('/suppliers/options')
export const getSupplier = (id) => http.get(`/suppliers/${id}`)
export const createSupplier = (data) => http.post('/suppliers', data)
export const updateSupplier = (id, data) => http.put(`/suppliers/${id}`, data)
export const deleteSupplier = (id) => http.delete(`/suppliers/${id}`)
