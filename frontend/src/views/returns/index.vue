<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        退货管理
        <span class="sub">共 {{ total }} 张退货单</span>
      </div>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" @click="openForm()">新建退货单</el-button>
      </div>
    </div>

    <div class="card">
      <div class="search-bar">
        <el-select v-model="query.returnType" placeholder="全部类型" clearable style="width: 150px">
          <el-option v-for="(v, k) in RETURN_TYPE" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="退货单号 / 原单号"
          clearable
          style="width: 200px"
          @keyup.enter="search"
        />
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
        <el-table-column prop="orderNo" label="退货单号" width="170" />
        <el-table-column label="退货类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.returnType)" size="small" effect="light">
              {{ row.returnTypeLabel || typeLabel(row.returnType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceOrderNo" label="关联原单" width="170">
          <template #default="{ row }">{{ dash(row.sourceOrderNo) }}</template>
        </el-table-column>
        <el-table-column prop="partnerName" label="往来单位 / 客户" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ dash(row.partnerName) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="90" align="right">
          <template #default="{ row }">{{ num(row.totalQuantity) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span class="money">{{ money(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="returnDate" label="退货日期" width="115" align="center" />
        <el-table-column prop="reason" label="退货原因" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CANCELED' ? 'info' : 'success'" size="small">
              {{ row.statusLabel || (row.status === 'CANCELED' ? '已作废' : '已生效') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button
              v-if="row.status !== 'CANCELED'"
              link
              type="warning"
              @click="onCancel(row)"
            >
              作废
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

    <!-- 新建退货单 -->
    <el-dialog v-model="formVisible" title="新建退货单" width="920px" top="5vh" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="14">
          <el-col :span="8">
            <el-form-item label="退货类型" required>
              <el-radio-group v-model="form.returnType" @change="onTypeChange">
                <el-radio-button value="PURCHASE_RETURN">采购退货</el-radio-button>
                <el-radio-button value="SALE_RETURN">销售退货</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="退货日期" required>
              <el-date-picker
                v-model="form.returnDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关联原单">
              <el-select
                v-model="form.sourceOrderId"
                placeholder="可不选"
                clearable
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="o in sourceOrders"
                  :key="o.id"
                  :label="`${o.orderNo}（${o.partner || ''} ${o.orderDate || ''}）`"
                  :value="o.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="退货原因">
              <el-input v-model="form.reason" placeholder="如 顾客买错口味 / 包装破损" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-alert
        :type="form.returnType === 'SALE_RETURN' ? 'success' : 'warning'"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      >
        {{
          form.returnType === 'SALE_RETURN'
            ? '销售退货：客户退回商品，库存会增加（原路退回原批次）。'
            : '采购退货：把货退给供应商，库存会减少（按先进先出扣减）。'
        }}
      </el-alert>

      <div class="card-head">
        <span>退货明细</span>
        <el-button type="primary" plain size="small" :icon="Plus" @click="addItem">
          添加商品
        </el-button>
      </div>

      <el-table :data="form.items" border size="small" empty-text="请添加退货商品">
        <el-table-column type="index" label="#" width="46" align="center" />
        <el-table-column label="商品" min-width="240">
          <template #default="{ row }">
            <el-select
              v-model="row.productId"
              placeholder="搜索商品"
              filterable
              style="width: 100%"
              @change="(val) => onProductChange(row, val)"
            >
              <el-option
                v-for="p in products"
                :key="p.id"
                :label="`${p.name}（${p.code}）`"
                :value="p.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="130">
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="1"
              :step="1"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="单价" width="130">
          <template #default="{ row }">
            <el-input-number
              v-model="row.price"
              :min="0"
              :precision="2"
              :step="0.1"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span class="money">{{ money(row.quantity * row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="form.items.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="summary">
        合计数量 <b>{{ num(formTotalQuantity) }}</b> ，合计金额
        <b>{{ money(formTotalAmount) }}</b> 元
      </div>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">
          提交（立即生效并回退库存）
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="退货单详情" width="880px" top="6vh">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="退货单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="退货类型">
          {{ detail.returnTypeLabel || typeLabel(detail.returnType) }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ detail.statusLabel || (detail.status === 'CANCELED' ? '已作废' : '已生效') }}
        </el-descriptions-item>
        <el-descriptions-item label="关联原单">{{ dash(detail.sourceOrderNo) }}</el-descriptions-item>
        <el-descriptions-item label="往来单位">{{ dash(detail.partnerName) }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ detail.returnDate }}</el-descriptions-item>
        <el-descriptions-item label="原因" :span="3">{{ dash(detail.reason) }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items || []" size="small" border style="margin-top: 14px">
        <el-table-column prop="productName" label="商品" min-width="180" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批次" width="150" />
        <el-table-column label="数量" width="80" align="right">
          <template #default="{ row }">{{ num(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="单价" width="100" align="right">
          <template #default="{ row }">{{ money(row.price) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">
            <span class="money">{{ money(row.amount) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  cancelReturn,
  createReturn,
  getReturn,
  pageReturns
} from '@/api/returns'
import { pageSales } from '@/api/sale'
import { pagePurchases } from '@/api/purchase'
import { pageProducts } from '@/api/product'
import { RETURN_TYPE, dash, money, num, today } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const products = ref([])
const sourceOrders = ref([])
const dateRange = ref([])

const query = reactive({ returnType: null, keyword: '', page: 1, size: 10 })

const typeTag = (t) => (RETURN_TYPE[t] || {}).type || 'info'
const typeLabel = (t) => (RETURN_TYPE[t] || {}).label || t || '-'

watch(dateRange, (val) => {
  query.startDate = val && val[0] ? val[0] : undefined
  query.endDate = val && val[1] ? val[1] : undefined
})

async function load() {
  loading.value = true
  try {
    const res = await pageReturns(query)
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
    returnType: null,
    keyword: '',
    startDate: undefined,
    endDate: undefined,
    page: 1,
    size: 10
  })
  load()
}

/* ---------------- 新建 ---------------- */
const formVisible = ref(false)
const form = reactive({
  returnType: 'SALE_RETURN',
  returnDate: today(),
  sourceOrderId: null,
  reason: '',
  items: []
})

const formTotalQuantity = computed(() =>
  form.items.reduce((s, r) => s + Number(r.quantity || 0), 0)
)
const formTotalAmount = computed(() =>
  form.items.reduce((s, r) => s + Number(r.quantity || 0) * Number(r.price || 0), 0)
)

function openForm() {
  form.returnType = 'SALE_RETURN'
  form.returnDate = today()
  form.sourceOrderId = null
  form.reason = ''
  form.items = []
  addItem()
  loadSourceOrders()
  formVisible.value = true
}

function addItem() {
  form.items.push({ productId: null, quantity: 1, price: 0 })
}

function onProductChange(row, productId) {
  const p = products.value.find((x) => x.id === productId)
  if (p) row.price = Number(p.salePrice || p.purchasePrice || 0)
}

function onTypeChange() {
  form.sourceOrderId = null
  loadSourceOrders()
}

/** 原单只作参考，给下拉里显示单号与往来单位 */
async function loadSourceOrders() {
  try {
    if (form.returnType === 'SALE_RETURN') {
      const res = await pageSales({ page: 1, size: 50, status: 'FINISHED' })
      sourceOrders.value = ((res && res.records) || []).map((o) => ({
        id: o.id,
        orderNo: o.orderNo,
        partner: o.customerName,
        orderDate: o.orderDate
      }))
    } else {
      const res = await pagePurchases({ page: 1, size: 50, status: 'FINISHED' })
      sourceOrders.value = ((res && res.records) || []).map((o) => ({
        id: o.id,
        orderNo: o.orderNo,
        partner: o.supplierName,
        orderDate: o.orderDate
      }))
    }
  } catch {
    sourceOrders.value = []
  }
}

async function onSave() {
  if (!form.items.length) {
    ElMessage.warning('请至少添加一条退货明细')
    return
  }
  const invalid = form.items.find((r) => !r.productId || !r.quantity || r.quantity <= 0)
  if (invalid) {
    ElMessage.warning('明细中存在未选择商品或数量非法的行')
    return
  }

  saving.value = true
  try {
    await createReturn({
      returnType: form.returnType,
      sourceOrderId: form.sourceOrderId || undefined,
      returnDate: form.returnDate,
      reason: form.reason || undefined,
      items: form.items.map((r) => ({
        productId: r.productId,
        quantity: r.quantity,
        price: r.price
      }))
    })
    ElMessage.success('退货单已生效，库存已回退')
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

/* ---------------- 详情 / 作废 ---------------- */
const detailVisible = ref(false)
const detail = ref({})

async function showDetail(row) {
  detail.value = (await getReturn(row.id)) || {}
  detailVisible.value = true
}

async function onCancel(row) {
  await ElMessageBox.confirm(
    `确认作废退货单「${row.orderNo}」？作废后库存会反向回滚回退货前状态。`,
    '作废确认',
    { type: 'warning', confirmButtonText: '确认作废', cancelButtonText: '取消' }
  )
  await cancelReturn(row.id)
  ElMessage.success('已作废')
  load()
}

onMounted(async () => {
  try {
    const p = await pageProducts({ page: 1, size: 200, status: 1 })
    products.value = (p && p.records) || []
  } catch {
    /* 拦截器已提示 */
  }
  load()
})
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

.summary {
  margin-top: 14px;
  text-align: right;
  color: #606266;
  font-size: 14px;
}

.summary b {
  color: #f56c6c;
  font-size: 16px;
  margin: 0 4px;
}
</style>
