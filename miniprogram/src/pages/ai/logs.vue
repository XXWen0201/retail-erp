<template>
  <view class="page">
    <view class="list">
      <view v-for="log in list" :key="log.id" class="item">
        <view class="item-head">
          <view class="badge" :class="log.degraded ? 'degraded' : 'normal'">
            {{ log.sceneLabel || log.scene || 'AI 调用' }}
          </view>
          <text class="cost">{{ log.costMs || 0 }} ms</text>
        </view>

        <view v-if="log.question" class="question">
          <text class="q-label">问</text>
          <text class="q-text">{{ log.question }}</text>
        </view>

        <view v-if="log.answer" class="answer">
          <text class="a-text">{{ log.answer }}</text>
        </view>

        <view v-if="log.toolsUsed" class="tools">
          <text class="tools-text">调用工具：{{ log.toolsUsed }}</text>
        </view>

        <view class="item-foot">
          <text class="foot-text">{{ log.model || '-' }}</text>
          <text class="foot-text">{{ shortTime(log.createTime) }}</text>
        </view>

        <view v-if="log.degraded" class="degraded-tip">
          <text class="degraded-text">本次未取到模型回复，展示的是本地兜底文案</text>
        </view>
      </view>
    </view>

    <view v-if="!loading && !list.length" class="empty">暂无 AI 调用记录</view>
    <view v-if="loading" class="loading-more">加载中…</view>
    <view v-else-if="list.length && !hasMore" class="loading-more">没有更多了</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { pageAiLogs } from '../../api/ai'
import { shortTime } from '../../utils/format'

const PAGE_SIZE = 15

/** 后端 ai_log.log_type 的取值 → 中文标签 */
const LOG_TYPES = {
  REPLENISH_ANALYZE: '补货分析',
  REPLENISH: '补货分析',
  CHAT: '智能问答'
}

function logTypeText(type) {
  if (!type) {
    return 'AI 调用'
  }
  return LOG_TYPES[type] || type
}

const list = ref([])
const loading = ref(false)
const hasMore = ref(true)

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
    const res = await pageAiLogs({ page, size: PAGE_SIZE })
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

load(true)

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

.badge {
  padding: 5rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;

  &.normal {
    background: #ede9ff;
    color: #6d4aff;
  }

  &.degraded {
    background: $erp-warning-soft;
    color: $erp-warning;
  }
}

.cost {
  font-size: 22rpx;
  color: $erp-text-muted;
}

.question {
  display: flex;
  margin-top: 18rpx;
}

.q-label {
  flex-shrink: 0;
  width: 36rpx;
  height: 36rpx;
  line-height: 36rpx;
  text-align: center;
  border-radius: 10rpx;
  background: $erp-primary-soft;
  color: $erp-primary;
  font-size: 21rpx;
  font-weight: 700;
  margin-right: 14rpx;
}

.q-text {
  flex: 1;
  font-size: 27rpx;
  color: $erp-text;
  line-height: 1.5;
}

.answer {
  margin-top: 16rpx;
  padding: 20rpx 22rpx;
  border-radius: 14rpx;
  background: #fafbfe;
}

.a-text {
  font-size: 26rpx;
  color: $erp-text-sub;
  line-height: 1.6;
}

.tools {
  margin-top: 14rpx;
}

.tools-text {
  font-size: 22rpx;
  color: #6d4aff;
}

.item-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
  padding-top: 16rpx;
  border-top: 2rpx solid $erp-border;
}

.foot-text {
  font-size: 22rpx;
  color: $erp-text-muted;
}

.degraded-tip {
  margin-top: 14rpx;
  padding: 14rpx 18rpx;
  border-radius: 12rpx;
  background: $erp-warning-soft;
}

.degraded-text {
  font-size: 22rpx;
  color: $erp-warning;
}

.loading-more {
  padding: 28rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: $erp-text-muted;
}
</style>
