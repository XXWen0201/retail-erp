/**
 * 运行环境配置
 *
 * ⚠️ 真机预览时把 API_HOST 改成电脑的局域网地址（同 WiFi 下），
 *    例如 http://192.168.1.7:8080
 *    手机和电脑不在同一网段时小程序是连不上 localhost 的。
 */

export const API_HOST = 'http://localhost:8080'

/**
 * 接口基地址
 *
 * H5 端走 Vite 代理（见 vite.config.js），用相对路径 /api，
 * 好处是同源、不受跨域限制，调试时和 PC 端体验一致；
 * 小程序端没有代理这一层，必须写完整地址直连后端。
 */
// #ifdef H5
export const API_BASE = '/api'
// #endif
// #ifndef H5
export const API_BASE = API_HOST + '/api'
// #endif

/** 请求超时（毫秒）。AI 问答要走大模型，给得宽裕一些 */
export const REQUEST_TIMEOUT = 60000

/** 本地缓存键名 */
export const STORAGE_KEYS = {
  refreshToken: 'erp_refresh_token',
  user: 'erp_user_info',
  lastUsername: 'erp_last_username'
}
