<template>
  <view class="page">
    <!-- ---------- 扫码区 ---------- -->
    <view class="scan-area" @click="doScan">
      <view class="scan-frame">
        <view class="corner tl"></view>
        <view class="corner tr"></view>
        <view class="corner bl"></view>
        <view class="corner br"></view>
        <text class="scan-icon">▤</text>
      </view>
      <text class="scan-title">点击扫描商品条码</text>
      <text class="scan-tip">{{ scanTip }}</text>
    </view>

    <!-- ---------- 手动输入：H5 端没法调摄像头，真机上也能救急 ---------- -->
    <view class="manual">
      <view class="manual-box">
        <input
          class="manual-input"
          v-model="code"
          placeholder="也可以手动输入条码 / 商品编码"
          placeholder-class="ph"
          confirm-type="search"
          @confirm="search"
        />
      </view>
      <view class="manual-btn" @click="search">查询</view>
    </view>

    <!-- ---------- 查询结果 ---------- -->
    <view v-if="searched" class="result">
      <view v-if="list.length">
        <view v-for="item in list" :key="item.id" class="item" @click="goDetail(item.id)">
          <view class="item-main">
            <text class="item-name ellipsis">{{ item.name }}</text>
            <view class="item-meta">
              <text class="meta-text">{{ item.code }}</text>
              <text v-if="item.barcode" class="meta-dot">·</text>
              <text v-if="item.barcode" class="meta-text">{{ item.barcode }}</text>
            </view>
          </view>
          <view class="item-side">
            <text class="stock-num" :class="stockClass(item)">{{ quantity(item.stockQuantity) }}</text>
            <text class="side-price">￥{{ money(item.salePrice) }}</text>
          </view>
        </view>
      </view>
      <view v-else class="empty">没有匹配「{{ lastCode }}」的商品</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { findByBarcode } from '../../api/product'
import { money, quantity } from '../../utils/format'

const code = ref('')
const lastCode = ref('')
const list = ref([])
const searched = ref(false)
const scanTip = ref('支持条形码与二维码')

function stockClass(item) {
  const s = item.stockStatus
  if (s === 'OUT' || s === 'LOW') return 'danger'
  if (s === 'OVER') return 'warning'
  return ''
}

async function doScan() {
  try {
    const res = await uni.scanCode({
      onlyFromCamera: false,
      scanType: ['barCode', 'qrCode']
    })
    const value = (res && res.result) || ''
    if (!value) {
      return
    }
    code.value = value
    search()
  } catch (e) {
    // H5 端和部分环境没有摄像头权限，退化成手动输入即可
    uni.showToast({
      title: '当前环境无法调起扫码，请手动输入条码',
      icon: 'none',
      duration: 2500
    })
  }
}

async function search() {
  const keyword = code.value.trim()
  if (!keyword) {
    uni.showToast({ title: '请输入条码或商品编码', icon: 'none' })
    return
  }
  try {
    const res = await findByBarcode(keyword)
    list.value = (res && res.records) || []
    lastCode.value = keyword
    searched.value = true
    if (!list.value.length) {
      uni.showToast({ title: '未找到该商品', icon: 'none' })
    }
  } catch (e) {
    list.value = []
    searched.value = true
  }
}

function goDetail(id) {
  uni.navigateTo({ url: '/pages/stock/detail?id=' + id })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding-bottom: 60rpx;
}

/* ---------- 扫码区 ---------- */
.scan-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 0 64rpx;
  background: linear-gradient(160deg, #3a78ff, #1d4ed8);
}

.scan-frame {
  position: relative;
  width: 320rpx;
  height: 320rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 40rpx;
}

.scan-icon {
  font-size: 100rpx;
  color: rgba(255, 255, 255, 0.55);
}

/* 四个角用绝对定位的边框拼出来，比引图标库轻 */
.corner {
  position: absolute;
  width: 60rpx;
  height: 60rpx;
  border-color: #ffffff;
  border-style: solid;
  border-width: 0;
}

.tl {
  top: 0;
  left: 0;
  border-top-width: 6rpx;
  border-left-width: 6rpx;
  border-top-left-radius: 16rpx;
}

.tr {
  top: 0;
  right: 0;
  border-top-width: 6rpx;
  border-right-width: 6rpx;
  border-top-right-radius: 16rpx;
}

.bl {
  bottom: 0;
  left: 0;
  border-bottom-width: 6rpx;
  border-left-width: 6rpx;
  border-bottom-left-radius: 16rpx;
}

.br {
  bottom: 0;
  right: 0;
  border-bottom-width: 6rpx;
  border-right-width: 6rpx;
  border-bottom-right-radius: 16rpx;
}

.scan-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
}

.scan-tip {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.72);
}

/* ---------- 手动输入 ---------- */
.manual {
  display: flex;
  align-items: center;
  padding: 32rpx 24rpx 12rpx;
}

.manual-box {
  flex: 1;
  height: 84rpx;
  padding: 0 28rpx;
  border-radius: 42rpx;
  background: #ffffff;
  display: flex;
  align-items: center;
  box-shadow: $erp-shadow;
}

.manual-input {
  flex: 1;
  font-size: 28rpx;
  color: $erp-text;
}

.ph {
  color: #b6bfcc;
  font-size: 26rpx;
}

.manual-btn {
  margin-left: 18rpx;
  height: 84rpx;
  padding: 0 40rpx;
  border-radius: 42rpx;
  background: $erp-primary;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
}

/* ---------- 结果 ---------- */
.result {
  padding: 12rpx 24rpx 0;
}

.item {
  display: flex;
  align-items: center;
  background: $erp-card;
  border-radius: $erp-radius;
  padding: 26rpx 28rpx;
  margin-bottom: 18rpx;
  box-shadow: $erp-shadow;
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 30rpx;
  font-weight: 600;
  color: $erp-text;
}

.item-meta {
  display: flex;
  align-items: center;
  margin-top: 10rpx;
}

.meta-text {
  font-size: 23rpx;
  color: $erp-text-muted;
}

.meta-dot {
  margin: 0 8rpx;
  color: #ccd3de;
}

.item-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.stock-num {
  font-size: 40rpx;
  font-weight: 700;
  color: $erp-text;

  &.danger {
    color: $erp-danger;
  }
  &.warning {
    color: $erp-warning;
  }
}

.side-price {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: $erp-text-sub;
}
</style>
