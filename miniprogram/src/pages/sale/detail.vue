<template>
  <view class="page">
    <view v-if="detail" class="content">
      <!-- ---------- 单据头 ---------- -->
      <view class="head">
        <view class="head-top">
          <text class="order-no">{{ detail.orderNo }}</text>
          <view class="tag" :class="'tag-' + orderStatus(detail.status).type">
            {{ orderStatus(detail.status).text }}
          </view>
        </view>
        <view class="head-meta">
          <text class="meta-text">客户 {{ detail.customerName || '散客' }}</text>
          <text class="meta-dot">·</text>
          <text class="meta-text">{{ dateOnly(detail.orderDate) }}</text>
        </view>
        <view v-if="detail.operatorName" class="head-meta">
          <text class="meta-text">开单人 {{ detail.operatorName }}</text>
        </view>
      </view>

      <!-- ---------- 明细 ---------- -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">商品明细</text>
          <text class="card-extra">{{ (detail.items || []).length }} 项</text>
        </view>

        <view v-for="it in detail.items || []" :key="it.id" class="line">
          <view class="line-top">
            <text class="line-name ellipsis">{{ it.productName }}</text>
            <text class="line-amount">￥{{ money(it.amount) }}</text>
          </view>
          <view class="line-mid">
            <text class="line-info">
              {{ money(it.price) }} × {{ quantity(it.quantity) }}
            </text>
            <text v-if="it.batchNo" class="line-batch">{{ it.batchNo }}</text>
          </view>
          <view v-if="it.costAmount !== null && it.costAmount !== undefined" class="line-cost">
            <text class="cost-text">
              成本 ￥{{ money(it.costPrice) }} / 件，合计 ￥{{ money(it.costAmount) }}
            </text>
          </view>
        </view>
      </view>

      <!-- ---------- 金额汇总 ---------- -->
      <view class="card">
        <view class="kv">
          <text class="k">商品合计</text>
          <text class="v">￥{{ money(detail.totalAmount) }}</text>
        </view>
        <view v-if="Number(detail.discountAmount) > 0" class="kv">
          <text class="k">优惠</text>
          <text class="v">- ￥{{ money(detail.discountAmount) }}</text>
        </view>
        <view class="kv">
          <text class="k strong">应收金额</text>
          <text class="v pay">￥{{ money(detail.payAmount) }}</text>
        </view>
        <view v-if="detail.status === 'FINISHED'" class="kv">
          <text class="k">成本合计</text>
          <text class="v">￥{{ money(detail.totalCost) }}</text>
        </view>
        <view v-if="detail.status === 'FINISHED'" class="kv">
          <text class="k">毛利</text>
          <text class="v profit">
            ￥{{ money(detail.grossProfit) }}
            <text class="margin">（{{ marginText }}）</text>
          </text>
        </view>
      </view>

      <!-- ---------- 备注 ---------- -->
      <view v-if="detail.remark" class="card">
        <view class="card-head">
          <text class="card-title">备注</text>
        </view>
        <text class="remark">{{ detail.remark }}</text>
      </view>

      <!-- ---------- 操作 ---------- -->
      <view v-if="detail.status === 'FINISHED'" class="actions">
        <view class="btn btn-danger" @click="confirmCancel">作废此单</view>
      </view>
    </view>

    <view v-else-if="!loading" class="empty">单据不存在</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { cancelSale, getSale } from '../../api/sale'
import { dateOnly, money, orderStatus, quantity } from '../../utils/format'

const detail = ref(null)
const loading = ref(true)

const marginText = computed(() => {
  const amount = Number((detail.value && detail.value.payAmount) || 0)
  const profit = Number((detail.value && detail.value.grossProfit) || 0)
  if (amount <= 0) {
    return '—'
  }
  return ((profit / amount) * 100).toFixed(1) + '%'
})

async function load(id) {
  loading.value = true
  try {
    detail.value = await getSale(id)
  } catch (e) {
    detail.value = null
  } finally {
    loading.value = false
  }
}

function confirmCancel() {
  uni.showModal({
    title: '确认作废',
    content: '作废后该单的出库库存会按原批次精确回滚，操作不可撤销。',
    confirmText: '确认作废',
    confirmColor: '#f5483b',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await cancelSale(detail.value.id)
        uni.showToast({ title: '已作废', icon: 'success' })
        load(detail.value.id)
      } catch (e) {
        // 请求层已提示原因（例如状态不允许）
      }
    }
  })
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
  padding: 32rpx 24rpx 60rpx;
}

/* ---------- 单据头 ---------- */
.head {
  padding: 0 8rpx 28rpx;
}

.head-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.order-no {
  font-size: 38rpx;
  font-weight: 700;
  color: $erp-text;
}

.tag {
  padding: 5rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
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

.head-meta {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
}

.meta-text {
  font-size: 25rpx;
  color: $erp-text-muted;
}

.meta-dot {
  margin: 0 10rpx;
  color: #ccd3de;
}

/* ---------- 卡片 ---------- */
.card {
  margin-top: 24rpx;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
}

.card-extra {
  font-size: 24rpx;
  color: $erp-text-muted;
}

/* ---------- 明细行 ---------- */
.line {
  padding: 24rpx 0;

  & + .line {
    border-top: 2rpx solid $erp-border;
  }
}

.line-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.line-name {
  font-size: 29rpx;
  font-weight: 600;
  color: $erp-text;
  max-width: 440rpx;
}

.line-amount {
  font-size: 30rpx;
  font-weight: 700;
  color: $erp-text;
}

.line-mid {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10rpx;
}

.line-info {
  font-size: 24rpx;
  color: $erp-text-sub;
}

.line-batch {
  font-size: 22rpx;
  color: $erp-text-muted;
}

.line-cost {
  margin-top: 8rpx;
}

.cost-text {
  font-size: 22rpx;
  color: $erp-text-muted;
}

/* ---------- 汇总 ---------- */
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

  &.strong {
    font-weight: 600;
    color: $erp-text;
  }
}

.v {
  font-size: 28rpx;
  color: $erp-text;

  &.pay {
    font-size: 38rpx;
    font-weight: 700;
    color: $erp-danger;
  }

  &.profit {
    font-weight: 700;
    color: $erp-danger;
  }
}

.margin {
  font-size: 23rpx;
  font-weight: 400;
  color: $erp-text-muted;
}

.remark {
  font-size: 27rpx;
  color: $erp-text-sub;
  line-height: 1.6;
}

/* ---------- 操作 ---------- */
.actions {
  margin-top: 48rpx;
}

.btn {
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 44rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
}

.btn-danger {
  background: #ffffff;
  color: $erp-danger;
  border: 2rpx solid #f7c8c3;
}
</style>
