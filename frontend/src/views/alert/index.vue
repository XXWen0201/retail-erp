<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        库存预警
        <span class="sub">商品维度：断货 / 低于下限 / 超储；批次维度：临期 / 过期</span>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Search" :loading="scanning" @click="onScan">
          立即扫描
        </el-button>
      </div>
    </div>

    <!-- 各类型未处理数量，点一下直接筛选 -->
    <div class="stat-row">
      <div
        v-for="t in typeCards"
        :key="t.key"
        class="stat-card clickable"
        :class="{ active: query.alertType === t.key }"
        @click="toggleType(t.key)"
      >
        <div class="label">{{ t.label }}</div>
        <div class="value" :class="t.value > 0 ? t.cls : ''">{{ t.value }}</div>
        <div class="foot">{{ query.alertType === t.key ? '已筛选，点击取消' : '点击筛选' }}</div>
      </div>
    </div>

    <div class="card">
      <div class="search-bar">
        <el-select v-model="query.alertType" placeholder="全部类型" clearable style="width: 160px">
          <el-option v-for="(v, k) in ALERT_TYPE" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-select v-model="query.alertLevel" placeholder="全部级别" clearable style="width: 130px">
          <el-option label="紧急" value="DANGER" />
          <el-option label="提醒" value="WARN" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option v-for="(v, k) in ALERT_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="商品名称"
          clearable
          style="width: 180px"
          @keyup.enter="search"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="RefreshLeft" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe border>
        <el-table-column label="级别" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.alertLevel === 'DANGER' ? 'danger' : 'warning'" size="small" effect="dark">
              {{ row.alertLevel === 'DANGER' ? '紧急' : '提醒' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预警类型" width="140">
          <template #default="{ row }">
            {{ row.alertTypeLabel || (ALERT_TYPE[row.alertType] || {}).label || row.alertType }}
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="商品" min-width="190" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批次号" width="150">
          <template #default="{ row }">{{ dash(row.batchNo) }}</template>
        </el-table-column>
        <el-table-column label="当前值" width="100" align="right">
          <template #default="{ row }">
            <span class="text-danger money">{{ num(row.currentValue) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="阈值" width="100" align="right">
          <template #default="{ row }">
            <span class="text-muted">{{ num(row.thresholdValue) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="expireDate" label="到期日期" width="115" align="center">
          <template #default="{ row }">{{ dash(row.expireDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">
              {{ (ALERT_STATUS[row.status] || {}).label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="发现时间" width="155" />
        <el-table-column label="处理说明" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ dash(row.handleRemark) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'UNHANDLED'">
              <el-button link type="primary" @click="openHandle(row)">处理</el-button>
              <el-button link type="info" @click="onIgnore(row)">忽略</el-button>
            </template>
            <span v-else class="text-muted">已归档</span>
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

    <el-dialog v-model="handleVisible" title="处理预警" width="480px" destroy-on-close>
      <el-descriptions :column="1" border size="small" style="margin-bottom: 14px">
        <el-descriptions-item label="预警内容">
          {{ current.productName }} ·
          {{ current.alertTypeLabel || (ALERT_TYPE[current.alertType] || {}).label }}
        </el-descriptions-item>
        <el-descriptions-item label="当前值 / 阈值">
          {{ num(current.currentValue) }} / {{ num(current.thresholdValue) }}
        </el-descriptions-item>
      </el-descriptions>
      <el-input
        v-model="handleRemark"
        type="textarea"
        :rows="3"
        placeholder="填写处理方式，如：已向长沙晨光文具下单补货 60 支，预计 3 天到货"
      />
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="onHandle">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import {
  alertSummary,
  handleAlert,
  ignoreAlert,
  pageAlerts,
  scanAlerts
} from '@/api/alert'
import { ALERT_STATUS, ALERT_TYPE, dash, num } from '@/utils/format'

const loading = ref(false)
const scanning = ref(false)
const handling = ref(false)
const rows = ref([])
const total = ref(0)
const summary = ref({})

const query = reactive({
  alertType: null,
  alertLevel: null,
  status: 'UNHANDLED',
  keyword: '',
  page: 1,
  size: 10
})

const statusTag = (s) => (ALERT_STATUS[s] || {}).type || 'info'

const typeCards = computed(() => [
  { key: 'LOW_STOCK', label: '低于库存下限', value: summary.value.lowStockCount || 0, cls: 'text-warn' },
  { key: 'OUT_OF_STOCK', label: '库存断货', value: summary.value.outOfStockCount || 0, cls: 'text-danger' },
  { key: 'OVER_STOCK', label: '高于库存上限', value: summary.value.overStockCount || 0, cls: 'text-warn' },
  { key: 'NEAR_EXPIRY', label: '批次临期', value: summary.value.nearExpiryCount || 0, cls: 'text-warn' },
  { key: 'EXPIRED', label: '批次过期', value: summary.value.expiredCount || 0, cls: 'text-danger' }
])

async function loadSummary() {
  try {
    summary.value = (await alertSummary()) || {}
  } catch {
    /* 静默 */
  }
}

async function load() {
  loading.value = true
  try {
    const res = await pageAlerts(query)
    rows.value = (res && res.records) || []
    total.value = (res && res.total) || 0
  } finally {
    loading.value = false
  }
}

function toggleType(key) {
  query.alertType = query.alertType === key ? null : key
  search()
}

function search() {
  query.page = 1
  load()
}

function reset() {
  Object.assign(query, {
    alertType: null,
    alertLevel: null,
    status: 'UNHANDLED',
    keyword: '',
    page: 1,
    size: 10
  })
  load()
}

async function onScan() {
  scanning.value = true
  try {
    const res = await scanAlerts()
    ElMessage.success(
      `扫描完成：新增 ${res?.createdCount ?? 0} 条，更新 ${res?.updatedCount ?? 0} 条，` +
        `自动关闭 ${res?.closedCount ?? 0} 条`
    )
    loadSummary()
    load()
  } finally {
    scanning.value = false
  }
}

/* ---------------- 处理 / 忽略 ---------------- */
const handleVisible = ref(false)
const handleRemark = ref('')
const current = ref({})

function openHandle(row) {
  current.value = row
  handleRemark.value = ''
  handleVisible.value = true
}

async function onHandle() {
  handling.value = true
  try {
    await handleAlert(current.value.id, handleRemark.value || '已处理')
    ElMessage.success('已标记为处理完成')
    handleVisible.value = false
    loadSummary()
    load()
  } finally {
    handling.value = false
  }
}

async function onIgnore(row) {
  await ElMessageBox.confirm('忽略后该条预警不再提醒，确认忽略？', '忽略确认', {
    type: 'warning',
    confirmButtonText: '忽略',
    cancelButtonText: '取消'
  })
  await ignoreAlert(row.id)
  ElMessage.success('已忽略')
  loadSummary()
  load()
}

onMounted(() => {
  loadSummary()
  load()
})
</script>

<style scoped>
.stat-card.clickable {
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
  border: 1px solid transparent;
}

.stat-card.clickable:hover {
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.18);
}

.stat-card.clickable.active {
  border-color: #409eff;
}
</style>
