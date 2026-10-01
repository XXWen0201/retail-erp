<template>
  <view class="page">
    <!-- ---------- 顶部：问候 + 今日三大指标 ---------- -->
    <view class="hero">
      <view class="hero-top">
        <view class="hero-user">
          <text class="greet">{{ greeting }}</text>
          <text class="who">{{ store.displayName }} · {{ roleText(store.role) }}</text>
        </view>
        <text class="hero-date">{{ dateText }}</text>
      </view>

      <view class="hero-metrics">
        <view class="metric">
          <text class="metric-value">{{ money(data.todaySalesAmount) }}</text>
          <text class="metric-label">今日销售额</text>
        </view>
        <view class="metric-split"></view>
        <view class="metric">
          <text class="metric-value">{{ money(data.todayGrossProfit) }}</text>
          <text class="metric-label">今日毛利</text>
        </view>
        <view class="metric-split"></view>
        <view class="metric">
          <text class="metric-value">{{ data.todaySalesCount || 0 }}</text>
          <text class="metric-label">订单数</text>
        </view>
      </view>
    </view>

    <view class="body">
      <!-- ---------- 待办 ---------- -->
      <view class="card todo">
        <view class="todo-item" @click="goAlert">
          <view class="todo-left">
            <view class="dot dot-danger"></view>
            <text class="todo-label">未处理预警</text>
          </view>
          <view class="todo-right">
            <text class="todo-value">{{ data.unhandledAlertCount || 0 }}</text>
            <text class="arrow">›</text>
          </view>
        </view>
        <view class="todo-line"></view>
        <view class="todo-item" @click="goPurchaseTip">
          <view class="todo-left">
            <view class="dot dot-warning"></view>
            <text class="todo-label">待处理采购单</text>
          </view>
          <view class="todo-right">
            <text class="todo-value">{{ data.pendingPurchaseCount || 0 }}</text>
            <text class="arrow">›</text>
          </view>
        </view>
      </view>

      <!-- ---------- 本月概览 ---------- -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">本月概览</text>
        </view>
        <view class="grid3">
          <view class="grid-cell">
            <text class="cell-value">{{ money(data.monthSalesAmount) }}</text>
            <text class="cell-label">销售额</text>
          </view>
          <view class="grid-cell">
            <text class="cell-value">{{ money(data.monthGrossProfit) }}</text>
            <text class="cell-label">毛利</text>
          </view>
          <view class="grid-cell">
            <text class="cell-value">{{ money(data.totalStockValue) }}</text>
            <text class="cell-label">库存成本</text>
          </view>
        </view>
      </view>

      <!-- ---------- 近 7 天销售趋势 ---------- -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">近 7 天销售</text>
          <text class="card-extra">{{ trendTotal }}</text>
        </view>

        <view v-if="trend.length" class="chart">
          <view v-for="(bar, idx) in trend" :key="idx" class="chart-col">
            <text class="chart-val">{{ bar.short }}</text>
            <view class="chart-track">
              <view
                class="chart-bar"
                :class="{ empty: bar.value === 0 }"
                :style="{ height: bar.height + '%' }"
              ></view>
            </view>
            <text class="chart-label">{{ bar.day }}</text>
          </view>
        </view>
        <view v-else class="empty">近 7 天暂无销售数据</view>
      </view>

      <!-- ---------- 快捷入口 ---------- -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">快捷操作</text>
        </view>
        <view class="quick">
          <view v-for="item in quickActions" :key="item.text" class="quick-item" @click="item.action">
            <view class="quick-icon" :style="{ background: item.bg, color: item.color }">
              {{ item.icon }}
            </view>
            <text class="quick-text">{{ item.text }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { dashboard } from '../../api/report'
import { useUserStore } from '../../stores/user'
import { money, roleText, today } from '../../utils/format'

const store = useUserStore()

const data = ref({
  salesTrend: [],
  categoryStock: []
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const dateText = computed(() => {
  const d = new Date()
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  return `${d.getMonth() + 1}月${d.getDate()}日 ${week}`
})

/** 近 7 天柱状图：把金额换算成百分比高度，图用 view 画，不引图表库 */
const trend = computed(() => {
  const list = (data.value.salesTrend || []).slice(-7)
  const max = Math.max(...list.map((i) => Number(i.amount || 0)), 1)
  return list.map((i) => {
    const value = Number(i.amount || 0)
    return {
      day: String(i.date || '').slice(8),
      short: value >= 1000 ? Math.round(value / 1000) + 'k' : String(Math.round(value)),
      value,
      height: value === 0 ? 2 : Math.max(8, Math.round((value / max) * 100))
    }
  })
})

const trendTotal = computed(() => {
  const sum = (data.value.salesTrend || [])
    .slice(-7)
    .reduce((s, i) => s + Number(i.amount || 0), 0)
  return '合计 ¥' + money(sum)
})

const quickActions = [
  {
    icon: '单',
    text: '销售开单',
    bg: '#eaf1ff',
    color: '#2f6bff',
    action: () => uni.navigateTo({ url: '/pages/sale/create' })
  },
  {
    icon: '扫',
    text: '扫码查货',
    bg: '#e7f8f0',
    color: '#17b26a',
    action: () => uni.navigateTo({ url: '/pages/stock/scan' })
  },
  {
    icon: '查',
    text: '销售单',
    bg: '#fff4e5',
    color: '#ff9f1c',
    action: () => uni.navigateTo({ url: '/pages/sale/list' })
  },
  {
    icon: '库',
    text: '商品库存',
    bg: '#eaf1ff',
    color: '#2f6bff',
    action: () => uni.switchTab({ url: '/pages/stock/index' })
  },
  {
    icon: '期',
    text: '批次保质期',
    bg: '#fdeceb',
    color: '#f5483b',
    action: () => uni.navigateTo({ url: '/pages/stock/batch' })
  },
  {
    icon: '水',
    text: '库存流水',
    bg: '#eef0f4',
    color: '#6b7787',
    action: () => uni.navigateTo({ url: '/pages/stock/record' })
  },
  {
    icon: '警',
    text: '库存预警',
    bg: '#fdeceb',
    color: '#f5483b',
    action: () => uni.switchTab({ url: '/pages/alert/index' })
  },
  {
    icon: 'AI',
    text: '补货建议',
    bg: '#ede9ff',
    color: '#6d4aff',
    action: () => uni.navigateTo({ url: '/pages/ai/index' })
  }
]

async function load() {
  try {
    data.value = (await dashboard()) || {}
  } catch (e) {
    // 请求层已经弹过提示，这里保持页面原样即可
  }
}

function goAlert() {
  uni.switchTab({ url: '/pages/alert/index' })
}

function goPurchaseTip() {
  uni.showToast({
    title: '采购单请在 PC 管理端处理',
    icon: 'none',
    duration: 2000
  })
}

onShow(() => {
  load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding-bottom: 40rpx;
}

/* ---------- 顶部 ---------- */
.hero {
  padding: 36rpx 32rpx 40rpx;
  background: linear-gradient(160deg, #3a78ff 0%, #2f6bff 50%, #1d4ed8 100%);
  border-radius: 0 0 36rpx 36rpx;
}

.hero-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 40rpx;
}

.hero-user {
  display: flex;
  flex-direction: column;
}

.greet {
  font-size: 36rpx;
  font-weight: 600;
  color: #ffffff;
}

.who {
  margin-top: 8rpx;
  font-size: 25rpx;
  color: rgba(255, 255, 255, 0.76);
}

.hero-date {
  font-size: 25rpx;
  color: rgba(255, 255, 255, 0.76);
  padding-top: 8rpx;
}

.hero-metrics {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.14);
  border: 2rpx solid rgba(255, 255, 255, 0.18);
  border-radius: 24rpx;
  padding: 28rpx 0;
}

.metric {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.metric-value {
  font-size: 38rpx;
  font-weight: 700;
  color: #ffffff;
}

.metric-label {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: rgba(255, 255, 255, 0.74);
}

.metric-split {
  width: 2rpx;
  height: 56rpx;
  background: rgba(255, 255, 255, 0.2);
}

/* ---------- 主体 ---------- */
.body {
  padding: 0 24rpx;
  margin-top: -20rpx;
}

.card {
  margin-top: 24rpx;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $erp-text;
}

.card-extra {
  font-size: 25rpx;
  color: $erp-text-sub;
}

/* ---------- 待办 ---------- */
.todo {
  padding: 8rpx 28rpx;
}

.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 0;
}

.todo-left {
  display: flex;
  align-items: center;
}

.dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  margin-right: 16rpx;
}

.dot-danger {
  background: $erp-danger;
}

.dot-warning {
  background: $erp-warning;
}

.todo-label {
  font-size: 29rpx;
  color: $erp-text;
}

.todo-right {
  display: flex;
  align-items: center;
}

.todo-value {
  font-size: 32rpx;
  font-weight: 700;
  color: $erp-text;
  margin-right: 12rpx;
}

.arrow {
  font-size: 34rpx;
  color: $erp-text-muted;
}

.todo-line {
  height: 2rpx;
  background: $erp-border;
}

/* ---------- 三列指标 ---------- */
.grid3 {
  display: flex;
}

.grid-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.cell-value {
  font-size: 32rpx;
  font-weight: 700;
  color: $erp-text;
}

.cell-label {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: $erp-text-muted;
}

/* ---------- 柱状图 ---------- */
.chart {
  display: flex;
  align-items: flex-end;
  height: 300rpx;
}

.chart-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.chart-val {
  font-size: 20rpx;
  color: $erp-text-muted;
  margin-bottom: 8rpx;
}

.chart-track {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.chart-bar {
  width: 32rpx;
  border-radius: 8rpx 8rpx 0 0;
  background: linear-gradient(180deg, #5b8dff, #2f6bff);

  &.empty {
    background: #dde3ee;
  }
}

.chart-label {
  margin-top: 12rpx;
  font-size: 21rpx;
  color: $erp-text-muted;
}

/* ---------- 快捷入口 ---------- */
.quick {
  display: flex;
  flex-wrap: wrap;
}

.quick-item {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 18rpx 0;
}

.quick-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 14rpx;
}

.quick-text {
  font-size: 23rpx;
  color: $erp-text-sub;
}
</style>
