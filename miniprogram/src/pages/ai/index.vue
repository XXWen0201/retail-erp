<template>
  <view class="page">
    <!-- ---------- 顶部切换 ---------- -->
    <view class="tabs">
      <view class="tab" :class="{ active: tab === 'replenish' }" @click="tab = 'replenish'">
        智能补货建议
      </view>
      <view class="tab" :class="{ active: tab === 'chat' }" @click="tab = 'chat'">
        库存智能问答
      </view>
    </view>

    <!-- ================= 补货建议 ================= -->
    <view v-if="tab === 'replenish'" class="pane">
      <view class="algo-bar">
        <text class="algo-text">{{ algorithm }}</text>
      </view>

      <view class="list">
        <view v-for="it in suggestions" :key="it.productId" class="item">
          <view class="item-head">
            <view class="badge" :class="'badge-' + urgency(it.urgency).type">
              {{ urgency(it.urgency).text }}
            </view>
            <text class="item-name ellipsis">{{ it.productName }}</text>
          </view>

          <view class="metrics">
            <view class="metric">
              <text class="metric-value">{{ fmtQty(it.currentStock) }}</text>
              <text class="metric-label">当前库存</text>
            </view>
            <view class="metric">
              <text class="metric-value">{{ fmtQty(it.reorderPoint) }}</text>
              <text class="metric-label">补货点</text>
            </view>
            <view class="metric">
              <text class="metric-value primary">{{ fmtQty(it.suggestQuantity) }}</text>
              <text class="metric-label">建议补货</text>
            </view>
            <view class="metric">
              <text class="metric-value">{{ fmtQty(it.avgDailySales) }}</text>
              <text class="metric-label">日均销量</text>
            </view>
          </view>

          <view class="reason">
            <text class="reason-text">{{ it.reason }}</text>
          </view>

          <view class="item-foot">
            <text class="cost-text">预计采购成本 ￥{{ money(it.estCost) }}</text>
            <view class="analyze-btn" @click="analyze(it)">
              {{ analyzingId === it.productId ? '分析中…' : 'AI 分析' }}
            </view>
          </view>
        </view>
      </view>

      <view v-if="!loadingSuggestions && !suggestions.length" class="empty">
        当前没有需要补货的商品
      </view>
      <view v-if="loadingSuggestions" class="loading-more">加载中…</view>
    </view>

    <!-- ================= 智能问答 ================= -->
    <view v-else class="pane chat-pane">
      <scroll-view class="chat-body" scroll-y :scroll-into-view="scrollTarget">
        <view v-if="!messages.length" class="chat-intro">
          <text class="intro-title">可以这样问我</text>
          <view class="intro-list">
            <view
              v-for="q in quickQuestions"
              :key="q"
              class="intro-item"
              @click="askQuick(q)"
            >
              <text class="intro-text">{{ q }}</text>
            </view>
          </view>
          <text class="intro-tip">
            模型会自己判断要不要调用库存查询工具，答案里的数字都来自你的真实数据。
          </text>
        </view>

        <view
          v-for="(m, idx) in messages"
          :key="idx"
          :id="'msg' + idx"
          class="msg"
          :class="m.role"
        >
          <view class="bubble" :class="m.role">
            <text class="bubble-text">{{ m.content }}</text>
            <view v-if="m.tools && m.tools.length" class="tools">
              <text class="tools-text">已查询：{{ m.tools.join('、') }}</text>
            </view>
            <view v-if="m.degraded" class="degraded">
              <text class="degraded-text">AI 暂不可用，以上为本地兜底回复</text>
            </view>
          </view>
        </view>

        <view v-if="sending" class="msg assistant">
          <view class="bubble assistant">
            <text class="bubble-text">正在思考…</text>
          </view>
        </view>

        <view id="chat-bottom" class="chat-bottom-space"></view>
      </scroll-view>

      <view class="chat-bar">
        <input
          class="chat-input"
          v-model="question"
          placeholder="问点什么，比如「哪些商品快过期了」"
          placeholder-class="ph"
          confirm-type="send"
          :disabled="sending"
          @confirm="send"
        />
        <view class="send-btn" :class="{ disabled: sending }" @click="send">发送</view>
      </view>
    </view>

    <!-- ---------- AI 分析弹层 ---------- -->
    <view v-if="analysisVisible" class="mask" @click="analysisVisible = false">
      <view class="dialog" @click.stop>
        <text class="dialog-title">{{ analysisTitle }}</text>
        <text class="dialog-sub">{{ analysisModel }}</text>
        <scroll-view class="dialog-body" scroll-y>
          <text class="dialog-text">{{ analysisText }}</text>
        </scroll-view>
        <view class="dialog-actions">
          <view class="dialog-btn primary" @click="analysisVisible = false">知道了</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { aiChat, analyzeReplenish, replenishSuggestions } from '../../api/ai'
import { money, quantity as fmtQty, urgency } from '../../utils/format'

const tab = ref('replenish')

// ---------- 补货建议 ----------
const suggestions = ref([])
const algorithm = ref('本地算法计算，不依赖外部模型')
const loadingSuggestions = ref(false)
const analyzingId = ref(null)

// ---------- 问答 ----------
const messages = ref([])
const question = ref('')
const sending = ref(false)
const scrollTarget = ref('')

const quickQuestions = [
  '哪些商品快要过期了？',
  '现在有哪些商品库存低于下限？',
  '最近 30 天卖得最好的商品是哪几个？',
  '哪些商品需要尽快补货？'
]

// ---------- 分析弹层 ----------
const analysisVisible = ref(false)
const analysisTitle = ref('')
const analysisText = ref('')
const analysisModel = ref('')

async function loadSuggestions() {
  loadingSuggestions.value = true
  try {
    const res = await replenishSuggestions(30)
    suggestions.value = (res && res.items) || []
    if (res && res.algorithm) {
      algorithm.value = res.algorithm
    }
  } catch (e) {
    suggestions.value = []
  } finally {
    loadingSuggestions.value = false
  }
}

async function analyze(item) {
  if (analyzingId.value) {
    return
  }
  analyzingId.value = item.productId
  try {
    const res = await analyzeReplenish(item.productId, 30)
    analysisTitle.value = (res && res.productName) || item.productName
    analysisText.value = (res && res.analysis) || '没有取得分析内容'
    analysisModel.value = res && res.degraded
      ? 'AI 暂不可用，以上为本地规则生成的兜底说明'
      : '模型：' + ((res && res.model) || 'qwen-plus')
    analysisVisible.value = true
  } catch (e) {
    // 请求层已提示
  } finally {
    analyzingId.value = null
  }
}

// ---------- 问答 ----------

function pushMessage(role, content, extra = {}) {
  messages.value.push({ role, content, ...extra })
  // 等渲染完成再滚到底，否则新气泡还没进 DOM，滚不到底
  setTimeout(() => {
    scrollTarget.value = 'chat-bottom'
  }, 60)
}

async function ask(text) {
  const q = String(text || '').trim()
  if (!q || sending.value) {
    return
  }

  pushMessage('user', q)
  question.value = ''
  sending.value = true

  try {
    const res = await aiChat(q)
    pushMessage('assistant', (res && res.answer) || '没有得到回答', {
      tools: (res && res.toolsUsed) || [],
      degraded: !!(res && res.degraded)
    })
  } catch (e) {
    pushMessage('assistant', (e && e.message) || 'AI 服务暂时不可用，请稍后再试', {
      degraded: true
    })
  } finally {
    sending.value = false
  }
}

function send() {
  ask(question.value)
}

function askQuick(q) {
  ask(q)
}

onMounted(() => {
  loadSuggestions()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ---------- 顶部切换 ---------- */
.tabs {
  display: flex;
  padding: 20rpx 24rpx;
  background: #ffffff;
}

.tab {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  font-size: 27rpx;
  color: $erp-text-sub;
  background: $erp-bg;
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

.pane {
  flex: 1;
  min-height: 0;
}

/* ================= 补货建议 ================= */
.algo-bar {
  padding: 22rpx 30rpx 6rpx;
}

.algo-text {
  font-size: 22rpx;
  color: $erp-text-muted;
}

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
}

.badge {
  flex-shrink: 0;
  padding: 5rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  margin-right: 14rpx;
}

.badge-danger {
  background: $erp-danger-soft;
  color: $erp-danger;
}
.badge-warning {
  background: $erp-warning-soft;
  color: $erp-warning;
}
.badge-success {
  background: $erp-success-soft;
  color: $erp-success;
}
.badge-info {
  background: #eef0f4;
  color: $erp-text-sub;
}

.item-name {
  flex: 1;
  font-size: 30rpx;
  font-weight: 600;
  color: $erp-text;
}

.metrics {
  display: flex;
  margin-top: 22rpx;
}

.metric {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.metric-value {
  font-size: 32rpx;
  font-weight: 700;
  color: $erp-text;

  &.primary {
    color: $erp-primary;
  }
}

.metric-label {
  margin-top: 6rpx;
  font-size: 21rpx;
  color: $erp-text-muted;
}

.reason {
  margin-top: 22rpx;
  padding: 18rpx 22rpx;
  border-radius: 14rpx;
  background: #fafbfe;
}

.reason-text {
  font-size: 24rpx;
  color: $erp-text-sub;
  line-height: 1.55;
}

.item-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 22rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid $erp-border;
}

.cost-text {
  font-size: 23rpx;
  color: $erp-text-muted;
}

.analyze-btn {
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 30rpx;
  border-radius: 32rpx;
  background: #ede9ff;
  color: #6d4aff;
  font-size: 25rpx;
  font-weight: 600;
}

/* ================= 问答 ================= */
.chat-pane {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 112rpx);
}

.chat-body {
  flex: 1;
  min-height: 0;
  padding: 24rpx 24rpx 0;
}

.chat-intro {
  background: $erp-card;
  border-radius: $erp-radius;
  padding: 32rpx 28rpx;
  box-shadow: $erp-shadow;
}

.intro-title {
  display: block;
  font-size: 29rpx;
  font-weight: 600;
  color: $erp-text;
  margin-bottom: 22rpx;
}

.intro-list {
  display: flex;
  flex-direction: column;
}

.intro-item {
  padding: 22rpx 24rpx;
  border-radius: 16rpx;
  background: $erp-primary-soft;

  & + .intro-item {
    margin-top: 14rpx;
  }
}

.intro-text {
  font-size: 26rpx;
  color: $erp-primary;
}

.intro-tip {
  display: block;
  margin-top: 24rpx;
  font-size: 22rpx;
  color: $erp-text-muted;
  line-height: 1.6;
}

.msg {
  display: flex;
  margin-bottom: 22rpx;

  &.user {
    justify-content: flex-end;
  }

  &.assistant {
    justify-content: flex-start;
  }
}

.bubble {
  max-width: 540rpx;
  padding: 22rpx 26rpx;
  border-radius: 20rpx;

  &.user {
    background: linear-gradient(135deg, #4b85ff, #2f6bff);
    border-bottom-right-radius: 6rpx;
  }

  &.assistant {
    background: #ffffff;
    border-bottom-left-radius: 6rpx;
    box-shadow: $erp-shadow;
  }
}

.bubble-text {
  font-size: 27rpx;
  line-height: 1.6;
  word-break: break-all;
}

.bubble.user .bubble-text {
  color: #ffffff;
}

.bubble.assistant .bubble-text {
  color: $erp-text;
}

.tools {
  margin-top: 14rpx;
  padding-top: 12rpx;
  border-top: 2rpx solid $erp-border;
}

.tools-text {
  font-size: 21rpx;
  color: #6d4aff;
}

.degraded {
  margin-top: 12rpx;
}

.degraded-text {
  font-size: 21rpx;
  color: $erp-warning;
}

.chat-bottom-space {
  height: 24rpx;
}

.chat-bar {
  display: flex;
  align-items: center;
  padding: 20rpx 24rpx;
  background: #ffffff;
  border-top: 2rpx solid $erp-border;
}

.chat-input {
  flex: 1;
  height: 80rpx;
  padding: 0 28rpx;
  border-radius: 40rpx;
  background: $erp-bg;
  font-size: 27rpx;
  color: $erp-text;
}

.ph {
  color: #b6bfcc;
  font-size: 25rpx;
}

.send-btn {
  margin-left: 18rpx;
  height: 80rpx;
  line-height: 80rpx;
  padding: 0 40rpx;
  border-radius: 40rpx;
  background: linear-gradient(135deg, #4b85ff, #2f6bff);
  color: #ffffff;
  font-size: 27rpx;
  font-weight: 600;

  &.disabled {
    opacity: 0.6;
  }
}

.loading-more {
  padding: 28rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: $erp-text-muted;
}

/* ---------- 分析弹层 ---------- */
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
  padding: 0 48rpx;
}

.dialog {
  width: 100%;
  max-height: 70vh;
  background: #ffffff;
  border-radius: 28rpx;
  padding: 40rpx 36rpx 32rpx;
  display: flex;
  flex-direction: column;
}

.dialog-title {
  font-size: 34rpx;
  font-weight: 700;
  color: $erp-text;
}

.dialog-sub {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: $erp-text-muted;
}

.dialog-body {
  flex: 1;
  min-height: 0;
  max-height: 46vh;
  margin-top: 24rpx;
}

.dialog-text {
  font-size: 27rpx;
  color: $erp-text-sub;
  line-height: 1.7;
}

.dialog-actions {
  margin-top: 28rpx;
}

.dialog-btn {
  height: 82rpx;
  line-height: 82rpx;
  text-align: center;
  border-radius: 41rpx;
  font-size: 29rpx;

  &.primary {
    background: linear-gradient(135deg, #4b85ff, #2f6bff);
    color: #ffffff;
    font-weight: 600;
  }
}
</style>
