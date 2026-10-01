<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div class="page-title">
        盘点单详情
        <span class="sub">{{ detail.checkNo }}</span>
      </div>
      <div class="page-actions">
        <el-button :icon="Back" @click="$router.push('/check')">返回列表</el-button>
        <template v-if="isDraft">
          <el-button :icon="DocumentAdd" :loading="savingItems" @click="saveItems">
            保存实盘数
          </el-button>
          <el-button type="primary" :icon="Select" :loading="finishing" @click="onFinish">
            提交盘点
          </el-button>
        </template>
      </div>
    </div>

    <div class="card">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="盘点单号">{{ detail.checkNo }}</el-descriptions-item>
        <el-descriptions-item label="盘点日期">{{ detail.checkDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag" size="small">{{ statusLabel }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作人">{{ dash(detail.operatorName) }}</el-descriptions-item>
        <el-descriptions-item label="差异商品数">
          <span :class="diffItemCount > 0 ? 'text-warn' : 'text-muted'">{{ diffItemCount }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="差异数量合计">
          <span :class="Math.abs(diffQuantityTotal) > 0 ? 'text-warn' : 'text-muted'">
            {{ diffQuantityTotal }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ shortTime(detail.finishTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ dash(detail.remark) }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="isDraft"
        type="info"
        :closable="false"
        show-icon
        style="margin: 14px 0 0"
      >
        <template #default>
          只填<b>实盘数与账面数不一致</b>的行即可，没填的行按「无差异」处理。
          提交后系统按「实盘 − 账面」自动生成盘盈入库或盘亏出库流水。
        </template>
      </el-alert>
      <el-alert
        v-else
        type="success"
        :closable="false"
        show-icon
        style="margin: 14px 0 0"
        title="该盘点单已提交，明细只读。差异已写入库存流水，可在「库存流水」中按盘点单号追溯。"
      />
    </div>

    <div class="card">
      <div class="card-head">
        <span>盘点明细（共 {{ (detail.items || []).length }} 行）</span>
        <el-input
          v-if="isDraft"
          v-model="keyword"
          placeholder="按商品名筛选"
          clearable
          size="small"
          style="width: 200px"
        />
      </div>

      <el-table :data="filteredItems" border size="small" max-height="560">
        <el-table-column type="index" label="#" width="46" align="center" />
        <el-table-column prop="productName" label="商品" min-width="200" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批次" width="145">
          <template #default="{ row }">{{ dash(row.batchNo) }}</template>
        </el-table-column>
        <el-table-column label="账面数" width="95" align="right">
          <template #default="{ row }">
            <span class="text-muted">{{ num(row.bookQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="实盘数" width="140">
          <template #default="{ row }">
            <el-input-number
              v-if="isDraft"
              v-model="row.actualQuantity"
              :min="0"
              :step="1"
              size="small"
              controls-position="right"
              style="width: 100%"
            />
            <span v-else class="money">{{ num(row.actualQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差异" width="100" align="right">
          <template #default="{ row }">
            <span :class="diffClass(row)">{{ diffText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="差异原因 / 处理说明" min-width="220">
          <template #default="{ row }">
            <el-input
              v-if="isDraft"
              v-model="row.reason"
              size="small"
              placeholder="如 货架串位 / 破损报废 / 记错数量"
            />
            <span v-else>{{ dash(row.reason) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, DocumentAdd, Select } from '@element-plus/icons-vue'
import {
  finishStockCheck,
  getStockCheck,
  updateCheckItems
} from '@/api/stockCheck'
import { dash, num, shortTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const savingItems = ref(false)
const finishing = ref(false)
const keyword = ref('')
const detail = ref({ items: [] })

const isDraft = computed(() => detail.value.status === 'DRAFT')

const CHECK_STATUS = {
  DRAFT: { label: '盘点中', type: 'warning' },
  FINISHED: { label: '已完成', type: 'success' },
  CANCELED: { label: '已作废', type: 'info' }
}
const statusLabel = computed(() => (CHECK_STATUS[detail.value.status] || {}).label || '-')
const statusTag = computed(() => (CHECK_STATUS[detail.value.status] || {}).type || 'info')

const filteredItems = computed(() => {
  const items = detail.value.items || []
  if (!keyword.value) return items
  const kw = keyword.value.trim().toLowerCase()
  return items.filter((r) => String(r.productName || '').toLowerCase().includes(kw))
})

function diffOf(row) {
  if (row.actualQuantity === null || row.actualQuantity === undefined) return 0
  return Number(row.actualQuantity) - Number(row.bookQuantity || 0)
}

function diffText(row) {
  const d = diffOf(row)
  if (d > 0) return `+${num(d)}`
  if (d < 0) return num(d)
  return '0'
}

function diffClass(row) {
  const d = diffOf(row)
  if (d > 0) return 'text-ok money'
  if (d < 0) return 'text-danger money'
  return 'text-muted money'
}

const diffItemCount = computed(
  () => (detail.value.items || []).filter((r) => diffOf(r) !== 0).length
)
const diffQuantityTotal = computed(() =>
  (detail.value.items || []).reduce((s, r) => s + diffOf(r), 0)
)

async function load() {
  loading.value = true
  try {
    const data = await getStockCheck(route.params.id)
    detail.value = data || { items: [] }
    // 未录入实盘数的行显示为 null，交给用户填
    ;(detail.value.items || []).forEach((r) => {
      if (r.actualQuantity === undefined) r.actualQuantity = null
    })
  } finally {
    loading.value = false
  }
}

async function saveItems() {
  const items = (detail.value.items || [])
    .filter((r) => r.actualQuantity !== null && r.actualQuantity !== undefined)
    .map((r) => ({
      id: r.id,
      actualQuantity: Number(r.actualQuantity),
      reason: r.reason || undefined
    }))

  if (!items.length) {
    ElMessage.warning('还没有填写任何实盘数')
    return
  }

  savingItems.value = true
  try {
    await updateCheckItems(detail.value.id, { items })
    ElMessage.success(`已保存 ${items.length} 行实盘数`)
    load()
  } finally {
    savingItems.value = false
  }
}

async function onFinish() {
  const diffCount = diffItemCount.value
  const diffQty = diffQuantityTotal.value

  await ElMessageBox.confirm(
    `确认提交盘点单「${detail.value.checkNo}」？\n\n` +
      `差异商品：${diffCount} 种\n差异数量合计：${diffQty > 0 ? '+' : ''}${diffQty}\n\n` +
      '提交后会立即调整库存并生成盘盈 / 盘亏流水，之后不能再修改。',
    '提交盘点',
    { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '再检查一下' }
  )

  finishing.value = true
  try {
    await finishStockCheck(detail.value.id)
    ElMessage.success('盘点已提交，库存已按实盘数调整')
    load()
  } finally {
    finishing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 12px;
}
</style>
