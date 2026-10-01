<template>
  <view class="page">
    <!-- ---------- 业务类型筛选 ---------- -->
    <scroll-view class="chips" scroll-x :show-scrollbar="false">
      <view class="chips-inner">
        <view
          v-for="t in bizTypes"
          :key="t.key"
          class="chip"
          :class="{ active: bizType === t.key }"
          @click="pick(t.key)"
        >
          {{ t.text }}
        </view>
      </view>
    </scroll-view>

    <view class="summary">
      <text class="summary-text">近 {{ days }} 天共 {{ total }} 条流水</text>
    </view>

    <!-- ---------- 流水列表 ---------- -->
    <view class="list">
      <view v-for="r in list" :key="r.id" class="item">
        <view class="item-head">
          <view class="badge" :class="Number(r.changeQuantity) > 0 ? 'badge-in' : 'badge-out'">
            {{ r.bizTypeLabel || r.bizType }}
          </view>
          <text class="change" :class="Number(r.changeQuantity) > 0 ? 'up' : 'down'">
            {{ Number(r.changeQuantity) > 0 ? '+' : '' }}{{ quantity(r.changeQuantity) }}
          </text>
        </view>

        <text class="product-name ellipsis">{{ r.productName }}</text>

        <view v-if="r.batchNo" class="batch-line">
          <text class="batch-text">批次 {{ r.batchNo }}</text>
        </view>

        <view class="item-foot">
          <view class="foot-left">
            <text class="stock-flow">
              {{ quantity(r.beforeQuantity) }} → {{ quantity(r.afterQuantity) }}
            </text>
          </view>
          <view class="foot-right">
            <text class="biz-no">{{ r.bizNo }}</text>
            <text class="time">{{ shortTime(r.createTime) }}</text>
          </view>
        </view>

        <view v-if="r.operatorName || r.remark" class="item-remark">
          <text class="remark-text">
            {{ r.operatorName || '' }}{{ r.remark ? ' · ' + r.remark : '' }}
          </text>
        </view>
      </view>
    </view>

    <view v-if="!loading && !list.length" class="empty">该条件下暂无库存流水</view>
    <view v-if="loading" class="loading-more">加载中…</view>
    <view v-else-if="list.length && !hasMore" class="loading-more">没有更多了</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { pageRecords } from '../../api/stock'
import { quantity, shortTime } from '../../utils/format'

const PAGE_SIZE = 15

/** 与后端 BizTypeEnum 的枚举名一一对应，写错会导致筛选查不到数据 */
const bizTypes = [
  { key: '', text: '全部' },
  { key: 'PURCHASE_IN', text: '采购入库' },
  { key: 'SALE_OUT', text: '销售出库' },
  { key: 'SALE_RETURN_IN', text: '销售退货' },
  { key: 'PURCHASE_RETURN_OUT', text: '采购退货' },
  { key: 'CHECK_GAIN', text: '盘盈' },
  { key: 'CHECK_LOSS', text: '盘亏' }
]

const bizType = ref('')
const list = ref([])
const total = ref(0)
const loading = ref(false)
const hasMore = ref(true)
const days = ref(30)

let productId = null
let page = 1

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
    if (bizType.value) {
      params.bizType = bizType.value
    }

    const res = await pageRecords(params)
    const records = (res && res.records) || []
    total.value = (res && res.total) || 0
    list.value = reset ? records : list.value.concat(records)
    hasMore.value = list.value.length < total.value && records.length > 0
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
  if (bizType.value === key) {
    return
  }
  bizType.value = key
  list.value = []
  load(true)
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

/* ---------- 筛选 ---------- */
.chips {
  background: #ffffff;
  white-space: nowrap;
}

.chips-inner {
  display: flex;
  padding: 20rpx 24rpx;
}

.chip {
  flex-shrink: 0;
  height: 60rpx;
  line-height: 60rpx;
  padding: 0 26rpx;
  margin-right: 14rpx;
  border-radius: 30rpx;
  background: $erp-bg;
  color: $erp-text-sub;
  font-size: 25rpx;

  &.active {
    background: $erp-primary;
    color: #ffffff;
    font-weight: 600;
  }
}

.summary {
  padding: 20rpx 30rpx 4rpx;
}

.summary-text {
  font-size: 24rpx;
  color: $erp-text-muted;
}

/* ---------- 列表 ---------- */
.list {
  padding: 12rpx 24rpx 0;
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

.badge {
  padding: 5rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
}

.badge-in {
  background: $erp-success-soft;
  color: $erp-success;
}

.badge-out {
  background: $erp-warning-soft;
  color: $erp-warning;
}

.change {
  font-size: 38rpx;
  font-weight: 700;

  &.up {
    color: $erp-success;
  }

  &.down {
    color: $erp-warning;
  }
}

.product-name {
  display: block;
  margin-top: 14rpx;
  font-size: 29rpx;
  font-weight: 600;
  color: $erp-text;
}

.batch-line {
  margin-top: 8rpx;
}

.batch-text {
  font-size: 23rpx;
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

.stock-flow {
  font-size: 25rpx;
  color: $erp-text-sub;
}

.foot-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.biz-no {
  font-size: 23rpx;
  color: $erp-text-sub;
}

.time {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $erp-text-muted;
}

.item-remark {
  margin-top: 12rpx;
}

.remark-text {
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
