import http from '../utils/request'

/** 商品分页 */
export function pageProducts(params) {
  return http.get('/products', params)
}

/** 商品详情 */
export function getProduct(id) {
  return http.get('/products/' + id)
}

/** 按条码精确查商品（扫码查货用） */
export function findByBarcode(barcode) {
  return http.get('/products', { keyword: barcode, page: 1, size: 5 })
}

/** 全部分类 */
export function listCategories() {
  return http.get('/categories')
}

/** 供应商下拉选项 */
export function supplierOptions() {
  return http.get('/suppliers/options')
}
