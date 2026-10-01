<template>
  <div class="navbar">
    <div class="left">
      <el-icon class="collapse-btn" @click="toggle">
        <component :is="collapsed ? 'Expand' : 'Fold'" />
      </el-icon>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="right">
      <el-tooltip content="库存预警" placement="bottom">
        <el-badge :value="alertCount" :max="99" :hidden="alertCount === 0" class="bell">
          <el-icon class="icon-btn" @click="$router.push('/alert')"><Bell /></el-icon>
        </el-badge>
      </el-tooltip>

      <el-tooltip content="刷新当前页" placement="bottom">
        <el-icon class="icon-btn" @click="reload"><Refresh /></el-icon>
      </el-tooltip>

      <el-dropdown @command="onCommand">
        <div class="user">
          <el-avatar :size="28" class="avatar">{{ avatarText }}</el-avatar>
          <span class="name">{{ userStore.displayName }}</span>
          <el-tag size="small" :type="userStore.isAdmin ? 'danger' : 'info'" effect="plain">
            {{ roleLabel }}
          </el-tag>
          <el-icon class="caret"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="me">
              当前账号：{{ userStore.user?.username || '-' }}
            </el-dropdown-item>
            <el-dropdown-item command="refreshMe" divided>刷新权限信息</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { alertSummary } from '@/api/alert'

const props = defineProps({
  collapsed: { type: Boolean, default: false }
})
const emit = defineEmits(['update:collapsed', 'refresh'])

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const currentTitle = computed(() => route.meta.title || '')
const roleLabel = computed(() => {
  const map = { ADMIN: '管理员', MANAGER: '店长', STAFF: '店员' }
  return map[userStore.role] || userStore.role || '-'
})
const avatarText = computed(() => (userStore.displayName || 'U').slice(0, 1))

function toggle() {
  emit('update:collapsed', !props.collapsed)
}

function reload() {
  // 交给布局层换 key 重建 <router-view>，页面 onMounted 会重跑，
  // 比 window.location.reload() 轻，也不会丢掉侧边栏状态
  emit('refresh')
}

const alertCount = ref(0)
let timer = null
async function loadAlertCount() {
  try {
    const data = await alertSummary()
    alertCount.value = (data && data.unhandledTotal) || 0
  } catch {
    /* 静默 */
  }
}

async function onCommand(cmd) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.replace('/login')
  } else if (cmd === 'refreshMe') {
    try {
      await userStore.reloadMe()
      ElMessage.success('权限信息已刷新')
    } catch {
      /* 拦截器已提示 */
    }
  }
}

onMounted(() => {
  loadAlertCount()
  timer = setInterval(loadAlertCount, 60000)
})
onUnmounted(() => timer && clearInterval(timer))
</script>

<style scoped>
.navbar {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
}

.left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.collapse-btn {
  font-size: 18px;
  color: #606266;
  cursor: pointer;
}
.collapse-btn:hover {
  color: #409eff;
}

.right {
  display: flex;
  align-items: center;
  gap: 18px;
}

.icon-btn {
  font-size: 17px;
  color: #606266;
  cursor: pointer;
}
.icon-btn:hover {
  color: #409eff;
}

.bell :deep(.el-badge__content) {
  top: 6px;
  right: 10px;
}

.user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.avatar {
  background: #409eff;
  font-size: 13px;
}

.name {
  font-size: 14px;
  color: #303133;
}

.caret {
  font-size: 12px;
  color: #909399;
}
</style>
