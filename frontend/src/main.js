import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import {
  ArrowDown, Back, Bell, BellFilled, Box, ChatDotRound, Check, Close, Delete,
  DocumentAdd, Files, Lock, MagicStick, Odometer, Plus, Promotion, Refresh,
  RefreshLeft, Search, Select, Sell, ShoppingCart, TrendCharts, User, VideoPlay
} from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/request'
import { useUserStore } from './stores/user'
import './styles/index.css'

const app = createApp(App)
const pinia = createPinia()

/**
 * 图标按需注册
 *
 * 之前是 `import * as` 全量注册两百多个图标，其中绝大多数从未出现在模板里，
 * 却全都被打进主包。改成只注册真正用到的 25 个之后，主包体积明显下降。
 *
 * 为什么仍然要全局注册一部分，而不是让每个页面自己 import：
 * Navbar、Sidebar、工作台、登录页这四个文件在模板里直接写 <el-icon><Search /></el-icon>，
 * 依赖的正是全局注册。把它们逐个改成 import 也能行，但侧边栏那种
 * 「图标名写在路由配置的字符串里」的用法反而更绕。
 * 所以这里取折中：只把实际用到的注册成全局，新增图标时记得加进下面这个清单。
 *
 * 注意：页面里已有的显式 `import { Plus } from '@element-plus/icons-vue'` 不受影响，
 * 局部导入优先级高于全局注册。
 */
const icons = {
  ArrowDown, Back, Bell, BellFilled, Box, ChatDotRound, Check, Close, Delete,
  DocumentAdd, Files, Lock, MagicStick, Odometer, Plus, Promotion, Refresh,
  RefreshLeft, Search, Select, Sell, ShoppingCart, TrendCharts, User, VideoPlay
}
for (const [key, component] of Object.entries(icons)) {
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
