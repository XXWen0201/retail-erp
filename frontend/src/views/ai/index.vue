<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        AI 智能助手
        <span class="sub">补货量由本地算法算出，AI 负责解释与问答 —— 断网也不会给出假数据</span>
      </div>
      <div class="page-actions">
        <el-select v-model="days" style="width: 130px" @change="loadSuggestions">
          <el-option label="近 7 天" :value="7" />
          <el-option label="近 30 天" :value="30" />
          <el-option label="近 90 天" :value="90" />
        </el-select>
        <el-button :icon="Refresh" :loading="loading" @click="loadSuggestions">重新计算</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="ai-tabs">
      <!-- ============ 补货建议 ============ -->
      <el-tab-pane label="智能补货建议" name="replenish">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px">
          <template #title>
            算法：{{ meta.algorithm || '移动平均 + 安全库存' }} ｜ 生成时间：{{ meta.generatedAt || '-' }}
          </template>
          <template #default>
            补货点 = 日均销量 × 到货周期 + 安全库存；建议补货量 = 补货点 × 2 − 当前库存（向上取整）。
            下方紧急度由「当前库存可支撑天数」判定。
          </template>
        </el-alert>

        <div class="stat-row">
          <div class="stat-card">
            <div class="label">需要补货的商品</div>
            <div class="value text-warn">{{ suggestions.length }}</div>
            <div class="foot">低于补货点的商品数</div>
          </div>
          <div class="stat-card">
            <div class="label">其中紧急</div>
            <div class="value text-danger">{{ urgentCount }}</div>
            <div class="foot">可支撑不足 3 天或已断货</div>
          </div>
          <div class="stat-card">
            <div class="label">建议采购总成本</div>
            <div class="value">{{ money(totalCost) }}<small>元</small></div>
            <div class="foot">按当前进货价估算</div>
          </div>
        </div>

        <div class="card">
          <el-table v-loading="loading" :data="suggestions" stripe border>
            <el-table-column label="紧急度" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="urgencyTag(row.urgency)" size="small" effect="dark">
                  {{ urgencyLabel(row.urgency) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="productName" label="商品" min-width="180" show-overflow-tooltip />
            <el-table-column label="当前库存" width="95" align="right">
              <template #default="{ row }">
                <span class="text-danger money">{{ num(row.currentStock) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="库存上下限" width="110" align="center">
              <template #default="{ row }">
                <span class="text-muted">{{ num(row.stockLower) }} ~ {{ num(row.stockUpper) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="日均销量" width="100" align="right">
              <template #default="{ row }">{{ num(row.avgDailySales, 1) }}</template>
            </el-table-column>
            <el-table-column label="可支撑" width="95" align="right">
              <template #default="{ row }">
                <span :class="row.stockDays < 3 ? 'text-danger' : 'text-warn'">
                  {{ num(row.stockDays, 1) }} 天
                </span>
              </template>
            </el-table-column>
            <el-table-column label="安全库存" width="95" align="right">
              <template #default="{ row }">
                <span class="text-muted">{{ num(row.safetyStock) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="补货点" width="90" align="right">
              <template #default="{ row }">
                <span class="text-muted">{{ num(row.reorderPoint) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="建议补货" width="105" align="right">
              <template #default="{ row }">
                <span class="money text-ok">{{ num(row.suggestQuantity) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="预计成本" width="110" align="right">
              <template #default="{ row }">{{ money(row.estCost) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="测算依据" min-width="230" show-overflow-tooltip />
            <el-table-column label="操作" width="110" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openAnalysis(row)">AI 分析</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ============ 库存问答 ============ -->
      <el-tab-pane label="库存智能问答" name="chat">
        <div class="card chat-card">
          <div class="chat-body" ref="chatBodyRef">
            <div v-if="!messages.length" class="chat-empty">
              <el-icon class="chat-empty-icon"><ChatDotRound /></el-icon>
              <p>问点什么吧，比如「哪些商品快过期了」「订书机现在有多少库存」</p>
              <div class="quick">
                <el-tag
                  v-for="q in quickQuestions"
                  :key="q"
                  class="quick-item"
                  effect="plain"
                  @click="ask(q)"
                >
                  {{ q }}
                </el-tag>
              </div>
            </div>

            <div
              v-for="(m, i) in messages"
              :key="i"
              class="msg"
              :class="m.role === 'user' ? 'msg-user' : 'msg-ai'"
            >
              <div class="bubble">
                <div v-if="m.tools && m.tools.length" class="tools">
                  <el-tag
                    v-for="t in m.tools"
                    :key="t"
                    size="small"
                    type="success"
                    effect="plain"
                  >
                    调用工具 {{ t }}
                  </el-tag>
                </div>
                <div class="text" v-html="renderText(m.content)"></div>
                <span v-if="m.streaming" class="cursor">▍</span>
                <div v-if="m.degraded" class="degraded">
                  AI 服务暂不可用，以上为本地规则生成的兜底说明。
                </div>
              </div>
            </div>
          </div>

          <div class="chat-input">
            <el-input
              v-model="question"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="输入问题后按 Ctrl + Enter 发送"
              @keydown.ctrl.enter.prevent="ask()"
            />
            <div class="chat-actions">
              <el-button v-if="streaming" type="danger" plain :icon="Close" @click="stop">
                停止生成
              </el-button>
              <el-button
                type="primary"
                :icon="Promotion"
                :loading="streaming"
                :disabled="!question.trim()"
                @click="ask()"
              >
                发送
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- AI 补货分析抽屉 -->
    <el-drawer v-model="analysisVisible" size="520px" :title="`AI 分析 · ${analysisProduct}`">
      <div v-loading="analyzing" class="analysis-body">
        <div v-if="analysisText" class="analysis-text" v-html="renderText(analysisText)"></div>
        <el-empty v-else description="正在生成分析…" :image-size="80" />
      </div>
      <template #footer>
        <el-tag v-if="analysisModel" size="small" type="info" effect="plain">
          模型：{{ analysisModel }}
        </el-tag>
        <el-tag v-if="analysisDegraded" size="small" type="warning" effect="plain">
          已降级为本地兜底文案
        </el-tag>
        <el-button @click="analysisVisible = false">关闭</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Close, Promotion, Refresh } from '@element-plus/icons-vue'
import {
  aiChatStream,
  analyzeReplenish,
  replenishStream,
  replenishSuggestions
} from '@/api/ai'
import { URGENCY, money, num } from '@/utils/format'

const activeTab = ref('replenish')
const days = ref(30)
const loading = ref(false)
const suggestions = ref([])
const meta = reactive({ algorithm: '', generatedAt: '' })

const urgencyTag = (u) => (URGENCY[u] || {}).type || 'info'
const urgencyLabel = (u) => (URGENCY[u] || {}).label || u || '-'

const urgentCount = computed(() => suggestions.value.filter((r) => r.urgency === 'HIGH').length)
const totalCost = computed(() =>
  suggestions.value.reduce((s, r) => s + Number(r.estCost || 0), 0)
)

async function loadSuggestions() {
  loading.value = true
  try {
    const data = await replenishSuggestions({ days: days.value })
    suggestions.value = (data && data.items) || []
    meta.algorithm = (data && data.algorithm) || ''
    meta.generatedAt = (data && data.generatedAt) || ''
  } finally {
    loading.value = false
  }
}

/* ---------------- AI 补货分析 ---------------- */
const analysisVisible = ref(false)
const analyzing = ref(false)
const analysisText = ref('')
const analysisProduct = ref('')
const analysisModel = ref('')
const analysisDegraded = ref(false)
let analysisAbort = null

async function openAnalysis(row) {
  analysisVisible.value = true
  analyzing.value = true
  analysisText.value = ''
  analysisProduct.value = row.productName
  analysisModel.value = ''
  analysisDegraded.value = false
  analysisAbort = new AbortController()

  try {
    await replenishStream(
      row.productId,
      days.value,
      {
        onMessage: (piece, full) => {
          analysisText.value = full
        }
      },
      analysisAbort.signal
    )
  } catch {
    // 流式失败时退回到一次性接口，它内部还有一层本地兜底
    try {
      const res = await analyzeReplenish(row.productId, days.value)
      analysisText.value = (res && res.analysis) || '暂时无法生成分析。'
      analysisModel.value = (res && res.model) || ''
      analysisDegraded.value = !!(res && res.degraded)
    } catch {
      analysisText.value = 'AI 服务暂时不可用，请稍后重试。'
    }
  } finally {
    analyzing.value = false
  }
}

/* ---------------- 问答 ---------------- */
const quickQuestions = [
  '哪些商品快过期了？',
  '当前有哪些商品低于库存下限？',
  '最近卖得最好的商品是什么？',
  '帮我看看订书机还有多少库存'
]

const messages = ref([])
const question = ref('')
const streaming = ref(false)
const chatBodyRef = ref(null)
let chatAbort = null

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

/** 模型返回的是纯文本，转义后再把换行变成 <br>，避免 XSS */
function renderText(text) {
  return escapeHtml(text || '').replace(/\n/g, '<br/>')
}

async function scrollToBottom() {
  await nextTick()
  const el = chatBodyRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function ask(preset) {
  const q = (preset || question.value || '').trim()
  if (!q) return
  if (streaming.value) {
    ElMessage.warning('正在生成中，请稍候或先停止')
    return
  }

  question.value = ''
  messages.value.push({ role: 'user', content: q })

  const aiMsg = reactive({ role: 'ai', content: '', streaming: true, tools: [], degraded: false })
  messages.value.push(aiMsg)
  scrollToBottom()

  streaming.value = true
  chatAbort = new AbortController()

  try {
    await aiChatStream(
      q,
      {
        onMessage: (piece, full) => {
          aiMsg.content = full
          scrollToBottom()
        }
      },
      chatAbort.signal
    )
    if (!aiMsg.content) {
      aiMsg.content = '没有取到回答，请稍后重试。'
    }
  } catch (e) {
    if (e.name !== 'AbortError') {
      aiMsg.degraded = true
      if (!aiMsg.content) {
        aiMsg.content =
          'AI 服务暂时不可用。\n\n你仍然可以在「智能补货建议」页看到由本地算法算出的补货量与测算依据，那部分不依赖外部模型。'
      }
    }
  } finally {
    aiMsg.streaming = false
    streaming.value = false
    chatAbort = null
    scrollToBottom()
  }
}

function stop() {
  if (chatAbort) chatAbort.abort()
  streaming.value = false
}

onMounted(loadSuggestions)

onBeforeUnmount(() => {
  if (chatAbort) chatAbort.abort()
  if (analysisAbort) analysisAbort.abort()
})
</script>

<style scoped>
.ai-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

:deep(.el-tabs__item) {
  font-size: 14px;
}

.chat-card {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 260px);
  min-height: 420px;
  padding: 0;
  overflow: hidden;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
  background: #fafbfc;
}

.chat-empty {
  text-align: center;
  color: #909399;
  padding-top: 40px;
}

.chat-empty-icon {
  font-size: 42px;
  color: #c0c4cc;
}

.quick {
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.quick-item {
  cursor: pointer;
}

.msg {
  display: flex;
  margin-bottom: 16px;
}

.msg-user {
  justify-content: flex-end;
}

.msg-ai {
  justify-content: flex-start;
}

.bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: 10px;
  line-height: 1.75;
  font-size: 14px;
  position: relative;
  word-break: break-word;
}

.msg-user .bubble {
  background: #409eff;
  color: #fff;
  border-top-right-radius: 2px;
}

.msg-ai .bubble {
  background: #fff;
  color: #303133;
  border: 1px solid #ebeef5;
  border-top-left-radius: 2px;
}

.tools {
  margin-bottom: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.cursor {
  animation: blink 1s steps(2, start) infinite;
  color: #409eff;
}

@keyframes blink {
  to {
    visibility: hidden;
  }
}

.degraded {
  margin-top: 8px;
  font-size: 12px;
  color: #e6a23c;
  background: #fdf6ec;
  border-radius: 4px;
  padding: 5px 8px;
}

.chat-input {
  border-top: 1px solid #ebeef5;
  padding: 12px 14px;
  background: #fff;
}

.chat-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.analysis-body {
  min-height: 200px;
  line-height: 1.9;
  font-size: 14px;
  color: #303133;
  white-space: normal;
}
</style>
