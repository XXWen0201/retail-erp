import http, { clean } from './request'

/** 库存总览卡片数据 */
export const stockOverview = () => http.get('/stock/overview')

/** 批次分页：productId / expiringSoon / expired / page / size */
export const pageBatches = (params) => http.get('/stock/batches', { params: clean(params) })

/** 流水分页：productId / bizType / bizNo / startDate / endDate / page / size */
export const pageRecords = (params) => http.get('/stock/records', { params: clean(params) })

/**
 * 并发扣减压测
 * 会真实扣减库存并写 bizNo=BENCHMARK 的流水，耗时可到几十秒，超时放宽到 5 分钟
 */
export const runBenchmark = (data) =>
  http.post('/stock/benchmark', data, { timeout: 300000 })
