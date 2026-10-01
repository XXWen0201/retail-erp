<template>
  <view class="page">
    <!-- ---------- 搜索 ---------- -->
    <view class="search-bar">
      <view class="search-box">
        <text class="search-icon">🔍</text>
        <input
          class="search-input"
          v-model="keyword"
          placeholder="搜索商品名称 / 编码 / 条码"
          placeholder-class="ph"
          confirm-type="search"
          @confirm="doSearch"
          @input="onKeywordInput"
        />
        <text v-if="keyword" class="search-clear" @click="clearKeyword">×</text>
      </view>
      <view class="scan-btn" @click="goScan">
        <text class="scan-text">扫码</text>
      </view>
    </view>

    <!-- ---------- 分类筛选 ---------- -->
    <scroll-view class="chips" scroll-x :show-scrollbar="false">
      <view class="chips-inner">
        <view
          class="chip"
          :class="{ active: !query.categoryId }"
          @click="pickCategory(null)"
        >
          全部
        </view>
        <view
          v-for="c in categories"
          :key="c.id"
          class="chip"
          :class="{ active: query.categoryId === c.id }"
          @click="pickCategory(c.id)"
        >
          {{ c.name }}
        </view>
      </view>
    </scroll-view>

    <!-- ---------- 低库存开关 + 统计 ---------- -->
    <view class="filter-bar">
      <view class="switch-wrap" @click="toggleLowStock">
        <view class="switch" :class="{ on: query.lowStockOnly }">
          <view class="switch-dot"></view>
        </view>
        <text class="switch-label">仅看低于下限</text>
      </view>
      <text class="filter-total">共 {{ total }} 种商品</text>
    </view>

    <!-- ---------- 列表 ---------- -->
    <view class="list">
      <view v-for="item in list" :key="item.id" class="item" @click="goDetail(item.id)">
        <view class="item-main">
          <view class="item-top">
            <text class="item-name ellipsis">{{ item.name }}</text>
            <view class="tag" :class="'tag-' + stockStatus(item.stockStatus).type">
              {{ stockStatus(item.stockStatus).text }}
            </view>
          </view>
          <view class="item-meta">
            <text class="meta-text">{{ item.code }}</text>
            <text v-if="item.categoryName" class="meta-dot">·</text>
            <text v-if="item.categoryName" class="meta-text">{{ item.categoryName }}</text>
            <text v-if="item.spec" class="meta-dot">·</text>
            <text v-if="item.spec" class="meta-text">{{ item.spec }}</text>
          </view>
        </view>

        <view class="item-side">
          <view class="side-stock">
            <text class="stock-num" :class="stockClass(item)">{{ quantity(item.stockQuantity) }}</text>
            <text class="stock-unit">{{ item.unit || '件' }}</text>
          </view>
          <text class="side-price">￥{{ money(item.salePrice) }}</text>
        </view>
      </view>
    </view>

    <!-- ---------- 状态提示 ---------- -->
    <view v-if="!loading && !list.length" class="empty">
      {{ query.lowStockOnly ? '暂无低于下限的商品' : '没有找到符合条件的商品' }}
    </view>
    <view v-if="loading" class="loading-more">加载中…</view>
    <view v-else-if="list.length && !hasMore" class="loading-more">没有更多了</view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { listCategories, pageProducts } from '../../api/product'
import { money, quantity, stockStatus } from '../../utils/format'

const PAGE_SIZE = 15

const keyword = ref('')
const categories = ref([])
const list = ref([])
const total = ref(0)
const loading = ref(false)
const hasMore = ref(true)

const query = reactive({
  categoryId: null,
  lowStockOnly: false,
  page: 1
})

let loadedOnce = false
let searchTimer = null

/** 库存数字按状态着色：断货和低于下限用红色，让店员一眼扫到问题商品 */
function stockClass(item) {
  const s = item.stockStatus
  if (s === 'OUT' || s === 'LOW') {
    return 'danger'
  }
  if (s === 'OVER') {
    return 'warning'
  }
  return ''
}

async function loadCategories() {
  try {
    categories.value = (await listCategories()) || []
  } catch (e) {
    categories.value = []
  }
}

async function load(reset = false) {
  if (loading.value) {
    return
  }
  if (reset) {
    query.page = 1
    hasMore.value = true
  }
  if (!hasMore.value) {
    return
  }

  loading.value = true
  try {
    const params = {
      page: query.page,
      size: PAGE_SIZE
    }
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim()
    }
    if (query.categoryId) {
      params.categoryId = query.categoryId
    }
    if (query.lowStockOnly) {
      params.lowStockOnly = true
    }

    const res = await pageProducts(params)
    const records = (res && res.records) || []
    total.value = (res && res.total) || 0
    list.value = reset ? records : list.value.concat(records)
    hasMore.value = list.value.length < total.value && records.length > 0
    query.page += 1
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

function onKeywordInput() {
  // 输入停顿 350ms 再查，避免每敲一个字都打一次接口
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    load(true)
  }, 350)
}

function clearKeyword() {
  keyword.value = ''
  load(true)
}

function pickCategory(id) {
  query.categoryId = id
  list.value = []
  load(true)
}

function toggleLowStock() {
  query.lowStockOnly = !query.lowStockOnly
  list.value = []
  load(true)
}

function goDetail(id) {
  uni.navigateTo({ url: '/pages/stock/detail?id=' + id })
}

function goScan() {
  uni.navigateTo({ url: '/pages/stock/scan' })
}

onShow(() => {
  // 首次进入才拉数据；之后切回来保持现场，想刷新自己下拉
  if (!loadedOnce) {
    loadedOnce = true
    loadCategories()
    load(true)
  }
})

onPullDownRefresh(async () => {
  await Promise.all([loadCategories(), load(true)])
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
  padding: 20rpx 24rpx 12rpx;
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

.scan-btn {
  margin-left: 16rpx;
  height: 76rpx;
  padding: 0 28rpx;
  border-radius: 38rpx;
  background: $erp-primary-soft;
  display: flex;
  align-items: center;
}

.scan-text {
  font-size: 27rpx;
  color: $erp-primary;
  font-weight: 600;
}

/* ---------- 分类 chips ---------- */
.chips {
  background: #ffffff;
  white-space: nowrap;
}

.chips-inner {
  display: flex;
  padding: 16rpx 24rpx 20rpx;
}

.chip {
  flex-shrink: 0;
  height: 60rpx;
  line-height: 60rpx;
  padding: 0 28rpx;
  margin-right: 16rpx;
  border-radius: 30rpx;
  background: $erp-bg;
  color: $erp-text-sub;
  font-size: 26rpx;

  &.active {
    background: $erp-primary;
    color: #ffffff;
    font-weight: 600;
  }
}

/* ---------- 过滤条 ---------- */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 30rpx;
}

.switch-wrap {
  display: flex;
  align-items: center;
}

.switch {
  width: 76rpx;
  height: 42rpx;
  border-radius: 21rpx;
  background: #d3dae6;
  padding: 4rpx;
  transition: background 0.2s;

  &.on {
    background: $erp-primary;
  }
}

.switch-dot {
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  background: #ffffff;
  transition: transform 0.2s;
}

.switch.on .switch-dot {
  transform: translateX(34rpx);
}

.switch-label {
  margin-left: 16rpx;
  font-size: 26rpx;
  color: $erp-text-sub;
}

.filter-total {
  font-size: 25rpx;
  color: $erp-text-muted;
}

/* ---------- 列表 ---------- */
.list {
  padding: 0 24rpx;
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

.item-top {
  display: flex;
  align-items: center;
}

.item-name {
  font-size: 30rpx;
  font-weight: 600;
  color: $erp-text;
  max-width: 380rpx;
}

.tag {
  margin-left: 14rpx;
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

.item-meta {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
}

.meta-text {
  font-size: 23rpx;
  color: $erp-text-muted;
}

.meta-dot {
  margin: 0 8rpx;
  color: #ccd3de;
  font-size: 22rpx;
}

.item-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-left: 16rpx;
}

.side-stock {
  display: flex;
  align-items: baseline;
}

.stock-num {
  font-size: 36rpx;
  font-weight: 700;
  color: $erp-text;

  &.danger {
    color: $erp-danger;
  }

  &.warning {
    color: $erp-warning;
  }
}

.stock-unit {
  margin-left: 6rpx;
  font-size: 22rpx;
  color: $erp-text-muted;
}

.side-price {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: $erp-text-sub;
}

.loading-more {
  padding: 28rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: $erp-text-muted;
}
</style>
