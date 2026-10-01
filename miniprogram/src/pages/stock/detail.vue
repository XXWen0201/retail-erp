<template>
  <view class="page">
    <view v-if="item" class="content">
      <!-- ---------- 基本信息 ---------- -->
      <view class="head">
        <text class="name">{{ item.name }}</text>
        <view class="tag" :class="'tag-' + stockStatus(item.stockStatus).type">
          {{ stockStatus(item.stockStatus).text }}
        </view>
        <view class="meta">
          <text class="meta-text">{{ item.code }}</text>
          <text v-if="item.categoryName" class="meta-dot">·</text>
          <text v-if="item.categoryName" class="meta-text">{{ item.categoryName }}</text>
          <text v-if="item.spec" class="meta-dot">·</text>
          <text v-if="item.spec" class="meta-text">{{ item.spec }}</text>
        </view>
        <view v-if="item.barcode" class="meta">
          <text class="meta-text">条码 {{ item.barcode }}</text>
        </view>
      </view>

      <!-- ---------- 库存 ---------- -->
      <view class="card stock-card">
        <view class="stock-left">
          <text class="stock-num" :class="stockClass">{{ quantity(item.stockQuantity) }}</text>
          <text class="stock-unit">{{ item.unit || '件' }}</text>
        </view>
        <view class="stock-right">
          <view class="limit-row">
            <text class="limit-label">库存下限</text>
            <text class="limit-value">{{ quantity(item.stockLower) }}</text>
          </view>
          <view class="limit-row">
            <text class="limit-label">库存上限</text>
            <text class="limit-value">{{ quantity(item.stockUpper) }}</text>
          </view>
        </view>
      </view>

      <!-- ---------- 价格 ---------- -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">价格信息</text>
        </view>
        <view class="kv">
          <text class="k">零售价</text>
          <text class="v strong">￥{{ money(item.salePrice) }}</text>
        </view>
        <view class="kv">
          <text class="k">采购价</text>
          <text class="v">￥{{ money(item.purchasePrice) }}</text>
        </view>
        <view class="kv">
          <text class="k">移动加权平均成本</text>
          <text class="v">￥{{ costText }}</text>
        </view>
        <view class="kv">
          <text class="k">单件毛利</text>
          <text class="v" :class="profit >= 0 ? 'profit-up' : 'profit-down'">
            ￥{{ money(profit) }}（{{ marginText }}）
          </text>
        </view>
        <view v-if="item.shelfLifeDays" class="kv">
          <text class="k">保质期</text>
          <text class="v">{{ item.shelfLifeDays }} 天</text>
        </view>
      </view>

      <!-- ---------- 批次 ---------- -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">库存批次</text>
          <text class="card-extra">{{ batches.length }} 个在用批次</text>
        </view>

        <view v-if="batches.length" class="batches">
          <view v-for="b in batches" :key="b.id" class="batch">
            <view class="batch-top">
              <text class="batch-no ellipsis">{{ b.batchNo }}</text>
              <view class="tag" :class="'tag-' + expireStatus(b.expireStatus).type">
                {{ expireStatus(b.expireStatus).text }}
              </view>
            </view>
            <view class="batch-bottom">
              <text class="batch-qty">剩余 {{ quantity(b.stockQuantity) }}</text>
              <text class="batch-expire">{{ expireDesc(b.daysToExpire) }}</text>
            </view>
          </view>
        </view>
        <view v-else class="empty">该商品暂无在用批次</view>
      </view>

      <!-- ---------- 操作 ---------- -->
      <view class="actions">
        <view class="btn btn-ghost" @click="goRecord">查看流水</view>
        <view class="btn btn-primary" @click="goCreateSale">去开单</view>
      </view>
    </view>

    <view v-else-if="!loading" class="empty">商品不存在或已被删除</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProduct } from '../../api/product'
import { pageBatches } from '../../api/stock'
import { expireDesc, expireStatus, money, quantity, stockStatus } from '../../utils/format'

const item = ref(null)
const batches = ref([])
const loading = ref(true)

const stockClass = computed(() => {
  const s = item.value && item.value.stockStatus
  if (s === 'OUT' || s === 'LOW') return 'danger'
  if (s === 'OVER') return 'warning'
  return ''
})

/** 平均成本保留 4 位：移动加权平均是逐笔累加出来的，截断到 2 位会累积误差 */
const costText = computed(() => {
  const c = Number((item.value && item.value.avgCost) || 0)
  return c.toFixed(4)
})

const profit = computed(() => {
  const sale = Number((item.value && item.value.salePrice) || 0)
  const cost = Number((item.value && item.value.avgCost) || 0)
  return sale - cost
})

const marginText = computed(() => {
  const sale = Number((item.value && item.value.salePrice) || 0)
  if (sale <= 0) return '—'
  return ((profit.value / sale) * 100).toFixed(1) + '%'
})

async function load(id) {
  loading.value = true
  try {
    item.value = await getProduct(id)
    try {
      const res = await pageBatches({ productId: id, page: 1, size: 20 })
      // 只展示还有余量的批次，空批次显示出来只会干扰判断
      batches.value = ((res && res.records) || []).filter((b) => Number(b.stockQuantity) > 0)
    } catch (e) {
      batches.value = []
    }
  } catch (e) {
    item.value = null
  } finally {
    loading.value = false
  }
}

function goRecord() {
  uni.navigateTo({ url: '/pages/stock/record?productId=' + item.value.id })
}

function goCreateSale() {
  uni.navigateTo({ url: '/pages/sale/create?productId=' + item.value.id })
}

onLoad((options) => {
  const id = options && options.id
  if (id) {
    load(id)
  } else {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding-bottom: 60rpx;
}

.content {
  padding: 32rpx 24rpx 0;
}

/* ---------- 头部 ---------- */
.head {
  padding: 0 8rpx 28rpx;
}

.name {
  font-size: 40rpx;
  font-weight: 700;
  color: $erp-text;
}

.tag {
  display: inline-block;
  margin-left: 16rpx;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  font-size: 21rpx;
  vertical-align: middle;
}

.tag-success {
  background: $erp-success-soft;
  color: $erp-success;
}
.tag-danger {
  background: $erp-danger-soft;
  color: $erp-danger;
}
.tag-warning {
  background: $erp-warning-soft;
  color: $erp-warning;
}
.tag-info {
  background: #eef0f4;
  color: $erp-text-sub;
}

.meta {
  display: flex;
  align-items: center;
  margin-top: 14rpx;
}

.meta-text {
  font-size: 25rpx;
  color: $erp-text-muted;
}

.meta-dot {
  margin: 0 10rpx;
  color: #ccd3de;
}

/* ---------- 库存卡片 ---------- */
.stock-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #3a78ff, #2f6bff);
  box-shadow: 0 12rpx 32rpx rgba(47, 107, 255, 0.24);
}

.stock-left {
  display: flex;
  align-items: baseline;
}

.stock-num {
  font-size: 72rpx;
  font-weight: 700;
  color: #ffffff;

  &.danger {
    color: #ffd9d4;
  }
  &.warning {
    color: #ffe6b8;
  }
}

.stock-unit {
  margin-left: 10rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.8);
}

.stock-right {
  display: flex;
  flex-direction: column;
}

.limit-row {
  display: flex;
  align-items: center;
  justify-content: space-between;

  & + .limit-row {
    margin-top: 12rpx;
  }
}

.limit-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.76);
  margin-right: 20rpx;
}

.limit-value {
  font-size: 28rpx;
  font-weight: 600;
  color: #ffffff;
}

/* ---------- 通用卡片 ---------- */
.card {
  margin-top: 24rpx;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
}

.card-extra {
  font-size: 24rpx;
  color: $erp-text-muted;
}

.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 0;

  & + .kv {
    border-top: 2rpx solid $erp-border;
  }
}

.k {
  font-size: 27rpx;
  color: $erp-text-sub;
}

.v {
  font-size: 27rpx;
  color: $erp-text;

  &.strong {
    font-weight: 700;
    color: $erp-primary;
  }
}

.profit-up {
  color: $erp-danger;
}

.profit-down {
  color: $erp-success;
}

/* ---------- 批次 ---------- */
.batch {
  padding: 22rpx 0;

  & + .batch {
    border-top: 2rpx solid $erp-border;
  }
}

.batch-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.batch-no {
  font-size: 26rpx;
  color: $erp-text;
  max-width: 420rpx;
}

.batch-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10rpx;
}

.batch-qty {
  font-size: 24rpx;
  color: $erp-text-sub;
}

.batch-expire {
  font-size: 23rpx;
  color: $erp-text-muted;
}

/* ---------- 底部操作 ---------- */
.actions {
  display: flex;
  margin-top: 40rpx;
}

.btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;

  & + .btn {
    margin-left: 20rpx;
  }
}

.btn-ghost {
  background: #ffffff;
  color: $erp-text-sub;
  border: 2rpx solid $erp-border;
}

.btn-primary {
  background: linear-gradient(135deg, #4b85ff, #2f6bff);
  color: #ffffff;
  box-shadow: 0 12rpx 28rpx rgba(47, 107, 255, 0.28);
}
</style>
