<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        AI 调用记录
        <span class="sub">补货分析与智能问答共用一个列表，便于查看用量与效果</span>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="card">
      <div class="search-bar">
        <el-select v-model="query.logType" placeholder="全部类型" clearable style="width: 150px">
          <el-option label="补货分析" value="REPLENISH" />
          <el-option label="智能问答" value="CHAT" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="标题 / 提问内容"
          clearable
          style="width: 220px"
          @keyup.enter="search"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe border>
        <el-table-column prop="createTime" label="调用时间" width="155" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.logType === 'CHAT' ? 'primary' : 'success'" size="small" effect="light">
              {{ row.logType === 'CHAT' ? '智能问答' : '补货分析' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="170" show-overflow-tooltip />
        <el-table-column prop="question" label="提问 / 提示词" min-width="220" show-overflow-tooltip />
        <el-table-column prop="model" label="模型" width="110" align="center">
          <template #default="{ row }">
            <span class="text-muted">{{ dash(row.model) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90" align="right">
          <template #default="{ row }">{{ num(row.costMs) }} ms</template>
        </el-table-column>
        <el-table-column label="Token" width="130" align="right">
          <template #default="{ row }">
            <span class="text-muted">
              {{ num(row.promptTokens) }} / {{ num(row.completionTokens) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="90" align="center" />
        <el-table-column label="回答" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button link type="primary" @click="showAnswer(row)">查看回答</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="load"
          @current-change="load"
        />
      </div>
    </div>

    <el-dialog v-model="answerVisible" title="模型回答" width="680px" top="8vh">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="标题">{{ dash(current.title) }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ dash(current.model) }}</el-descriptions-item>
        <el-descriptions-item label="提问" :span="2">{{ dash(current.question) }}</el-descriptions-item>
      </el-descriptions>
      <div class="answer">{{ current.answer || '（无回答内容）' }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import { pageAiLogs } from '@/api/ai'
import { dash, num } from '@/utils/format'

const loading = ref(false)
const rows = ref([])
const total = ref(0)

const query = reactive({ logType: null, keyword: '', page: 1, size: 10 })

async function load() {
  loading.value = true
  try {
    const res = await pageAiLogs(query)
    rows.value = (res && res.records) || []
    total.value = (res && res.total) || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  Object.assign(query, { logType: null, keyword: '', page: 1, size: 10 })
  load()
}

const answerVisible = ref(false)
const current = ref({})

function showAnswer(row) {
  current.value = row
  answerVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.answer {
  white-space: pre-wrap;
  line-height: 1.9;
  font-size: 14px;
  color: #1f2d3d;
  max-height: 50vh;
  overflow-y: auto;
  background: #fafbfc;
  border-radius: 6px;
  padding: 14px;
}
</style>
