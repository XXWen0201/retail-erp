<template>
  <view class="page">
    <!-- ---------- 用户卡片 ---------- -->
    <view class="profile">
      <view class="avatar">
        <text class="avatar-text">{{ avatarText }}</text>
      </view>
      <view class="profile-main">
        <text class="profile-name">{{ store.displayName }}</text>
        <view class="profile-meta">
          <text class="meta-text">{{ store.user ? store.user.username : '-' }}</text>
          <text class="meta-dot">·</text>
          <text class="meta-text">{{ roleText(store.role) }}</text>
        </view>
      </view>
    </view>

    <!-- ---------- 权限说明 ---------- -->
    <view class="card role-card">
      <view class="card-head">
        <text class="card-title">我的操作权限</text>
      </view>
      <view class="perm-list">
        <view v-for="p in permissions" :key="p.text" class="perm">
          <view class="perm-dot" :class="p.allow ? 'allow' : 'deny'">
            {{ p.allow ? '✓' : '✕' }}
          </view>
          <text class="perm-text">{{ p.text }}</text>
        </view>
      </view>
    </view>

    <!-- ---------- 功能入口 ---------- -->
    <view class="card menu-card">
      <view class="menu-item" @click="go('/pages/sale/list')">
        <text class="menu-text">销售单记录</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="go('/pages/stock/batch')">
        <text class="menu-text">批次与保质期</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="go('/pages/stock/record')">
        <text class="menu-text">库存流水</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item" @click="go('/pages/ai/index')">
        <text class="menu-text">AI 智能助手</text>
        <text class="arrow">›</text>
      </view>
      <view class="menu-item last" @click="go('/pages/ai/logs')">
        <text class="menu-text">AI 调用记录</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <!-- ---------- 关于 ---------- -->
    <view class="card about-card">
      <view class="card-head">
        <text class="card-title">关于</text>
      </view>
      <view class="kv">
        <text class="k">客户端版本</text>
        <text class="v">v1.0.0</text>
      </view>
      <view class="kv">
        <text class="k">技术栈</text>
        <text class="v">uni-app + Vue 3</text>
      </view>
      <view class="kv">
        <text class="k">后端服务</text>
        <text class="v">{{ apiHost }}</text>
      </view>
    </view>

    <!-- ---------- 退出 ---------- -->
    <view class="logout" @click="confirmLogout">退出登录</view>

    <view class="bottom-tip">
      <text class="tip-text">零售进销存与库存预警管理系统</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { API_HOST } from '../../config'
import { useUserStore } from '../../stores/user'
import { roleText } from '../../utils/format'

const store = useUserStore()

const apiHost = API_HOST

const avatarText = computed(() => {
  const name = store.displayName || '未'
  return name.slice(0, 1)
})

/** 权限说明直接反映后端 RBAC 的实际约束，避免店员点了才发现没权限 */
const permissions = computed(() => {
  const manager = store.isManager
  return [
    { text: '查询商品、库存与批次', allow: true },
    { text: '销售开单与作废', allow: true },
    { text: '处理库存预警', allow: true },
    { text: '使用 AI 补货建议与问答', allow: true },
    { text: '维护商品与供应商资料', allow: manager },
    { text: '采购入库与采购退货', allow: manager },
    { text: '提交库存盘点', allow: manager }
  ]
})

function go(url) {
  uni.navigateTo({ url })
}

function confirmLogout() {
  uni.showModal({
    title: '退出登录',
    content: '退出后需要重新输入账号密码。',
    confirmText: '退出',
    confirmColor: '#f5483b',
    success: (res) => {
      if (res.confirm) {
        store.logout()
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: 0 24rpx 60rpx;
}

/* ---------- 用户卡 ---------- */
.profile {
  display: flex;
  align-items: center;
  margin: 32rpx 0 24rpx;
  padding: 40rpx 32rpx;
  border-radius: $erp-radius;
  background: linear-gradient(150deg, #3a78ff, #1d4ed8);
  box-shadow: 0 14rpx 36rpx rgba(29, 78, 216, 0.24);
}

.avatar {
  width: 108rpx;
  height: 108rpx;
  border-radius: 34rpx;
  background: rgba(255, 255, 255, 0.2);
  border: 2rpx solid rgba(255, 255, 255, 0.32);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 26rpx;
}

.avatar-text {
  font-size: 44rpx;
  font-weight: 700;
  color: #ffffff;
}

.profile-main {
  display: flex;
  flex-direction: column;
}

.profile-name {
  font-size: 38rpx;
  font-weight: 700;
  color: #ffffff;
}

.profile-meta {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
}

.meta-text {
  font-size: 25rpx;
  color: rgba(255, 255, 255, 0.8);
}

.meta-dot {
  margin: 0 10rpx;
  color: rgba(255, 255, 255, 0.6);
}

/* ---------- 卡片 ---------- */
.card {
  margin-bottom: 24rpx;
}

.card-head {
  margin-bottom: 20rpx;
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
}

/* ---------- 权限 ---------- */
.perm {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
}

.perm-dot {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  margin-right: 18rpx;

  &.allow {
    background: $erp-success-soft;
    color: $erp-success;
  }

  &.deny {
    background: #eef0f4;
    color: #a7b0bd;
  }
}

.perm-text {
  font-size: 27rpx;
  color: $erp-text;
}

/* ---------- 菜单 ---------- */
.menu-card {
  padding: 8rpx 28rpx;
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx 0;
  border-bottom: 2rpx solid $erp-border;

  &.last {
    border-bottom: none;
  }
}

.menu-text {
  font-size: 29rpx;
  color: $erp-text;
}

.arrow {
  font-size: 34rpx;
  color: $erp-text-muted;
}

/* ---------- 关于 ---------- */
.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14rpx 0;
}

.k {
  font-size: 26rpx;
  color: $erp-text-sub;
}

.v {
  font-size: 26rpx;
  color: $erp-text;
}

/* ---------- 退出 ---------- */
.logout {
  margin-top: 16rpx;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 46rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: $erp-danger;
  background: #ffffff;
  border: 2rpx solid #f7c8c3;
}

.bottom-tip {
  margin-top: 48rpx;
  text-align: center;
}

.tip-text {
  font-size: 22rpx;
  color: $erp-text-muted;
}
</style>
