<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title">
        库存盘点
        <span class="sub">建账抓取账面数 → 录实盘数 → 提交后自动生成盘盈/盘亏流水</span>
      </div>
      <div class="page-actions">
        <el-button type="primary" :icon="Plus" @click="openForm">新建盘点单</el-button>
      </div>
    </div>

    <div class="card">
      <div class="search-bar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option v-for="(v, k) in CHECK_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 250px"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe border>
        <el-table-column prop="checkNo" label="盘点单号" width="170" />
        <el-table-column prop="checkDate" label="盘点日期" width="115" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="差异商品数" width="110" align="center">
          <template #default="{ row }">
            <span :class="row.diffItemCount > 0 ? 'text-warn' : 'text-muted'">
              {{ num(row.diffItemCount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="差异数量合计" width="120" align="right">
          <template #default="{ row }">
            <span :class="row.totalDiffQuantity > 0 ? 'text-warn' : 'text-muted'">
              {{ num(row.totalDiffQuantity) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="90" align="center" />
        <el-table-column label="完成时间" width="150" align="center">
          <template #default="{ row }">{{ shortTime(row.finishTime) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/check/${row.id}`)">
              {{ row.status === 'DRAFT' ? '录入实盘数' : '查看明细' }}
            </el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="warning" @click="onCancel(row)">
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

    <el-dialog v-model="formVisible" title="新建盘点单" width="620px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="盘点日期" required>
          <el-date-picker
            v-model="form.checkDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="盘点范围">
          <el-select
            v-model="form.productIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="不选 = 对全部在用商品建账"
            style="width: 100%"
          >
            <el-option
              v-for="p in products"
              :key="p.id"
              :label="`${p.name}（账面 ${p.stockQuantity}）`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="如 月度盘点 / 货架整理后复盘" />
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" show-icon>
        <template #default>
          建账时会把每个商品<b>当前的账面库存</b>快照到明细里。之后即使有人继续开单，
          也以你录入的实盘数为准来调平，不会把中途发生的业务算成盘亏。
        </template>
      </el-alert>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">建账并开始盘点</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  cancelStockCheck,
  createStockCheck,
  pageStockChecks
} from '@/api/stockCheck'
import { pageProducts } from '@/api/product'
import { num, shortTime, today } from '@/utils/format'

// 盘点单只有草稿 / 已完成 / 已作废三种状态
const CHECK_STATUS = {
  DRAFT: { label: '盘点中', type: 'warning' },
  FINISHED: { label: '已完成', type: 'success' },
  CANCELED: { label: '已作废', type: 'info' }
}

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const products = ref([])
const dateRange = ref([])

const query = reactive({ status: null, page: 1, size: 10 })

const statusTag = (s) => (CHECK_STATUS[s] || {}).type || 'info'
const statusLabel = (s) => (CHECK_STATUS[s] || {}).label || s || '-'

watch(dateRange, (val) => {
  query.startDate = val && val[0] ? val[0] : undefined
  query.endDate = val && val[1] ? val[1] : undefined
})

async function load() {
  loading.value = true
  try {
    const res = await pageStockChecks(query)
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
    status: null,
    startDate: undefined,
    endDate: undefined,
    page: 1,
    size: 10
  })
  load()
}

const formVisible = ref(false)
const form = reactive({ checkDate: today(), remark: '', productIds: [] })

function openForm() {
  form.checkDate = today()
  form.remark = ''
  form.productIds = []
  formVisible.value = true
}

async function onCreate() {
  saving.value = true
  try {
    const data = await createStockCheck({
      checkDate: form.checkDate,
      remark: form.remark || undefined,
      productIds: form.productIds.length ? form.productIds : undefined
    })
    ElMessage.success(
      form.productIds.length
        ? '盘点单已建账，请录入实盘数'
        : '已对全部在用商品建账，请录入实盘数'
    )
    formVisible.value = false
    load()
    // 建完账直接进详情页录数，少一次点击
    if (data && data.id) router.push(`/check/${data.id}`)
  } finally {
    saving.value = false
  }
}

async function onCancel(row) {
  await ElMessageBox.confirm(
    `确认作废盘点单「${row.checkNo}」？作废后已录入的实盘数会一并丢弃。`,
    '作废确认',
    { type: 'warning', confirmButtonText: '作废', cancelButtonText: '取消' }
  )
  await cancelStockCheck(row.id)
  ElMessage.success('已作废')
  load()
}

onMounted(async () => {
  try {
    const p = await pageProducts({ page: 1, size: 200 })
    products.value = (p && p.records) || []
  } catch {
    /* 拦截器已提示 */
  }
  load()
})
</script>
