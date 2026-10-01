<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '210px'" class="layout-aside">
      <div class="logo" :class="{ 'is-collapsed': collapsed }">
        <div class="logo-mark">ERP</div>
        <div v-show="!collapsed" class="logo-text">
          <div class="logo-title">零售进销存</div>
          <div class="logo-sub">库存预警管理系统</div>
        </div>
      </div>
      <Sidebar :collapsed="collapsed" />
    </el-aside>

    <el-container class="layout-body">
      <el-header class="layout-header">
        <Navbar v-model:collapsed="collapsed" @refresh="viewKey++" />
      </el-header>
      <el-main class="layout-main">
        <router-view :key="`${route.path}-${viewKey}`" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './Sidebar.vue'
import Navbar from './Navbar.vue'

// 侧边栏折叠状态放在布局层，Sidebar 和 Navbar 只需要读
const collapsed = ref(false)
// 顶栏点刷新时自增，换掉 router-view 的 key 逼页面重建
const viewKey = ref(0)
const route = useRoute()
</script>

<style scoped>
.layout {
  height: 100vh;
  overflow: hidden;
}

.layout-aside {
  background: #1f2937;
  transition: width 0.22s ease;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.logo {
  height: var(--app-header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.logo.is-collapsed {
  justify-content: center;
  padding: 0;
}

.logo-mark {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 7px;
  background: linear-gradient(135deg, #409eff, #2b7de9);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 0.5px;
}

.logo-text {
  overflow: hidden;
  white-space: nowrap;
}

.logo-title {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
}

.logo-sub {
  color: #9ca3af;
  font-size: 11px;
  line-height: 1.3;
}

.layout-body {
  overflow: hidden;
}

.layout-header {
  height: var(--app-header-height);
  padding: 0;
  background: #fff;
  border-bottom: 1px solid #e8ebf0;
  flex-shrink: 0;
}

.layout-main {
  padding: 0;
  background: var(--app-bg);
  overflow-y: auto;
}
</style>
