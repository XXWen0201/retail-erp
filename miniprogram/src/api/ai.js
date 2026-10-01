import http from '../utils/request'

/**
 * 补货建议列表
 *
 * 这是本地算法算出来的（移动平均 + 安全库存），不依赖外部大模型，
 * 所以没有密钥、断网的情况下依然可用。
 */
export function replenishSuggestions(days = 30) {
  return http.get('/ai/replenish/suggestions', { days })
}

/** 针对单个商品生成 AI 分析文字（会真的调大模型） */
export function analyzeReplenish(productId, days = 30) {
  return http.post('/ai/replenish/analyze', { productId, days }, { silent: true })
}

/** 库存智能问答：模型会自己决定调用哪个库存查询工具 */
export function aiChat(question) {
  return http.post('/ai/chat', { question }, { silent: true })
}

/** AI 调用记录 */
export function pageAiLogs(params) {
  return http.get('/ai/logs', params)
}
