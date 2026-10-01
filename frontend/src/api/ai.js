import http, { clean } from './request'
import { getAccessToken } from './request'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

/** 本地算法算出的补货建议，不依赖外部大模型 */
export const replenishSuggestions = (params) =>
  http.get('/ai/replenish/suggestions', { params: clean(params) })

/** 单个商品的 AI 文字分析（完整返回） */
export const analyzeReplenish = (productId, days = 30) =>
  http.post('/ai/replenish/analyze', { productId, days }, { timeout: 120000 })

/** 库存智能问答（Function Calling，完整返回） */
export const aiChat = (question) =>
  http.post('/ai/chat', { question }, { timeout: 120000 })

export const pageAiLogs = (params) => http.get('/ai/logs', { params: clean(params) })

/**
 * 通用 SSE 流式请求
 *
 * 不用 EventSource：它只能发 GET、且不能自定义请求头，没法带 Bearer 令牌。
 * 这里用 fetch + ReadableStream 手工解析 text/event-stream。
 *
 * @param {string} path       相对 /api 的路径，如 '/ai/chat/stream'
 * @param {object} payload    请求体
 * @param {object} handlers   { onMessage(chunk), onDone(fullText), onError(err) }
 * @param {AbortSignal} signal 用于中止
 */
export async function streamRequest(path, payload, handlers = {}, signal) {
  const { onMessage, onDone, onError } = handlers
  let full = ''

  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(getAccessToken() ? { Authorization: `Bearer ${getAccessToken()}` } : {})
      },
      credentials: 'include',
      body: JSON.stringify(payload),
      signal
    })

    if (!res.ok || !res.body) {
      throw new Error(`连接失败（HTTP ${res.status}）`)
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    // eslint-disable-next-line no-constant-condition
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 事件以空行分隔，逐块切出来处理
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || ''

      for (const part of parts) {
        const lines = part.split('\n')
        for (const line of lines) {
          if (!line.startsWith('data:')) continue
          const data = line.slice(5).trim()
          if (data === '[DONE]') {
            onDone && onDone(full)
            return full
          }
          try {
            const obj = JSON.parse(data)
            const piece = obj.content ?? obj.data ?? ''
            if (piece) {
              full += piece
              onMessage && onMessage(piece, full)
            }
          } catch {
            // 非 JSON 片段直接当文本处理
            full += data
            onMessage && onMessage(data, full)
          }
        }
      }
    }

    onDone && onDone(full)
    return full
  } catch (e) {
    if (e.name === 'AbortError') return full
    onError && onError(e)
    throw e
  }
}

export const replenishStream = (productId, days, handlers, signal) =>
  streamRequest('/ai/replenish/analyze/stream', { productId, days }, handlers, signal)

export const aiChatStream = (question, handlers, signal) =>
  streamRequest('/ai/chat/stream', { question }, handlers, signal)
