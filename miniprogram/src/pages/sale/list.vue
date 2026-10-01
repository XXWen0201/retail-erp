<template>
  <view class="page">
    <!-- ---------- 搜索 ---------- -->
    <view class="search-bar">
      <view class="search-box">
        <text class="search-icon">🔍</text>
        <input
          class="search-input"
          v-model="keyword"
          placeholder="搜索单号 / 客户"
          placeholder-class="ph"
          confirm-type="search"
          @confirm="doSearch"
          @input="onInput"
        />
        <text v-if="keyword" class="search-clear" @click="clearKeyword">×</text>
      </view>
      <view class="add-btn" @click="goCreate">
        <text class="add-text">+ 开单</text>
      </view>
    </view>

    <!-- ---------- 列表 ---------- -->
    <view class="list">
      <view v-for="item in list" :key="item.id" class="item" @click="goDetail(item.id)">
        <view class="item-head">
          <text class="order-no">{{ item.orderNo }}</text>
          <view class="tag" :class="'tag-' + orderStatus(item.status).type">
            {{ orderStatus(item.status).text }}
          </view>
        </view>

        <view class="item-mid">
          <text class="customer ellipsis">{{ item.customerName || '散客' }}</text>
          <text class="qty">{{ quantity(item.totalQuantity) }} 件</text>
        </view>

        <view class="item-foot">
          <view class="foot-left">
            <text class="amount">￥{{ money(item.payAmount) }}</text>
            <text v-if="item.status === 'FINISHED'" class="profit">
              毛利 ￥{{ money(item.grossProfit) }}
            </text>
          </view>
          <view class="foot-right">
            <text class="operator">{{ item.operatorName || '' }}</text>
            <text class="time">{{ dateOnly(item.orderDate) }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="!loading && !list.length" class="empty">暂无销售单</view>
    <view v-if="loading" class="loading-more">加载中…</view>
    <view v-else-if="list.length && !hasMore" class="loading-more">没有更多了</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { pageSales } from '../../api/sale'
import { dateOnly, money, orderStatus, quantity } from '../../utils/format'

const PAGE_SIZE = 15

const keyword = ref('')
const list = ref([])
const loading = ref(false)
const hasMore = ref(true)

let page = 1
let loadedOnce = false
let searchTimer = null

async function load(reset = false) {
  if (loading.value) {
    return
  }
  if (reset) {
    page = 1
    hasMore.value = true
  }
  if (!hasMore.value) {
    return
  }

  loading.value = true
  try {
    const params = { page, size: PAGE_SIZE }
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim()
    }
    const res = await pageSales(params)
    const records = (res && res.records) || []
    const total = (res && res.total) || 0
    list.value = reset ? records : list.value.concat(records)
    hasMore.value = list.value.length < total && records.length > 0
    page += 1
  } catch (e) {
    if (reset) {
      list.value = []
    }
  } finally {
    loading.value = false
  }
}

function doSearch() {
  list.value = []
  load(true)
}

function onInput() {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => load(true), 350)
}

function clearKeyword() {
  keyword.value = ''
  load(true)
}

function goDetail(id) {
  uni.navigateTo({ url: '/pages/sale/detail?id=' + id })
}

function goCreate() {
  uni.navigateTo({ url: '/pages/sale/create' })
}

onShow(() => {
  // 开完单返回列表要能看到新单据，所以这里每次进来都刷新首页数据
  if (loadedOnce) {
    load(true)
  } else {
    loadedOnce = true
    load(true)
  }
})

onPullDownRefresh(async () => {
  await load(true)
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  load(false)
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding-bottom: 40rpx;
}

/* ---------- 搜索 ---------- */
.search-bar {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
  background: #ffffff;
}

.search-box {
  flex: 1;
  display: flex;
  align-items: center;
  height: 76rpx;
  padding: 0 24rpx;
  border-radius: 38rpx;
  background: $erp-bg;
}

.search-icon {
  font-size: 26rpx;
  margin-right: 12rpx;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  color: $erp-text;
}

.ph {
  color: #b6bfcc;
  font-size: 27rpx;
}

.search-clear {
  font-size: 36rpx;
  color: $erp-text-muted;
  padding: 0 6rpx;
  line-height: 1;
}

.add-btn {
  margin-left: 16rpx;
  height: 76rpx;
  padding: 0 26rpx;
  border-radius: 38rpx;
  background: $erp-primary;
  display: flex;
  align-items: center;
}

.add-text {
  font-size: 27rpx;
  color: #ffffff;
  font-weight: 600;
}

/* ---------- 列表 ---------- */
.list {
  padding: 16rpx 24rpx 0;
}

.item {
  background: $erp-card;
  border-radius: $erp-radius;
  padding: 26rpx 28rpx;
  margin-bottom: 18rpx;
  box-shadow: $erp-shadow;
}

.item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.order-no {
  font-size: 28rpx;
  font-weight: 600;
  color: $erp-text;
}

.tag {
  flex-shrink: 0;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  font-size: 21rpx;
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

.item-mid {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14rpx;
}

.customer {
  font-size: 27rpx;
  color: $erp-text-sub;
  max-width: 420rpx;
}

.qty {
  font-size: 24rpx;
  color: $erp-text-muted;
}

.item-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: 18rpx;
  padding-top: 18rpx;
  border-top: 2rpx solid $erp-border;
}

.foot-left {
  display: flex;
  flex-direction: column;
}

.amount {
  font-size: 34rpx;
  font-weight: 700;
  color: $erp-text;
}

.profit {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: $erp-text-muted;
}

.foot-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.operator {
  font-size: 23rpx;
  color: $erp-text-sub;
}

.time {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $erp-text-muted;
}

.loading-more {
  padding: 28rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: $erp-text-muted;
}
</style>
