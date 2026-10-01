<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        采购管理
        <span class="sub">共 {{ total }} 张采购单</span>
      </div>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" @click="$router.push('/purchase/create')">
          新建采购单
        </el-button>
      </div>
    </div>

    <div class="card">
      <div class="search-bar">
        <el-input
          v-model="query.keyword"
          placeholder="采购单号"
          clearable
          style="width: 200px"
          @keyup.enter="search"
        />
        <!-- 后端 /suppliers/options 返回通用的 {value,label} 结构 -->
        <el-select
          v-model="query.supplierId"
          placeholder="全部供应商"
          clearable
          filterable
          style="width: 220px"
        >
          <el-option v-for="s in suppliers" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option v-for="(v, k) in PURCHASE_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="下单开始"
          end-placeholder="下单结束"
          value-format="YYYY-MM-DD"
          style="width: 260px"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe border>
        <el-table-column prop="orderNo" label="采购单号" width="170" />
        <el-table-column prop="supplierName" label="供应商" min-width="200" show-overflow-tooltip />
        <el-table-column label="采购数量" width="100" align="right">
          <template #default="{ row }">{{ num(row.totalQuantity) }}</template>
        </el-table-column>
        <el-table-column label="采购金额" width="120" align="right">
          <template #default="{ row }">
            <span class="money">{{ money(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderDate" label="下单日期" width="115" align="center" />
        <el-table-column label="入库时间" width="150" align="center">
          <template #default="{ row }">{{ shortTime(row.receiveTime) }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="90" align="center" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button
              v-if="canReceive(row)"
              link
              type="success"
              @click="onReceive(row)"
            >
              入库
            </el-button>
            <el-button v-if="canCancel(row)" link type="warning" @click="onCancel(row)">
              作废
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="onDelete(row)">
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

    <!-- 采购单详情 -->
    <el-dialog v-model="detailVisible" title="采购单详情" width="900px" top="6vh">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="采购单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(detail.status)" size="small">
            {{ statusLabel(detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="下单日期">{{ detail.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="入库时间">{{ shortTime(detail.receiveTime) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ dash(detail.operatorName) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ dash(detail.remark) }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items || []" size="small" border style="margin-top: 14px">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="productName" label="商品" min-width="180" show-overflow-tooltip />
        <el-table-column label="数量" width="90" align="right">
          <template #default="{ row }">{{ num(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="单价" width="90" align="right">
          <template #default="{ row }">{{ money(row.price) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span class="money">{{ money(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次号" width="150" />
        <el-table-column prop="productionDate" label="生产日期" width="110" align="center" />
        <el-table-column prop="expireDate" label="到期日期" width="110" align="center" />
      </el-table>

      <div class="detail-total">
        合计数量 <b>{{ num(detail.totalQuantity) }}</b> ，合计金额
        <b class="money">{{ money(detail.totalAmount) }}</b> 元
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  cancelPurchase,
  deletePurchase,
  getPurchase,
  pagePurchases,
  receivePurchase
} from '@/api/purchase'
import { supplierOptions } from '@/api/supplier'
import { PURCHASE_STATUS, dash, money, num, shortTime } from '@/utils/format'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const suppliers = ref([])
const dateRange = ref([])

const query = reactive({
  keyword: '',
  supplierId: null,
  status: null,
  page: 1,
  size: 10
})

const statusTag = (s) => (PURCHASE_STATUS[s] || {}).type || 'info'
const statusLabel = (s) => (PURCHASE_STATUS[s] || {}).label || s || '-'

const canReceive = (row) => row.status === 'DRAFT' || row.status === 'PENDING'
const canCancel = (row) => row.status === 'DRAFT' || row.status === 'PENDING'

watch(dateRange, (val) => {
  query.startDate = val && val[0] ? val[0] : undefined
  query.endDate = val && val[1] ? val[1] : undefined
})

async function load() {
  loading.value = true
  try {
    const res = await pagePurchases(query)
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
    supplierId: null,
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
  detail.value = (await getPurchase(row.id)) || {}
  detailVisible.value = true
}

async function onReceive(row) {
  await ElMessageBox.confirm(
    `确认将采购单「${row.orderNo}」入库？\n\n入库后会按明细生成批次、增加库存并写入库存流水。`,
    '入库确认',
    { type: 'warning', confirmButtonText: '确认入库', cancelButtonText: '取消' }
  )
  await receivePurchase(row.id)
  ElMessage.success('入库成功，库存与批次已更新')
  load()
}

async function onCancel(row) {
  await ElMessageBox.confirm(
    `确认作废采购单「${row.orderNo}」？作废后不可再入库。`,
    '作废确认',
    { type: 'warning', confirmButtonText: '作废', cancelButtonText: '取消' }
  )
  await cancelPurchase(row.id)
  ElMessage.success('已作废')
  load()
}

async function onDelete(row) {
  await ElMessageBox.confirm(`确定删除草稿「${row.orderNo}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deletePurchase(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  try {
    suppliers.value = (await supplierOptions()) || []
  } catch {
    /* 拦截器已提示 */
  }
  load()
})
</script>

<style scoped>
.detail-total {
  margin-top: 14px;
  text-align: right;
  font-size: 14px;
  color: #606266;
}

.detail-total b {
  color: #f56c6c;
  font-size: 16px;
  margin: 0 4px;
}
</style>
