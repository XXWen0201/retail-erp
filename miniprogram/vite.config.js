import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

/**
 * uni-app Vue3 构建配置
 *
 * 只有 H5 端才会跑到下面的 server 配置；编译到微信小程序时，
 * devServer 是开发者工具自己的事，跟这里无关。
 */
export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5273,
    host: '0.0.0.0',
    proxy: {
      // 走同源代理是为了让 H5 端调试时不受跨域与 Cookie SameSite 的干扰，
      // 跟 pc 端保持一致的做法。小程序端不用代理，直连后端地址。
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          // 代理转发时必须把 Origin 摘掉。
          // 带着 Origin 转发过去，后端的 CORS 会把这当成一次跨域请求，
          // 而 H5 端的调试端口（5273）不在白名单里，结果就是 403
          // “Invalid CORS request”。同源代理本就不该有这个头。
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin')
          })
        }
      }
    }
  }
})
