<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        销售管理
        <span class="sub">共 {{ total }} 张销售单</span>
      </div>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" @click="$router.push('/sale/create')">
          新建销售单
        </el-button>
      </div>
    </div>

    <div class="card">
      <div class="search-bar">
        <el-input
          v-model="query.keyword"
          placeholder="单号 / 客户"
          clearable
          style="width: 200px"
          @keyup.enter="search"
        />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option v-for="(v, k) in SALE_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe border>
        <el-table-column prop="orderNo" label="销售单号" width="170" />
        <el-table-column prop="customerName" label="客户" width="110" show-overflow-tooltip />
        <el-table-column label="数量" width="80" align="right">
          <template #default="{ row }">{{ num(row.totalQuantity) }}</template>
        </el-table-column>
        <el-table-column label="销售金额" width="110" align="right">
          <template #default="{ row }">
            <span class="money">{{ money(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="优惠" width="90" align="right">
          <template #default="{ row }">
            <span class="text-muted">{{ money(row.discountAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="实收" width="110" align="right">
          <template #default="{ row }">
            <span class="money">{{ money(row.payAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成本" width="110" align="right">
          <template #default="{ row }">
            <span class="text-muted">{{ money(row.totalCost) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="毛利" width="110" align="right">
          <template #default="{ row }">
            <span class="money text-ok">{{ money(row.grossProfit) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderDate" label="销售日期" width="115" align="center" />
        <el-table-column prop="operatorName" label="操作人" width="90" align="center" />
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'FINISHED'" link type="warning" @click="onCancel(row)">
              作废
            </el-button>
            <el-button v-if="row.status !== 'FINISHED'" link type="danger" @click="onDelete(row)">
              删除
            </el-button>
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

    <el-dialog v-model="detailVisible" title="销售单详情" width="920px" top="6vh">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="销售单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail.status)" size="small">
            {{ statusLabel(detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="销售日期">{{ detail.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ dash(detail.operatorName) }}</el-descriptions-item>
        <el-descriptions-item label="优惠金额">{{ money(detail.discountAmount) }}</el-descriptions-item>
        <el-descriptions-item label="实收金额">{{ money(detail.payAmount) }}</el-descriptions-item>
        <el-descriptions-item label="毛利">
          <span class="text-ok money">{{ money(detail.grossProfit) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="4">{{ dash(detail.remark) }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items || []" size="small" border style="margin-top: 14px">
        <el-table-column prop="productName" label="商品" min-width="180" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="出库批次" width="150" />
        <el-table-column label="数量" width="80" align="right">
          <template #default="{ row }">{{ num(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="销售单价" width="100" align="right">
          <template #default="{ row }">{{ money(row.price) }}</template>
        </el-table-column>
        <el-table-column label="销售金额" width="110" align="right">
          <template #default="{ row }">
            <span class="money">{{ money(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成本单价" width="100" align="right">
          <template #default="{ row }">
            <span class="text-muted">{{ num(row.costPrice, 4) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="成本金额" width="110" align="right">
          <template #default="{ row }">
            <span class="text-muted">{{ money(row.costAmount) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { cancelSale, deleteSale, getSale, pageSales } from '@/api/sale'
import { SALE_STATUS, dash, money, num } from '@/utils/format'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const dateRange = ref([])

const query = reactive({ keyword: '', status: null, page: 1, size: 10 })

const statusTag = (s) => (SALE_STATUS[s] || {}).type || 'info'
const statusLabel = (s) => (SALE_STATUS[s] || {}).label || s || '-'

watch(dateRange, (val) => {
  query.startDate = val && val[0] ? val[0] : undefined
  query.endDate = val && val[1] ? val[1] : undefined
})

async function load() {
  loading.value = true
  try {
    const res = await pageSales(query)
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
  dateRange.value = []
  Object.assign(query, {
    keyword: '',
    status: null,
    startDate: undefined,
    endDate: undefined,
    page: 1,
    size: 10
  })
  load()
}

const detailVisible = ref(false)
const detail = ref({})

async function showDetail(row) {
  detail.value = (await getSale(row.id)) || {}
  detailVisible.value = true
}

async function onCancel(row) {
  await ElMessageBox.confirm(
    `确认作废销售单「${row.orderNo}」？\n\n作废后会把已扣减的批次库存原路退回。`,
    '作废确认',
    { type: 'warning', confirmButtonText: '确认作废', cancelButtonText: '取消' }
  )
  await cancelSale(row.id)
  ElMessage.success('已作废，库存已回滚')
  load()
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除销售单「${row.orderNo}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteSale(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
