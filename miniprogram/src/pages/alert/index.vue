<template>
  <view class="page">
    <!-- ---------- 顶部统计 ---------- -->
    <view class="summary">
      <view class="summary-main">
        <text class="summary-num">{{ summary.unhandledTotal || 0 }}</text>
        <text class="summary-label">条待处理预警</text>
      </view>
      <view class="scan-btn" @click="doScan">
        <text class="scan-text">{{ scanning ? '扫描中…' : '立即扫描' }}</text>
      </view>
    </view>

    <!-- 分类计数：点一下直接筛选，省得在列表里翻 -->
    <scroll-view class="counts" scroll-x :show-scrollbar="false">
      <view class="counts-inner">
        <view
          v-for="c in counters"
          :key="c.key"
          class="count-chip"
          :class="{ active: alertType === c.key }"
          @click="pickType(c.key)"
        >
          <text class="count-num">{{ c.value }}</text>
          <text class="count-text">{{ c.text }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- ---------- 状态切换 ---------- -->
    <view class="status-bar">
      <view
        v-for="s in statusTabs"
        :key="s.key"
        class="status-tab"
        :class="{ active: status === s.key }"
        @click="pickStatus(s.key)"
      >
        {{ s.text }}
      </view>
    </view>

    <!-- ---------- 列表 ---------- -->
    <view class="list">
      <view v-for="a in list" :key="a.id" class="item">
        <view class="item-head">
          <view class="type-line">
            <view class="level-dot" :class="a.alertLevel === 'DANGER' ? 'danger' : 'warn'"></view>
            <text class="type-text">{{ a.alertTypeLabel || a.alertType }}</text>
          </view>
          <text class="item-time">{{ shortTime(a.createTime) }}</text>
        </view>

        <text class="product-name ellipsis">{{ a.productName }}</text>
        <view v-if="a.batchNo" class="batch-line">
          <text class="batch-text">批次 {{ a.batchNo }}</text>
        </view>

        <view class="value-line">
          <text class="value-text">{{ valueText(a) }}</text>
        </view>

        <!-- 处理结果 -->
        <view v-if="a.status !== 'UNHANDLED'" class="handled">
          <text class="handled-text">
            {{ a.status === 'HANDLED' ? '已处理' : '已忽略' }}
            <text v-if="a.handleUser"> · {{ a.handleUser }}</text>
            <text v-if="a.handleRemark"> · {{ a.handleRemark }}</text>
          </text>
        </view>

        <!-- 操作 -->
        <view v-else class="item-actions">
          <view class="act act-ghost" @click="openHandle(a)">处理</view>
          <view class="act act-plain" @click="doIgnore(a)">忽略</view>
        </view>
      </view>
    </view>

    <view v-if="!loading && !list.length" class="empty">
      {{ status === 'UNHANDLED' ? '暂无待处理预警，一切正常' : '该条件下没有记录' }}
    </view>
    <view v-if="loading" class="loading-more">加载中…</view>
    <view v-else-if="list.length && !hasMore" class="loading-more">没有更多了</view>

    <!-- ---------- 处理弹窗 ---------- -->
    <view v-if="handleVisible" class="mask" @click="closeHandle">
      <view class="dialog" @click.stop>
        <text class="dialog-title">处理预警</text>
        <text class="dialog-desc">{{ current.productName }}</text>
        <text class="dialog-sub">{{ current.alertTypeLabel }}</text>

        <textarea
          class="dialog-input"
          v-model="handleRemark"
          placeholder="填写处理说明，例如：已下单补货 60 个"
          placeholder-class="ph"
          maxlength="120"
        />

        <view class="dialog-actions">
          <view class="dialog-btn" @click="closeHandle">取消</view>
          <view class="dialog-btn primary" @click="doHandle">确认处理</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { alertSummary, handleAlert, ignoreAlert, pageAlerts, scanAlerts } from '../../api/alert'
import { money, quantity, shortTime } from '../../utils/format'

const PAGE_SIZE = 15

const statusTabs = [
  { key: 'UNHANDLED', text: '待处理' },
  { key: 'HANDLED', text: '已处理' },
  { key: '', text: '全部' }
]

const alertType = ref('')
const status = ref('UNHANDLED')
const summary = reactive({})
const list = ref([])
const loading = ref(false)
const hasMore = ref(true)
const scanning = ref(false)

const handleVisible = ref(false)
const handleRemark = ref('')
const current = ref({})

let page = 1
let loadedOnce = false

const counters = computed(() => [
  { key: '', text: '全部类型', value: summary.unhandledTotal || 0 },
  { key: 'LOW_STOCK', text: '低于下限', value: summary.lowStockCount || 0 },
  { key: 'OUT_OF_STOCK', text: '断货', value: summary.outOfStockCount || 0 },
  { key: 'OVER_STOCK', text: '超储', value: summary.overStockCount || 0 },
  { key: 'NEAR_EXPIRY', text: '临期', value: summary.nearExpiryCount || 0 },
  { key: 'EXPIRED', text: '已过期', value: summary.expiredCount || 0 }
])

/** 不同类型预警的「当前值 → 阈值」读法不一样，分开表述才不别扭 */
function valueText(a) {
  const cur = quantity(a.currentValue)
  const th = quantity(a.thresholdValue)
  switch (a.alertType) {
    case 'LOW_STOCK':
      return `当前 ${cur}，低于下限 ${th}`
    case 'OUT_OF_STOCK':
      return `库存已为 0，下限 ${th}`
    case 'OVER_STOCK':
      return `当前 ${cur}，高于上限 ${th}`
    case 'NEAR_EXPIRY':
      return a.expireDate ? `剩余 ${cur}，${a.expireDate} 到期` : `剩余 ${cur}`
    case 'EXPIRED':
      return a.expireDate ? `剩余 ${cur}，已于 ${a.expireDate} 过期` : `剩余 ${cur}`
    default:
      return `当前 ${cur} / 阈值 ${th}`
  }
}

async function loadSummary() {
  try {
    const res = await alertSummary()
    Object.keys(summary).forEach((k) => delete summary[k])
    Object.assign(summary, res || {})
  } catch (e) {
    /* 顶部计数拿不到不影响列表展示 */
  }
}

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
    if (alertType.value) {
      params.alertType = alertType.value
    }
    if (status.value) {
      params.status = status.value
    }

    const res = await pageAlerts(params)
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

function pickType(key) {
  if (alertType.value === key) {
    return
  }
  alertType.value = key
  list.value = []
  load(true)
}

function pickStatus(key) {
  if (status.value === key) {
    return
  }
  status.value = key
  list.value = []
  load(true)
}

async function doScan() {
  if (scanning.value) {
    return
  }
  scanning.value = true
  try {
    const res = await scanAlerts()
    // 后端返回本次扫描的新增 / 更新 / 关闭数量，读出来更有说服力
    const parts = []
    if (res) {
      if (res.newCount !== undefined) parts.push('新增 ' + res.newCount)
      if (res.updatedCount !== undefined) parts.push('更新 ' + res.updatedCount)
      if (res.closedCount !== undefined) parts.push('自动关闭 ' + res.closedCount)
    }
    uni.showToast({
      title: parts.length ? '扫描完成：' + parts.join('，') : '扫描完成',
      icon: 'none',
      duration: 2500
    })
    await Promise.all([loadSummary(), load(true)])
  } catch (e) {
    /* 请求层已提示 */
  } finally {
    scanning.value = false
  }
}

function openHandle(a) {
  current.value = a
  handleRemark.value = ''
  handleVisible.value = true
}

function closeHandle() {
  handleVisible.value = false
}

async function doHandle() {
  const remark = handleRemark.value.trim()
  if (!remark) {
    uni.showToast({ title: '请填写处理说明', icon: 'none' })
    return
  }
  try {
    await handleAlert(current.value.id, remark)
    closeHandle()
    uni.showToast({ title: '已处理', icon: 'success' })
    await Promise.all([loadSummary(), load(true)])
  } catch (e) {
    /* 请求层已提示 */
  }
}

function doIgnore(a) {
  uni.showModal({
    title: '忽略此预警',
    content: `确认忽略「${a.productName}」的${a.alertTypeLabel || '该'}预警？`,
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await ignoreAlert(a.id)
        uni.showToast({ title: '已忽略', icon: 'success' })
        await Promise.all([loadSummary(), load(true)])
      } catch (e) {
        /* 请求层已提示 */
      }
    }
  })
}

onShow(() => {
  loadSummary()
  if (!loadedOnce) {
    loadedOnce = true
  }
  load(true)
})

onPullDownRefresh(async () => {
  await Promise.all([loadSummary(), load(true)])
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

/* ---------- 顶部 ---------- */
.summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 36rpx 32rpx;
  background: linear-gradient(160deg, #3a78ff, #1d4ed8);
}

.summary-main {
  display: flex;
  align-items: baseline;
}

.summary-num {
  font-size: 60rpx;
  font-weight: 700;
  color: #ffffff;
}

.summary-label {
  margin-left: 14rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.82);
}

.scan-btn {
  height: 68rpx;
  padding: 0 30rpx;
  border-radius: 34rpx;
  background: rgba(255, 255, 255, 0.18);
  border: 2rpx solid rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
}

.scan-text {
  font-size: 26rpx;
  color: #ffffff;
  font-weight: 600;
}

/* ---------- 分类计数 ---------- */
.counts {
  background: #ffffff;
  white-space: nowrap;
}

.counts-inner {
  display: flex;
  padding: 24rpx 24rpx 20rpx;
}

.count-chip {
  flex-shrink: 0;
  min-width: 132rpx;
  padding: 16rpx 20rpx;
  margin-right: 16rpx;
  border-radius: 18rpx;
  background: $erp-bg;
  display: flex;
  flex-direction: column;
  align-items: center;

  &.active {
    background: $erp-primary-soft;
    border: 2rpx solid $erp-primary;
  }
}

.count-num {
  font-size: 34rpx;
  font-weight: 700;
  color: $erp-text;
}

.count-chip.active .count-num {
  color: $erp-primary;
}

.count-text {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: $erp-text-muted;
}

/* ---------- 状态切换 ---------- */
.status-bar {
  display: flex;
  padding: 0 24rpx 20rpx;
  background: #ffffff;
}

.status-tab {
  height: 60rpx;
  line-height: 60rpx;
  padding: 0 28rpx;
  margin-right: 16rpx;
  border-radius: 30rpx;
  font-size: 25rpx;
  color: $erp-text-sub;

  &.active {
    background: $erp-primary;
    color: #ffffff;
    font-weight: 600;
  }
}

/* ---------- 列表 ---------- */
.list {
  padding: 24rpx 24rpx 0;
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

.type-line {
  display: flex;
  align-items: center;
}

.level-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  margin-right: 12rpx;

  &.danger {
    background: $erp-danger;
  }

  &.warn {
    background: $erp-warning;
  }
}

.type-text {
  font-size: 26rpx;
  font-weight: 600;
  color: $erp-text;
}

.item-time {
  font-size: 22rpx;
  color: $erp-text-muted;
}

.product-name {
  display: block;
  margin-top: 14rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: $erp-text;
}

.batch-line {
  margin-top: 6rpx;
}

.batch-text {
  font-size: 22rpx;
  color: $erp-text-muted;
}

.value-line {
  margin-top: 12rpx;
}

.value-text {
  font-size: 26rpx;
  color: $erp-text-sub;
}

.handled {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 2rpx solid $erp-border;
}

.handled-text {
  font-size: 23rpx;
  color: $erp-text-muted;
}

.item-actions {
  display: flex;
  margin-top: 22rpx;
  padding-top: 22rpx;
  border-top: 2rpx solid $erp-border;
}

.act {
  flex: 1;
  height: 70rpx;
  line-height: 70rpx;
  text-align: center;
  border-radius: 35rpx;
  font-size: 27rpx;

  & + .act {
    margin-left: 18rpx;
  }
}

.act-ghost {
  background: linear-gradient(135deg, #4b85ff, #2f6bff);
  color: #ffffff;
  font-weight: 600;
}

.act-plain {
  background: $erp-bg;
  color: $erp-text-sub;
}

.loading-more {
  padding: 28rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: $erp-text-muted;
}

/* ---------- 处理弹窗 ---------- */
.mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(15, 22, 36, 0.45);
  z-index: 99;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 56rpx;
}

.dialog {
  width: 100%;
  background: #ffffff;
  border-radius: 28rpx;
  padding: 40rpx 36rpx 32rpx;
}

.dialog-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: $erp-text;
}

.dialog-desc {
  display: block;
  margin-top: 16rpx;
  font-size: 28rpx;
  color: $erp-text-sub;
}

.dialog-sub {
  display: block;
  margin-top: 6rpx;
  font-size: 23rpx;
  color: $erp-text-muted;
}

.dialog-input {
  width: 100%;
  height: 168rpx;
  margin-top: 24rpx;
  padding: 20rpx 24rpx;
  box-sizing: border-box;
  border-radius: 16rpx;
  background: $erp-bg;
  font-size: 27rpx;
  color: $erp-text;
}

.ph {
  color: #b6bfcc;
  font-size: 25rpx;
}

.dialog-actions {
  display: flex;
  margin-top: 32rpx;
}

.dialog-btn {
  flex: 1;
  height: 82rpx;
  line-height: 82rpx;
  text-align: center;
  border-radius: 41rpx;
  font-size: 29rpx;
  background: $erp-bg;
  color: $erp-text-sub;

  &.primary {
    margin-left: 20rpx;
    background: linear-gradient(135deg, #4b85ff, #2f6bff);
    color: #ffffff;
    font-weight: 600;
  }
}
</style>
