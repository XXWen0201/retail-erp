import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'

/**
 * uni-app 的入口约定：必须导出 createApp 函数，
 * 且用 createSSRApp 而不是 createApp（小程序端靠它做同构渲染）。
 */
export function createApp() {
  const app = createSSRApp(App)
  app.use(createPinia())
  return { app }
}
