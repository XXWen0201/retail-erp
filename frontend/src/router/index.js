import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const Layout = () => import('@/layout/index.vue')

export const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'Odometer' }
      },
      {
        path: 'basic/product',
        name: 'Product',
        component: () => import('@/views/basic/product.vue'),
        meta: { title: '商品管理', icon: 'Goods' }
      },
      {
        path: 'basic/category',
        name: 'Category',
        component: () => import('@/views/basic/category.vue'),
        meta: { title: '商品分类', icon: 'Files' }
      },
      {
        path: 'basic/supplier',
        name: 'Supplier',
        component: () => import('@/views/basic/supplier.vue'),
        meta: { title: '供应商管理', icon: 'OfficeBuilding' }
      },
      {
        path: 'purchase',
        name: 'Purchase',
        component: () => import('@/views/purchase/index.vue'),
        meta: { title: '采购管理', icon: 'ShoppingCart' }
      },
      {
        path: 'purchase/create',
        name: 'PurchaseCreate',
        component: () => import('@/views/purchase/edit.vue'),
        meta: { title: '新建采购单', hidden: true }
      },
      {
        path: 'sale',
        name: 'Sale',
        component: () => import('@/views/sale/index.vue'),
        meta: { title: '销售管理', icon: 'Sell' }
      },
      {
        path: 'sale/create',
        name: 'SaleCreate',
        component: () => import('@/views/sale/edit.vue'),
        meta: { title: '新建销售单', hidden: true }
      },
      {
        path: 'returns',
        name: 'Returns',
        component: () => import('@/views/returns/index.vue'),
        meta: { title: '退货管理', icon: 'RefreshLeft' }
      },
      {
        path: 'stock/batch',
        name: 'StockBatch',
        component: () => import('@/views/stock/batch.vue'),
        meta: { title: '批次与保质期', icon: 'Box' }
      },
      {
        path: 'stock/record',
        name: 'StockRecord',
        component: () => import('@/views/stock/record.vue'),
        meta: { title: '库存流水', icon: 'Tickets' }
      },
      {
        path: 'stock/benchmark',
        name: 'StockBenchmark',
        component: () => import('@/views/stock/benchmark.vue'),
        meta: { title: '并发扣减压测', icon: 'DataLine' }
      },
      {
        path: 'check',
        name: 'StockCheck',
        component: () => import('@/views/check/index.vue'),
        meta: { title: '库存盘点', icon: 'DocumentChecked' }
      },
      {
        path: 'check/:id',
        name: 'StockCheckDetail',
        component: () => import('@/views/check/detail.vue'),
        meta: { title: '盘点单详情', hidden: true }
      },
      {
        path: 'alert',
        name: 'Alert',
        component: () => import('@/views/alert/index.vue'),
        meta: { title: '库存预警', icon: 'BellFilled' }
      },
      {
        path: 'report/sales',
        name: 'ReportSales',
        component: () => import('@/views/report/sales.vue'),
        meta: { title: '销售统计', icon: 'TrendCharts' }
      },
      {
        path: 'report/purchase',
        name: 'ReportPurchase',
        component: () => import('@/views/report/purchase.vue'),
        meta: { title: '采购统计', icon: 'Histogram' }
      },
      {
        path: 'report/stock',
        name: 'ReportStock',
        component: () => import('@/views/report/stock.vue'),
        meta: { title: '库存统计', icon: 'PieChart' }
      },
      {
        path: 'report/profit',
        name: 'ReportProfit',
        component: () => import('@/views/report/profit.vue'),
        meta: { title: '毛利统计', icon: 'Money' }
      },
      {
        path: 'ai',
        name: 'AiAssistant',
        component: () => import('@/views/ai/index.vue'),
        meta: { title: 'AI 智能助手', icon: 'MagicStick' }
      },
      {
        path: 'ai/logs',
        name: 'AiLogs',
        component: () => import('@/views/ai/logs.vue'),
        meta: { title: 'AI 调用记录', icon: 'Memo' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', public: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

const APP_TITLE = import.meta.env.VITE_APP_TITLE || '零售进销存管理系统'

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (to.meta.public) {
    // 已登录还去登录页，直接送回工作台
    if (to.path === '/login' && userStore.isLogin) return { path: '/dashboard' }
    return true
  }

  if (userStore.isLogin) return true

  // 刷新页面后内存里的令牌没了，尝试用 Cookie 里的刷新令牌恢复一次
  const ok = await userStore.restore()
  if (ok) return true

  return { path: '/login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${APP_TITLE}` : APP_TITLE
})

export default router
