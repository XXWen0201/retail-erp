<template>
  <view class="page">
    <!-- ---------- 筛选 ---------- -->
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="tab"
        :class="{ active: mode === t.key }"
        @click="pick(t.key)"
      >
        {{ t.text }}
      </view>
    </view>

    <!-- ---------- 列表 ---------- -->
    <view class="list">
      <view v-for="b in list" :key="b.id" class="item" @click="goProduct(b.productId)">
        <view class="item-head">
          <text class="batch-no ellipsis">{{ b.batchNo }}</text>
          <view class="tag" :class="'tag-' + expireStatus(b.expireStatus).type">
            {{ expireStatus(b.expireStatus).text }}
          </view>
        </view>

        <text class="product-name ellipsis">{{ b.productName }}</text>

        <view class="item-foot">
          <view class="foot-left">
            <text class="qty-num">{{ quantity(b.stockQuantity) }}</text>
            <text class="qty-unit">剩余</text>
          </view>
          <view class="foot-right">
            <text class="expire-date">{{ dateOnly(b.expireDate) }} 到期</text>
            <text
              class="expire-desc"
              :class="Number(b.daysToExpire) < 0 ? 'expired' : ''"
            >
              {{ expireDesc(b.daysToExpire) }}
            </text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="!loading && !list.length" class="empty">
      {{ emptyText }}
    </view>
    <view v-if="loading" class="loading-more">加载中…</view>
    <view v-else-if="list.length && !hasMore" class="loading-more">没有更多了</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { pageBatches } from '../../api/stock'
import { dateOnly, expireDesc, expireStatus, quantity } from '../../utils/format'

const PAGE_SIZE = 15

const tabs = [
  { key: 'all', text: '全部在用' },
  { key: 'near', text: '临期 30 天内' },
  { key: 'expired', text: '已过期' }
]

const mode = ref('all')
const list = ref([])
const loading = ref(false)
const hasMore = ref(true)

let productId = null
let page = 1

const emptyText = computed(() => {
  if (mode.value === 'near') return '近 30 天没有临期批次'
  if (mode.value === 'expired') return '没有已过期的批次'
  return '暂无批次数据'
})

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
    if (productId) {
      params.productId = productId
    }
    if (mode.value === 'near') {
      params.expiringSoon = true
    } else if (mode.value === 'expired') {
      params.expired = true
    }

    const res = await pageBatches(params)
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

function pick(key) {
  if (mode.value === key) {
    return
  }
  mode.value = key
  list.value = []
  load(true)
}

function goProduct(id) {
  if (id) {
    uni.navigateTo({ url: '/pages/stock/detail?id=' + id })
  }
}

onLoad((options) => {
  if (options && options.productId) {
    productId = options.productId
  }
  load(true)
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

/* ---------- 顶部分段 ---------- */
.tabs {
  display: flex;
  padding: 24rpx 24rpx 8rpx;
}

.tab {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  font-size: 27rpx;
  color: $erp-text-sub;
  background: #ffffff;
  border-radius: 16rpx;

  & + .tab {
    margin-left: 16rpx;
  }

  &.active {
    background: $erp-primary;
    color: #ffffff;
    font-weight: 600;
  }
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

.batch-no {
  font-size: 27rpx;
  font-weight: 600;
  color: $erp-text;
  max-width: 400rpx;
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

.product-name {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  color: $erp-text-sub;
}

.item-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid $erp-border;
}

.foot-left {
  display: flex;
  align-items: baseline;
}

.qty-num {
  font-size: 38rpx;
  font-weight: 700;
  color: $erp-text;
}

.qty-unit {
  margin-left: 8rpx;
  font-size: 23rpx;
  color: $erp-text-muted;
}

.foot-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.expire-date {
  font-size: 25rpx;
  color: $erp-text-sub;
}

.expire-desc {
  margin-top: 6rpx;
  font-size: 23rpx;
  color: $erp-warning;

  &.expired {
    color: $erp-danger;
  }
}

.loading-more {
  padding: 28rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: $erp-text-muted;
}
</style>
