<template>
  <el-scrollbar class="menu-scroll">
    <el-menu
      :default-active="activeMenu"
      :collapse="collapsed"
      :collapse-transition="false"
      background-color="#1f2937"
      text-color="#c9d1d9"
      active-text-color="#ffffff"
      router
      unique-opened
    >
      <el-menu-item index="/dashboard">
        <el-icon><Odometer /></el-icon>
        <template #title>工作台</template>
      </el-menu-item>

      <el-sub-menu index="basic">
        <template #title>
          <el-icon><Files /></el-icon>
          <span>基础资料</span>
        </template>
        <el-menu-item index="/basic/product">商品管理</el-menu-item>
        <el-menu-item index="/basic/category">商品分类</el-menu-item>
        <el-menu-item index="/basic/supplier">供应商管理</el-menu-item>
      </el-sub-menu>

      <el-menu-item index="/purchase">
        <el-icon><ShoppingCart /></el-icon>
        <template #title>采购管理</template>
      </el-menu-item>

      <el-menu-item index="/sale">
        <el-icon><Sell /></el-icon>
        <template #title>销售管理</template>
      </el-menu-item>

      <el-menu-item index="/returns">
        <el-icon><RefreshLeft /></el-icon>
        <template #title>退货管理</template>
      </el-menu-item>

      <el-sub-menu index="stock">
        <template #title>
          <el-icon><Box /></el-icon>
          <span>库存管理</span>
        </template>
        <el-menu-item index="/stock/batch">批次与保质期</el-menu-item>
        <el-menu-item index="/stock/record">库存流水</el-menu-item>
        <el-menu-item index="/check">库存盘点</el-menu-item>
        <el-menu-item index="/alert">
          库存预警
          <el-badge
            v-if="alertCount > 0"
            :value="alertCount"
            :max="99"
            class="menu-badge"
          />
        </el-menu-item>
        <el-menu-item index="/stock/benchmark">并发扣减压测</el-menu-item>
      </el-sub-menu>

      <el-sub-menu index="report">
        <template #title>
          <el-icon><TrendCharts /></el-icon>
          <span>统计报表</span>
        </template>
        <el-menu-item index="/report/sales">销售统计</el-menu-item>
        <el-menu-item index="/report/purchase">采购统计</el-menu-item>
        <el-menu-item index="/report/stock">库存统计</el-menu-item>
        <el-menu-item index="/report/profit">毛利统计</el-menu-item>
      </el-sub-menu>

      <el-sub-menu index="ai">
        <template #title>
          <el-icon><MagicStick /></el-icon>
          <span>AI 智能助手</span>
        </template>
        <el-menu-item index="/ai">补货建议与问答</el-menu-item>
        <el-menu-item index="/ai/logs">调用记录</el-menu-item>
      </el-sub-menu>
    </el-menu>
  </el-scrollbar>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { alertSummary } from '@/api/alert'

defineProps({
  collapsed: { type: Boolean, default: false }
})

const route = useRoute()

// 详情页（如盘点单详情）要让父菜单保持高亮
const activeMenu = computed(() => route.path)

const alertCount = ref(0)
let timer = null

async function loadAlertCount() {
  try {
    const data = await alertSummary()
    alertCount.value = (data && data.unhandledTotal) || 0
  } catch {
    // 静默：角标拿不到不影响主流程
  }
}

onMounted(() => {
  loadAlertCount()
  timer = setInterval(loadAlertCount, 60000)
})

onUnmounted(() => timer && clearInterval(timer))
</script>

<style scoped>
.menu-scroll {
  flex: 1;
  min-height: 0;
}

:deep(.el-menu) {
  border-right: none;
}

:deep(.el-menu-item.is-active) {
  background: #409eff !important;
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: #2b3648 !important;
}

:deep(.el-sub-menu .el-menu-item) {
  background: #18202c;
  min-width: auto;
}

:deep(.el-sub-menu .el-menu-item:hover) {
  background: #2b3648 !important;
}

.menu-badge {
  margin-left: 8px;
  margin-top: -2px;
}
</style>
