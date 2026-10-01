import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/request'
import { useUserStore } from './stores/user'
import './styles/index.css'

const app = createApp(App)
const pinia = createPinia()

// 图标全局注册，模板里直接 <el-icon><Search /></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn, size: 'default' })

/**
 * 令牌彻底失效（刷新令牌也过期/被吊销）时由请求层回调进来。
 * 放在这里而不是请求层，是为了把"跳登录页"这件事交给路由来处理，
 * 请求层保持对路由无感知。
 */
setUnauthorizedHandler(() => {
  const userStore = useUserStore(pinia)
  const wasLogin = userStore.isLogin
  userStore.clear()

  const current = router.currentRoute.value
  if (current.path !== '/login') {
    if (wasLogin) ElMessage.warning('登录已过期，请重新登录')
    router.replace({
      path: '/login',
      query: current.fullPath && current.fullPath !== '/' ? { redirect: current.fullPath } : {}
    })
  }
})

app.mount('#app')
